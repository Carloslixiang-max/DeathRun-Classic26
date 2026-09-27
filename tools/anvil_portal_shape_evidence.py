#!/usr/bin/env python3
"""Measure the planar shape of archived Nether Portal components.

Hive DeathRun documentation describes checkpoints as Nether Portal blocks.
This tool therefore measures whether surviving portal components look like
dense planar gates. Shape evidence alone still does not assign checkpoint
numbers, respawn coordinates, route order, start or finish roles.
"""

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from anvil_cluster import parse_probe_report
from anvil_topology import Component, portal_components


@dataclass(frozen=True)
class PortalShape:
    component: Component
    plane_axis: str | None
    plane_value: int | None
    width: int
    height: int
    rectangle_area: int
    filled: int
    holes: int
    fill_ratio: float


def analyze_component(component: Component) -> PortalShape:
    xs = sorted({item.x for item in component.members})
    ys = sorted({item.y for item in component.members})
    zs = sorted({item.z for item in component.members})

    plane_axis: str | None = None
    plane_value: int | None = None
    width = 0
    height = 0

    if len(xs) == 1:
        plane_axis = "x"
        plane_value = xs[0]
        width = max(zs) - min(zs) + 1
        height = max(ys) - min(ys) + 1
    elif len(zs) == 1:
        plane_axis = "z"
        plane_value = zs[0]
        width = max(xs) - min(xs) + 1
        height = max(ys) - min(ys) + 1
    elif len(ys) == 1:
        plane_axis = "y"
        plane_value = ys[0]
        width = max(xs) - min(xs) + 1
        height = max(zs) - min(zs) + 1

    rectangle_area = width * height if plane_axis is not None else 0
    filled = len(component.members)
    holes = max(0, rectangle_area - filled)
    fill_ratio = (filled / rectangle_area) if rectangle_area else 0.0
    return PortalShape(
        component=component,
        plane_axis=plane_axis,
        plane_value=plane_value,
        width=width,
        height=height,
        rectangle_area=rectangle_area,
        filled=filled,
        holes=holes,
        fill_ratio=fill_ratio,
    )


def analyze_components(components: Sequence[Component]) -> list[PortalShape]:
    return [analyze_component(component) for component in components]


def _bbox(component: Component) -> str:
    min_x, min_y, min_z, max_x, max_y, max_z = component.bbox
    return f"{min_x},{min_y},{min_z}:{max_x},{max_y},{max_z}"


def render_report(
    source: Path,
    shapes: Sequence[PortalShape],
    dense_threshold: float,
) -> tuple[str, dict[str, int]]:
    planar = [shape for shape in shapes if shape.plane_axis is not None]
    dense = [
        shape
        for shape in planar
        if shape.fill_ratio >= dense_threshold
    ]
    stats = {
        "components": len(shapes),
        "planar_components": len(planar),
        "dense_planar_components": len(dense),
    }

    lines = [
        "# DeathRun Classic26 Nether Portal shape evidence",
        f"source={source.name}",
        (
            "portal_shape_summary "
            f"components={stats['components']} "
            f"planar_components={stats['planar_components']} "
            f"dense_planar_components={stats['dense_planar_components']} "
            f"dense_threshold={dense_threshold:.3f}"
        ),
        "# Dense planar portal geometry is compatible with a gate/checkpoint wall.",
        "# It does NOT establish checkpoint numbering, route order, respawn point, start or finish.",
        "",
    ]

    for index, shape in enumerate(shapes, 1):
        axis = shape.plane_axis or "nonplanar"
        value = str(shape.plane_value) if shape.plane_value is not None else "none"
        lines.append(
            "PORTAL_SHAPE\t"
            f"id={index:03d}\t"
            f"bbox={_bbox(shape.component)}\t"
            f"plane_axis={axis}\tplane_value={value}\t"
            f"width={shape.width}\theight={shape.height}\t"
            f"rectangle_area={shape.rectangle_area}\t"
            f"filled={shape.filled}\tholes={shape.holes}\t"
            f"fill_ratio={shape.fill_ratio:.3f}"
        )
    return "\n".join(lines) + "\n", stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--dense-threshold", type=float, default=0.80)
    parser.add_argument("--min-planar", type=int, default=0)
    parser.add_argument("--min-dense-planar", type=int, default=0)
    args = parser.parse_args(argv)

    if not 0 < args.dense_threshold <= 1:
        parser.error("--dense-threshold must be in (0, 1]")

    _points, portal_points = parse_probe_report(args.probe_report)
    shapes = analyze_components(portal_components(portal_points))
    report, stats = render_report(
        args.probe_report,
        shapes,
        args.dense_threshold,
    )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if stats["planar_components"] < args.min_planar:
        return 2
    if stats["dense_planar_components"] < args.min_dense_planar:
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
