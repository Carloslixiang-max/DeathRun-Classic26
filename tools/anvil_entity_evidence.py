#!/usr/bin/env python3
"""Read-only entity metadata probe for archived pre-1.17 Anvil chunks.

The goal is to recover surviving map-author metadata such as ArmorStand names,
Tags, markers, item frames, or route words. It never changes the archive and
does not interpret an entity as a gameplay checkpoint without corroboration.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Sequence

from anvil_probe import (
    NbtReader,
    NbtError,
    REGION_RE,
    chunk_level,
    chunk_position,
    decompress_chunk,
    region_paths,
)


ROUTE_WORDS = {
    "checkpoint",
    "start",
    "finish",
    "end",
    "spawn",
    "lobby",
    "runner",
    "runners",
    "death",
    "stage",
}
WORD_RE = re.compile(r"[a-z0-9]+")
INTERESTING_ENTITY_IDS = {
    "armorstand",
    "armor_stand",
    "minecraft:armor_stand",
    "itemframe",
    "item_frame",
    "minecraft:item_frame",
    "glow_item_frame",
    "minecraft:glow_item_frame",
    "marker",
    "minecraft:marker",
    "area_effect_cloud",
    "minecraft:area_effect_cloud",
}


@dataclass(frozen=True)
class EntityEvidence:
    entity_id: str
    x: float | None
    y: float | None
    z: float | None
    custom_name: str
    tags: tuple[str, ...]
    invisible: bool
    marker: bool
    no_gravity: bool
    small: bool
    region: str
    chunk_x: int
    chunk_z: int
    depth: int

    @property
    def metadata_text(self) -> str:
        parts = [self.custom_name, *self.tags]
        return " ".join(part for part in parts if part)

    @property
    def route_words(self) -> tuple[str, ...]:
        tokens = set(WORD_RE.findall(self.metadata_text.lower()))
        return tuple(sorted(tokens & ROUTE_WORDS))


@dataclass
class EntityStats:
    regions: int = 0
    chunks: int = 0
    failed_chunks: int = 0
    external_chunks: int = 0
    all_entities: int = 0
    evidence_entities: int = 0
    named_entities: int = 0
    tagged_entities: int = 0
    route_keyword_entities: int = 0


@dataclass
class EntityProbeResult:
    stats: EntityStats
    entity_types: Counter[str]
    evidence: list[EntityEvidence]
    errors: list[str]


def _flatten_text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, list):
        return "".join(_flatten_text(item) for item in value)
    if isinstance(value, dict):
        parts: list[str] = []
        text_value = value.get("text")
        if isinstance(text_value, str):
            parts.append(text_value)
        translate = value.get("translate")
        if not parts and isinstance(translate, str):
            parts.append(translate)
        extra = value.get("extra")
        if isinstance(extra, list):
            parts.extend(_flatten_text(item) for item in extra)
        return "".join(parts)
    if isinstance(value, str):
        stripped = value.strip()
        if stripped.startswith(("{", "[", '"')):
            try:
                parsed = json.loads(value)
                if parsed != value:
                    return _flatten_text(parsed)
            except (json.JSONDecodeError, TypeError):
                pass
        return value
    return str(value)


def _clean_text(value: Any) -> str:
    return " ".join(
        _flatten_text(value)
        .replace("\r", " ")
        .replace("\n", " ")
        .replace("\t", " ")
        .split()
    )


def _entity_id(entry: dict[str, Any]) -> str:
    raw = entry.get("id", entry.get("Id", ""))
    return raw if isinstance(raw, str) else str(raw)


def _position(entry: dict[str, Any]) -> tuple[float | None, float | None, float | None]:
    raw = entry.get("Pos", entry.get("pos"))
    if isinstance(raw, list) and len(raw) >= 3:
        values: list[float | None] = []
        for value in raw[:3]:
            if isinstance(value, (int, float)):
                values.append(float(value))
            else:
                values.append(None)
        return values[0], values[1], values[2]

    result: list[float | None] = []
    for key in ("x", "y", "z"):
        value = entry.get(key)
        result.append(float(value) if isinstance(value, (int, float)) else None)
    return result[0], result[1], result[2]


def _tags(entry: dict[str, Any]) -> tuple[str, ...]:
    raw = entry.get("Tags", entry.get("tags", []))
    if not isinstance(raw, list):
        return ()
    return tuple(
        sorted(
            _clean_text(value)
            for value in raw
            if _clean_text(value)
        )
    )


def _bool_value(entry: dict[str, Any], *keys: str) -> bool:
    for key in keys:
        if key in entry:
            value = entry[key]
            if isinstance(value, (bool, int)):
                return bool(value)
    return False


def _normalize_id(entity_id: str) -> str:
    return entity_id.lower().replace("minecraft:", "")


def _interesting(record: EntityEvidence) -> bool:
    normalized = _normalize_id(record.entity_id)
    return (
        bool(record.custom_name)
        or bool(record.tags)
        or normalized in INTERESTING_ENTITY_IDS
        or record.entity_id.lower() in INTERESTING_ENTITY_IDS
        or record.marker
    )


def entity_records_from_level(
    level: dict[str, Any],
    region: str,
    chunk_x: int,
    chunk_z: int,
) -> tuple[list[EntityEvidence], Counter[str], int]:
    raw = level.get("Entities", level.get("entities", []))
    if not isinstance(raw, list):
        return [], Counter(), 0

    evidence: list[EntityEvidence] = []
    types: Counter[str] = Counter()
    total = 0

    def visit(entry: Any, depth: int) -> None:
        nonlocal total
        if not isinstance(entry, dict) or depth > 8:
            return

        entity_id = _entity_id(entry)
        if entity_id:
            types[entity_id] += 1
        total += 1

        x, y, z = _position(entry)
        custom_name = _clean_text(
            entry.get("CustomName", entry.get("custom_name", ""))
        )
        tags = _tags(entry)
        record = EntityEvidence(
            entity_id=entity_id or "(unknown)",
            x=x,
            y=y,
            z=z,
            custom_name=custom_name,
            tags=tags,
            invisible=_bool_value(entry, "Invisible", "invisible"),
            marker=_bool_value(entry, "Marker", "marker"),
            no_gravity=_bool_value(entry, "NoGravity", "no_gravity"),
            small=_bool_value(entry, "Small", "small"),
            region=region,
            chunk_x=chunk_x,
            chunk_z=chunk_z,
            depth=depth,
        )
        if _interesting(record):
            evidence.append(record)

        passengers = entry.get("Passengers", entry.get("passengers", []))
        if isinstance(passengers, list):
            for passenger in passengers:
                visit(passenger, depth + 1)

    for entry in raw:
        visit(entry, 0)

    return evidence, types, total


def scan_region(path: Path) -> EntityProbeResult:
    match = REGION_RE.match(path.name)
    if match is None:
        raise ValueError(f"not an Anvil region filename: {path}")

    region_x = int(match.group(1))
    region_z = int(match.group(2))
    data = path.read_bytes()
    if len(data) < 8192:
        raise ValueError(f"region file is too small: {path}")

    stats = EntityStats(regions=1)
    entity_types: Counter[str] = Counter()
    evidence: list[EntityEvidence] = []
    errors: list[str] = []

    for slot in range(1024):
        location = data[slot * 4 : slot * 4 + 4]
        sector_offset = int.from_bytes(location[:3], "big")
        sector_count = location[3]
        if sector_offset == 0 or sector_count == 0:
            continue

        fallback_x = region_x * 32 + (slot & 31)
        fallback_z = region_z * 32 + (slot >> 5)
        byte_offset = sector_offset * 4096

        try:
            if byte_offset + 5 > len(data):
                raise NbtError("chunk offset outside region file")

            length = int.from_bytes(data[byte_offset : byte_offset + 4], "big")
            if length <= 1:
                raise NbtError(f"invalid chunk payload length {length}")

            compression = data[byte_offset + 4]
            if compression & 0x80:
                stats.external_chunks += 1
                errors.append(
                    f"{path.name} chunk {fallback_x},{fallback_z}: external .mcc payload not available"
                )
                continue

            end = byte_offset + 4 + length
            if end > len(data):
                raise NbtError("chunk payload extends beyond region file")

            raw_nbt = decompress_chunk(compression, data[byte_offset + 5 : end])
            root = NbtReader(raw_nbt).root()
            level = chunk_level(root)
            if not level:
                raise NbtError("chunk root is not a compound")

            chunk_x, chunk_z = chunk_position(level, fallback_x, fallback_z)
            records, types, total = entity_records_from_level(
                level, path.name, chunk_x, chunk_z
            )
            evidence.extend(records)
            entity_types.update(types)
            stats.all_entities += total
            stats.chunks += 1
        except Exception as exc:
            stats.failed_chunks += 1
            errors.append(
                f"{path.name} chunk {fallback_x},{fallback_z}: {type(exc).__name__}: {exc}"
            )

    stats.evidence_entities = len(evidence)
    stats.named_entities = sum(bool(item.custom_name) for item in evidence)
    stats.tagged_entities = sum(bool(item.tags) for item in evidence)
    stats.route_keyword_entities = sum(bool(item.route_words) for item in evidence)
    evidence.sort(
        key=lambda item: (
            item.x if item.x is not None else float("inf"),
            item.y if item.y is not None else float("inf"),
            item.z if item.z is not None else float("inf"),
            item.entity_id,
            item.custom_name,
            item.tags,
        )
    )
    return EntityProbeResult(stats, entity_types, evidence, errors)


def merge_results(results: Sequence[EntityProbeResult]) -> EntityProbeResult:
    stats = EntityStats()
    entity_types: Counter[str] = Counter()
    evidence: list[EntityEvidence] = []
    errors: list[str] = []

    for result in results:
        stats.regions += result.stats.regions
        stats.chunks += result.stats.chunks
        stats.failed_chunks += result.stats.failed_chunks
        stats.external_chunks += result.stats.external_chunks
        stats.all_entities += result.stats.all_entities
        stats.evidence_entities += result.stats.evidence_entities
        stats.named_entities += result.stats.named_entities
        stats.tagged_entities += result.stats.tagged_entities
        stats.route_keyword_entities += result.stats.route_keyword_entities
        entity_types.update(result.entity_types)
        evidence.extend(result.evidence)
        errors.extend(result.errors)

    evidence.sort(
        key=lambda item: (
            item.x if item.x is not None else float("inf"),
            item.y if item.y is not None else float("inf"),
            item.z if item.z is not None else float("inf"),
            item.entity_id,
        )
    )
    return EntityProbeResult(stats, entity_types, evidence, errors)


def _fmt_coord(value: float | None) -> str:
    if value is None:
        return "unknown"
    if value.is_integer():
        return str(int(value))
    return f"{value:.3f}"


def render_report(result: EntityProbeResult, world_name: str) -> str:
    stats = result.stats
    lines = [
        "# DeathRun Classic26 archived entity evidence",
        f"world={world_name}",
        (
            "entity_evidence_summary "
            f"regions={stats.regions} chunks={stats.chunks} "
            f"failed_chunks={stats.failed_chunks} external_chunks={stats.external_chunks} "
            f"all_entities={stats.all_entities} evidence_entities={stats.evidence_entities} "
            f"named_entities={stats.named_entities} tagged_entities={stats.tagged_entities} "
            f"route_keyword_entities={stats.route_keyword_entities}"
        ),
        "# ENTITY_EVIDENCE is preserved NBT metadata, not an automatic gameplay role.",
        "",
    ]

    for entity_id, count in sorted(
        result.entity_types.items(),
        key=lambda item: (-item[1], item[0].lower()),
    ):
        lines.append(f"ENTITY_TYPE\tcount={count}\tid={entity_id}")

    lines.append("")
    for item in result.evidence:
        pos = ",".join(_fmt_coord(value) for value in (item.x, item.y, item.z))
        route = ",".join(item.route_words) or "none"
        tags = ",".join(item.tags) or "none"
        name = item.custom_name or "none"
        lines.append(
            "ENTITY_EVIDENCE\t"
            f"id={item.entity_id}\tpos={pos}\tname={name}\ttags={tags}\t"
            f"route_words={route}\tinvisible={str(item.invisible).lower()}\t"
            f"marker={str(item.marker).lower()}\tno_gravity={str(item.no_gravity).lower()}\t"
            f"small={str(item.small).lower()}\tdepth={item.depth}\t"
            f"region={item.region}\tchunk={item.chunk_x},{item.chunk_z}"
        )

    if result.errors:
        lines.append("")
        lines.append("# Errors / skipped chunks")
        lines.extend(f"ERROR\t{error}" for error in result.errors)

    return "\n".join(lines) + "\n"


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inputs", nargs="+")
    parser.add_argument("--world-name", default="unknown-world")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--min-chunks", type=int, default=0)
    args = parser.parse_args(argv)

    try:
        paths = region_paths(args.inputs)
        if not paths:
            raise FileNotFoundError("no .mca region files found")
        result = merge_results([scan_region(path) for path in paths])
    except Exception as exc:
        print(f"anvil_entity_evidence: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 2

    report = render_report(result, args.world_name)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if result.stats.chunks < args.min_chunks:
        print(
            f"anvil_entity_evidence: expected at least {args.min_chunks} chunks, got {result.stats.chunks}",
            file=sys.stderr,
        )
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
