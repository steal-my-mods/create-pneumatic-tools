<!--
The CurseForge project page copy. Kept here so the store description is versioned alongside the
mod it describes, and so updating it is an edit rather than a rewrite from memory.

The SUMMARY below goes in the project's one-line Summary field. Everything under the rule goes in
the Description field, as markdown.

Image placeholders are marked TODO. CurseForge hosts images itself, so upload
branding/tools-in-hand.png and branding/the-eight-tools.png through its gallery first, then paste
back the URLs it gives you.
-->

SUMMARY: Handheld tools that run off your Create backtank: a drill, a jackhammer, a tunnelling drill, a tree-felling saw, a grinder, a buffer, a vacuum wand, and a wrench that powers a kinetic network while you hold it.

---

# Create: Pneumatic Tools

A Copper Backtank is already a portable power supply. Create just spends it on breathing and on the Extendo Grip. This addon spends it on work.

Eight handheld tools that run off the tank on your back. No durability and no repairs: they run while there is air, and they slow down when there isn't. Every one of them is a real 3D model with a part that moves. Bits and sockets turn, blades spin, the jackhammer's chisel hammers. They idle while you carry them and spool up when you put them to work.

<!-- TODO: tools-in-hand.png -->

## The tools

**Digging**

- **Hand Drill.** Chews through anything a pickaxe or a shovel handles. About 900 blocks on a full Copper Backtank.
- **Pneumatic Jackhammer.** Shatters hard blocks in a quarter of a second, whether that block is Deepslate or Obsidian. Slow and free on anything soft.
- **Tunnelling Drill.** Clears a 3x3 slice lying flat against the face you drilled. Drill a wall and you get a corridor; drill the floor and you get a trench. One charge per burst, however many of the nine blocks were actually there.
- **Pneumatic Saw.** One cut fells the whole tree, trunk and canopy, for a single charge. Also harvests bamboo, sugar cane, cactus and chorus.

**Finishing**

- **Pneumatic Grinder.** Strips the bark off a log, scrapes a stage of oxide off copper, or takes the wax off a sealed block.
- **Pneumatic Buffer.** Waxes copper by friction alone, with no Honeycomb. Polishes a whole stack of Rose Quartz in one click.

**Power and pickup**

- **Pneumatic Vacuum Wand.** Hold right-click and loose items and experience come to you from eight blocks out. A pulse that catches nothing is free, so leaning on the button costs you nothing.
- **Pneumatic Wrench.** Hold it against a shaft and it drives your kinetic network at 64 RPM with 1024 Stress Units behind it, for as long as you hold the button. Twice a Hand Crank's speed, four times its torque, and no hunger. Let go, walk away or run dry and it stops. Nothing is left behind.

<!-- TODO: the-eight-tools.png -->

## How the air works

Wear a backtank, hold a tool, use it. That is the whole of it.

Every tool is rated in **uses per tank**, the same unit Create rates the Extendo Grip and the Potato Cannon in, so you can read these numbers against Create's own without converting anything. All of them are in the config, and setting one to 0 makes that tool free.

Running dry breaks nothing. The digging tools keep digging, just no faster than your bare hands, and the moving parts stop turning, so an empty tank is something you notice in your hand before you notice it anywhere else.

The four digging tools take **Fortune and Silk Touch** from an anvil.

## Getting them

Most of these are one Create machine over a Brass Sheet over an Andesite Alloy: a Mechanical Drill makes the Hand Drill, a Mechanical Saw makes the Saw, a Propeller makes the Vacuum Wand, a Hand Crank makes the Wrench. The Jackhammer and the Tunnelling Drill are built out of the tools you have already made.

## Requires

**Minecraft 1.21.1, NeoForge, and Create 6.0 or later**

Open source under the MIT licence. Source, the full write-up of how everything works, and the issue tracker are on [GitHub](https://github.com/steal-my-mods/create-pneumatic-tools).
