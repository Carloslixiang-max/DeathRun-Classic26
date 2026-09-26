#!/usr/bin/env python3
"""Correlate archived DeathRun stage markers with nearby control evidence.

The tool uses preserved named entity metadata (for example "Next Stage" and
"Previous Stage") and links it to nearby buttons, directional action signs,
hoppers and dispensers. It is a spatial evidence report only; it does not infer
a Classic26 stage number, Death spawn, trap type, or target region.
"""

from __future__ import annotations

import argparse
import math
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from anvil_sign_links import Button, Sign, parse_probe_report, sign_kind


STAGE_NAME_RE = re.compile(r"\bstage\b", re.IGNORECASE)


@dataclass(frozen=True)
class StageMarker:
    entity_id: str
    name: str
    x: float
    y: float
    z: float


@dataclass(frozen=True)
class Mechanism:
    entity_id: str
    x: float
    y: float
    z: float


@dataclass(frozen=True)
class PressurePlate:
    name: str
    x: int
    y: int
    z: int


@dataclass(frozen=True)
class StageNeighborhood:
    marker: StageMarker
    buttons: tuple[Button, ...]
    actions: tuple[Sign, ...]
    mechanisms: tuple[Mechanism, ...]
    anchor_plates: tuple[PressurePlate, ...]


def _fields(raw: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for field in raw.split("\t")[1:]:
        if "=" not in field:
            continue
        key, value = field.split("=", 1)
        result[key] = value
    return result


def _parse_xyz(raw: str) -> tuple[float, float, float] | None:
    parts = raw.split(",")
    if len(parts) != 3:
        return None
    try:
        return float(parts[0]), float(parts[1]), float(parts[2])
    except ValueError:
        return None


def parse_stage_markers(path: Path) -> list[StageMarker]:
    markers: list[StageMarker] = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.startswith("ENTITY_EVIDENCE\t"):
            continue
        fields = _fields(raw)
        name = fields.get("name", "")
        if not name or name == "none" or not STAGE_NAME_RE.search(name):
            continue
        xyz = _parse_xyz(fields.get("pos", ""))
        if xyz is None:
            continue
        markers.append(
            StageMarker(
                entity_id=fields.get("id", "(unknown)"),
                name=name,
                x=xyz[0],
                y=xyz[1],
                z=xyz[2],
            )
        )
    markers.sort(key=lambda item: (item.x, item.y, item.z, item.name.lower()))
    return markers


def parse_pressure_plates(path: Path) -> list[PressurePlate]:
    plates: list[PressurePlate] = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.startswith("BLOCK\tPRESSURE_PLATE\t"):
            continue
        fields = raw.split("\t")
        if len(fields) < 6:
            continue
        try:
            plates.append(
                PressurePlate(
                    name=fields[2],
                    x=int(fields[3]),
                    y=int(fields[4]),
                    z=int(fields[5]),
                )
            )
        except ValueError:
            continue
    plates.sort(key=lambda item: (item.x, item.y, item.z, item.name))
    return plates


def parse_mechanisms(path: Path) -> list[Mechanism]:
    mechanisms: list[Mechanism] = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.startswith("BLOCK_ENTITY_EVIDENCE\t"):
            continue
        fields = _fields(raw)
        entity_id = fields.get("id", "")
        if entity_id not in {"minecraft:hopper", "minecraft:dispenser"}:
            continue
        xyz = _parse_xyz(fields.get("pos", ""))
        if xyz is None:
            continue
        mechanisms.append(
            Mechanism(
                entity_id=entity_id,
                x=xyz[0],
                y=xyz[1],
                z=xyz[2],
            )
        )
    mechanisms.sort(key=lambda item: (item.x, item.y, item.z, item.entity_id))
    return mechanisms


def horizontal_distance(
    left_x: float,
    left_z: float,
    right_x: float,
    right_z: float,
) -> float:
    return math.hypot(left_x - right_x, left_z - right_z)


def _within(
    marker: StageMarker,
    x: float,
    y: float,
    z: float,
    horizontal_radius: float,
    vertical_radius: float,
) -> bool:
    return (
        horizontal_distance(marker.x, marker.z, x, z) <= horizontal_radius
        and abs(marker.y - y) <= vertical_radius
    )


def build_neighborhood(
    marker: StageMarker,
    buttons: Sequence[Button],
    signs: Sequence[Sign],
    mechanisms: Sequence[Mechanism],
    plates: Sequence[PressurePlate],
    horizontal_radius: float,
    vertical_radius: float,
    anchor_horizontal_radius: float = 1.0,
    anchor_vertical_below_min: float = 0.5,
    anchor_vertical_below_max: float = 2.0,
) -> StageNeighborhood:
    nearby_buttons = tuple(
        sorted(
            (
                button
                for button in buttons
                if _within(
                    marker,
                    button.x,
                    button.y,
                    button.z,
                    horizontal_radius,
                    vertical_radius,
                )
            ),
            key=lambda item: (
                horizontal_distance(marker.x, marker.z, item.x, item.z),
                abs(marker.y - item.y),
                item.x,
                item.y,
                item.z,
            ),
        )
    )
    nearby_actions = tuple(
        sorted(
            (
                sign
                for sign in signs
                if sign_kind(sign) == "DIRECTIONAL_ACTION"
                and _within(
                    marker,
                    sign.x,
                    sign.y,
                    sign.z,
                    horizontal_radius,
                    vertical_radius,
                )
            ),
            key=lambda item: (
                horizontal_distance(marker.x, marker.z, item.x, item.z),
                abs(marker.y - item.y),
                item.x,
                item.y,
                item.z,
            ),
        )
    )
    anchor_plates = tuple(
        sorted(
            (
                plate
                for plate in plates
                if horizontal_distance(marker.x, marker.z, plate.x, plate.z)
                <= anchor_horizontal_radius
                and anchor_vertical_below_min
                <= marker.y - plate.y
                <= anchor_vertical_below_max
            ),
            key=lambda item: (
                horizontal_distance(marker.x, marker.z, item.x, item.z),
                abs(marker.y - item.y),
                item.x,
                item.y,
                item.z,
                item.name,
            ),
        )
    )
    nearby_mechanisms = tuple(
        sorted(
            (
                mechanism
                for mechanism in mechanisms
                if _within(
                    marker,
                    mechanism.x,
                    mechanism.y,
                    mechanism.z,
                    horizontal_radius,
                    vertical_radius,
                )
            ),
            key=lambda item: (
                horizontal_distance(marker.x, marker.z, item.x, item.z),
                abs(marker.y - item.y),
                item.x,
                item.y,
                item.z,
                item.entity_id,
            ),
        )
    )
    return StageNeighborhood(
        marker=marker,
        buttons=nearby_buttons,
        actions=nearby_actions,
        mechanisms=nearby_mechanisms,
        anchor_plates=anchor_plates,
    )


def marker_direction(name: str) -> str:
    tokens = set(re.findall(r"[a-z]+", name.lower()))
    if "next" in tokens:
        return "NEXT"
    if "previous" in tokens or "prev" in tokens:
        return "PREVIOUS"
    return "STAGE"


def _button_text(button: Button) -> str:
    return f"{button.x},{button.y},{button.z}:{button.name}"


def _sign_text(sign: Sign) -> str:
    return f"{sign.x},{sign.y},{sign.z}:{sign.text}"


def _plate_text(plate: PressurePlate) -> str:
    return f"{plate.x},{plate.y},{plate.z}:{plate.name}"


def _mechanism_text(mechanism: Mechanism) -> str:
    x = int(mechanism.x) if mechanism.x.is_integer() else mechanism.x
    y = int(mechanism.y) if mechanism.y.is_integer() else mechanism.y
    z = int(mechanism.z) if mechanism.z.is_integer() else mechanism.z
    return f"{x},{y},{z}:{mechanism.entity_id}"


def render_report(
    probe_path: Path,
    entity_path: Path,
    block_entity_path: Path,
    horizontal_radius: float,
    vertical_radius: float,
) -> str:
    buttons, signs = parse_probe_report(probe_path)
    markers = parse_stage_markers(entity_path)
    mechanisms = parse_mechanisms(block_entity_path)
    plates = parse_pressure_plates(probe_path)
    neighborhoods = [
        build_neighborhood(
            marker,
            buttons,
            signs,
            mechanisms,
            plates,
            horizontal_radius,
            vertical_radius,
        )
        for marker in markers
    ]

    next_count = sum(marker_direction(item.marker.name) == "NEXT" for item in neighborhoods)
    previous_count = sum(
        marker_direction(item.marker.name) == "PREVIOUS" for item in neighborhoods
    )
    with_buttons = sum(bool(item.buttons) for item in neighborhoods)
    with_actions = sum(bool(item.actions) for item in neighborhoods)
    with_mechanisms = sum(bool(item.mechanisms) for item in neighborhoods)
    with_anchor_plates = sum(bool(item.anchor_plates) for item in neighborhoods)
    unique_anchor_plates = sum(len(item.anchor_plates) == 1 for item in neighborhoods)

    lines = [
        "# DeathRun Classic26 stage-control evidence",
        f"probe_source={probe_path.name}",
        f"entity_source={entity_path.name}",
        f"block_entity_source={block_entity_path.name}",
        (
            "stage_evidence_summary "
            f"stage_markers={len(neighborhoods)} next_markers={next_count} "
            f"previous_markers={previous_count} markers_with_buttons={with_buttons} "
            f"markers_with_actions={with_actions} "
            f"markers_with_mechanisms={with_mechanisms} "
            f"markers_with_anchor_plates={with_anchor_plates} "
            f"markers_with_unique_anchor_plate={unique_anchor_plates} "
            f"horizontal_radius={horizontal_radius:g} "
            f"vertical_radius={vertical_radius:g}"
        ),
        "# STAGE_MARKER_NEIGHBORHOOD is spatial evidence only; no gameplay role is inferred.",
        "# STAGE_ANCHOR_PLATE means a pressure plate is directly beneath/adjacent to the named stage marker.",
        "# It strengthens physical stage-control geometry but does not define stage numbering or player spawn.",
        "",
    ]

    for index, item in enumerate(neighborhoods, 1):
        marker = item.marker
        lines.append(
            "STAGE_MARKER_NEIGHBORHOOD\t"
            f"id={index:03d}\tdirection={marker_direction(marker.name)}\t"
            f"name={marker.name}\t"
            f"pos={marker.x:.3f},{marker.y:.3f},{marker.z:.3f}\t"
            f"buttons={len(item.buttons)}\tactions={len(item.actions)}\t"
            f"mechanisms={len(item.mechanisms)}\t"
            f"anchor_plates={len(item.anchor_plates)}"
        )
        for plate in item.anchor_plates:
            lines.append(
                f"STAGE_ANCHOR_PLATE\tmarker={index:03d}\t{_plate_text(plate)}"
            )
        for button in item.buttons:
            lines.append(
                f"STAGE_BUTTON\tmarker={index:03d}\t{_button_text(button)}"
            )
        for sign in item.actions:
            lines.append(
                f"STAGE_ACTION\tmarker={index:03d}\t{_sign_text(sign)}"
            )
        for mechanism in item.mechanisms:
            lines.append(
                f"STAGE_MECHANISM\tmarker={index:03d}\t{_mechanism_text(mechanism)}"
            )

    return "\n".join(lines) + "\n"


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("entity_evidence_report", type=Path)
    parser.add_argument("block_entity_evidence_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--horizontal-radius", type=float, default=12.0)
    parser.add_argument("--vertical-radius", type=float, default=10.0)
    parser.add_argument("--min-stage-markers", type=int, default=0)
    parser.add_argument("--min-stage-anchor-plates", type=int, default=0)
    args = parser.parse_args(argv)

    if args.horizontal_radius <= 0 or args.vertical_radius < 0:
        parser.error("radii must be positive (vertical may be zero)")

    report = render_report(
        args.probe_report,
        args.entity_evidence_report,
        args.block_entity_evidence_report,
        args.horizontal_radius,
        args.vertical_radius,
    )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    markers = parse_stage_markers(args.entity_evidence_report)
    if len(markers) < args.min_stage_markers:
        print(
            f"anvil_stage_evidence: expected at least {args.min_stage_markers} stage markers, got {len(markers)}",
            file=sys.stderr,
        )
        return 2

    buttons, signs = parse_probe_report(args.probe_report)
    mechanisms = parse_mechanisms(args.block_entity_evidence_report)
    plates = parse_pressure_plates(args.probe_report)
    neighborhoods = [
        build_neighborhood(
            marker,
            buttons,
            signs,
            mechanisms,
            plates,
            args.horizontal_radius,
            args.vertical_radius,
        )
        for marker in markers
    ]
    anchored = sum(bool(item.anchor_plates) for item in neighborhoods)
    if anchored < args.min_stage_anchor_plates:
        print(
            "anvil_stage_evidence: expected at least "
            f"{args.min_stage_anchor_plates} stage markers with an anchor "
            f"pressure plate, got {anchored}",
            file=sys.stderr,
        )
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
