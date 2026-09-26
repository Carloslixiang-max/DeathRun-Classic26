#!/usr/bin/env python3
"""Report physical components of pressure plates and portal blocks in Anvil evidence."""

from __future__ import annotations

import argparse
from collections import Counter, deque
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Sequence

from anvil_cluster import Candidate, parse_probe_report


@dataclass(frozen=True)
class Component:
    members: tuple[Candidate, ...]

    @property
    def center(self) -> tuple[float, float, float]:
        size = len(self.members)
        return (
            sum(item.x for item in self.members) / size,
            sum(item.y for item in self.members) / size,
            sum(item.z for item in self.members) / size,
        )

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
    def materials(self) -> Counter[str]:
        return Counter(item.name for item in self.members)


def _components(
    points: Sequence[Candidate],
    neighbors: Callable[[tuple[int, int, int]], Sequence[tuple[int, int, int]]],
) -> list[Component]:
    by_pos = {(item.x, item.y, item.z): item for item in points}
    remaining = set(by_pos)
    components: list[Component] = []

    while remaining:
        start = min(remaining)
        queue = deque([start])
        remaining.remove(start)
        members: list[Candidate] = []

        while queue:
            pos = queue.popleft()
            members.append(by_pos[pos])
            for neighbor in neighbors(pos):
                if neighbor in remaining:
                    remaining.remove(neighbor)
                    queue.append(neighbor)

        components.append(
            Component(
                tuple(
                    sorted(
                        members,
                        key=lambda item: (item.x, item.y, item.z, item.name),
                    )
                )
            )
        )

    components.sort(
        key=lambda component: (
            -len(component.members),
            component.center[0],
            component.center[2],
            component.center[1],
        )
    )
    return components


def pressure_plate_components(points: Sequence[Candidate]) -> list[Component]:
    plates = [item for item in points if item.category == "PRESSURE_PLATE"]

    def neighbors(pos: tuple[int, int, int]) -> list[tuple[int, int, int]]:
        x, y, z = pos
        result: list[tuple[int, int, int]] = []
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx == 0 and dz == 0:
                    continue
                result.append((x + dx, y, z + dz))
        return result

    return _components(plates, neighbors)


def portal_components(portals: Sequence[Candidate]) -> list[Component]:
    def neighbors(pos: tuple[int, int, int]) -> list[tuple[int, int, int]]:
        x, y, z = pos
        return [
            (x - 1, y, z),
            (x + 1, y, z),
            (x, y - 1, z),
            (x, y + 1, z),
            (x, y, z - 1),
            (x, y, z + 1),
        ]

    return _components(portals, neighbors)


def _fmt(value: float) -> str:
    return f"{value:.2f}"


def _material_text(component: Component) -> str:
    return ",".join(
        f"{name}:{count}" for name, count in sorted(component.materials.items())
    )


def _component_line(kind: str, index: int, component: Component) -> str:
    cx, cy, cz = component.center
    min_x, min_y, min_z, max_x, max_y, max_z = component.bbox
    return (
        f"{kind}\tid={index:03d}\tsize={len(component.members)}\t"
        f"center={_fmt(cx)},{_fmt(cy)},{_fmt(cz)}\t"
        f"bbox={min_x},{min_y},{min_z}:{max_x},{max_y},{max_z}\t"
        f"materials={_material_text(component)}"
    )


def render_report(
    source: Path,
    points: Sequence[Candidate],
    portals: Sequence[Candidate],
    plates: Sequence[Component],
    portal_groups: Sequence[Component],
) -> str:
    plate_materials = Counter(
        item.name for item in points if item.category == "PRESSURE_PLATE"
    )
    portal_materials = Counter(item.name for item in portals)
    largest_plate = max((len(group.members) for group in plates), default=0)
    largest_portal = max((len(group.members) for group in portal_groups), default=0)

    lines = [
        "# DeathRun Classic26 physical topology evidence",
        f"source={source.name}",
        (
            "topology_summary "
            f"pressure_plate_points={sum(plate_materials.values())} "
            f"pressure_plate_components={len(plates)} "
            f"largest_pressure_plate_component={largest_plate} "
            f"portal_points={sum(portal_materials.values())} "
            f"portal_components={len(portal_groups)} "
            f"largest_portal_component={largest_portal}"
        ),
        "pressure_plate_materials "
        + " ".join(f"{key}={plate_materials[key]}" for key in sorted(plate_materials)),
        "portal_materials "
        + " ".join(f"{key}={portal_materials[key]}" for key in sorted(portal_materials)),
        "# Components are physical adjacency only; they are not checkpoint/trap labels.",
        "",
    ]

    for index, component in enumerate(plates, 1):
        lines.append(_component_line("PRESSURE_PLATE_COMPONENT", index, component))
    for index, component in enumerate(portal_groups, 1):
        lines.append(_component_line("PORTAL_COMPONENT", index, component))
    return "\n".join(lines) + "\n"


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--min-pressure-components", type=int, default=0)
    parser.add_argument("--min-portal-components", type=int, default=0)
    args = parser.parse_args(argv)

    points, portals = parse_probe_report(args.probe_report)
    plates = pressure_plate_components(points)
    portal_groups = portal_components(portals)
    report = render_report(args.probe_report, points, portals, plates, portal_groups)

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        print(report, end="")

    if len(plates) < args.min_pressure_components:
        return 2
    if len(portal_groups) < args.min_portal_components:
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
