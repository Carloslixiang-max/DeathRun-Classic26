#!/usr/bin/env python3
"""Read-only Minecraft Anvil (.mca) candidate scanner.

This tool intentionally uses only the Python standard library. It supports:
- legacy numeric section storage (Blocks/Data/Add)
- palette/block-state section storage used by modern Anvil chunks
- legacy TileEntities and modern block_entities command block records

It never modifies a world. The output is intended as research evidence for
authoring Classic26 maps, not as an automatic trap-type classifier.
"""

from __future__ import annotations

import argparse
import gzip
import io
import json
import math
import os
import re
import struct
import sys
import zlib
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Iterator, Sequence


REGION_RE = re.compile(r"^r\.(-?\d+)\.(-?\d+)\.mca$", re.IGNORECASE)

LEGACY_CANDIDATES = {
    70: ("PRESSURE_PLATE", "minecraft:stone_pressure_plate"),
    72: ("PRESSURE_PLATE", "minecraft:oak_pressure_plate"),
    77: ("BUTTON", "minecraft:stone_button"),
    90: ("PORTAL", "minecraft:nether_portal"),
    119: ("PORTAL", "minecraft:end_portal"),
    120: ("PORTAL", "minecraft:end_portal_frame"),
    137: ("COMMAND_BLOCK", "minecraft:command_block"),
    143: ("BUTTON", "minecraft:oak_button"),
    147: ("PRESSURE_PLATE", "minecraft:light_weighted_pressure_plate"),
    148: ("PRESSURE_PLATE", "minecraft:heavy_weighted_pressure_plate"),
    209: ("PORTAL", "minecraft:end_gateway"),
    210: ("COMMAND_BLOCK", "minecraft:repeating_command_block"),
    211: ("COMMAND_BLOCK", "minecraft:chain_command_block"),
}

COMMAND_ENTITY_IDS = {
    "control",
    "minecraft:command_block",
}


class NbtError(ValueError):
    pass


class NbtReader:
    def __init__(self, data: bytes):
        self.stream = io.BytesIO(data)

    def _read(self, count: int) -> bytes:
        data = self.stream.read(count)
        if len(data) != count:
            raise NbtError(f"unexpected EOF: wanted {count}, got {len(data)}")
        return data

    def _unpack(self, fmt: str) -> Any:
        size = struct.calcsize(fmt)
        return struct.unpack(fmt, self._read(size))[0]

    def _string(self) -> str:
        length = self._unpack(">H")
        return self._read(length).decode("utf-8", errors="replace")

    def payload(self, tag_type: int, depth: int = 0) -> Any:
        if depth > 128:
            raise NbtError("NBT nesting limit exceeded")

        if tag_type == 0:
            return None
        if tag_type == 1:
            return self._unpack(">b")
        if tag_type == 2:
            return self._unpack(">h")
        if tag_type == 3:
            return self._unpack(">i")
        if tag_type == 4:
            return self._unpack(">q")
        if tag_type == 5:
            return self._unpack(">f")
        if tag_type == 6:
            return self._unpack(">d")
        if tag_type == 7:
            length = self._unpack(">i")
            if length < 0:
                raise NbtError("negative byte-array length")
            return self._read(length)
        if tag_type == 8:
            return self._string()
        if tag_type == 9:
            child_type = self._unpack(">B")
            length = self._unpack(">i")
            if length < 0:
                raise NbtError("negative list length")
            return [self.payload(child_type, depth + 1) for _ in range(length)]
        if tag_type == 10:
            result: dict[str, Any] = {}
            while True:
                child_type = self._unpack(">B")
                if child_type == 0:
                    break
                name = self._string()
                result[name] = self.payload(child_type, depth + 1)
            return result
        if tag_type == 11:
            length = self._unpack(">i")
            if length < 0:
                raise NbtError("negative int-array length")
            return [self._unpack(">i") for _ in range(length)]
        if tag_type == 12:
            length = self._unpack(">i")
            if length < 0:
                raise NbtError("negative long-array length")
            return [self._unpack(">q") for _ in range(length)]

        raise NbtError(f"unsupported NBT tag type {tag_type}")

    def root(self) -> Any:
        tag_type = self._unpack(">B")
        if tag_type == 0:
            return None
        _name = self._string()
        return self.payload(tag_type, 0)


@dataclass(frozen=True)
class BlockCandidate:
    category: str
    name: str
    x: int
    y: int
    z: int
    region: str
    chunk_x: int
    chunk_z: int
    properties: tuple[tuple[str, str], ...] = ()


@dataclass(frozen=True)
class CommandEntity:
    entity_id: str
    x: int
    y: int
    z: int
    command: str
    region: str
    chunk_x: int
    chunk_z: int


@dataclass(frozen=True)
class SignEntity:
    entity_id: str
    x: int
    y: int
    z: int
    text: str
    region: str
    chunk_x: int
    chunk_z: int


@dataclass
class ProbeStats:
    regions: int = 0
    chunks: int = 0
    failed_chunks: int = 0
    external_chunks: int = 0
    candidate_blocks: int = 0
    command_entities: int = 0
    sign_entities: int = 0


@dataclass
class ProbeResult:
    stats: ProbeStats
    blocks: list[BlockCandidate]
    commands: list[CommandEntity]
    signs: list[SignEntity]
    errors: list[str]


def candidate_category(name: str) -> str | None:
    normalized = name.lower()
    if normalized.endswith("_button"):
        return "BUTTON"
    if normalized.endswith("_pressure_plate"):
        return "PRESSURE_PLATE"
    if normalized.endswith("_wall_sign") or normalized.endswith("_sign"):
        return "SIGN_BLOCK"
    if normalized in {
        "minecraft:command_block",
        "minecraft:chain_command_block",
        "minecraft:repeating_command_block",
    }:
        return "COMMAND_BLOCK"
    if normalized in {
        "minecraft:nether_portal",
        "minecraft:end_portal",
        "minecraft:end_portal_frame",
        "minecraft:end_gateway",
    }:
        return "PORTAL"
    return None


def palette_name(entry: Any) -> str | None:
    if isinstance(entry, str):
        return entry
    if not isinstance(entry, dict):
        return None
    value = entry.get("Name", entry.get("name"))
    return value if isinstance(value, str) else None


def palette_properties(entry: Any) -> tuple[tuple[str, str], ...]:
    if not isinstance(entry, dict):
        return ()
    raw = entry.get("Properties", entry.get("properties"))
    if not isinstance(raw, dict):
        return ()
    return tuple(
        sorted(
            (str(key), str(value))
            for key, value in raw.items()
            if isinstance(key, str)
        )
    )


def _unsigned_long(value: int) -> int:
    return value & ((1 << 64) - 1)


def decode_palette_index(
    data: Sequence[int],
    block_index: int,
    palette_size: int,
) -> int:
    if palette_size <= 1:
        return 0
    if not data:
        return 0

    bits = max(4, (palette_size - 1).bit_length())
    mask = (1 << bits) - 1
    padded_per_long = 64 // bits
    padded_expected = math.ceil(4096 / padded_per_long)
    compact_expected = math.ceil(4096 * bits / 64)

    # Minecraft 1.16+ stores a whole number of entries in each long.
    if len(data) == padded_expected or (
        len(data) != compact_expected and len(data) >= padded_expected
    ):
        long_index = block_index // padded_per_long
        if long_index >= len(data):
            return 0
        shift = (block_index % padded_per_long) * bits
        return (_unsigned_long(data[long_index]) >> shift) & mask

    # Older palette storage allowed an entry to cross a 64-bit boundary.
    bit_index = block_index * bits
    long_index = bit_index // 64
    start = bit_index % 64
    if long_index >= len(data):
        return 0

    value = _unsigned_long(data[long_index]) >> start
    used = 64 - start
    if used < bits and long_index + 1 < len(data):
        value |= _unsigned_long(data[long_index + 1]) << used
    return value & mask


def nibble(data: bytes | bytearray | None, index: int) -> int:
    if not data:
        return 0
    byte_index = index >> 1
    if byte_index >= len(data):
        return 0
    raw = data[byte_index]
    return (raw >> 4) & 0xF if index & 1 else raw & 0xF


def section_y(section: dict[str, Any]) -> int | None:
    value = section.get("Y", section.get("y"))
    return int(value) if isinstance(value, int) else None


def scan_modern_section(
    section: dict[str, Any],
    chunk_x: int,
    chunk_z: int,
    region: str,
) -> list[BlockCandidate]:
    sy = section_y(section)
    if sy is None:
        return []

    palette = section.get("Palette")
    data = section.get("BlockStates")

    if palette is None:
        block_states = section.get("block_states")
        if isinstance(block_states, dict):
            palette = block_states.get("palette")
            data = block_states.get("data")

    if not isinstance(palette, list) or not palette:
        return []
    if not isinstance(data, list):
        data = []

    names = [palette_name(entry) for entry in palette]
    interesting = {
        index: (candidate_category(name), name, palette_properties(palette[index]))
        for index, name in enumerate(names)
        if isinstance(name, str) and candidate_category(name) is not None
    }
    if not interesting:
        return []

    result: list[BlockCandidate] = []
    for index in range(4096):
        palette_index = decode_palette_index(data, index, len(palette))
        candidate = interesting.get(palette_index)
        if candidate is None:
            continue

        category, name, properties = candidate
        local_x = index & 15
        local_z = (index >> 4) & 15
        local_y = (index >> 8) & 15
        result.append(
            BlockCandidate(
                category=category or "UNKNOWN",
                name=name,
                x=chunk_x * 16 + local_x,
                y=sy * 16 + local_y,
                z=chunk_z * 16 + local_z,
                region=region,
                chunk_x=chunk_x,
                chunk_z=chunk_z,
                properties=properties,
            )
        )

    return result


def scan_legacy_section(
    section: dict[str, Any],
    chunk_x: int,
    chunk_z: int,
    region: str,
) -> list[BlockCandidate]:
    sy = section_y(section)
    blocks = section.get("Blocks")
    if sy is None or not isinstance(blocks, (bytes, bytearray)) or len(blocks) < 4096:
        return []

    add = section.get("Add")
    add_bytes = add if isinstance(add, (bytes, bytearray)) else None

    result: list[BlockCandidate] = []
    for index in range(4096):
        block_id = blocks[index] | (nibble(add_bytes, index) << 8)
        candidate = LEGACY_CANDIDATES.get(block_id)
        if candidate is None:
            continue

        category, name = candidate
        local_x = index & 15
        local_z = (index >> 4) & 15
        local_y = (index >> 8) & 15
        result.append(
            BlockCandidate(
                category=category,
                name=name,
                x=chunk_x * 16 + local_x,
                y=sy * 16 + local_y,
                z=chunk_z * 16 + local_z,
                region=region,
                chunk_x=chunk_x,
                chunk_z=chunk_z,
            )
        )

    return result


def chunk_level(root: Any) -> dict[str, Any]:
    if not isinstance(root, dict):
        return {}
    level = root.get("Level")
    return level if isinstance(level, dict) else root


def chunk_position(level: dict[str, Any], fallback_x: int, fallback_z: int) -> tuple[int, int]:
    x = level.get("xPos", fallback_x)
    z = level.get("zPos", fallback_z)
    return (
        int(x) if isinstance(x, int) else fallback_x,
        int(z) if isinstance(z, int) else fallback_z,
    )


def block_entities(level: dict[str, Any]) -> list[dict[str, Any]]:
    raw = level.get("block_entities", level.get("TileEntities", []))
    return [entry for entry in raw if isinstance(entry, dict)] if isinstance(raw, list) else []


def command_entities_from_chunk(
    level: dict[str, Any],
    region: str,
    chunk_x: int,
    chunk_z: int,
) -> list[CommandEntity]:
    result: list[CommandEntity] = []
    for entry in block_entities(level):
        entity_id = entry.get("id", entry.get("Id", ""))
        if not isinstance(entity_id, str) or entity_id.lower() not in COMMAND_ENTITY_IDS:
            continue

        x = entry.get("x")
        y = entry.get("y")
        z = entry.get("z")
        if not all(isinstance(value, int) for value in (x, y, z)):
            continue

        command = entry.get("Command", entry.get("command", ""))
        if not isinstance(command, str):
            command = str(command)

        result.append(
            CommandEntity(
                entity_id=entity_id,
                x=int(x),
                y=int(y),
                z=int(z),
                command=command.replace("\r", " ").replace("\n", " ").strip(),
                region=region,
                chunk_x=chunk_x,
                chunk_z=chunk_z,
            )
        )
    return result


def _flatten_text_component(value: Any) -> str:
    """Return readable text from legacy/modern JSON text components."""
    if value is None:
        return ""
    if isinstance(value, list):
        return "".join(_flatten_text_component(item) for item in value)
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
            parts.extend(_flatten_text_component(item) for item in extra)
        return "".join(parts)
    if isinstance(value, str):
        stripped = value.strip()
        if stripped.startswith(("{", "[", '"')):
            try:
                parsed = json.loads(value)
                if parsed != value:
                    return _flatten_text_component(parsed)
            except (json.JSONDecodeError, TypeError):
                pass
        return value
    return str(value)


def _clean_sign_line(value: Any) -> str:
    return " ".join(
        _flatten_text_component(value)
        .replace("\\r", " ")
        .replace("\\n", " ")
        .replace("\\t", " ")
        .split()
    )


def _is_sign_entity_id(entity_id: str) -> bool:
    normalized = entity_id.lower()
    bare = normalized.removeprefix("minecraft:")
    return bare == "sign" or bare.endswith("_sign") or bare.endswith("_hanging_sign")


def _sign_lines(entry: dict[str, Any]) -> list[str]:
    legacy = [_clean_sign_line(entry.get(f"Text{index}", "")) for index in range(1, 5)]
    if any(legacy):
        return legacy

    front_text = entry.get("front_text")
    if isinstance(front_text, dict):
        messages = front_text.get("messages")
        if isinstance(messages, list):
            return [_clean_sign_line(message) for message in messages]

    return legacy


def sign_entities_from_chunk(
    level: dict[str, Any],
    region: str,
    chunk_x: int,
    chunk_z: int,
) -> list[SignEntity]:
    result: list[SignEntity] = []
    for entry in block_entities(level):
        entity_id = entry.get("id", entry.get("Id", ""))
        if not isinstance(entity_id, str) or not _is_sign_entity_id(entity_id):
            continue

        x = entry.get("x")
        y = entry.get("y")
        z = entry.get("z")
        if not all(isinstance(value, int) for value in (x, y, z)):
            continue

        lines = [line for line in _sign_lines(entry) if line]
        result.append(
            SignEntity(
                entity_id=entity_id,
                x=int(x),
                y=int(y),
                z=int(z),
                text=" | ".join(lines),
                region=region,
                chunk_x=chunk_x,
                chunk_z=chunk_z,
            )
        )
    return result


def decompress_chunk(compression: int, payload: bytes) -> bytes:
    kind = compression & 0x7F
    if kind == 1:
        return gzip.decompress(payload)
    if kind == 2:
        return zlib.decompress(payload)
    if kind == 3:
        return payload
    raise NbtError(f"unsupported Anvil compression type {kind}")


def scan_region(path: Path) -> ProbeResult:
    match = REGION_RE.match(path.name)
    if match is None:
        raise ValueError(f"not an Anvil region filename: {path}")

    region_x = int(match.group(1))
    region_z = int(match.group(2))
    data = path.read_bytes()
    if len(data) < 8192:
        raise ValueError(f"region file is too small: {path}")

    stats = ProbeStats(regions=1)
    blocks: list[BlockCandidate] = []
    commands: list[CommandEntity] = []
    signs: list[SignEntity] = []
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
            sections = level.get("sections", level.get("Sections", []))
            if not isinstance(sections, list):
                sections = []

            for section in sections:
                if not isinstance(section, dict):
                    continue
                if isinstance(section.get("Blocks"), (bytes, bytearray)):
                    blocks.extend(scan_legacy_section(section, chunk_x, chunk_z, path.name))
                else:
                    blocks.extend(scan_modern_section(section, chunk_x, chunk_z, path.name))

            commands.extend(
                command_entities_from_chunk(level, path.name, chunk_x, chunk_z)
            )
            signs.extend(
                sign_entities_from_chunk(level, path.name, chunk_x, chunk_z)
            )
            stats.chunks += 1
        except Exception as exc:  # keep scanning the rest of the archive
            stats.failed_chunks += 1
            errors.append(
                f"{path.name} chunk {fallback_x},{fallback_z}: {type(exc).__name__}: {exc}"
            )

    stats.candidate_blocks = len(blocks)
    stats.command_entities = len(commands)
    stats.sign_entities = len(signs)
    return ProbeResult(
        stats=stats,
        blocks=blocks,
        commands=commands,
        signs=signs,
        errors=errors,
    )


def merge_results(results: Sequence[ProbeResult]) -> ProbeResult:
    stats = ProbeStats()
    blocks: list[BlockCandidate] = []
    commands: list[CommandEntity] = []
    signs: list[SignEntity] = []
    errors: list[str] = []

    for result in results:
        stats.regions += result.stats.regions
        stats.chunks += result.stats.chunks
        stats.failed_chunks += result.stats.failed_chunks
        stats.external_chunks += result.stats.external_chunks
        stats.candidate_blocks += result.stats.candidate_blocks
        stats.command_entities += result.stats.command_entities
        stats.sign_entities += result.stats.sign_entities
        blocks.extend(result.blocks)
        commands.extend(result.commands)
        signs.extend(result.signs)
        errors.extend(result.errors)

    blocks.sort(key=lambda item: (item.x, item.y, item.z, item.category, item.name))
    commands.sort(key=lambda item: (item.x, item.y, item.z, item.entity_id))
    signs.sort(key=lambda item: (item.x, item.y, item.z, item.entity_id, item.text))
    return ProbeResult(
        stats=stats,
        blocks=blocks,
        commands=commands,
        signs=signs,
        errors=errors,
    )


def region_paths(inputs: Iterable[str]) -> list[Path]:
    found: set[Path] = set()
    for raw in inputs:
        path = Path(raw)
        if path.is_dir():
            for candidate in path.rglob("*.mca"):
                if REGION_RE.match(candidate.name):
                    found.add(candidate)
        elif path.is_file() and REGION_RE.match(path.name):
            found.add(path)
        else:
            raise FileNotFoundError(f"region input not found or unsupported: {path}")
    return sorted(found)


def render_report(result: ProbeResult, world_name: str) -> str:
    lines = [
        "# DeathRun Classic26 Anvil region probe",
        f"world={world_name}",
        (
            "summary "
            f"regions={result.stats.regions} "
            f"chunks={result.stats.chunks} "
            f"failed_chunks={result.stats.failed_chunks} "
            f"external_chunks={result.stats.external_chunks} "
            f"candidate_blocks={result.stats.candidate_blocks} "
            f"command_entities={result.stats.command_entities} "
            f"sign_entities={result.stats.sign_entities}"
        ),
        "",
    ]

    for block in result.blocks:
        properties = ",".join(f"{key}:{value}" for key, value in block.properties)
        suffix = f"\tprops={properties}" if properties else ""
        lines.append(
            "BLOCK\t"
            f"{block.category}\t{block.name}\t"
            f"{block.x}\t{block.y}\t{block.z}\t"
            f"region={block.region}\tchunk={block.chunk_x},{block.chunk_z}"
            f"{suffix}"
        )

    for command in result.commands:
        lines.append(
            "COMMAND\t"
            f"{command.entity_id}\t{command.x}\t{command.y}\t{command.z}\t"
            f"region={command.region}\tchunk={command.chunk_x},{command.chunk_z}\t"
            f"cmd={command.command}"
        )

    for sign in result.signs:
        lines.append(
            "SIGN\t"
            f"{sign.entity_id}\t{sign.x}\t{sign.y}\t{sign.z}\t"
            f"region={sign.region}\tchunk={sign.chunk_x},{sign.chunk_z}\t"
            f"text={sign.text}"
        )

    if result.errors:
        lines.append("")
        lines.append("# Errors / skipped chunks")
        lines.extend(f"ERROR\t{error}" for error in result.errors)

    return "\n".join(lines) + "\n"


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inputs", nargs="+", help=".mca files or directories containing region files")
    parser.add_argument("--world-name", default="unknown-world")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--min-chunks", type=int, default=0)
    parser.add_argument("--min-candidates", type=int, default=0)
    args = parser.parse_args(argv)

    paths = region_paths(args.inputs)
    if not paths:
        parser.error("no .mca region files found")

    result = merge_results([scan_region(path) for path in paths])
    report = render_report(result, args.world_name)

    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if result.stats.chunks < args.min_chunks:
        print(
            f"anvil_probe: expected at least {args.min_chunks} parsed chunks, got {result.stats.chunks}",
            file=sys.stderr,
        )
        return 2

    total_candidates = result.stats.candidate_blocks + result.stats.command_entities
    if total_candidates < args.min_candidates:
        print(
            f"anvil_probe: expected at least {args.min_candidates} candidates, got {total_candidates}",
            file=sys.stderr,
        )
        return 3

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
