#!/usr/bin/env python3
"""Read-only Minecraft level.dat metadata probe for archived DeathRun worlds."""

from __future__ import annotations

import argparse
import gzip
import sys
from pathlib import Path
from typing import Any, Sequence

from anvil_probe import NbtReader


def read_level_dat(path: Path) -> dict[str, Any]:
    payload = path.read_bytes()
    if payload.startswith(b"\x1f\x8b"):
        payload = gzip.decompress(payload)
    root = NbtReader(payload).root()
    if not isinstance(root, dict):
        raise ValueError("level.dat root is not a compound")
    data = root.get("Data", root)
    if not isinstance(data, dict):
        raise ValueError("level.dat Data tag is not a compound")
    return data


def _value(data: dict[str, Any], *names: str) -> Any:
    for name in names:
        if name in data:
            return data[name]
    return None


def extract_metadata(data: dict[str, Any]) -> dict[str, Any]:
    spawn = (
        _value(data, "SpawnX"),
        _value(data, "SpawnY"),
        _value(data, "SpawnZ"),
    )
    return {
        "data_version": _value(data, "DataVersion"),
        "level_name": _value(data, "LevelName"),
        "spawn": spawn,
        "game_type": _value(data, "GameType"),
        "difficulty": _value(data, "Difficulty"),
        "hardcore": _value(data, "hardcore", "Hardcore"),
        "initialized": _value(data, "initialized", "Initialized"),
        "time": _value(data, "Time"),
        "day_time": _value(data, "DayTime"),
        "version_name": (
            data.get("Version", {}).get("Name")
            if isinstance(data.get("Version"), dict)
            else None
        ),
    }


def _render(value: Any) -> str:
    if value is None:
        return "unknown"
    if isinstance(value, str):
        return value.replace("\t", " ").replace("\n", " ")
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


def render_report(path: Path, metadata: dict[str, Any]) -> str:
    sx, sy, sz = metadata["spawn"]
    spawn = ",".join(_render(value) for value in (sx, sy, sz))
    fields = [
        f"path={path.name}",
        f"data_version={_render(metadata['data_version'])}",
        f"version_name={_render(metadata['version_name'])}",
        f"level_name={_render(metadata['level_name'])}",
        f"spawn={spawn}",
        f"game_type={_render(metadata['game_type'])}",
        f"difficulty={_render(metadata['difficulty'])}",
        f"hardcore={_render(metadata['hardcore'])}",
        f"initialized={_render(metadata['initialized'])}",
        f"time={_render(metadata['time'])}",
        f"day_time={_render(metadata['day_time'])}",
    ]
    return "leveldat\t" + "\t".join(fields) + "\n"


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("level_dat", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--require-spawn", action="store_true")
    args = parser.parse_args(argv)

    try:
        metadata = extract_metadata(read_level_dat(args.level_dat))
    except Exception as exc:
        print(f"leveldat_probe: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 2

    report = render_report(args.level_dat, metadata)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)

    if args.require_spawn and any(value is None for value in metadata["spawn"]):
        print("leveldat_probe: SpawnX/SpawnY/SpawnZ are incomplete", file=sys.stderr)
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
