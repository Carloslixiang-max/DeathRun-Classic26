#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from anvil_cluster import (
    Candidate,
    build_clusters,
    local_groups,
    parse_probe_report,
    render_report,
    spatial_groups,
)


class AnvilClusterTest(unittest.TestCase):

    def test_spatial_groups_use_transitive_links(self):
        points = [
            Candidate("BUTTON", "minecraft:stone_button", 0, 64, 0, "fixture"),
            Candidate("BUTTON", "minecraft:stone_button", 3, 64, 0, "fixture"),
            Candidate("PRESSURE_PLATE", "minecraft:stone_pressure_plate", 6, 64, 0, "fixture"),
            Candidate("BUTTON", "minecraft:stone_button", 30, 64, 0, "fixture"),
        ]
        groups = spatial_groups(points, horizontal_radius=3.1, vertical_radius=1.0)
        self.assertEqual([3, 1], [len(group) for group in groups])

    def test_local_groups_do_not_chain_across_a_corridor(self):
        points = [
            Candidate("BUTTON", "minecraft:stone_button", 0, 64, 0, "fixture"),
            Candidate("BUTTON", "minecraft:stone_button", 3, 64, 0, "fixture"),
            Candidate("BUTTON", "minecraft:stone_button", 6, 64, 0, "fixture"),
            Candidate("BUTTON", "minecraft:stone_button", 9, 64, 0, "fixture"),
        ]
        connected = spatial_groups(points, horizontal_radius=3.1, vertical_radius=1.0)
        local = local_groups(points, horizontal_radius=3.1, vertical_radius=1.0)

        self.assertEqual([4], [len(group) for group in connected])
        self.assertEqual([3, 1], [len(group) for group in local])

    def test_parse_cluster_and_priority(self):
        report = """# fixture
summary regions=1 chunks=1 failed_chunks=0 external_chunks=0 candidate_blocks=5 command_entities=1

BLOCK\tBUTTON\tminecraft:stone_button\t0\t64\t0\tregion=r.0.0.mca\tchunk=0,0
BLOCK\tPRESSURE_PLATE\tminecraft:stone_pressure_plate\t2\t64\t0\tregion=r.0.0.mca\tchunk=0,0
BLOCK\tPORTAL\tminecraft:nether_portal\t4\t64\t0\tregion=r.0.0.mca\tchunk=0,0
BLOCK\tBUTTON\tminecraft:stone_button\t30\t70\t30\tregion=r.0.0.mca\tchunk=1,1
COMMAND\tminecraft:command_block\t31\t70\t30\tregion=r.0.0.mca\tchunk=1,1\tcmd=say fixture
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(report, encoding="utf-8")
            points, portals = parse_probe_report(path)
            clusters = build_clusters(points, portals, 4.0, 3.0, mode="local")

            self.assertEqual(4, len(points))
            self.assertEqual(1, len(portals))
            self.assertEqual(2, len(clusters))
            self.assertEqual("HIGH", clusters[0].priority)
            self.assertEqual("HIGH", clusters[1].priority)
            self.assertTrue(any("mixed-inputs" in c.reasons for c in clusters))
            self.assertTrue(any("command-evidence" in c.reasons for c in clusters))

            rendered = render_report(
                path, points, portals, clusters, 4.0, 3.0, 2, mode="local"
            )
            self.assertIn(
                "interactive_points=4 portals=1 mode=local clusters=2",
                rendered,
            )
            self.assertIn("BUTTON=2", rendered)
            self.assertIn("COMMAND_ENTITY=1", rendered)
            self.assertIn("PRESSURE_PLATE=1", rendered)
            self.assertIn("nearest_portal=", rendered)
            self.assertNotIn("trap_type=", rendered)


if __name__ == "__main__":
    unittest.main()
