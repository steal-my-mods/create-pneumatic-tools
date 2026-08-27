<!--
The CurseForge project page copy. Kept here so the store description is versioned alongside the
mod it describes, and so updating it is an edit rather than a rewrite from memory.

The SUMMARY below goes in the project's one-line Summary field. Everything under the rule goes in
the Description field, as markdown.

Before pasting this into the project page, do the one thing that cannot be done from here:
CurseForge hosts images itself, so upload branding/tools-in-hand.png and branding/the-nine-tools.png
through the project's gallery, then replace the two IMAGE markers below with the URLs it gives you.
Leaving a marker in place ships a comment where a screenshot should be, which is invisible in the
rendered page -- the section simply has no picture in it.

The icon is branding/icon-512.png, uploaded separately as the project's avatar.
-->

SUMMARY: Handheld tools that run off your Create backtank: a drill, a jackhammer, a 3x3 tunneller, a 5x5 borer, a tree-felling saw, a grinder, a buffer, a vacuum wand, and a wrench that powers a kinetic network while you hold it.

---

# Create: Pneumatic Tools

A Copper Backtank is already a portable power supply. Create just spends it on breathing and on the Extendo Grip. This addon spends it on work.

Nine handheld tools that run off the tank on your back. No durability and no repairs: they run while there is air, and they slow down when there isn't. Every one of them is a real 3D model with a part that moves. Bits and sockets turn, the saw's blade and the grinder's disc spin out on the left where you can watch them work, the buffer's pad faces forward and turns on the barrel's own axis, the borer's whole face plate turns with its cutters, and the jackhammer's chisel hammers. They idle while you carry them and spool up when you put them to work.

<!-- IMAGE: tools-in-hand.png -- replace with the gallery URL -->

## The tools

**Digging**

- **Hand Drill.** Chews through anything a pickaxe or a shovel handles. About 900 blocks on a full Copper Backtank.
- **Pneumatic Jackhammer.** Shatters hard blocks in a quarter of a second, whether that block is Deepslate or Obsidian. Slow and free on anything soft.
- **Tunnelling Drill.** Clears a 3x3 slice lying flat against the face you drilled. Drill a wall and you get a corridor; drill the floor and you get a trench. One charge per burst, however many of the nine blocks were actually there.
- **Pneumatic Borer.** The same burst five blocks across instead of three: twenty-five blocks at a time. Wider and slower per block than the Tunnelling Drill and out of half as many bursts a tank, so it moves far more stone and empties a tank far faster. Built in a Mechanical Crafter from the Tunnelling Drill, sixteen more Mechanical Drills, four Precision Mechanisms and four Brass Sheets — one Mechanical Drill for every block it breaks.
- **Pneumatic Saw.** One cut fells the whole tree, trunk and canopy, for a single charge. Also harvests bamboo, sugar cane, cactus, kelp and chorus. It charges per cut rather than per tree, and only for a cut that could start a felling: logs, and the stalks it harvests. Leaves, pumpkins, melons and planks are free — on those it is just a very fast axe.

Tier is never the limit. These are diamond and netherite tier, which in 1.21 both clear vanilla's highest gate, so everything they break they drop — Obsidian, Ancient Debris and Netherite Blocks included, and a Borer burst takes twenty-five of them at once.

**Finishing**

- **Pneumatic Grinder.** Strips the bark off a log, scrapes a stage of oxide off copper, or takes the wax off a sealed block.
- **Pneumatic Buffer.** Seals copper with the wax built into it: no Honeycomb to carry and none consumed. It is crafted around a Honeycomb Block, which is where that wax came from and the only time you pay for it.

**Power and pickup**

- **Pneumatic Vacuum Wand.** Hold right-click and loose items and experience come to you from eight blocks out. A pulse that catches nothing is free, so leaning on the button costs you nothing.
- **Pneumatic Wrench.** Hold it against a still shaft and it drives your kinetic network at 64 RPM with 1024 Stress Units behind it, for as long as you hold the button. Twice a Hand Crank's speed, four times its output, and no hunger. Held against a network that is *already* turning it matches that network's speed and direction instead and lends the same 1024 SU, which is what you want when a line is overstressed rather than stopped. It is worth those 1024 SU at any speed: half the RPM, twice the torque. Let go, walk away or run dry and it stops. Nothing is left behind.

<!-- IMAGE: the-nine-tools.png -- replace with the gallery URL -->

## How the air works

Wear a backtank, hold a tool, use it. That is the whole of it.

Every tool is rated in **uses per tank**, the same unit Create rates the Extendo Grip and the Potato Cannon in, so you can read these numbers against Create's own without converting anything. All of them are in the config, and setting one to 0 makes that tool free.

Running dry breaks nothing. The digging tools keep digging, just no faster than your bare hands, and the moving parts stop turning, so an empty tank is something you notice in your hand before you notice it anywhere else.

The five digging tools take **Fortune, Silk Touch and Efficiency** from an anvil. One level of Efficiency is worth one level of Haste, so a book and a beacon stack, and on the Jackhammer it shortens the fixed break time without letting hardness back into it.

## Getting them

Most of these are one head over a Brass Sheet over an Andesite Alloy: a Mechanical Drill makes the Hand Drill, a Mechanical Saw makes the Saw, a Propeller makes the Vacuum Wand, a Hand Crank makes the Wrench, Red Sand Paper makes the Grinder and a Honeycomb Block makes the Buffer. The Jackhammer and the Tunnelling Drill are both built out of a Hand Drill you have already made.

The Borer is the one thing here you cannot make at a workbench. Five blocks across is a recipe five blocks across, so it goes in a **Mechanical Crafter** — the only tool in the mod that asks you to have built a *machine* first, rather than just another tool.

## Requires

**Minecraft 1.21.1 · NeoForge 21.1.219 or later · Create 6.x**

Create 6 is required and Create 7 is not accepted: the dependency range is `[6.0.0,7.0)`, so this refuses to install alongside a Create it has not been built against rather than breaking once it is there.

Open source under the MIT licence. Source, the full write-up of how everything works, and the issue tracker are on [GitHub](https://github.com/steal-my-mods/create-pneumatic-tools).
