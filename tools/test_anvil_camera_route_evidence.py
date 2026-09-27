#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from anvil_camera_route_evidence import (
    build_targets,
    render_report,
    view_vector,
)
from anvil_cluster import Candidate
from anvil_sign_links import Sign
from anvil_topology import portal_components


class AnvilCameraRouteEvidenceTest(unittest.TestCase):

    def test_minecraft_yaw_pitch_and_target_angles(self):
        direction = view_vector(0.0, 0.0)
        self.assertAlmostEqual(0.0, direction[0])
        self.assertAlmostEqual(0.0, direction[1])
        self.assertAlmostEqual(1.0, direction[2])

        portals = portal_components(
            [
                Candidate("PORTAL", "minecraft:nether_portal", 0, 0, 10, "fixture"),
                Candidate("PORTAL", "minecraft:nether_portal", 0, 1, 10, "fixture"),
            ]
        )
        title = Sign(
            "minecraft:sign",
            10,
            0,
            0,
            "To Bee or not | to Bee",
        )
        targets = build_targets(
            (0.0, 0.0, 0.0),
            (0.0, 0.0),
            portals,
            [title],
        )

        portal = next(item for item in targets if item.kind == "PORTAL")
        title_target = next(item for item in targets if item.kind == "TITLE")
        self.assertLess(portal.angle_degrees, 3.0)
        self.assertAlmostEqual(90.0, title_target.angle_degrees)

        with tempfile.TemporaryDirectory() as tmp:
            report, stats = render_report(
                Path(tmp) / "level.dat",
                Path(tmp) / "probe.txt",
                (0.0, 0.0, 0.0),
                (0.0, 0.0),
                targets,
            )
            self.assertEqual("001", stats["best_portal_id"])
            self.assertLess(float(stats["best_portal_angle"]), 3.0)
            self.assertIn("CAMERA_TARGET", report)
            self.assertIn("author/save-camera anchor only", report)
            self.assertIn("does NOT define spawn", report)


if __name__ == "__main__":
    unittest.main()
