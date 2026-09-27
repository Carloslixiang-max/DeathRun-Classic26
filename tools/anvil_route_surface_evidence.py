#!/usr/bin/env python3
"""Recover walkable route-surface components near unresolved To Bee trap warnings.

Only raw physical geometry is reported. A standing surface near a warning does
not prove the original server-side trap target; the report is a review queue
for Flood, Floor Fall, Minefield, Fire Snake and Random Wall reconstruction.
"""

from __future__ import annotations

import argparse
import math
import sys
from collections import Counter, deque
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Sequence

from anvil_portal_spawn_surface_evidence import AIR, _is_support_block, scan_blocks


@dataclass(frozen=True)
class SurfaceCase:
    id: str
    label: str
    warning: tuple[int, int, int]


CASES = (
    SurfaceCase("003", "Summon random wall A", (-55, 25, 0)),
    SurfaceCase("004", "Flood floor A", (-46, 25, 20)),
    SurfaceCase("008", "Summon random wall B", (-4, 25, 31)),
    SurfaceCase("009", "Minefield", (6, 25, 46)),
    SurfaceCase("010", "Fire snake A", (6, 25, 60)),
    SurfaceCase("013", "Summon random wall C", (22, 25, -33)),
    SurfaceCase("016", "Floor fall A", (33, 25, -28)),
    SurfaceCase("017", "Flood floor B", (36, 45, 21)),
    SurfaceCase("019", "Floor fall B", (50, 25, 53)),
    SurfaceCase("020", "Fire snake B", (70, 25, 53)),
)


def standing_surfaces(
    blocks: Mapping[tuple[int, int, int], str],
    case: SurfaceCase,
    radius: int,
    vertical: int,
) -> dict[tuple[int, int, int], str]:
    ax, ay, az = case.warning
    result: dict[tuple[int, int, int], str] = {}
    for x in range(ax - radius, ax + radius + 1):
        for z in range(az - radius, az + radius + 1):
            for feet_y in range(ay - vertical, ay + vertical + 1):
                feet = blocks.get((x, feet_y, z), "minecraft:air")
                head = blocks.get((x, feet_y + 1, z), "minecraft:air")
                support = blocks.get((x, feet_y - 1, z), "minecraft:air")
                if feet not in AIR or head not in AIR or not _is_support_block(support):
                    continue
                result[(x, feet_y - 1, z)] = support
    return result


def components_by_material(
    surfaces: Mapping[tuple[int, int, int], str]
) -> list[tuple[str, set[tuple[int, int, int]]]]:
    remaining = set(surfaces)
    offsets = ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1))
    result: list[tuple[str, set[tuple[int, int, int]]]] = []
    while remaining:
        seed = min(remaining)
        remaining.remove(seed)
        material = surfaces[seed]
        component = {seed}
        queue = deque([seed])
        while queue:
            x, y, z = queue.popleft()
            for dx, dy, dz in offsets:
                neighbor = (x + dx, y + dy, z + dz)
                if neighbor not in remaining or surfaces.get(neighbor) != material:
                    continue
                remaining.remove(neighbor)
                component.add(neighbor)
                queue.append(neighbor)
        result.append((material, component))
    result.sort(key=lambda item: (-len(item[1]), item[0], min(item[1])))
    return result


def _bbox(points: set[tuple[int, int, int]]) -> str:
    xs=[p[0] for p in points]; ys=[p[1] for p in points]; zs=[p[2] for p in points]
    return f"{min(xs)},{min(ys)},{min(zs)}:{max(xs)},{max(ys)},{max(zs)}"


def render_case(
    case: SurfaceCase,
    surfaces: Mapping[tuple[int, int, int], str],
    top: int,
) -> list[str]:
    ax, ay, az = case.warning
    components = components_by_material(surfaces)
    lines=[
        "SURFACE_CASE\t"
        f"id={case.id}\tlabel={case.label}\twarning={ax},{ay},{az}\t"
        f"surface_cells={len(surfaces)}\tcomponents={len(components)}"
    ]
    for rank,(material,component) in enumerate(components[:top],1):
        distance=min(math.sqrt((x-ax)**2+(y-(ay-1))**2+(z-az)**2) for x,y,z in component)
        positions=";".join(f"{x},{y},{z}" for x,y,z in sorted(component)) if len(component)<=120 else "omitted"
        lines.append(
            "SURFACE_COMPONENT\t"
            f"case={case.id}\trank={rank}\tmaterial={material}\tsize={len(component)}\t"
            f"min_distance={distance:.2f}\tbbox={_bbox(component)}\tpositions={positions}"
        )
    return lines


def main(argv: Sequence[str] | None=None) -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world_dir",type=Path)
    parser.add_argument("--output",type=Path)
    parser.add_argument("--radius",type=int,default=10)
    parser.add_argument("--vertical",type=int,default=3)
    parser.add_argument("--top",type=int,default=12)
    args=parser.parse_args(argv)
    if args.radius<1 or args.vertical<0 or args.top<1:
        parser.error("invalid scan geometry")
    bounds=[]
    for case in CASES:
        x,y,z=case.warning
        bounds.append((x-args.radius,y-args.vertical-2,z-args.radius,x+args.radius,y+args.vertical+2,z+args.radius))
    blocks,stats=scan_blocks(args.world_dir,bounds)
    lines=[
        "# DeathRun Classic26 unresolved trap route-surface evidence",
        f"source={args.world_dir.name}",
        f"route_surface_summary cases={len(CASES)} regions={stats.regions} chunks={stats.chunks} captured_blocks={stats.captured_blocks} radius={args.radius} vertical={args.vertical}",
        "# Surface components are walkable physical geometry only, not confirmed trap targets.",
        ""
    ]
    for case in CASES:
        surfaces=standing_surfaces(blocks,case,args.radius,args.vertical)
        lines.extend(render_case(case,surfaces,args.top))
    report="\n".join(lines)+"\n"
    if args.output:
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(report,encoding="utf-8")
    else:
        sys.stdout.write(report)
    return 0


if __name__=="__main__":
    raise SystemExit(main())
