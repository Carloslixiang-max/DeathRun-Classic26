#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from anvil_stage_direction_evidence import render_report, StageDirectionCheck


class AnvilStageDirectionEvidenceTest(unittest.TestCase):

    def test_cross_check_preserves_candidate_status(self):
        checks = [
            StageDirectionCheck(
                path=(1, 2, 3),
                warning_agree=8,
                warning_disagree=1,
                warning_lateral=1,
                next_portals=(1,),
                previous_portals=(3,),
                stage_order_compatible=True,
            ),
            StageDirectionCheck(
                path=(3, 2, 1),
                warning_agree=1,
                warning_disagree=8,
                warning_lateral=1,
                next_portals=(1,),
                previous_portals=(3,),
                stage_order_compatible=False,
            ),
        ]
        with tempfile.TemporaryDirectory() as tmp:
            report, stats = render_report(
                Path(tmp) / "probe.txt",
                Path(tmp) / "entities.txt",
                checks,
            )
        self.assertEqual(1, stats["stage_compatible_directions"])
        self.assertEqual("001->002->003", stats["best_warning_path"])
        self.assertEqual("true", stats["best_warning_stage_compatible"])
        self.assertIn("not confirmed Runner checkpoint order", report)


if __name__ == "__main__":
    unittest.main()
