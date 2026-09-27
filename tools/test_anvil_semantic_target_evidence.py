#!/usr/bin/env python3

import unittest

from anvil_semantic_target_evidence import (
    TargetCase,
    analyze_case,
    connected_components,
    semantic_family,
)


class SemanticTargetEvidenceTest(unittest.TestCase):
    def test_material_families_are_narrow(self):
        self.assertEqual("ICE", semantic_family("minecraft:packed_ice"))
        self.assertEqual("WOODLIKE", semantic_family("minecraft:dark_oak_planks"))
        self.assertEqual("WOODLIKE", semantic_family("minecraft:spruce_log"))
        self.assertEqual("RED_STRUCTURAL", semantic_family("minecraft:red_concrete"))
        self.assertEqual("RED_STRUCTURAL", semantic_family("minecraft:red_terracotta"))
        self.assertEqual("COAL", semantic_family("minecraft:coal_block"))
        self.assertEqual("SEA_LANTERN", semantic_family("minecraft:sea_lantern"))
        self.assertIsNone(semantic_family("minecraft:redstone_block"))
        self.assertIsNone(semantic_family("minecraft:red_tulip"))
        self.assertIsNone(semantic_family("minecraft:oak_leaves"))
        self.assertIsNone(semantic_family("minecraft:stone_brick_slab"))
        self.assertIsNone(semantic_family("minecraft:stone_brick_stairs"))

    def test_connected_components_use_face_adjacency(self):
        points = {(0, 0, 0), (1, 0, 0), (1, 1, 0), (5, 0, 0)}
        components = connected_components(points)
        self.assertEqual([3, 1], [len(component) for component in components])

    def test_case_analysis_filters_family_and_radius(self):
        case = TargetCase("x", "red", (0, 0, 0), ("RED_STRUCTURAL",))
        blocks = {
            (1, 0, 0): "minecraft:red_wool",
            (2, 0, 0): "minecraft:red_concrete",
            (9, 0, 0): "minecraft:red_wool",
            (0, 0, 1): "minecraft:redstone_block",
        }
        result = analyze_case(blocks, case, radius=3, vertical_radius=1)
        self.assertEqual(1, len(result))
        family, component, distance, materials = result[0]
        self.assertEqual("RED_STRUCTURAL", family)
        self.assertEqual(2, len(component))
        self.assertEqual(1.0, distance)
        self.assertEqual(1, materials["minecraft:red_wool"])
        self.assertEqual(1, materials["minecraft:red_concrete"])


if __name__ == "__main__":
    unittest.main()
