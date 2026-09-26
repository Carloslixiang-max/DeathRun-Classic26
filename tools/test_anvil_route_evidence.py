#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from anvil_route_evidence import (
    numeric_components,
    numeric_markers,
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
