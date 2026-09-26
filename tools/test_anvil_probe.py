#!/usr/bin/env python3

import io
import struct
import unittest

from anvil_probe import (
    NbtReader,
    decode_palette_index,
    scan_legacy_section,
    scan_modern_section,
    sign_entities_from_chunk,
)


def pack_padded(indices, palette_size):
    bits = max(4, (palette_size - 1).bit_length())
    per_long = 64 // bits
    words = [0] * ((4096 + per_long - 1) // per_long)
    mask = (1 << bits) - 1
    for i, value in enumerate(indices):
        word = i // per_long
        shift = (i % per_long) * bits
        words[word] |= (value & mask) << shift
    return words


class AnvilProbeTest(unittest.TestCase):

    def test_decode_padded_palette_index(self):
        indices = [0] * 4096
        indices[17] = 3
        data = pack_padded(indices, 5)
        self.assertEqual(3, decode_palette_index(data, 17, 5))
        self.assertEqual(0, decode_palette_index(data, 18, 5))

    def test_scan_modern_button(self):
        indices = [0] * 4096
        indices[0] = 1
        section = {
            "Y": 4,
            "Palette": [
                {"Name": "minecraft:air"},
                {"Name": "minecraft:stone_button"},
            ],
            "BlockStates": pack_padded(indices, 2),
        }

        candidates = scan_modern_section(section, 2, -3, "r.0.-1.mca")
        self.assertEqual(1, len(candidates))
        candidate = candidates[0]
        self.assertEqual("BUTTON", candidate.category)
        self.assertEqual((32, 64, -48), (candidate.x, candidate.y, candidate.z))

    def test_scan_legacy_command_block(self):
        blocks = bytearray(4096)
        blocks[0] = 137
        section = {
            "Y": 5,
            "Blocks": bytes(blocks),
            "Data": bytes(2048),
        }

        candidates = scan_legacy_section(section, -2, 3, "r.-1.0.mca")
        self.assertEqual(1, len(candidates))
        candidate = candidates[0]
        self.assertEqual("COMMAND_BLOCK", candidate.category)
        self.assertEqual((-32, 80, 48), (candidate.x, candidate.y, candidate.z))


    def test_extracts_legacy_sign_text_components(self):
        level = {
            "TileEntities": [
                {
                    "id": "minecraft:sign",
                    "x": 12,
                    "y": 65,
                    "z": -4,
                    "Text1": '{"text":"Trap"}',
                    "Text2": '{"text":"Fire","extra":[{"text":" Arrows"}]}',
                    "Text3": '{"text":""}',
                    "Text4": "Plain line",
                }
            ]
        }

        signs = sign_entities_from_chunk(level, "r.0.-1.mca", 0, -1)
        self.assertEqual(1, len(signs))
        sign = signs[0]
        self.assertEqual((12, 65, -4), (sign.x, sign.y, sign.z))
        self.assertEqual("Trap | Fire Arrows | Plain line", sign.text)

    def test_extracts_modern_front_sign_text(self):
        level = {
            "block_entities": [
                {
                    "id": "minecraft:oak_sign",
                    "x": 3,
                    "y": 70,
                    "z": 8,
                    "front_text": {
                        "messages": [
                            '{"text":"Checkpoint"}',
                            '{"text":"Nine"}',
                        ]
                    },
                }
            ]
        }
        signs = sign_entities_from_chunk(level, "r.0.0.mca", 0, 0)
        self.assertEqual("Checkpoint | Nine", signs[0].text)

    def test_minimal_nbt_root(self):
        # root compound named "" with one TAG_Int DataVersion=1234
        raw = (
            b"\x0a\x00\x00"
            b"\x03\x00\x0bDataVersion"
            + struct.pack(">i", 1234)
            + b"\x00"
        )
        parsed = NbtReader(raw).root()
        self.assertEqual({"DataVersion": 1234}, parsed)


if __name__ == "__main__":
    unittest.main()
