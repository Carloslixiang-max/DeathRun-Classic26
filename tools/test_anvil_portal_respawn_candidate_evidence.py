#!/usr/bin/env python3

import unittest

from anvil_portal_respawn_candidate_evidence import _rank, _weak_support
from anvil_portal_spawn_surface_evidence import StandingCandidate


class AnvilPortalRespawnCandidateEvidenceTest(unittest.TestCase):

    def test_strong_floor_support_ranks_ahead_of_weak_structure(self):
        weak = StandingCandidate(
            portal_id=1,
            side=1,
            x=1,
            y=64,
            z=0,
            normal_distance=1,
            support="minecraft:iron_bars",
            distance_to_center=1.0,
        )
        floor = StandingCandidate(
            portal_id=1,
            side=1,
            x=2,
            y=64,
            z=0,
            normal_distance=2,
            support="minecraft:grass_block",
            distance_to_center=2.0,
        )
        ranked = _rank([weak, floor])
        self.assertEqual(floor, ranked[0])
        self.assertTrue(_weak_support(weak.support))
        self.assertFalse(_weak_support(floor.support))


if __name__ == "__main__":
    unittest.main()
