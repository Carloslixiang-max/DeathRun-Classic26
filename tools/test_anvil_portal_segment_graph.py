#!/usr/bin/env python3

import unittest
from pathlib import Path

from anvil_portal_segment_graph import (
    SegmentGap,
    minimum_spanning_tree,
    render_report,
)
from anvil_sign_links import Sign


def sign(x: int, y: int, z: int) -> Sign:
    return Sign("minecraft:sign", x, y, z, "Warning!")


class AnvilPortalSegmentGraphTest(unittest.TestCase):

    def test_mst_path_is_reported_without_direction(self):
        gaps = [
            SegmentGap(1, 2, 10.0, sign(0, 0, 0), sign(10, 0, 0)),
            SegmentGap(2, 3, 11.0, sign(10, 0, 0), sign(21, 0, 0)),
            SegmentGap(3, 4, 12.0, sign(21, 0, 0), sign(33, 0, 0)),
            SegmentGap(1, 3, 20.0, sign(0, 0, 0), sign(21, 0, 0)),
            SegmentGap(2, 4, 21.0, sign(10, 0, 0), sign(33, 0, 0)),
            SegmentGap(1, 4, 30.0, sign(0, 0, 0), sign(33, 0, 0)),
        ]
        tree = minimum_spanning_tree(4, gaps)
        self.assertEqual(3, len(tree))
        self.assertEqual([(1, 2), (2, 3), (3, 4)], [(e.left, e.right) for e in tree])

        grouped = {
            1: [sign(0, 0, 0)],
            2: [sign(10, 0, 0)],
            3: [sign(21, 0, 0)],
            4: [sign(33, 0, 0)],
        }
        report, stats = render_report(
            Path("probe.txt"),
            4,
            grouped,
            gaps,
            tree,
        )
        self.assertEqual("true", stats["mst_is_path"])
        self.assertEqual("001,004", stats["mst_endpoints"])
        self.assertIn("MST_EDGE", report)
        self.assertIn("does NOT establish route direction", report)


if __name__ == "__main__":
    unittest.main()
