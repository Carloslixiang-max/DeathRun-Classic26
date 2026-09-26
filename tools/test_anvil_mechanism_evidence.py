#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from anvil_mechanism_evidence import (
    facing_consistent,
    parse_mechanisms,
    render_report,
)
from anvil_sign_links import parse_probe_report


class AnvilMechanismEvidenceTest(unittest.TestCase):

    def test_parses_facing_and_correlates_fire_arrow_pair(self):
        fixture = """BLOCK\tMECHANISM\tminecraft:dispenser\t0\t64\t0\tregion=r.0.0.mca\tchunk=0,0\tprops=facing:south,triggered:false
BLOCK\tMECHANISM\tminecraft:hopper\t2\t64\t3\tregion=r.0.0.mca\tchunk=0,0\tprops=enabled:true,facing:down
SIGN\tminecraft:sign\t0\t64\t5\tregion=r.0.0.mca\tchunk=0,0\ttext=Warning! | Deadly fire arrows!
SIGN\tminecraft:sign\t2\t64\t8\tregion=r.0.0.mca\tchunk=0,0\ttext=Fire Arrows | <<<
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(fixture, encoding="utf-8")
            mechanisms = parse_mechanisms(path)
            _buttons, signs = parse_probe_report(path)

            self.assertEqual(2, len(mechanisms))
            dispenser = mechanisms[0]
            self.assertEqual("minecraft:dispenser", dispenser.name)
            self.assertEqual("south", dispenser.property("facing"))

            warning = next(sign for sign in signs if sign.text.startswith("Warning"))
            self.assertTrue(facing_consistent(dispenser, warning))

            report, stats = render_report(
                path,
                signs,
                mechanisms,
                pair_distance=30.0,
                mechanism_radius=14.0,
            )
            self.assertEqual(1, stats["pairs"])
            self.assertEqual(1, stats["pairs_with_dispensers"])
            self.assertEqual(1, stats["pairs_with_consistent"])
            self.assertEqual(1, stats["fire_arrow_pairs"])
            self.assertEqual(1, stats["fire_arrow_consistent"])
            self.assertIn("action=Fire Arrows", report)
            self.assertIn("faces_warning=true", report)
            self.assertNotIn("trap_type=", report)

    def test_wrong_facing_is_not_consistent(self):
        fixture = """BLOCK\tMECHANISM\tminecraft:dispenser\t0\t64\t0\tregion=r.0.0.mca\tchunk=0,0\tprops=facing:north,triggered:false
SIGN\tminecraft:sign\t0\t64\t5\tregion=r.0.0.mca\tchunk=0,0\ttext=Warning! | Deadly fire arrows!
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(fixture, encoding="utf-8")
            mechanisms = parse_mechanisms(path)
            _buttons, signs = parse_probe_report(path)
            self.assertFalse(facing_consistent(mechanisms[0], signs[0]))


if __name__ == "__main__":
    unittest.main()
