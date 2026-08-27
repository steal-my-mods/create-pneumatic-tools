package com.createpneumatictools.item;

import com.createpneumatictools.CPTConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The one-block breaker: netherite-tier, and it shatters anything hard in a fixed handful of ticks
 * regardless of how hard it actually is.
 *
 * <p>A fixed <em>time</em> rather than a fixed speed is the whole idea. Vanilla break time is
 * {@code hardness * 30 / speed}, so quoting a speed makes Obsidian at 50 hardness take thirty-three
 * times as long as Deepslate at 3 — which is exactly the difference this tool exists to erase. Asking
 * for a number of ticks and solving for the speed instead means a config that says "five ticks" gets
 * five ticks on both.
 *
 * <p>Below {@link CPTConfig#jackhammerMinHardness} it does nothing at all: no boost, and no charge
 * either. Digging out the dirt around a vein with one in your hand is slow and free, which is the
 * trade against the Hand Drill rather than an oversight.
 */
public class JackhammerItem extends PneumaticDiggerItem {

	/** Vanilla's constant: a block takes {@code hardness * 30 / speed} ticks to break by hand. */
	private static final float TICKS_PER_HARDNESS = 30.0F;

	public JackhammerItem(Properties properties) {
		super(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, BlockTags.MINEABLE_WITH_PICKAXE, properties);
	}

	@Override
	public int usesPerTank() {
		return CPTConfig.jackhammerUsesPerTank();
	}

	@Override
	public float poweredSpeed(BlockState state, Level level, BlockPos pos) {
		if (!shatters(state, level, pos))
			return 1.0F;
		float hardness = hardness(state, level, pos);
		// Solved backwards out of a time rather than quoted as a speed, which is the whole reason the
		// tool erases the difference between Deepslate and Obsidian: vanilla's break time is
		// hardness * 30 / speed, so asking for ticks and dividing is what makes "five ticks" mean five
		// ticks on both.
		float flat = hardness * TICKS_PER_HARDNESS / CPTConfig.jackhammerBreakTicks();

		// And optionally tilted so that harder is *quicker* rather than merely equal. The law is
		//     break time = breakTicks * (minHardness / hardness) ^ bias
		// which rearranges to the factor below; at bias 0 it is exactly 1 and this is the flat tool.
		// The early return is not just for speed, though this does run on every break-speed event on
		// both sides: it also keeps the default bit-identical to what the tool did before the knob
		// existed, rather than bit-identical-in-theory via Math.pow(x, 0).
		float bias = CPTConfig.jackhammerHardnessBias();
		float anchor = CPTConfig.jackhammerMinHardness();
		if (bias <= 0.0F || anchor <= 0.0F)
			return flat;
		return flat * (float) Math.pow(hardness / anchor, bias);
	}

	@Override
	protected void dig(ItemStack stack, Level level, BlockState state, BlockPos pos, Player player) {
		if (shatters(state, level, pos))
			spendAir(player);
	}

	private boolean shatters(BlockState state, Level level, BlockPos pos) {
		if (!digs(state))
			return false;
		float hardness = hardness(state, level, pos);
		// Unbreakable blocks report -1. Left out explicitly: without this they would sail past a
		// minHardness of 0 and be handed a negative mining speed.
		return hardness >= CPTConfig.jackhammerMinHardness() && hardness > 0.0F;
	}
}
