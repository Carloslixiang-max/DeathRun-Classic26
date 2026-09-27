#!/usr/bin/env python3
"""Compare local evidence around the two endpoint gates of the portal-segment MST.

The endpoints come from the undirected warning-proximity MST. Local context is
reported symmetrically for both ends so start/finish-side hypotheses can be
reviewed without assigning either role automatically.
"""

from __future__ import annotations

import argparse
import math
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from anvil_cluster import Candidate, parse_probe_report as parse_candidate_probe
from anvil_portal_segment_graph import (
    build_gaps,
    minimum_spanning_tree,
    warning_groups,
)
from anvil_sign_links import Sign, parse_probe_report as parse_sign_probe, sign_kind
from anvil_stage_evidence import StageMarker, parse_stage_markers
from anvil_topology import Component
from anvil_trap_evidence import build_actions, build_pair_candidates


@dataclass(frozen=True)
class Mechanism:
    name: str
    x: int
    y: int
    z: int


@dataclass(frozen=True)
class EndpointContext:
    portal_id: int
    portal: Component
    buttons: tuple[Candidate, ...]
    pressure_plates: tuple[Candidate, ...]
    mechanisms: tuple[Mechanism, ...]
    warnings: tuple[Sign, ...]
    actions: tuple[Sign, ...]
    hints: tuple[Sign, ...]
    titles: tuple[Sign, ...]
    stage_markers: tuple[StageMarker, ...]
    unmatched_actions: tuple[Sign, ...]


def _distance_xyz(
    ax: float, ay: float, az: float,
    bx: float, by: float, bz: float,
) -> float:
    return math.sqrt((ax-bx)**2 + (ay-by)**2 + (az-bz)**2)


def _center(component: Component) -> tuple[float, float, float]:
    return component.center


def _within(component: Component, x: float, y: float, z: float, radius: float) -> bool:
    cx, cy, cz = _center(component)
    return _distance_xyz(cx, cy, cz, x, y, z) <= radius


def parse_mechanisms(path: Path) -> list[Mechanism]:
    result: list[Mechanism] = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.startswith("BLOCK\tMECHANISM\t"):
            continue
        fields = raw.split("\t")
        if len(fields) < 6:
            continue
        try:
            result.append(
                Mechanism(fields[2], int(fields[3]), int(fields[4]), int(fields[5]))
            )
        except ValueError:
            continue
    result.sort(key=lambda item: (item.x, item.y, item.z, item.name))
    return result


def mst_endpoints(probe_report: Path) -> tuple[list[Component], list[int]]:
    portals, grouped = warning_groups(probe_report)
    gaps = build_gaps(len(portals), grouped)
    tree = minimum_spanning_tree(len(portals), gaps)
    degree = Counter()
    for edge in tree:
        degree[edge.left] += 1
        degree[edge.right] += 1
    endpoints = sorted(
        node
        for node in range(1, len(portals) + 1)
        if degree[node] == 1
    )
    return portals, endpoints


def build_contexts(
    probe_report: Path,
    entity_evidence_report: Path,
    radius: float,
) -> list[EndpointContext]:
    portals, endpoints = mst_endpoints(probe_report)
    interactive, _portal_points = parse_candidate_probe(probe_report)
    _buttons, signs = parse_sign_probe(probe_report)
    mechanisms = parse_mechanisms(probe_report)
    stages = parse_stage_markers(entity_evidence_report)

    warnings = [sign for sign in signs if sign_kind(sign) == "WARNING"]
    actions = [sign for sign in signs if sign_kind(sign) == "DIRECTIONAL_ACTION"]
    hints = [
        sign for sign in signs
        if sign_kind(sign) == "OTHER"
        and sign.text.strip().lower().startswith("hint")
    ]
    titles = [sign for sign in signs if sign_kind(sign) == "TITLE"]

    action_evidence = build_actions(signs)
    pairs, _unmatched_warnings, unmatched_action_evidence = build_pair_candidates(
        sorted(warnings, key=lambda sign: (sign.x, sign.y, sign.z, sign.text)),
        action_evidence,
        30.0,
    )
    del pairs
    unmatched_action_signs = [item.sign for item in unmatched_action_evidence]

    result: list[EndpointContext] = []
    for portal_id in endpoints:
        portal = portals[portal_id - 1]
        result.append(
            EndpointContext(
                portal_id=portal_id,
                portal=portal,
                buttons=tuple(
                    item for item in interactive
                    if item.category == "BUTTON"
                    and _within(portal, item.x, item.y, item.z, radius)
                ),
                pressure_plates=tuple(
                    item for item in interactive
                    if item.category == "PRESSURE_PLATE"
                    and _within(portal, item.x, item.y, item.z, radius)
                ),
                mechanisms=tuple(
                    item for item in mechanisms
                    if _within(portal, item.x, item.y, item.z, radius)
                ),
                warnings=tuple(
                    sign for sign in warnings
                    if _within(portal, sign.x, sign.y, sign.z, radius)
                ),
                actions=tuple(
                    sign for sign in actions
                    if _within(portal, sign.x, sign.y, sign.z, radius)
                ),
                hints=tuple(
                    sign for sign in hints
                    if _within(portal, sign.x, sign.y, sign.z, radius)
                ),
                titles=tuple(
                    sign for sign in titles
                    if _within(portal, sign.x, sign.y, sign.z, radius)
                ),
                stage_markers=tuple(
                    marker for marker in stages
                    if _within(portal, marker.x, marker.y, marker.z, radius)
                ),
                unmatched_actions=tuple(
                    sign for sign in unmatched_action_signs
                    if _within(portal, sign.x, sign.y, sign.z, radius)
                ),
            )
        )
    return result


def _nearest(
    portal: Component,
    items: Sequence[object],
    coord,
    render,
) -> str:
    if not items:
        return "none"
    cx, cy, cz = portal.center
    ranked = []
    for item in items:
        x, y, z = coord(item)
        ranked.append((_distance_xyz(cx, cy, cz, x, y, z), item))
    ranked.sort(key=lambda pair: pair[0])
    distance, item = ranked[0]
    return f"{render(item)}@{distance:.2f}"


def _sign_text(sign: Sign) -> str:
    text = sign.text.replace("\t", " ").replace("\n", " ")
    return f"{sign.x},{sign.y},{sign.z}[{text}]"


def _candidate_text(item: Candidate) -> str:
    return f"{item.x},{item.y},{item.z}[{item.name}]"


def _mechanism_text(item: Mechanism) -> str:
    return f"{item.x},{item.y},{item.z}[{item.name}]"


def _stage_text(item: StageMarker) -> str:
    return f"{item.x:.3f},{item.y:.3f},{item.z:.3f}[{item.name}]"


def _bbox(component: Component) -> str:
    min_x, min_y, min_z, max_x, max_y, max_z = component.bbox
    return f"{min_x},{min_y},{min_z}:{max_x},{max_y},{max_z}"


def render_report(
    source: Path,
    contexts: Sequence[EndpointContext],
    radius: float,
) -> tuple[str, dict[str, int | str]]:
    endpoint_ids = ",".join(f"{item.portal_id:03d}" for item in contexts)
    stats: dict[str, int | str] = {
        "endpoints": len(contexts),
        "endpoint_ids": endpoint_ids or "none",
        "endpoints_with_hint": sum(bool(item.hints) for item in contexts),
        "endpoints_with_stage_marker": sum(bool(item.stage_markers) for item in contexts),
    }

    lines = [
        "# DeathRun Classic26 endpoint-gate local evidence",
        f"source={source.name}",
        (
            "endpoint_context_summary "
            f"endpoints={stats['endpoints']} "
            f"endpoint_ids={stats['endpoint_ids']} "
            f"endpoints_with_hint={stats['endpoints_with_hint']} "
            f"endpoints_with_stage_marker={stats['endpoints_with_stage_marker']} "
            f"radius={radius:g}"
        ),
        "# Endpoint means only an endpoint of the undirected warning-proximity MST.",
        "# Local context does NOT assign start, finish, checkpoint number, respawn or travel direction.",
        "",
    ]

    for item in contexts:
        portal = item.portal
        cx, cy, cz = portal.center
        lines.append(
            "ENDPOINT_CONTEXT\t"
            f"portal={item.portal_id:03d}\t"
            f"center={cx:.2f},{cy:.2f},{cz:.2f}\t"
            f"bbox={_bbox(portal)}\t"
            f"buttons={len(item.buttons)}\t"
            f"pressure_plates={len(item.pressure_plates)}\t"
            f"mechanisms={len(item.mechanisms)}\t"
            f"warnings={len(item.warnings)}\t"
            f"actions={len(item.actions)}\t"
            f"hints={len(item.hints)}\t"
            f"titles={len(item.titles)}\t"
            f"stage_markers={len(item.stage_markers)}\t"
            f"unmatched_actions={len(item.unmatched_actions)}"
        )
        lines.append(
            "ENDPOINT_NEAREST\t"
            f"portal={item.portal_id:03d}\t"
            f"button={_nearest(portal,item.buttons,lambda x:(x.x,x.y,x.z),_candidate_text)}\t"
            f"pressure_plate={_nearest(portal,item.pressure_plates,lambda x:(x.x,x.y,x.z),_candidate_text)}\t"
            f"mechanism={_nearest(portal,item.mechanisms,lambda x:(x.x,x.y,x.z),_mechanism_text)}\t"
            f"warning={_nearest(portal,item.warnings,lambda x:(x.x,x.y,x.z),_sign_text)}\t"
            f"action={_nearest(portal,item.actions,lambda x:(x.x,x.y,x.z),_sign_text)}\t"
            f"hint={_nearest(portal,item.hints,lambda x:(x.x,x.y,x.z),_sign_text)}\t"
            f"title={_nearest(portal,item.titles,lambda x:(x.x,x.y,x.z),_sign_text)}\t"
            f"stage_marker={_nearest(portal,item.stage_markers,lambda x:(x.x,x.y,x.z),_stage_text)}\t"
            f"unmatched_action={_nearest(portal,item.unmatched_actions,lambda x:(x.x,x.y,x.z),_sign_text)}"
        )
    return "\n".join(lines) + "\n", stats


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("probe_report", type=Path)
    parser.add_argument("entity_evidence_report", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--radius", type=float, default=20.0)
    parser.add_argument("--min-endpoints", type=int, default=0)
    parser.add_argument("--min-endpoints-with-special-anchor", type=int, default=0)
    args = parser.parse_args(argv)

    if args.radius <= 0:
        parser.error("--radius must be positive")

    contexts = build_contexts(
        args.probe_report,
        args.entity_evidence_report,
        args.radius,
    )
    report, stats = render_report(args.probe_report, contexts, args.radius)

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if int(stats["endpoints"]) < args.min_endpoints:
        return 2
    special = int(stats["endpoints_with_hint"]) + int(stats["endpoints_with_stage_marker"])
    if special < args.min_endpoints_with_special_anchor:
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
