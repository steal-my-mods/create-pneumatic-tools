# Create: Pneumatic Tools

Eight handheld tools that run off a Create backtank.

**Minecraft 1.21.1 · NeoForge 21.1.219+ · Create 6.0+**

![The Hand Drill in hand, with the other seven tools on the hotbar](branding/tools-in-hand.png)

A Copper Backtank is already a portable power supply — Create just spends it on breathing and on the
Extendo Grip. This addon spends it on work. Most of what Create does with rotation to a block bolted
to the ground it could do to a block in front of you, so the Mechanical Drill, the Mechanical Saw
and the Hand Crank each get a version you hold, and the gaps between them are filled with the tools
an actual workshop has: a jackhammer, a grinder, a buffer, a vacuum.

Nothing here is a new mechanic. Every tool spends air through Create's own `BacktankUtil`, at a
"uses per tank" rating in the same unit Create rates its own equipment in.

Every tool is a **3D model with a part that moves** — a pistol-grip body with the working end
pointing where you are aiming it. Bits, impellers and sockets turn on the barrel, blades and wheels
spin on a spindle across it, and the jackhammer's chisel recoils and strikes. They idle while you
carry them, spool up when you put them to work, and stop when the tank runs dry, so an empty
backtank is something you see in your hand before you swing at anything.

![All eight tools on a hotbar](branding/the-eight-tools.png)

---

## The tools

### Mining and harvesting

| Tool | What it does | Per tank |
|---|---|---|
| **Hand Drill** | A Mechanical Drill you can carry. Quick on anything a pickaxe or a shovel handles. | 900 blocks |
| **Pneumatic Jackhammer** | Shatters hard blocks in a fixed quarter-second — Obsidian and Deepslate take the same moment. Netherite tier. Slow and **free** on anything soft. | 90 hard blocks |
| **Tunnelling Drill** | Clears a 3x3 slice lying flat against the face you drilled — a wall if you drilled a wall, a trench if you drilled the floor. Charged once per burst, however many of the nine blocks were there. | 100 bursts |
| **Pneumatic Saw** | Fells a whole tree from one log, canopy and all. Bamboo, cane, cactus and chorus too. | 60 trees |

### Surface treatment

| Tool | What it does | Per tank |
|---|---|---|
| **Pneumatic Grinder** | Strips bark from a log, scrapes one stage of oxide off copper, takes the wax off a sealed block. | 300 surfaces |
| **Pneumatic Buffer** | Waxes copper by friction alone — no Honeycomb. Polishes a whole stack of Rose Quartz in one click. | 300 actions |

### Power and pickup

| Tool | What it does | Per tank |
|---|---|---|
| **Pneumatic Vacuum Wand** | Hold Right-Click and loose items and experience come to you from eight blocks out. A pulse with nothing in range is free. | 900 pulses |
| **Pneumatic Wrench** | Portable torque. Hold it against a shaft and it drives the network at 64 RPM with 1024 SU behind it — twice a Hand Crank's speed and four times its torque — for as long as you hold the button. | 450 pulses |

---

## How the air works

Wear a **Copper Backtank** (or a Netherite one) and the tools work. Take it off and they do not —
there is no durability to fall back on, and the bar under a tool in your hotbar is the tank's charge,
not the tool's.

Costs are stated as **uses per tank**, which is the unit Create already uses for the Extendo Grip
(1000 actions) and the Potato Cannon (200 shots), so these numbers can be read straight against
Create's own. A rating becomes a cost the way Create's does: `airInBacktank / usesPerTank`, charged
against the *emptiest* tank you are wearing — so a second backtank is a second tank, not a spare.

Two tools charge only for what they are for, which is deliberate rather than an oversight:

- The **Jackhammer** charges nothing on a soft block, and gives no speed there either. The air is
  what does the shattering.
- The **Saw** charges per *tree*, not per log. Chopping a plank is free.

The **Wrench** is the odd one out and worth explaining. Create's rotation is a graph of block
entities, so a source of it has to *be somewhere* — an item in a hand cannot join a kinetic network.
So while you hold the button the wrench keeps an invisible generator block in the empty space against
the face you aimed at, exactly where you would have placed a motor by hand. It holds a short lease
that only the wrench renews, so letting go, walking away, switching item, dying, disconnecting or
crashing all end it the same way: the renewals stop and the block deletes itself.

Everything is configurable in `config/createpneumatictools-server.toml`, including the jackhammer's
break time in ticks and the vacuum's radius. Set any tool's rating to **0** and it becomes free and
needs no backtank at all.

---

## Recipes

Seven of the nine are the same shape — a **working head**, a **Brass Sheet**, an **Andesite Alloy**
grip, stacked in a column:

```
 H       H = the working head
 S       S = Brass Sheet
 A       A = Andesite Alloy
```

| Tool | Head |
|---|---|
| Hand Drill | Mechanical Drill |
| Pneumatic Saw | Mechanical Saw |
| Pneumatic Wrench | Hand Crank |
| Pneumatic Grinder | Red Sand Paper |
| Pneumatic Buffer | Sand Paper |
| Pneumatic Vacuum Wand | Propeller |

The coarse paper makes the subtractive tool and the fine paper makes the finishing one, which is the
whole of what you need to remember about that pair.

The other two are upgrades of tools you already have:

```
 . O .          O = Powdered Obsidian        D D D          D = Mechanical Drill
 O D O          D = Hand Drill               D W D          W = Pneumatic Wrench
 . O .                                       D D D
   -> Pneumatic Jackhammer                     -> Tunnelling Drill
```

---

## Building

```bash
./gradlew build              # compile + jar
./gradlew runClient          # dev client
./gradlew runServer          # dev dedicated server (needs run/eula.txt)
./gradlew runGameTestServer  # automated in-world tests -- the real check
```

JDK 21. Art and the GameTest template are generated:

```bash
python3 tools/generate_models.py       # every 3D model: chassis, heads, moving parts
python3 tools/generate_textures.py     # the eight material panels
python3 tools/generate_logo.py         # the in-jar badge at 256
python3 tools/generate_logo.py branding/icon-512.png --size 512
python3 tools/generate_structures.py   # the empty GameTest template
python3 tools/check_lang.py            # every tooltip key resolves
```

See [CLAUDE.md](CLAUDE.md) for how the repo is put together and the things that will bite you.

---

## What is not here

Three tools from the original list are not built, each with a write-up in `docs/` explaining why and
what it would take:

- **Pneumatic Track Trolley** (`docs/rail-rider.md`) — buildable on Create's own `TravellingPoint`,
  which follows curves and takes junctions from a look vector. What is unsettled is moving a player
  smoothly, and what to do about Trains already using the track.
- **Pneumatic Stilts** (`docs/pneumatic-stilts.md`) — **dropped**, not deferred. The engineering
  came good — a leased platform under your feet, the wrench's self-deleting trick again — but any
  version of it puts a hidden, temporary, walkable floor in a shared world, and everything standing
  on yours falls when you walk away.
- **The wrench as a portable generator** (`docs/wrench-as-a-generator.md`) — Create's rotation is a
  graph of block entities, so a source of it has to be somewhere. The wrench that shipped puts a
  leased, invisible generator against the face you aim at, and keeps renewing it while you hold the
  button.

## Licence

MIT — see [LICENSE](LICENSE). Third-party notices are in [NOTICE.md](NOTICE.md). No Create art is
used or derived from; every sprite here is generated by this repo's own tooling.
