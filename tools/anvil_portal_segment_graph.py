#!/usr/bin/env python3
"""Build an undirected proximity graph between portal trap segments.

Each portal segment is represented by the Runner warning positions assigned by
anvil_portal_segment_evidence. Edge weight is the minimum 3D warning-to-warning
distance between two different portal segments.

The minimum spanning tree (MST) is reported only as a compact geometry summary.
Even when the MST is a path, it does NOT establish Runner travel direction,
checkpoint numbering, start, finish, or respawn points.
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
from anvil_portal_segment_evidence import assign_pairs
from anvil_sign_links import Sign, parse_probe_report as parse_sign_probe, sign_kind
from anvil_topology import Component, portal_components
from anvil_trap_evidence import build_actions, build_pair_candidates


@dataclass(frozen=True)
class SegmentGap:
    left: int
    right: int
    distance: float
    left_warning: Sign
    right_warning: Sign


def _distance(left: Sign, right: Sign) -> float:
    return math.sqrt(
        (left.x - right.x) ** 2
        + (left.y - right.y) ** 2
        + (left.z - right.z) ** 2
    )


def warning_groups(probe_report: Path) -> tuple[list[Component], dict[int, list[Sign]]]:
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
    _points, portal_points = parse_candidate_probe(probe_report)
    portals = portal_components(portal_points)
    assignments = assign_pairs(pairs, portals)

    grouped: dict[int, list[Sign]] = defaultdict(list)
    for assignment in assignments:
        if assignment.warning_portal_id is not None:
            grouped[assignment.warning_portal_id].append(assignment.pair.warning)
    for values in grouped.values():
        values.sort(key=lambda sign: (sign.x, sign.y, sign.z, sign.text))
    return portals, grouped


def build_gaps(
    portal_count: int,
    grouped_warnings: dict[int, list[Sign]],
) -> list[SegmentGap]:
    gaps: list[SegmentGap] = []
    for left in range(1, portal_count + 1):
        for right in range(left + 1, portal_count + 1):
            left_warnings = grouped_warnings.get(left, [])
            right_warnings = grouped_warnings.get(right, [])
            if not left_warnings or not right_warnings:
                continue
            candidates = sorted(
                (
                    (_distance(a, b), a, b)
                    for a in left_warnings
                    for b in right_warnings
                ),
                key=lambda item: (
                    item[0],
                    item[1].x,
                    item[1].y,
                    item[1].z,
                    item[2].x,
                    item[2].y,
                    item[2].z,
                ),
            )
            distance, a, b = candidates[0]
            gaps.append(SegmentGap(left, right, distance, a, b))
    gaps.sort(
        key=lambda gap: (
            gap.distance,
            gap.left,
            gap.right,
            gap.left_warning.x,
            gap.left_warning.y,
            gap.left_warning.z,
            gap.right_warning.x,
            gap.right_warning.y,
            gap.right_warning.z,
        )
    )
    return gaps


def minimum_spanning_tree(
    portal_count: int,
    gaps: Sequence[SegmentGap],
) -> list[SegmentGap]:
    parent = list(range(portal_count + 1))

    def find(node: int) -> int:
        while parent[node] != node:
            parent[node] = parent[parent[node]]
            node = parent[node]
        return node

    def union(left: int, right: int) -> bool:
        a = find(left)
        b = find(right)
        if a == b:
            return False
        parent[b] = a
        return True

    tree: list[SegmentGap] = []
    for gap in gaps:
        if union(gap.left, gap.right):
            tree.append(gap)
            if len(tree) == portal_count - 1:
                break
    return tree


def _pos(sign: Sign) -> str:
    return f"{sign.x},{sign.y},{sign.z}"


def render_report(
    source: Path,
    portal_count: int,
    grouped_warnings: dict[int, list[Sign]],
    gaps: Sequence[SegmentGap],
    tree: Sequence[SegmentGap],
) -> tuple[str, dict[str, int | str]]:
    degrees = Counter()
    for edge in tree:
        degrees[edge.left] += 1
        degrees[edge.right] += 1

    endpoints = sorted(
        node
        for node in range(1, portal_count + 1)
        if degrees[node] == 1
    )
    covered_nodes = {node for edge in tree for node in (edge.left, edge.right)}
    mst_connected = (
        portal_count == 1
        or (len(tree) == portal_count - 1 and len(covered_nodes) == portal_count)
    )
    mst_is_path = (
        mst_connected
        and portal_count >= 2
        and len(endpoints) == 2
        and all(degrees[node] <= 2 for node in range(1, portal_count + 1))
    )

    stats: dict[str, int | str] = {
        "portal_segments": portal_count,
        "segments_with_warnings": sum(bool(grouped_warnings.get(i)) for i in range(1, portal_count + 1)),
        "complete_edges": len(gaps),
        "mst_edges": len(tree),
        "mst_is_path": "true" if mst_is_path else "false",
        "mst_endpoints": ",".join(f"{node:03d}" for node in endpoints) or "none",
    }

    lines = [
        "# DeathRun Classic26 portal-segment warning proximity graph",
        f"source={source.name}",
        (
            "segment_graph_summary "
            f"portal_segments={stats['portal_segments']} "
            f"segments_with_warnings={stats['segments_with_warnings']} "
            f"complete_edges={stats['complete_edges']} "
            f"mst_edges={stats['mst_edges']} "
            f"mst_is_path={stats['mst_is_path']} "
            f"mst_endpoints={stats['mst_endpoints']}"
        ),
        "# Edge weight = nearest Runner-warning distance between two portal segments.",
        "# MST is an undirected geometry summary only.",
        "# Even an MST path does NOT establish route direction, checkpoint numbering, start, finish or respawn.",
        "",
    ]

    for rank, gap in enumerate(gaps, 1):
        lines.append(
            "SEGMENT_GAP\t"
            f"rank={rank:03d}\t"
            f"left={gap.left:03d}\tright={gap.right:03d}\t"
            f"distance={gap.distance:.2f}\t"
            f"left_warning={_pos(gap.left_warning)}\t"
            f"right_warning={_pos(gap.right_warning)}"
        )

    lines.append("")
    for index, gap in enumerate(tree, 1):
        lines.append(
            "MST_EDGE\t"
            f"id={index:03d}\t"
            f"left={gap.left:03d}\tright={gap.right:03d}\t"
            f"distance={gap.distance:.2f}\t"
            f"left_warning={_pos(gap.left_warning)}\t"
            f"right_warning={_pos(gap.right_warning)}"
        )

    return "\n".join(lines) + "\n", stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--min-portals", type=int, default=0)
    parser.add_argument("--require-mst-path", action="store_true")
    args = parser.parse_args(argv)

    portals, grouped = warning_groups(args.probe_report)
    gaps = build_gaps(len(portals), grouped)
    tree = minimum_spanning_tree(len(portals), gaps)
    report, stats = render_report(
        args.probe_report,
        len(portals),
        grouped,
        gaps,
        tree,
    )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if len(portals) < args.min_portals:
        return 2
    if args.require_mst_path and stats["mst_is_path"] != "true":
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
