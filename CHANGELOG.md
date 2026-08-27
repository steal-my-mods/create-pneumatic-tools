# Changelog

## Unreleased

**New**

- **The Pneumatic Borer.** The Tunnelling Drill's burst five blocks across instead of three —
  twenty-five at a time, flat against the face you drilled, charged once per burst. Wider and slower
  per block, from half as many bursts a tank: it moves more stone and empties a tank far faster,
  which is the trade. The one tool here you cannot make at a workbench — a recipe five blocks across
  goes in a **Mechanical Crafter**, from the Tunnelling Drill and sixteen more Mechanical Drills.
  That is one Mechanical Drill for every block it breaks, which is the same rule the Tunnelling Drill
  is built on.

**Models**

- **The Pneumatic Saw, Grinder and Buffer no longer look like the same tool in three colours.** They
  shared a head, a spindle, a mount and a disc of the same size; only the disc's thickness and
  material differed, which at sixteen pixels is nothing. The Saw is now a circular saw — a big thin
  blade slung below the barrel, a hood over it, a shoe under the nose. The Grinder is an angle
  grinder — a small thick disc at the nose behind a half guard, with a side handle out the far flank.
  And the Buffer's pad now faces *forward* on the barrel's own axis and spins about it, rather than
  standing up beside it like the other two.
- **The Saw's blade and the Grinder's disc are on the tool's left flank, so you can watch them work.**
  On the right they sat behind the barrel and at the edge of the screen: the grinder's wheel was
  almost entirely hidden in first person, which is the only view that matters for the one part of a
  tool that moves.

**Tools**

- **The Pneumatic Wrench is worth 1024 Stress Units at any speed**, rather than a fixed 16 SU per
  RPM. Create bills a generator as torque times speed, which is right when a generator owns its speed
  — a Water Wheel turns at 8 and that is that — and wrong for a tool that can match somebody else's:
  a wrench joined onto a network already running at 192 RPM was worth 3072 SU for exactly the air
  that buys 1024 on a shaft of your own. It now delivers a fixed amount of *power*, so at half the
  speed it supplies twice the torque and at twice the speed half of it, which is what an air motor
  does. The config key is now `wrenchStressUnits` (the whole figure, default 1024) in place of
  `wrenchCapacity` (a per-RPM one, default 16) — an existing config file will pick up the new default.
- **The Pneumatic Wrench now falls in behind a network that is already turning** rather than fighting
  it. Its direction comes from which face you grab, so roughly half of all clicks onto a moving shaft
  used to disagree with it — and Create's answer to two generators that disagree is to *destroy* one,
  so the source was deleted on the tick it appeared and the wrench, which re-places it every tick,
  put another one there. The visible symptom was a wrench that worked on one side of a shaft and did
  nothing but hiss on the other. It now matches the network's speed and direction exactly and
  contributes its stress capacity, which makes it useful for the case it was always wanted for: a
  line that is overstressed rather than stopped. A matched wrench is billed at the speed it matched,
  so it is worth less than its full 1024 SU on a network slower than 64 RPM. A still shaft still gets
  the wrench's own 64 RPM.
- The Pneumatic Buffer no longer polishes Rose Quartz out of your other hand. Every tool in the mod
  now does its work to a block in the world; polishing a stack from a hotbar slot was the one job
  here that was really a crafting shortcut, and a Sand Paper already does it.
- The Pneumatic Buffer is now built around a **Honeycomb Block** instead of a Sand Paper, which is
  where the wax it lays down comes from. Nothing about using it changed — it still seals copper with
  no Honeycomb in your inventory — but the wax is now paid for once, at the bench.
- The Tunnelling Drill is now a **Hand Drill surrounded by eight Mechanical Drills**, rather than a
  Pneumatic Wrench surrounded by them. That is nine Mechanical Drills for a tool that breaks nine
  blocks, and it puts both of the advanced diggers on the same upgrade path from the Hand Drill; the
  Wrench never had anything to do with digging.

**Multiplayer**

- A Pneumatic Wrench now only drives sources it placed. Two players wrenching within range of each
  other used to find each other's: one player's renewals kept the other's generator alive while
  their own lease ran out, and letting go of the button removed a generator somebody else was still
  using.
- The Tunnelling Drill and the Pneumatic Saw no longer reach through spawn protection or the world
  border. Vanilla checks both only against the block a player actually swung at; the extra blocks
  these two take down now ask for themselves. Claim mods were already respected.
- The Pneumatic Wrench asks permission for the space the source goes in, rather than only for the
  block it was held against.
- An empty backtank says so once rather than five times a second. Holding the button on a dry tank
  used to broadcast two sounds every four ticks to everyone nearby; the refusal now puts the tool on
  a half-second cooldown, which also greys the icon.
- New config option `vacuum.vacuumOnlyOwnDrops` (default off) makes the Vacuum Wand leave alone
  stacks another player dropped or died holding. Block and mob drops belong to nobody and are taken
  either way.

**Performance**

- The Pneumatic Wrench no longer reads every block state within reach, every tick, on both sides —
  4,913 of them at the default range and 274,625 at the largest the config allows. It walks the
  block entities of the chunks in range instead.
- Polishing a stack with the Pneumatic Buffer no longer runs the recipe lookup twice per item.

## 0.1.0

First release. Eight handheld tools that run off a Create backtank.

**Mining and harvesting**

- **Hand Drill** — a Mechanical Drill you can carry. Quick on anything a pickaxe or a shovel
  handles, one air per block.
- **Pneumatic Jackhammer** — shatters hard blocks in a fixed quarter-second whatever their hardness,
  so Obsidian and Deepslate take the same moment. Slow and free on anything soft.
- **Tunnelling Drill** — clears a 3x3 slice lying flat against the face you drilled, charged
  once per burst. Drill a wall and it takes a wall; drill the floor and it cuts a trench.
- **Pneumatic Saw** — fells a whole tree from one log, and harvests bamboo, cane, cactus and chorus
  the way a mounted Mechanical Saw does.

**Surface treatment**

- **Pneumatic Grinder** — strips bark, scrapes a stage of oxide off copper, takes wax off a sealed
  block.
- **Pneumatic Buffer** — waxes copper by friction with no Honeycomb, and polishes a whole stack of
  Rose Quartz in one click.

**Power and pickup**

- **Pneumatic Vacuum Wand** — hold Right-Click and loose items and experience come to you.
- **Pneumatic Wrench** — portable torque. Hold it against a shaft and it drives the network at
  64 RPM with 1024 Stress Units behind it — twice a Hand Crank's speed and four times its torque —
  by keeping an invisible generator in the world for exactly as long as you hold the button.

Every tool is a 3D model with a moving part: a pistol-grip body with the working end pointing where
you are aiming it. Bits, impellers and sockets turn on the barrel, blades and wheels spin on a
spindle across it, and the jackhammer's chisel recoils and strikes. They idle while carried, spool up
under load, and stop when the tank runs dry.

Every cost is configurable as a "uses per tank" rating, the same unit Create rates the Extendo Grip
and the Potato Cannon in. Set any of them to 0 to make that tool free.
