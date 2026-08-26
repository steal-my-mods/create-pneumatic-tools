# Pneumatic Track Trolley (Rail Rider)

**Status: designed, not built.** Read this before starting it; update it if the thinking changes.

A drive mechanism that clamps onto a Create Track or a vanilla Rail and propels the player along it
on backtank air, with no minecart and no train assembly.

## Why it is not in the first release

Every other tool in this mod is an `Item` with a behaviour and an air cost. This one is not a tool at
all — it is a **vehicle**, and a vehicle in Minecraft is an entity with a network-synced position, a
riding player, a collision box, and a save format. That is more new surface area than the other nine
tools put together, and none of it is shared with them.

The specific costs, none of which the rest of the mod pays:

- **A new entity type**, with a renderer, a spawn packet, NBT, and a `ClientboundSetEntityMotion`
  story that survives a player riding it at speed across chunk borders.
- **Two rail systems that do not agree about what a rail is.** Vanilla rails are blocks with a
  `SHAPE` property and `BaseRailBlock.isRail`. Create Tracks are a `TrackBlock` holding a graph of
  `TrackNode`s and `TrackEdge`s in `TrackGraph` — curves, slopes and junctions that have no block
  state to read. Following one is `AbstractMinecart` logic; following the other is Create's
  `TravellingPoint` walking a graph. A trolley that does both is two vehicles in one class.
- **Trains already own the graph.** Create's `TrackGraph` is what a Train navigates, and a trolley
  sitting on an edge that a Train is scheduled onto is a collision case Create has no hook for. The
  honest options are to make the trolley a real one-carriage Train (which means Bogeys, an assembly
  step, and no longer being a handheld tool) or to ignore trains entirely and be a hazard.

## What it would look like if built

Two shapes are worth considering, and they are not variations of each other:

1. **A real entity.** `PneumaticTrolleyEntity extends VehicleEntity`, placed on a rail, ridden like a
   minecart, drawing air per tick while the throttle is held. Vanilla rails only. This is the version
   that works, and it is a second mod's worth of code.
2. **A held item that moves the player.** No entity: while the item is in use and the player is
   standing on a rail, set the player's velocity along the rail's direction and spend air. Cheap to
   write, and wrong in every corner — the player is not attached to anything, so the first curve,
   slope or gap throws them off, and on a Create Track (which has no shape property) there is nothing
   to read a direction from at all.

If it is built, build 1, for vanilla rails only, and say so in the tooltip. Do not build 2 and hope.

## The smaller idea inside it

The genuinely handheld part of the fantasy — *going fast on air* — does not need a rail at all. A
"pneumatic jet pack" or a dash that spends air for a burst of movement is one `Item`, no entity, and
no track graph. It is a different tool from the one asked for, but it is the part of this one that
fits the mod.
