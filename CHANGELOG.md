# Changelog

## 0.1.0

First release. Nine handheld tools that run off a Create backtank.

A Copper Backtank is already a portable power supply, and Create only spends it on breathing and on
the Extendo Grip. These tools spend it on work.

**Mining and harvesting**

- **Hand Drill** — a Mechanical Drill you can carry. Quick on anything a pickaxe or a shovel handles,
  one air per block.
- **Pneumatic Jackhammer** — shatters hard blocks in a fixed quarter-second whatever their hardness,
  so Obsidian and Deepslate take the same moment. Slow and free on anything soft, because the air is
  what does the shattering.
- **Tunnelling Drill** — clears a 3x3 slice lying flat against the face you drilled, charged once per
  burst however many of the nine blocks were there. Drill a wall and it takes a wall; drill the floor
  and it cuts a trench.
- **Pneumatic Borer** — the same burst five blocks across instead of three, twenty-five at a time.
  Wider and slower per block, from half as many bursts a tank: it moves more stone and empties a tank
  far faster, which is the trade. The one tool here you cannot make at a workbench — a recipe five
  blocks across goes in a **Mechanical Crafter**, from the Tunnelling Drill and sixteen more
  Mechanical Drills. That is one Mechanical Drill for every block it breaks, which is the rule the
  Tunnelling Drill is built on too.
- **Pneumatic Saw** — fells a whole tree from one log, canopy and all, and harvests bamboo, cane,
  cactus, kelp and chorus the way a mounted Mechanical Saw does. Charged per cut, and only for a cut
  that could start a felling: leaves, pumpkins, melons and planks are free, and on those it is just a
  very fast axe.

Tier is never the limit on any of them. The diggers are diamond and the Jackhammer netherite, which in
1.21 come to the same thing — vanilla's highest gate is `needs_diamond_tool`, and a diamond tool clears
it — so everything these five break, they drop, Ancient Debris and Netherite Blocks included.

**Surface treatment**

- **Pneumatic Grinder** — strips bark, scrapes a stage of oxide off copper, takes wax off a sealed
  block. All three are one NeoForge item ability apiece, so modded logs and modded copper work on the
  day they are added.
- **Pneumatic Buffer** — seals copper with the wax built into it, with no Honeycomb to carry and none
  consumed. It is crafted around a Honeycomb Block, which is where that wax came from and the one
  time you pay for it.

**Power and pickup**

- **Pneumatic Vacuum Wand** — hold Right-Click and loose items and experience come to you from eight
  blocks out. A pulse that catches nothing is free, so leaning on the button costs nothing. It leaves
  alone items still inside their pickup delay, so it cannot undo your own Q press.
- **Pneumatic Wrench** — portable torque. Held against a still shaft it drives the network at 64 RPM
  with 1024 Stress Units behind it, twice a Hand Crank's speed and four times its output, by keeping
  an invisible leased generator in the world for exactly as long as you hold the button. Held against
  a network already turning it matches that network's speed and direction instead and lends the same
  1024 SU, which is the case a wrench is most wanted for: a line that is overstressed rather than
  stopped. Two generators that disagree in Create is not a slow network but a destroyed block, so
  matching is the only thing it can safely do.
  - It is worth those 1024 SU **at any speed**. A tank breathes at one rate, so the wrench delivers
    one amount of power, and the speed it happens to be turning at decides whether that arrives as
    speed or as torque — half the RPM, twice the torque. Create bills its own generators the other
    way, as a fixed torque whose Stress Units rise with speed, which is right when a generator owns
    its speed and wrong for a tool that can be handed somebody else's.
  - Let go, switch slot, walk away, die, disconnect, change dimension or run dry and it stops. The
    source holds a short lease that only the wrench renews, so every one of those is the same case:
    the renewals stop and the block deletes itself. Nothing is left behind.

**The air**

Every tool is rated in **uses per tank**, the unit Create already states its own equipment in — the
Extendo Grip is 1000 actions, the Potato Cannon 200 shots — so these numbers read against Create's
own without converting anything. A rating becomes a cost the way Create's does, charged against the
emptiest backtank you are wearing.

- Set any rating to **0** and that tool is free and needs no backtank at all.
- Raising one past **900** does nothing: a use cannot cost less than one air unit, so that is the
  most uses a tank can give however large the number.
- Create's **Capacity** enchantment on the backtank is the one thing that goes past that ceiling. The
  cost per use is worked out from an unenchanted tank, so Capacity III doubles the air without
  touching the price and every tool gets twice the uses.

There is no durability and there are no repairs. The bar under a tool in the hotbar is the tank's
charge. Running dry breaks nothing — the digging tools keep digging, just no faster than bare hands,
and the moving parts stop turning, so an empty tank is something you see in your hand before you swing
at anything.

**Enchantments**

The five digging tools take **Fortune**, **Silk Touch** and **Efficiency**, from an anvil and a book.
An enchanting table refuses all nine, which is the right answer for a tool with no durability and the
same one Create's Extendo Grip gives.

- Fortune and Silk Touch apply to every block in a burst, not just the one you swung at, so a Silk
  Touch Borer takes twenty-five intact blocks at once.
- One level of Efficiency is worth one level of Haste — vanilla's own per-level step — so Efficiency V
  doubles a tool's speed and a beacon stacks on top of it. It multiplies rather than adds the way
  vanilla's does, because the Jackhammer's speed is solved backwards out of a fixed break *time*: a
  flat bonus would shorten Deepslate far more than Obsidian and undo the one thing that tool is for.
- Efficiency does nothing where the air does nothing, so a Jackhammer on soft stone is slow and free
  with a book on it, exactly as it is without.
- On the backtank itself only **Capacity** touches how far a tank goes.

**The models**

Every tool is a 3D model with a part that moves: a pistol-grip body with the working end pointing
where you are aiming it. Bits, impellers and sockets turn on the barrel; the Saw's blade and the
Grinder's disc spin on a spindle out on the left flank where you can watch them work; the Buffer's pad
faces forward and turns on the barrel's own axis; the Borer's whole face plate turns with its cutters;
and the Jackhammer's chisel recoils and strikes. They idle while carried, spool up under load, and stop
when the tank runs dry. The tools do not swing — you hold a drill against the work.

**Configuration**

Twenty-three server-side options in `config/createpneumatictools-server.toml`, every rating and every
speed among them. Two worth knowing about:

- `jackhammerHardnessBias` (default 0, the flat tool) tilts the jackhammer so that harder blocks break
  *faster* rather than merely as fast: at 0.5, Obsidian goes about four times quicker than Deepslate.
  Worth knowing that a flat break time already *is* an inverted hardness scale, since the multiplier
  grows linearly with hardness and cancels it out; this makes it grow faster than linearly, which is
  what it takes for harder to be genuinely quicker.
- `vacuumOnlyOwnDrops` (default off) makes the Vacuum Wand leave alone stacks another player dropped
  or died holding. Block and mob drops belong to nobody and are taken either way. A shared server
  usually wants this on: vanilla's "whoever reaches it first" is a different bargain when the reach is
  eight blocks and needs no line of sight.

**Multiplayer**

- The Tunnelling Drill, the Borer and the Saw do not reach through spawn protection or the world
  border. Vanilla checks both only against the block a player actually swung at, so every extra block
  these tools take down asks for itself. Claim mods were always covered, because Create's break helper
  posts a cancellable break event.
- The Pneumatic Wrench asks permission for the space its source goes in, not only for the block it was
  held against, and only ever drives sources it placed itself. Without that, two people wrenching in
  one workshop drove each other's blocks.
- An empty backtank says so once rather than five times a second. A refusal puts the tool on a
  half-second cooldown, which also greys the icon.
- The wrench finds its source by walking the block entities of the chunks in range rather than reading
  every block state within reach on both sides every tick — 4,913 palette lookups at the default range
  and 274,625 at the largest the config allows.
