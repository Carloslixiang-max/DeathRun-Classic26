#!/usr/bin/env python3
"""Correlate selected archived pressure plates with surviving route evidence.

The report is intentionally conservative: pressure plates are physical blocks,
not automatic checkpoints/start/finish triggers. It only measures geometry to
warning/action/hint signs and physical Nether Portal components.
"""

from __future__ import annotations

import argparse
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from anvil_cluster import Candidate, parse_probe_report as parse_candidate_probe
from anvil_sign_links import Sign, parse_probe_report as parse_sign_probe, sign_kind
from anvil_topology import Component, portal_components


@dataclass(frozen=True)
class PlateContext:
    plate: Candidate
    nearest_warning: tuple[Sign, float] | None
    nearest_action: tuple[Sign, float] | None
    nearest_hint: tuple[Sign, float] | None
    nearest_portal: tuple[int, Component, float] | None


def _distance_xyz(
    ax: float, ay: float, az: float,
    bx: float, by: float, bz: float,
) -> float:
    return math.sqrt((ax - bx) ** 2 + (ay - by) ** 2 + (az - bz) ** 2)


def _sign_distance(plate: Candidate, sign: Sign) -> float:
    return _distance_xyz(plate.x, plate.y, plate.z, sign.x, sign.y, sign.z)


def _portal_distance(plate: Candidate, component: Component) -> float:
    cx, cy, cz = component.center
    return _distance_xyz(plate.x, plate.y, plate.z, cx, cy, cz)


def _nearest_sign(
    plate: Candidate,
    signs: Sequence[Sign],
) -> tuple[Sign, float] | None:
    if not signs:
        return None
    ranked = sorted(
        ((sign, _sign_distance(plate, sign)) for sign in signs),
        key=lambda item: (
            item[1], item[0].x, item[0].y, item[0].z, item[0].text
        ),
    )
    return ranked[0]


def build_contexts(
    plates: Sequence[Candidate],
    signs: Sequence[Sign],
    portals: Sequence[Component],
) -> list[PlateContext]:
    warnings = [sign for sign in signs if sign_kind(sign) == "WARNING"]
    actions = [sign for sign in signs if sign_kind(sign) == "DIRECTIONAL_ACTION"]
    hints = [
        sign
        for sign in signs
        if sign_kind(sign) == "OTHER"
        and sign.text.strip().lower().startswith("hint")
    ]

    result: list[PlateContext] = []
    for plate in sorted(plates, key=lambda p: (p.x, p.y, p.z, p.name)):
        nearest_portal = None
        if portals:
            ranked_portals = sorted(
                (
                    (index, component, _portal_distance(plate, component))
                    for index, component in enumerate(portals, 1)
                ),
                key=lambda item: (item[2], item[0]),
            )
            nearest_portal = ranked_portals[0]
        result.append(
            PlateContext(
                plate=plate,
                nearest_warning=_nearest_sign(plate, warnings),
                nearest_action=_nearest_sign(plate, actions),
                nearest_hint=_nearest_sign(plate, hints),
                nearest_portal=nearest_portal,
            )
        )
    return result


def _sign_ref(value: tuple[Sign, float] | None) -> str:
    if value is None:
        return "none"
    sign, distance = value
    text = sign.text.replace("\t", " ").replace("\n", " ")
    return (
        f"{sign.x},{sign.y},{sign.z}"
        f"[distance={distance:.2f};text={text}]"
    )


def _bbox(component: Component) -> str:
    min_x, min_y, min_z, max_x, max_y, max_z = component.bbox
    return f"{min_x},{min_y},{min_z}:{max_x},{max_y},{max_z}"


def _portal_ref(value: tuple[int, Component, float] | None) -> str:
    if value is None:
        return "none"
    index, component, distance = value
    cx, cy, cz = component.center
    return (
        f"{index:03d}"
        f"[distance={distance:.2f};center={cx:.2f},{cy:.2f},{cz:.2f};"
        f"bbox={_bbox(component)}]"
    )


def render_report(
    source: Path,
    material: str,
    contexts: Sequence[PlateContext],
    total_pressure_plates: int,
) -> str:
    lines = [
        "# DeathRun Classic26 selected pressure-plate route context",
        f"source={source.name}",
        (
            "pressure_route_summary "
            f"material={material} selected_plates={len(contexts)} "
            f"all_pressure_plates={total_pressure_plates}"
        ),
        "# Pressure-plate proximity is physical evidence only.",
        "# A selected plate is NOT automatically a checkpoint, spawn, start, finish, trap or navigation trigger.",
        "",
    ]

    for index, item in enumerate(contexts, 1):
        plate = item.plate
        lines.append(
            "PRESSURE_ROUTE_PLATE\t"
            f"id={index:03d}\tmaterial={plate.name}\t"
            f"pos={plate.x},{plate.y},{plate.z}\t"
            f"nearest_warning={_sign_ref(item.nearest_warning)}\t"
            f"nearest_action={_sign_ref(item.nearest_action)}\t"
            f"nearest_hint={_sign_ref(item.nearest_hint)}\t"
            f"nearest_portal={_portal_ref(item.nearest_portal)}"
        )
    return "\n".join(lines) + "\n"


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument(
        "--material",
        default="minecraft:heavy_weighted_pressure_plate",
    )
    parser.add_argument("--output", type=Path)
    parser.add_argument("--min-plates", type=int, default=0)
    args = parser.parse_args(argv)

    points, portal_points = parse_candidate_probe(args.probe_report)
    _buttons, signs = parse_sign_probe(args.probe_report)
    all_plates = [item for item in points if item.category == "PRESSURE_PLATE"]
    selected = [item for item in all_plates if item.name == args.material]
    portals = portal_components(portal_points)
    contexts = build_contexts(selected, signs, portals)
    report = render_report(
        args.probe_report,
        args.material,
        contexts,
        len(all_plates),
    )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if len(selected) < args.min_plates:
        print(
            f"anvil_pressure_route_evidence: expected at least {args.min_plates} "
            f"{args.material} blocks, got {len(selected)}",
            file=sys.stderr,
        )
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
