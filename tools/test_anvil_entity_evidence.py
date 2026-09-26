#!/usr/bin/env python3

import unittest

from anvil_entity_evidence import entity_records_from_level, render_report


class AnvilEntityEvidenceTest(unittest.TestCase):

    def test_extracts_named_tagged_armor_stand_and_passenger(self):
        level = {
            "Entities": [
                {
                    "id": "minecraft:armor_stand",
                    "Pos": [10.5, 64.0, -2.5],
                    "CustomName": '{"text":"Checkpoint 4"}',
                    "Tags": ["deathrun", "runner_spawn"],
                    "Invisible": 1,
                    "Marker": 1,
                    "NoGravity": 1,
                    "Passengers": [
                        {
                            "id": "minecraft:item_frame",
                            "Pos": [10.5, 65.0, -2.5],
                        }
                    ],
                },
                {
                    "id": "minecraft:zombie",
                    "Pos": [0.0, 64.0, 0.0],
                },
            ]
        }

        evidence, types, total = entity_records_from_level(
            level, "r.0.-1.mca", 0, -1
        )

        self.assertEqual(3, total)
        self.assertEqual(1, types["minecraft:armor_stand"])
        self.assertEqual(1, types["minecraft:item_frame"])
        self.assertEqual(1, types["minecraft:zombie"])
        self.assertEqual(2, len(evidence))

        armor = next(item for item in evidence if "armor_stand" in item.entity_id)
        self.assertEqual("Checkpoint 4", armor.custom_name)
        self.assertEqual(("deathrun", "runner_spawn"), armor.tags)
        self.assertEqual(("checkpoint", "runner", "spawn"), armor.route_words)
        self.assertTrue(armor.invisible)
        self.assertTrue(armor.marker)
        self.assertTrue(armor.no_gravity)

    def test_plain_mob_is_counted_but_not_evidence(self):
        level = {
            "Entities": [
                {
                    "id": "minecraft:zombie",
                    "Pos": [0.0, 64.0, 0.0],
                }
            ]
        }
        evidence, types, total = entity_records_from_level(
            level, "r.0.0.mca", 0, 0
        )
        self.assertEqual(1, total)
        self.assertEqual(1, types["minecraft:zombie"])
        self.assertEqual([], evidence)


if __name__ == "__main__":
    unittest.main()
