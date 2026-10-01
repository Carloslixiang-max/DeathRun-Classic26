import tempfile
from pathlib import Path
import unittest
from waiting_lobby_regression import prepare, check


class WaitingLobbyRegressionTest(unittest.TestCase):
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
            map_path.write_text(map_path.read_text().replace('x: 90.5', 'x: 85.5'))
            with self.assertRaisesRegex(ValueError, 'Waiting lobby was changed'):
                check(map_path, snapshot)


if __name__ == '__main__':
    unittest.main()
