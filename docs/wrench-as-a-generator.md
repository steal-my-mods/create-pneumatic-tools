# The Pneumatic Wrench as a portable source of rotation

**Status: built, after all.** This document argued against it and was overruled by the obvious
counter-argument: a temporary block is fine if nothing has to remember to remove it. Kept because the
reasoning about *why the direct version is impossible* is still the reason the mod works this way,
and because the objection it raises to the temporary block is real and is what the lease answers.

The original idea was "a handheld Hand Crank … providing portable kinetic torque straight from your
main hand": point the wrench at a kinetic block and it becomes a generator on that network for as
long as you hold it, with no block placed.

## Why it cannot work the way it reads

Rotation in Create is a property of a **graph of block entities**, not of a position. A
`KineticBlockEntity` finds its neighbours through `RotationPropagator`, which walks from block entity
to block entity asking `hasShaftTowards` and comparing ratios; a `GeneratingKineticBlockEntity` joins
a `KineticNetwork` keyed by a `Long` source id and contributes capacity to it. Every one of those
steps needs something in the world at a `BlockPos` with a `BlockEntityType` — a network cannot have a
member that is an item in somebody's hand, because there is no position to propagate from, nothing
for a neighbour to find when *it* recalculates, and nowhere for the network to survive the player
walking away mid-tick.

The workarounds all amount to placing a block:

- **A temporary generator block** placed while the button is held and removed when it is released.
  This works, and it is a *block* — which means a block entity, and a very good chance of leaving one
  behind when a player logs out mid-click. (That last objection is the one that turned out to be
  answerable, and it is what the mod does now. See "What shipped".)
- **A fake block entity injected into the neighbour's network.** Create's propagator does not have a
  hook for a member that is not in the world, and adding one means either a mixin into
  `RotationPropagator` or a fork of the whole kinetics graph. Both are far outside "connect existing
  Create concepts".

## What shipped

The first option, with the objection answered rather than avoided.

The wrench puts a real `PneumaticSourceBlock` into the empty space against the face you aimed at —
invisible, no collision, no drops, no item form, tagged non-movable. What makes it safe is not care
about removing it but that **nothing has to remember to**: the block holds a ten-tick lease and
deletes itself when it runs out, and the wrench is the only thing that renews it. Releasing the
button, switching slot, walking away, dying, disconnecting, the chunk unloading and the game crashing
are then all one case, and it is the case the block already handles.

An earlier version instead drove Create's Hand Crank and Copper Valve Handle, spending air instead of
hunger. It worked and it was safe, and it was also a worse tool: it needed a crank to already be
there, which is precisely the situation where you do not need a portable source of rotation.

## The idea this still leaves on the table

A **placeable air motor**: a permanent kinetic generator that consumes Compressed Air from a pipe or
a vessel, rather than from a backtank on your back. That is a machine rather than a tool, and it
belongs in an addon about pressure vessels — `create-caes` is the repo where it would fit.
