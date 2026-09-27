#!/usr/bin/env python3
"""Infer candidate route-entry/exit sides of portal gates from MST connection warnings.

The best warning-facing route path is used only as a candidate direction.
For each portal on that path, the warning selected by the MST edge to the
previous/next segment is classified relative to the portal's plane.

If an interior gate's two connection warnings lie on opposite sides, the gate
has strong geometric evidence for a route crossing. Endpoint external sides are
reported as candidates only. No original respawn/start/finish coordinate is
invented.
"""

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from anvil_portal_segment_graph import SegmentGap, build_gaps, minimum_spanning_tree, warning_groups
from anvil_route_direction_hypothesis import ordered_path, score_direction
from anvil_warning_facing_evidence import measure_tree
from anvil_topology import Component
from anvil_sign_links import Sign


@dataclass(frozen=True)
class PortalRouteSide:
    portal_id: int
    component: Component
    path_index: int
    previous_portal: int | None
    next_portal: int | None
    previous_warning: Sign | None
    next_warning: Sign | None
    previous_side: int | None
    next_side: int | None
    classification: str
    external_side_candidate: int | None


def _edge_for(tree: Sequence[SegmentGap], left: int, right: int) -> SegmentGap:
    for edge in tree:
        if {edge.left, edge.right} == {left, right}:
            return edge
    raise KeyError((left, right))


def _warning_for(edge: SegmentGap, portal_id: int) -> Sign:
    if edge.left == portal_id:
        return edge.left_warning
    if edge.right == portal_id:
        return edge.right_warning
    raise KeyError(portal_id)


def _plane(component: Component) -> tuple[str, int] | None:
    min_x, _min_y, min_z, max_x, _max_y, max_z = component.bbox
    if min_x == max_x:
        return "x", min_x
    if min_z == max_z:
        return "z", min_z
    return None


def _side(component: Component, sign: Sign | None) -> int | None:
    if sign is None:
        return None
    plane = _plane(component)
    if plane is None:
        return None
    axis, value = plane
    coordinate = sign.x if axis == "x" else sign.z
    if coordinate < value:
        return -1
    if coordinate > value:
        return 1
    return 0


def best_candidate_path(probe_report: Path, dot_threshold: float) -> tuple[int, ...]:
    portals, grouped = warning_groups(probe_report)
    gaps = build_gaps(len(portals), grouped)
    tree = minimum_spanning_tree(len(portals), gaps)
    base = ordered_path(len(portals), tree)
    if not base:
        return ()
    measurements = measure_tree(tree)
    candidates = [
        score_direction(base, measurements, dot_threshold),
        score_direction(tuple(reversed(base)), measurements, dot_threshold),
    ]
    candidates.sort(
        key=lambda item: (
            -(item.agree - item.disagree),
            -item.agree,
            item.disagree,
            item.path,
        )
    )
    return candidates[0].path


def build_route_sides(
    probe_report: Path,
    dot_threshold: float,
) -> tuple[tuple[int, ...], list[PortalRouteSide]]:
    portals, grouped = warning_groups(probe_report)
    gaps = build_gaps(len(portals), grouped)
    tree = minimum_spanning_tree(len(portals), gaps)
    path = best_candidate_path(probe_report, dot_threshold)
    result: list[PortalRouteSide] = []

    for index, portal_id in enumerate(path):
        component = portals[portal_id - 1]
        previous_portal = path[index - 1] if index > 0 else None
        next_portal = path[index + 1] if index + 1 < len(path) else None

        previous_warning = None
        if previous_portal is not None:
            previous_warning = _warning_for(
                _edge_for(tree, previous_portal, portal_id),
                portal_id,
            )
        next_warning = None
        if next_portal is not None:
            next_warning = _warning_for(
                _edge_for(tree, portal_id, next_portal),
                portal_id,
            )

        previous_side = _side(component, previous_warning)
        next_side = _side(component, next_warning)
        external_side = None

        if previous_portal is None and next_side in (-1, 1):
            classification = "START_ENDPOINT"
            external_side = -next_side
        elif next_portal is None and previous_side in (-1, 1):
            classification = "FINISH_ENDPOINT"
            external_side = -previous_side
        elif previous_side in (-1, 1) and next_side in (-1, 1):
            classification = (
                "OPPOSITE_SIDES"
                if previous_side != next_side
                else "SAME_SIDE_AMBIGUOUS"
            )
        else:
            classification = "UNRESOLVED"

        result.append(
            PortalRouteSide(
                portal_id=portal_id,
                component=component,
                path_index=index,
                previous_portal=previous_portal,
                next_portal=next_portal,
                previous_warning=previous_warning,
                next_warning=next_warning,
                previous_side=previous_side,
                next_side=next_side,
                classification=classification,
                external_side_candidate=external_side,
            )
        )
    return path, result


def _side_text(value: int | None) -> str:
    return {None: "none", -1: "negative", 0: "on-plane", 1: "positive"}[value]


def _pos(sign: Sign | None) -> str:
    if sign is None:
        return "none"
    return f"{sign.x},{sign.y},{sign.z}"


def _id(value: int | None) -> str:
    return "none" if value is None else f"{value:03d}"


def render_report(
    source: Path,
    path: Sequence[int],
    evidence: Sequence[PortalRouteSide],
) -> tuple[str, dict[str, int | str]]:
    interiors = [
        item
        for item in evidence
        if item.previous_portal is not None and item.next_portal is not None
    ]
    opposite = sum(item.classification == "OPPOSITE_SIDES" for item in interiors)
    same = sum(item.classification == "SAME_SIDE_AMBIGUOUS" for item in interiors)
    unresolved = sum(item.classification == "UNRESOLVED" for item in interiors)
    start = next((item for item in evidence if item.classification == "START_ENDPOINT"), None)
    finish = next((item for item in evidence if item.classification == "FINISH_ENDPOINT"), None)

    stats: dict[str, int | str] = {
        "portals": len(evidence),
        "interior_portals": len(interiors),
        "opposite_side_interiors": opposite,
        "same_side_interiors": same,
        "unresolved_interiors": unresolved,
        "start_portal": _id(start.portal_id if start else None),
        "finish_portal": _id(finish.portal_id if finish else None),
        "start_external_side": _side_text(start.external_side_candidate if start else None),
        "finish_external_side": _side_text(finish.external_side_candidate if finish else None),
    }

    path_text = "->".join(f"{item:03d}" for item in path) or "none"
    lines = [
        "# DeathRun Classic26 candidate portal route-side geometry",
        f"source={source.name}",
        (
            "portal_route_side_summary "
            f"path={path_text} "
            f"portals={stats['portals']} "
            f"interior_portals={stats['interior_portals']} "
            f"opposite_side_interiors={stats['opposite_side_interiors']} "
            f"same_side_interiors={stats['same_side_interiors']} "
            f"unresolved_interiors={stats['unresolved_interiors']} "
            f"start_portal={stats['start_portal']} "
            f"start_external_side={stats['start_external_side']} "
            f"finish_portal={stats['finish_portal']} "
            f"finish_external_side={stats['finish_external_side']}"
        ),
        "# Connection warnings come from the MST edge endpoints for each portal segment.",
        "# Opposite-side geometry supports a gate crossing but does NOT prove original checkpoint respawn coordinates.",
        "# Endpoint external sides are candidates under the current route-direction hypothesis only.",
        "",
    ]

    for item in evidence:
        plane = _plane(item.component)
        plane_text = "unknown" if plane is None else f"{plane[0]}={plane[1]}"
        lines.append(
            "PORTAL_ROUTE_SIDE\t"
            f"portal={item.portal_id:03d}\t"
            f"path_index={item.path_index}\t"
            f"plane={plane_text}\t"
            f"previous={_id(item.previous_portal)}\t"
            f"previous_warning={_pos(item.previous_warning)}\t"
            f"previous_side={_side_text(item.previous_side)}\t"
            f"next={_id(item.next_portal)}\t"
            f"next_warning={_pos(item.next_warning)}\t"
            f"next_side={_side_text(item.next_side)}\t"
            f"classification={item.classification}\t"
            f"external_side_candidate={_side_text(item.external_side_candidate)}"
        )

    return "\n".join(lines) + "\n", stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--dot-threshold", type=float, default=0.25)
    parser.add_argument("--min-portals", type=int, default=0)
    parser.add_argument("--min-opposite-side-interiors", type=int, default=0)
    args = parser.parse_args(argv)

    path, evidence = build_route_sides(args.probe_report, args.dot_threshold)
    report, stats = render_report(args.probe_report, path, evidence)

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if int(stats["portals"]) < args.min_portals:
        return 2
    if int(stats["opposite_side_interiors"]) < args.min_opposite_side_interiors:
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
