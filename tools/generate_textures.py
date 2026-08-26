#!/usr/bin/env python3
"""
Draws the material panels the 3D tool models are skinned with.

There are eight of them and not one is a picture of anything: every face of every box in
tools/generate_models.py takes its UV from its own measurements, sampling the top-left corner of a
panel at one texel per model unit. So a panel has to look right cropped to any rectangle from its top
left, which rules out anything with a frame, a border or a feature in it, and leaves grain. That is
the whole design brief -- eight surfaces that read as andesite, brass, copper, gunmetal, steel,
obsidian, grinding grit and a buffing pad at a glance, and tile invisibly at any crop.

This file used to draw nine 16x16 item sprites instead, back when the tools were flat. They are gone:
the models carry their own geometry now, and an unused sprite in the jar is a sprite someone will
later assume is load-bearing.

None of Create's art is used or derived from: Create's code is MIT but everything under its `assets/`
is All Rights Reserved, so the only safe amount of it to copy is none. What is borrowed is the
*convention* -- a flat base with two shade steps and a hard highlight, which is how Minecraft metal
has looked since 2011 and is not anyone's to own.

Everything is deterministic: the "noise" is a hash of the coordinate, so re-running this produces
byte-identical files and a diff in the repo means someone changed a drawing.

    python3 tools/generate_textures.py

The mod badge is not here -- it is tools/generate_logo.py, which draws the Create-family disc.
"""

import os
import struct
import sys
import zlib

PARTS = 'src/main/resources/assets/createpneumatictools/textures/item/part'
SIZE = 16


# --- PNG ---------------------------------------------------------------------------------------


def write_png(path, width, height, pixels):
    """pixels: flat list of (r, g, b, a) tuples, row-major from the top left."""
    raw = bytearray()
    for y in range(height):
        raw.append(0)  # filter type 0 (None) -- these are tiny, compression is not the point
        for x in range(width):
            raw.extend(pixels[y * width + x])

    def chunk(tag, data):
        body = tag + data
        return struct.pack('>I', len(data)) + body + struct.pack('>I', zlib.crc32(body))

    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
    png += chunk(b'IEND', b'')

    directory = os.path.dirname(path)
    if directory:
        os.makedirs(directory, exist_ok=True)
    with open(path, 'wb') as handle:
        handle.write(png)


# --- helpers -----------------------------------------------------------------------------------


def noise(x, y, salt):
    """A stable -1/0/1 per pixel, so a surface looks worked rather than printed."""
    h = (x * 374761393 + y * 668265263 + salt * 2246822519) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h >> 7) % 3) - 1


def spark(x, y, salt, chance):
    """True for roughly one pixel in `chance`, deterministically."""
    h = (x * 2654435761 + y * 40503 + salt * 97) & 0xFFFFFFFF
    h = (h ^ (h >> 15)) * 2246822519 & 0xFFFFFFFF
    return (h >> 11) % chance == 0


def shade(colour, amount):
    return tuple(max(0, min(255, c + amount)) for c in colour[:3]) + (colour[3],)


def panel(base, salt, strength=6, brushed=0, sparkle=None):
    """
    One material: a flat base, a per-pixel grain, and optionally a horizontal brushed streak or a
    scatter of bright specks.

    `brushed` runs the variation along rows rather than per pixel, which is what makes steel and brass
    read as machined instead of cast -- and, since faces crop from the top left at arbitrary sizes,
    a row-wise pattern survives cropping where a radial or centred one would not.
    """
    pixels = []
    for y in range(SIZE):
        streak = noise(0, y, salt + 91) * brushed
        for x in range(SIZE):
            colour = shade(base, noise(x, y, salt) * strength + streak)
            if sparkle and spark(x, y, salt, sparkle[0]):
                colour = shade(colour, sparkle[1])
            pixels.append(colour)
    return pixels


# --- the materials -------------------------------------------------------------------------------
#
# The same andesite, brass, copper and steel the badge is painted in, so the tools and the mod's own
# icon are one palette rather than two that only look similar.

MATERIALS = {
    'andesite': ((118, 118, 116, 255), 11, 7, 0, None),
    'brass': ((186, 151, 73, 255), 23, 5, 4, None),
    'copper': ((196, 123, 78, 255), 37, 7, 0, None),
    'gunmetal': ((68, 72, 80, 255), 53, 5, 3, None),
    'steel': ((150, 158, 168, 255), 71, 4, 6, None),
    # Obsidian gets a scatter of bright flecks: at this size a dark purple panel with no highlight
    # reads as a hole in the model rather than as a material.
    'obsidian': ((84, 66, 114, 255), 89, 6, 0, (11, 46)),
    # Grit is deliberately the coarsest of the eight -- it is the one surface whose job is to look
    # abrasive, and it has to be told apart from the buffing pad at a glance.
    'grit': ((176, 84, 68, 255), 101, 14, 0, (7, 26)),
    'pad': ((238, 232, 220, 255), 113, 5, 2, None),
}


def main():
    for name, (base, salt, strength, brushed, sparkle) in sorted(MATERIALS.items()):
        write_png(os.path.join(PARTS, name + '.png'), SIZE, SIZE,
                  panel(base, salt, strength, brushed, sparkle))
    print('wrote %d material panels to %s' % (len(MATERIALS), PARTS))
    return 0


if __name__ == '__main__':
    sys.exit(main())
