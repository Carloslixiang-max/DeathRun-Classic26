#!/usr/bin/env python3
"""Correlate archived Death stage markers with surviving Nether Portal gates.

This is spatial evidence only. A nearby portal does not assign a checkpoint
number, route order, Runner respawn point, Death spawn, start or finish role.
"""

from __future__ import annotations

import argparse
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from anvil_cluster import parse_probe_report as parse_candidate_probe
from anvil_stage_evidence import StageMarker, marker_direction, parse_stage_markers
from anvil_topology import Component, portal_components


@dataclass(frozen=True)
class StagePortalLink:
    marker: StageMarker
    nearest_portal_id: int | None
    nearest_portal: Component | None
    nearest_distance: float | None
    portals_within_radius: tuple[tuple[int, Component, float], ...]


def _distance(marker: StageMarker, component: Component) -> float:
    cx, cy, cz = component.center
    return math.sqrt(
        (marker.x - cx) ** 2
        + (marker.y - cy) ** 2
        + (marker.z - cz) ** 2
    )


def build_links(
    markers: Sequence[StageMarker],
    portals: Sequence[Component],
    portal_radius: float,
) -> list[StagePortalLink]:
    result: list[StagePortalLink] = []
    for marker in markers:
        ranked = sorted(
            (
                (index, component, _distance(marker, component))
                for index, component in enumerate(portals, 1)
            ),
            key=lambda item: (item[2], item[0]),
        )
        nearby = tuple(item for item in ranked if item[2] <= portal_radius)
        result.append(
            StagePortalLink(
                marker=marker,
                nearest_portal_id=ranked[0][0] if ranked else None,
                nearest_portal=ranked[0][1] if ranked else None,
                nearest_distance=ranked[0][2] if ranked else None,
                portals_within_radius=nearby,
            )
        )
    return result


def _bbox(component: Component | None) -> str:
    if component is None:
        return "none"
    min_x, min_y, min_z, max_x, max_y, max_z = component.bbox
    return f"{min_x},{min_y},{min_z}:{max_x},{max_y},{max_z}"


def render_report(
    probe_source: Path,
    entity_source: Path,
    links: Sequence[StagePortalLink],
    portal_count: int,
    portal_radius: float,
    very_close_radius: float,
) -> tuple[str, dict[str, int]]:
    with_portal = sum(bool(link.portals_within_radius) for link in links)
    very_close = sum(
        link.nearest_distance is not None
        and link.nearest_distance <= very_close_radius
        for link in links
    )
    distinct_nearest = {
        link.nearest_portal_id
        for link in links
        if link.nearest_portal_id is not None
    }
    stats = {
        "stage_markers": len(links),
        "portal_components": portal_count,
        "markers_with_portal_within_radius": with_portal,
        "markers_with_very_close_portal": very_close,
        "distinct_nearest_portals": len(distinct_nearest),
    }

    lines = [
        "# DeathRun Classic26 stage-marker / portal-gate evidence",
        f"probe_source={probe_source.name}",
        f"entity_source={entity_source.name}",
        (
            "stage_portal_summary "
            f"stage_markers={stats['stage_markers']} "
            f"portal_components={stats['portal_components']} "
            f"markers_with_portal_within_radius={stats['markers_with_portal_within_radius']} "
            f"markers_with_very_close_portal={stats['markers_with_very_close_portal']} "
            f"distinct_nearest_portals={stats['distinct_nearest_portals']} "
            f"portal_radius={portal_radius:g} "
            f"very_close_radius={very_close_radius:g}"
        ),
        "# Nearest portal is geometry only; it does not assign checkpoint numbering or route order.",
        "# Named Next/Previous Stage markers are Death-control evidence, not Runner respawn markers.",
        "",
    ]

    for index, link in enumerate(links, 1):
        marker = link.marker
        portal_id = (
            f"{link.nearest_portal_id:03d}"
            if link.nearest_portal_id is not None
            else "none"
        )
        distance = (
            f"{link.nearest_distance:.2f}"
            if link.nearest_distance is not None
            else "none"
        )
        nearby = ";".join(
            f"{pid:03d}@{dist:.2f}"
            for pid, _component, dist in link.portals_within_radius
        ) or "none"
        lines.append(
            "STAGE_PORTAL_LINK\t"
            f"id={index:03d}\t"
            f"direction={marker_direction(marker.name)}\t"
            f"name={marker.name}\t"
            f"marker_pos={marker.x:.3f},{marker.y:.3f},{marker.z:.3f}\t"
            f"nearest_portal={portal_id}\t"
            f"nearest_distance={distance}\t"
            f"nearest_bbox={_bbox(link.nearest_portal)}\t"
            f"portals_within_radius={nearby}"
        )
    return "\n".join(lines) + "\n", stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("entity_evidence_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--portal-radius", type=float, default=30.0)
    parser.add_argument("--very-close-radius", type=float, default=8.0)
    parser.add_argument("--min-stage-markers", type=int, default=0)
    parser.add_argument("--min-markers-with-portal", type=int, default=0)
    parser.add_argument("--min-very-close", type=int, default=0)
    args = parser.parse_args(argv)

    if args.portal_radius <= 0 or args.very_close_radius <= 0:
        parser.error("portal radii must be positive")

    _points, portal_points = parse_candidate_probe(args.probe_report)
    portals = portal_components(portal_points)
    markers = parse_stage_markers(args.entity_evidence_report)
    links = build_links(markers, portals, args.portal_radius)
    report, stats = render_report(
        args.probe_report,
        args.entity_evidence_report,
        links,
        len(portals),
        args.portal_radius,
        args.very_close_radius,
    )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if stats["stage_markers"] < args.min_stage_markers:
        return 2
    if (
        stats["markers_with_portal_within_radius"]
        < args.min_markers_with_portal
    ):
        return 3
    if stats["markers_with_very_close_portal"] < args.min_very_close:
        return 4
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
