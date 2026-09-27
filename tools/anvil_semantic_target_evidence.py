#!/usr/bin/env python3
"""Correlate semantic To Bee trap labels with preserved block-material components.

This pass is deliberately conservative. It does not infer server-side behavior;
it only asks whether materials named by surviving warning/action text form
physical components near the corresponding Runner-side anchor in the archive.
"""

from __future__ import annotations

import argparse
import math
import sys
from collections import Counter, deque
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Sequence

from anvil_portal_spawn_surface_evidence import scan_blocks


@dataclass(frozen=True)
class TargetCase:
    id: str
    label: str
    anchor: tuple[int, int, int]
    families: tuple[str, ...]


CASES = (
    TargetCase("001", "Melt the ice", (-62, 25, 24), ("ICE",)),
    TargetCase("002", "Remove dark wood A", (-57, 25, -14), ("WOODLIKE",)),
    TargetCase("006", "Remove red blocks A", (-25, 18, -34), ("RED_STRUCTURAL",)),
    TargetCase("012", "Remove red blocks B", (7, 25, -36), ("RED_COLOR",)),
    TargetCase("014", "Remove dark wood B", (24, 35, 79), ("WOODLIKE",)),
    TargetCase("015", "Set the coals on fire", (32, 45, 11), ("COAL",)),
    TargetCase("018", "Remove red blocks C", (37, 34, 85), ("RED_COLOR",)),
    TargetCase("021", "Remove red blocks D", (93, 25, 56), ("RED_COLOR",)),
    TargetCase("sea-lantern", "Remove Sea Lanterns", (72, 25, 76), ("SEA_LANTERN",)),
)


RED_STRUCTURAL = {
    "minecraft:red_terracotta",
    "minecraft:red_wool",
    "minecraft:red_concrete",
    "minecraft:red_concrete_powder",
    "minecraft:red_stained_glass",
    "minecraft:red_glazed_terracotta",
}

WOODLIKE_SUFFIXES = (
    "_planks", "_log", "_wood", "_stem", "_hyphae",
    "_slab", "_stairs", "_fence", "_fence_gate", "_trapdoor",
)


def semantic_family(name: str) -> str | None:
    normalized = name.lower()
    if normalized in {"minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice", "minecraft:frosted_ice"}:
        return "ICE"
    if normalized in RED_STRUCTURAL:
        return "RED_STRUCTURAL"
    if normalized.startswith("minecraft:") and normalized.endswith(WOODLIKE_SUFFIXES):
        return "WOODLIKE"
    if normalized == "minecraft:coal_block":
        return "COAL"
    if normalized == "minecraft:sea_lantern":
        return "SEA_LANTERN"
    return None


def connected_components(points: set[tuple[int, int, int]]) -> list[set[tuple[int, int, int]]]:
    remaining = set(points)
    result: list[set[tuple[int, int, int]]] = []
    offsets = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))

    while remaining:
        seed = remaining.pop()
        component = {seed}
        queue = deque([seed])
        while queue:
            x, y, z = queue.popleft()
            for dx, dy, dz in offsets:
                neighbor = (x + dx, y + dy, z + dz)
                if neighbor not in remaining:
                    continue
                remaining.remove(neighbor)
                component.add(neighbor)
                queue.append(neighbor)
        result.append(component)

    result.sort(key=lambda component: (-len(component), min(component)))
    return result


def _inside_case(pos: tuple[int, int, int], case: TargetCase, radius: int, vertical_radius: int) -> bool:
    x, y, z = pos
    ax, ay, az = case.anchor
    return abs(x - ax) <= radius and abs(z - az) <= radius and abs(y - ay) <= vertical_radius


def analyze_case(
    blocks: Mapping[tuple[int, int, int], str],
    case: TargetCase,
    radius: int,
    vertical_radius: int,
) -> list[tuple[str, set[tuple[int, int, int]], float, Counter[str]]]:
    by_family: dict[str, dict[tuple[int, int, int], str]] = {}
    for pos, material in blocks.items():
        if not _inside_case(pos, case, radius, vertical_radius):
            continue
        family = semantic_family(material)
        if family not in case.families:
            continue
        by_family.setdefault(family, {})[pos] = material

    result: list[tuple[str, set[tuple[int, int, int]], float, Counter[str]]] = []
    ax, ay, az = case.anchor
    for family, entries in by_family.items():
        for component in connected_components(set(entries)):
            distance = min(
                math.sqrt((x - ax) ** 2 + (y - ay) ** 2 + (z - az) ** 2)
                for x, y, z in component
            )
            materials = Counter(entries[pos] for pos in component)
            result.append((family, component, distance, materials))

    result.sort(key=lambda item: (item[2], -len(item[1]), item[0], min(item[1])))
    return result


def _bbox(component: set[tuple[int, int, int]]) -> str:
    xs = [pos[0] for pos in component]
    ys = [pos[1] for pos in component]
    zs = [pos[2] for pos in component]
    return f"{min(xs)},{min(ys)},{min(zs)}:{max(xs)},{max(ys)},{max(zs)}"


def render_report(
    world_name: str,
    blocks: Mapping[tuple[int, int, int], str],
    radius: int,
    vertical_radius: int,
    top: int,
) -> tuple[str, dict[str, int]]:
    analyses = [(case, analyze_case(blocks, case, radius, vertical_radius)) for case in CASES]
    cases_with_matches = sum(bool(items) for _, items in analyses)
    total_matching = sum(sum(len(component) for _, component, _, _ in items) for _, items in analyses)
    lines = [
        "# DeathRun Classic26 semantic trap-target material evidence",
        f"source={world_name}",
        (
            "semantic_target_summary "
            f"cases={len(CASES)} cases_with_matches={cases_with_matches} "
            f"total_matching_blocks={total_matching} radius={radius} vertical_radius={vertical_radius}"
        ),
        "# Components are physical archive geometry only; they are not automatic trap targets.",
        "# RED_STRUCTURAL is a whitelist and excludes flowers/redstone/decorative non-target noise.",
        "",
    ]

    for case, items in analyses:
        total = sum(len(component) for _, component, _, _ in items)
        ax, ay, az = case.anchor
        lines.append(
            "TARGET_CASE\t"
            f"id={case.id}\tlabel={case.label}\tanchor={ax},{ay},{az}\t"
            f"families={';'.join(case.families)}\tmatching_blocks={total}\tcomponents={len(items)}"
        )
        for rank, (family, component, distance, materials) in enumerate(items[:top], 1):
            material_text = ";".join(f"{name}:{count}" for name, count in sorted(materials.items()))
            positions_text = ";".join(f"{x},{y},{z}" for x, y, z in sorted(component)) if len(component) <= 80 else "omitted"
            lines.append(
                "TARGET_COMPONENT\t"
                f"case={case.id}\trank={rank}\tfamily={family}\tsize={len(component)}\t"
                f"min_distance={distance:.2f}\tbbox={_bbox(component)}\tmaterials={material_text}\t"
                f"positions={positions_text}"
            )

    return "\n".join(lines) + "\n", {
        "cases": len(CASES),
        "cases_with_matches": cases_with_matches,
        "total_matching_blocks": total_matching,
    }


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world_dir", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--radius", type=int, default=18)
    parser.add_argument("--vertical-radius", type=int, default=8)
    parser.add_argument("--top", type=int, default=8)
    parser.add_argument("--min-cases-with-matches", type=int, default=0)
    args = parser.parse_args(argv)
    if args.radius < 1 or args.vertical_radius < 0 or args.top < 1:
        parser.error("invalid scan geometry")

    bounds = []
    for case in CASES:
        x, y, z = case.anchor
        bounds.append((
            x - args.radius, y - args.vertical_radius, z - args.radius,
            x + args.radius, y + args.vertical_radius, z + args.radius,
        ))

    blocks, scan_stats = scan_blocks(args.world_dir, bounds)
    report, stats = render_report(args.world_dir.name, blocks, args.radius, args.vertical_radius, args.top)
    report = report.replace(
        "semantic_target_summary ",
        f"scan_stats regions={scan_stats.regions} chunks={scan_stats.chunks} captured_blocks={scan_stats.captured_blocks}\nsemantic_target_summary ",
        1,
    )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    return 0 if stats["cases_with_matches"] >= args.min_cases_with_matches else 2


if __name__ == "__main__":
    raise SystemExit(main())
