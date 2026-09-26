#!/usr/bin/env python3
"""Extract route/checkpoint evidence from archived DeathRun sign text.

This tool deliberately distinguishes direct route keywords from unexplained
numeric-pair sign panels. Numeric signs are grouped physically and catalogued,
but are never interpreted as checkpoints without independent evidence.
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter, deque
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from anvil_sign_links import Sign, parse_probe_report, sign_kind


TOKEN_RE = re.compile(r"[a-z0-9]+")
NUMERIC_PAIR_RE = re.compile(r"^\s*(\d+)\s*\|\s*(\d+)\s*$")
ROUTE_KEYWORDS = {
    "checkpoint",
    "start",
    "finish",
    "end",
    "spawn",
    "lobby",
    "runner",
    "runners",
    "death",
}


@dataclass(frozen=True)
class NumericMarker:
    sign: Sign
    left: int
    right: int


@dataclass(frozen=True)
class NumericComponent:
    members: tuple[NumericMarker, ...]

    @property
    def bbox(self) -> tuple[int, int, int, int, int, int]:
        return (
            min(item.sign.x for item in self.members),
            min(item.sign.y for item in self.members),
            min(item.sign.z for item in self.members),
            max(item.sign.x for item in self.members),
            max(item.sign.y for item in self.members),
            max(item.sign.z for item in self.members),
        )

    @property
    def values(self) -> Counter[tuple[int, int]]:
        return Counter((item.left, item.right) for item in self.members)


def text_tokens(text: str) -> frozenset[str]:
    return frozenset(TOKEN_RE.findall(text.lower()))


def route_keywords(sign: Sign) -> tuple[str, ...]:
    tokens = text_tokens(sign.text)
    return tuple(sorted(tokens & ROUTE_KEYWORDS))


def numeric_markers(signs: Sequence[Sign]) -> list[NumericMarker]:
    result: list[NumericMarker] = []
    for sign in signs:
        match = NUMERIC_PAIR_RE.fullmatch(sign.text.strip())
        if not match:
            continue
        result.append(
            NumericMarker(
                sign=sign,
                left=int(match.group(1)),
                right=int(match.group(2)),
            )
        )
    result.sort(
        key=lambda item: (
            item.sign.x,
            item.sign.y,
            item.sign.z,
            item.left,
            item.right,
        )
    )
    return result


def numeric_components(markers: Sequence[NumericMarker]) -> list[NumericComponent]:
    """Group face-adjacent numeric signs into physical sign panels."""
    by_pos = {
        (item.sign.x, item.sign.y, item.sign.z): item
        for item in markers
    }
    remaining = set(by_pos)
    groups: list[NumericComponent] = []

    while remaining:
        start = min(remaining)
        remaining.remove(start)
        queue = deque([start])
        members: list[NumericMarker] = []

        while queue:
            x, y, z = queue.popleft()
            members.append(by_pos[(x, y, z)])
            for neighbor in (
                (x - 1, y, z),
                (x + 1, y, z),
                (x, y - 1, z),
                (x, y + 1, z),
                (x, y, z - 1),
                (x, y, z + 1),
            ):
                if neighbor in remaining:
                    remaining.remove(neighbor)
                    queue.append(neighbor)

        groups.append(
            NumericComponent(
                tuple(
                    sorted(
                        members,
                        key=lambda item: (
                            item.sign.x,
                            item.sign.y,
                            item.sign.z,
                            item.left,
                            item.right,
                        ),
                    )
                )
            )
        )

    groups.sort(
        key=lambda group: (
            -len(group.members),
            group.bbox[0],
            group.bbox[1],
            group.bbox[2],
        )
    )
    return groups


def _pos(sign: Sign) -> str:
    return f"{sign.x},{sign.y},{sign.z}"


def render_report(source: Path, signs: Sequence[Sign]) -> str:
    numeric = numeric_markers(signs)
    components = numeric_components(numeric)
    pair_counts = Counter((item.left, item.right) for item in numeric)
    route_signs = [
        (sign, route_keywords(sign))
        for sign in signs
        if route_keywords(sign)
    ]
    route_signs.sort(
        key=lambda item: (
            item[0].x,
            item[0].y,
            item[0].z,
            item[0].text,
        )
    )

    largest = max((len(group.members) for group in components), default=0)
    multi_sign_components = sum(len(group.members) > 1 for group in components)

    lines = [
        "# DeathRun Classic26 route/checkpoint sign evidence",
        f"source={source.name}",
        (
            "route_evidence_summary "
            f"signs={len(signs)} route_keyword_signs={len(route_signs)} "
            f"numeric_pair_signs={len(numeric)} "
            f"distinct_numeric_pairs={len(pair_counts)} "
            f"numeric_components={len(components)} "
            f"multi_sign_numeric_components={multi_sign_components} "
            f"largest_numeric_component={largest}"
        ),
        "# ROUTE_SIGN is a direct preserved keyword hit; it is not automatically a gameplay region.",
        "# Numeric-pair panels remain unexplained metadata until independently corroborated.",
        "",
    ]

    for sign, keywords in route_signs:
        lines.append(
            "ROUTE_SIGN\t"
            f"pos={_pos(sign)}\tkeywords={','.join(keywords)}\ttext={sign.text}"
        )

    lines.append("")
    for (left, right), count in sorted(pair_counts.items()):
        coords = ";".join(
            _pos(item.sign)
            for item in numeric
            if (item.left, item.right) == (left, right)
        )
        lines.append(
            "NUMERIC_PAIR_CATALOG\t"
            f"value={left}|{right}\tcount={count}\tcoords={coords}"
        )

    lines.append("")
    for index, component in enumerate(components, 1):
        min_x, min_y, min_z, max_x, max_y, max_z = component.bbox
        values = ",".join(
            f"{left}|{right}:{count}"
            for (left, right), count in sorted(component.values.items())
        )
        coords = ";".join(_pos(item.sign) for item in component.members)
        lines.append(
            "NUMERIC_COMPONENT\t"
            f"id={index:03d}\tsize={len(component.members)}\t"
            f"bbox={min_x},{min_y},{min_z}:{max_x},{max_y},{max_z}\t"
            f"values={values}\tcoords={coords}"
        )

    return "\n".join(lines) + "\n"


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--min-numeric", type=int, default=0)
    args = parser.parse_args(argv)

    _buttons, signs = parse_probe_report(args.probe_report)
    report = render_report(args.probe_report, signs)

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    numeric_count = sum(sign_kind(sign) == "NUMERIC_PAIR" for sign in signs)
    if numeric_count < args.min_numeric:
        print(
            f"anvil_route_evidence: expected at least {args.min_numeric} numeric signs, got {numeric_count}",
            file=sys.stderr,
        )
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
