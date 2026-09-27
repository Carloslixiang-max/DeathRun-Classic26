#!/usr/bin/env python3

import unittest

from anvil_cluster import Candidate
from anvil_portal_spawn_surface_evidence import standing_candidates
from anvil_topology import portal_components


class AnvilPortalSpawnSurfaceEvidenceTest(unittest.TestCase):

    def test_finds_air_columns_with_solid_support_on_both_gate_sides(self):
        portal_points = [
            Candidate("PORTAL", "minecraft:nether_portal", 0, y, z, "fixture")
            for y in range(64, 67)
            for z in range(0, 3)
        ]
        component = portal_components(portal_points)[0]

        blocks = {}
        for x in (-2, -1, 1, 2):
            for z in range(-1, 4):
                blocks[(x, 63, z)] = "minecraft:stone"
                blocks[(x, 64, z)] = "minecraft:air"
                blocks[(x, 65, z)] = "minecraft:air"

        evidence = standing_candidates(
            1,
            component,
            blocks,
            max_normal_distance=2,
            lateral_padding=1,
        )

        self.assertEqual("x", evidence.plane_axis)
        self.assertTrue(evidence.negative)
        self.assertTrue(evidence.positive)
        self.assertEqual(1, evidence.negative[0].normal_distance)
        self.assertEqual("minecraft:stone", evidence.positive[0].support)

    def test_grass_plant_is_not_accepted_as_spawn_support(self):
        portal_points = [
            Candidate("PORTAL", "minecraft:nether_portal", 0, 64, 0, "fixture"),
            Candidate("PORTAL", "minecraft:nether_portal", 0, 65, 0, "fixture"),
        ]
        component = portal_components(portal_points)[0]
        blocks = {
            (1, 63, 0): "minecraft:grass",
            (1, 64, 0): "minecraft:air",
            (1, 65, 0): "minecraft:air",
        }
        evidence = standing_candidates(
            1,
            component,
            blocks,
            max_normal_distance=1,
            lateral_padding=0,
        )
        self.assertFalse(evidence.positive)

    def test_barrier_is_not_accepted_as_spawn_support(self):
        portal_points = [
            Candidate("PORTAL", "minecraft:nether_portal", 0, 64, 0, "fixture"),
            Candidate("PORTAL", "minecraft:nether_portal", 0, 65, 0, "fixture"),
        ]
        component = portal_components(portal_points)[0]
        blocks = {
            (1, 63, 0): "minecraft:barrier",
            (1, 64, 0): "minecraft:air",
            (1, 65, 0): "minecraft:air",
        }
        evidence = standing_candidates(
            1,
            component,
            blocks,
            max_normal_distance=1,
            lateral_padding=0,
        )
        self.assertFalse(evidence.positive)


if __name__ == "__main__":
    unittest.main()
