#!/usr/bin/env python3
"""
Writes the empty template the GameTests build their rigs into.

A GameTest needs a structure file to exist before it will run, even when the test lays every block
it needs itself -- which these do, because a mod of nine hand tools has no standing arrangement of
blocks to photograph. So the file is one thing: an empty box of the right size. Checking in a
hand-made .nbt for that would be checking in an opaque blob nobody can read or resize.

Deterministic on purpose: gzip is given mtime=0, so re-running writes a byte-identical file and
the CI gate that regenerates everything and fails on a diff stays honest.

    python3 tools/generate_structures.py
"""

import gzip
import os
import struct
import sys

DATA_VERSION = 3955  # 1.21.1


# --- a very small NBT writer --------------------------------------------------------------------


def _str(value):
    raw = value.encode('utf8')
    return struct.pack('>H', len(raw)) + raw


def _compound(pairs):
    out = b''
    for name, (tag, payload) in pairs:
        out += bytes([tag]) + _str(name) + payload
    return out + b'\x00'


def _list(tag, items):
    return bytes([tag]) + struct.pack('>i', len(items)) + b''.join(items)


def _int_list(values):
    return _list(3, [struct.pack('>i', v) for v in values])


def write_structure(path, size, palette, blocks):
    """
    palette: list of (name, {property: value}).
    blocks: list of (state_index, (x, y, z)).
    """
    palette_entries = []
    for name, properties in palette:
        pairs = [('Name', (8, _str(name)))]
        if properties:
            pairs.append(('Properties', (10, _compound(
                [(k, (8, _str(v))) for k, v in sorted(properties.items())]))))
        palette_entries.append(_compound(pairs))

    block_entries = []
    for state, pos in blocks:
        block_entries.append(_compound([
            ('state', (3, struct.pack('>i', state))),
            ('pos', (9, _int_list(list(pos)))),
        ]))

    root = _compound([
        ('DataVersion', (3, struct.pack('>i', DATA_VERSION))),
        ('size', (9, _int_list(list(size)))),
        ('palette', (9, _list(10, palette_entries))),
        ('blocks', (9, _list(10, block_entries))),
        ('entities', (9, _list(0, []))),
    ])
    payload = b'\x0a' + _str('') + root

    directory = os.path.dirname(path)
    if directory:
        os.makedirs(directory, exist_ok=True)
    with gzip.GzipFile(path, 'wb', mtime=0) as handle:
        handle.write(payload)


# --- the template ------------------------------------------------------------------------------

STRUCTURE_ROOT = 'src/main/resources/data/createpneumatictools/structure'

# Room for the widest thing any test builds: a 5x5 wall for the tunnelling drill, a nine-log tree
# for the saw, and air around both so nothing a test breaks reaches the edge of the box.
WORKSHOP_SIZE = (13, 12, 13)


def main():
    path = os.path.join(STRUCTURE_ROOT, 'workshop.nbt')
    write_structure(path, WORKSHOP_SIZE, [('minecraft:air', None)], [])
    print('wrote %s (empty %sx%sx%s)' % ((path,) + WORKSHOP_SIZE))
    return 0


if __name__ == '__main__':
    sys.exit(main())
