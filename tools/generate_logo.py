#!/usr/bin/env python3
"""
Generates the mod badge: the Create-family circle of blue graph paper with this mod's subject
drawn large in front of it.

The badge *convention* -- a white-ringed azure disc of graph paper, the subject given a white
stroke and a soft shadow -- is what every Create addon uses to say "this plugs into Create", and a
convention is not artwork. Nothing here is copied from Create: the palette, the proportions and
the grid are the ones the sibling addons in this family already use, so the badges sit together on
a mods list, and the subject is drawn from scratch.

The subject is a pneumatic rock drill: a D-handle you hold in two hands, a brass body with a copper
air line coming out of its flank, a gunmetal chuck and a steel bit tapering to a point. A tool with
a hose attached to it is what "pneumatic" looks like wherever it is drawn, which is why it reads
without a caption -- and the point at the bottom is what stops it being mistaken for the sibling
badge's gas cylinder at thumbnail size.

It is drawn in pixels on a 16x20 grid and blown up by a whole number, so it is Minecraft-shaped
rather than a smooth vector illustration. Everything else is described once at a 256px reference
and scaled by a whole factor, so `--size 512` is the same badge larger rather than a different one.
Sizes must be multiples of 256.

    python3 tools/generate_logo.py [output.png] [--size 256]
"""

import math
import os
import struct
import sys
import zlib

REFERENCE = 256                # the size every measurement below was tuned at
SS = 3                         # supersampling factor per axis

# --- badge palette, shared with the sibling addons so the three match ----------
WHITE       = (255.0, 255.0, 255.0)
FIELD_LIGHT = (104.0, 172.0, 217.0)
FIELD       = ( 75.0, 139.0, 193.0)
FIELD_DEEP  = ( 56.0, 114.0, 168.0)
GRID        = (126.0, 190.0, 228.0)
SHADOW      = ( 30.0,  64.0, 100.0)

# --- subject palette, shared with tools/generate_textures.py -------------------
# The same andesite, brass, copper and steel the nine item sprites are painted in, so the badge is
# the mod's own colours rather than a second set that only looks similar.
ANDESITE       = (118.0, 118.0, 116.0)
ANDESITE_LIGHT = (152.0, 152.0, 148.0)
ANDESITE_DARK  = ( 84.0,  84.0,  84.0)
BRASS          = (176.0, 141.0,  63.0)
BRASS_LIGHT    = (214.0, 180.0,  96.0)
BRASS_DARK     = (128.0, 100.0,  40.0)
BRASS_DEEP     = ( 96.0,  74.0,  30.0)
COPPER         = (196.0, 123.0,  78.0)
COPPER_LIGHT   = (222.0, 156.0, 110.0)
COPPER_DARK    = (146.0,  88.0,  54.0)
STEEL          = (140.0, 148.0, 158.0)
STEEL_LIGHT    = (196.0, 204.0, 214.0)
STEEL_DARK     = ( 92.0,  99.0, 108.0)
GUNMETAL       = ( 58.0,  62.0,  70.0)
GUNMETAL_LIGHT = ( 86.0,  92.0, 102.0)

# --- weights, which are fractions rather than lengths and so do not scale ------
GRID_ALPHA = 0.28
SHADOW_ALPHA = 0.26

USAGE = ('usage: generate_logo.py [output.png] [--size N]  '
         '(N a positive multiple of {})'.format(REFERENCE))

# --- geometry, in reference pixels ---------------------------------------------
# One factor moves all of it, and it has to leave SPRITE_SCALE a whole number -- keeping the
# subject's pixels square is the entire reason it is scaled by an integer -- so the output size
# must be a multiple of REFERENCE. 256 is the in-jar logo; 512 is what CurseForge wants for a
# project icon, since it downscales gracefully and never upscales.
GEOMETRY = {
    'RADIUS': 124.0,           # outer edge of the badge
    'RING': 9.0,               # white ring thickness
    'GRID_SPACING': 46.0,
    'GRID_HALF_WIDTH': 2.5,
    'SPRITE_SCALE': 9,         # whole number, so subject pixels stay square
    'STROKE': 6.0,             # white outline thickness
    'SHADOW_DX': 6.0,
    'SHADOW_DY': 8.0,
    'GLOW_DX': -44.0,          # where the light sits, relative to the centre
    'GLOW_DY': -52.0,
}


def configure(size):
    """Scales the geometry above to the requested output size. Call before rendering."""
    if size <= 0 or size % REFERENCE:
        raise SystemExit('size must be a positive multiple of {}, got {}'.format(REFERENCE, size))
    factor = size // REFERENCE
    globals().update({name: value * factor for name, value in GEOMETRY.items()})
    globals().update(OUT=size, N=size * SS, CX=size / 2.0, CY=size / 2.0)


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t,
            a[1] + (b[1] - a[1]) * t,
            a[2] + (b[2] - a[2]) * t)


# --- the subject ---------------------------------------------------------------

SPRITE_W, SPRITE_H = 16, 22

PALETTE = {
    '.': None,
    'a': ANDESITE,
    'A': ANDESITE_LIGHT,
    'z': ANDESITE_DARK,
    'b': BRASS,
    'B': BRASS_LIGHT,
    'd': BRASS_DARK,
    'D': BRASS_DEEP,
    'c': COPPER,
    'C': COPPER_LIGHT,
    'e': COPPER_DARK,
    's': STEEL,
    'S': STEEL_LIGHT,
    't': STEEL_DARK,
    'g': GUNMETAL,
    'G': GUNMETAL_LIGHT,
}

# The badge is drawn the same way the item sprites are, as a character grid keyed to the palette
# above -- see tools/generate_textures.py for why a picture beats a procedure for something whose
# only job is to be recognised in one glance.
#
# Three things about this drawing are load-bearing, and each was arrived at by drawing the
# alternative:
#
#   * The handle is open. A solid bar across the top of a cylinder is a lid, and a cylinder with a
#     lid on it is the sibling badge's pressure vessel. Two courses of daylight under the crossbar
#     are the whole difference between a tank and a thing you hold -- and they only survive because
#     outside_cells() below can tell a hole from the outside. Fill that in and the badge reverts to
#     a jar.
#   * The drawing steps in and out four times on the way down -- handle, wider shoulder, body,
#     wider chuck flange, then six courses of bit narrowing to a point. Draw the handle and the body
#     at the same width and the silhouette is a padlock, which is what the first three drafts were;
#     the taper is what makes it a tool, and it needs the bit to be a third of the height to land.
#   * The air line leaves the flank, not the base. Under the tool it would vanish behind the white
#     stroke; out to the side it is the only part of the drawing that breaks the outline, and an
#     outline with one thing sticking out of it is far easier to read than a symmetrical one.

SPRITE = """
....zaAAAAaz....
....zaAAAAaz....
....zA....Az....
....zA....Az....
....zA....Az....
..dbaaaaaaaabd..
...dBBbbbdddD...
...dBBbbbdddD...
.ecdBBbbbdddD...
.cCdBBbbbdddD...
.ecdBBbbbdddD...
...dBBbbbdddD...
...dBBbbbdddD...
..dggGGGGGGggd..
...ggGGGGGGgg...
.....gGGGGg.....
.....sStSSs.....
.....sStSSs.....
......StSS......
......StSS......
.......SS.......
.......Ss.......
"""


def subject_sprite():
    """The drill, as (width, height, rows-of-RGBA)."""
    lines = [line for line in SPRITE.splitlines() if line]
    if len(lines) != SPRITE_H:
        raise SystemExit('sprite has %d rows, want %d' % (len(lines), SPRITE_H))
    rows = []
    for line in lines:
        if len(line) != SPRITE_W:
            raise SystemExit('sprite row is %d wide, want %d: %r' % (len(line), SPRITE_W, line))
        row = []
        for char in line:
            if char not in PALETTE:
                raise SystemExit('unknown palette character %r' % char)
            colour = PALETTE[char]
            row.append(tuple(int(round(v)) for v in colour) + (255,) if colour else (0, 0, 0, 0))
        rows.append(row)
    return SPRITE_W, SPRITE_H, rows


def opaque_bounds(width, height, pixels):
    """Bounding box of the visible part, so the badge centres on the art not the canvas."""
    min_x, min_y, max_x, max_y = width, height, -1, -1
    for y in range(height):
        for x in range(width):
            if pixels[y][x][3] > 0:
                min_x = min(min_x, x)
                max_x = max(max_x, x)
                min_y = min(min_y, y)
                max_y = max(max_y, y)
    if max_x < 0:
        raise ValueError('subject is entirely transparent')
    return min_x, min_y, max_x + 1, max_y + 1


def outside_cells(width, height, pixels):
    """
    Which empty cells of the sprite grid are outside the subject rather than holes in it.

    A flood fill from the border, done at sprite resolution because that is where the answer is
    exact and cheap -- 440 cells rather than two million supersamples.

    The stroke needs this in general: a stroke that cannot tell a hole from the outside fills the gaps
    between a tower's legs with white and turns an open frame into a solid plinth.

    Measured on the sprite as drawn, though, it currently suppresses nothing -- all 186 transparent
    cells are reachable from the border, so there are no enclosed holes at all. What actually keeps
    graph paper visible between the legs is that the gaps are wider than twice STROKE. This stays
    because the moment a drawing grows a closed opening -- a window in the headframe, a ring, a
    counterweight with a hole in it -- the stroke needs it, and finding that out from a rendered badge
    is much slower than keeping fifteen lines.
    """
    outside = [[False] * width for _ in range(height)]
    stack = []
    for x in range(width):
        stack.append((x, 0))
        stack.append((x, height - 1))
    for y in range(height):
        stack.append((0, y))
        stack.append((width - 1, y))

    while stack:
        x, y = stack.pop()
        if not (0 <= x < width and 0 <= y < height):
            continue
        if outside[y][x] or pixels[y][x][3] > 0:
            continue
        outside[y][x] = True
        stack.extend(((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)))
    return outside


def place_sprite():
    """
    Blows the subject up to badge scale.

    Returns the supersampled colour buffer and, alongside it, a mask of the samples that lie outside
    the subject's silhouette -- everything beyond its edge, but not the holes within it.
    """
    width, height, pixels = subject_sprite()
    min_x, min_y, max_x, max_y = opaque_bounds(width, height, pixels)
    outside = outside_cells(width, height, pixels)

    drawn_width = (max_x - min_x) * SPRITE_SCALE
    drawn_height = (max_y - min_y) * SPRITE_SCALE
    left = CX - drawn_width / 2.0 - min_x * SPRITE_SCALE
    top = CY - drawn_height / 2.0 - min_y * SPRITE_SCALE

    step = SPRITE_SCALE * SS
    buffer = [None] * (N * N)
    # Anything the sprite grid does not cover at all is outside it, by definition.
    strokeable = bytearray(b'\x01') * (N * N)
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[y][x]
            packed = (r, g, b) if a else None
            x0 = int(round((left + x * SPRITE_SCALE) * SS))
            y0 = int(round((top + y * SPRITE_SCALE) * SS))
            for gy in range(max(0, y0), min(N, y0 + step)):
                row = gy * N
                for gx in range(max(0, x0), min(N, x0 + step)):
                    if packed is not None:
                        buffer[row + gx] = packed
                    elif not outside[y][x]:
                        strokeable[row + gx] = 0
    return buffer, strokeable


def outline_distance(buffer, reach):
    """
    Chamfer distance from the subject, in supersampled pixels, so the white stroke can be taken as a
    band around it. Two sweeps, which is plenty for so short a reach.
    """
    far = float(reach + 2)
    distance = [0.0 if cell is not None else far for cell in buffer]
    straight, diagonal = 1.0, 1.41421356

    for y in range(N):
        row = y * N
        previous = row - N
        for x in range(N):
            index = row + x
            best = distance[index]
            if best == 0.0:
                continue
            if x > 0:
                best = min(best, distance[index - 1] + straight)
            if y > 0:
                best = min(best, distance[previous + x] + straight)
                if x > 0:
                    best = min(best, distance[previous + x - 1] + diagonal)
                if x < N - 1:
                    best = min(best, distance[previous + x + 1] + diagonal)
            distance[index] = best

    for y in range(N - 1, -1, -1):
        row = y * N
        following = row + N
        for x in range(N - 1, -1, -1):
            index = row + x
            best = distance[index]
            if best == 0.0:
                continue
            if x < N - 1:
                best = min(best, distance[index + 1] + straight)
            if y < N - 1:
                best = min(best, distance[following + x] + straight)
                if x < N - 1:
                    best = min(best, distance[following + x + 1] + diagonal)
                if x > 0:
                    best = min(best, distance[following + x - 1] + diagonal)
            distance[index] = best

    return distance


def background(x, y):
    """The graph-paper field at one point, before the subject is laid over it."""
    glow = math.hypot(x - (CX + GLOW_DX), y - (CY + GLOW_DY)) / (RADIUS * 1.55)
    colour = lerp(FIELD_LIGHT, FIELD, min(1.0, glow))
    distance = math.hypot(x - CX, y - CY)
    rim = min(1.0, max(0.0, (distance / RADIUS - 0.55) / 0.45)) ** 1.4
    colour = lerp(colour, FIELD_DEEP, rim)
    for coordinate in (x, y):
        offset = abs(((coordinate + GRID_SPACING / 2.0) % GRID_SPACING) - GRID_SPACING / 2.0)
        if offset < GRID_HALF_WIDTH:
            colour = lerp(colour, GRID, GRID_ALPHA)
    return colour


def render():
    buffer, strokeable = place_sprite()
    reach = STROKE * SS
    distance = outline_distance(buffer, reach)

    shadow_dx = int(round(SHADOW_DX * SS))
    shadow_dy = int(round(SHADOW_DY * SS))
    inner = RADIUS - RING

    rows = []
    samples = SS * SS
    for py in range(OUT):
        row = []
        for px in range(OUT):
            r = g = b = a = 0.0
            for sy in range(SS):
                gy = py * SS + sy
                y = (gy + 0.5) / SS
                for sx in range(SS):
                    gx = px * SS + sx
                    x = (gx + 0.5) / SS

                    from_centre = math.hypot(x - CX, y - CY)
                    if from_centre > RADIUS:
                        continue
                    if from_centre > inner:
                        colour = WHITE
                    else:
                        index = gy * N + gx
                        cell = buffer[index]
                        if cell is not None:
                            colour = (float(cell[0]), float(cell[1]), float(cell[2]))
                        elif distance[index] <= reach and strokeable[index]:
                            colour = WHITE
                        else:
                            colour = background(x, y)
                            sx0, sy0 = gx - shadow_dx, gy - shadow_dy
                            if 0 <= sx0 < N and 0 <= sy0 < N:
                                cast = sy0 * N + sx0
                                if buffer[cast] is not None or distance[cast] <= reach:
                                    colour = lerp(colour, SHADOW, SHADOW_ALPHA)

                    r += colour[0]
                    g += colour[1]
                    b += colour[2]
                    a += 1.0

            if a <= 0.0:
                row.append((0, 0, 0, 0))
                continue
            row.append((
                int(round(min(255.0, r / a))),
                int(round(min(255.0, g / a))),
                int(round(min(255.0, b / a))),
                int(round(255.0 * a / samples)),
            ))
        rows.append(row)
    return rows


def write_png(path, rows):
    height, width = len(rows), len(rows[0])
    raw = b''.join(b'\x00' + b''.join(struct.pack('BBBB', *p) for p in row) for row in rows)

    def chunk(kind, data):
        return (struct.pack('>I', len(data)) + kind + data
                + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff))

    png = (b'\x89PNG\r\n\x1a\n'
           + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
           + chunk(b'IDAT', zlib.compress(raw, 9))
           + chunk(b'IEND', b''))
    directory = os.path.dirname(path)
    if directory:
        os.makedirs(directory, exist_ok=True)
    with open(path, 'wb') as handle:
        handle.write(png)
    return len(png)


def main():
    arguments = sys.argv[1:]
    size = REFERENCE
    if '--size' in arguments:
        at = arguments.index('--size')
        if at + 1 >= len(arguments):
            raise SystemExit('--size needs a value: %s' % USAGE)
        try:
            size = int(arguments[at + 1])
        except ValueError:
            raise SystemExit('--size wants a whole number, got %r: %s'
                             % (arguments[at + 1], USAGE))
        del arguments[at:at + 2]
    target = arguments[0] if arguments else 'src/main/resources/createpneumatictools_icon.png'

    configure(size)
    written = write_png(target, render())
    print('wrote {} ({}x{}, {} bytes)'.format(target, OUT, OUT, written))


if __name__ == '__main__':
    main()
