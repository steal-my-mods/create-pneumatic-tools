# Pneumatic Track Trolley (Rail Rider)

**Status: not built, and the first draft's reasoning was partly wrong.** It rejected a
no-entity, ride-the-rail tool on the grounds that a Create Track has nothing to read a direction from.
It has. Read this before starting it; update it if the thinking changes again.

A drive mechanism that carries the player along a Create Track or a vanilla Rail on backtank air, with
no minecart and no train assembly. Not a grinding mechanic — the grind is the *shape* of the idea, not
the mechanic: you are riding the rail itself rather than putting a vehicle on it.

## What the first draft got wrong

It said: "Create Tracks are a `TrackBlock` holding a graph of `TrackNode`s and `TrackEdge`s in
`TrackGraph` — curves, slopes and junctions that have no block state to read", and concluded that a
held item "has nothing to read a direction from at all".

Checked against Create 6.0.11 rather than remembered, that is false twice over:

- `TrackBlock` **does** carry `EnumProperty<TrackShape> SHAPE`, and implements `ITrackBlock`, which
  exposes `getTrackAxes`, `getCurveStart`, `getUpNormal` and `getYOffsetAt` on any track block. It is
  an interface, so modded track materials come along for free. There is a direction to read, and it is
  a published one.
- `TrackGraphHelper.getGraphLocationAt(level, pos, axisDirection, axis)` returns a
  `TrackGraphLocation` — the graph, the edge, and how far along it you are — from nothing but a block
  position and an axis. That is the entry point from "a player standing on a rail" into Create's own
  graph.

And the graph is walkable with Create's own code. `TravellingPoint` is the class a Train's bogeys ride
on: `travel(graph, distance, selector)` moves it along, `getPosition(graph)` gives the world point —
correct on curves, slopes and banked track, because it is the same call Create draws carriages with —
and `steer(SteerDirection, Vec3)` is a track selector that resolves junctions from a **direction
vector**. Point it at the player's look vector and steering at a switch is "look where you want to
go", for free, from Create's own code.

So the shape of the tool is not two vehicles in one class, and it is not a bespoke curve-follower. It
is: find a `TrackGraphLocation` under the player, hold a `TravellingPoint`, and spend air per tick to
advance it.

## What the first draft got right

- **Vanilla rails are a different system**, and still are. `BaseRailBlock.isRail` and a `SHAPE`
  property, no graph. A rider that does both is two followers behind one item — which is fine, but it
  is two, and the vanilla one is the throwaway of the pair.
- **Trains already own the graph.** A rider sitting on an edge a Train is scheduled onto is a case
  Create has no hook for. There are three honest positions: read `TrackEdge`/`EdgeData` for
  occupation and refuse to enter, accept being run over (defensible, and in keeping with a tool that
  is deliberately not a vehicle), or make the thing a real one-carriage Train and stop calling it a
  hand tool. Pick one on purpose; do not arrive at the third by accident.

## The part that is actually hard

**Moving a player.** Player position is client-authoritative: the client simulates, the server
corrects. Calling `setPos` on a `ServerPlayer` every tick sends a teleport every tick, and the result
is rubber-banding, not riding. Two ways out, and this is the real design decision:

1. **The player rides something.** Vanilla already syncs "passenger of entity" smoothly, which is why
   minecarts, boats and Create's own carriages all work. But the entity this needs is far smaller than
   the `VehicleEntity` the first draft costed: no model, no inventory, no interaction, no save format
   at all (`shouldBeSaved()` false — it is exactly as temporary as the wrench's generator block, and
   for the same reason). It holds a `TravellingPoint` and moves. That is a spawn packet and a
   no-op renderer.
2. **Set velocity, not position.** Aim `setDeltaMovement` down the tangent at
   `getPositionWithOffset(graph, lookahead, ...)` and let client prediction carry the player there.
   No entity at all. Cheap, and it degrades on tight curves and at junctions in ways that are hard to
   test and easy to ship without noticing.

Build 1. The first draft's instinct to prefer an entity was right; what was wrong was the price it put
on one.

## If it is built

- Create Tracks via `TravellingPoint`, steering from the look vector, air spent per tick of travel.
- Vanilla rails as a separate, dumber follower reading `SHAPE`, or not at all in the first version —
  and say which in the tooltip.
- Decide the Train collision policy explicitly and write it down here.
- The ride ends the way everything else in this mod ends: the renewals stop. No dismount handling to
  forget.

## The smaller idea inside it

Unchanged, and still worth noting: the genuinely handheld part of the fantasy — *going fast on air* —
does not need a rail. A dash that spends air for a burst of movement is one `Item`, no entity, and no
track graph. It is a different tool from the one asked for, and it is the part of this one that fits
the mod as it stands.
