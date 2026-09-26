#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from anvil_stage_evidence import (
    build_neighborhood,
    parse_mechanisms,
    parse_stage_markers,
    render_report,
)
from anvil_sign_links import parse_probe_report


class AnvilStageEvidenceTest(unittest.TestCase):

    def test_stage_marker_links_control_evidence(self):
        probe = """BLOCK\tBUTTON\tminecraft:stone_button\t25\t48\t13\tregion=r.0.0.mca\tchunk=1,0
SIGN\tminecraft:sign\t29\t44\t9\tregion=r.0.0.mca\tchunk=1,0\ttext=Drop TNT | <<<
SIGN\tminecraft:sign\t80\t44\t80\tregion=r.0.0.mca\tchunk=5,5\ttext=>>> | Far trap | >>>
"""
        entity = """ENTITY_EVIDENCE\tid=minecraft:armor_stand\tpos=24.500,45.250,13.500\tname=Next Stage\ttags=none\troute_words=stage\tinvisible=true\tmarker=true
"""
        block_entity = """BLOCK_ENTITY_EVIDENCE\tid=minecraft:hopper\tpos=25,52,13\troute_words=none\tmetadata=none
BLOCK_ENTITY_EVIDENCE\tid=minecraft:skull\tpos=25,45,13\troute_words=none\tmetadata=none
"""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            probe_path = root / "probe.txt"
            entity_path = root / "entity.txt"
            block_path = root / "block.txt"
            probe_path.write_text(probe, encoding="utf-8")
            entity_path.write_text(entity, encoding="utf-8")
            block_path.write_text(block_entity, encoding="utf-8")

            buttons, signs = parse_probe_report(probe_path)
            markers = parse_stage_markers(entity_path)
            mechanisms = parse_mechanisms(block_path)
            self.assertEqual(1, len(markers))
            self.assertEqual(1, len(mechanisms))

            neighborhood = build_neighborhood(
                markers[0],
                buttons,
                signs,
                mechanisms,
                horizontal_radius=12.0,
                vertical_radius=10.0,
            )
            self.assertEqual(1, len(neighborhood.buttons))
            self.assertEqual(1, len(neighborhood.actions))
            self.assertEqual(1, len(neighborhood.mechanisms))

            report = render_report(
                probe_path,
                entity_path,
                block_path,
                horizontal_radius=12.0,
                vertical_radius=10.0,
            )
            self.assertIn("stage_markers=1", report)
            self.assertIn("next_markers=1", report)
            self.assertIn("markers_with_actions=1", report)
            self.assertIn("direction=NEXT", report)
            self.assertIn("Drop TNT", report)
            self.assertNotIn("trap_type=", report)


if __name__ == "__main__":
    unittest.main()
