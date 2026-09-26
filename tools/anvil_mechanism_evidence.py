#!/usr/bin/env python3
"""Correlate archived DeathRun warning/action pairs with mechanism BlockStates.

This report combines three surviving evidence layers:
- Runner warning signs
- directional Death action signs
- nearby dispenser/hopper/dropper BlockStates and facing

A facing-consistent dispenser means only that its preserved cardinal direction
points approximately toward the warning-sign area. It does not prove the
original item contents, exact trap target cuboid, timing, or reset behavior.
"""

from __future__ import annotations

import argparse
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from anvil_sign_links import Sign, parse_probe_report, sign_kind
from anvil_trap_evidence import build_actions, build_pair_candidates, clean_label


@dataclass(frozen=True)
class Mechanism:
    name: str
    x: int
    y: int
    z: int
    properties: tuple[tuple[str, str], ...]

    def property(self, name: str) -> str | None:
        for key, value in self.properties:
            if key == name:
                return value
        return None


def parse_mechanisms(path: Path) -> list[Mechanism]:
    result: list[Mechanism] = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.startswith("BLOCK\tMECHANISM\t"):
            continue
        fields = raw.split("\t")
        if len(fields) < 6:
            continue
        try:
            properties: list[tuple[str, str]] = []
            props_field = next(
                (field[6:] for field in fields[6:] if field.startswith("props=")),
                "",
            )
            if props_field:
                for pair in props_field.split(","):
                    if ":" not in pair:
                        continue
                    key, value = pair.split(":", 1)
                    properties.append((key, value))
            result.append(
                Mechanism(
                    name=fields[2],
                    x=int(fields[3]),
                    y=int(fields[4]),
                    z=int(fields[5]),
                    properties=tuple(sorted(properties)),
                )
            )
        except ValueError:
            continue

    result.sort(key=lambda item: (item.x, item.y, item.z, item.name))
    return result


def distance(sign: Sign, mechanism: Mechanism) -> float:
    return math.sqrt(
        (sign.x - mechanism.x) ** 2
        + (sign.y - mechanism.y) ** 2
        + (sign.z - mechanism.z) ** 2
    )


def facing_consistent(mechanism: Mechanism, target: Sign) -> bool:
    """Whether a dispenser/dropper axis points roughly toward target.

    Horizontal facings use a 90-degree cone (forward component positive and at
    least as large as lateral offset). Vertical facings use the same idea with
    horizontal radius as the lateral component.
    """
    facing = mechanism.property("facing")
    if facing is None:
        return False

    dx = target.x - mechanism.x
    dy = target.y - mechanism.y
    dz = target.z - mechanism.z

    if facing == "east":
        return dx > 0 and abs(dz) <= dx
    if facing == "west":
        return dx < 0 and abs(dz) <= -dx
    if facing == "south":
        return dz > 0 and abs(dx) <= dz
    if facing == "north":
        return dz < 0 and abs(dx) <= -dz
    horizontal = math.hypot(dx, dz)
    if facing == "up":
        return dy > 0 and horizontal <= dy
    if facing == "down":
        return dy < 0 and horizontal <= -dy
    return False


def nearby_mechanisms(
    warning: Sign,
    mechanisms: Sequence[Mechanism],
    radius: float,
) -> tuple[Mechanism, ...]:
    return tuple(
        sorted(
            (item for item in mechanisms if distance(warning, item) <= radius),
            key=lambda item: (
                distance(warning, item),
                item.x,
                item.y,
                item.z,
                item.name,
            ),
        )
    )


def _pos(sign: Sign) -> str:
    return f"{sign.x},{sign.y},{sign.z}"


def _props(mechanism: Mechanism) -> str:
    return ",".join(f"{key}:{value}" for key, value in mechanism.properties) or "none"


def _bbox(mechanisms: Sequence[Mechanism]) -> tuple[int, int, int, int, int, int]:
    return (
        min(item.x for item in mechanisms),
        min(item.y for item in mechanisms),
        min(item.z for item in mechanisms),
        max(item.x for item in mechanisms),
        max(item.y for item in mechanisms),
        max(item.z for item in mechanisms),
    )


def _bbox_text(mechanisms: Sequence[Mechanism]) -> str:
    min_x, min_y, min_z, max_x, max_y, max_z = _bbox(mechanisms)
    return f"{min_x},{min_y},{min_z}:{max_x},{max_y},{max_z}"


def dispenser_banks(
    dispensers: Sequence[Mechanism],
) -> dict[str, tuple[Mechanism, ...]]:
    groups: dict[str, list[Mechanism]] = {}
    for item in dispensers:
        facing = item.property("facing")
        if facing not in {"north", "south", "east", "west"}:
            continue
        groups.setdefault(facing, []).append(item)
    return {
        facing: tuple(
            sorted(items, key=lambda item: (item.x, item.y, item.z, item.name))
        )
        for facing, items in sorted(groups.items())
    }


def _range_overlap(
    left_min: int,
    left_max: int,
    right_min: int,
    right_max: int,
) -> tuple[int, int] | None:
    low = max(left_min, right_min)
    high = min(left_max, right_max)
    return (low, high) if low <= high else None


def opposing_bank_evidence(
    banks: dict[str, tuple[Mechanism, ...]],
) -> list[tuple[str, str, str, tuple[int, int] | None, tuple[int, int] | None]]:
    """Return opposing horizontal-bank geometry without inferring a target cuboid.

    Result tuples are (axis, facing_a, facing_b, cross_axis_overlap, y_overlap).
    """
    result = []
    for axis, first, second in (
        ("z", "north", "south"),
        ("x", "west", "east"),
    ):
        left = banks.get(first)
        right = banks.get(second)
        if not left or not right:
            continue
        left_bbox = _bbox(left)
        right_bbox = _bbox(right)
        if axis == "z":
            cross = _range_overlap(left_bbox[0], left_bbox[3], right_bbox[0], right_bbox[3])
        else:
            cross = _range_overlap(left_bbox[2], left_bbox[5], right_bbox[2], right_bbox[5])
        vertical = _range_overlap(left_bbox[1], left_bbox[4], right_bbox[1], right_bbox[4])
        result.append((axis, first, second, cross, vertical))
    return result


def _range_text(value: tuple[int, int] | None) -> str:
    return "none" if value is None else f"{value[0]}:{value[1]}"


def render_report(
    source: Path,
    signs: Sequence[Sign],
    mechanisms: Sequence[Mechanism],
    pair_distance: float,
    mechanism_radius: float,
) -> tuple[str, dict[str, int]]:
    actions = build_actions(signs)
    warnings = sorted(
        (sign for sign in signs if sign_kind(sign) == "WARNING"),
        key=lambda sign: (sign.x, sign.y, sign.z, sign.text),
    )
    pairs, unmatched_warnings, unmatched_actions = build_pair_candidates(
        warnings,
        actions,
        pair_distance,
    )

    pairs_with_mechanisms = 0
    pairs_with_dispensers = 0
    pairs_with_consistent = 0
    fire_arrow_pairs = 0
    fire_arrow_consistent = 0
    fire_arrow_pairs_with_banks = 0
    fire_arrow_pairs_with_opposing_banks = 0
    details: list[str] = []

    for index, pair in enumerate(pairs, 1):
        nearby = nearby_mechanisms(pair.warning, mechanisms, mechanism_radius)
        dispensers = tuple(
            item
            for item in nearby
            if item.name in {"minecraft:dispenser", "minecraft:dropper"}
        )
        consistent = tuple(
            item for item in dispensers if facing_consistent(item, pair.warning)
        )

        if nearby:
            pairs_with_mechanisms += 1
        if dispensers:
            pairs_with_dispensers += 1
        if consistent:
            pairs_with_consistent += 1

        label = pair.action.label
        is_fire_arrow = label.lower() == "fire arrows"
        banks = dispenser_banks(dispensers) if is_fire_arrow else {}
        opposing = opposing_bank_evidence(banks) if is_fire_arrow else []
        if is_fire_arrow:
            fire_arrow_pairs += 1
            if consistent:
                fire_arrow_consistent += 1
            if banks:
                fire_arrow_pairs_with_banks += 1
            if opposing:
                fire_arrow_pairs_with_opposing_banks += 1

        details.append(
            "TRAP_MECHANISM_PAIR\t"
            f"id={index:03d}\taction={label}\t"
            f"warning_pos={_pos(pair.warning)}\t"
            f"action_pos={_pos(pair.action.sign)}\t"
            f"shared={','.join(pair.shared_tokens) or 'none'}\t"
            f"warning_action_distance={pair.distance:.2f}\t"
            f"nearby_mechanisms={len(nearby)}\t"
            f"nearby_dispensers={len(dispensers)}\t"
            f"facing_consistent_dispensers={len(consistent)}\t"
            f"warning_text={pair.warning.text}"
        )
        if is_fire_arrow:
            for facing, bank in banks.items():
                faces_warning = sum(
                    facing_consistent(item, pair.warning) for item in bank
                )
                details.append(
                    "FIRE_ARROW_BANK\t"
                    f"pair={index:03d}\tfacing={facing}\tcount={len(bank)}\t"
                    f"bbox={_bbox_text(bank)}\t"
                    f"faces_warning={faces_warning}"
                )
            for axis, first, second, cross, vertical in opposing:
                cross_axis = "x" if axis == "z" else "z"
                details.append(
                    "FIRE_ARROW_OPPOSING_BANKS\t"
                    f"pair={index:03d}\taxis={axis}\t"
                    f"facings={first},{second}\t"
                    f"cross_axis={cross_axis}\t"
                    f"cross_overlap={_range_text(cross)}\t"
                    f"vertical_overlap={_range_text(vertical)}"
                )

        for mechanism in nearby:
            details.append(
                "TRAP_MECHANISM\t"
                f"pair={index:03d}\t"
                f"name={mechanism.name}\t"
                f"pos={mechanism.x},{mechanism.y},{mechanism.z}\t"
                f"distance={distance(pair.warning, mechanism):.2f}\t"
                f"facing={mechanism.property('facing') or 'unknown'}\t"
                f"faces_warning={str(facing_consistent(mechanism, pair.warning)).lower()}\t"
                f"props={_props(mechanism)}"
            )

    stats = {
        "pairs": len(pairs),
        "unmatched_warnings": len(unmatched_warnings),
        "unmatched_actions": len(unmatched_actions),
        "mechanisms": len(mechanisms),
        "pairs_with_mechanisms": pairs_with_mechanisms,
        "pairs_with_dispensers": pairs_with_dispensers,
        "pairs_with_consistent": pairs_with_consistent,
        "fire_arrow_pairs": fire_arrow_pairs,
        "fire_arrow_consistent": fire_arrow_consistent,
        "fire_arrow_pairs_with_banks": fire_arrow_pairs_with_banks,
        "fire_arrow_pairs_with_opposing_banks": fire_arrow_pairs_with_opposing_banks,
    }
    lines = [
        "# DeathRun Classic26 warning/action/mechanism evidence",
        f"source={source.name}",
        (
            "mechanism_evidence_summary "
            f"pairs={stats['pairs']} "
            f"unmatched_warnings={stats['unmatched_warnings']} "
            f"unmatched_actions={stats['unmatched_actions']} "
            f"mechanisms={stats['mechanisms']} "
            f"pairs_with_mechanisms={stats['pairs_with_mechanisms']} "
            f"pairs_with_dispensers={stats['pairs_with_dispensers']} "
            f"pairs_with_facing_consistent_dispensers={stats['pairs_with_consistent']} "
            f"fire_arrow_pairs={stats['fire_arrow_pairs']} "
            f"fire_arrow_pairs_with_facing_consistent_dispensers={stats['fire_arrow_consistent']} "
            f"fire_arrow_pairs_with_banks={stats['fire_arrow_pairs_with_banks']} "
            f"fire_arrow_pairs_with_opposing_banks={stats['fire_arrow_pairs_with_opposing_banks']} "
            f"pair_distance={pair_distance:g} mechanism_radius={mechanism_radius:g}"
        ),
        "# Facing consistency is spatial corroboration only, not a confirmed trap definition.",
        "# FIRE_ARROW_BANK / OPPOSING_BANKS describe preserved in-game dispenser geometry only.",
        "# Bank overlap is not an exact projectile path, target cuboid, payload or timing definition.",
        "",
        *details,
    ]
    return "\n".join(lines) + "\n", stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--pair-distance", type=float, default=30.0)
    parser.add_argument("--mechanism-radius", type=float, default=14.0)
    parser.add_argument("--min-pairs-with-dispensers", type=int, default=0)
    parser.add_argument("--min-facing-consistent", type=int, default=0)
    parser.add_argument("--min-fire-arrow-consistent", type=int, default=0)
    parser.add_argument("--min-fire-arrow-opposing-banks", type=int, default=0)
    args = parser.parse_args(argv)

    if args.pair_distance <= 0 or args.mechanism_radius <= 0:
        parser.error("distance/radius must be positive")

    _buttons, signs = parse_probe_report(args.probe_report)
    mechanisms = parse_mechanisms(args.probe_report)
    report, stats = render_report(
        args.probe_report,
        signs,
        mechanisms,
        args.pair_distance,
        args.mechanism_radius,
    )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if stats["pairs_with_dispensers"] < args.min_pairs_with_dispensers:
        print(
            "anvil_mechanism_evidence: expected at least "
            f"{args.min_pairs_with_dispensers} pairs with dispensers, "
            f"got {stats['pairs_with_dispensers']}",
            file=sys.stderr,
        )
        return 2
    if stats["pairs_with_consistent"] < args.min_facing_consistent:
        print(
            "anvil_mechanism_evidence: expected at least "
            f"{args.min_facing_consistent} facing-consistent pairs, "
            f"got {stats['pairs_with_consistent']}",
            file=sys.stderr,
        )
        return 3
    if stats["fire_arrow_consistent"] < args.min_fire_arrow_consistent:
        print(
            "anvil_mechanism_evidence: expected at least "
            f"{args.min_fire_arrow_consistent} Fire Arrows pairs with "
            "facing-consistent dispensers, "
            f"got {stats['fire_arrow_consistent']}",
            file=sys.stderr,
        )
        return 4
    if (
        stats["fire_arrow_pairs_with_opposing_banks"]
        < args.min_fire_arrow_opposing_banks
    ):
        print(
            "anvil_mechanism_evidence: expected at least "
            f"{args.min_fire_arrow_opposing_banks} Fire Arrows pairs with "
            "opposing dispenser banks, got "
            f"{stats['fire_arrow_pairs_with_opposing_banks']}",
            file=sys.stderr,
        )
        return 5
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
