#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from anvil_cluster import parse_probe_report as parse_candidate_probe
from anvil_pressure_route_evidence import build_contexts, render_report
from anvil_sign_links import parse_probe_report as parse_sign_probe
from anvil_topology import portal_components


class AnvilPressureRouteEvidenceTest(unittest.TestCase):

    def test_selected_plate_reports_nearest_route_evidence_without_role(self):
        fixture = """BLOCK\tPRESSURE_PLATE\tminecraft:heavy_weighted_pressure_plate\t10\t64\t10\tregion=r.0.0.mca\tchunk=0,0
BLOCK\tPRESSURE_PLATE\tminecraft:stone_pressure_plate\t50\t64\t50\tregion=r.0.0.mca\tchunk=3,3
BLOCK\tPORTAL\tminecraft:nether_portal\t12\t64\t10\tregion=r.0.0.mca\tchunk=0,0
BLOCK\tPORTAL\tminecraft:nether_portal\t12\t65\t10\tregion=r.0.0.mca\tchunk=0,0
SIGN\tminecraft:sign\t9\t64\t10\tregion=r.0.0.mca\tchunk=0,0\ttext=Warning! | Test trap
SIGN\tminecraft:sign\t10\t64\t13\tregion=r.0.0.mca\tchunk=0,0\ttext=>>> | Test action | >>>
SIGN\tminecraft:sign\t15\t64\t10\tregion=r.0.0.mca\tchunk=0,0\ttext=Hint: | Look down!
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(fixture, encoding="utf-8")
            points, portal_points = parse_candidate_probe(path)
            _buttons, signs = parse_sign_probe(path)
            selected = [
                item for item in points
                if item.name == "minecraft:heavy_weighted_pressure_plate"
            ]
            contexts = build_contexts(
                selected,
                signs,
                portal_components(portal_points),
            )
            self.assertEqual(1, len(contexts))
            report = render_report(
                path,
                "minecraft:heavy_weighted_pressure_plate",
                contexts,
                total_pressure_plates=2,
            )
            self.assertIn("selected_plates=1", report)
            self.assertIn("pos=10,64,10", report)
            self.assertIn("nearest_warning=9,64,10", report)
            self.assertIn("nearest_action=10,64,13", report)
            self.assertIn("nearest_hint=15,64,10", report)
            self.assertIn("nearest_portal=001", report)
            self.assertIn("NOT automatically a checkpoint", report)


if __name__ == "__main__":
    unittest.main()
