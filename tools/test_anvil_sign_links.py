#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from anvil_sign_links import (
    button_front_vector,
    link_action,
    parse_probe_report,
    render_report,
    sign_front_vector,
)


class AnvilSignLinksTest(unittest.TestCase):

    def test_panel_facing_reduces_nearby_buttons(self):
        fixture = """# fixture
BLOCK\tBUTTON\tminecraft:stone_button\t-4\t70\t0\tregion=r.-1.0.mca\tchunk=-1,0\tprops=face:wall,facing:north,powered:false
BLOCK\tBUTTON\tminecraft:stone_button\t4\t70\t0\tregion=r.0.0.mca\tchunk=0,0\tprops=face:wall,facing:south,powered:false
BLOCK\tSIGN_BLOCK\tminecraft:oak_wall_sign\t0\t64\t0\tregion=r.0.0.mca\tchunk=0,0\tprops=facing:north,waterlogged:false
SIGN\tminecraft:sign\t0\t64\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=>>> | Release fire | snake | >>>
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(fixture, encoding="utf-8")
            buttons, signs = parse_probe_report(path)
            self.assertEqual((0.0, -1.0), sign_front_vector(signs[0]))
            self.assertEqual((0.0, -1.0), button_front_vector(buttons[0]))

            action = link_action(signs[0], buttons, 9.0, 0.70)
            self.assertEqual(2, len(action.nearby_buttons))
            self.assertEqual(1, len(action.panel_buttons))
            self.assertEqual(-4, action.panel_buttons[0].x)

    def test_renders_panel_summary_without_trap_classification(self):
        fixture = """BLOCK\tBUTTON\tminecraft:stone_button\t-4\t70\t0\tregion=r.-1.0.mca\tchunk=-1,0\tprops=face:wall,facing:north
BLOCK\tSIGN_BLOCK\tminecraft:oak_wall_sign\t0\t64\t0\tregion=r.0.0.mca\tchunk=0,0\tprops=facing:north
SIGN\tminecraft:sign\t0\t64\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=>>> | Flood the | floor | >>>
SIGN\tminecraft:sign\t5\t64\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=To Bee or not | to Bee
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(fixture, encoding="utf-8")
            buttons, signs = parse_probe_report(path)
            report = render_report(path, buttons, signs, 9.0, 0.70)
            self.assertIn("directional_actions=1", report)
            self.assertIn("button_facing_known=1", report)
            self.assertIn("panel_unique_links=1", report)
            self.assertIn("unique_panel_buttons=1", report)
            self.assertIn("link=PANEL_UNIQUE", report)
            self.assertIn("target_arrow=RIGHT", report)
            self.assertIn("TITLE_SIGN", report)
            self.assertNotIn("trap_type=", report)


if __name__ == "__main__":
    unittest.main()
