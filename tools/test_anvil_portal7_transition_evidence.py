#!/usr/bin/env python3

import unittest
from pathlib import Path

from anvil_cluster import Candidate
from anvil_portal7_transition_evidence import (
    Portal7Pair,
    render_report,
)
from anvil_sign_links import Sign
from anvil_stage_evidence import StageMarker
from anvil_topology import portal_components


class AnvilPortal7TransitionEvidenceTest(unittest.TestCase):

    def test_resolves_high_negative_low_positive_split(self):
        portal = portal_components([
            Candidate("PORTAL", "minecraft:nether_portal", x, y, 36, "fixture")
            for x in range(32, 37)
            for y in range(45, 48)
        ])[0]

        pairs = [
            Portal7Pair(
                Sign("minecraft:sign", 34, 45, 20, "Warning!"),
                Sign("minecraft:sign", 30, 44, 20, ">>> | A"),
                "A", -1, -1, 0.0, -1.0,
            ),
            Portal7Pair(
                Sign("minecraft:sign", 35, 45, 10, "Warning!"),
                Sign("minecraft:sign", 31, 44, 10, ">>> | B"),
                "B", -1, -1, 0.0, -1.0,
            ),
            Portal7Pair(
                Sign("minecraft:sign", 50, 25, 53, "Warning!"),
                Sign("minecraft:sign", 52, 25, 56, ">>> | C"),
                "C", 1, 1, -20.0, -20.0,
            ),
        ]
        stages = [
            (StageMarker("minecraft:armor_stand", "Next Stage", 24.5, 45.25, 13.5), -1, 0.25)
        ]
        report, stats = render_report(
            Path("probe.txt"),
            Path("entities.txt"),
            portal,
            pairs,
            stages,
            high_tolerance=3.0,
        )
        self.assertEqual("negative", stats["candidate_outgoing_side"])
        self.assertEqual(2, stats["negative_high_pairs"])
        self.assertEqual(1, stats["positive_low_pairs"])
        self.assertIn("NOT recovered original checkpoint config", report)


if __name__ == "__main__":
    unittest.main()
