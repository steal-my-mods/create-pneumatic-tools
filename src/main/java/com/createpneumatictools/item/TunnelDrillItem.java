package com.createpneumatictools.item;

import com.createpneumatictools.CPTConfig;
import com.createpneumatictools.registry.CPTTags;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Nine Mechanical Drills in one: a dig clears the 3x3 slice of wall in front of you.
 *
 * <p>All of the work is in {@link BurstDiggerItem}; this is the 3x3 of it, and the first rung of the
 * two-step ladder that ends at the {@link PneumaticBorerItem}.
 */
public class TunnelDrillItem extends BurstDiggerItem {

	public TunnelDrillItem(Properties properties) {
		super(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, CPTTags.DRILL_MINEABLE, properties);
	}

	@Override
	protected int radius() {
		return 1;
	}

	@Override
	public int usesPerTank() {
		return CPTConfig.tunnelDrillUsesPerTank();
	}

	@Override
	public float poweredSpeed(BlockState state, Level level, BlockPos pos) {
		return digs(state) ? CPTConfig.tunnelDrillSpeed() : 1.0F;
	}
}
