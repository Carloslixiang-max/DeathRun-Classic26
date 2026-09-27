#!/usr/bin/env python3

import unittest
from pathlib import Path

from anvil_route_direction_hypothesis import (
    ordered_path,
    render_report,
    score_direction,
)
from anvil_sign_links import Sign
from anvil_portal_segment_graph import SegmentGap
from anvil_warning_facing_evidence import measure_tree


def wall_sign(x, z, facing):
    return Sign(
        "minecraft:sign",
        x,
        64,
        z,
        "Warning!",
        block_name="minecraft:oak_wall_sign",
        properties=(("facing", facing),),
    )


class AnvilRouteDirectionHypothesisTest(unittest.TestCase):

    def test_scores_both_path_directions_without_claiming_confirmation(self):
        # Geometry path 1--2--3. Signs face west, so under the incoming-facing
        # assumption the preferred travel direction is 1->2->3.
        e12 = SegmentGap(
            1, 2, 10.0,
            wall_sign(0, 0, "west"),
            wall_sign(10, 0, "west"),
        )
        e23 = SegmentGap(
            2, 3, 10.0,
            wall_sign(10, 0, "west"),
            wall_sign(20, 0, "west"),
        )
        tree = [e12, e23]
        path = ordered_path(3, tree)
        self.assertEqual((1, 2, 3), path)

        measurements = measure_tree(tree)
        forward = score_direction(path, measurements, 0.25)
        reverse = score_direction(tuple(reversed(path)), measurements, 0.25)
        self.assertGreater(forward.agree, reverse.agree)
        self.assertGreater(forward.agree - forward.disagree, 0)

        report, stats = render_report(
            Path("probe.txt"),
            [forward, reverse],
            0.25,
        )
        self.assertEqual("001->002->003", stats["best_path"])
        self.assertIn("candidate interpretation", report)
        self.assertIn("NOT confirmed start/finish", report)


if __name__ == "__main__":
    unittest.main()
