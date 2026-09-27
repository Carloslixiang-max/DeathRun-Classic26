#!/usr/bin/env python3
"""Score both directions of the portal MST using Runner-warning sign fronts.

Assumption used only for the score:
- a warning sign's *front* is the side from which an approaching Runner can read it;
- therefore local travel through that warning is opposite the sign-front vector.

The report scores this hypothesis in both chain directions. A higher score is
still a route-direction *candidate*, not confirmation of start/finish or
checkpoint numbering.
"""

from __future__ import annotations

import argparse
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from anvil_portal_segment_graph import build_gaps, minimum_spanning_tree, warning_groups
from anvil_warning_facing_evidence import FacingMeasurement, measure_tree


@dataclass(frozen=True)
class DirectionScore:
    path: tuple[int, ...]
    agree: int
    disagree: int
    lateral: int
    edges_both_agree: int
    edges_any_disagree: int


def ordered_path(portal_count: int, tree) -> tuple[int, ...]:
    adjacency: dict[int, list[int]] = defaultdict(list)
    for edge in tree:
        adjacency[edge.left].append(edge.right)
        adjacency[edge.right].append(edge.left)
    endpoints = sorted(
        node
        for node in range(1, portal_count + 1)
        if len(adjacency[node]) == 1
    )
    if len(endpoints) != 2:
        return ()
    path = [endpoints[0]]
    previous = None
    current = endpoints[0]
    while True:
        next_nodes = [node for node in adjacency[current] if node != previous]
        if not next_nodes:
            break
        if len(next_nodes) != 1:
            return ()
        nxt = next_nodes[0]
        path.append(nxt)
        previous, current = current, nxt
    return tuple(path)


def _classify(value: float | None, expect_positive: bool, threshold: float) -> str:
    if value is None or abs(value) <= threshold:
        return "lateral"
    actual_positive = value > 0
    return "agree" if actual_positive == expect_positive else "disagree"


def score_direction(
    path: Sequence[int],
    measurements: Sequence[FacingMeasurement],
    threshold: float = 0.25,
) -> DirectionScore:
    by_pair = {
        frozenset((item.left, item.right)): item
        for item in measurements
    }
    agree = disagree = lateral = 0
    edges_both_agree = 0
    edges_any_disagree = 0

    for source, destination in zip(path, path[1:]):
        item = by_pair[frozenset((source, destination))]
        if source == item.left and destination == item.right:
            source_dot = item.left_dot_to_right
            destination_dot = item.right_dot_to_left
        elif source == item.right and destination == item.left:
            source_dot = item.right_dot_to_left
            destination_dot = item.left_dot_to_right
        else:
            raise AssertionError("MST measurement does not match path edge")

        # Incoming-facing hypothesis:
        # source sign front should point away from destination (<0);
        # destination sign front should point back toward source (>0).
        source_result = _classify(source_dot, expect_positive=False, threshold=threshold)
        destination_result = _classify(
            destination_dot,
            expect_positive=True,
            threshold=threshold,
        )
        results = (source_result, destination_result)
        agree += results.count("agree")
        disagree += results.count("disagree")
        lateral += results.count("lateral")
        if results == ("agree", "agree"):
            edges_both_agree += 1
        if "disagree" in results:
            edges_any_disagree += 1

    return DirectionScore(
        path=tuple(path),
        agree=agree,
        disagree=disagree,
        lateral=lateral,
        edges_both_agree=edges_both_agree,
        edges_any_disagree=edges_any_disagree,
    )


def _path_text(path: Sequence[int]) -> str:
    return "->".join(f"{node:03d}" for node in path)


def render_report(
    source: Path,
    scores: Sequence[DirectionScore],
    threshold: float,
) -> tuple[str, dict[str, int | str]]:
    ranked = sorted(
        scores,
        key=lambda item: (
            -(item.agree - item.disagree),
            -item.agree,
            item.disagree,
            item.lateral,
            item.path,
        ),
    )
    best = ranked[0] if ranked else None
    runner_up = ranked[1] if len(ranked) > 1 else None
    margin = (
        (best.agree - best.disagree)
        - (runner_up.agree - runner_up.disagree)
        if best and runner_up
        else 0
    )
    stats: dict[str, int | str] = {
        "candidate_directions": len(scores),
        "best_path": _path_text(best.path) if best else "none",
        "best_agree": best.agree if best else 0,
        "best_disagree": best.disagree if best else 0,
        "best_lateral": best.lateral if best else 0,
        "best_edges_both_agree": best.edges_both_agree if best else 0,
        "best_edges_any_disagree": best.edges_any_disagree if best else 0,
        "score_margin": margin,
    }

    lines = [
        "# DeathRun Classic26 warning-facing route-direction hypothesis",
        f"source={source.name}",
        (
            "route_direction_summary "
            f"candidate_directions={stats['candidate_directions']} "
            f"best_path={stats['best_path']} "
            f"best_agree={stats['best_agree']} "
            f"best_disagree={stats['best_disagree']} "
            f"best_lateral={stats['best_lateral']} "
            f"best_edges_both_agree={stats['best_edges_both_agree']} "
            f"best_edges_any_disagree={stats['best_edges_any_disagree']} "
            f"score_margin={stats['score_margin']} "
            f"dot_threshold={threshold:g}"
        ),
        "# Scoring assumption: Runner approaches a warning sign from its front side.",
        "# This is a candidate interpretation, NOT confirmed start/finish or checkpoint order.",
        "",
    ]
    for score in scores:
        lines.append(
            "DIRECTION_SCORE\t"
            f"path={_path_text(score.path)}\t"
            f"agree={score.agree}\t"
            f"disagree={score.disagree}\t"
            f"lateral={score.lateral}\t"
            f"edges_both_agree={score.edges_both_agree}\t"
            f"edges_any_disagree={score.edges_any_disagree}\t"
            f"net={score.agree-score.disagree}"
        )
    return "\n".join(lines) + "\n", stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--dot-threshold", type=float, default=0.25)
    parser.add_argument("--min-best-agree", type=int, default=0)
    parser.add_argument("--min-score-margin", type=int, default=0)
    args = parser.parse_args(argv)

    if not 0 <= args.dot_threshold < 1:
        parser.error("--dot-threshold must be in [0,1)")

    portals, grouped = warning_groups(args.probe_report)
    gaps = build_gaps(len(portals), grouped)
    tree = minimum_spanning_tree(len(portals), gaps)
    measurements = measure_tree(tree)
    path = ordered_path(len(portals), tree)
    scores = []
    if path:
        scores = [
            score_direction(path, measurements, args.dot_threshold),
            score_direction(tuple(reversed(path)), measurements, args.dot_threshold),
        ]
    report, stats = render_report(
        args.probe_report,
        scores,
        args.dot_threshold,
    )

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if int(stats["best_agree"]) < args.min_best_agree:
        return 2
    if int(stats["score_margin"]) < args.min_score_margin:
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
