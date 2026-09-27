#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from anvil_endpoint_context import render_report, EndpointContext
from anvil_cluster import Candidate
from anvil_sign_links import Sign
from anvil_stage_evidence import StageMarker
from anvil_topology import portal_components
from anvil_endpoint_context import Mechanism


class AnvilEndpointContextTest(unittest.TestCase):

    def test_symmetric_endpoint_context_does_not_assign_start_finish(self):
        portals = portal_components([
            Candidate("PORTAL","minecraft:nether_portal",0,64,0,"fixture"),
            Candidate("PORTAL","minecraft:nether_portal",0,65,0,"fixture"),
            Candidate("PORTAL","minecraft:nether_portal",30,64,0,"fixture"),
            Candidate("PORTAL","minecraft:nether_portal",30,65,0,"fixture"),
        ])
        warning = Sign("minecraft:sign",2,64,0,"Warning! | test")
        hint = Sign("minecraft:sign",28,64,0,"Hint: | Look down!")
        contexts = [
            EndpointContext(
                portal_id=1,
                portal=portals[0],
                buttons=(),
                pressure_plates=(),
                mechanisms=(),
                route_structures=(
                    Candidate("ROUTE_STRUCTURE","minecraft:iron_bars",1,64,1,"fixture"),
                ),
                warnings=(warning,),
                actions=(),
                hints=(),
                titles=(),
                stage_markers=(StageMarker("minecraft:armor_stand","Previous Stage",1,64,0),),
                unmatched_actions=(),
            ),
            EndpointContext(
                portal_id=2,
                portal=portals[1],
                buttons=(),
                pressure_plates=(),
                mechanisms=(Mechanism("minecraft:dispenser",29,64,0),),
                route_structures=(),
                warnings=(),
                actions=(),
                hints=(hint,),
                titles=(),
                stage_markers=(),
                unmatched_actions=(),
            ),
        ]
        with tempfile.TemporaryDirectory() as tmp:
            report, stats = render_report(Path(tmp)/"probe.txt", contexts, radius=20)
            self.assertEqual(2, stats["endpoints"])
            self.assertEqual("001,002", stats["endpoint_ids"])
            self.assertEqual(1, stats["endpoints_with_hint"])
            self.assertEqual(1, stats["endpoints_with_stage_marker"])
            self.assertEqual(1, stats["endpoints_with_route_structure"])
            self.assertIn("route_structures=1", report)
            self.assertIn("route_structure=1,64,1[minecraft:iron_bars]", report)
            self.assertIn("ENDPOINT_CONTEXT", report)
            self.assertIn("does NOT assign start, finish", report)


if __name__ == "__main__":
    unittest.main()
