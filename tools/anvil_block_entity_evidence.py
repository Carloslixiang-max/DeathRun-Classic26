#!/usr/bin/env python3
"""Read-only inventory of archived Minecraft BlockEntity metadata.

Signs and command blocks already have dedicated probes. This tool closes the
remaining NBT gap by counting every BlockEntity type and emitting non-sign
BlockEntities plus selected textual metadata that may preserve map-author
markers such as structure names, container names, locks, or loot-table IDs.
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Sequence

from anvil_probe import (
    NbtError,
    NbtReader,
    REGION_RE,
    block_entities,
    chunk_level,
    chunk_position,
    decompress_chunk,
    region_paths,
)


WORD_RE = re.compile(r"[a-z0-9]+")
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
INTERESTING_KEY_PARTS = (
    "name",
    "tag",
    "author",
    "metadata",
    "structure",
    "loot",
    "lock",
    "command",
    "book",
    "text",
)


@dataclass(frozen=True)
class BlockEntityEvidence:
    entity_id: str
    x: int | None
    y: int | None
    z: int | None
    metadata: tuple[tuple[str, str], ...]
    region: str
    chunk_x: int
    chunk_z: int

    @property
    def metadata_text(self) -> str:
        return " ".join(value for _key, value in self.metadata)

    @property
    def route_words(self) -> tuple[str, ...]:
        tokens = set(WORD_RE.findall(self.metadata_text.lower()))
        return tuple(sorted(tokens & ROUTE_WORDS))


@dataclass
class BlockEntityStats:
    regions: int = 0
    chunks: int = 0
    failed_chunks: int = 0
    external_chunks: int = 0
    all_block_entities: int = 0
    non_sign_block_entities: int = 0
    evidence_entities: int = 0
    route_keyword_entities: int = 0


@dataclass
class BlockEntityProbeResult:
    stats: BlockEntityStats
    entity_types: Counter[str]
    evidence: list[BlockEntityEvidence]
    errors: list[str]


def _entity_id(entry: dict[str, Any]) -> str:
    raw = entry.get("id", entry.get("Id", ""))
    return raw if isinstance(raw, str) else str(raw)


def _bare_id(entity_id: str) -> str:
    return entity_id.lower().removeprefix("minecraft:")


def _is_sign(entity_id: str) -> bool:
    bare = _bare_id(entity_id)
    return bare == "sign" or bare.endswith("_sign") or bare.endswith("_hanging_sign")


def _sanitize(value: Any) -> str:
    if isinstance(value, bytes):
        return f"<bytes:{len(value)}>"
    text = str(value).replace("\r", " ").replace("\n", " ").replace("\t", " ")
    return " ".join(text.split())[:500]


def _metadata(entry: dict[str, Any], max_items: int = 64) -> tuple[tuple[str, str], ...]:
    found: list[tuple[str, str]] = []

    def visit(value: Any, path: str, depth: int) -> None:
        if len(found) >= max_items or depth > 5:
            return
        if isinstance(value, dict):
            for key in sorted(value, key=str):
                child = value[key]
                child_path = f"{path}.{key}" if path else str(key)
                key_lower = str(key).lower()
                if isinstance(child, str) and any(
                    part in key_lower for part in INTERESTING_KEY_PARTS
                ):
                    cleaned = _sanitize(child)
                    if cleaned:
                        found.append((child_path, cleaned))
                        if len(found) >= max_items:
                            return
                elif isinstance(child, (dict, list)):
                    visit(child, child_path, depth + 1)
        elif isinstance(value, list):
            for index, child in enumerate(value[:64]):
                visit(child, f"{path}[{index}]", depth + 1)

    visit(entry, "", 0)

    # id is structural, not useful metadata, and would create false "end" hits
    # from unrelated entity names.
    filtered = [
        (key, value)
        for key, value in found
        if key.lower() not in {"id", "Id".lower()}
    ]
    return tuple(filtered)


def evidence_from_level(
    level: dict[str, Any],
    region: str,
    chunk_x: int,
    chunk_z: int,
) -> tuple[list[BlockEntityEvidence], Counter[str], int, int]:
    raw = block_entities(level)
    types: Counter[str] = Counter()
    evidence: list[BlockEntityEvidence] = []
    non_sign = 0

    for entry in raw:
        entity_id = _entity_id(entry) or "(unknown)"
        types[entity_id] += 1
        if _is_sign(entity_id):
            continue

        non_sign += 1
        x = entry.get("x")
        y = entry.get("y")
        z = entry.get("z")
        coords = tuple(
            int(value) if isinstance(value, int) else None
            for value in (x, y, z)
        )
        evidence.append(
            BlockEntityEvidence(
                entity_id=entity_id,
                x=coords[0],
                y=coords[1],
                z=coords[2],
                metadata=_metadata(entry),
                region=region,
                chunk_x=chunk_x,
                chunk_z=chunk_z,
            )
        )

    return evidence, types, len(raw), non_sign


def scan_region(path: Path) -> BlockEntityProbeResult:
    match = REGION_RE.match(path.name)
    if match is None:
        raise ValueError(f"not an Anvil region filename: {path}")

    region_x = int(match.group(1))
    region_z = int(match.group(2))
    data = path.read_bytes()
    if len(data) < 8192:
        raise ValueError(f"region file is too small: {path}")

    stats = BlockEntityStats(regions=1)
    types: Counter[str] = Counter()
    evidence: list[BlockEntityEvidence] = []
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

            root = NbtReader(
                decompress_chunk(compression, data[byte_offset + 5 : end])
            ).root()
            level = chunk_level(root)
            if not level:
                raise NbtError("chunk root is not a compound")

            chunk_x, chunk_z = chunk_position(level, fallback_x, fallback_z)
            records, chunk_types, total, non_sign = evidence_from_level(
                level, path.name, chunk_x, chunk_z
            )
            evidence.extend(records)
            types.update(chunk_types)
            stats.all_block_entities += total
            stats.non_sign_block_entities += non_sign
            stats.chunks += 1
        except Exception as exc:
            stats.failed_chunks += 1
            errors.append(
                f"{path.name} chunk {fallback_x},{fallback_z}: {type(exc).__name__}: {exc}"
            )

    stats.evidence_entities = len(evidence)
    stats.route_keyword_entities = sum(bool(item.route_words) for item in evidence)
    evidence.sort(
        key=lambda item: (
            item.x if item.x is not None else 10**9,
            item.y if item.y is not None else 10**9,
            item.z if item.z is not None else 10**9,
            item.entity_id,
        )
    )
    return BlockEntityProbeResult(stats, types, evidence, errors)


def merge_results(
    results: Sequence[BlockEntityProbeResult],
) -> BlockEntityProbeResult:
    stats = BlockEntityStats()
    types: Counter[str] = Counter()
    evidence: list[BlockEntityEvidence] = []
    errors: list[str] = []

    for result in results:
        stats.regions += result.stats.regions
        stats.chunks += result.stats.chunks
        stats.failed_chunks += result.stats.failed_chunks
        stats.external_chunks += result.stats.external_chunks
        stats.all_block_entities += result.stats.all_block_entities
        stats.non_sign_block_entities += result.stats.non_sign_block_entities
        stats.evidence_entities += result.stats.evidence_entities
        stats.route_keyword_entities += result.stats.route_keyword_entities
        types.update(result.entity_types)
        evidence.extend(result.evidence)
        errors.extend(result.errors)

    evidence.sort(
        key=lambda item: (
            item.x if item.x is not None else 10**9,
            item.y if item.y is not None else 10**9,
            item.z if item.z is not None else 10**9,
            item.entity_id,
        )
    )
    return BlockEntityProbeResult(stats, types, evidence, errors)


def _coord(value: int | None) -> str:
    return "unknown" if value is None else str(value)


def render_report(result: BlockEntityProbeResult, world_name: str) -> str:
    stats = result.stats
    lines = [
        "# DeathRun Classic26 BlockEntity inventory",
        f"world={world_name}",
        (
            "block_entity_summary "
            f"regions={stats.regions} chunks={stats.chunks} "
            f"failed_chunks={stats.failed_chunks} external_chunks={stats.external_chunks} "
            f"all_block_entities={stats.all_block_entities} "
            f"non_sign_block_entities={stats.non_sign_block_entities} "
            f"evidence_entities={stats.evidence_entities} "
            f"route_keyword_entities={stats.route_keyword_entities}"
        ),
        "# Non-sign BlockEntities are surfaced for review; no gameplay role is inferred.",
        "",
    ]

    for entity_id, count in sorted(
        result.entity_types.items(),
        key=lambda item: (-item[1], item[0].lower()),
    ):
        lines.append(f"BLOCK_ENTITY_TYPE\tcount={count}\tid={entity_id}")

    lines.append("")
    for item in result.evidence:
        metadata = ";".join(f"{key}={value}" for key, value in item.metadata) or "none"
        route = ",".join(item.route_words) or "none"
        pos = ",".join(_coord(value) for value in (item.x, item.y, item.z))
        lines.append(
            "BLOCK_ENTITY_EVIDENCE\t"
            f"id={item.entity_id}\tpos={pos}\troute_words={route}\t"
            f"metadata={metadata}\tregion={item.region}\t"
            f"chunk={item.chunk_x},{item.chunk_z}"
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
        print(
            f"anvil_block_entity_evidence: {type(exc).__name__}: {exc}",
            file=sys.stderr,
        )
        return 2

    report = render_report(result, args.world_name)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if result.stats.chunks < args.min_chunks:
        print(
            f"anvil_block_entity_evidence: expected at least {args.min_chunks} chunks, got {result.stats.chunks}",
            file=sys.stderr,
        )
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
