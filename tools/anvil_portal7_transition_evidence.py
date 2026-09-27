#!/usr/bin/env python3
"""Resolve the special Portal #007 side using its full local trap/stage evidence.

The generic MST-side pass is ambiguous for #007 because the same low warning is
used by both nearest-neighbor MST edges. This tool instead examines every
warning/action pair whose Runner warning is assigned to Portal #007, classifies
both signs relative to the portal plane, compares their elevation with the
portal base, and adds the named Next Stage marker.

This is a stronger local transition hypothesis, not recovered original server
checkpoint configuration.
"""

from __future__ import annotations

import argparse
import statistics
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from anvil_cluster import parse_probe_report as parse_candidate_probe
from anvil_portal_segment_evidence import assign_pairs
from anvil_sign_links import Sign, parse_probe_report as parse_sign_probe, sign_kind
from anvil_stage_evidence import StageMarker, marker_direction, parse_stage_markers
from anvil_topology import Component, portal_components
from anvil_trap_evidence import build_actions, build_pair_candidates


@dataclass(frozen=True)
class Portal7Pair:
    warning: Sign
    action: Sign
    label: str
    warning_side: int | None
    action_side: int | None
    warning_y_delta: float
    action_y_delta: float


def _plane(component: Component) -> tuple[str, int] | None:
    min_x, _min_y, min_z, max_x, _max_y, max_z = component.bbox
    if min_x == max_x:
        return "x", min_x
    if min_z == max_z:
        return "z", min_z
    return None


def _side_xyz(component: Component, x: float, z: float) -> int | None:
    plane = _plane(component)
    if plane is None:
        return None
    axis, value = plane
    coordinate = x if axis == "x" else z
    if coordinate < value:
        return -1
    if coordinate > value:
        return 1
    return 0


def _side_sign(component: Component, sign: Sign) -> int | None:
    return _side_xyz(component, sign.x, sign.z)


def build_portal7_pairs(probe_report: Path) -> tuple[Component, list[Portal7Pair]]:
    _buttons, signs = parse_sign_probe(probe_report)
    warnings = sorted(
        (sign for sign in signs if sign_kind(sign) == "WARNING"),
        key=lambda sign: (sign.x, sign.y, sign.z, sign.text),
    )
    actions = build_actions(signs)
    pairs, _unmatched_warnings, _unmatched_actions = build_pair_candidates(
        warnings,
        actions,
        30.0,
    )

    _interactive, portal_points = parse_candidate_probe(probe_report)
    portals = portal_components(portal_points)
    if len(portals) < 7:
        raise ValueError(f"expected at least 7 portals, got {len(portals)}")
    portal7 = portals[6]
    assignments = assign_pairs(pairs, portals)

    min_y = portal7.bbox[1]
    result: list[Portal7Pair] = []
    for item in assignments:
        if item.warning_portal_id != 7:
            continue
        warning = item.pair.warning
        action = item.pair.action.sign
        result.append(
            Portal7Pair(
                warning=warning,
                action=action,
                label=item.pair.action.label,
                warning_side=_side_sign(portal7, warning),
                action_side=_side_sign(portal7, action),
                warning_y_delta=warning.y - min_y,
                action_y_delta=action.y - min_y,
            )
        )

    result.sort(key=lambda item: (item.warning.y, item.warning.x, item.warning.z, item.label))
    return portal7, result


def next_stage_markers(
    entity_report: Path,
    portal7: Component,
) -> list[tuple[StageMarker, int | None, float]]:
    min_y = portal7.bbox[1]
    result = []
    for marker in parse_stage_markers(entity_report):
        if marker_direction(marker.name) != "NEXT":
            continue
        result.append(
            (
                marker,
                _side_xyz(portal7, marker.x, marker.z),
                marker.y - min_y,
            )
        )
    result.sort(key=lambda item: (item[0].x, item[0].y, item[0].z))
    return result


def _side_text(value: int | None) -> str:
    return {None: "none", -1: "negative", 0: "on-plane", 1: "positive"}[value]


def _pair_text(item: Portal7Pair) -> str:
    return (
        f"warning={item.warning.x},{item.warning.y},{item.warning.z}"
        f"[side={_side_text(item.warning_side)},dy={item.warning_y_delta:.1f}]"
        f" action={item.action.x},{item.action.y},{item.action.z}"
        f"[side={_side_text(item.action_side)},dy={item.action_y_delta:.1f}]"
        f" label={item.label}"
    )


def render_report(
    probe_source: Path,
    entity_source: Path,
    portal7: Component,
    pairs: Sequence[Portal7Pair],
    stage_markers: Sequence[tuple[StageMarker, int | None, float]],
    high_tolerance: float,
) -> tuple[str, dict[str, int | float | str]]:
    negative = [item for item in pairs if item.warning_side == -1]
    positive = [item for item in pairs if item.warning_side == 1]
    same_side_pairs = sum(
        item.warning_side is not None
        and item.warning_side == item.action_side
        for item in pairs
    )

    negative_high = [
        item for item in negative
        if abs(item.warning_y_delta) <= high_tolerance
        and abs(item.action_y_delta) <= high_tolerance + 2
    ]
    positive_low = [
        item for item in positive
        if item.warning_y_delta <= -10
        and item.action_y_delta <= -10
    ]

    next_negative_high = [
        marker
        for marker, side, delta in stage_markers
        if side == -1 and abs(delta) <= high_tolerance
    ]

    negative_mean_y = (
        statistics.mean(item.warning.y for item in negative)
        if negative else -999.0
    )
    positive_mean_y = (
        statistics.mean(item.warning.y for item in positive)
        if positive else -999.0
    )

    # Candidate side is emitted only when all independently visible local
    # structure agrees on the high negative / low positive split.
    resolved_side = "none"
    if (
        len(negative_high) >= 2
        and len(positive_low) >= 1
        and len(next_negative_high) >= 1
        and same_side_pairs == len(pairs)
    ):
        resolved_side = "negative"

    stats: dict[str, int | float | str] = {
        "portal7_pairs": len(pairs),
        "negative_pairs": len(negative),
        "positive_pairs": len(positive),
        "same_side_pairs": same_side_pairs,
        "negative_high_pairs": len(negative_high),
        "positive_low_pairs": len(positive_low),
        "next_stage_markers": len(stage_markers),
        "next_negative_high": len(next_negative_high),
        "negative_warning_mean_y": negative_mean_y,
        "positive_warning_mean_y": positive_mean_y,
        "candidate_outgoing_side": resolved_side,
    }

    min_x, min_y, min_z, max_x, max_y, max_z = portal7.bbox
    lines = [
        "# DeathRun Classic26 Portal #007 local transition evidence",
        f"probe_source={probe_source.name}",
        f"entity_source={entity_source.name}",
        (
            "portal7_transition_summary "
            f"bbox={min_x},{min_y},{min_z}:{max_x},{max_y},{max_z} "
            f"portal7_pairs={stats['portal7_pairs']} "
            f"negative_pairs={stats['negative_pairs']} "
            f"positive_pairs={stats['positive_pairs']} "
            f"same_side_pairs={stats['same_side_pairs']} "
            f"negative_high_pairs={stats['negative_high_pairs']} "
            f"positive_low_pairs={stats['positive_low_pairs']} "
            f"next_stage_markers={stats['next_stage_markers']} "
            f"next_negative_high={stats['next_negative_high']} "
            f"negative_warning_mean_y={float(stats['negative_warning_mean_y']):.2f} "
            f"positive_warning_mean_y={float(stats['positive_warning_mean_y']):.2f} "
            f"candidate_outgoing_side={stats['candidate_outgoing_side']}"
        ),
        "# Candidate side requires a high negative-side trap cluster, a low positive-side trap cluster,",
        "# and the independent Next Stage marker on the high negative side.",
        "# It is a playtest route-side hypothesis, NOT recovered original checkpoint config.",
        "",
    ]

    for index, item in enumerate(pairs, 1):
        lines.append(f"PORTAL7_PAIR\tid={index:03d}\t{_pair_text(item)}")

    for index, (marker, side, delta) in enumerate(stage_markers, 1):
        lines.append(
            "PORTAL7_STAGE\t"
            f"id={index:03d}\tname={marker.name}\t"
            f"pos={marker.x:.3f},{marker.y:.3f},{marker.z:.3f}\t"
            f"side={_side_text(side)}\tdy={delta:.2f}"
        )

    return "\n".join(lines) + "\n", stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("entity_evidence_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--high-tolerance", type=float, default=3.0)
    parser.add_argument("--min-pairs", type=int, default=0)
    parser.add_argument("--require-resolved-side", action="store_true")
    args = parser.parse_args(argv)

    portal7, pairs = build_portal7_pairs(args.probe_report)
    stages = next_stage_markers(args.entity_evidence_report, portal7)
    report, stats = render_report(
        args.probe_report,
        args.entity_evidence_report,
        portal7,
        pairs,
        stages,
        args.high_tolerance,
    )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if int(stats["portal7_pairs"]) < args.min_pairs:
        return 2
    if args.require_resolved_side and stats["candidate_outgoing_side"] == "none":
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
