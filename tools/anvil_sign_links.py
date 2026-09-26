#!/usr/bin/env python3
"""Link archived DeathRun action signs to nearby button evidence.

The tool only correlates surviving world evidence. A directional sign is one
whose preserved text contains <<< or >>>. Its text is never converted into a
Classic26 trap type automatically.
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


@dataclass(frozen=True)
class LinkedAction:
    sign: Sign
    nearest_button: Button | None
    nearest_distance: float | None
    nearby_buttons: tuple[Button, ...]


def parse_probe_report(path: Path) -> tuple[list[Button], list[Sign]]:
    buttons: list[Button] = []
    signs: list[Sign] = []

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
                    )
                )
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
                signs.append(
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


def distance(left: Sign, right: Button) -> float:
    return math.sqrt(
        (left.x - right.x) ** 2
        + (left.y - right.y) ** 2
        + (left.z - right.z) ** 2
    )


def link_action(
    sign: Sign,
    buttons: Sequence[Button],
    nearby_radius: float,
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
    return LinkedAction(sign, nearest_button, nearest_distance, nearby)


def render_report(
    source: Path,
    buttons: Sequence[Button],
    signs: Sequence[Sign],
    nearby_radius: float,
) -> str:
    kinds = Counter(sign_kind(sign) for sign in signs)
    actions = [
        link_action(sign, buttons, nearby_radius)
        for sign in signs
        if sign_kind(sign) == "DIRECTIONAL_ACTION"
    ]
    linked_actions = [action for action in actions if action.nearby_buttons]
    linked = len(linked_actions)
    unique_nearest = {
        (
            action.nearest_button.x,
            action.nearest_button.y,
            action.nearest_button.z,
            action.nearest_button.name,
        )
        for action in linked_actions
        if action.nearest_button is not None
    }
    single_candidate = sum(
        len(action.nearby_buttons) == 1 for action in linked_actions
    )
    ambiguous = sum(
        len(action.nearby_buttons) > 1 for action in linked_actions
    )

    lines = [
        "# DeathRun Classic26 sign-to-button evidence",
        f"source={source.name}",
        (
            "sign_link_summary "
            f"buttons={len(buttons)} signs={len(signs)} "
            f"directional_actions={kinds['DIRECTIONAL_ACTION']} "
            f"directional_with_button_within_radius={linked} "
            f"unique_nearest_buttons={len(unique_nearest)} "
            f"single_candidate_links={single_candidate} "
            f"ambiguous_links={ambiguous} "
            f"unlinked_actions={len(actions) - linked} "
            f"warnings={kinds['WARNING']} numeric_pairs={kinds['NUMERIC_PAIR']} "
            f"titles={kinds['TITLE']} credits={kinds['CREDIT']} "
            f"other={kinds['OTHER']} empty={kinds['EMPTY']} "
            f"nearby_radius={nearby_radius:g}"
        ),
        "# ACTION_SIGN is preserved text plus spatial correlation, not a trap classification.",
        "",
    ]

    for index, action in enumerate(actions, 1):
        sign = action.sign
        if action.nearest_button is None:
            nearest = "none"
            distance_text = "none"
        else:
            button = action.nearest_button
            nearest = f"{button.x},{button.y},{button.z}:{button.name}"
            distance_text = f"{action.nearest_distance:.2f}"
        nearby_text = ";".join(
            f"{button.x},{button.y},{button.z}:{button.name}"
            for button in action.nearby_buttons
        ) or "none"

        link_status = "STRONG" if action.nearby_buttons else "UNLINKED"
        lines.append(
            "ACTION_SIGN\t"
            f"id={index:03d}\tlink={link_status}\tpos={sign.x},{sign.y},{sign.z}\t"
            f"nearest_button={nearest}\tdistance={distance_text}\t"
            f"nearby_buttons={len(action.nearby_buttons)}\t"
            f"nearby={nearby_text}\ttext={sign.text}"
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
    parser.add_argument("--min-actions", type=int, default=0)
    args = parser.parse_args(argv)

    if args.nearby_radius <= 0:
        parser.error("--nearby-radius must be positive")

    buttons, signs = parse_probe_report(args.probe_report)
    report = render_report(args.probe_report, buttons, signs, args.nearby_radius)

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
