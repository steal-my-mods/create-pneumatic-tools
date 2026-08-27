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
	public final ModConfigSpec.DoubleValue jackhammerHardnessBias;

	public final ModConfigSpec.IntValue tunnelDrillUsesPerTank;
	public final ModConfigSpec.DoubleValue tunnelDrillSpeed;

	public final ModConfigSpec.IntValue borerUsesPerTank;
	public final ModConfigSpec.DoubleValue borerSpeed;

	public final ModConfigSpec.IntValue sawUsesPerTank;
	public final ModConfigSpec.DoubleValue sawSpeed;

	public final ModConfigSpec.IntValue grinderUsesPerTank;
	public final ModConfigSpec.IntValue bufferUsesPerTank;

	public final ModConfigSpec.IntValue vacuumUsesPerTank;
	public final ModConfigSpec.DoubleValue vacuumRadius;
	public final ModConfigSpec.IntValue vacuumInterval;
	public final ModConfigSpec.BooleanValue vacuumOnlyOwnDrops;

	public final ModConfigSpec.IntValue wrenchUsesPerTank;
	public final ModConfigSpec.IntValue wrenchInterval;
	public final ModConfigSpec.DoubleValue wrenchRpm;
	public final ModConfigSpec.DoubleValue wrenchStressUnits;
	public final ModConfigSpec.DoubleValue wrenchRange;

	/**
	 * Create's own default air in a Copper Backtank, quoted in the config comments.
	 *
	 * <p>Not read from Create: {@code AllConfigs.server()} is not loaded when this spec is built, and a
	 * comment is a string in a file rather than a live figure anyway. It is only ever used to tell a
	 * reader where the ceiling on a rating is; the arithmetic that matters happens in
	 * {@code BacktankUtil}, against whatever Create is actually configured for.
	 */
	private static final int AIR_IN_A_TANK = 900;

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
				"point of the tool: Obsidian and Deepslate take the same moment. 20 ticks is a second.",
				"Haste and Efficiency both shorten it -- they multiply the speed this figure was solved",
				"backwards out of, so a beacon or an Efficiency book makes the moment shorter without",
				"making it depend on hardness again. Mining Fatigue lengthens it the same way. So this",
				"is the time on a bare player, not a ceiling.")
			.defineInRange("jackhammerBreakTicks", 5, 1, 200);
		jackhammerHardnessBias = builder
			.comment("How much *faster* the jackhammer gets on harder blocks, as an exponent:",
				"    break time = jackhammerBreakTicks * (jackhammerMinHardness / hardness) ^ bias",
				"0 is a flat break time -- every qualifying block takes jackhammerBreakTicks, which is",
				"what the tool has always done and what its tooltip describes. Turn it up and the",
				"softest qualifying block still takes that long while harder ones take less: at 0.5,",
				"Obsidian breaks about four times quicker than Deepslate. Past about 1 there is nothing",
				"left to give -- a block cannot break in less than one tick, so everything from roughly",
				"fifteen hardness up lands on that floor and Obsidian, Ancient Debris and a Netherite",
				"Block all feel the same. Ignored entirely if jackhammerMinHardness is 0, since with no",
				"threshold there is no softest qualifying block to anchor the curve to.")
			.defineInRange("jackhammerHardnessBias", 0.0, 0.0, 2.0);
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

		builder.comment("5x5 Pneumatic Borer").push("borer");
		borerUsesPerTank = usesPerTank(builder, "borerUsesPerTank", 50,
			"Bursts a full Copper Backtank will fire. One burst is the whole 5x5 wall, charged once,",
			"however many of the twenty-five blocks were actually there. Half the Tunnelling Drill's",
			"count for nearly three times the blocks: the Borer moves more stone per tank and empties",
			"one far faster, which is the trade it is meant to be.");
		borerSpeed = builder
			.comment("Mining speed on anything a pickaxe or a shovel handles. Lower again than the",
				"Tunnelling Drill's -- every step up in width is a step down in speed per block.")
			.defineInRange("borerSpeed", 5.0, 1.0, 1000.0);
		builder.pop();

		builder.comment("Pneumatic Saw").push("saw");
		sawUsesPerTank = usesPerTank(builder, "sawUsesPerTank", 60,
			"Cuts a full Copper Backtank will pay for. Charged per *cut*, not per tree actually",
			"felled: on a tree that is once rather than once per log, and the whole canopy comes down",
			"for one payment. Only a cut that could start a felling is charged at all -- logs, roots,",
			"and the stalks (bamboo, cane, cactus, kelp, chorus). Leaves, pumpkins and melons are free,",
			"because they fell nothing: they are on Create's list for a *mounted* saw, which is not",
			"billed per cut. There is no cap on how big a tree may be, so a giant jungle one is a",
			"single payment and a single long tick: this is the one tool here with no ceiling on the",
			"work one click can ask for.");
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
			"Blocks a full Copper Backtank will seal. One waxed block is one use.");
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
		vacuumOnlyOwnDrops = builder
			.comment("Whether the wand leaves alone stacks another player dropped or died holding.",
				"Block and mob drops belong to nobody and are taken either way -- this is only about",
				"stacks with an owner on them. Off matches vanilla, where a dropped stack goes to",
				"whoever reaches it first; that is a different bargain when the reach is eight blocks",
				"and does not need line of sight, so a shared server usually wants this on.")
			.define("vacuumOnlyOwnDrops", false);
		builder.pop();

		builder.comment("Pneumatic Wrench").push("wrench");
		wrenchUsesPerTank = usesPerTank(builder, "wrenchUsesPerTank", 450,
			"Pulses of rotation a full Copper Backtank will drive. See wrenchInterval for how long a",
			"pulse lasts -- at the defaults a tank is about three and a half minutes of turning.");
		wrenchInterval = builder
			.comment("Ticks between charges while the wrench is driving. Every one costs one use.")
			.defineInRange("wrenchInterval", 10, 1, 200);
		wrenchRpm = builder
			.comment("Speed the wrench turns a network at. Create's Hand Crank, the hand tool this is",
				"the powered answer to, is 32 -- so the default here is twice a crank.")
			.defineInRange("wrenchRpm", 64.0, 1.0, 256.0);
		wrenchStressUnits = builder
			.comment("Stress Units the wrench supplies -- the whole figure, not a per-RPM one, and the",
				"same figure at every speed. A tank breathes at one rate, so the wrench delivers one",
				"amount of power: at half the speed it supplies twice the torque and at twice the",
				"speed half of it, which is what a real air motor does and what stops a wrench that",
				"joined somebody else's fast network being worth more than one driving its own.",
				"Create's Hand Crank manages 256 at its own 32 RPM, so this is four times a crank.",
				"That is the difference between only just turning one Mechanical Press and running one",
				"with the belts that feed it.")
			.defineInRange("wrenchStressUnits", 1024.0, 0.0, 1000000.0);
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
		String[] lines = new String[comment.length + 2];
		System.arraycopy(comment, 0, lines, 0, comment.length);
		lines[comment.length] = "Set to 0 to make the tool free and let it work with no backtank at all.";
		// The ceiling is Create's, not ours, and it is invisible without being told: a rating becomes a
		// cost as max(airInBacktank / usesPerTank, 1) in BacktankUtil.canAbsorbDamage, so once the
		// division reaches 1 a larger rating buys nothing. At Create's default 900 air that is 900 uses,
		// whatever number is written here.
		lines[comment.length + 1] = "Above " + AIR_IN_A_TANK + " this stops doing anything: a use cannot"
			+ " cost less than one air unit, so that is the most uses a tank can give.";
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

	public static float jackhammerHardnessBias() {
		return read(INSTANCE.jackhammerHardnessBias).floatValue();
	}

	public static int tunnelDrillUsesPerTank() {
		return read(INSTANCE.tunnelDrillUsesPerTank);
	}

	public static float tunnelDrillSpeed() {
		return read(INSTANCE.tunnelDrillSpeed).floatValue();
	}

	public static int borerUsesPerTank() {
		return read(INSTANCE.borerUsesPerTank);
	}

	public static float borerSpeed() {
		return read(INSTANCE.borerSpeed).floatValue();
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

	public static boolean vacuumOnlyOwnDrops() {
		return read(INSTANCE.vacuumOnlyOwnDrops);
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

	public static double wrenchStressUnits() {
		return read(INSTANCE.wrenchStressUnits);
	}

	public static double wrenchRange() {
		return read(INSTANCE.wrenchRange);
	}
}
