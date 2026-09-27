#!/usr/bin/env python3
"""Correlate archived level.dat save-camera orientation with route evidence.

The saved Player.Pos/Rotation is treated only as an author/save-camera anchor.
It is not a Runner spawn, Death spawn, checkpoint, start or finish definition.
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
from leveldat_probe import extract_metadata, read_level_dat


@dataclass(frozen=True)
class CameraTarget:
    kind: str
    target_id: str
    x: float
    y: float
    z: float
    distance: float
    angle_degrees: float
    detail: str


def view_vector(yaw_degrees: float, pitch_degrees: float) -> tuple[float, float, float]:
    """Minecraft player look vector for yaw/pitch in degrees."""
    yaw = math.radians(yaw_degrees)
    pitch = math.radians(pitch_degrees)
    return (
        -math.sin(yaw) * math.cos(pitch),
        -math.sin(pitch),
        math.cos(yaw) * math.cos(pitch),
    )


def _target_metrics(
    camera_pos: tuple[float, float, float],
    camera_view: tuple[float, float, float],
    target: tuple[float, float, float],
) -> tuple[float, float]:
    vx = target[0] - camera_pos[0]
    vy = target[1] - camera_pos[1]
    vz = target[2] - camera_pos[2]
    distance = math.sqrt(vx * vx + vy * vy + vz * vz)
    if distance == 0:
        return 0.0, 0.0

    dot = (
        camera_view[0] * vx
        + camera_view[1] * vy
        + camera_view[2] * vz
    ) / distance
    dot = max(-1.0, min(1.0, dot))
    return distance, math.degrees(math.acos(dot))


def build_targets(
    camera_pos: tuple[float, float, float],
    camera_rotation: tuple[float, float],
    portals: Sequence[Component],
    title_signs: Sequence[Sign],
) -> list[CameraTarget]:
    camera_view = view_vector(camera_rotation[0], camera_rotation[1])
    result: list[CameraTarget] = []

    for index, portal in enumerate(portals, 1):
        cx, cy, cz = portal.center
        distance, angle = _target_metrics(
            camera_pos,
            camera_view,
            (cx, cy, cz),
        )
        min_x, min_y, min_z, max_x, max_y, max_z = portal.bbox
        result.append(
            CameraTarget(
                kind="PORTAL",
                target_id=f"{index:03d}",
                x=cx,
                y=cy,
                z=cz,
                distance=distance,
                angle_degrees=angle,
                detail=(
                    f"bbox={min_x},{min_y},{min_z}:"
                    f"{max_x},{max_y},{max_z}"
                ),
            )
        )

    for index, sign in enumerate(title_signs, 1):
        distance, angle = _target_metrics(
            camera_pos,
            camera_view,
            (float(sign.x), float(sign.y), float(sign.z)),
        )
        result.append(
            CameraTarget(
                kind="TITLE",
                target_id=f"{index:03d}",
                x=float(sign.x),
                y=float(sign.y),
                z=float(sign.z),
                distance=distance,
                angle_degrees=angle,
                detail=f"text={sign.text}",
            )
        )

    return result


def render_report(
    level_dat: Path,
    probe_report: Path,
    camera_pos: tuple[float, float, float] | None,
    camera_rotation: tuple[float, float] | None,
    targets: Sequence[CameraTarget],
) -> tuple[str, dict[str, float | int | str]]:
    portal_targets = [item for item in targets if item.kind == "PORTAL"]
    title_targets = [item for item in targets if item.kind == "TITLE"]
    best_portal = min(
        portal_targets,
        key=lambda item: (item.angle_degrees, item.distance, item.target_id),
        default=None,
    )
    best_title = min(
        title_targets,
        key=lambda item: (item.angle_degrees, item.distance, item.target_id),
        default=None,
    )

    stats: dict[str, float | int | str] = {
        "saved_camera": 1 if camera_pos is not None and camera_rotation is not None else 0,
        "portal_targets": len(portal_targets),
        "title_targets": len(title_targets),
        "best_portal_id": best_portal.target_id if best_portal else "none",
        "best_portal_angle": best_portal.angle_degrees if best_portal else 999.0,
        "best_title_angle": best_title.angle_degrees if best_title else 999.0,
    }

    pos_text = (
        ",".join(f"{value:.3f}" for value in camera_pos)
        if camera_pos is not None
        else "none"
    )
    rotation_text = (
        ",".join(f"{value:.3f}" for value in camera_rotation)
        if camera_rotation is not None
        else "none"
    )
    lines = [
        "# DeathRun Classic26 archived save-camera / route evidence",
        f"level_source={level_dat.name}",
        f"probe_source={probe_report.name}",
        (
            "camera_route_summary "
            f"saved_camera={stats['saved_camera']} "
            f"camera_pos={pos_text} "
            f"camera_rotation={rotation_text} "
            f"portal_targets={stats['portal_targets']} "
            f"title_targets={stats['title_targets']} "
            f"best_portal={stats['best_portal_id']} "
            f"best_portal_angle={float(stats['best_portal_angle']):.2f} "
            f"best_title_angle={float(stats['best_title_angle']):.2f}"
        ),
        "# Saved Player.Pos/Rotation is an author/save-camera anchor only.",
        "# Angular alignment does NOT define spawn, checkpoint order, start or finish.",
        "",
    ]

    for item in sorted(
        targets,
        key=lambda target: (
            target.kind,
            target.angle_degrees,
            target.distance,
            target.target_id,
        ),
    ):
        lines.append(
            "CAMERA_TARGET\t"
            f"kind={item.kind}\t"
            f"id={item.target_id}\t"
            f"target={item.x:.2f},{item.y:.2f},{item.z:.2f}\t"
            f"distance={item.distance:.2f}\t"
            f"angle={item.angle_degrees:.2f}\t"
            f"{item.detail}"
        )

    return "\n".join(lines) + "\n", stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("level_dat", type=Path)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--min-portals", type=int, default=0)
    parser.add_argument("--max-best-portal-angle", type=float)
    args = parser.parse_args(argv)

    metadata = extract_metadata(read_level_dat(args.level_dat))
    raw_pos = metadata.get("player_save_pos")
    raw_rotation = metadata.get("player_save_rotation")
    camera_pos = (
        tuple(float(value) for value in raw_pos)
        if isinstance(raw_pos, tuple) and len(raw_pos) == 3
        else None
    )
    camera_rotation = (
        tuple(float(value) for value in raw_rotation)
        if isinstance(raw_rotation, tuple) and len(raw_rotation) == 2
        else None
    )

    _points, portal_points = parse_candidate_probe(args.probe_report)
    portals = portal_components(portal_points)
    _buttons, signs = parse_sign_probe(args.probe_report)
    title_signs = [sign for sign in signs if sign_kind(sign) == "TITLE"]

    targets = (
        build_targets(camera_pos, camera_rotation, portals, title_signs)
        if camera_pos is not None and camera_rotation is not None
        else []
    )
    report, stats = render_report(
        args.level_dat,
        args.probe_report,
        camera_pos,
        camera_rotation,
        targets,
    )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if int(stats["portal_targets"]) < args.min_portals:
        return 2
    if args.max_best_portal_angle is not None:
        if float(stats["best_portal_angle"]) > args.max_best_portal_angle:
            return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
