#!/usr/bin/env python3
"""Probe the special #006/#007 vertical-transition area around "Hint: Look down!".

This tool deliberately treats the #006↔#007 relationship as unresolved special
geometry. It reports:
- conservative vertical drop/landing columns near the Hint sign;
- climbable / vertical-movement materials in the wider #006↔#007 corridor;
- portal/hint vertical offsets.

It does not assign a checkpoint respawn, route direction, teleport, jump, drop,
or finish behavior without surviving geometry that supports it.
"""

from __future__ import annotations

import argparse
import math
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping, Sequence

from anvil_cluster import parse_probe_report as parse_candidate_probe
from anvil_portal_spawn_surface_evidence import (
    AIR,
    _is_support_block,
    scan_blocks,
)
from anvil_sign_links import Sign, parse_probe_report as parse_sign_probe, sign_kind
from anvil_topology import Component, portal_components


VERTICAL_MATERIALS = {
    "minecraft:ladder",
    "minecraft:vine",
    "minecraft:scaffolding",
    "minecraft:water",
    "minecraft:soul_sand",
    "minecraft:magma_block",
    "minecraft:slime_block",
    "minecraft:honey_block",
    "minecraft:hay_block",
    "minecraft:cobweb",
}


@dataclass(frozen=True)
class DropCandidate:
    x: int
    z: int
    top_y: int
    support_y: int
    landing_y: int
    drop: int
    support: str
    horizontal_distance: float


def _hint_sign(signs: Sequence[Sign]) -> Sign | None:
    matches = [
        sign
        for sign in signs
        if sign_kind(sign) == "OTHER"
        and sign.text.strip().lower().startswith("hint")
    ]
    matches.sort(key=lambda sign: (sign.x, sign.y, sign.z, sign.text))
    return matches[0] if matches else None


def _component_center(component: Component) -> tuple[float, float, float]:
    return component.center


def _bounds_for_transition(
    portal6: Component,
    portal7: Component,
    hint: Sign,
    padding: int,
    min_y: int,
    max_y: int,
) -> list[tuple[int, int, int, int, int, int]]:
    xs = [
        portal6.bbox[0], portal6.bbox[3],
        portal7.bbox[0], portal7.bbox[3],
        hint.x,
    ]
    zs = [
        portal6.bbox[2], portal6.bbox[5],
        portal7.bbox[2], portal7.bbox[5],
        hint.z,
    ]
    return [(
        min(xs) - padding,
        min_y,
        min(zs) - padding,
        max(xs) + padding,
        max_y,
        max(zs) + padding,
    )]


def _first_non_air_below(
    blocks: Mapping[tuple[int, int, int], str],
    x: int,
    top_y: int,
    z: int,
    min_y: int,
) -> tuple[int, str] | None:
    for y in range(top_y - 1, min_y - 1, -1):
        name = blocks.get((x, y, z), "minecraft:air")
        if name not in AIR:
            return y, name
    return None


def hint_drop_candidates(
    blocks: Mapping[tuple[int, int, int], str],
    hint: Sign,
    radius: int,
    min_drop: int,
    max_drop: int,
    min_y: int,
) -> list[DropCandidate]:
    result: list[DropCandidate] = []
    top_y = hint.y
    for x in range(hint.x - radius, hint.x + radius + 1):
        for z in range(hint.z - radius, hint.z + radius + 1):
            # The route-space at the hint's elevation must be open for a player.
            if blocks.get((x, top_y, z), "minecraft:air") not in AIR:
                continue
            if blocks.get((x, top_y + 1, z), "minecraft:air") not in AIR:
                continue

            found = _first_non_air_below(blocks, x, top_y, z, min_y)
            if found is None:
                continue
            support_y, support = found
            if not _is_support_block(support):
                continue
            landing_y = support_y + 1
            drop = top_y - landing_y
            if drop < min_drop or drop > max_drop:
                continue

            # Every block from landing feet through the source level must remain air.
            if any(
                blocks.get((x, y, z), "minecraft:air") not in AIR
                for y in range(landing_y, top_y + 2)
            ):
                continue

            result.append(
                DropCandidate(
                    x=x,
                    z=z,
                    top_y=top_y,
                    support_y=support_y,
                    landing_y=landing_y,
                    drop=drop,
                    support=support,
                    horizontal_distance=math.hypot(x - hint.x, z - hint.z),
                )
            )

    result.sort(
        key=lambda item: (
            item.horizontal_distance,
            item.drop,
            item.x,
            item.z,
            item.support,
        )
    )
    return result


def vertical_material_positions(
    blocks: Mapping[tuple[int, int, int], str],
) -> dict[str, list[tuple[int, int, int]]]:
    result: dict[str, list[tuple[int, int, int]]] = {}
    for pos, name in blocks.items():
        if name not in VERTICAL_MATERIALS:
            continue
        result.setdefault(name, []).append(pos)
    for positions in result.values():
        positions.sort()
    return result


def _bbox(positions: Sequence[tuple[int, int, int]]) -> str:
    if not positions:
        return "none"
    xs = [pos[0] for pos in positions]
    ys = [pos[1] for pos in positions]
    zs = [pos[2] for pos in positions]
    return (
        f"{min(xs)},{min(ys)},{min(zs)}:"
        f"{max(xs)},{max(ys)},{max(zs)}"
    )


def _drop_text(item: DropCandidate) -> str:
    return (
        f"{item.x},{item.landing_y},{item.z}"
        f"[source_y={item.top_y},drop={item.drop},"
        f"support_y={item.support_y},support={item.support},"
        f"horiz={item.horizontal_distance:.2f}]"
    )


def render_report(
    source: Path,
    hint: Sign,
    portal6: Component,
    portal7: Component,
    blocks: Mapping[tuple[int, int,int], str],
    drops: Sequence[DropCandidate],
    top: int,
) -> tuple[str, dict[str, int | float]]:
    materials = vertical_material_positions(blocks)
    counts = Counter({name: len(pos) for name, pos in materials.items()})
    p6 = _component_center(portal6)
    p7 = _component_center(portal7)

    nearby_drops = [item for item in drops if item.horizontal_distance <= 4.0]
    stats: dict[str, int | float] = {
        "drop_candidates": len(drops),
        "drop_candidates_within4": len(nearby_drops),
        "vertical_material_blocks": sum(counts.values()),
        "ladder_blocks": counts.get("minecraft:ladder", 0),
        "vine_blocks": counts.get("minecraft:vine", 0),
        "scaffolding_blocks": counts.get("minecraft:scaffolding", 0),
        "water_blocks": counts.get("minecraft:water", 0),
        "portal6_center_y": p6[1],
        "portal7_center_y": p7[1],
        "portal_center_dy": p7[1] - p6[1],
    }

    lines = [
        "# DeathRun Classic26 #006/#007 vertical-transition evidence",
        f"source={source.name}",
        (
            "vertical_transition_summary "
            f"hint={hint.x},{hint.y},{hint.z} "
            f"drop_candidates={stats['drop_candidates']} "
            f"drop_candidates_within4={stats['drop_candidates_within4']} "
            f"vertical_material_blocks={stats['vertical_material_blocks']} "
            f"ladder_blocks={stats['ladder_blocks']} "
            f"vine_blocks={stats['vine_blocks']} "
            f"scaffolding_blocks={stats['scaffolding_blocks']} "
            f"water_blocks={stats['water_blocks']} "
            f"portal6_center_y={float(stats['portal6_center_y']):.2f} "
            f"portal7_center_y={float(stats['portal7_center_y']):.2f} "
            f"portal_center_dy={float(stats['portal_center_dy']):.2f}"
        ),
        "# Drop candidates are open vertical air columns near the Hint with a conservative floor below.",
        "# Vertical materials are inventory only; vines/water may be decorative.",
        "# Evidence does NOT assign checkpoint respawn, teleport, drop, climb or route direction.",
        "",
    ]

    lines.append(
        "HINT_DROP_TOP\t"
        + (";".join(_drop_text(item) for item in drops[:top]) or "none")
    )

    for name in sorted(materials):
        positions = materials[name]
        preview = ";".join(
            f"{x},{y},{z}" for x, y, z in positions[:top]
        ) or "none"
        lines.append(
            "VERTICAL_MATERIAL\t"
            f"name={name}\tcount={len(positions)}\t"
            f"bbox={_bbox(positions)}\tpreview={preview}"
        )

    return "\n".join(lines) + "\n", stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world_dir", type=Path)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--hint-radius", type=int, default=8)
    parser.add_argument("--min-drop", type=int, default=3)
    parser.add_argument("--max-drop", type=int, default=24)
    parser.add_argument("--min-y", type=int, default=0)
    parser.add_argument("--max-y", type=int, default=64)
    parser.add_argument("--corridor-padding", type=int, default=6)
    parser.add_argument("--top", type=int, default=12)
    parser.add_argument("--min-hint-drop-candidates", type=int, default=0)
    args = parser.parse_args(argv)

    _interactive, portal_points = parse_candidate_probe(args.probe_report)
    portals = portal_components(portal_points)
    if len(portals) < 7:
        print("expected at least 7 portal components", file=sys.stderr)
        return 2

    _buttons, signs = parse_sign_probe(args.probe_report)
    hint = _hint_sign(signs)
    if hint is None:
        print("Hint sign not found", file=sys.stderr)
        return 3

    portal6 = portals[5]
    portal7 = portals[6]
    bounds = _bounds_for_transition(
        portal6,
        portal7,
        hint,
        args.corridor_padding,
        args.min_y,
        args.max_y,
    )
    blocks, _stats = scan_blocks(args.world_dir, bounds)
    drops = hint_drop_candidates(
        blocks,
        hint,
        args.hint_radius,
        args.min_drop,
        args.max_drop,
        args.min_y,
    )
    report, stats = render_report(
        args.probe_report,
        hint,
        portal6,
        portal7,
        blocks,
        drops,
        args.top,
    )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if int(stats["drop_candidates"]) < args.min_hint_drop_candidates:
        return 4
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
