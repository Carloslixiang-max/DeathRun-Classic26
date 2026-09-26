#!/usr/bin/env python3
"""Build a transparent trap-evidence catalog from archived DeathRun signs.

The report preserves exact surviving sign text and coordinates. It normalizes
only for catalog/search purposes and ranks WARNING signs against directional
action signs using two independent signals:
- physical distance
- shared normalized words

No ranked result is treated as a confirmed Classic26 trap mapping.
"""

from __future__ import annotations

import argparse
import math
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from anvil_sign_links import Sign, parse_probe_report, sign_kind


WORD_RE = re.compile(r"[a-z0-9]+")
STOPWORDS = {
    "a", "an", "and", "any", "are", "area", "at", "attention", "beware",
    "can", "caution", "could", "for", "high", "is", "look", "may", "of",
    "only", "out", "risk", "the", "to", "warning", "watch", "you", "your",
}
TOKEN_NORMALIZATION = {
    "arrows": "arrow",
    "blocks": "block",
    "coals": "coal",
    "firing": "fire",
    "flooding": "flood",
    "lanterns": "lantern",
    "walls": "wall",
}


@dataclass(frozen=True)
class ActionEvidence:
    sign: Sign
    label: str
    tokens: frozenset[str]


@dataclass(frozen=True)
class RankedAction:
    action: ActionEvidence
    distance: float
    shared_tokens: tuple[str, ...]


@dataclass(frozen=True)
class PairCandidate:
    warning: Sign
    action: ActionEvidence
    distance: float
    shared_tokens: tuple[str, ...]


def clean_label(text: str) -> str:
    cleaned = text.replace("<<<", " ").replace(">>>", " ").replace("|", " ")
    cleaned = cleaned.replace("~", " ")
    cleaned = " ".join(cleaned.split())
    return cleaned.strip(" -:!?.") or "(empty)"


def evidence_tokens(text: str) -> frozenset[str]:
    result: set[str] = set()
    for token in WORD_RE.findall(clean_label(text).lower()):
        token = TOKEN_NORMALIZATION.get(token, token)
        if token and token not in STOPWORDS:
            result.add(token)
    return frozenset(result)


def sign_distance(left: Sign, right: Sign) -> float:
    return math.sqrt(
        (left.x - right.x) ** 2
        + (left.y - right.y) ** 2
        + (left.z - right.z) ** 2
    )


def build_actions(signs: Sequence[Sign]) -> list[ActionEvidence]:
    actions = [
        ActionEvidence(sign=sign, label=clean_label(sign.text), tokens=evidence_tokens(sign.text))
        for sign in signs
        if sign_kind(sign) == "DIRECTIONAL_ACTION"
    ]
    actions.sort(key=lambda item: (item.sign.x, item.sign.y, item.sign.z, item.label.lower()))
    return actions


def rank_warning(warning: Sign, actions: Sequence[ActionEvidence]) -> list[RankedAction]:
    warning_tokens = evidence_tokens(warning.text)
    ranked = [
        RankedAction(
            action=action,
            distance=sign_distance(warning, action.sign),
            shared_tokens=tuple(sorted(warning_tokens & action.tokens)),
        )
        for action in actions
    ]
    ranked.sort(
        key=lambda item: (
            -len(item.shared_tokens),
            item.distance,
            item.action.label.lower(),
            item.action.sign.x,
            item.action.sign.y,
            item.action.sign.z,
        )
    )
    return ranked


def build_pair_candidates(
    warnings: Sequence[Sign],
    actions: Sequence[ActionEvidence],
    max_distance: float,
) -> tuple[list[PairCandidate], list[Sign], list[ActionEvidence]]:
    """Build a deterministic one-to-one evidence candidate set.

    Edges require at least one shared normalized token and must be within
    max_distance. More shared tokens are preferred, then shorter distance.
    The result is explicitly a review candidate set, not confirmed gameplay.
    """
    edges: list[tuple[int, float, int, int, tuple[str, ...]]] = []
    for warning_index, warning in enumerate(warnings):
        warning_tokens = evidence_tokens(warning.text)
        for action_index, action in enumerate(actions):
            shared = tuple(sorted(warning_tokens & action.tokens))
            if not shared:
                continue
            distance = sign_distance(warning, action.sign)
            if distance > max_distance:
                continue
            edges.append(
                (-len(shared), distance, warning_index, action_index, shared)
            )

    edges.sort(
        key=lambda item: (
            item[0],
            item[1],
            warnings[item[2]].x,
            warnings[item[2]].y,
            warnings[item[2]].z,
            actions[item[3]].sign.x,
            actions[item[3]].sign.y,
            actions[item[3]].sign.z,
            actions[item[3]].label.lower(),
        )
    )

    used_warnings: set[int] = set()
    used_actions: set[int] = set()
    pairs: list[PairCandidate] = []
    for _negative_shared_count, distance, warning_index, action_index, shared in edges:
        if warning_index in used_warnings or action_index in used_actions:
            continue
        used_warnings.add(warning_index)
        used_actions.add(action_index)
        pairs.append(
            PairCandidate(
                warning=warnings[warning_index],
                action=actions[action_index],
                distance=distance,
                shared_tokens=shared,
            )
        )

    pairs.sort(
        key=lambda item: (
            item.warning.x,
            item.warning.y,
            item.warning.z,
            item.action.sign.x,
            item.action.sign.y,
            item.action.sign.z,
        )
    )
    unmatched_warnings = [
        warning for index, warning in enumerate(warnings) if index not in used_warnings
    ]
    unmatched_actions = [
        action for index, action in enumerate(actions) if index not in used_actions
    ]
    return pairs, unmatched_warnings, unmatched_actions


def _pos(sign: Sign) -> str:
    return f"{sign.x},{sign.y},{sign.z}"


def render_report(
    source: Path,
    signs: Sequence[Sign],
    top_candidates: int = 3,
    nearby_distance: float = 30.0,
) -> str:
    actions = build_actions(signs)
    warnings = [sign for sign in signs if sign_kind(sign) == "WARNING"]
    warnings.sort(key=lambda sign: (sign.x, sign.y, sign.z, sign.text))

    by_label: dict[str, list[ActionEvidence]] = defaultdict(list)
    display_label: dict[str, str] = {}
    for action in actions:
        key = action.label.lower()
        by_label[key].append(action)
        display_label.setdefault(key, action.label)

    warnings_with_overlap = 0
    warnings_with_nearby = 0
    pairs, unmatched_warnings, unmatched_actions = build_pair_candidates(
        warnings, actions, nearby_distance
    )
    lines = [
        "# DeathRun Classic26 archived trap evidence catalog",
        f"source={source.name}",
    ]

    catalog_lines: list[str] = []
    for key in sorted(by_label, key=lambda value: (display_label[value].lower(), value)):
        group = by_label[key]
        coords = ";".join(_pos(action.sign) for action in group)
        token_text = ",".join(sorted(group[0].tokens)) or "none"
        catalog_lines.append(
            "ACTION_CATALOG\t"
            f"count={len(group)}\tlabel={display_label[key]}\t"
            f"tokens={token_text}\tcoords={coords}"
        )

    warning_lines: list[str] = []
    for index, warning in enumerate(warnings, 1):
        ranked = rank_warning(warning, actions)
        overlap = [item for item in ranked if item.shared_tokens]
        nearest = sorted(
            ranked,
            key=lambda item: (
                item.distance,
                -len(item.shared_tokens),
                item.action.label.lower(),
            ),
        )

        if overlap:
            warnings_with_overlap += 1
        if nearest and nearest[0].distance <= nearby_distance:
            warnings_with_nearby += 1

        lexical_text = "none"
        if overlap:
            lexical_text = ";".join(
                (
                    f"{item.action.label}@{_pos(item.action.sign)}"
                    f"[shared={','.join(item.shared_tokens)};distance={item.distance:.2f}]"
                )
                for item in overlap[:top_candidates]
            )

        nearest_text = "none"
        if nearest:
            nearest_text = ";".join(
                f"{item.action.label}@{_pos(item.action.sign)}[distance={item.distance:.2f}]"
                for item in nearest[:top_candidates]
            )

        warning_lines.append(
            "WARNING_EVIDENCE\t"
            f"id={index:03d}\tpos={_pos(warning)}\t"
            f"warning_tokens={','.join(sorted(evidence_tokens(warning.text))) or 'none'}\t"
            f"nearest={nearest_text}\tlexical={lexical_text}\ttext={warning.text}"
        )

    summary = (
        "trap_evidence_summary "
        f"directional_actions={len(actions)} unique_action_labels={len(by_label)} "
        f"warnings={len(warnings)} warnings_with_text_overlap={warnings_with_overlap} "
        f"warnings_with_nearest_action_within_{nearby_distance:g}={warnings_with_nearby} "
        f"pair_candidates={len(pairs)} "
        f"unmatched_warnings={len(unmatched_warnings)} "
        f"unmatched_actions={len(unmatched_actions)} "
        f"top_candidates={top_candidates}"
    )
    lines.extend(
        [
            summary,
            "# ACTION_CATALOG groups only equivalent cleaned surviving sign text.",
            "# WARNING_EVIDENCE shows independent nearest and lexical rankings; neither is confirmation.",
            "# PAIR_CANDIDATE is a greedy one-to-one lexical+distance review set, not confirmation.",
            "",
            *catalog_lines,
            "",
            *warning_lines,
            "",
            *[
                (
                    "PAIR_CANDIDATE\t"
                    f"id={index:03d}\twarning_pos={_pos(pair.warning)}\t"
                    f"action_pos={_pos(pair.action.sign)}\t"
                    f"label={pair.action.label}\t"
                    f"shared={','.join(pair.shared_tokens)}\t"
                    f"distance={pair.distance:.2f}\twarning={pair.warning.text}"
                )
                for index, pair in enumerate(pairs, 1)
            ],
            *[
                (
                    "UNMATCHED_WARNING\t"
                    f"pos={_pos(warning)}\ttext={warning.text}"
                )
                for warning in unmatched_warnings
            ],
            *[
                (
                    "UNMATCHED_ACTION\t"
                    f"pos={_pos(action.sign)}\tlabel={action.label}\t"
                    f"text={action.sign.text}"
                )
                for action in unmatched_actions
            ],
        ]
    )
    return "\n".join(lines) + "\n"


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--top-candidates", type=int, default=3)
    parser.add_argument("--nearby-distance", type=float, default=30.0)
    parser.add_argument("--min-actions", type=int, default=0)
    parser.add_argument("--min-warnings", type=int, default=0)
    args = parser.parse_args(argv)

    if args.top_candidates <= 0:
        parser.error("--top-candidates must be positive")
    if args.nearby_distance <= 0:
        parser.error("--nearby-distance must be positive")

    _buttons, signs = parse_probe_report(args.probe_report)
    actions = build_actions(signs)
    warnings = [sign for sign in signs if sign_kind(sign) == "WARNING"]
    report = render_report(
        args.probe_report,
        signs,
        top_candidates=args.top_candidates,
        nearby_distance=args.nearby_distance,
    )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if len(actions) < args.min_actions:
        print(
            f"anvil_trap_evidence: expected at least {args.min_actions} actions, got {len(actions)}",
            file=sys.stderr,
        )
        return 2
    if len(warnings) < args.min_warnings:
        print(
            f"anvil_trap_evidence: expected at least {args.min_warnings} warnings, got {len(warnings)}",
            file=sys.stderr,
        )
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
