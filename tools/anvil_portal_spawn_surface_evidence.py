#!/usr/bin/env python3
"""Find conservative standing-surface candidates beside archived portal gates.

For each surviving Nether Portal component, scan a small block window on both
normal sides of the gate. A standing column is considered conservative when:
- feet block is air;
- head block is air;
- support block below is a conservative floor-like block, excluding air, fluids,
  portals, barriers, no-collision plants, rails, wire, buttons and pressure plates.

This is physical geometry evidence only. The nearest standing column is NOT
automatically the original Hive checkpoint respawn, start, or finish position.
"""

from __future__ import annotations

import argparse
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Sequence

from anvil_cluster import Candidate, parse_probe_report
from anvil_probe import (
    NbtReader,
    REGION_RE,
    chunk_level,
    chunk_position,
    decode_palette_index,
    decompress_chunk,
    palette_name,
    region_paths,
    section_y,
)
from anvil_topology import Component, portal_components


AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
NON_SUPPORT = AIR | {
    "minecraft:water",
    "minecraft:lava",
    "minecraft:nether_portal",
    "minecraft:end_portal",
    "minecraft:end_gateway",
    "minecraft:barrier",
    # No-collision / non-floor blocks. The scan is deliberately conservative:
    # if an integer-Y spawn would fall through or settle lower than modeled,
    # it is not accepted as a safe standing support.
    "minecraft:grass",
    "minecraft:tall_grass",
    "minecraft:fern",
    "minecraft:large_fern",
    "minecraft:dead_bush",
    "minecraft:vine",
    "minecraft:seagrass",
    "minecraft:tall_seagrass",
    "minecraft:kelp",
    "minecraft:kelp_plant",
    "minecraft:sugar_cane",
    "minecraft:bamboo",
    "minecraft:bamboo_sapling",
    "minecraft:snow",
    "minecraft:redstone_wire",
    "minecraft:tripwire",
    "minecraft:torch",
    "minecraft:wall_torch",
    "minecraft:redstone_torch",
    "minecraft:redstone_wall_torch",
    "minecraft:soul_torch",
    "minecraft:soul_wall_torch",
    "minecraft:rail",
    "minecraft:powered_rail",
    "minecraft:detector_rail",
    "minecraft:activator_rail",
    "minecraft:dandelion",
    "minecraft:poppy",
    "minecraft:blue_orchid",
    "minecraft:allium",
    "minecraft:azure_bluet",
    "minecraft:red_tulip",
    "minecraft:orange_tulip",
    "minecraft:white_tulip",
    "minecraft:pink_tulip",
    "minecraft:oxeye_daisy",
    "minecraft:cornflower",
    "minecraft:lily_of_the_valley",
    "minecraft:wither_rose",
    "minecraft:sunflower",
    "minecraft:lilac",
    "minecraft:rose_bush",
    "minecraft:peony",
}

NON_SUPPORT_SUFFIXES = (
    "_sapling",
    "_roots",
    "_fungus",
    "_coral_fan",
    "_wall_coral_fan",
    "_button",
    "_pressure_plate",
)


def _is_support_block(name: str) -> bool:
    if name in NON_SUPPORT:
        return False
    return not name.endswith(NON_SUPPORT_SUFFIXES)


@dataclass(frozen=True)
class ScanStats:
    regions: int
    chunks: int
    modern_sections: int
    legacy_sections_skipped: int
    captured_blocks: int


@dataclass(frozen=True)
class StandingCandidate:
    portal_id: int
    side: int
    x: int
    y: int
    z: int
    normal_distance: int
    support: str
    distance_to_center: float


@dataclass(frozen=True)
class PortalStandingEvidence:
    portal_id: int
    portal: Component
    plane_axis: str
    negative: tuple[StandingCandidate, ...]
    positive: tuple[StandingCandidate, ...]


def _portal_bounds(
    portals: Sequence[Component],
    normal_distance: int,
    lateral_padding: int,
) -> list[tuple[int, int, int, int, int, int]]:
    result = []
    expand = normal_distance + lateral_padding + 1
    for component in portals:
        min_x, min_y, min_z, max_x, max_y, max_z = component.bbox
        result.append(
            (
                min_x - expand,
                min_y - 4,
                min_z - expand,
                max_x + expand,
                max_y + 4,
                max_z + expand,
            )
        )
    return result


def _inside_any(
    x: int,
    y: int,
    z: int,
    bounds: Sequence[tuple[int, int, int, int, int, int]],
) -> bool:
    return any(
        min_x <= x <= max_x
        and min_y <= y <= max_y
        and min_z <= z <= max_z
        for min_x, min_y, min_z, max_x, max_y, max_z in bounds
    )


def _section_overlaps(
    chunk_x: int,
    section_y_value: int,
    chunk_z: int,
    bounds: Sequence[tuple[int, int, int, int, int, int]],
) -> bool:
    min_x = chunk_x * 16
    min_y = section_y_value * 16
    min_z = chunk_z * 16
    max_x = min_x + 15
    max_y = min_y + 15
    max_z = min_z + 15
    return any(
        min_x <= bx2 and max_x >= bx1
        and min_y <= by2 and max_y >= by1
        and min_z <= bz2 and max_z >= bz1
        for bx1, by1, bz1, bx2, by2, bz2 in bounds
    )


def scan_blocks(
    world_dir: Path,
    bounds: Sequence[tuple[int, int, int, int, int, int]],
) -> tuple[dict[tuple[int, int, int], str], ScanStats]:
    captured: dict[tuple[int, int, int], str] = {}
    regions = chunks = modern_sections = legacy_sections = 0

    for path in region_paths([str(world_dir)]):
        match = REGION_RE.match(path.name)
        if match is None:
            continue
        regions += 1
        region_x = int(match.group(1))
        region_z = int(match.group(2))
        data = path.read_bytes()
        if len(data) < 8192:
            continue

        for slot in range(1024):
            location = data[slot * 4 : slot * 4 + 4]
            sector_offset = int.from_bytes(location[:3], "big")
            sector_count = location[3]
            if sector_offset == 0 or sector_count == 0:
                continue

            fallback_x = region_x * 32 + (slot & 31)
            fallback_z = region_z * 32 + (slot >> 5)
            byte_offset = sector_offset * 4096
            if byte_offset + 5 > len(data):
                continue
            length = int.from_bytes(data[byte_offset : byte_offset + 4], "big")
            if length <= 1:
                continue
            compression = data[byte_offset + 4]
            if compression & 0x80:
                continue
            end = byte_offset + 4 + length
            if end > len(data):
                continue

            try:
                raw_nbt = decompress_chunk(compression, data[byte_offset + 5 : end])
                root = NbtReader(raw_nbt).root()
                level = chunk_level(root)
                chunk_x, chunk_z = chunk_position(level, fallback_x, fallback_z)
            except Exception:
                continue

            sections = level.get("sections", level.get("Sections", []))
            if not isinstance(sections, list):
                continue
            chunks += 1

            for section in sections:
                if not isinstance(section, dict):
                    continue
                sy = section_y(section)
                if sy is None or not _section_overlaps(chunk_x, sy, chunk_z, bounds):
                    continue
                if isinstance(section.get("Blocks"), (bytes, bytearray)):
                    legacy_sections += 1
                    continue

                palette = section.get("Palette")
                data_values = section.get("BlockStates")
                if palette is None:
                    block_states = section.get("block_states")
                    if isinstance(block_states, dict):
                        palette = block_states.get("palette")
                        data_values = block_states.get("data")
                if not isinstance(palette, list) or not palette:
                    continue
                if not isinstance(data_values, list):
                    data_values = []

                modern_sections += 1
                names = [palette_name(entry) or "minecraft:air" for entry in palette]
                for index in range(4096):
                    local_x = index & 15
                    local_z = (index >> 4) & 15
                    local_y = (index >> 8) & 15
                    x = chunk_x * 16 + local_x
                    y = sy * 16 + local_y
                    z = chunk_z * 16 + local_z
                    if not _inside_any(x, y, z, bounds):
                        continue
                    palette_index = decode_palette_index(data_values, index, len(palette))
                    name = names[palette_index] if palette_index < len(names) else "minecraft:air"
                    captured[(x, y, z)] = name

    return captured, ScanStats(
        regions=regions,
        chunks=chunks,
        modern_sections=modern_sections,
        legacy_sections_skipped=legacy_sections,
        captured_blocks=len(captured),
    )


def _safe_standing(
    blocks: Mapping[tuple[int, int, int], str],
    x: int,
    y: int,
    z: int,
) -> tuple[bool, str]:
    feet = blocks.get((x, y, z), "minecraft:air")
    head = blocks.get((x, y + 1, z), "minecraft:air")
    support = blocks.get((x, y - 1, z), "minecraft:air")
    return (
        feet in AIR and head in AIR and _is_support_block(support),
        support,
    )


def standing_candidates(
    portal_id: int,
    component: Component,
    blocks: Mapping[tuple[int, int, int], str],
    max_normal_distance: int,
    lateral_padding: int,
) -> PortalStandingEvidence:
    min_x, min_y, min_z, max_x, max_y, max_z = component.bbox
    if min_x == max_x:
        axis = "x"
    elif min_z == max_z:
        axis = "z"
    else:
        axis = "unknown"

    cx, cy, cz = component.center
    by_side: dict[int, list[StandingCandidate]] = {-1: [], 1: []}

    if axis == "unknown":
        return PortalStandingEvidence(portal_id, component, axis, (), ())

    for side in (-1, 1):
        for normal_distance in range(1, max_normal_distance + 1):
            if axis == "x":
                x_values = [min_x + side * normal_distance]
                z_values = range(min_z - lateral_padding, max_z + lateral_padding + 1)
            else:
                x_values = range(min_x - lateral_padding, max_x + lateral_padding + 1)
                z_values = [min_z + side * normal_distance]

            for x in x_values:
                for z in z_values:
                    for y in range(min_y - 2, min_y + 4):
                        ok, support = _safe_standing(blocks, x, y, z)
                        if not ok:
                            continue
                        distance = math.sqrt(
                            (x + 0.5 - cx) ** 2
                            + (y - cy) ** 2
                            + (z + 0.5 - cz) ** 2
                        )
                        by_side[side].append(
                            StandingCandidate(
                                portal_id=portal_id,
                                side=side,
                                x=x,
                                y=y,
                                z=z,
                                normal_distance=normal_distance,
                                support=support,
                                distance_to_center=distance,
                            )
                        )

    for side in (-1, 1):
        by_side[side].sort(
            key=lambda item: (
                item.normal_distance,
                abs(item.y - min_y),
                item.distance_to_center,
                item.x,
                item.y,
                item.z,
                item.support,
            )
        )

    return PortalStandingEvidence(
        portal_id=portal_id,
        portal=component,
        plane_axis=axis,
        negative=tuple(by_side[-1]),
        positive=tuple(by_side[1]),
    )


def _candidate_text(item: StandingCandidate) -> str:
    return (
        f"{item.x},{item.y},{item.z}"
        f"[d={item.normal_distance},support={item.support},center={item.distance_to_center:.2f}]"
    )


def render_report(
    source: Path,
    stats: ScanStats,
    evidence: Sequence[PortalStandingEvidence],
    top: int,
) -> tuple[str, dict[str, int]]:
    with_any = sum(bool(item.negative or item.positive) for item in evidence)
    with_both = sum(bool(item.negative and item.positive) for item in evidence)
    result_stats = {
        "portals": len(evidence),
        "portals_with_any_candidates": with_any,
        "portals_with_both_sides": with_both,
    }

    lines = [
        "# DeathRun Classic26 portal-side standing-surface evidence",
        f"source={source.name}",
        (
            "portal_spawn_surface_summary "
            f"portals={result_stats['portals']} "
            f"portals_with_any_candidates={result_stats['portals_with_any_candidates']} "
            f"portals_with_both_sides={result_stats['portals_with_both_sides']} "
            f"regions={stats.regions} chunks={stats.chunks} "
            f"modern_sections={stats.modern_sections} "
            f"legacy_sections_skipped={stats.legacy_sections_skipped} "
            f"captured_blocks={stats.captured_blocks}"
        ),
        "# Candidates require air at feet/head plus a conservative floor-like support block.",
        "# They are safe-standing geometry candidates only; they are NOT original checkpoint respawns.",
        "",
    ]

    for item in evidence:
        min_x, min_y, min_z, max_x, max_y, max_z = item.portal.bbox
        neg = ";".join(_candidate_text(value) for value in item.negative[:top]) or "none"
        pos = ";".join(_candidate_text(value) for value in item.positive[:top]) or "none"
        lines.append(
            "PORTAL_STANDING\t"
            f"portal={item.portal_id:03d}\t"
            f"bbox={min_x},{min_y},{min_z}:{max_x},{max_y},{max_z}\t"
            f"plane_axis={item.plane_axis}\t"
            f"negative_count={len(item.negative)}\t"
            f"positive_count={len(item.positive)}\t"
            f"negative_top={neg}\t"
            f"positive_top={pos}"
        )

    return "\n".join(lines) + "\n", result_stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world_dir", type=Path)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--max-normal-distance", type=int, default=6)
    parser.add_argument("--lateral-padding", type=int, default=2)
    parser.add_argument("--top", type=int, default=5)
    parser.add_argument("--min-portals", type=int, default=0)
    parser.add_argument("--min-portals-with-candidates", type=int, default=0)
    args = parser.parse_args(argv)

    if args.max_normal_distance < 1 or args.lateral_padding < 0 or args.top < 1:
        parser.error("invalid scan geometry")

    _points, portal_points = parse_probe_report(args.probe_report)
    portals = portal_components(portal_points)
    bounds = _portal_bounds(
        portals,
        args.max_normal_distance,
        args.lateral_padding,
    )
    blocks, scan_stats = scan_blocks(args.world_dir, bounds)
    evidence = [
        standing_candidates(
            index,
            component,
            blocks,
            args.max_normal_distance,
            args.lateral_padding,
        )
        for index, component in enumerate(portals, 1)
    ]
    report, stats = render_report(
        args.probe_report,
        scan_stats,
        evidence,
        args.top,
    )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if stats["portals"] < args.min_portals:
        return 2
    if (
        stats["portals_with_any_candidates"]
        < args.min_portals_with_candidates
    ):
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
