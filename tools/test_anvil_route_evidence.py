#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from anvil_route_evidence import (
    numeric_components,
    numeric_markers,
    redstone_block_positions,
    render_report,
    route_keywords,
)
from anvil_sign_links import parse_probe_report


class AnvilRouteEvidenceTest(unittest.TestCase):

    def test_numeric_signs_form_physical_panels(self):
        fixture = """SIGN\tminecraft:sign\t0\t20\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=6 | 3
SIGN\tminecraft:sign\t1\t20\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=6 | 3
SIGN\tminecraft:sign\t1\t20\t1\tregion=r.0.0.mca\tchunk=0,0\ttext=8 | 2
SIGN\tminecraft:sign\t20\t20\t20\tregion=r.0.0.mca\tchunk=1,1\ttext=16 | 2
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(fixture, encoding="utf-8")
            _buttons, signs = parse_probe_report(path)
            markers = numeric_markers(signs)
            groups = numeric_components(markers)

            self.assertEqual(4, len(markers))
            self.assertEqual([3, 1], [len(group.members) for group in groups])
            self.assertEqual(2, len(groups[0].values))

    def test_numeric_panel_correlates_redstone_blocks_directly_above(self):
        fixture = """BLOCK\tREDSTONE\tminecraft:redstone_block\t0\t21\t0\tregion=r.0.0.mca\tchunk=0,0
BLOCK\tREDSTONE\tminecraft:redstone_block\t1\t21\t0\tregion=r.0.0.mca\tchunk=0,0
SIGN\tminecraft:sign\t0\t20\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=6 | 3
SIGN\tminecraft:sign\t1\t20\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=6 | 3
SIGN\tminecraft:sign\t2\t20\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=8 | 2
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(fixture, encoding="utf-8")
            _buttons, signs = parse_probe_report(path)
            redstone = redstone_block_positions(path)
            self.assertEqual(2, len(redstone))

            report = render_report(path, signs, redstone)
            self.assertIn("numeric_signs_with_redstone_block_above=2", report)
            self.assertIn("numeric_pairs_with_redstone_support=1", report)
            self.assertIn(
                "NUMERIC_REDSTONE_SUPPORT\tvalue=6|3\tcount=2",
                report,
            )
            self.assertIn("value=8|2\tcount=1\tredstone_block_above=0", report)

    def test_other_nonempty_signs_are_preserved_for_manual_review(self):
        fixture = """SIGN\tminecraft:sign\t0\t64\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=Welcome bees!
SIGN\tminecraft:sign\t1\t64\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=
SIGN\tminecraft:sign\t2\t64\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=Warning! | Trap ahead
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(fixture, encoding="utf-8")
            _buttons, signs = parse_probe_report(path)

            report = render_report(path, signs)
            self.assertIn("other_nonempty_signs=1", report)
            self.assertIn("OTHER_SIGN\tpos=0,64,0\ttext=Welcome bees!", report)
            self.assertNotIn("OTHER_SIGN\tpos=1,64,0", report)
            self.assertNotIn("OTHER_SIGN\tpos=2,64,0", report)

    def test_route_keyword_matching_uses_whole_tokens(self):
        fixture = """SIGN\tminecraft:sign\t0\t64\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=LE END!
SIGN\tminecraft:sign\t1\t64\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=Remove Sea Lanterns
SIGN\tminecraft:sign\t2\t64\t0\tregion=r.0.0.mca\tchunk=0,0\ttext=Checkpoint 4
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(fixture, encoding="utf-8")
            _buttons, signs = parse_probe_report(path)

            self.assertEqual(("end",), route_keywords(signs[0]))
            self.assertEqual((), route_keywords(signs[1]))
            self.assertEqual(("checkpoint",), route_keywords(signs[2]))

            report = render_report(path, signs)
            self.assertIn("route_keyword_signs=2", report)
            self.assertIn("keywords=end", report)
            self.assertIn("keywords=checkpoint", report)


if __name__ == "__main__":
    unittest.main()
