#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from anvil_cluster import Candidate
from anvil_route_portal_evidence import build_links, render_report
from anvil_sign_links import parse_probe_report as parse_sign_probe
from anvil_topology import portal_components


class AnvilRoutePortalEvidenceTest(unittest.TestCase):

    def test_correlates_warning_without_assigning_checkpoint_role(self):
        fixture = """BLOCK\tPORTAL\tminecraft:nether_portal\t0\t64\t0\tregion=r.0.0.mca\tchunk=0,0
BLOCK\tPORTAL\tminecraft:nether_portal\t0\t65\t0\tregion=r.0.0.mca\tchunk=0,0
BLOCK\tPORTAL\tminecraft:nether_portal\t40\t64\t0\tregion=r.0.0.mca\tchunk=2,0
SIGN\tminecraft:sign\t3\t64\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=Warning! | Test trap
SIGN\tminecraft:sign\t42\t64\t0\tregion=r.0.0.mca\tchunk=2,0\ttext=Hint: | Look down!
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(fixture, encoding="utf-8")
            _buttons, signs = parse_sign_probe(path)
            portals = [
                Candidate("PORTAL", "minecraft:nether_portal", 0, 64, 0, "fixture"),
                Candidate("PORTAL", "minecraft:nether_portal", 0, 65, 0, "fixture"),
                Candidate("PORTAL", "minecraft:nether_portal", 40, 64, 0, "fixture"),
            ]
            components = portal_components(portals)
            links = build_links(
                components,
                [sign for sign in signs if sign.text.startswith("Warning")],
                warning_radius=10.0,
                hints=[sign for sign in signs if sign.text.startswith("Hint")],
                hint_radius=5.0,
            )
            self.assertEqual(2, len(links))
            self.assertEqual(1, sum(bool(link.nearby_warnings) for link in links))
            self.assertEqual(1, sum(bool(link.nearby_hints) for link in links))

            report, stats = render_report(
                path,
                signs,
                components,
                warning_radius=10.0,
                hint_radius=5.0,
            )
            self.assertEqual(1, stats["portals_with_warning_nearby"])
            self.assertEqual(1, stats["portals_without_warning_nearby"])
            self.assertEqual(1, stats["hints"])
            self.assertEqual(1, stats["portals_with_hint_nearby"])
            self.assertIn("ROUTE_PORTAL", report)
            self.assertIn("ROUTE_PORTAL_HINT", report)
            self.assertIn("ROUTE_PORTAL_WARNING", report)
            self.assertNotIn("checkpoint=true", report)
            self.assertIn("NOT automatically a checkpoint", report)


if __name__ == "__main__":
    unittest.main()
