# Pneumatic Stilts

**Status: designed, not built.** Read this before starting it; update it if the thinking changes.

High-pressure piston boots that hold the player some number of blocks above the ground, so
scaffolding is not needed for high work. Holding Piston Extension Poles lets you Right-Click to add
height and Sneak-Right-Click to take it back.

## Why it is not in the first release

The interface is excellent — Piston Extension Poles as the unit of height is exactly the kind of
"connect two existing Create things" this mod is for, and it is the best idea in the list. The
problem is underneath it: **there is nothing to stand on.**

A player's height above the floor is not a number Minecraft stores. It is a consequence of collision
against blocks. Holding a player at ground + 4 means one of:

- **Fake collision.** Give the boots a per-tick handler that cancels the fall and pins `player.setPos`
  to `groundY + height`. Works standing still on flat ground; fights every other system the moment
  you move. Walking off an edge has to re-find "the ground" (which ground? the one below you now, or
  the one you left?), water and ladders have their own movement, a piston or a falling block passing
  under you changes the answer mid-step, and the server's movement checks see a player hovering.
- **A real block.** Place an invisible collision block under the player and move it as they walk.
  Now the world has a block in it that must be cleaned up on death, disconnect, dimension change and
  crash, and a chunk unload at the wrong moment leaves it behind forever.
- **An entity to stand on.** A shulker-like collidable entity the player rides. This is the version
  that actually works with vanilla movement — you are standing on something, so everything downstream
  is normal — and it is a new entity with a renderer, sync and save format, which is the same bill
  the Rail Rider runs up.

None of the three is a bad idea. All three are much bigger than "an item with an air cost", which is
what the other nine tools are, and the failure mode of getting it wrong is a player stuck in the
floor or falling through the world — considerably worse than a tool that does not work.

## What to build first if it is picked up

The entity version, and start from the *height* rather than from the movement: a stack of visible
piston-pole segments as a single collidable entity, the player riding the top of it, air spent per
segment per second. Then the Right-Click-to-add-a-pole interface is exactly as described above, and
every question about walking off an edge is answered by "you are riding something, so you don't".

## What the mod does instead, for now

Nothing. There is no half-version of this worth shipping: a tool that lifts you and occasionally
drops you through the floor is worse than no tool.
