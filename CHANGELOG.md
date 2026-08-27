# Changelog

## Unreleased

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
