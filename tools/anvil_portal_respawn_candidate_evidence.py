#!/usr/bin/env python3
"""Combine route-side geometry with safe portal-adjacent standing surfaces.

The selected side is evidence-driven:
- START_ENDPOINT: candidate external/start side;
- FINISH_ENDPOINT: candidate external/finish side;
- OPPOSITE_SIDES interior gate: candidate outgoing side after crossing;
- ambiguous gates: no side selected.

Candidates remain geometry-only. They are suitable for playtest review but are
NOT claimed as the original Hive checkpoint respawn coordinates.
"""

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from anvil_cluster import parse_probe_report
from anvil_portal_route_side_evidence import PortalRouteSide, build_route_sides
from anvil_portal_spawn_surface_evidence import (
    StandingCandidate,
    _portal_bounds,
    scan_blocks,
    standing_candidates,
)
from anvil_topology import portal_components


@dataclass(frozen=True)
class RespawnCandidateEvidence:
    portal_id: int
    classification: str
    selected_side: int | None
    purpose: str
    candidates: tuple[StandingCandidate, ...]


def _weak_support(name: str) -> bool:
    return (
        name == "minecraft:iron_bars"
        or name.endswith("_leaves")
        or name.endswith("_fence")
        or name.endswith("_fence_gate")
        or name.endswith("_wall")
    )


def _rank(candidates: Sequence[StandingCandidate]) -> tuple[StandingCandidate, ...]:
    return tuple(
        sorted(
            candidates,
            key=lambda item: (
                _weak_support(item.support),
                item.normal_distance,
                item.distance_to_center,
                item.x,
                item.y,
                item.z,
            ),
        )
    )


def selected_side(item: PortalRouteSide) -> tuple[int | None, str]:
    if item.classification == "START_ENDPOINT":
        return item.external_side_candidate, "START_SIDE"
    if item.classification == "FINISH_ENDPOINT":
        return item.external_side_candidate, "FINISH_SIDE"
    if item.classification == "OPPOSITE_SIDES":
        return item.next_side, "CHECKPOINT_OUTGOING_SIDE"
    return None, "UNRESOLVED"


def build_evidence(
    world_dir: Path,
    probe_report: Path,
    dot_threshold: float,
    max_normal_distance: int,
    lateral_padding: int,
) -> list[RespawnCandidateEvidence]:
    _interactive, portal_points = parse_probe_report(probe_report)
    portals = portal_components(portal_points)
    bounds = _portal_bounds(portals, max_normal_distance, lateral_padding)
    blocks, _stats = scan_blocks(world_dir, bounds)
    standing = {
        index: standing_candidates(
            index,
            component,
            blocks,
            max_normal_distance,
            lateral_padding,
        )
        for index, component in enumerate(portals, 1)
    }

    _path, route_sides = build_route_sides(probe_report, dot_threshold)
    result: list[RespawnCandidateEvidence] = []
    for item in route_sides:
        side, purpose = selected_side(item)
        available: Sequence[StandingCandidate] = ()
        if side == -1:
            available = standing[item.portal_id].negative
        elif side == 1:
            available = standing[item.portal_id].positive
        result.append(
            RespawnCandidateEvidence(
                portal_id=item.portal_id,
                classification=item.classification,
                selected_side=side,
                purpose=purpose,
                candidates=_rank(available),
            )
        )
    return result


def _side(value: int | None) -> str:
    return {None: "none", -1: "negative", 1: "positive"}.get(value, "on-plane")


def _candidate(item: StandingCandidate) -> str:
    quality = "weak" if _weak_support(item.support) else "floor"
    return (
        f"{item.x},{item.y},{item.z}"
        f"[d={item.normal_distance},support={item.support},quality={quality},center={item.distance_to_center:.2f}]"
    )


def render_report(
    source: Path,
    evidence: Sequence[RespawnCandidateEvidence],
    top: int,
) -> tuple[str, dict[str, int]]:
    selected = [item for item in evidence if item.selected_side in (-1, 1)]
    with_candidates = [item for item in selected if item.candidates]
    unresolved = [item for item in evidence if item.selected_side is None]
    strong_primary = [
        item for item in with_candidates
        if not _weak_support(item.candidates[0].support)
    ]
    stats = {
        "portals": len(evidence),
        "selected_portals": len(selected),
        "selected_with_candidates": len(with_candidates),
        "unresolved_portals": len(unresolved),
        "strong_primary_candidates": len(strong_primary),
    }

    lines = [
        "# DeathRun Classic26 route-side standing candidates",
        f"source={source.name}",
        (
            "respawn_candidate_summary "
            f"portals={stats['portals']} "
            f"selected_portals={stats['selected_portals']} "
            f"selected_with_candidates={stats['selected_with_candidates']} "
            f"unresolved_portals={stats['unresolved_portals']} "
            f"strong_primary_candidates={stats['strong_primary_candidates']}"
        ),
        "# Selected side combines the route-direction hypothesis with portal crossing geometry.",
        "# Candidate coordinates are playtest-safe geometry, NOT recovered original Hive respawn coordinates.",
        "",
    ]

    for item in evidence:
        candidates = ";".join(_candidate(value) for value in item.candidates[:top]) or "none"
        primary = _candidate(item.candidates[0]) if item.candidates else "none"
        lines.append(
            "RESPAWN_CANDIDATE\t"
            f"portal={item.portal_id:03d}\t"
            f"classification={item.classification}\t"
            f"purpose={item.purpose}\t"
            f"selected_side={_side(item.selected_side)}\t"
            f"candidate_count={len(item.candidates)}\t"
            f"primary={primary}\t"
            f"top={candidates}"
        )

    return "\n".join(lines) + "\n", stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world_dir", type=Path)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--dot-threshold", type=float, default=0.25)
    parser.add_argument("--max-normal-distance", type=int, default=6)
    parser.add_argument("--lateral-padding", type=int, default=2)
    parser.add_argument("--top", type=int, default=5)
    parser.add_argument("--min-selected-portals", type=int, default=0)
    parser.add_argument("--min-selected-with-candidates", type=int, default=0)
    args = parser.parse_args(argv)

    evidence = build_evidence(
        args.world_dir,
        args.probe_report,
        args.dot_threshold,
        args.max_normal_distance,
        args.lateral_padding,
    )
    report, stats = render_report(args.probe_report, evidence, args.top)

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if stats["selected_portals"] < args.min_selected_portals:
        return 2
    if stats["selected_with_candidates"] < args.min_selected_with_candidates:
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
