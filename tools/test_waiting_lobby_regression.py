import tempfile
from pathlib import Path
import unittest
from waiting_lobby_regression import prepare, check


class WaitingLobbyRegressionTest(unittest.TestCase):
    def test_other_maps_waiting_points_do_not_contaminate_tobee_probe(self):
        text = """maps:
- id: to-bee-or-not-to-bee
  arena-waiting-lobby-location:
    world: tobee_ci
    x: 85.5
    y: 25.0
    z: 79.5
    yaw: 90.0
    pitch: 0.0
- id: classic26-playtest
  arena-waiting-lobby-location:
    world: engineering
    x: 1.5
    y: 70.0
    z: 1.5
    yaw: 0.0
    pitch: 0.0
"""
        with tempfile.TemporaryDirectory() as root:
            map_path = Path(root) / 'map.yml'
            snapshot = Path(root) / 'expected.json'
            map_path.write_text(text)
            prepare(map_path, snapshot)
            check(map_path, snapshot)
            self.assertIn('x: 1.5', map_path.read_text())

    def test_preservation_probe_rejects_runner_spawn_overwrite(self):
        text = """arena-waiting-lobby-location: null
maps:
- id: to-bee-or-not-to-bee
  arena-waiting-lobby-location:
    world: tobee_ci
    worldUuid: 'original-world-uuid'
    x: 85.5
    y: 25.0
    z: 79.5
    yaw: 90.0
    pitch: 0.0
  arena-runner-spawn-locations: []
"""
        with tempfile.TemporaryDirectory() as root:
            map_path = Path(root) / 'map.yml'
            snapshot = Path(root) / 'expected.json'
            map_path.write_text(text)
            prepare(map_path, snapshot)
            check(map_path, snapshot)
            map_path.write_text(map_path.read_text().replace('x: 35.5', 'x: 85.5'))
            with self.assertRaisesRegex(ValueError, 'Waiting lobby was changed'):
                check(map_path, snapshot)


if __name__ == '__main__':
    unittest.main()
