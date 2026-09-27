#!/usr/bin/env python3
"""Partition archived warning/action trap pairs by nearest surviving portal gate.

The Runner warning position is used as the route-side anchor. Nearest-center
assignment is a geometric review aid only; it does not establish checkpoint
numbering, route order, respawn points, start or finish roles.
"""

from __future__ import annotations

import argparse
import math
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from anvil_cluster import parse_probe_report as parse_candidate_probe
from anvil_sign_links import Sign, parse_probe_report as parse_sign_probe, sign_kind
from anvil_topology import Component, portal_components
from anvil_trap_evidence import (
    ActionEvidence,
    PairCandidate,
    build_actions,
    build_pair_candidates,
)


@dataclass(frozen=True)
class PairPortalAssignment:
    pair: PairCandidate
    warning_portal_id: int | None
    warning_portal: Component | None
    warning_distance: float | None
    action_portal_id: int | None
    action_portal: Component | None
    action_distance: float | None


def _distance_xyz(
    x: float,
    y: float,
    z: float,
    component: Component,
) -> float:
    cx, cy, cz = component.center
    return math.sqrt((x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2)


def _nearest_portal(
    x: float,
    y: float,
    z: float,
    portals: Sequence[Component],
) -> tuple[int, Component, float] | None:
    if not portals:
        return None
    ranked = sorted(
        (
            (index, component, _distance_xyz(x, y, z, component))
            for index, component in enumerate(portals, 1)
        ),
        key=lambda item: (item[2], item[0]),
    )
    return ranked[0]


def assign_pairs(
    pairs: Sequence[PairCandidate],
    portals: Sequence[Component],
) -> list[PairPortalAssignment]:
    result: list[PairPortalAssignment] = []
    for pair in pairs:
        warning_nearest = _nearest_portal(
            pair.warning.x,
            pair.warning.y,
            pair.warning.z,
            portals,
        )
        action_sign = pair.action.sign
        action_nearest = _nearest_portal(
            action_sign.x,
            action_sign.y,
            action_sign.z,
            portals,
        )
        result.append(
            PairPortalAssignment(
                pair=pair,
                warning_portal_id=warning_nearest[0] if warning_nearest else None,
                warning_portal=warning_nearest[1] if warning_nearest else None,
                warning_distance=warning_nearest[2] if warning_nearest else None,
                action_portal_id=action_nearest[0] if action_nearest else None,
                action_portal=action_nearest[1] if action_nearest else None,
                action_distance=action_nearest[2] if action_nearest else None,
            )
        )
    return result


def _bbox(component: Component) -> str:
    min_x, min_y, min_z, max_x, max_y, max_z = component.bbox
    return f"{min_x},{min_y},{min_z}:{max_x},{max_y},{max_z}"


def _pos(sign: Sign) -> str:
    return f"{sign.x},{sign.y},{sign.z}"


def render_report(
    source: Path,
    portals: Sequence[Component],
    assignments: Sequence[PairPortalAssignment],
) -> tuple[str, dict[str, int]]:
    warning_counts = Counter(
        item.warning_portal_id
        for item in assignments
        if item.warning_portal_id is not None
    )
    same = sum(
        item.warning_portal_id is not None
        and item.warning_portal_id == item.action_portal_id
        for item in assignments
    )
    cross = sum(
        item.warning_portal_id is not None
        and item.action_portal_id is not None
        and item.warning_portal_id != item.action_portal_id
        for item in assignments
    )
    stats = {
        "pairs": len(assignments),
        "portal_components": len(portals),
        "portals_with_warning_pairs": len(warning_counts),
        "same_nearest_portal_pairs": same,
        "cross_nearest_portal_pairs": cross,
    }

    lines = [
        "# DeathRun Classic26 warning/action pair -> portal segment evidence",
        f"source={source.name}",
        (
            "portal_segment_summary "
            f"pairs={stats['pairs']} "
            f"portal_components={stats['portal_components']} "
            f"portals_with_warning_pairs={stats['portals_with_warning_pairs']} "
            f"same_nearest_portal_pairs={stats['same_nearest_portal_pairs']} "
            f"cross_nearest_portal_pairs={stats['cross_nearest_portal_pairs']}"
        ),
        "# Warning nearest-gate assignment is a spatial partition only.",
        "# It does NOT establish checkpoint number, checkpoint order, respawn point, start or finish.",
        "",
    ]

    grouped: dict[int, list[PairPortalAssignment]] = defaultdict(list)
    for item in assignments:
        if item.warning_portal_id is not None:
            grouped[item.warning_portal_id].append(item)

    for portal_id, portal in enumerate(portals, 1):
        members = grouped.get(portal_id, [])
        labels = ";".join(item.pair.action.label for item in members) or "none"
        warnings = ";".join(_pos(item.pair.warning) for item in members) or "none"
        lines.append(
            "PORTAL_SEGMENT\t"
            f"portal={portal_id:03d}\t"
            f"bbox={_bbox(portal)}\t"
            f"warning_pairs={len(members)}\t"
            f"labels={labels}\t"
            f"warning_positions={warnings}"
        )

    lines.append("")
    for index, item in enumerate(assignments, 1):
        pair = item.pair
        action_sign = pair.action.sign
        warning_portal = (
            f"{item.warning_portal_id:03d}"
            if item.warning_portal_id is not None
            else "none"
        )
        action_portal = (
            f"{item.action_portal_id:03d}"
            if item.action_portal_id is not None
            else "none"
        )
        warning_distance = (
            f"{item.warning_distance:.2f}"
            if item.warning_distance is not None
            else "none"
        )
        action_distance = (
            f"{item.action_distance:.2f}"
            if item.action_distance is not None
            else "none"
        )
        lines.append(
            "PORTAL_PAIR_ASSIGNMENT\t"
            f"id={index:03d}\t"
            f"warning_pos={_pos(pair.warning)}\t"
            f"action={pair.action.label}\t"
            f"action_pos={_pos(action_sign)}\t"
            f"warning_portal={warning_portal}\t"
            f"warning_distance={warning_distance}\t"
            f"action_portal={action_portal}\t"
            f"action_distance={action_distance}\t"
            f"same_nearest_portal={str(item.warning_portal_id == item.action_portal_id).lower()}"
        )

    return "\n".join(lines) + "\n", stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--pair-distance", type=float, default=30.0)
    parser.add_argument("--min-pairs", type=int, default=0)
    parser.add_argument("--min-portals", type=int, default=0)
    args = parser.parse_args(argv)

    if args.pair_distance <= 0:
        parser.error("--pair-distance must be positive")

    _buttons, signs = parse_sign_probe(args.probe_report)
    warnings = sorted(
        (sign for sign in signs if sign_kind(sign) == "WARNING"),
        key=lambda sign: (sign.x, sign.y, sign.z, sign.text),
    )
    actions: list[ActionEvidence] = build_actions(signs)
    pairs, _unmatched_warnings, _unmatched_actions = build_pair_candidates(
        warnings,
        actions,
        args.pair_distance,
    )

    _points, portal_points = parse_candidate_probe(args.probe_report)
    portals = portal_components(portal_points)
    assignments = assign_pairs(pairs, portals)
    report, stats = render_report(args.probe_report, portals, assignments)

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if stats["pairs"] < args.min_pairs:
        return 2
    if stats["portal_components"] < args.min_portals:
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
