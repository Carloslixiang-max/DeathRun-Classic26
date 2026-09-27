#!/usr/bin/env python3

import unittest

from anvil_portal_respawn_candidate_evidence import _rank, _weak_support, selected_side
from anvil_portal_route_side_evidence import PortalRouteSide
from anvil_portal_spawn_surface_evidence import StandingCandidate


class AnvilPortalRespawnCandidateEvidenceTest(unittest.TestCase):

    def test_portal7_override_resolves_special_transition_only(self):
        item = PortalRouteSide(
            portal_id=7,
            component=None,
            path_index=1,
            previous_portal=6,
            next_portal=2,
            previous_warning=None,
            next_warning=None,
            previous_side=1,
            next_side=1,
            classification="SAME_SIDE_AMBIGUOUS",
            external_side_candidate=None,
        )
        self.assertEqual(
            (-1, "CHECKPOINT_TRANSITION_SIDE"),
            selected_side(item, -1),
        )
        self.assertEqual(
            (None, "UNRESOLVED"),
            selected_side(item, None),
        )

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
