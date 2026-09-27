#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from anvil_cluster import Candidate
from anvil_portal_segment_evidence import assign_pairs, render_report
from anvil_sign_links import Sign
from anvil_topology import portal_components
from anvil_trap_evidence import ActionEvidence, PairCandidate


class AnvilPortalSegmentEvidenceTest(unittest.TestCase):

    def test_assigns_runner_warning_to_nearest_gate_without_route_number(self):
        portals = portal_components(
            [
                Candidate("PORTAL", "minecraft:nether_portal", 0, 64, 0, "fixture"),
                Candidate("PORTAL", "minecraft:nether_portal", 0, 65, 0, "fixture"),
                Candidate("PORTAL", "minecraft:nether_portal", 30, 64, 0, "fixture"),
                Candidate("PORTAL", "minecraft:nether_portal", 30, 65, 0, "fixture"),
            ]
        )
        warning = Sign("minecraft:sign", 3, 64, 0, "Warning! | Test")
        action_sign = Sign("minecraft:sign", 26, 64, 0, ">>> | Test action | >>>")
        action = ActionEvidence(action_sign, "Test action", frozenset({"test"}))
        pair = PairCandidate(warning, action, 23.0, ("test",))

        assignments = assign_pairs([pair], portals)
        self.assertEqual(1, assignments[0].warning_portal_id)
        self.assertEqual(2, assignments[0].action_portal_id)

        with tempfile.TemporaryDirectory() as tmp:
            report, stats = render_report(
                Path(tmp) / "probe.txt",
                portals,
                assignments,
            )
            self.assertEqual(1, stats["pairs"])
            self.assertEqual(2, stats["portal_components"])
            self.assertEqual(1, stats["portals_with_warning_pairs"])
            self.assertEqual(0, stats["same_nearest_portal_pairs"])
            self.assertEqual(1, stats["cross_nearest_portal_pairs"])
            self.assertIn("PORTAL_SEGMENT", report)
            self.assertIn("warning_portal=001", report)
            self.assertIn("action_portal=002", report)
            self.assertIn("does NOT establish checkpoint number", report)


if __name__ == "__main__":
    unittest.main()
