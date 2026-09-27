#!/usr/bin/env python3
"""Measure preserved Runner-warning sign fronts across the portal-segment MST.

The report compares each warning sign's horizontal front vector with the
warning-to-warning vector used by an MST edge. This is orientation geometry
only. It does not assume that warning signs necessarily face the Runner's
incoming travel direction.
"""

from __future__ import annotations

import argparse
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from anvil_portal_segment_graph import (
    SegmentGap,
    build_gaps,
    minimum_spanning_tree,
    warning_groups,
)
from anvil_sign_links import Sign, parse_probe_report, sign_front_vector


@dataclass(frozen=True)
class FacingMeasurement:
    edge_index: int
    left: int
    right: int
    left_warning: Sign
    right_warning: Sign
    left_front: tuple[float, float] | None
    right_front: tuple[float, float] | None
    left_dot_to_right: float | None
    right_dot_to_left: float | None


def _horizontal_unit(left: Sign, right: Sign) -> tuple[float, float] | None:
    dx = right.x - left.x
    dz = right.z - left.z
    length = math.hypot(dx, dz)
    if length == 0:
        return None
    return dx / length, dz / length


def _dot(front: tuple[float, float] | None, direction: tuple[float, float] | None) -> float | None:
    if front is None or direction is None:
        return None
    return front[0] * direction[0] + front[1] * direction[1]


def measure_tree(tree: Sequence[SegmentGap]) -> list[FacingMeasurement]:
    result: list[FacingMeasurement] = []
    for index, edge in enumerate(tree, 1):
        left_front = sign_front_vector(edge.left_warning)
        right_front = sign_front_vector(edge.right_warning)
        left_to_right = _horizontal_unit(edge.left_warning, edge.right_warning)
        right_to_left = (
            (-left_to_right[0], -left_to_right[1])
            if left_to_right is not None
            else None
        )
        result.append(
            FacingMeasurement(
                edge_index=index,
                left=edge.left,
                right=edge.right,
                left_warning=edge.left_warning,
                right_warning=edge.right_warning,
                left_front=left_front,
                right_front=right_front,
                left_dot_to_right=_dot(left_front, left_to_right),
                right_dot_to_left=_dot(right_front, right_to_left),
            )
        )
    return result


def _pos(sign: Sign) -> str:
    return f"{sign.x},{sign.y},{sign.z}"


def _front(value: tuple[float, float] | None) -> str:
    if value is None:
        return "unknown"
    return f"{value[0]:.3f},{value[1]:.3f}"


def _scalar(value: float | None) -> str:
    return "unknown" if value is None else f"{value:.3f}"


def render_report(
    source: Path,
    measurements: Sequence[FacingMeasurement],
) -> tuple[str, dict[str, int]]:
    sides = []
    for item in measurements:
        sides.extend((item.left_dot_to_right, item.right_dot_to_left))
    known = [value for value in sides if value is not None]
    toward = sum(value > 0.25 for value in known)
    away = sum(value < -0.25 for value in known)
    lateral = sum(-0.25 <= value <= 0.25 for value in known)
    stats = {
        "mst_edges": len(measurements),
        "edge_sides": len(measurements) * 2,
        "known_front_sides": len(known),
        "toward_neighbor": toward,
        "away_from_neighbor": away,
        "roughly_lateral": lateral,
    }

    lines = [
        "# DeathRun Classic26 Runner-warning facing evidence on portal MST edges",
        f"source={source.name}",
        (
            "warning_facing_summary "
            f"mst_edges={stats['mst_edges']} "
            f"edge_sides={stats['edge_sides']} "
            f"known_front_sides={stats['known_front_sides']} "
            f"toward_neighbor={stats['toward_neighbor']} "
            f"away_from_neighbor={stats['away_from_neighbor']} "
            f"roughly_lateral={stats['roughly_lateral']}"
        ),
        "# dot>0 means the sign front points generally toward the paired warning on the adjacent segment.",
        "# dot<0 means it points generally away from that adjacent-segment warning.",
        "# This does NOT by itself establish Runner travel direction or start/finish.",
        "",
    ]

    for item in measurements:
        lines.append(
            "MST_WARNING_FACING\t"
            f"edge={item.edge_index:03d}\t"
            f"left={item.left:03d}\tright={item.right:03d}\t"
            f"left_warning={_pos(item.left_warning)}\t"
            f"left_front={_front(item.left_front)}\t"
            f"left_dot_to_right={_scalar(item.left_dot_to_right)}\t"
            f"right_warning={_pos(item.right_warning)}\t"
            f"right_front={_front(item.right_front)}\t"
            f"right_dot_to_left={_scalar(item.right_dot_to_left)}"
        )

    return "\n".join(lines) + "\n", stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--min-known-front-sides", type=int, default=0)
    args = parser.parse_args(argv)

    portals, grouped = warning_groups(args.probe_report)
    gaps = build_gaps(len(portals), grouped)
    tree = minimum_spanning_tree(len(portals), gaps)
    measurements = measure_tree(tree)
    report, stats = render_report(args.probe_report, measurements)

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if stats["known_front_sides"] < args.min_known_front_sides:
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
