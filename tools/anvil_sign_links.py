#!/usr/bin/env python3
"""Link archived DeathRun action signs to nearby button evidence.

Directional action signs are correlated with surviving buttons. When the sign
block's facing/rotation state survives, <<< / >>> is projected into world-space
and used to reduce button ambiguity. This is evidence correlation only, never
automatic Classic26 trap classification.
"""

from __future__ import annotations

import argparse
import math
import re
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


NUMERIC_PAIR_RE = re.compile(r"^\s*\d+\s*\|\s*\d+\s*$")


@dataclass(frozen=True)
class Button:
    name: str
    x: int
    y: int
    z: int


@dataclass(frozen=True)
class Sign:
    entity_id: str
    x: int
    y: int
    z: int
    text: str
    block_name: str | None = None
    properties: tuple[tuple[str, str], ...] = ()


@dataclass(frozen=True)
class LinkedAction:
    sign: Sign
    nearest_button: Button | None
    nearest_distance: float | None
    nearby_buttons: tuple[Button, ...]
    arrow_buttons: tuple[Button, ...]
    arrow_direction: str | None
    front_vector: tuple[float, float] | None


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


def parse_probe_report(path: Path) -> tuple[list[Button], list[Sign]]:
    buttons: list[Button] = []
    raw_signs: list[Sign] = []
    sign_states: dict[tuple[int, int, int], tuple[str, tuple[tuple[str, str], ...]]] = {}

    for raw in path.read_text(encoding="utf-8").splitlines():
        if raw.startswith("BLOCK\tBUTTON\t"):
            fields = raw.split("\t")
            if len(fields) < 6:
                continue
            try:
                buttons.append(
                    Button(fields[2], int(fields[3]), int(fields[4]), int(fields[5]))
                )
            except ValueError:
                continue
        elif raw.startswith("BLOCK\tSIGN_BLOCK\t"):
            fields = raw.split("\t")
            if len(fields) < 6:
                continue
            try:
                pos = (int(fields[3]), int(fields[4]), int(fields[5]))
                sign_states[pos] = (fields[2], _parse_properties(fields[6:]))
            except ValueError:
                continue
        elif raw.startswith("SIGN\t"):
            fields = raw.split("\t")
            if len(fields) < 5:
                continue
            try:
                text_field = next(
                    (field[5:] for field in fields[5:] if field.startswith("text=")),
                    "",
                )
                raw_signs.append(
                    Sign(
                        entity_id=fields[1],
                        x=int(fields[2]),
                        y=int(fields[3]),
                        z=int(fields[4]),
                        text=text_field,
                    )
                )
            except ValueError:
                continue

    signs: list[Sign] = []
    for sign in raw_signs:
        state = sign_states.get((sign.x, sign.y, sign.z))
        signs.append(
            Sign(
                entity_id=sign.entity_id,
                x=sign.x,
                y=sign.y,
                z=sign.z,
                text=sign.text,
                block_name=state[0] if state else None,
                properties=state[1] if state else (),
            )
        )

    buttons.sort(key=lambda item: (item.x, item.y, item.z, item.name))
    signs.sort(key=lambda item: (item.x, item.y, item.z, item.text))
    return buttons, signs


def sign_kind(sign: Sign) -> str:
    text = sign.text.strip()
    if not text:
        return "EMPTY"
    if NUMERIC_PAIR_RE.fullmatch(text):
        return "NUMERIC_PAIR"
    if "<<<" in text or ">>>" in text:
        return "DIRECTIONAL_ACTION"

    lowered = text.lower()
    warning_prefixes = (
        "warning",
        "attention",
        "caution",
        "watch out",
        "look out",
        "high risk",
    )
    if lowered.startswith(warning_prefixes):
        return "WARNING"
    if "to bee or not" in lowered:
        return "TITLE"
    if "map made by" in lowered or "team nectar" in lowered:
        return "CREDIT"
    return "OTHER"


def arrow_direction(sign: Sign) -> str | None:
    has_left = "<<<" in sign.text
    has_right = ">>>" in sign.text
    if has_left and not has_right:
        return "LEFT"
    if has_right and not has_left:
        return "RIGHT"
    return None


def front_vector(sign: Sign) -> tuple[float, float] | None:
    props = dict(sign.properties)
    facing = props.get("facing")
    cardinal = {
        "north": (0.0, -1.0),
        "south": (0.0, 1.0),
        "west": (-1.0, 0.0),
        "east": (1.0, 0.0),
    }
    if facing in cardinal:
        return cardinal[facing]

    rotation = props.get("rotation")
    if rotation is not None:
        try:
            value = int(rotation) % 16
        except ValueError:
            return None
        theta = math.radians(value * 22.5)
        return (-math.sin(theta), math.cos(theta))
    return None


def distance(left: Sign, right: Button) -> float:
    return math.sqrt(
        (left.x - right.x) ** 2
        + (left.y - right.y) ** 2
        + (left.z - right.z) ** 2
    )


def horizontal_distance(left: Sign, right: Button) -> float:
    return math.hypot(left.x - right.x, left.z - right.z)


def _arrow_world_vector(
    front: tuple[float, float],
    direction: str,
) -> tuple[float, float]:
    dx, dz = front
    # The viewer stands on the sign's front side and looks opposite the front
    # normal. Screen-right therefore maps to (dz, -dx).
    right = (dz, -dx)
    return (-right[0], -right[1]) if direction == "LEFT" else right


def _arrow_candidates(
    sign: Sign,
    buttons: Sequence[Button],
    direction: str | None,
    front: tuple[float, float] | None,
    horizontal_radius: float,
    vertical_radius: float,
    corridor_half_width: float,
) -> tuple[Button, ...]:
    if direction is None or front is None:
        return ()

    ax, az = _arrow_world_vector(front, direction)
    ranked: list[tuple[float, float, float, Button]] = []
    for button in buttons:
        dy = abs(button.y - sign.y)
        if dy > vertical_radius:
            continue
        vx = button.x - sign.x
        vz = button.z - sign.z
        horizontal = math.hypot(vx, vz)
        if horizontal > horizontal_radius:
            continue
        along = vx * ax + vz * az
        if along <= 0:
            continue
        perpendicular = abs(vx * az - vz * ax)
        if perpendicular > corridor_half_width:
            continue
        ranked.append((perpendicular, along, dy, button))

    ranked.sort(
        key=lambda item: (
            item[0],
            item[1],
            item[2],
            item[3].x,
            item[3].y,
            item[3].z,
            item[3].name,
        )
    )
    return tuple(item[3] for item in ranked)


def link_action(
    sign: Sign,
    buttons: Sequence[Button],
    nearby_radius: float,
    arrow_horizontal_radius: float = 16.0,
    arrow_vertical_radius: float = 10.0,
    corridor_half_width: float = 4.0,
) -> LinkedAction:
    ordered = sorted(
        ((distance(sign, button), button) for button in buttons),
        key=lambda pair: (
            pair[0],
            pair[1].x,
            pair[1].y,
            pair[1].z,
            pair[1].name,
        ),
    )
    nearest_distance = ordered[0][0] if ordered else None
    nearest_button = ordered[0][1] if ordered else None
    nearby = tuple(button for dist, button in ordered if dist <= nearby_radius)
    direction = arrow_direction(sign)
    front = front_vector(sign)
    arrow_buttons = _arrow_candidates(
        sign,
        buttons,
        direction,
        front,
        arrow_horizontal_radius,
        arrow_vertical_radius,
        corridor_half_width,
    )
    return LinkedAction(
        sign=sign,
        nearest_button=nearest_button,
        nearest_distance=nearest_distance,
        nearby_buttons=nearby,
        arrow_buttons=arrow_buttons,
        arrow_direction=direction,
        front_vector=front,
    )


def render_report(
    source: Path,
    buttons: Sequence[Button],
    signs: Sequence[Sign],
    nearby_radius: float,
    arrow_horizontal_radius: float = 16.0,
    arrow_vertical_radius: float = 10.0,
    corridor_half_width: float = 4.0,
) -> str:
    kinds = Counter(sign_kind(sign) for sign in signs)
    actions = [
        link_action(
            sign,
            buttons,
            nearby_radius,
            arrow_horizontal_radius,
            arrow_vertical_radius,
            corridor_half_width,
        )
        for sign in signs
        if sign_kind(sign) == "DIRECTIONAL_ACTION"
    ]

    linked_actions = [action for action in actions if action.nearby_buttons]
    arrow_known = [action for action in actions if action.front_vector is not None]
    arrow_unique = [action for action in actions if len(action.arrow_buttons) == 1]
    arrow_ambiguous = [action for action in actions if len(action.arrow_buttons) > 1]
    arrow_unresolved = [
        action
        for action in actions
        if action.front_vector is not None and not action.arrow_buttons
    ]
    unique_arrow_buttons = {
        (
            action.arrow_buttons[0].x,
            action.arrow_buttons[0].y,
            action.arrow_buttons[0].z,
            action.arrow_buttons[0].name,
        )
        for action in arrow_unique
    }

    lines = [
        "# DeathRun Classic26 sign-to-button evidence",
        f"source={source.name}",
        (
            "sign_link_summary "
            f"buttons={len(buttons)} signs={len(signs)} "
            f"directional_actions={kinds['DIRECTIONAL_ACTION']} "
            f"directional_with_button_within_radius={len(linked_actions)} "
            f"facing_known_actions={len(arrow_known)} "
            f"arrow_unique_links={len(arrow_unique)} "
            f"arrow_ambiguous_links={len(arrow_ambiguous)} "
            f"arrow_unresolved={len(arrow_unresolved)} "
            f"unique_arrow_buttons={len(unique_arrow_buttons)} "
            f"warnings={kinds['WARNING']} numeric_pairs={kinds['NUMERIC_PAIR']} "
            f"titles={kinds['TITLE']} credits={kinds['CREDIT']} "
            f"other={kinds['OTHER']} empty={kinds['EMPTY']} "
            f"nearby_radius={nearby_radius:g} "
            f"arrow_horizontal_radius={arrow_horizontal_radius:g} "
            f"arrow_vertical_radius={arrow_vertical_radius:g} "
            f"corridor_half_width={corridor_half_width:g}"
        ),
        "# Arrow filtering uses preserved sign BlockState plus <<< / >>> only.",
        "# Evidence status is not a Classic26 trap classification.",
        "",
    ]

    for index, action in enumerate(actions, 1):
        sign = action.sign
        nearest = "none"
        distance_text = "none"
        if action.nearest_button is not None:
            button = action.nearest_button
            nearest = f"{button.x},{button.y},{button.z}:{button.name}"
            distance_text = f"{action.nearest_distance:.2f}"

        if len(action.arrow_buttons) == 1:
            status = "ARROW_UNIQUE"
        elif len(action.arrow_buttons) > 1:
            status = "ARROW_AMBIGUOUS"
        elif action.front_vector is not None:
            status = "ARROW_UNRESOLVED"
        elif action.nearby_buttons:
            status = "NEARBY_ONLY"
        else:
            status = "UNLINKED"

        facing_text = "unknown"
        if action.front_vector is not None:
            facing_text = f"{action.front_vector[0]:.3f},{action.front_vector[1]:.3f}"

        arrow_text = ";".join(
            f"{button.x},{button.y},{button.z}:{button.name}"
            for button in action.arrow_buttons
        ) or "none"

        lines.append(
            "ACTION_SIGN\t"
            f"id={index:03d}\tlink={status}\tpos={sign.x},{sign.y},{sign.z}\t"
            f"block={sign.block_name or 'unknown'}\tprops={dict(sign.properties)}\t"
            f"arrow={action.arrow_direction or 'unknown'}\tfront={facing_text}\t"
            f"nearest_button={nearest}\tdistance={distance_text}\t"
            f"nearby_buttons={len(action.nearby_buttons)}\t"
            f"arrow_buttons={len(action.arrow_buttons)}\t"
            f"arrow_candidates={arrow_text}\ttext={sign.text}"
        )

    for kind in ("TITLE", "CREDIT"):
        for sign in signs:
            if sign_kind(sign) != kind:
                continue
            lines.append(
                f"{kind}_SIGN\tpos={sign.x},{sign.y},{sign.z}\ttext={sign.text}"
            )

    return "\n".join(lines) + "\n"


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--nearby-radius", type=float, default=9.0)
    parser.add_argument("--arrow-horizontal-radius", type=float, default=16.0)
    parser.add_argument("--arrow-vertical-radius", type=float, default=10.0)
    parser.add_argument("--corridor-half-width", type=float, default=4.0)
    parser.add_argument("--min-actions", type=int, default=0)
    args = parser.parse_args(argv)

    for name, value in (
        ("--nearby-radius", args.nearby_radius),
        ("--arrow-horizontal-radius", args.arrow_horizontal_radius),
        ("--arrow-vertical-radius", args.arrow_vertical_radius),
        ("--corridor-half-width", args.corridor_half_width),
    ):
        if value <= 0:
            parser.error(f"{name} must be positive")

    buttons, signs = parse_probe_report(args.probe_report)
    report = render_report(
        args.probe_report,
        buttons,
        signs,
        args.nearby_radius,
        args.arrow_horizontal_radius,
        args.arrow_vertical_radius,
        args.corridor_half_width,
    )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    action_count = sum(sign_kind(sign) == "DIRECTIONAL_ACTION" for sign in signs)
    if action_count < args.min_actions:
        print(
            f"anvil_sign_links: expected at least {args.min_actions} directional action signs, got {action_count}",
            file=sys.stderr,
        )
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
