#!/usr/bin/env python3
"""Catalog preserved redstone/control-chain geometry in an archived DeathRun world.

This tool reports physical proximity only. A proximity component is NOT a
simulated redstone circuit and must not be treated as proof that a button
activates a mechanism. Its purpose is to answer a narrower question: did enough
physical control-chain material survive the archive to support further manual
or automated reconstruction?
"""

from __future__ import annotations

import argparse
import math
import sys
from collections import Counter, deque
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


@dataclass(frozen=True)
class Block:
    category: str
    name: str
    x: int
    y: int
    z: int
    properties: tuple[tuple[str, str], ...] = ()

    @property
    def pos(self) -> tuple[int, int, int]:
        return self.x, self.y, self.z


@dataclass(frozen=True)
class Component:
    members: tuple[Block, ...]

    @property
    def bbox(self) -> tuple[int, int, int, int, int, int]:
        return (
            min(item.x for item in self.members),
            min(item.y for item in self.members),
            min(item.z for item in self.members),
            max(item.x for item in self.members),
            max(item.y for item in self.members),
            max(item.z for item in self.members),
        )

    @property
    def types(self) -> Counter[str]:
        return Counter(item.name for item in self.members)


def _parse_properties(fields: Sequence[str]) -> tuple[tuple[str, str], ...]:
    raw = next((field[6:] for field in fields if field.startswith("props=")), "")
    pairs: list[tuple[str, str]] = []
    if raw:
        for item in raw.split(","):
            if ":" not in item:
                continue
            key, value = item.split(":", 1)
            pairs.append((key, value))
    return tuple(sorted(pairs))


def parse_blocks(path: Path) -> tuple[list[Block], list[Block], list[Block]]:
    redstone: list[Block] = []
    buttons: list[Block] = []
    mechanisms: list[Block] = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.startswith("BLOCK\t"):
            continue
        fields = raw.split("\t")
        if len(fields) < 6:
            continue
        category = fields[1]
        if category not in {"REDSTONE", "BUTTON", "MECHANISM"}:
            continue
        try:
            block = Block(
                category=category,
                name=fields[2],
                x=int(fields[3]),
                y=int(fields[4]),
                z=int(fields[5]),
                properties=_parse_properties(fields[6:]),
            )
        except ValueError:
            continue
        if category == "REDSTONE":
            redstone.append(block)
        elif category == "BUTTON":
            buttons.append(block)
        else:
            mechanisms.append(block)

    key = lambda item: (item.x, item.y, item.z, item.name)
    redstone.sort(key=key)
    buttons.sort(key=key)
    mechanisms.sort(key=key)
    return redstone, buttons, mechanisms


def proximity_components(redstone: Sequence[Block]) -> list[Component]:
    """Build 26-neighbor physical proximity components.

    Diagonal adjacency is intentionally allowed so stair-stepping redstone
    geometry is not split. This remains structural proximity, not electrical
    connectivity.
    """
    by_pos: dict[tuple[int, int, int], list[Block]] = {}
    for block in redstone:
        by_pos.setdefault(block.pos, []).append(block)

    remaining = set(by_pos)
    result: list[Component] = []
    offsets = [
        (dx, dy, dz)
        for dx in (-1, 0, 1)
        for dy in (-1, 0, 1)
        for dz in (-1, 0, 1)
        if (dx, dy, dz) != (0, 0, 0)
    ]

    while remaining:
        start = min(remaining)
        remaining.remove(start)
        queue = deque([start])
        positions: list[tuple[int, int, int]] = []
        while queue:
            pos = queue.popleft()
            positions.append(pos)
            x, y, z = pos
            for dx, dy, dz in offsets:
                neighbor = (x + dx, y + dy, z + dz)
                if neighbor in remaining:
                    remaining.remove(neighbor)
                    queue.append(neighbor)

        members = tuple(
            sorted(
                (block for pos in positions for block in by_pos[pos]),
                key=lambda item: (item.x, item.y, item.z, item.name),
            )
        )
        result.append(Component(members))

    result.sort(
        key=lambda item: (
            -len(item.members),
            item.bbox[0],
            item.bbox[1],
            item.bbox[2],
        )
    )
    return result


def distance(left: Block, right: Block) -> float:
    return math.sqrt(
        (left.x - right.x) ** 2
        + (left.y - right.y) ** 2
        + (left.z - right.z) ** 2
    )


def nearby_anchors(
    component: Component,
    anchors: Sequence[Block],
    radius: float,
) -> tuple[Block, ...]:
    result = [
        anchor
        for anchor in anchors
        if any(distance(member, anchor) <= radius for member in component.members)
    ]
    return tuple(sorted(result, key=lambda item: (item.x, item.y, item.z, item.name)))


def _block_text(block: Block) -> str:
    props = ",".join(f"{key}:{value}" for key, value in block.properties) or "none"
    return f"{block.x},{block.y},{block.z}:{block.name}[{props}]"


def render_report(source: Path, redstone: Sequence[Block], buttons: Sequence[Block],
                  mechanisms: Sequence[Block], anchor_radius: float,
                  max_components: int) -> tuple[str, dict[str, int]]:
    components = proximity_components(redstone)
    enriched: list[tuple[Component, tuple[Block, ...], tuple[Block, ...]]] = []
    for component in components:
        nearby_buttons = nearby_anchors(component, buttons, anchor_radius)
        nearby_mechanisms = nearby_anchors(component, mechanisms, anchor_radius)
        enriched.append((component, nearby_buttons, nearby_mechanisms))

    type_counts = Counter(block.name for block in redstone)
    with_buttons = sum(bool(item[1]) for item in enriched)
    with_mechanisms = sum(bool(item[2]) for item in enriched)
    bridges = sum(bool(item[1]) and bool(item[2]) for item in enriched)
    largest = max((len(item.members) for item in components), default=0)
    stats = {
        "redstone_blocks": len(redstone),
        "redstone_types": len(type_counts),
        "components": len(components),
        "largest_component": largest,
        "components_near_buttons": with_buttons,
        "components_near_mechanisms": with_mechanisms,
        "bridge_components": bridges,
    }

    lines = [
        "# DeathRun Classic26 archived redstone/control-chain evidence",
        f"source={source.name}",
        (
            "redstone_evidence_summary "
            f"redstone_blocks={stats['redstone_blocks']} "
            f"redstone_types={stats['redstone_types']} "
            f"components={stats['components']} "
            f"largest_component={stats['largest_component']} "
            f"components_near_buttons={stats['components_near_buttons']} "
            f"components_near_mechanisms={stats['components_near_mechanisms']} "
            f"bridge_components={stats['bridge_components']} "
            f"buttons={len(buttons)} mechanisms={len(mechanisms)} "
            f"anchor_radius={anchor_radius:g}"
        ),
        "# Components use 26-neighbor physical proximity; they are not simulated redstone circuits.",
        "# A button/mechanism anchor means only that it lies within the configured radius.",
        "REDSTONE_TYPES\t" + (
            ";".join(f"{name}:{count}" for name, count in sorted(type_counts.items()))
            or "none"
        ),
        "",
    ]

    # Put the most reconstruction-useful components first, then largest remainder.
    ordered = sorted(
        enriched,
        key=lambda item: (
            -(1 if item[1] and item[2] else 0),
            -(1 if item[1] else 0),
            -(1 if item[2] else 0),
            -len(item[0].members),
            item[0].bbox,
        ),
    )
    for index, (component, nearby_buttons, nearby_mechanisms) in enumerate(
        ordered[:max_components], 1
    ):
        min_x, min_y, min_z, max_x, max_y, max_z = component.bbox
        types = ",".join(
            f"{name}:{count}" for name, count in sorted(component.types.items())
        )
        lines.append(
            "REDSTONE_COMPONENT\t"
            f"id={index:03d}\tsize={len(component.members)}\t"
            f"bbox={min_x},{min_y},{min_z}:{max_x},{max_y},{max_z}\t"
            f"types={types}\tbuttons={len(nearby_buttons)}\t"
            f"mechanisms={len(nearby_mechanisms)}"
        )
        for button in nearby_buttons:
            lines.append(
                f"REDSTONE_BUTTON\tcomponent={index:03d}\t{_block_text(button)}"
            )
        for mechanism in nearby_mechanisms:
            lines.append(
                f"REDSTONE_MECHANISM\tcomponent={index:03d}\t{_block_text(mechanism)}"
            )

    if len(ordered) > max_components:
        lines.append(
            f"TRUNCATED\tshown_components={max_components}\ttotal_components={len(ordered)}"
        )

    return "\n".join(lines) + "\n", stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--anchor-radius", type=float, default=2.0)
    parser.add_argument("--max-components", type=int, default=80)
    parser.add_argument("--min-redstone", type=int, default=0)
    args = parser.parse_args(argv)

    if args.anchor_radius <= 0:
        parser.error("--anchor-radius must be positive")
    if args.max_components < 1:
        parser.error("--max-components must be positive")

    redstone, buttons, mechanisms = parse_blocks(args.probe_report)
    report, stats = render_report(
        args.probe_report,
        redstone,
        buttons,
        mechanisms,
        args.anchor_radius,
        args.max_components,
    )
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if stats["redstone_blocks"] < args.min_redstone:
        print(
            "anvil_redstone_evidence: expected at least "
            f"{args.min_redstone} redstone blocks, got {stats['redstone_blocks']}",
            file=sys.stderr,
        )
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
