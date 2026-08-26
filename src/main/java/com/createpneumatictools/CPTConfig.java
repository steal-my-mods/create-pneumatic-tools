package com.createpneumatictools;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Server config.
 *
 * <p>Every tool's running cost is a <em>uses per tank</em> rating, the unit Create states the Extendo
 * Grip and the Potato Cannon in, so these numbers can be compared with Create's own without
 * converting. See {@link com.createpneumatictools.air.AirSupply} for how a rating becomes a cost.
 * Zero means the tool costs nothing and needs no backtank.
 *
 * <p>SERVER rather than COMMON because the ratings decide whether a tool works at all, and the
 * mining-speed hook reads them on both sides — a client with different numbers would draw a crack
 * overlay the server disagrees with.
 */
public class CPTConfig {

	public static final ModConfigSpec SPEC;
	public static final CPTConfig INSTANCE;

	public final ModConfigSpec.IntValue handDrillUsesPerTank;
	public final ModConfigSpec.DoubleValue handDrillSpeed;

	public final ModConfigSpec.IntValue jackhammerUsesPerTank;
	public final ModConfigSpec.DoubleValue jackhammerMinHardness;
	public final ModConfigSpec.IntValue jackhammerBreakTicks;

	public final ModConfigSpec.IntValue tunnelDrillUsesPerTank;
	public final ModConfigSpec.DoubleValue tunnelDrillSpeed;

	public final ModConfigSpec.IntValue sawUsesPerTank;
	public final ModConfigSpec.DoubleValue sawSpeed;

	public final ModConfigSpec.IntValue grinderUsesPerTank;
	public final ModConfigSpec.IntValue bufferUsesPerTank;

	public final ModConfigSpec.IntValue vacuumUsesPerTank;
	public final ModConfigSpec.DoubleValue vacuumRadius;
	public final ModConfigSpec.IntValue vacuumInterval;

	public final ModConfigSpec.IntValue wrenchUsesPerTank;
	public final ModConfigSpec.IntValue wrenchInterval;
	public final ModConfigSpec.DoubleValue wrenchRpm;
	public final ModConfigSpec.DoubleValue wrenchCapacity;
	public final ModConfigSpec.DoubleValue wrenchRange;

	private CPTConfig(ModConfigSpec.Builder builder) {
		builder.comment("Hand Drill").push("hand_drill");
		handDrillUsesPerTank = usesPerTank(builder, "handDrillUsesPerTank", 900,
			"Blocks a full Copper Backtank will break. 900 is one air unit per block, the same rate",
			"the tank breathes at underwater.");
		handDrillSpeed = builder
			.comment("Mining speed on anything a pickaxe or a shovel handles. A Diamond Pickaxe is 8.")
			.defineInRange("handDrillSpeed", 9.0, 1.0, 1000.0);
		builder.pop();

		builder.comment("Pneumatic Jackhammer").push("jackhammer");
		jackhammerUsesPerTank = usesPerTank(builder, "jackhammerUsesPerTank", 90,
			"Hard blocks a full Copper Backtank will shatter. Only blocks past minHardness are",
			"charged for -- on anything softer the jackhammer is an ordinary slow pickaxe and free.");
		jackhammerMinHardness = builder
			.comment("Block hardness the jackhammer starts working on. Deepslate is 3, Obsidian 50,",
				"Stone 1.5.")
			.defineInRange("jackhammerMinHardness", 3.0, 0.0, 10000.0);
		jackhammerBreakTicks = builder
			.comment("Ticks to shatter a qualifying block, whatever its hardness. This is the whole",
				"point of the tool: Obsidian and Deepslate take the same moment. 20 ticks is a second.")
			.defineInRange("jackhammerBreakTicks", 5, 1, 200);
		builder.pop();

		builder.comment("3x3 Tunnelling Drill").push("tunnel_drill");
		tunnelDrillUsesPerTank = usesPerTank(builder, "tunnelDrillUsesPerTank", 100,
			"Bursts a full Copper Backtank will fire. One burst is the whole 3x3 wall, charged once,",
			"however many of the nine blocks were actually there.");
		tunnelDrillSpeed = builder
			.comment("Mining speed on anything a pickaxe or a shovel handles. Lower than the Hand",
				"Drill's: the tunneller trades speed per block for nine of them at once.")
			.defineInRange("tunnelDrillSpeed", 7.0, 1.0, 1000.0);
		builder.pop();

		builder.comment("Pneumatic Saw").push("saw");
		sawUsesPerTank = usesPerTank(builder, "sawUsesPerTank", 60,
			"Trees a full Copper Backtank will fell. Charged per tree, not per log -- the whole",
			"canopy comes down for one payment.");
		sawSpeed = builder
			.comment("Mining speed on anything an axe handles. A Diamond Axe is 8.")
			.defineInRange("sawSpeed", 9.0, 1.0, 1000.0);
		builder.pop();

		builder.comment("Pneumatic Grinder").push("grinder");
		grinderUsesPerTank = usesPerTank(builder, "grinderUsesPerTank", 300,
			"Surfaces a full Copper Backtank will strip, scrape or unwax.");
		builder.pop();

		builder.comment("Pneumatic Buffer").push("buffer");
		bufferUsesPerTank = usesPerTank(builder, "bufferUsesPerTank", 300,
			"Actions a full Copper Backtank will pay for. One waxed block or one polished item is",
			"one action, so buffing a stack of 64 costs 64 of these.");
		builder.pop();

		builder.comment("Pneumatic Vacuum Wand").push("vacuum");
		vacuumUsesPerTank = usesPerTank(builder, "vacuumUsesPerTank", 900,
			"Pulses a full Copper Backtank will pay for. See vacuumInterval for how long a pulse",
			"lasts -- at the defaults a tank is three minutes of continuous suction.");
		vacuumRadius = builder
			.comment("How far the suction reaches, in blocks.")
			.defineInRange("vacuumRadius", 8.0, 1.0, 32.0);
		vacuumInterval = builder
			.comment("Ticks between pulses while the button is held. Every pulse costs one use.")
			.defineInRange("vacuumInterval", 4, 1, 100);
		builder.pop();

		builder.comment("Pneumatic Wrench").push("wrench");
		wrenchUsesPerTank = usesPerTank(builder, "wrenchUsesPerTank", 450,
			"Pulses of rotation a full Copper Backtank will drive. See wrenchInterval for how long a",
			"pulse lasts -- at the defaults a tank is about three and a half minutes of turning.");
		wrenchInterval = builder
			.comment("Ticks between charges while the wrench is driving. Every one costs one use.")
			.defineInRange("wrenchInterval", 10, 1, 200);
		wrenchRpm = builder
			.comment("Speed the wrench turns a network at. Create's Hand Crank, which this replaces,",
				"is 32.")
			.defineInRange("wrenchRpm", 32.0, 1.0, 256.0);
		wrenchCapacity = builder
			.comment("Stress capacity the wrench supplies, per RPM. Create's Hand Crank is 8, so at",
				"the default speed the wrench is worth 256 Stress Units -- enough to start a mixer,",
				"not enough to run a factory.")
			.defineInRange("wrenchCapacity", 8.0, 0.0, 10000.0);
		wrenchRange = builder
			.comment("How far you can get from the shaft before the wrench lets go of it, in blocks.")
			.defineInRange("wrenchRange", 8.0, 1.0, 32.0);
		builder.pop();
	}

	/**
	 * A rating field, with the "0 is free" note appended so it appears against every one of them
	 * rather than being explained once at the top of a file people read in fragments.
	 */
	private static ModConfigSpec.IntValue usesPerTank(ModConfigSpec.Builder builder, String name,
		int fallback, String... comment) {
		String[] lines = new String[comment.length + 1];
		System.arraycopy(comment, 0, lines, 0, comment.length);
		lines[comment.length] = "Set to 0 to make the tool free and let it work with no backtank at all.";
		return builder.comment(lines)
			.defineInRange(name, fallback, 0, 1000000);
	}

	static {
		Pair<CPTConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(CPTConfig::new);
		INSTANCE = pair.getLeft();
		SPEC = pair.getRight();
	}

	/**
	 * Reading a config value before its file is loaded throws. Most callers are deep inside a player
	 * interaction, where it always is loaded — but the item's own tooltip and the mining-speed hook
	 * can be reached from a menu at the title screen, and a crash there would be a poor trade for a
	 * number with a perfectly good default sitting next to it.
	 */
	private static <T> T read(ModConfigSpec.ConfigValue<T> value) {
		return SPEC.isLoaded() ? value.get() : value.getDefault();
	}

	public static int handDrillUsesPerTank() {
		return read(INSTANCE.handDrillUsesPerTank);
	}

	public static float handDrillSpeed() {
		return read(INSTANCE.handDrillSpeed).floatValue();
	}

	public static int jackhammerUsesPerTank() {
		return read(INSTANCE.jackhammerUsesPerTank);
	}

	public static float jackhammerMinHardness() {
		return read(INSTANCE.jackhammerMinHardness).floatValue();
	}

	public static int jackhammerBreakTicks() {
		return read(INSTANCE.jackhammerBreakTicks);
	}

	public static int tunnelDrillUsesPerTank() {
		return read(INSTANCE.tunnelDrillUsesPerTank);
	}

	public static float tunnelDrillSpeed() {
		return read(INSTANCE.tunnelDrillSpeed).floatValue();
	}

	public static int sawUsesPerTank() {
		return read(INSTANCE.sawUsesPerTank);
	}

	public static float sawSpeed() {
		return read(INSTANCE.sawSpeed).floatValue();
	}

	public static int grinderUsesPerTank() {
		return read(INSTANCE.grinderUsesPerTank);
	}

	public static int bufferUsesPerTank() {
		return read(INSTANCE.bufferUsesPerTank);
	}

	public static int vacuumUsesPerTank() {
		return read(INSTANCE.vacuumUsesPerTank);
	}

	public static double vacuumRadius() {
		return read(INSTANCE.vacuumRadius);
	}

	public static int vacuumInterval() {
		return read(INSTANCE.vacuumInterval);
	}

	public static int wrenchUsesPerTank() {
		return read(INSTANCE.wrenchUsesPerTank);
	}

	public static int wrenchInterval() {
		return read(INSTANCE.wrenchInterval);
	}

	public static float wrenchRpm() {
		return read(INSTANCE.wrenchRpm).floatValue();
	}

	public static double wrenchCapacity() {
		return read(INSTANCE.wrenchCapacity);
	}

	public static double wrenchRange() {
		return read(INSTANCE.wrenchRange);
	}
}
