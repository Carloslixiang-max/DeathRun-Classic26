#!/usr/bin/env python3

import unittest

from anvil_sign_links import Sign
from anvil_vertical_hint_evidence import hint_drop_candidates


class AnvilVerticalHintEvidenceTest(unittest.TestCase):

    def test_finds_clear_drop_to_solid_floor(self):
        hint = Sign("minecraft:sign", 0, 10, 0, "Hint: | Look down!")
        blocks = {}
        # Air from y=10 down through y=5; solid support at y=4 => landing y=5, drop 5.
        blocks[(1, 4, 0)] = "minecraft:stone"
        drops = hint_drop_candidates(
            blocks,
            hint,
            radius=2,
            min_drop=3,
            max_drop=8,
            min_y=0,
        )
        match = next(item for item in drops if (item.x, item.z) == (1, 0))
        self.assertEqual(5, match.landing_y)
        self.assertEqual(5, match.drop)
        self.assertEqual("minecraft:stone", match.support)

    def test_rejects_water_floor(self):
        hint = Sign("minecraft:sign", 0, 10, 0, "Hint: | Look down!")
        blocks = {(1, 4, 0): "minecraft:water"}
        drops = hint_drop_candidates(
            blocks,
            hint,
            radius=2,
            min_drop=3,
            max_drop=8,
            min_y=0,
        )
        self.assertFalse(any((item.x, item.z) == (1, 0) for item in drops))


if __name__ == "__main__":
    unittest.main()
