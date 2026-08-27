#!/usr/bin/env python3
"""
Checks that the README documents every config option, and no options it no longer has.

Config drift is silent in a way that matters more than it looks. Nothing breaks when an option goes
undocumented -- it works perfectly, for the one person who read the source -- so the only symptom is a
server owner who cannot find the knob they were told exists. Twenty-three options accumulated here
before anyone counted, and the README named one of them.

Both directions are checked. A new option missing from the README is the ordinary failure; a README
that still lists an option the config dropped is the one that actively misleads, because it reads as
authoritative and the config file will not mention it at all.

The options are read out of CPTConfig.java rather than the generated TOML on purpose: the TOML only
exists after a run has written one, so a checkout that has never launched the game would pass by
having nothing to compare against.

    python3 tools/check_config_docs.py

Exits non-zero and says which way it drifted.
"""

import re
import sys

CONFIG = 'src/main/java/com/createpneumatictools/CPTConfig.java'
README = 'README.md'


def declared():
    """Every option name in the config spec, however it is declared."""
    source = open(CONFIG).read()
    # Two shapes: the builder's own define/defineInRange, and the usesPerTank helper that wraps it.
    names = set(re.findall(r'\.define(?:InRange)?\(\s*"([A-Za-z]+)"', source))
    names |= set(re.findall(r'usesPerTank\(\s*builder,\s*"([A-Za-z]+)"', source))
    if not names:
        raise SystemExit('%s: found no config options; update this check' % CONFIG)
    return names


def documented():
    """Every option name the README mentions in backticks."""
    text = open(README).read()
    # Anything in backticks that looks like one of these camelCase option names. Deliberately loose:
    # the point is to catch a name the README still carries, not to police how it is written about.
    return set(re.findall(r'`([a-z][A-Za-z]*(?:UsesPerTank|Speed|Ticks|Bias|Hardness|Radius'
                          r'|Interval|Rpm|StressUnits|Range|OnlyOwnDrops))`', text))


def main():
    options = declared()
    described = documented()

    problems = []
    for missing in sorted(options - described):
        problems.append('%s is a config option and the README does not mention it' % missing)
    for stale in sorted(described - options):
        problems.append('the README documents %s, which is not a config option any more' % stale)

    if problems:
        print('\n'.join(problems), file=sys.stderr)
        return 1
    print('%d config options, all documented in %s.' % (len(options), README))
    return 0


if __name__ == '__main__':
    sys.exit(main())
