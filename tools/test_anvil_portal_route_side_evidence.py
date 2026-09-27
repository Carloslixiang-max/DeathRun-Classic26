#!/usr/bin/env python3

import unittest

from anvil_cluster import Candidate
from anvil_portal_route_side_evidence import _side
from anvil_sign_links import Sign
from anvil_topology import portal_components


class AnvilPortalRouteSideEvidenceTest(unittest.TestCase):

    def test_classifies_sign_side_of_planar_portal(self):
        points = [
            Candidate("PORTAL", "minecraft:nether_portal", 5, y, z, "fixture")
            for y in range(64, 67)
            for z in range(10, 13)
        ]
        component = portal_components(points)[0]
        negative = Sign("minecraft:sign", 1, 64, 11, "Warning!")
        positive = Sign("minecraft:sign", 9, 64, 11, "Warning!")
        on_plane = Sign("minecraft:sign", 5, 64, 11, "Warning!")

        self.assertEqual(-1, _side(component, negative))
        self.assertEqual(1, _side(component, positive))
        self.assertEqual(0, _side(component, on_plane))


if __name__ == "__main__":
    unittest.main()
