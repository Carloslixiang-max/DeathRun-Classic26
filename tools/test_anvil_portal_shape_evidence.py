#!/usr/bin/env python3

import unittest

from anvil_cluster import Candidate
from anvil_portal_shape_evidence import analyze_component, render_report
from anvil_topology import portal_components
from pathlib import Path


class AnvilPortalShapeEvidenceTest(unittest.TestCase):

    def test_dense_vertical_portal_wall_is_planar(self):
        points = [
            Candidate("PORTAL", "minecraft:nether_portal", 5, y, z, "fixture")
            for y in range(64, 67)
            for z in range(10, 14)
            if not (y == 65 and z == 11)
        ]
        component = portal_components(points)[0]
        shape = analyze_component(component)
        self.assertEqual("x", shape.plane_axis)
        self.assertEqual(5, shape.plane_value)
        self.assertEqual(4, shape.width)
        self.assertEqual(3, shape.height)
        self.assertEqual(12, shape.rectangle_area)
        self.assertEqual(11, shape.filled)
        self.assertEqual(1, shape.holes)
        self.assertAlmostEqual(11 / 12, shape.fill_ratio)

        report, stats = render_report(
            Path("probe.txt"),
            [shape],
            dense_threshold=0.80,
        )
        self.assertEqual(1, stats["planar_components"])
        self.assertEqual(1, stats["dense_planar_components"])
        self.assertIn("plane_axis=x", report)
        self.assertIn("fill_ratio=0.917", report)
        self.assertIn("does NOT establish checkpoint numbering", report)

    def test_nonplanar_component_is_not_dense_gate(self):
        points = [
            Candidate("PORTAL", "minecraft:nether_portal", 0, 64, 0, "fixture"),
            Candidate("PORTAL", "minecraft:nether_portal", 1, 64, 0, "fixture"),
            Candidate("PORTAL", "minecraft:nether_portal", 1, 65, 0, "fixture"),
            Candidate("PORTAL", "minecraft:nether_portal", 1, 65, 1, "fixture"),
        ]
        component = portal_components(points)[0]
        shape = analyze_component(component)
        self.assertIsNone(shape.plane_axis)


if __name__ == "__main__":
    unittest.main()
