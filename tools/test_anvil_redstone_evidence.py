#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from anvil_redstone_evidence import (
    parse_blocks,
    proximity_components,
    render_report,
)


class AnvilRedstoneEvidenceTest(unittest.TestCase):

    def test_builds_structural_component_and_reports_anchors(self):
        fixture = """BLOCK\tBUTTON\tminecraft:oak_button\t0\t64\t-1\tregion=r.0.0.mca\tchunk=0,0\tprops=face:wall,facing:south
BLOCK\tREDSTONE\tminecraft:redstone_wire\t0\t64\t0\tregion=r.0.0.mca\tchunk=0,0\tprops=power:0
BLOCK\tREDSTONE\tminecraft:repeater\t1\t64\t0\tregion=r.0.0.mca\tchunk=0,0\tprops=facing:east,powered:false
BLOCK\tREDSTONE\tminecraft:redstone_wire\t2\t65\t0\tregion=r.0.0.mca\tchunk=0,0\tprops=power:0
BLOCK\tMECHANISM\tminecraft:dispenser\t3\t65\t0\tregion=r.0.0.mca\tchunk=0,0\tprops=facing:east
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(fixture, encoding="utf-8")
            redstone, buttons, mechanisms = parse_blocks(path)
            self.assertEqual(3, len(redstone))
            self.assertEqual(1, len(proximity_components(redstone)))

            report, stats = render_report(
                path, redstone, buttons, mechanisms,
                anchor_radius=1.5, max_components=20
            )
            self.assertEqual(1, stats["components"])
            self.assertEqual(3, stats["largest_component"])
            self.assertEqual(1, stats["components_near_buttons"])
            self.assertEqual(1, stats["components_near_mechanisms"])
            self.assertEqual(1, stats["bridge_components"])
            self.assertIn("minecraft:repeater:1", report)
            self.assertIn("REDSTONE_BUTTON", report)
            self.assertIn("REDSTONE_MECHANISM", report)
            self.assertIn("not simulated redstone circuits", report)

    def test_empty_archive_is_valid_negative_evidence(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(
                "BLOCK\tBUTTON\tminecraft:stone_button\t0\t64\t0\tregion=r.0.0.mca\tchunk=0,0\n",
                encoding="utf-8",
            )
            redstone, buttons, mechanisms = parse_blocks(path)
            report, stats = render_report(
                path, redstone, buttons, mechanisms,
                anchor_radius=2.0, max_components=10
            )
            self.assertEqual(0, stats["redstone_blocks"])
            self.assertIn("redstone_blocks=0", report)
            self.assertIn("REDSTONE_TYPES\tnone", report)


if __name__ == "__main__":
    unittest.main()
