#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from anvil_sign_links import (
    arrow_direction,
    front_vector,
    link_action,
    parse_probe_report,
    render_report,
    sign_kind,
)


class AnvilSignLinksTest(unittest.TestCase):

    def test_arrow_projection_reduces_two_sided_buttons_to_one(self):
        fixture = """# fixture
BLOCK\tBUTTON\tminecraft:stone_button\t-4\t70\t0\tregion=r.-1.0.mca\tchunk=-1,0
BLOCK\tBUTTON\tminecraft:stone_button\t4\t70\t0\tregion=r.0.0.mca\tchunk=0,0
BLOCK\tSIGN_BLOCK\tminecraft:oak_wall_sign\t0\t64\t0\tregion=r.0.0.mca\tchunk=0,0\tprops=facing:north,waterlogged:false
SIGN\tminecraft:sign\t0\t64\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=>>> | Release fire | snake | >>>
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(fixture, encoding="utf-8")
            buttons, signs = parse_probe_report(path)
            self.assertEqual(2, len(buttons))
            self.assertEqual(1, len(signs))
            sign = signs[0]
            self.assertEqual("RIGHT", arrow_direction(sign))
            self.assertEqual((0.0, -1.0), front_vector(sign))

            action = link_action(sign, buttons, 9.0)
            self.assertEqual(2, len(action.nearby_buttons))
            self.assertEqual(1, len(action.arrow_buttons))
            # facing north -> viewer looks south -> screen-right is west
            self.assertEqual(-4, action.arrow_buttons[0].x)

    def test_renders_orientation_summary_without_trap_classification(self):
        fixture = """BLOCK\tBUTTON\tminecraft:stone_button\t-4\t70\t0\tregion=r.-1.0.mca\tchunk=-1,0
BLOCK\tSIGN_BLOCK\tminecraft:oak_wall_sign\t0\t64\t0\tregion=r.0.0.mca\tchunk=0,0\tprops=facing:north
SIGN\tminecraft:sign\t0\t64\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=>>> | Flood the | floor | >>>
SIGN\tminecraft:sign\t5\t64\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=To Bee or not | to Bee
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(fixture, encoding="utf-8")
            buttons, signs = parse_probe_report(path)
            report = render_report(path, buttons, signs, 9.0)
            self.assertIn("directional_actions=1", report)
            self.assertIn("facing_known_actions=1", report)
            self.assertIn("arrow_unique_links=1", report)
            self.assertIn("unique_arrow_buttons=1", report)
            self.assertIn("link=ARROW_UNIQUE", report)
            self.assertIn("TITLE_SIGN", report)
            self.assertNotIn("trap_type=", report)


if __name__ == "__main__":
    unittest.main()
