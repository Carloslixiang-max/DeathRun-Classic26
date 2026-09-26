#!/usr/bin/env python3
"""Correlate preserved Runner warning signs with physical Nether Portal components.

This is route-geometry evidence only. Portals are not automatically checkpoints,
start/finish gates, or teleport destinations, and warning signs are not used to
invent route order.
"""

from __future__ import annotations

import argparse
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from anvil_cluster import parse_probe_report as parse_candidate_probe
from anvil_sign_links import Sign, parse_probe_report as parse_sign_probe, sign_kind
from anvil_topology import Component, portal_components


@dataclass(frozen=True)
class PortalWarningLink:
    component: Component
    nearest_warning: Sign | None
    nearest_distance: float | None
    nearby_warnings: tuple[tuple[Sign, float], ...]


def _distance(component: Component, sign: Sign) -> float:
    cx, cy, cz = component.center
    return math.sqrt((cx - sign.x) ** 2 + (cy - sign.y) ** 2 + (cz - sign.z) ** 2)


def build_links(
    components: Sequence[Component],
    warnings: Sequence[Sign],
    warning_radius: float,
) -> list[PortalWarningLink]:
    result: list[PortalWarningLink] = []
    for component in components:
        ranked = sorted(
            ((warning, _distance(component, warning)) for warning in warnings),
            key=lambda item: (
                item[1],
                item[0].x,
                item[0].y,
                item[0].z,
                item[0].text,
            ),
        )
        nearby = tuple(item for item in ranked if item[1] <= warning_radius)
        result.append(
            PortalWarningLink(
                component=component,
                nearest_warning=ranked[0][0] if ranked else None,
                nearest_distance=ranked[0][1] if ranked else None,
                nearby_warnings=nearby,
            )
        )
    return result


def _bbox_text(component: Component) -> str:
    min_x, min_y, min_z, max_x, max_y, max_z = component.bbox
    return f"{min_x},{min_y},{min_z}:{max_x},{max_y},{max_z}"


def _center_text(component: Component) -> str:
    x, y, z = component.center
    return f"{x:.2f},{y:.2f},{z:.2f}"


def _warning_pos(sign: Sign) -> str:
    return f"{sign.x},{sign.y},{sign.z}"


def render_report(
    source: Path,
    signs: Sequence[Sign],
    components: Sequence[Component],
    warning_radius: float,
) -> tuple[str, dict[str, int]]:
    warnings = sorted(
        (sign for sign in signs if sign_kind(sign) == "WARNING"),
        key=lambda sign: (sign.x, sign.y, sign.z, sign.text),
    )
    links = build_links(components, warnings, warning_radius)

    with_warning = sum(bool(link.nearby_warnings) for link in links)
    without_warning = len(links) - with_warning
    covered_warning_positions = {
        (warning.x, warning.y, warning.z)
        for link in links
        for warning, _distance_value in link.nearby_warnings
    }
    stats = {
        "warnings": len(warnings),
        "portal_components": len(components),
        "portals_with_warning_nearby": with_warning,
        "portals_without_warning_nearby": without_warning,
        "warnings_near_portal": len(covered_warning_positions),
    }

    lines = [
        "# DeathRun Classic26 portal/Runner-warning route geometry",
        f"source={source.name}",
        (
            "route_portal_summary "
            f"warnings={stats['warnings']} "
            f"portal_components={stats['portal_components']} "
            f"portals_with_warning_nearby={stats['portals_with_warning_nearby']} "
            f"portals_without_warning_nearby={stats['portals_without_warning_nearby']} "
            f"warnings_near_portal={stats['warnings_near_portal']} "
            f"warning_radius={warning_radius:g}"
        ),
        "# Portal proximity is physical evidence only.",
        "# A portal with/without nearby warning signs is NOT automatically a checkpoint, start, finish, or route-order marker.",
        "",
    ]

    for index, link in enumerate(links, 1):
        nearest_pos = (
            _warning_pos(link.nearest_warning) if link.nearest_warning is not None else "none"
        )
        nearest_distance = (
            f"{link.nearest_distance:.2f}" if link.nearest_distance is not None else "none"
        )
        lines.append(
            "ROUTE_PORTAL\t"
            f"id={index:03d}\tsize={len(link.component.members)}\t"
            f"center={_center_text(link.component)}\t"
            f"bbox={_bbox_text(link.component)}\t"
            f"nearby_warnings={len(link.nearby_warnings)}\t"
            f"nearest_warning={nearest_pos}\t"
            f"nearest_distance={nearest_distance}"
        )
        for warning, distance_value in link.nearby_warnings:
            lines.append(
                "ROUTE_PORTAL_WARNING\t"
                f"portal={index:03d}\twarning_pos={_warning_pos(warning)}\t"
                f"distance={distance_value:.2f}\ttext={warning.text}"
            )

    return "\n".join(lines) + "\n", stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--warning-radius", type=float, default=20.0)
    parser.add_argument("--min-portals", type=int, default=0)
    parser.add_argument("--min-portals-with-warning", type=int, default=0)
    parser.add_argument("--min-portals-without-warning", type=int, default=0)
    args = parser.parse_args(argv)

    if args.warning_radius <= 0:
        parser.error("--warning-radius must be positive")

    _buttons, signs = parse_sign_probe(args.probe_report)
    points, portals = parse_candidate_probe(args.probe_report)
    del points
    components = portal_components(portals)
    report, stats = render_report(
        args.probe_report,
        signs,
        components,
        args.warning_radius,
    )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if stats["portal_components"] < args.min_portals:
        return 2
    if stats["portals_with_warning_nearby"] < args.min_portals_with_warning:
        return 3
    if stats["portals_without_warning_nearby"] < args.min_portals_without_warning:
        return 4
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
