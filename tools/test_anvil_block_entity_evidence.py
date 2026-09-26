#!/usr/bin/env python3

import unittest

from anvil_block_entity_evidence import evidence_from_level


class AnvilBlockEntityEvidenceTest(unittest.TestCase):

    def test_counts_signs_but_surfaces_non_sign_metadata(self):
        level = {
            "TileEntities": [
                {
                    "id": "minecraft:sign",
                    "x": 1,
                    "y": 64,
                    "z": 1,
                    "Text1": '{"text":"ignored here"}',
                },
                {
                    "id": "minecraft:structure_block",
                    "x": 2,
                    "y": 65,
                    "z": 2,
                    "name": "deathrun:checkpoint_4",
                    "author": "builder",
                    "metadata": "runner spawn",
                },
                {
                    "id": "minecraft:chest",
                    "x": 3,
                    "y": 65,
                    "z": 3,
                    "CustomName": "Death Spawn Kit",
                },
            ]
        }

        evidence, types, total, non_sign = evidence_from_level(
            level, "r.0.0.mca", 0, 0
        )

        self.assertEqual(3, total)
        self.assertEqual(2, non_sign)
        self.assertEqual(1, types["minecraft:sign"])
        self.assertEqual(1, types["minecraft:structure_block"])
        self.assertEqual(1, types["minecraft:chest"])
        self.assertEqual(2, len(evidence))

        structure = next(
            item for item in evidence if item.entity_id == "minecraft:structure_block"
        )
        self.assertIn("checkpoint", structure.route_words)
        self.assertIn("runner", structure.route_words)
        self.assertIn("spawn", structure.route_words)

        chest = next(item for item in evidence if item.entity_id == "minecraft:chest")
        self.assertEqual(("death", "spawn"), chest.route_words)

    def test_extracts_container_inventory_stacks(self):
        level = {
            "TileEntities": [
                {
                    "id": "minecraft:dispenser",
                    "x": 10,
                    "y": 64,
                    "z": 10,
                    "Items": [
                        {
                            "Slot": 0,
                            "id": "minecraft:arrow",
                            "Count": 64,
                        },
                        {
                            "Slot": 3,
                            "id": "minecraft:fire_charge",
                            "Count": 4,
                        },
                    ],
                }
            ]
        }
        evidence, _types, total, non_sign = evidence_from_level(
            level, "r.0.0.mca", 0, 0
        )
        self.assertEqual(1, total)
        self.assertEqual(1, non_sign)
        self.assertEqual(2, len(evidence[0].inventory))
        self.assertEqual("minecraft:arrow", evidence[0].inventory[0].item_id)
        self.assertEqual(64, evidence[0].inventory[0].count)
        self.assertEqual(0, evidence[0].inventory[0].slot)
        self.assertEqual("minecraft:fire_charge", evidence[0].inventory[1].item_id)

    def test_plain_non_sign_block_entity_is_still_reviewable(self):
        level = {
            "block_entities": [
                {
                    "id": "minecraft:skull",
                    "x": 4,
                    "y": 70,
                    "z": 4,
                }
            ]
        }
        evidence, _types, total, non_sign = evidence_from_level(
            level, "r.0.0.mca", 0, 0
        )
        self.assertEqual(1, total)
        self.assertEqual(1, non_sign)
        self.assertEqual(1, len(evidence))
        self.assertEqual((), evidence[0].metadata)


if __name__ == "__main__":
    unittest.main()
