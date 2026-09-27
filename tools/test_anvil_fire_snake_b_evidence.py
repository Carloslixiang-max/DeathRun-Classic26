#!/usr/bin/env python3

import unittest

from anvil_fire_snake_b_evidence import source_dispensers, walkable_slice
from anvil_mechanism_evidence import Mechanism


class FireSnakeBEvidenceTest(unittest.TestCase):
    def test_source_requires_exact_row_and_west_facing(self):
        mechanisms = [
            Mechanism("minecraft:dispenser", 84, 24, 47, (("facing", "west"),)),
            Mechanism("minecraft:dispenser", 84, 24, 48, (("facing", "east"),)),
            Mechanism("minecraft:dispenser", 83, 24, 47, (("facing", "west"),)),
            Mechanism("minecraft:hopper", 84, 24, 49, (("facing", "west"),)),
        ]
        result = source_dispensers(mechanisms)
        self.assertEqual(1, len(result))
        self.assertEqual((84, 24, 47), (result[0].x, result[0].y, result[0].z))

    def test_walkable_slice_keeps_only_supported_air_columns(self):
        blocks = {
            (70, 24, 50): "minecraft:grass_block",
            (70, 25, 50): "minecraft:air",
            (70, 26, 50): "minecraft:air",
            (70, 24, 51): "minecraft:red_tulip",
            (70, 25, 51): "minecraft:air",
            (70, 26, 51): "minecraft:air",
            (70, 24, 52): "minecraft:stone",
            (70, 25, 52): "minecraft:water",
            (70, 26, 52): "minecraft:air",
        }
        result = walkable_slice(blocks, 70, 50, 52, 25, 25)
        self.assertEqual(1, len(result))
        self.assertEqual("minecraft:grass_block", result[0].material)


if __name__ == "__main__":
    unittest.main()
