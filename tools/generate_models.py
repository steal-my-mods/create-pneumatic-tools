#!/usr/bin/env python3
"""
Builds the 3D item models for every tool: one static base model each, plus the moving parts the
renderer animates.

Why these are generated rather than drawn in Blockbench, which is what everyone else uses: nine tools
share one chassis, and the whole point of a shared chassis is that it cannot drift. A grip one pixel
thicker on the saw than on the drill is invisible while you are drawing it and glaring when the nine
sit in a row in a creative tab. Written down once, the chassis is one constant. It also means the
mounting offsets the *renderer* needs are the same numbers that laid the geometry out, rather than
two sets of measurements taken off each other -- and `check_placements` fails the build if they stop
agreeing.

## Conventions, every one of which the renderer depends on

**The tool points along +Y, its grip hangs toward -Z, and +X is across it.** That is an odd-looking
frame until you see where it comes from: vanilla's `crossbow` display transforms hold an item pointing
*away from the player*, and they are written for a model whose forward direction is +Y. Reusing them
is what makes these read as tools you aim rather than tools you wave — an earlier pass authored them
along a diagonal like a pickaxe, and the result was a drill held up beside your ear with the bit
pointing at the sky.

So: +Y is the muzzle, +Z is up, -Z is where the pistol grip hangs, and the moving part is at the front
where the work happens.

**Every moving part is authored centred on (8, 8, 8) and extends along +Y.** The renderer's pose
origin is the middle of the item, so a part centred there draws centred on the origin, which makes
spinning it one call with nothing to undo. It is then translated out along the barrel. Author a part
anywhere else and it orbits instead of spinning.

**The barrel is above the middle of the model**, because the grip has to hang below it. So every mount
carries an `up` as well as a `forward`, and a part with `up` left at zero sits inside the grip.

**Round things are built the way the badge is.** A disc is one box per row of a pixel circle, with
equal neighbouring rows merged. Overlapping rotated squares were tried first and are not round: a
square's corners reach 1.41 times its half-width, so their union is a star with an outer radius half
again its inner one.

**UVs come from each face's own measurements**, so texels stay square whatever a box measures. There
is no hand-placed UV anywhere here; the material textures are flat panels made for exactly that.

    python3 tools/generate_models.py
"""

import json
import math
import os
import re
import sys

MODELS = 'src/main/resources/assets/createpneumatictools/models/item'
JAVA_PARTIALS = 'src/main/java/com/createpneumatictools/client/CPTPartials.java'
NAMESPACE = 'createpneumatictools'

C = 8.0            # the middle of the model, and the centre every part is authored around
BARREL_UP = 2.5    # how far above the middle the barrel's axis sits, to leave room for the grip


# --- boxes ---------------------------------------------------------------------------------------


def box(bounds, material, rotation=None):
    """One cuboid as (x0, y0, z0, x1, y1, z1), with UVs derived from its own measurements."""
    x0, y0, z0, x1, y1, z1 = bounds
    width, height, depth = x1 - x0, y1 - y0, z1 - z0
    element = {
        'from': [r(x0), r(y0), r(z0)],
        'to': [r(x1), r(y1), r(z1)],
        'faces': {
            'north': uv(width, height, material),
            'south': uv(width, height, material),
            'east': uv(depth, height, material),
            'west': uv(depth, height, material),
            'up': uv(width, depth, material),
            'down': uv(width, depth, material),
        },
    }
    if rotation:
        element['rotation'] = rotation
    return element


# Which coordinate runs along each axis, and which two are perpendicular to it.
PERPENDICULAR = {'x': (1, 2), 'y': (0, 2), 'z': (0, 1)}
AXIAL = {'x': 0, 'y': 1, 'z': 2}


def r(value):
    return round(value + 0.0, 4)


def uv(width, height, material):
    # A face's UV is its own footprint, so one flat panel texture serves every box at the same texel
    # density however large the box is.
    return {'uv': [0, 0, r(min(16.0, width)), r(min(16.0, height))], 'texture': '#' + material}


def boxes(specification):
    return [box(bounds, material) for bounds, material in specification]


def spun(origin_axis, angle):
    return {'origin': [C, C, C], 'axis': origin_axis, 'angle': angle}


# --- discs ---------------------------------------------------------------------------------------


def bands(outer, inner=0.0, step=0.5):
    """
    The rows of a pixel circle as (start, end, half_width, hole_half_width) offsets around zero.

    One row per unit of diameter, its half-width snapped to `step` so the edge stays chunky rather
    than smooth, and neighbouring rows of equal width merged into one box.
    """
    found = []
    for index in range(math.floor(-outer), int(math.ceil(outer))):
        centre = index + 0.5
        if abs(centre) > outer:
            continue
        half = snap(math.sqrt(max(0.0, outer * outer - centre * centre)), step)
        hole = snap(math.sqrt(max(0.0, inner * inner - centre * centre)), step) if inner else 0.0
        if half <= 0 or hole >= half:
            continue
        if found and found[-1][1] == index and found[-1][2] == half and found[-1][3] == hole:
            found[-1] = (found[-1][0], index + 1, half, hole)
        else:
            found.append((index, index + 1, half, hole))
    return found


def snap(value, step):
    return round(value / step) * step


def disc(outer, half_depth, plane, material, inner=0.0, step=0.5):
    """
    A disc, or a ring with `inner`, built as one box per merged row of a pixel circle.

    `plane` is the plane it lies in, named for its two spanning axes: 'yz' is a saw blade standing up
    along the barrel, whose axis is X; 'xz' is an impeller across the bore, whose axis is Y.
    """
    elements = []
    for start, end, half, hole in bands(outer, inner, step):
        runs = [(-half, half)] if hole <= 0 else [(-half, -hole), (hole, half)]
        for low, high in runs:
            if plane == 'yz':
                bounds = (C - half_depth, C + start, C + low, C + half_depth, C + end, C + high)
            elif plane == 'xz':
                bounds = (C + start, C - half_depth, C + low, C + end, C + half_depth, C + high)
            else:
                bounds = (C + low, C + start, C - half_depth, C + high, C + end, C + half_depth)
            elements.append(box(bounds, material))
    return elements


def tube(axis, low, high, outer, inner, material, centre=(C, C)):
    """
    A square annulus around an axis: four boxes, a hole down the middle.

    Cuboids cannot be hollow, so anything a moving part has to sit inside has to be built as walls. A
    solid box in the same place looks identical from outside and swallows the part whole.

    `centre` is where the hole goes, in the two directions perpendicular to `axis`. It is a parameter
    and not the middle of the model because the barrel is not the middle of the model: a flare built
    around (8, 8) has its bore two and a half pixels below the bore it is supposed to continue.
    """
    first, second = PERPENDICULAR[axis]
    walls = []
    spans = [
        # two full-width walls, then two shorter ones between them, so the corners are not doubled
        ((-outer, outer), (inner, outer)),
        ((-outer, outer), (-outer, -inner)),
        ((inner, outer), (-inner, inner)),
        ((-outer, -inner), (-inner, inner)),
    ]
    for (a0, a1), (b0, b1) in spans:
        bounds = [0.0] * 6
        bounds[AXIAL[axis]] = low
        bounds[AXIAL[axis] + 3] = high
        bounds[first] = centre[0] + a0
        bounds[first + 3] = centre[0] + a1
        bounds[second] = centre[1] + b0
        bounds[second + 3] = centre[1] + b1
        walls.append(box(tuple(bounds), material))
    return walls


def ring(axis, low, high, outer, inner, material, centre, step=1.0):
    """
    One round course of a tube: a pixel-circle annulus, set across `axis` between `low` and `high`.

    The square version this replaces read as a picture frame rather than as the mouth of a nozzle. A
    coarse step keeps the box count down -- a ring is one box per run per row, and a smooth one at
    this diameter is sixty boxes for a part nobody looks at closely.
    """
    first, second = PERPENDICULAR[axis]
    made = []
    for element in disc(outer, 0.5, plane_of(axis), material, inner=inner, step=step):
        bounds = [0.0] * 6
        bounds[AXIAL[axis]] = low
        bounds[AXIAL[axis] + 3] = high
        for offset, index in ((0, first), (3, first + 3)):
            bounds[index] = element['from' if offset == 0 else 'to'][first] + centre[0] - C
        for offset, index in ((0, second), (3, second + 3)):
            bounds[index] = element['from' if offset == 0 else 'to'][second] + centre[1] - C
        made.append(box(tuple(bounds), material))
    return made


def plane_of(axis):
    """The name `disc` uses for the plane perpendicular to `axis`."""
    return {'x': 'yz', 'y': 'xz', 'z': 'xy'}[axis]


def toothed(elements, radius, half_depth, material, tooth=0.5, stickout=0.6):
    """
    Eight teeth around a blade: one box each, poking out past the rim at the compass points and the
    diagonals.

    An earlier version drew four bars laid right across the disc, which is two teeth per box and half
    the geometry. It also z-fought across the entire blade: a bar the same thickness as the disc
    shares its face planes everywhere they overlap, and the depth test has no way to choose between
    them. Separate teeth, set slightly thinner than the blade, overlap only the outermost row and
    share no plane with anything.

    The diagonals come from the same four boxes turned 45 degrees, which is one of the five angles a
    model element is allowed to carry. The teeth only show while the saw is standing still, which is
    exactly when a bare disc in a hotbar would read as a grinding wheel.
    """
    depth = half_depth * 0.7
    root = radius - 0.4
    for angle in (0, 45):
        for bounds in (
            (C - depth, C + root, C - tooth, C + depth, C + radius + stickout, C + tooth),
            (C - depth, C - radius - stickout, C - tooth, C + depth, C - root, C + tooth),
            (C - depth, C - tooth, C + root, C + depth, C + tooth, C + radius + stickout),
            (C - depth, C - tooth, C - radius - stickout, C + depth, C + tooth, C - root),
        ):
            elements.append(box(bounds, material, rotation=spun('x', angle) if angle else None))
    return elements


# --- the chassis ---------------------------------------------------------------------------------
#
# Read along +Y, from the butt of the tool to the muzzle: rear cap, brass body with a pistol grip
# hanging under it, copper collar, gunmetal chuck. Everything past y=11.5 is the tool's own head. The
# barrel's axis is at x=8, z=8+BARREL_UP; the grip hangs below it, which is what makes the silhouette
# a tool you hold rather than a rod you wave.

CHASSIS = [
    ((6.5, 0.5, 8.5, 9.5, 1.5, 12.5), 'andesite'),      # rear cap
    ((5.5, 1.5, 8.0, 10.5, 8.5, 13.0), 'brass'),        # body
    ((6.0, 2.5, 2.5, 10.0, 6.5, 8.0), 'andesite'),      # pistol grip
    ((6.25, 1.75, 1.25, 9.75, 6.75, 2.5), 'copper'),    # hose fitting, under the grip where it belongs
    ((7.0, 6.5, 6.5, 9.0, 7.5, 8.0), 'steel'),          # trigger
    ((5.0, 8.5, 7.5, 11.0, 10.0, 13.5), 'copper'),      # collar
    ((6.0, 10.0, 8.5, 10.0, 11.5, 12.5), 'gunmetal'),   # chuck
]

# --- the details ----------------------------------------------------------------------------------
#
# The chassis above is the shape; this is everything that stops it being four smooth boxes. All of it
# is proud of a surface rather than flush with one, and inset from the edges it sits on, because a
# detail that shares a face plane with what it is attached to z-fights against it -- which is what
# `check_coplanar` is there to catch. Read it as: a ribbed motor housing, a bolted-on back plate, a
# guarded trigger, a knurled grip, an exhaust port, and a hose that goes somewhere.

DETAILS = [
    # Cooling ribs across the top of the motor housing. Three, not five: at sixteen pixels a fourth
    # rib is a texture, and a texture is what the panel already provides.
    ((5.7, 2.6, 12.8, 10.3, 3.4, 13.5), 'gunmetal'),
    ((5.7, 4.1, 12.8, 10.3, 4.9, 13.5), 'gunmetal'),
    ((5.7, 5.6, 12.8, 10.3, 6.4, 13.5), 'gunmetal'),
    # Bolt heads at the corners of the rear cap. One pixel each, which is as small as a thing can be
    # and still read as hardware.
    ((6.7, 0.2, 8.7, 7.3, 0.8, 9.3), 'steel'),
    ((8.7, 0.2, 8.7, 9.3, 0.8, 9.3), 'steel'),
    ((6.7, 0.2, 11.7, 7.3, 0.8, 12.3), 'steel'),
    ((8.7, 0.2, 11.7, 9.3, 0.8, 12.3), 'steel'),
    # A trigger guard: a loop under the trigger, which is the one detail that makes the grip read as
    # a pistol grip rather than as a handle.
    ((6.8, 7.4, 5.4, 9.2, 8.0, 6.6), 'gunmetal'),
    ((6.8, 6.3, 5.4, 9.2, 7.4, 6.0), 'gunmetal'),
    # Knurling on the grip: two shallow bands, so the hand-hold has a texture at a glance.
    ((5.85, 3.2, 3.0, 10.15, 3.8, 7.5), 'gunmetal'),
    ((5.85, 4.6, 3.0, 10.15, 5.2, 7.5), 'gunmetal'),
    # An exhaust port on the left flank, where a real air tool vents.
    ((5.2, 6.4, 9.0, 5.6, 7.8, 12.0), 'gunmetal'),
    # A pressure gauge on the right flank. Small: at a bezel any wider the pale face stops reading as
    # a dial and starts reading as a window cut in the side of the tool.
    ((10.4, 5.7, 9.9, 10.85, 7.3, 11.5), 'gunmetal'),
    ((10.85, 6.1, 10.3, 11.0, 6.9, 11.1), 'pad'),
    # The air line: a ferrule and two courses of hose, so it leaves the tool rather than stopping.
    ((6.6, 1.2, 1.4, 9.4, 1.7, 2.35), 'brass'),
    ((6.9, 0.4, 1.5, 9.1, 1.2, 2.3), 'copper'),
]

# --- the heads -----------------------------------------------------------------------------------
#
# Only the static part of each head is here; anything that moves is a part. The nose rings matter more
# than they look: a moving part socketed into a ring reads as mounted, and the same part floating off
# the end of the chuck reads as a bug.

HEADS = {
    'hand_drill': [
        ((6.5, 11.5, 9.0, 9.5, 12.5, 12.0), 'gunmetal'),
    ],
    'pneumatic_jackhammer': [
        # Squat and heavy where the drill is slim: at sixteen pixels that is the whole difference
        # between the two, so the head is deliberately the widest thing on the tool. Stepped in three
        # courses rather than cast as one slab, with tie bolts down the flanks.
        ((5.0, 10.0, 7.5, 11.0, 12.0, 13.5), 'gunmetal'),
        ((5.6, 12.0, 8.1, 10.4, 13.2, 12.9), 'gunmetal'),
        ((6.4, 13.2, 8.9, 9.6, 13.9, 12.1), 'gunmetal'),
        ((5.3, 10.4, 13.5, 10.7, 11.6, 13.9), 'steel'),
        ((5.3, 10.4, 7.1, 10.7, 11.6, 7.5), 'steel'),
    ],
    'tunnel_drill': [
        # One wide plate carrying three bits, so the tool reads as three times the drill -- and wide
        # and deep enough that every bit's swept circle is inside it. An off-centre spinning shaft in
        # a plate that only just covers it shows its corners through the face four times a turn.
        ((2.5, 10.0, 6.0, 13.5, 13.0, 15.0), 'gunmetal'),
    ],
    # The three wheeled tools carry their disc *beside* the barrel on a stub spindle, the way a
    # circular saw and an angle grinder both do. Mounted on the centre line the disc's own radius
    # reaches back through the body, the collar and the trigger, and a wheel that cuts through the
    # tool it is bolted to is the clipping this arrangement exists to avoid.
    'pneumatic_saw': [
        ((6.5, 11.5, 9.0, 10.5, 12.5, 12.0), 'gunmetal'),
        ((10.5, 10.25, 9.5, 11.65, 11.75, 11.5), 'gunmetal'),   # spindle, out to meet the blade
    ],
    'pneumatic_grinder': [
        ((6.5, 11.5, 9.0, 10.5, 12.5, 12.0), 'gunmetal'),
        ((10.5, 10.5, 9.5, 11.1, 12.0, 11.5), 'gunmetal'),
    ],
    'pneumatic_buffer': [
        ((6.5, 11.5, 9.0, 10.5, 12.5, 12.0), 'gunmetal'),
        ((10.5, 10.5, 9.5, 11.25, 12.0, 11.5), 'gunmetal'),
    ],
    # Filled in below: a trumpet has to be hollow, or the impeller is a moving part nobody can see.
    'pneumatic_vacuum_wand': [],
    'pneumatic_wrench': [
        # Small enough that its corners clear the socket's bore: at half an inch wider the four
        # corners of a square nose poke through the inside of a spinning ring.
        ((6.5, 11.5, 9.0, 9.5, 12.0, 12.0), 'gunmetal'),
    ],
}

# Heads that are built rather than listed. `HEADS` holds plain boxes; anything that needs a helper --
# so far only the vacuum's flare, which has to be hollow -- is assembled here and merged in by
# `static_of`. The bore of each course has to clear the impeller's swept circle, which is what sets
# the inner radii.
BORE = (C, C + BARREL_UP)   # where the barrel's bore is, in x and z

BUILT_HEADS = {
    'pneumatic_vacuum_wand': ring('y', 11.5, 13.0, 3.0, 2.0, 'steel', BORE)
                             + ring('y', 13.0, 14.5, 4.0, 3.0, 'steel', BORE)
                             + ring('y', 14.5, 15.5, 5.0, 4.0, 'steel', BORE),
}


# Tools whose own head takes the place of the shared chuck: the tunneller, whose three bits are spread
# far wider than a chuck that reaches only the middle one, and the jackhammer, whose head is a bigger
# block in the same place.
NO_CHUCK = {'tunnel_drill', 'pneumatic_jackhammer'}
CHUCK = CHASSIS[-1]


def static_of(tool):
    """Every static element of one tool: the shared chassis and its details, its head, and any built."""
    chassis = [part for part in CHASSIS if not (tool in NO_CHUCK and part is CHUCK)]
    return boxes(chassis + DETAILS + HEADS[tool]) + BUILT_HEADS.get(tool, [])

# --- the moving parts ------------------------------------------------------------------------------
#
# Authored centred on (8, 8, 8) and lying along +Y, so the renderer can spin one about the barrel with
# a single rotation and nothing to undo.

PARTS = {
    # A twist bit: shaft, taper, point. Used by the Hand Drill and, three at a time, by the tunneller.
    # The tail starts at y=6.5 for two reasons, half a pixel apart. Any further back and, mounted, it
    # reaches into the collar, where an off-centre bit on the tunneller pokes out through the side.
    # Any further forward -- flush with the back of the chuck, which is where it was -- and the two
    # faces sit at the same depth pointing the same way, and the whole back of the tool shimmers.
    'bit': boxes([
        ((7.0, 6.5, 7.0, 9.0, 10.5, 9.0), 'steel'),
        ((7.5, 10.5, 7.5, 8.5, 11.5, 8.5), 'steel'),
        ((7.75, 11.5, 7.75, 8.25, 12.0, 8.25), 'steel'),
    ]),
    # A chisel, not a bit: blunt, and obsidian for most of its length so the difference survives being
    # seen at sixteen pixels.
    'chisel': boxes([
        ((6.5, 4.0, 6.5, 9.5, 8.0, 9.5), 'gunmetal'),
        ((6.75, 8.0, 6.25, 9.25, 10.0, 9.75), 'obsidian'),
        ((7.25, 10.0, 7.0, 8.75, 11.0, 9.0), 'obsidian'),
        ((7.5, 11.0, 7.5, 8.5, 11.5, 8.5), 'obsidian'),
    ]),
    # Standing up along the barrel, so the cutting edge is at the front where you are pushing it.
    'blade': toothed(disc(4.0, 0.35, 'yz', 'steel'), 4.0, 0.35, 'steel', stickout=0.5),
    'wheel': disc(4.0, 0.9, 'yz', 'grit'),
    'buffing_pad': disc(4.0, 1.25, 'yz', 'pad'),
    # Across the bore, spinning about the barrel: an impeller you look straight into.
    'impeller': disc(3.0, 0.4, 'xz', 'steel'),
    # A ring, not a block: a socket that is not hollow is a hammer. The flange behind it is what makes
    # the bore readable from the side, which is the angle a hotbar shows it from.
    'socket': disc(4.5, 1.0, 'xz', 'gunmetal', inner=3.0) \
        + [box((b['from'][0], b['from'][1] + 2.0, b['from'][2],
                b['to'][0], b['to'][1] + 2.0, b['to'][2]), 'steel')
           for b in disc(3.0, 1.0, 'xz', 'steel', inner=1.5)],
}

# --- where the moving parts are mounted -----------------------------------------------------------
#
# (part, forward along the barrel, across it, up from the middle of the model). Almost everything sits
# on the barrel's own axis at BARREL_UP; the tunneller's three bits do not, which is the whole reason
# `up` is per-mount. These numbers also live in CPTPartials.java, and `check_placements` fails the
# build if the two disagree.

PLACEMENTS = {
    'hand_drill': [('bit', 4.0, 0.0, BARREL_UP)],
    'pneumatic_jackhammer': [('chisel', 4.0, 0.0, BARREL_UP)],
    # A triangle, not a row. Three bits abreast are three bits you cannot see: the icon is a side view,
    # so a row across the tool collapses into one. Staggered, the tool is visibly more than one drill
    # from every angle.
    'tunnel_drill': [('bit', 4.0, -3.0, BARREL_UP - 2.0), ('bit', 4.0, 3.0, BARREL_UP - 2.0),
                     ('bit', 4.0, 0.0, BARREL_UP + 2.0)],
    # Out to the side, clear of the barrel: a wheel on the centre line saws through the tool.
    'pneumatic_saw': [('blade', 3.0, 4.0, BARREL_UP)],
    'pneumatic_grinder': [('wheel', 3.0, 4.0, BARREL_UP)],
    'pneumatic_buffer': [('buffing_pad', 3.0, 4.5, BARREL_UP)],
    # Right at the mouth, in the widest course of the flare: the only place its swept circle clears
    # the bore, and the only place you can see it turning anyway.
    'pneumatic_vacuum_wand': [('impeller', 7.0, 0.0, BARREL_UP)],
    'pneumatic_wrench': [('socket', 4.5, 0.0, BARREL_UP)],
}


# --- display transforms ----------------------------------------------------------------------------
#
# Vanilla's `crossbow`, not its `handheld`. A crossbow is the one vanilla item held pointing away from
# the player, and its transforms are written for a model whose forward direction is +Y -- which is why
# these are authored that way. The values are vanilla's, with the hand scales taken down by BULK and
# the icon replaced by a plain side view.
#
# The Z term is a yaw, not a roll: with the rotation composed as Rx * Ry * Rz, the Z rotation is
# applied to the model first, about its own up axis. Its *sign* decides whether the working end swings
# into view or out of it. Negative turns the muzzle away from the crosshair, and since the right hand
# already sits at the right edge of the screen, that hides the head behind the barrel and the tool
# animates where nobody can see it. Positive brings the head round toward the middle, which is the
# three-quarter view every first-person game holds a tool at, and for the same reason.
#
# The translations are not vanilla's either, and the Z is the reason. A sprite is one pixel deep, so
# it sits wherever it is put; this model is sixteen long, and after the tip forward half of that is
# *behind* the pivot -- pointing at the camera. At vanilla's +1.13 the butt of the tool fills the
# corner of the screen and you never see the business end. Pushing it away is what puts the muzzle in
# view, which is the entire point of holding it this way.

# How much smaller than a sprite a solid tool has to be drawn in the hand. Vanilla's scales are
# written for something 16x16x1; these are 5x16x13, and at full size the muzzle fills the screen.
BULK = 0.7

DISPLAY = {
    'thirdperson_righthand': {'rotation': [-90, 0, 25], 'translation': [0, 2.5, -3.5],
                              'scale': [0.9 * BULK] * 3},
    'thirdperson_lefthand': {'rotation': [-90, 0, -25], 'translation': [0, 2.5, -3.5],
                             'scale': [0.9 * BULK] * 3},
    'firstperson_righthand': {'rotation': [-90, 0, 30], 'translation': [0.6, 3.0, -3.0],
                              'scale': [0.68 * BULK] * 3},
    'firstperson_lefthand': {'rotation': [-90, 0, -30], 'translation': [0.6, 3.0, -3.0],
                             'scale': [0.68 * BULK] * 3},
    # Three-quarters: muzzle to the right, grip below, turned far enough to show the ribbed top and
    # the near flank. Create draws every one of its own 3D items this way -- the Extendo Grip, the
    # Potato Cannon and the Wrench all carry a gui rotation on all three axes -- and a flat elevation
    # among them reads as the one item that forgot to have depth. Filled in by `gui_view` below,
    # because the numbers are a composition rather than something worth typing.
    'gui': {'rotation': None, 'translation': [0, 0, 0], 'scale': [1.0, 1.0, 1.0]},
    'ground': {'rotation': None, 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    'fixed': {'rotation': None, 'translation': [0, 0, 0], 'scale': [1.0, 1.0, 1.0]},
    'head': {'rotation': None, 'translation': [0, 13, 7], 'scale': [1.0, 1.0, 1.0]},
}

# How far the icon is turned off a plain side view: yaw first, then a look down from above.
GUI_YAW = -35.0
GUI_PITCH = 18.0


def matmul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def axis_matrix(axis, degrees):
    radians = math.radians(degrees)
    cos, sin = math.cos(radians), math.sin(radians)
    if axis == 'x':
        return [[1, 0, 0], [0, cos, -sin], [0, sin, cos]]
    if axis == 'y':
        return [[cos, 0, sin], [0, 1, 0], [-sin, 0, cos]]
    return [[cos, -sin, 0], [sin, cos, 0], [0, 0, 1]]


def gui_view():
    """
    The icon's rotation, as the Euler triple a model file wants.

    Built rather than typed because it is a composition: the side view that puts the muzzle to the
    right and the grip below, then a yaw and a pitch applied *on top of it* in screen space. Adding
    those to the side view's own terms would not do -- a model file's rotation composes as
    Rx * Ry * Rz, so the terms turn the model about its own axes, and adding to the middle one rolls
    the tool about its barrel instead of turning it to face you.
    """
    side = matmul(matmul(axis_matrix('x', -90), axis_matrix('y', 0)), axis_matrix('z', -90))
    turned = matmul(matmul(axis_matrix('x', GUI_PITCH), axis_matrix('y', GUI_YAW)), side)
    triple = euler_xyz(turned)

    # Extraction from a rotation matrix is exactly the sort of arithmetic that produces a plausible
    # wrong answer -- there are two conventions, they differ only in the order of two multiplications,
    # and both give a tool held at a believable angle. Rebuilding the matrix from the answer and
    # comparing costs nine multiplications and turns "looks a bit off" into a failed build.
    rebuilt = matmul(matmul(axis_matrix('x', triple[0]), axis_matrix('y', triple[1])),
                     axis_matrix('z', triple[2]))
    error = max(abs(turned[i][j] - rebuilt[i][j]) for i in range(3) for j in range(3))
    if error > 1e-4:
        raise SystemExit('the icon rotation does not survive being decomposed to Euler angles '
                         '(error %.6f) -- euler_xyz and the composition disagree about the order'
                         % error)
    return triple


def euler_xyz(rotation):
    """Inverts Rx * Ry * Rz, which is how a model file's rotation is composed."""
    y = math.asin(max(-1.0, min(1.0, rotation[0][2])))
    x = math.atan2(-rotation[1][2], rotation[2][2])
    z = math.atan2(-rotation[0][1], rotation[0][0])
    return [round(math.degrees(v), 2) + 0.0 for v in (x, y, z)]


def display():
    """The transforms above, with the icon scaled so no tool hangs outside its slot."""
    out = {}
    for context, entry in DISPLAY.items():
        factor = GUI_SCALE if context in ('gui', 'fixed', 'head') else 1.0
        rotation = gui_view() if entry['rotation'] is None else list(entry['rotation'])
        out[context] = {'rotation': rotation,
                        'translation': list(entry['translation']),
                        'scale': [round(v * factor, 4) for v in entry['scale']]}
    return out


def gui_scale():
    """
    The largest the icon can be drawn without any of it hanging outside the slot.

    One factor for all of them, so they are drawn at the same size -- a per-tool scale would fit each
    perfectly and make the saw visibly bigger than the drill. The icon is a plain side view, so the
    measurement is just how far the geometry reaches from the middle in Y and Z.
    """
    rotation = matmul(matmul(axis_matrix('x', GUI_PITCH), axis_matrix('y', GUI_YAW)),
                      matmul(matmul(axis_matrix('x', -90), axis_matrix('y', 0)),
                             axis_matrix('z', -90)))
    worst = 0.0
    for tool in HEADS:
        for point in points_of(tool):
            delta = [point[i] - C for i in range(3)]
            turned = [sum(rotation[i][j] * delta[j] for j in range(3)) for i in range(3)]
            worst = max(worst, abs(turned[0]), abs(turned[1]))
    return math.floor(800.0 / worst) / 100.0


# --- clearance ------------------------------------------------------------------------------------
#
# Which axis each tool's part turns about, matching the Motion in CPTRenderers: SPIN_AXIAL turns about
# the barrel (Y), SPIN_FACE about a spindle across it (X), and HAMMER does not turn at all.

# The axis a hammering part travels along. Only the jackhammer's chisel does, and it runs up the
# barrel like everything else.
HAMMER_AXIS = 'y'

SPIN = {
    'hand_drill': 'y',
    'pneumatic_jackhammer': None,
    'tunnel_drill': 'y',
    'pneumatic_saw': 'x',
    'pneumatic_grinder': 'x',
    'pneumatic_buffer': 'x',
    'pneumatic_vacuum_wand': 'y',
    'pneumatic_wrench': 'y',
}

def radial_span(element, axis, centre):
    """
    How near and how far a box reaches from a rotation axis, and how near its nearest *face* is.

    The third number is what separates a housing from an obstruction. A box the axis runs through has
    a nearest distance of zero either way; what says whether a spinning part inside it is hidden or
    is about to burst out through the side is the distance to the closest face.
    """
    first, second = PERPENDICULAR[axis]
    low = [min(c[i] for c in corners(element)) for i in range(3)]
    high = [max(c[i] for c in corners(element)) for i in range(3)]
    spans = [(low[first] - centre[0], high[first] - centre[0]),
             (low[second] - centre[1], high[second] - centre[1])]
    # Nearest point of the rectangle to the axis, which is zero on an axis it contains.
    near = [0.0 if a <= 0 <= b else min(abs(a), abs(b)) for a, b in spans]
    nearest = math.hypot(near[0], near[1])
    farthest = max(math.hypot(a, b) for a in spans[0] for b in spans[1])
    inscribed = min(max(abs(a), abs(b)) if a <= 0 <= b else 0.0 for a, b in spans)
    return nearest, farthest, inscribed


def axial_span(element, axis):
    index = AXIAL[axis]
    return (min(c[index] for c in corners(element)), max(c[index] for c in corners(element)))


def check_clearance():
    """
    Refuses geometry a moving part would visibly sweep through.

    A spinning part traces an annulus: everything between the nearest and furthest its own material
    reaches from its axis. Static geometry standing in that band gets cut through twice a turn, and
    what a player sees is the head of the tool flickering. It is not an error anywhere -- the model
    loads, the part turns -- so it can only be found by looking at the tool, at which point it reads
    as bad art rather than as arithmetic.

    Two arrangements are fine and are exempted. A housing that entirely encloses the swept circle
    hides the part rather than colliding with it, which is what a chuck is for; and anything sitting
    inside the bore of a ring-shaped part is inside the hole.
    """
    problems = []
    for tool in sorted(HEADS):
        axis = SPIN[tool]
        if axis is None:
            continue
        for name, forward, across, up in PLACEMENTS[tool]:
            offset = (across, forward, up)
            moved = [shifted(element, offset) for element in PARTS[name]]
            centre = axis_centre(axis, offset)

            for element in static_of(tool):
                low, high = axial_span(element, axis)
                # Only the part's own boxes that share this stretch of the axis can reach it. Taking
                # one band for the whole part instead reports a tapered bit's widest section against
                # geometry it never comes near, and the false alarms bury the real ones.
                overlapping = [m for m in moved
                               if axial_span(m, axis)[1] > low + 0.01
                               and axial_span(m, axis)[0] < high - 0.01]
                if not overlapping:
                    continue
                reach = [radial_span(m, axis, centre) for m in overlapping]
                inner = min(r[0] for r in reach)
                outer = max(r[1] for r in reach)

                nearest, farthest, inscribed = radial_span(element, axis, centre)
                if farthest <= inner + 0.01 or nearest >= outer - 0.01:
                    continue          # inside the bore, or clear of the whole sweep
                if inner <= 0.01 and inscribed >= outer - 0.01:
                    continue          # a housing that encloses the part completely
                problems.append(
                    '%s: the %s sweeps %.2f..%.2f from its axis where static geometry at %s reaches '
                    '%.2f..%.2f into it' % (tool, name, inner, outer, element['from'], nearest,
                                            farthest))
    if problems:
        raise SystemExit('\n'.join(sorted(set(problems))))


def shifted(element, offset):
    moved = json.loads(json.dumps(element))
    moved['from'] = [element['from'][i] + offset[i] for i in range(3)]
    moved['to'] = [element['to'][i] + offset[i] for i in range(3)]
    if 'rotation' in moved:
        moved['rotation'] = dict(element['rotation'])
        moved['rotation']['origin'] = [element['rotation']['origin'][i] + offset[i] for i in range(3)]
    return moved


def axis_centre(axis, offset):
    """Where the rotation axis sits in the two directions perpendicular to it."""
    first, second = PERPENDICULAR[axis]
    return (C + offset[first], C + offset[second])


def aabb(element):
    points = corners(element)
    return [(min(p[i] for p in points), max(p[i] for p in points)) for i in range(3)]


def check_coplanar():
    """
    Refuses two boxes that overlap *and* share a face plane.

    Two surfaces at exactly the same depth are the definition of z-fighting: the depth test cannot
    choose between them, so which one is drawn changes with the viewing angle and the surface
    shimmers. It is the other half of the clipping this file guards against, and the harder half to
    see coming -- overlapping boxes are normal and fine, and only become a problem when an edge of
    one lands exactly on an edge of the other.

    Boxes that merely touch are not overlapping and are not a problem: their coincident faces point
    in opposite directions, so only ever one of them is drawn.

    A moving part is also checked against the tool it is mounted in, but only on the axes that do not
    move. A part spinning about Y keeps its Y coordinates for ever, so a shaft whose tail ends flush
    with the back of its housing z-fights against it permanently; its X and Z faces sweep away from
    whatever they line up with and are nobody's problem. A hammering part is the other way round.
    That distinction is why this was missed the first time: the bit's tail was flush with the back of
    the chuck on every drill in the mod, and neither half of the checking saw it, because the
    clearance check exempts a housing that encloses the part and this check only looked within a
    group.
    """
    problems = []
    for name, elements in sorted(PARTS.items()):
        problems += coplanar_pairs('part %s' % name, elements, elements, (0, 1, 2))
    for tool in sorted(HEADS):
        static = static_of(tool)
        problems += coplanar_pairs('%s (static)' % tool, static, static, (0, 1, 2))
        axis = SPIN[tool]
        # Spinning keeps the axial coordinate; hammering keeps the two perpendicular ones.
        fixed = (AXIAL[axis],) if axis else PERPENDICULAR[HAMMER_AXIS]
        for part, forward, across, up in PLACEMENTS[tool]:
            moved = [shifted(element, (across, forward, up)) for element in PARTS[part]]
            problems += coplanar_pairs('%s: the %s against its housing' % (tool, part),
                                       moved, static, fixed)
    if problems:
        raise SystemExit('\n'.join(sorted(set(problems))))


def coplanar_pairs(label, ones, others, axes):
    """Every overlapping pair from the two lists that shares a face plane on one of `axes`."""
    found = []
    same = ones is others
    for first, one in enumerate(ones):
        a = aabb(one)
        for second, other in enumerate(others):
            if same and second <= first:
                continue
            b = aabb(other)
            if any(a[i][1] <= b[i][0] + 0.001 or b[i][1] <= a[i][0] + 0.001 for i in range(3)):
                continue              # touching or apart, not overlapping
            shared = [i for i in axes
                      if abs(a[i][0] - b[i][0]) < 0.001 or abs(a[i][1] - b[i][1]) < 0.001]
            if shared:
                found.append('%s: boxes at %s and %s overlap and share a face plane on %s'
                             % (label, one['from'], other['from'],
                                ''.join('xyz'[i] for i in shared)))
    return found


def check_renderers():
    """Fails if CPTRenderers no longer spins a part about the axis the clearance check assumed."""
    java = 'src/main/java/com/createpneumatictools/client/CPTRenderers.java'
    if not os.path.exists(java):
        return
    source = open(java).read()
    expected = {'y': 'SPIN_AXIAL', 'x': 'SPIN_FACE', None: 'HAMMER'}
    for tool, axis in sorted(SPIN.items()):
        constant = TOOL_CONSTANTS[tool]
        match = re.search(r'CPTItems\.%s\.get\(\), Motion\.(\w+)' % constant, source)
        if not match:
            raise SystemExit('%s: no renderer registered for %s' % (java, tool))
        if match.group(1) != expected[axis]:
            raise SystemExit('%s: %s spins as %s, but the clearance check assumed %s'
                             % (java, tool, match.group(1), expected[axis]))


TOOL_CONSTANTS = {
    'hand_drill': 'HAND_DRILL',
    'pneumatic_jackhammer': 'JACKHAMMER',
    'tunnel_drill': 'TUNNEL_DRILL',
    'pneumatic_saw': 'SAW',
    'pneumatic_grinder': 'GRINDER',
    'pneumatic_buffer': 'BUFFER',
    'pneumatic_vacuum_wand': 'VACUUM_WAND',
    'pneumatic_wrench': 'WRENCH',
}


# --- bounds ------------------------------------------------------------------------------------


def corners(element):
    """The eight corners of one element, with its own rotation already applied."""
    low, high = element['from'], element['to']
    found = [(x, y, z) for x in (low[0], high[0]) for y in (low[1], high[1])
             for z in (low[2], high[2])]
    rotation = element.get('rotation')
    if not rotation:
        return found
    return [turn(p, rotation['origin'], rotation['axis'], rotation['angle']) for p in found]


def turn(point, origin, axis, degrees):
    """One point rotated about one axis, the way the game applies an element's own rotation."""
    radians = math.radians(degrees)
    cos, sin = math.cos(radians), math.sin(radians)
    x, y, z = (point[i] - origin[i] for i in range(3))
    if axis == 'x':
        y, z = y * cos - z * sin, y * sin + z * cos
    elif axis == 'y':
        x, z = x * cos + z * sin, -x * sin + z * cos
    else:
        x, y = x * cos - y * sin, x * sin + y * cos
    return (x + origin[0], y + origin[1], z + origin[2])


def mount(point, forward, across, up):
    """The renderer's own transform for a part: out along the barrel, then across and up to its seat."""
    return (point[0] + across, point[1] + forward, point[2] + up)


def points_of(tool):
    found = []
    for element in static_of(tool):
        found += corners(element)
    for name, forward, across, up in PLACEMENTS[tool]:
        for element in PARTS[name]:
            found += [mount(p, forward, across, up) for p in corners(element)]
    return found


def check_bounds():
    """
    Refuses a tool that leaves the 0..16 item box.

    Coordinates outside it are legal and do render, which is what makes this worth a check rather than
    a glance: an overhanging blade is not an error anywhere, it simply draws outside its slot and is
    clipped, and in a hotbar that reads as a design choice rather than as a mistake.
    """
    problems = []
    for tool in sorted(HEADS):
        low = min(p[i] for p in points_of(tool) for i in (0, 1, 2))
        high = max(p[i] for p in points_of(tool) for i in (0, 1, 2))
        if low < -0.01 or high > 16.01:
            problems.append('%s spans %.2f..%.2f, outside the 0..16 item box' % (tool, low, high))
    if problems:
        raise SystemExit('\n'.join(problems))


def check_placements():
    """Fails if CPTPartials.java has drifted from the numbers the geometry was laid out with."""
    if not os.path.exists(JAVA_PARTIALS):
        return
    source = resolved(open(JAVA_PARTIALS).read())
    for tool, mounts in sorted(PLACEMENTS.items()):
        for name, forward, across, up in mounts:
            wanted = '%sF, %sF, %sF' % (trim(forward), trim(across), trim(up))
            if wanted not in source:
                raise SystemExit('%s: nothing mounted at (%s) -- generate_models.py and CPTPartials '
                                 'disagree about where %s sits on the %s'
                                 % (JAVA_PARTIALS, wanted, name, tool))


def resolved(source):
    """
    The Java source with its one named constant folded into literals, so the mounts can be compared.

    `BARREL_UP` is worth keeping as a name on that side -- eight mounts repeating 2.5F would be eight
    places to change it -- so the check does the arithmetic instead of demanding the numbers be
    written out. It also checks the constant itself agrees, since folding in a wrong value would make
    every comparison pass.
    """
    named = re.search(r'BARREL_UP = ([0-9.]+)F', source)
    if not named:
        raise SystemExit('%s: no BARREL_UP to resolve' % JAVA_PARTIALS)
    if abs(float(named.group(1)) - BARREL_UP) > 1e-6:
        raise SystemExit('%s: BARREL_UP is %s, the geometry was laid out at %s'
                         % (JAVA_PARTIALS, named.group(1), BARREL_UP))
    source = re.sub(r'BARREL_UP ([+-]) ([0-9.]+)F',
                    lambda m: trim(BARREL_UP + (1 if m.group(1) == '+' else -1) * float(m.group(2)))
                    + 'F', source)
    return source.replace('BARREL_UP', trim(BARREL_UP) + 'F')


def trim(value):
    text = ('%.4f' % value).rstrip('0')
    return text + '0' if text.endswith('.') else text


# --- emitting ------------------------------------------------------------------------------------


def materials_of(elements):
    return {face['texture'].lstrip('#') for element in elements for face in element['faces'].values()}


def wrap(elements, with_display):
    model = {'textures': {name: '%s:item/part/%s' % (NAMESPACE, name)
                          for name in sorted(materials_of(elements))}}
    if with_display:
        model['display'] = SCALED_DISPLAY
    model['elements'] = elements
    return model


def write(path, model):
    directory = os.path.dirname(path)
    if directory:
        os.makedirs(directory, exist_ok=True)
    with open(path, 'w') as handle:
        json.dump(model, handle, indent=2)
        handle.write('\n')


# Filled in by main() before anything is written; gui_scale needs the geometry to measure.
GUI_SCALE = 1.0
SCALED_DISPLAY = {}


def main():
    global GUI_SCALE, SCALED_DISPLAY
    check_bounds()
    check_placements()
    check_renderers()
    check_clearance()
    check_coplanar()
    GUI_SCALE = gui_scale()
    SCALED_DISPLAY = display()

    written = 0
    for tool in sorted(HEADS):
        write(os.path.join(MODELS, tool + '.json'), wrap(static_of(tool), True))
        written += 1
        for name in sorted({placement[0] for placement in PLACEMENTS[tool]}):
            write(os.path.join(MODELS, tool, name + '.json'), wrap(PARTS[name], False))
            written += 1
    print('wrote %d models to %s (gui scale %.2f)' % (written, MODELS, GUI_SCALE))
    return 0


if __name__ == '__main__':
    sys.exit(main())
