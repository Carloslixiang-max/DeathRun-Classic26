#!/usr/bin/env python3
"""Cross-check warning-facing route direction with named Death stage controls.

This report uses two independent archived evidence families:
1) the warning-facing direction hypothesis on the portal MST;
2) named "Next Stage" / "Previous Stage" ArmorStand control markers mapped to
   their nearest portal gates.

Interpretive assumption for the stage check:
- a Next Stage control belongs to the earlier side of a stage transition;
- a Previous Stage control belongs to the later side.

This is still candidate route-order evidence, not proof of Runner spawn,
checkpoint numbering, respawn coordinates, start or finish.
"""

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from anvil_portal_segment_graph import build_gaps, minimum_spanning_tree, warning_groups
from anvil_route_direction_hypothesis import ordered_path, score_direction
from anvil_stage_evidence import marker_direction, parse_stage_markers
from anvil_stage_portal_evidence import build_links
from anvil_warning_facing_evidence import measure_tree


@dataclass(frozen=True)
class StageDirectionCheck:
    path: tuple[int, ...]
    warning_agree: int
    warning_disagree: int
    warning_lateral: int
    next_portals: tuple[int, ...]
    previous_portals: tuple[int, ...]
    stage_order_compatible: bool


def build_checks(
    probe_report: Path,
    entity_report: Path,
    dot_threshold: float,
    portal_radius: float,
) -> list[StageDirectionCheck]:
    portals, grouped = warning_groups(probe_report)
    gaps = build_gaps(len(portals), grouped)
    tree = minimum_spanning_tree(len(portals), gaps)
    measurements = measure_tree(tree)
    base_path = ordered_path(len(portals), tree)
    if not base_path:
        return []

    markers = parse_stage_markers(entity_report)
    links = build_links(markers, portals, portal_radius)

    next_portals = tuple(
        sorted(
            link.nearest_portal_id
            for link in links
            if link.nearest_portal_id is not None
            and marker_direction(link.marker.name) == "NEXT"
        )
    )
    previous_portals = tuple(
        sorted(
            link.nearest_portal_id
            for link in links
            if link.nearest_portal_id is not None
            and marker_direction(link.marker.name) == "PREVIOUS"
        )
    )

    checks: list[StageDirectionCheck] = []
    for path in (base_path, tuple(reversed(base_path))):
        score = score_direction(path, measurements, dot_threshold)
        index = {portal_id: position for position, portal_id in enumerate(path)}

        compatible = False
        if next_portals and previous_portals:
            next_positions = [index[item] for item in next_portals if item in index]
            previous_positions = [index[item] for item in previous_portals if item in index]
            if next_positions and previous_positions:
                compatible = max(next_positions) < min(previous_positions)

        checks.append(
            StageDirectionCheck(
                path=tuple(path),
                warning_agree=score.agree,
                warning_disagree=score.disagree,
                warning_lateral=score.lateral,
                next_portals=next_portals,
                previous_portals=previous_portals,
                stage_order_compatible=compatible,
            )
        )
    return checks


def _path(path: Sequence[int]) -> str:
    return "->".join(f"{item:03d}" for item in path)


def _ids(items: Sequence[int]) -> str:
    return ",".join(f"{item:03d}" for item in items) or "none"


def render_report(
    probe_report: Path,
    entity_report: Path,
    checks: Sequence[StageDirectionCheck],
) -> tuple[str, dict[str, int | str]]:
    ranked = sorted(
        checks,
        key=lambda item: (
            -(item.warning_agree - item.warning_disagree),
            -item.warning_agree,
            item.warning_disagree,
            item.path,
        ),
    )
    best = ranked[0] if ranked else None
    compatible = [item for item in checks if item.stage_order_compatible]

    stats: dict[str, int | str] = {
        "candidate_directions": len(checks),
        "stage_compatible_directions": len(compatible),
        "best_warning_path": _path(best.path) if best else "none",
        "best_warning_stage_compatible": (
            "true" if best and best.stage_order_compatible else "false"
        ),
        "next_portals": _ids(best.next_portals) if best else "none",
        "previous_portals": _ids(best.previous_portals) if best else "none",
    }

    lines = [
        "# DeathRun Classic26 stage-control / warning-direction cross-check",
        f"probe_source={probe_report.name}",
        f"entity_source={entity_report.name}",
        (
            "stage_direction_summary "
            f"candidate_directions={stats['candidate_directions']} "
            f"stage_compatible_directions={stats['stage_compatible_directions']} "
            f"best_warning_path={stats['best_warning_path']} "
            f"best_warning_stage_compatible={stats['best_warning_stage_compatible']} "
            f"next_portals={stats['next_portals']} "
            f"previous_portals={stats['previous_portals']}"
        ),
        "# Stage compatibility assumes Next Stage is earlier than Previous Stage.",
        "# This is an independent semantic hypothesis, not confirmed Runner checkpoint order.",
        "",
    ]

    for item in checks:
        lines.append(
            "STAGE_DIRECTION_CHECK\t"
            f"path={_path(item.path)}\t"
            f"warning_agree={item.warning_agree}\t"
            f"warning_disagree={item.warning_disagree}\t"
            f"warning_lateral={item.warning_lateral}\t"
            f"next_portals={_ids(item.next_portals)}\t"
            f"previous_portals={_ids(item.previous_portals)}\t"
            f"stage_order_compatible={str(item.stage_order_compatible).lower()}"
        )
    return "\n".join(lines) + "\n", stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("entity_evidence_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--dot-threshold", type=float, default=0.25)
    parser.add_argument("--portal-radius", type=float, default=30.0)
    parser.add_argument("--require-best-stage-compatible", action="store_true")
    parser.add_argument("--min-stage-compatible-directions", type=int, default=0)
    args = parser.parse_args(argv)

    checks = build_checks(
        args.probe_report,
        args.entity_evidence_report,
        args.dot_threshold,
        args.portal_radius,
    )
    report, stats = render_report(
        args.probe_report,
        args.entity_evidence_report,
        checks,
    )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if (
        int(stats["stage_compatible_directions"])
        < args.min_stage_compatible_directions
    ):
        return 2
    if (
        args.require_best_stage_compatible
        and stats["best_warning_stage_compatible"] != "true"
    ):
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
