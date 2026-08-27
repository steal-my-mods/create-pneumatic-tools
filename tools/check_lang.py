#!/usr/bin/env python3
"""
Checks that every translation key this mod asks for at runtime actually exists, and that it does not
ship keys nothing asks for.

Everything Create's tooltip system does fails silently. `ItemDescription` derives its keys from the
item's own description id and simply renders nothing where a key is missing -- so a tool with no
`tooltip.summary` looks like a tool the author forgot to describe, and a `condition2` with no
`behaviour2` renders as a heading with empty space under it. Neither logs anything. The only symptom
is a player hovering over an item.

The checks, in the order they run:

  * every item registered in CPTItems has a display name;
  * every block registered in CPTBlocks has one too. The source block is invisible and has no shape,
    so nothing in ordinary play can target it and read the name back -- which makes a missing one
    completely silent rather than merely quiet;
  * every one of them has a tooltip summary, at least one condition, and a behaviour for each
    condition -- because every one of them is handed to `ItemDescription.Modifier` in CPTTooltips;
  * the creative tab has a title;
  * nothing in en_us.json is unaccounted for, which is what catches a key left behind by a rename.

    python3 tools/check_lang.py

Exits non-zero and says what is wrong.
"""

import json
import os
import re
import sys

NAMESPACE = 'createpneumatictools'
LANG = 'src/main/resources/assets/%s/lang/en_us.json' % NAMESPACE
ITEMS = 'src/main/java/com/%s/registry/CPTItems.java' % NAMESPACE
BLOCKS = 'src/main/java/com/%s/registry/CPTBlocks.java' % NAMESPACE
TOOLTIPS = 'src/main/java/com/%s/client/CPTTooltips.java' % NAMESPACE


def registered_items():
    """The registry ids in CPTItems, read out of the source so adding a tool is enough."""
    source = open(ITEMS).read()
    ids = re.findall(r'ITEMS\.registerItem\(\s*"([^"]+)"', source)
    if not ids:
        raise SystemExit('%s: found no registered items; update this check' % ITEMS)
    return ids


def registered_blocks():
    """The registry ids in CPTBlocks, read out of the source the same way the items are."""
    source = open(BLOCKS).read()
    ids = re.findall(r'BLOCKS\.register\(\s*"([^"]+)"', source)
    if not ids:
        raise SystemExit('%s: found no registered blocks; update this check' % BLOCKS)
    return ids


def everything_gets_a_create_tooltip():
    """
    Whether CPTTooltips still gives *every* item a description, which is what lets this script
    demand a summary for all of them rather than keeping a second list that would drift.
    """
    source = open(TOOLTIPS).read()
    return 'CPTItems.all()' in source and 'ItemDescription.Modifier' in source


def main():
    lang = json.load(open(LANG))
    problems = []
    expected = set()

    tab = 'itemGroup.%s' % NAMESPACE
    expected.add(tab)
    if tab not in lang:
        problems.append('%s is missing, so the creative tab is titled with its own key' % tab)

    if not everything_gets_a_create_tooltip():
        raise SystemExit('%s no longer describes every item from CPTItems.all(); this check assumed '
                         'it did, and needs updating' % TOOLTIPS)

    for block in registered_blocks():
        name = 'block.%s.%s' % (NAMESPACE, block)
        expected.add(name)
        if name not in lang:
            problems.append('%s is missing, so the block has no name' % name)

    for item in registered_items():
        name = 'item.%s.%s' % (NAMESPACE, item)
        expected.add(name)
        if name not in lang:
            problems.append('%s is missing, so the item has no name' % name)

        summary = name + '.tooltip.summary'
        expected.add(summary)
        if summary not in lang:
            problems.append('%s is missing, so the item has no description' % summary)

        index = 1
        while '%s.tooltip.condition%d' % (name, index) in lang:
            expected.add('%s.tooltip.condition%d' % (name, index))
            behaviour = '%s.tooltip.behaviour%d' % (name, index)
            expected.add(behaviour)
            if behaviour not in lang:
                problems.append('%s is missing, so condition%d renders as a heading with nothing '
                                'under it' % (behaviour, index))
            index += 1
        if index == 1:
            problems.append('%s has no tooltip.condition1, so Hold Shift shows nothing' % name)

    # A key nothing asks for is almost always the debris of a rename, and it is invisible in game.
    for key in sorted(lang):
        if key not in expected:
            problems.append('%s is in en_us.json but nothing asks for it' % key)

    if problems:
        print('%d translation problem(s):' % len(problems), file=sys.stderr)
        for problem in problems:
            print('  ' + problem, file=sys.stderr)
        return 1

    print('%d translation keys check out.' % len(expected))
    return 0


if __name__ == '__main__':
    sys.exit(main())
