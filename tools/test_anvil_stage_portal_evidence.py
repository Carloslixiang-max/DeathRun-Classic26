#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from anvil_cluster import Candidate
from anvil_stage_evidence import StageMarker
from anvil_stage_portal_evidence import build_links, render_report
from anvil_topology import portal_components


class AnvilStagePortalEvidenceTest(unittest.TestCase):

    def test_stage_markers_link_to_distinct_portal_gates_without_assigning_order(self):
        portals = portal_components(
            [
                Candidate("PORTAL", "minecraft:nether_portal", 0, 64, 0, "fixture"),
                Candidate("PORTAL", "minecraft:nether_portal", 0, 65, 0, "fixture"),
                Candidate("PORTAL", "minecraft:nether_portal", 30, 64, 0, "fixture"),
                Candidate("PORTAL", "minecraft:nether_portal", 30, 65, 0, "fixture"),
            ]
        )
        markers = [
            StageMarker("minecraft:armor_stand", "Previous Stage", 2.0, 64.5, 0.0),
            StageMarker("minecraft:armor_stand", "Next Stage", 27.0, 64.5, 0.0),
        ]
        links = build_links(markers, portals, portal_radius=10.0)

        self.assertEqual(2, len(links))
        self.assertEqual(1, links[0].nearest_portal_id)
        self.assertEqual(2, links[1].nearest_portal_id)
        self.assertLess(links[0].nearest_distance, 3.0)
        self.assertLess(links[1].nearest_distance, 4.0)

        with tempfile.TemporaryDirectory() as tmp:
            report, stats = render_report(
                Path(tmp) / "probe.txt",
                Path(tmp) / "entities.txt",
                links,
                portal_count=2,
                portal_radius=10.0,
                very_close_radius=4.0,
            )
            self.assertEqual(2, stats["stage_markers"])
            self.assertEqual(2, stats["markers_with_portal_within_radius"])
            self.assertEqual(2, stats["markers_with_very_close_portal"])
            self.assertEqual(2, stats["distinct_nearest_portals"])
            self.assertIn("direction=PREVIOUS", report)
            self.assertIn("direction=NEXT", report)
            self.assertIn("nearest_portal=001", report)
            self.assertIn("nearest_portal=002", report)
            self.assertIn("does not assign checkpoint numbering or route order", report)


if __name__ == "__main__":
    unittest.main()
