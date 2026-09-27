#!/usr/bin/env python3

import unittest

from anvil_route_surface_evidence import SurfaceCase, components_by_material, standing_surfaces


class RouteSurfaceEvidenceTest(unittest.TestCase):
    def test_standing_surface_requires_air_headroom_and_support(self):
        case=SurfaceCase("x","test",(0,2,0))
        blocks={
            (0,1,0):"minecraft:stone",
            (0,2,0):"minecraft:air",
            (0,3,0):"minecraft:air",
            (1,1,0):"minecraft:red_tulip",
            (1,2,0):"minecraft:air",
            (1,3,0):"minecraft:air",
            (2,1,0):"minecraft:stone",
            (2,2,0):"minecraft:water",
            (2,3,0):"minecraft:air",
        }
        surfaces=standing_surfaces(blocks,case,3,1)
        self.assertEqual({(0,1,0):"minecraft:stone"},surfaces)

    def test_components_do_not_merge_different_materials(self):
        surfaces={
            (0,1,0):"minecraft:stone",
            (1,1,0):"minecraft:stone",
            (2,1,0):"minecraft:spruce_planks",
        }
        components=components_by_material(surfaces)
        self.assertEqual([2,1],[len(c[1]) for c in components])


if __name__=="__main__":
    unittest.main()
