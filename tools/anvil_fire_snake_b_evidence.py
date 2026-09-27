#!/usr/bin/env python3
"""Recover the second To Bee Fire Snake launcher and westbound route slices.

The low-stage warning/action/button identity is already independently strong.
This tool tests the physical hypothesis that the preserved west-facing
dispenser row at x=84 is its launch source and prints conservative walkable
support cells from the source toward the Runner warning.
"""

from __future__ import annotations

import argparse
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Sequence

from anvil_mechanism_evidence import Mechanism, parse_mechanisms
from anvil_portal_spawn_surface_evidence import AIR, _is_support_block, scan_blocks


WARNING = (70, 25, 53)
ACTION = (76, 25, 56)
BUTTON = (76, 25, 47)
SOURCE_X = 84
SOURCE_Y = 24
SOURCE_Z_MIN = 47
SOURCE_Z_MAX = 54


@dataclass(frozen=True)
class SliceCell:
    x: int
    support_y: int
    z: int
    material: str


def source_dispensers(mechanisms: Sequence[Mechanism]) -> tuple[Mechanism, ...]:
    return tuple(
        item for item in mechanisms
        if item.name == "minecraft:dispenser"
        and item.x == SOURCE_X
        and item.y == SOURCE_Y
        and SOURCE_Z_MIN <= item.z <= SOURCE_Z_MAX
        and item.property("facing") == "west"
    )


def walkable_slice(
    blocks: Mapping[tuple[int, int, int], str],
    x: int,
    z_min: int = 47,
    z_max: int = 54,
    feet_y_min: int = 23,
    feet_y_max: int = 26,
) -> tuple[SliceCell, ...]:
    cells: list[SliceCell] = []
    for z in range(z_min, z_max + 1):
        for feet_y in range(feet_y_min, feet_y_max + 1):
            feet = blocks.get((x, feet_y, z), "minecraft:air")
            head = blocks.get((x, feet_y + 1, z), "minecraft:air")
            support = blocks.get((x, feet_y - 1, z), "minecraft:air")
            if feet not in AIR or head not in AIR or not _is_support_block(support):
                continue
            cells.append(SliceCell(x, feet_y - 1, z, support))
            break
    return tuple(cells)


def render_report(
    probe: Path,
    world_dir: Path,
    source_min: int,
) -> tuple[str, dict[str, int]]:
    mechanisms = parse_mechanisms(probe)
    sources = source_dispensers(mechanisms)
    bounds = [(60, 20, 44, 85, 28, 57)]
    blocks, stats = scan_blocks(world_dir, bounds)

    lines = [
        "# DeathRun Classic26 To Bee Fire Snake B physical evidence",
        f"source={world_dir.name}",
        (
            "fire_snake_b_summary "
            f"sources={len(sources)} expected_min={source_min} "
            f"warning={WARNING[0]},{WARNING[1]},{WARNING[2]} "
            f"action={ACTION[0]},{ACTION[1]},{ACTION[2]} "
            f"button={BUTTON[0]},{BUTTON[1]},{BUTTON[2]} "
            f"regions={stats.regions} chunks={stats.chunks} captured_blocks={stats.captured_blocks}"
        ),
        "# Source facings and route support are archive geometry; server timing remains unknown.",
        "",
    ]

    for item in sources:
        lines.append(
            "FIRE_SNAKE_B_SOURCE\t"
            f"pos={item.x},{item.y},{item.z}\tfacing={item.property('facing')}"
        )

    nonempty = 0
    total_cells = 0
    for x in range(83, 62, -1):
        cells = walkable_slice(blocks, x)
        if cells:
            nonempty += 1
            total_cells += len(cells)
        materials = Counter(cell.material for cell in cells)
        material_text = ";".join(f"{name}:{count}" for name, count in sorted(materials.items())) or "none"
        positions = ";".join(f"{cell.x},{cell.support_y},{cell.z}:{cell.material}" for cell in cells) or "none"
        lines.append(
            "FIRE_SNAKE_B_SLICE\t"
            f"x={x}\tcells={len(cells)}\tmaterials={material_text}\tpositions={positions}"
        )

    lines.append(
        "fire_snake_b_route_summary "
        f"slices=21 nonempty_slices={nonempty} total_support_cells={total_cells}"
    )
    return "\n".join(lines) + "\n", {
        "sources": len(sources),
        "nonempty_slices": nonempty,
        "total_support_cells": total_cells,
    }


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world_dir", type=Path)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--min-west-source", type=int, default=8)
    parser.add_argument("--min-nonempty-slices", type=int, default=16)
    args = parser.parse_args(argv)

    report, stats = render_report(args.probe_report, args.world_dir, args.min_west_source)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if stats["sources"] < args.min_west_source:
        return 2
    if stats["nonempty_slices"] < args.min_nonempty_slices:
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
