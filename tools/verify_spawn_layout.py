#!/usr/bin/env python3
"""Verify reviewed spawn platforms against the pinned raw Anvil archive."""
from pathlib import Path
import re,sys
from anvil_portal_spawn_surface_evidence import scan_blocks,AIR
root=Path(__file__).resolve().parents[1]
source=(root/'core/src/main/java/pl/mrstudios/deathrun/classic/tobee/ToBeeSpawnGeometry.java').read_text()
controls=re.findall(r'new ControlLanding\("([^\"]+)", new Pos\((-?\d+), (-?\d+), (-?\d+)\), new Pos\((-?\d+), (-?\d+), (-?\d+)\)\)',source)
assert len(controls)==23
blocks,_=scan_blocks(Path(sys.argv[1]),[(-70,15,-50,100,53,90)])
full={'minecraft:grass_block','minecraft:red_terracotta','minecraft:pink_terracotta','minecraft:stone_bricks','minecraft:mossy_stone_bricks','minecraft:spruce_planks','minecraft:sea_lantern'}
def valid(x,y,z):
    return blocks.get((x,y,z),'minecraft:air') in AIR and blocks.get((x,y+1,z),'minecraft:air') in AIR and blocks.get((x,y-1,z),'minecraft:air') in full
for id,bx,by,bz,x,y,z in controls:
    assert valid(int(x),int(y),int(z)),id
for pos in ((75,25,56),(76,25,57)): assert valid(*pos),pos
runner=[(x,25,z) for x in range(85,93) for z in range(79,86) if valid(x,25,z) and blocks.get((x,24,z))=='minecraft:grass_block']
assert len(runner)>=20,len(runner)
print(f'SPAWN_LAYOUT PASS runner_capacity={len(runner)} death=2 control_landings=23')

# Screenshot 3 courtyard: matching ring, clear full-block landing outside the central pit.
assert valid(34,35,59), "waiting-courtyard-landing"
for x,z in ((34,45),(34,57),(28,51),(40,51)):
    assert blocks.get((x,35,z)) == 'minecraft:light_weighted_pressure_plate', (x,z)
print('WAITING_COURTYARD PASS location=34.5,35.0,59.5 yaw=180 gold_plate_ring=matched')
