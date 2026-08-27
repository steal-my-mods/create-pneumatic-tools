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
- **`GeneratingKineticBlockEntity`**, `DirectionalKineticBlock` and `KineticBlockEntity` are what the
  Pneumatic Wrench's source block *is*; it extends them rather than calling them.
- **`KineticNetwork`** defines what the wrench is worth. Create bills a generator's capacity as
  `per-RPM × speed`, and `PneumaticSourceBlockEntity.calculateAddedStressCapacity` exists to answer
  that formula; `RotationPropagator`'s speed arithmetic is what the source matches itself to.
- **`BlockStressValues`** is how the source's capacity and quoted speed reach Create at all.
- **`CustomRenderedItemModelRenderer`** and `PartialItemModelRenderer` draw every tool's moving parts.
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
copy is none. Every visual asset in this mod is generated from scratch by this repo's own tooling:
the tools' geometry and display transforms by `tools/generate_models.py`, the material panels they
are skinned with by `tools/generate_textures.py`, and the badge by `tools/generate_logo.py`. All
three are byte-deterministic, and CI regenerates everything and fails on a diff.

What the badge does borrow is a *convention* — the white-ringed azure disc of graph paper that every
Create addon uses to say "this plugs into Create". A convention is not artwork, and the palette,
proportions and grid used here are the ones this author's sibling addons already share, not Create's.

## Minecraft and NeoForge

Compiled against NeoForge and Minecraft 1.21.1 via the NeoForge Mod Development plugin. Neither is
redistributed in this jar. Mappings are Parchment (licensed under the Parchment licence) and are a
build-time aid only — no mapping data ships.
