#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from anvil_sign_links import parse_probe_report
from anvil_trap_evidence import (
    build_actions,
    clean_label,
    evidence_tokens,
    rank_warning,
    render_report,
)


class AnvilTrapEvidenceTest(unittest.TestCase):

    def test_normalizes_action_labels_without_inventing_types(self):
        self.assertEqual(
            "Release fire snake",
            clean_label(">>> | Release fire | snake | >>>"),
        )
        self.assertEqual(
            frozenset({"release", "fire", "snake"}),
            evidence_tokens(">>> | Release fire | snake | >>>"),
        )

    def test_warning_ranking_keeps_distance_and_text_signals_separate(self):
        fixture = """SIGN\tminecraft:sign\t10\t64\t10\tregion=r.0.0.mca\tchunk=0,0\ttext=>>> | Explode the | minefield | >>>
SIGN\tminecraft:sign\t30\t64\t30\tregion=r.0.0.mca\tchunk=1,1\ttext=<<< | Summon a | random wall | <<<
SIGN\tminecraft:sign\t12\t64\t10\tregion=r.0.0.mca\tchunk=0,0\ttext=Caution! | Deadly | Minefield!
SIGN\tminecraft:sign\t29\t64\t30\tregion=r.0.0.mca\tchunk=1,1\ttext=Warning! | Random wall | may appear!
"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "probe.txt"
            path.write_text(fixture, encoding="utf-8")
            _buttons, signs = parse_probe_report(path)
            actions = build_actions(signs)
            warnings = [sign for sign in signs if "Warning" in sign.text or "Caution" in sign.text]

            minefield = next(sign for sign in warnings if "Minefield" in sign.text)
            ranked = rank_warning(minefield, actions)
            self.assertEqual("Explode the minefield", ranked[0].action.label)
            self.assertEqual(("minefield",), ranked[0].shared_tokens)
            self.assertAlmostEqual(2.0, ranked[0].distance)

            report = render_report(path, signs, top_candidates=2, nearby_distance=30.0)
            self.assertIn("directional_actions=2", report)
            self.assertIn("unique_action_labels=2", report)
            self.assertIn("warnings=2", report)
            self.assertIn("ACTION_CATALOG", report)
            self.assertIn("WARNING_EVIDENCE", report)
            self.assertNotIn("trap_type=", report)


if __name__ == "__main__":
    unittest.main()
