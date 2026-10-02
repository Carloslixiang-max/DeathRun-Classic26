"""CI sentinel for preserving a configured waiting location through repair/restart.

The sentinel is a test destination, not a recovered original DeathRun lobby.
Only the plugin's emitted block-style Location mapping is accepted.
"""
import argparse
import json
from pathlib import Path
import re


def location_block(text):
    lines = text.splitlines(keepends=True)
    entries = []
    for row, line in enumerate(lines):
        match = re.fullmatch(r"(\s*)-\s+id:\s*(.*?)\s*\n?", line)
        if match:
            entries.append((row, len(match.group(1)), match.group(2).strip("'\"")))
    targets = [entry for entry in entries if entry[2] == 'to-bee-or-not-to-bee']
    if len(targets) != 1:
        raise ValueError("Expected exactly one ToBee map entry")
    map_start, map_indent, _ = targets[0]
    map_end = next((row for row, indent, _ in entries if row > map_start and indent <= map_indent), len(lines))
    blocks = []
    for start, line in enumerate(lines):
        if not map_start <= start < map_end:
            continue
        match = re.fullmatch(r"(\s*)arena-waiting-lobby-location:\s*\n?", line)
        if not match:
            continue
        indent = len(match.group(1))
        end = start + 1
        while end < map_end:
            row = lines[end]
            if row.strip() and not row.lstrip().startswith('#') and len(row) - len(row.lstrip()) <= indent:
                break
            end += 1
        blocks.append((start, end))
    if len(blocks) != 1:
        raise ValueError(f"Expected exactly one configured waiting location, got {len(blocks)}")
    start, end = blocks[0]
    return lines, start, end


def values(text):
    lines, start, end = location_block(text)
    result = {}
    for row in lines[start + 1:end]:
        match = re.fullmatch(r"\s*(\w+):\s*(.*?)\s*\n?", row)
        if match:
            key, value = match.groups()
            try:
                value = float(value)
            except ValueError:
                value = value.strip("'\"")
            result[key] = value
    if not {'world', 'x', 'y', 'z', 'yaw', 'pitch'} <= result.keys():
        raise ValueError("Incomplete serialized waiting location")
    return result


def prepare(map_path, snapshot_path):
    text = map_path.read_text()
    lines, start, end = location_block(text)
    block = ''.join(lines[start:end])
    block, x_count = re.subn(r"(?m)^(\s*x:)\s*.*$", r"\g<1> 35.5", block)
    block, yaw_count = re.subn(r"(?m)^(\s*yaw:)\s*.*$", r"\g<1> 33.0", block)
    if x_count != 1 or yaw_count != 1:
        raise ValueError("Location fields were not unique")
    text = ''.join(lines[:start]) + block + ''.join(lines[end:])
    snapshot_path.write_text(json.dumps(values(text), sort_keys=True))
    map_path.write_text(text)


def check(map_path, snapshot_path):
    expected = json.loads(snapshot_path.read_text())
    actual = values(map_path.read_text())
    if actual != expected:
        raise ValueError(f"Waiting lobby was changed: {expected} -> {actual}")
    print("Waiting lobby preservation PASS")



def legacy(map_path):
    text = map_path.read_text()
    lines, start, end = location_block(text)
    block = ''.join(lines[start:end])
    for key, value in {'x': 85.5, 'y': 25.0, 'z': 79.5, 'yaw': 90.0, 'pitch': 0.0}.items():
        block, count = re.subn(r'(?m)^(\s*' + key + r':)\s*.*$', r'\g<1> ' + str(value), block)
        if count != 1:
            raise ValueError('Location fields were not unique')
    map_path.write_text(''.join(lines[:start]) + block + ''.join(lines[end:]))


def courtyard(map_path):
    actual = values(map_path.read_text())
    expected = {'x': 34.5, 'y': 35.0, 'z': 59.5, 'yaw': 180.0, 'pitch': 0.0}
    if any(actual[key] != value for key, value in expected.items()):
        raise ValueError(f'Wrong courtyard landing: {actual}')
    if 'name: To Bee Or Not To Bee' not in map_path.read_text():
        raise ValueError('English map name was not applied')
    print('Waiting courtyard migration PASS')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['prepare', 'check', 'legacy', 'courtyard'])
    parser.add_argument('map', type=Path)
    parser.add_argument('snapshot', type=Path, nargs='?')
    args = parser.parse_args()
    if args.action in ('legacy', 'courtyard'):
        (legacy if args.action == 'legacy' else courtyard)(args.map)
    else:
        (prepare if args.action == 'prepare' else check)(args.map, args.snapshot)
