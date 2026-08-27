#!/usr/bin/env python3
"""
Writes one recipe advancement per crafting recipe, so the items appear in the recipe book.

A recipe on its own is craftable but invisible: vanilla only shows a recipe in the book once an
advancement has granted it, and the mod shipped without any. JEI and EMI read the recipe manager
directly and were never affected, which is exactly why nobody noticed -- the people most likely to
be testing a Create addon are the ones least likely to see the gap.

Generated rather than hand-written because the two files have to agree: a new recipe without its
advancement is a new item that never appears in the book, and there is nothing to notice. The CI
gate re-runs this and fails on a diff, so the pair cannot drift.

    python3 tools/generate_recipe_advancements.py

Byte-deterministic, like every other generator here.
"""

import collections
import json
import os

NAMESPACE = 'createpneumatictools'
RECIPES = 'src/main/resources/data/%s/recipe' % NAMESPACE
ADVANCEMENTS = 'src/main/resources/data/%s/advancement/recipes' % NAMESPACE

# The body every tool is built on. These say nothing about which tool you are about to make, so
# unlocking eight recipes off a Brass Sheet would put the whole mod in the book at once and tell a
# player nothing about any of it.
COMMON = {'create:brass_sheet', 'create:andesite_alloy'}


def ingredients(recipe):
    """Every distinct item the recipe names, in the order the pattern first uses them."""
    key = recipe['key']
    seen = []
    for row in recipe['pattern']:
        for symbol in row:
            if symbol == ' ' or symbol not in key:
                continue
            entry = key[symbol]
            item = entry.get('item') or entry.get('tag')
            if item and item not in seen:
                seen.append(item)
    return seen


def trigger_for(recipe, name):
    """
    What holding this unlocks the recipe.

    The last ingredient that is not common body material. For the one-column tools that is the Create
    machine at the head -- a Mechanical Drill unlocks the Hand Drill, a Propeller the Vacuum Wand --
    and for the two built out of this mod's own tools it is that tool, which is the right gate
    anyway: the Jackhammer wants a Hand Drill and the Tunnelling Drill wants a Wrench, so neither
    appears in the book before you could make one.
    """
    distinctive = [item for item in ingredients(recipe) if item not in COMMON]
    if not distinctive:
        raise SystemExit('%s is made entirely of common materials; pick its trigger by hand' % name)
    return distinctive[-1]


def advancement(name, trigger):
    recipe_id = '%s:%s' % (NAMESPACE, name)
    got = 'has_' + trigger.split(':')[-1]
    return collections.OrderedDict([
        ('parent', 'minecraft:recipes/root'),
        ('criteria', collections.OrderedDict([
            (got, collections.OrderedDict([
                ('conditions', {'items': [{'items': trigger}]}),
                ('trigger', 'minecraft:inventory_changed'),
            ])),
            ('has_the_recipe', collections.OrderedDict([
                ('conditions', {'recipe': recipe_id}),
                ('trigger', 'minecraft:recipe_unlocked'),
            ])),
        ])),
        ('requirements', [['has_the_recipe', got]]),
        ('rewards', {'recipes': [recipe_id]}),
    ])


def main():
    os.makedirs(ADVANCEMENTS, exist_ok=True)
    names = sorted(f[:-5] for f in os.listdir(RECIPES) if f.endswith('.json'))
    if not names:
        raise SystemExit('no recipes found in %s -- this generator has nothing to do' % RECIPES)

    wanted = set()
    for name in names:
        with open(os.path.join(RECIPES, name + '.json')) as handle:
            recipe = json.load(handle, object_pairs_hook=collections.OrderedDict)
        if recipe.get('type') != 'minecraft:crafting_shaped':
            continue
        path = os.path.join(ADVANCEMENTS, name + '.json')
        wanted.add(name + '.json')
        with open(path, 'w') as handle:
            json.dump(advancement(name, trigger_for(recipe, name)), handle, indent=2)
            handle.write('\n')

    # A recipe that was renamed or deleted leaves an advancement behind that grants a recipe id no
    # longer in the pack. That logs nothing and shows nothing; it just sits there.
    for stale in sorted(set(os.listdir(ADVANCEMENTS)) - wanted):
        os.remove(os.path.join(ADVANCEMENTS, stale))
        print('removed orphan %s' % stale)

    print('%d recipe advancements written.' % len(wanted))


if __name__ == '__main__':
    main()
