#!/usr/bin/env python3
"""Link archived DeathRun action signs to likely control-panel buttons.

This correlates preserved world evidence only. Directional sign arrows are
reported as target-direction evidence but are NOT used to choose a control
button. Button candidates are reduced using distance plus compatible sign/button
facing where both BlockStates survive.
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
    properties: tuple[tuple[str, str], ...] = ()


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
    panel_buttons: tuple[Button, ...]
    target_arrow: str | None
    sign_front: tuple[float, float] | None


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
                    Button(
                        name=fields[2],
                        x=int(fields[3]),
                        y=int(fields[4]),
                        z=int(fields[5]),
                        properties=_parse_properties(fields[6:]),
                    )
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


def target_arrow(sign: Sign) -> str | None:
    has_left = "<<<" in sign.text
    has_right = ">>>" in sign.text
    if has_left and not has_right:
        return "LEFT"
    if has_right and not has_left:
        return "RIGHT"
    return None


def _cardinal_vector(value: str | None) -> tuple[float, float] | None:
    return {
        "north": (0.0, -1.0),
        "south": (0.0, 1.0),
        "west": (-1.0, 0.0),
        "east": (1.0, 0.0),
    }.get(value or "")


def sign_front_vector(sign: Sign) -> tuple[float, float] | None:
    props = dict(sign.properties)
    facing = _cardinal_vector(props.get("facing"))
    if facing is not None:
        return facing

    rotation = props.get("rotation")
    if rotation is None:
        return None
    try:
        value = int(rotation) % 16
    except ValueError:
        return None

    # Java standing signs: 0=south, 4=west, 8=north, 12=east.
    theta = math.radians(value * 22.5)
    return (-math.sin(theta), math.cos(theta))


def button_front_vector(button: Button) -> tuple[float, float] | None:
    props = dict(button.properties)
    if props.get("face") != "wall":
        return None
    return _cardinal_vector(props.get("facing"))


def distance(left: Sign, right: Button) -> float:
    return math.sqrt(
        (left.x - right.x) ** 2
        + (left.y - right.y) ** 2
        + (left.z - right.z) ** 2
    )


def _facing_dot(
    sign_front: tuple[float, float] | None,
    button: Button,
) -> float | None:
    button_front = button_front_vector(button)
    if sign_front is None or button_front is None:
        return None
    return sign_front[0] * button_front[0] + sign_front[1] * button_front[1]


def link_action(
    sign: Sign,
    buttons: Sequence[Button],
    nearby_radius: float,
    facing_min_dot: float = 0.70,
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

    sign_front = sign_front_vector(sign)
    panel_ranked: list[tuple[float, float, Button]] = []
    for button in nearby:
        dot = _facing_dot(sign_front, button)
        if dot is None or dot < facing_min_dot:
            continue
        panel_ranked.append((-dot, distance(sign, button), button))
    panel_ranked.sort(
        key=lambda item: (
            item[0],
            item[1],
            item[2].x,
            item[2].y,
            item[2].z,
            item[2].name,
        )
    )

    return LinkedAction(
        sign=sign,
        nearest_button=nearest_button,
        nearest_distance=nearest_distance,
        nearby_buttons=nearby,
        panel_buttons=tuple(item[2] for item in panel_ranked),
        target_arrow=target_arrow(sign),
        sign_front=sign_front,
    )


def _button_text(button: Button) -> str:
    props = ",".join(f"{key}:{value}" for key, value in button.properties)
    return f"{button.x},{button.y},{button.z}:{button.name}[{props or 'no-props'}]"


def render_report(
    source: Path,
    buttons: Sequence[Button],
    signs: Sequence[Sign],
    nearby_radius: float,
    facing_min_dot: float = 0.70,
) -> str:
    kinds = Counter(sign_kind(sign) for sign in signs)
    actions = [
        link_action(sign, buttons, nearby_radius, facing_min_dot)
        for sign in signs
        if sign_kind(sign) == "DIRECTIONAL_ACTION"
    ]

    linked_actions = [action for action in actions if action.nearby_buttons]
    sign_facing_known = [action for action in actions if action.sign_front is not None]
    buttons_with_facing = sum(button_front_vector(button) is not None for button in buttons)
    panel_unique = [action for action in actions if len(action.panel_buttons) == 1]
    panel_ambiguous = [action for action in actions if len(action.panel_buttons) > 1]
    panel_unresolved = [
        action
        for action in actions
        if action.sign_front is not None and not action.panel_buttons
    ]
    unique_panel_buttons = {
        (
            action.panel_buttons[0].x,
            action.panel_buttons[0].y,
            action.panel_buttons[0].z,
            action.panel_buttons[0].name,
        )
        for action in panel_unique
    }

    lines = [
        "# DeathRun Classic26 sign-to-button evidence",
        f"source={source.name}",
        (
            "sign_link_summary "
            f"buttons={len(buttons)} button_facing_known={buttons_with_facing} "
            f"signs={len(signs)} directional_actions={kinds['DIRECTIONAL_ACTION']} "
            f"directional_with_button_within_radius={len(linked_actions)} "
            f"sign_facing_known_actions={len(sign_facing_known)} "
            f"panel_unique_links={len(panel_unique)} "
            f"panel_ambiguous_links={len(panel_ambiguous)} "
            f"panel_unresolved={len(panel_unresolved)} "
            f"unique_panel_buttons={len(unique_panel_buttons)} "
            f"warnings={kinds['WARNING']} numeric_pairs={kinds['NUMERIC_PAIR']} "
            f"titles={kinds['TITLE']} credits={kinds['CREDIT']} "
            f"other={kinds['OTHER']} empty={kinds['EMPTY']} "
            f"nearby_radius={nearby_radius:g} facing_min_dot={facing_min_dot:g}"
        ),
        "# <<< / >>> is preserved as target-direction evidence only.",
        "# PANEL candidates require nearby wall-button facing compatible with the sign.",
        "# Evidence status is not a Classic26 trap classification.",
        "",
    ]

    for index, action in enumerate(actions, 1):
        sign = action.sign
        nearest = (
            _button_text(action.nearest_button)
            if action.nearest_button is not None
            else "none"
        )
        distance_text = (
            f"{action.nearest_distance:.2f}"
            if action.nearest_distance is not None
            else "none"
        )

        if len(action.panel_buttons) == 1:
            status = "PANEL_UNIQUE"
        elif len(action.panel_buttons) > 1:
            status = "PANEL_AMBIGUOUS"
        elif action.nearby_buttons:
            status = "NEARBY_ONLY"
        else:
            status = "UNLINKED"

        sign_front = (
            f"{action.sign_front[0]:.3f},{action.sign_front[1]:.3f}"
            if action.sign_front is not None
            else "unknown"
        )
        nearby_text = ";".join(_button_text(button) for button in action.nearby_buttons) or "none"
        panel_text = ";".join(_button_text(button) for button in action.panel_buttons) or "none"

        lines.append(
            "ACTION_SIGN\t"
            f"id={index:03d}\tlink={status}\tpos={sign.x},{sign.y},{sign.z}\t"
            f"block={sign.block_name or 'unknown'}\tprops={dict(sign.properties)}\t"
            f"target_arrow={action.target_arrow or 'unknown'}\tfront={sign_front}\t"
            f"nearest_button={nearest}\tdistance={distance_text}\t"
            f"nearby_buttons={len(action.nearby_buttons)}\t"
            f"panel_buttons={len(action.panel_buttons)}\t"
            f"panel_candidates={panel_text}\tnearby={nearby_text}\ttext={sign.text}"
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
    parser.add_argument("--facing-min-dot", type=float, default=0.70)
    parser.add_argument("--min-actions", type=int, default=0)
    args = parser.parse_args(argv)

    if args.nearby_radius <= 0:
        parser.error("--nearby-radius must be positive")
    if not -1.0 <= args.facing_min_dot <= 1.0:
        parser.error("--facing-min-dot must be between -1 and 1")

    buttons, signs = parse_probe_report(args.probe_report)
    report = render_report(
        args.probe_report,
        buttons,
        signs,
        args.nearby_radius,
        args.facing_min_dot,
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
