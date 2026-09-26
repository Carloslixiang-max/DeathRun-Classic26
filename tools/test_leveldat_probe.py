#!/usr/bin/env python3

import gzip
import struct
import tempfile
import unittest
from pathlib import Path

from leveldat_probe import extract_metadata, read_level_dat, render_report


def nbt_string(value):
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def named_int(name, value):
    return b"\x03" + nbt_string(name) + struct.pack(">i", value)


def named_long(name, value):
    return b"\x04" + nbt_string(name) + struct.pack(">q", value)


def named_byte(name, value):
    return b"\x01" + nbt_string(name) + struct.pack(">b", value)


def named_string(name, value):
    return b"\x08" + nbt_string(name) + nbt_string(value)


def fixture_level_dat():
    data_payload = b"".join(
        [
            named_int("DataVersion", 2586),
            named_string("LevelName", "To Bee Fixture"),
            named_int("SpawnX", 12),
            named_int("SpawnY", 70),
            named_int("SpawnZ", -34),
            named_int("GameType", 2),
            named_byte("Difficulty", 2),
            named_byte("hardcore", 0),
            named_byte("initialized", 1),
            named_long("Time", 12345),
            named_long("DayTime", 6000),
            b"\x00",
        ]
    )
    root = b"\x0a\x00\x00" + b"\x0a" + nbt_string("Data") + data_payload + b"\x00"
    return gzip.compress(root)


class LevelDatProbeTest(unittest.TestCase):

    def test_reads_gzipped_level_dat(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "level.dat"
            path.write_bytes(fixture_level_dat())

            metadata = extract_metadata(read_level_dat(path))
            self.assertEqual(2586, metadata["data_version"])
            self.assertEqual("To Bee Fixture", metadata["level_name"])
            self.assertEqual((12, 70, -34), metadata["spawn"])
            self.assertEqual(2, metadata["game_type"])
            self.assertEqual(2, metadata["difficulty"])
            self.assertEqual(12345, metadata["time"])

            report = render_report(path, metadata)
            self.assertIn("data_version=2586", report)
            self.assertIn("level_name=To Bee Fixture", report)
            self.assertIn("spawn=12,70,-34", report)


if __name__ == "__main__":
    unittest.main()
