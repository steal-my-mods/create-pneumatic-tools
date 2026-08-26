# Pneumatic Stilts

**Status: dropped.** Not "not yet" — decided against, in August 2026, after the technical blocker was
removed and the tool still did not look like a good thing to have in the game. This file records the
whole route so the idea does not get re-litigated from the top; if it is ever revived, the questions
at the bottom are the ones to answer first, and they are design questions, not engineering ones.

The idea: high-pressure piston boots that hold the player some number of blocks above the ground, so
scaffolding is not needed for high work. Piston Extension Poles as the unit of height — Right-Click to
add one, Sneak-Right-Click to take it back. It was the best idea in the original list, and the
interface is still the best thing about it.

## The blocker, and how it came off

A player's height above the floor is not a number Minecraft stores. It is a consequence of collision
against blocks, and there is no attribute or field that offsets it. The nearest vanilla things are all
the wrong shape: `Attributes.STEP_HEIGHT` lets you climb *onto* a ledge and does nothing on flat
ground, and `Attributes.SCALE` makes a giant — a player who no longer fits a doorway is not a player
on stilts. So holding someone at `groundY + 4` means one of three things, and the first draft of this
file rejected all three:

- **Fake collision** — cancel the fall and pin `player.setPos`. Works standing still, fights every
  other system the moment you move, and the failure mode is a player in the floor.
- **A real block** — place a collision block under the player and move it as they walk. Rejected
  because it "must be cleaned up on death, disconnect, dimension change and crash, and a chunk unload
  at the wrong moment leaves it behind forever."
- **An entity to stand on** — recommended, and costed at an entity type with a renderer, sync and a
  save format.

The middle objection then stopped being true. It is a list of ways to *forget to remove* something,
and the Pneumatic Wrench had to solve that exact list: `PneumaticSourceBlockEntity` counts a lease
down and deletes itself, and the holder pushes the counter back up. A leased block is not a block left
in the world. The design that follows is real and would work: a 3x3 platform of leased, invisible,
solid blocks renewed at `feetY - 1` each tick, mounted with one deliberate teleport, so that walking,
jumping, sprinting, stepping and falling are all vanilla because you are genuinely standing on blocks.

That is not why it was dropped.

## Why it was dropped anyway

Four questions, asked in that order, and the second one is fatal.

**Would this be better as a flight mechanic?** It would be easier, and it would be a different tool.
Hovering has no ground contact, so the height-in-Piston-Poles interface — the best part of the idea —
has nothing to attach to, and a hover that is strictly better than stilts at everything stilts do just
replaces them. Worse, granting flight has the same shape of bug the lease was invented to kill, one
level up: an ability set on a player outlives the item, the death, the disconnect and the mod being
removed, and there is no counting-down block to be the one rule. If a flying tool is ever wanted it is
its own item — an air-powered dash or jetpack, the same "smaller idea" noted at the bottom of
`rail-rider.md` — and not this one wearing a different hat.

**Would other players walk on the phantom blocks?** Yes, and that is the one that ends it. The
platform is made of real, solid, server-side blocks: other players stand on them, mobs path onto them,
items and minecarts rest on them, water and lava stop at them, arrows hit them. Then the owner walks
away and everything on top drops, because the lease that makes the tool safe is also what makes the
floor disappear ten ticks later. It can be narrowed — `getCollisionShape` is handed an
`EntityCollisionContext` and can return an empty shape for everyone but the owner, which is how
Scaffolding and Powder Snow discriminate — but the price is a world that is solid for one player and
empty for the next, evaluated separately on every client. That is not a smaller problem than the one
it fixes. Either way a hidden, temporary, walkable floor exists in a shared world, and no version of
this tool gets to not have that.

**Can we show in the world that the player is on stilts?** Only by paying the renderer that the entity
design was rejected for, and it is worse than it sounds. The player is standing on the platform, so
their model's feet are already at working height; the stilts have to be drawn in the space *below*
the feet, which is exactly where the invisible blocks are. Doing it properly means a custom layer on
the player renderer and posing the legs, in third person, for the benefit of onlookers. Leaving it out
means a player who appears to be standing on nothing — which reads as a hacked client, not as a tool.

**Is there a third way, just offsetting the player's height?** No, and that absence is the whole
problem rather than an oversight. There is no offset to set. The three options above are not a
shortlist, they are the complete set of ways to change how high a player is standing.

## If it is ever revived

Start from the second question, not from the code. A design that answers "what do other people see and
walk on" convincingly is a design worth building, and the leased-platform mechanism is sitting there
ready for it. A design that does not is this one again.

Do not build the fake-collision version under any circumstances. A tool that occasionally drops a
player through the floor is worse than no tool, and it is the only failure mode here that cannot be
walked back.
