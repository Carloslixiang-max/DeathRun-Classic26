#!/usr/bin/env python3
"""Cluster DeathRun Anvil probe candidates into reviewable spatial evidence groups.

This is a research aid, not a trap classifier. Nearby interaction evidence is
grouped for human review without assigning a DeathRun trap type.
"""

from __future__ import annotations

import argparse
import math
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


INTERACTIVE = {"BUTTON", "PRESSURE_PLATE", "COMMAND_BLOCK", "COMMAND_ENTITY"}


@dataclass(frozen=True)
class Candidate:
    category: str
    name: str
    x: int
    y: int
    z: int
    source: str


@dataclass(frozen=True)
class EvidenceCluster:
    members: tuple[Candidate, ...]
    priority: str
    score: int
    reasons: tuple[str, ...]
    nearest_portal: float | None

    @property
    def counts(self) -> Counter[str]:
        return Counter(member.category for member in self.members)

    @property
    def center(self) -> tuple[float, float, float]:
        size = len(self.members)
        return (
            sum(member.x for member in self.members) / size,
            sum(member.y for member in self.members) / size,
            sum(member.z for member in self.members) / size,
        )

    @property
    def bbox(self) -> tuple[int, int, int, int, int, int]:
        return (
            min(member.x for member in self.members),
            min(member.y for member in self.members),
            min(member.z for member in self.members),
            max(member.x for member in self.members),
            max(member.y for member in self.members),
            max(member.z for member in self.members),
        )


def parse_probe_report(path: Path) -> tuple[list[Candidate], list[Candidate]]:
    interactive: list[Candidate] = []
    portals: list[Candidate] = []

    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw or raw.startswith("#"):
            continue
        fields = raw.split("\t")
        if not fields:
            continue

        try:
            if fields[0] == "BLOCK" and len(fields) >= 6:
                category = fields[1]
                candidate = Candidate(
                    category=category,
                    name=fields[2],
                    x=int(fields[3]),
                    y=int(fields[4]),
                    z=int(fields[5]),
                    source=path.name,
                )
                if category == "PORTAL":
                    portals.append(candidate)
                elif category in INTERACTIVE:
                    interactive.append(candidate)
            elif fields[0] == "COMMAND" and len(fields) >= 5:
                interactive.append(
                    Candidate(
                        category="COMMAND_ENTITY",
                        name=fields[1],
                        x=int(fields[2]),
                        y=int(fields[3]),
                        z=int(fields[4]),
                        source=path.name,
                    )
                )
        except ValueError:
            continue

    interactive.sort(key=lambda item: (item.x, item.y, item.z, item.category, item.name))
    portals.sort(key=lambda item: (item.x, item.y, item.z, item.name))
    return interactive, portals


class UnionFind:
    def __init__(self, size: int):
        self.parent = list(range(size))
        self.rank = [0] * size

    def find(self, value: int) -> int:
        parent = self.parent[value]
        if parent != value:
            self.parent[value] = self.find(parent)
        return self.parent[value]

    def union(self, left: int, right: int) -> None:
        root_left = self.find(left)
        root_right = self.find(right)
        if root_left == root_right:
            return
        if self.rank[root_left] < self.rank[root_right]:
            root_left, root_right = root_right, root_left
        self.parent[root_right] = root_left
        if self.rank[root_left] == self.rank[root_right]:
            self.rank[root_left] += 1


def _validate_radii(horizontal_radius: float, vertical_radius: float) -> None:
    if horizontal_radius <= 0 or vertical_radius < 0:
        raise ValueError("radii must be positive (vertical may be zero)")


def _bucket_key(point: Candidate, cell_xz: float, cell_y: float) -> tuple[int, int, int]:
    return (
        math.floor(point.x / cell_xz),
        math.floor(point.y / cell_y),
        math.floor(point.z / cell_xz),
    )


def _neighbor_indexes(
    points: Sequence[Candidate],
    horizontal_radius: float,
    vertical_radius: float,
) -> list[set[int]]:
    _validate_radii(horizontal_radius, vertical_radius)
    cell_xz = max(1.0, horizontal_radius)
    cell_y = max(1.0, vertical_radius if vertical_radius > 0 else 1.0)
    buckets: dict[tuple[int, int, int], list[int]] = {}

    for index, point in enumerate(points):
        buckets.setdefault(_bucket_key(point, cell_xz, cell_y), []).append(index)

    horizontal_sq = horizontal_radius * horizontal_radius
    result: list[set[int]] = []
    for index, point in enumerate(points):
        bx, by, bz = _bucket_key(point, cell_xz, cell_y)
        nearby: set[int] = {index}
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    for other_index in buckets.get((bx + dx, by + dy, bz + dz), ()):
                        other = points[other_index]
                        if abs(point.y - other.y) > vertical_radius:
                            continue
                        if (point.x - other.x) ** 2 + (point.z - other.z) ** 2 <= horizontal_sq:
                            nearby.add(other_index)
        result.append(nearby)
    return result


def spatial_groups(
    points: Sequence[Candidate],
    horizontal_radius: float,
    vertical_radius: float,
) -> list[tuple[Candidate, ...]]:
    """Connected-component grouping; useful for topology but may chain-grow."""
    if not points:
        return []

    neighbors = _neighbor_indexes(points, horizontal_radius, vertical_radius)
    union = UnionFind(len(points))
    for index, nearby in enumerate(neighbors):
        for other_index in nearby:
            union.union(index, other_index)

    grouped: dict[int, list[Candidate]] = {}
    for index, point in enumerate(points):
        grouped.setdefault(union.find(index), []).append(point)

    groups = [
        tuple(sorted(members, key=lambda item: (item.x, item.y, item.z, item.category, item.name)))
        for members in grouped.values()
    ]
    groups.sort(
        key=lambda members: (
            min(item.x for item in members),
            min(item.z for item in members),
            min(item.y for item in members),
            -len(members),
        )
    )
    return groups


def local_groups(
    points: Sequence[Candidate],
    horizontal_radius: float,
    vertical_radius: float,
) -> list[tuple[Candidate, ...]]:
    """Bounded local neighborhoods that avoid single-linkage chain growth.

    Every group is formed from one seed and only points directly within that
    seed's configured radius. A-near-B-near-C therefore cannot create an
    arbitrarily long group when A is far from C.
    """
    if not points:
        return []

    neighbors = _neighbor_indexes(points, horizontal_radius, vertical_radius)
    remaining = set(range(len(points)))
    groups: list[tuple[Candidate, ...]] = []

    while remaining:
        seed = min(
            remaining,
            key=lambda index: (
                -len(neighbors[index] & remaining),
                points[index].x,
                points[index].z,
                points[index].y,
                points[index].category,
                points[index].name,
            ),
        )
        member_indexes = neighbors[seed] & remaining
        members = tuple(
            sorted(
                (points[index] for index in member_indexes),
                key=lambda item: (item.x, item.y, item.z, item.category, item.name),
            )
        )
        groups.append(members)
        remaining.difference_update(member_indexes)

    groups.sort(
        key=lambda members: (
            min(item.x for item in members),
            min(item.z for item in members),
            min(item.y for item in members),
            -len(members),
        )
    )
    return groups


def nearest_portal_distance(
    members: Sequence[Candidate],
    portals: Sequence[Candidate],
) -> float | None:
    if not portals:
        return None
    cx = sum(item.x for item in members) / len(members)
    cy = sum(item.y for item in members) / len(members)
    cz = sum(item.z for item in members) / len(members)
    return min(
        math.sqrt((cx - portal.x) ** 2 + (cy - portal.y) ** 2 + (cz - portal.z) ** 2)
        for portal in portals
    )


def review_priority(members: Sequence[Candidate]) -> tuple[str, int, tuple[str, ...]]:
    counts = Counter(member.category for member in members)
    reasons: list[str] = []
    score = 0

    command_count = counts["COMMAND_BLOCK"] + counts["COMMAND_ENTITY"]
    if command_count:
        score += 6
        reasons.append("command-evidence")

    if counts["BUTTON"] and counts["PRESSURE_PLATE"]:
        score += 5
        reasons.append("mixed-inputs")
    elif counts["PRESSURE_PLATE"]:
        score += 3
        reasons.append("pressure-plate")

    if len(members) >= 6:
        score += 4
        reasons.append("dense-group")
    elif len(members) >= 3:
        score += 2
        reasons.append("multi-block")
    elif len(members) == 2:
        score += 1
        reasons.append("pair")

    if counts["BUTTON"] >= 2:
        score += 1
        reasons.append("multi-button")

    if score >= 6:
        priority = "HIGH"
    elif score >= 2:
        priority = "MEDIUM"
    else:
        priority = "LOW"

    if not reasons:
        reasons.append("isolated-candidate")
    return priority, score, tuple(reasons)


def build_clusters(
    points: Sequence[Candidate],
    portals: Sequence[Candidate],
    horizontal_radius: float,
    vertical_radius: float,
    mode: str = "local",
) -> list[EvidenceCluster]:
    if mode == "connected":
        groups = spatial_groups(points, horizontal_radius, vertical_radius)
    elif mode == "local":
        groups = local_groups(points, horizontal_radius, vertical_radius)
    else:
        raise ValueError(f"unsupported grouping mode: {mode}")

    clusters: list[EvidenceCluster] = []
    for members in groups:
        priority, score, reasons = review_priority(members)
        clusters.append(
            EvidenceCluster(
                members=members,
                priority=priority,
                score=score,
                reasons=reasons,
                nearest_portal=nearest_portal_distance(members, portals),
            )
        )

    priority_rank = {"HIGH": 0, "MEDIUM": 1, "LOW": 2}
    clusters.sort(
        key=lambda cluster: (
            priority_rank[cluster.priority],
            -cluster.score,
            -len(cluster.members),
            cluster.center[0],
            cluster.center[2],
            cluster.center[1],
        )
    )
    return clusters


def _fmt_float(value: float) -> str:
    return f"{value:.2f}"


def render_report(
    source: Path,
    points: Sequence[Candidate],
    portals: Sequence[Candidate],
    clusters: Sequence[EvidenceCluster],
    horizontal_radius: float,
    vertical_radius: float,
    members_limit: int,
    mode: str = "local",
) -> str:
    priorities = Counter(cluster.priority for cluster in clusters)
    categories = Counter(point.category for point in points)
    largest = max((len(cluster.members) for cluster in clusters), default=0)

    lines = [
        "# DeathRun Classic26 spatial evidence clusters",
        f"source={source.name}",
        (
            "summary "
            f"interactive_points={len(points)} portals={len(portals)} "
            f"mode={mode} clusters={len(clusters)} high={priorities['HIGH']} "
            f"medium={priorities['MEDIUM']} low={priorities['LOW']} "
            f"largest={largest} horizontal_radius={horizontal_radius:g} "
            f"vertical_radius={vertical_radius:g}"
        ),
        "categories " + " ".join(f"{key}={categories[key]}" for key in sorted(categories)),
        "# Priority is structural review priority only; it is NOT a trap-type classification.",
        "",
    ]

    for index, cluster in enumerate(clusters, 1):
        cx, cy, cz = cluster.center
        min_x, min_y, min_z, max_x, max_y, max_z = cluster.bbox
        counts = ",".join(
            f"{key}:{value}" for key, value in sorted(cluster.counts.items())
        )
        portal = "none" if cluster.nearest_portal is None else _fmt_float(cluster.nearest_portal)
        lines.append(
            "CLUSTER\t"
            f"id={index:03d}\tpriority={cluster.priority}\tscore={cluster.score}\t"
            f"size={len(cluster.members)}\t"
            f"center={_fmt_float(cx)},{_fmt_float(cy)},{_fmt_float(cz)}\t"
            f"bbox={min_x},{min_y},{min_z}:{max_x},{max_y},{max_z}\t"
            f"counts={counts}\tnearest_portal={portal}\t"
            f"reasons={','.join(cluster.reasons)}"
        )
        if members_limit > 0:
            for member in cluster.members[:members_limit]:
                lines.append(
                    "MEMBER\t"
                    f"cluster={index:03d}\t{member.category}\t{member.name}\t"
                    f"{member.x}\t{member.y}\t{member.z}"
                )
            omitted = len(cluster.members) - members_limit
            if omitted > 0:
                lines.append(f"MEMBER\tcluster={index:03d}\tomitted={omitted}")

    return "\n".join(lines) + "\n"


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--radius", type=float, default=4.0)
    parser.add_argument("--vertical-radius", type=float, default=3.0)
    parser.add_argument(
        "--mode",
        choices=("connected", "local"),
        default="local",
        help="connected preserves topology; local prevents chain-grown review groups",
    )
    parser.add_argument("--members-limit", type=int, default=0)
    parser.add_argument("--min-points", type=int, default=0)
    parser.add_argument("--min-clusters", type=int, default=0)
    args = parser.parse_args(argv)

    points, portals = parse_probe_report(args.probe_report)
    clusters = build_clusters(
        points,
        portals,
        args.radius,
        args.vertical_radius,
        mode=args.mode,
    )
    report = render_report(
        args.probe_report,
        points,
        portals,
        clusters,
        args.radius,
        args.vertical_radius,
        max(0, args.members_limit),
        mode=args.mode,
    )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if len(points) < args.min_points:
        print(
            f"anvil_cluster: expected at least {args.min_points} interactive points, got {len(points)}",
            file=sys.stderr,
        )
        return 2
    if len(clusters) < args.min_clusters:
        print(
            f"anvil_cluster: expected at least {args.min_clusters} clusters, got {len(clusters)}",
            file=sys.stderr,
        )
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
