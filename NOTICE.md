# Third-party notices

Create: Pneumatic Tools is MIT licensed; see [LICENSE](LICENSE). This file records the third-party
code it is built from, and the notices that code's licence requires be carried along with it.

## Create

Create is split-licensed: its **code is MIT**, and everything under its `assets/` is **All Rights
Reserved**. Only the MIT half is relevant here.

This mod does not copy Create's code, but it calls into a good deal of it and its behaviour is
defined by that code rather than merely assisted by it:

- **`BacktankUtil`** is the whole power model. Every tool's cost is a "uses per tank" rating handed
  to `BacktankUtil.canAbsorbDamage`, and the durability bar under a tool is
  `BacktankUtil.getBarWidth`.
- **`TreeCutter`** is the Pneumatic Saw's felling. The saw reaches it the same way
  `SawBlockEntity` does, so a hand saw and a mounted saw agree about what a tree is.
- **`BlockHelper.destroyBlockAs`** breaks the extra blocks the tunnelling drill and the saw take
  down.
- **`SandPaperPolishingRecipe`** and Create's `pressing` recipe type are what the Buffer and the
  Crimper process; `SandPaperItem.spawnParticles` draws the result.
- **`HandCrankBlockEntity.turn`** is what the Pneumatic Wrench drives.
- **`ItemDescription`** and `TooltipModifier` give the items Create-style tooltips.

Whether that reaches "substantial portions" is arguable, and the argument is not worth having when
carrying the notice costs nothing:

> MIT License
>
> Copyright (c) The Create Team / The Creators of Create
>
> Permission is hereby granted, free of charge, to any person obtaining a copy
> of this software and associated documentation files (the "Software"), to deal
> in the Software without restriction, including without limitation the rights
> to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
> copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all
> copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
> IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
> FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
> AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
> LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
> OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
> SOFTWARE.

### Art

**No Create art is used.** Create's `assets/` are All Rights Reserved, so the only safe amount to
copy is none. Every sprite in this mod, and the badge, is generated from scratch by
`tools/generate_textures.py` and `tools/generate_logo.py`.

What the badge does borrow is a *convention* — the white-ringed azure disc of graph paper that every
Create addon uses to say "this plugs into Create". A convention is not artwork, and the palette,
proportions and grid used here are the ones this author's sibling addons already share, not Create's.

## Minecraft and NeoForge

Compiled against NeoForge and Minecraft 1.21.1 via the NeoForge Mod Development plugin. Neither is
redistributed in this jar. Mappings are Parchment (licensed under the Parchment licence) and are a
build-time aid only — no mapping data ships.
