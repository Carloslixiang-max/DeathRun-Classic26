#!/usr/bin/env python3

import unittest
from pathlib import Path

from anvil_sign_links import Sign
from anvil_portal_segment_graph import SegmentGap
from anvil_warning_facing_evidence import measure_tree, render_report


class AnvilWarningFacingEvidenceTest(unittest.TestCase):

    def test_reports_front_dot_without_assigning_route_direction(self):
        left = Sign(
            "minecraft:sign", 0, 64, 0, "Warning!",
            block_name="minecraft:oak_wall_sign",
            properties=(("facing", "east"),),
        )
        right = Sign(
            "minecraft:sign", 10, 64, 0, "Warning!",
            block_name="minecraft:oak_wall_sign",
            properties=(("facing", "west"),),
        )
        edge = SegmentGap(1, 2, 10.0, left, right)
        measurements = measure_tree([edge])

        self.assertAlmostEqual(1.0, measurements[0].left_dot_to_right)
        self.assertAlmostEqual(1.0, measurements[0].right_dot_to_left)

        report, stats = render_report(Path("probe.txt"), measurements)
        self.assertEqual(2, stats["known_front_sides"])
        self.assertEqual(2, stats["toward_neighbor"])
        self.assertIn("left_dot_to_right=1.000", report)
        self.assertIn("does NOT by itself establish Runner travel direction", report)


if __name__ == "__main__":
    unittest.main()
