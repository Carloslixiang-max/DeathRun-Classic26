#!/usr/bin/env python3

import unittest

from anvil_cluster import Candidate
from anvil_topology import portal_components, pressure_plate_components


class AnvilTopologyTest(unittest.TestCase):

    def test_pressure_plates_group_horizontal_neighbors_only(self):
        points = [
            Candidate("PRESSURE_PLATE", "minecraft:stone_pressure_plate", 0, 64, 0, "fixture"),
            Candidate("PRESSURE_PLATE", "minecraft:stone_pressure_plate", 1, 64, 1, "fixture"),
            Candidate("PRESSURE_PLATE", "minecraft:oak_pressure_plate", 8, 64, 8, "fixture"),
            Candidate("PRESSURE_PLATE", "minecraft:oak_pressure_plate", 9, 64, 8, "fixture"),
            Candidate("BUTTON", "minecraft:stone_button", 1, 64, 0, "fixture"),
        ]
        groups = pressure_plate_components(points)
        self.assertEqual([2, 2], [len(group.members) for group in groups])
        materials = {
            material
            for group in groups
            for material in group.materials
        }
        self.assertEqual(
            {"minecraft:stone_pressure_plate", "minecraft:oak_pressure_plate"},
            materials,
        )

    def test_portals_group_face_adjacent_blocks(self):
        portals = [
            Candidate("PORTAL", "minecraft:nether_portal", 10, 64, 10, "fixture"),
            Candidate("PORTAL", "minecraft:nether_portal", 10, 65, 10, "fixture"),
            Candidate("PORTAL", "minecraft:nether_portal", 20, 64, 20, "fixture"),
        ]
        groups = portal_components(portals)
        self.assertEqual([2, 1], [len(group.members) for group in groups])


if __name__ == "__main__":
    unittest.main()
