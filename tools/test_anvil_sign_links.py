#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from anvil_sign_links import (
    link_action,
    parse_probe_report,
    render_report,
    sign_kind,
)


class AnvilSignLinksTest(unittest.TestCase):

    def test_parses_and_links_directional_action(self):
        fixture = """# fixture
BLOCK\tBUTTON\tminecraft:stone_button\t10\t64\t10\tregion=r.0.0.mca\tchunk=0,0
BLOCK\tBUTTON\tminecraft:oak_button\t30\t64\t30\tregion=r.0.0.mca\tchunk=1,1
SIGN\tminecraft:sign\t11\t64\t10\tregion=r.0.0.mca\tchunk=0,0\ttext=>>> | Release fire | snake | >>>
SIGN\tminecraft:sign\t20\t70\t20\tregion=r.0.0.mca\tchunk=1,1\ttext=Warning! | Floor may fall
SIGN\tminecraft:sign\t0\t16\t-40\tregion=r.0.-1.mca\tchunk=0,-3\ttext=8 | 2
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(fixture, encoding="utf-8")
            buttons, signs = parse_probe_report(path)

            self.assertEqual(2, len(buttons))
            self.assertEqual(3, len(signs))
            self.assertEqual("DIRECTIONAL_ACTION", sign_kind(signs[1]))
            action = link_action(signs[1], buttons, 6.0)
            self.assertEqual((10, 64, 10), (
                action.nearest_button.x,
                action.nearest_button.y,
                action.nearest_button.z,
            ))
            self.assertAlmostEqual(1.0, action.nearest_distance)
            self.assertEqual(1, len(action.nearby_buttons))

    def test_renders_structural_summary_without_trap_classification(self):
        fixture = """BLOCK\tBUTTON\tminecraft:stone_button\t0\t64\t0\tregion=r.0.0.mca\tchunk=0,0
SIGN\tminecraft:sign\t1\t64\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=<<< | Flood the | floor | <<<
SIGN\tminecraft:sign\t5\t64\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=To Bee or not | to Bee
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(fixture, encoding="utf-8")
            buttons, signs = parse_probe_report(path)
            report = render_report(path, buttons, signs, 6.0)
            self.assertIn("directional_actions=1", report)
            self.assertIn("directional_with_button_within_radius=1", report)
            self.assertIn("ACTION_SIGN", report)
            self.assertIn("TITLE_SIGN", report)
            self.assertNotIn("trap_type=", report)


if __name__ == "__main__":
    unittest.main()
