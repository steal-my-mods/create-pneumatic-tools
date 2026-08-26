package com.createpneumatictools.item;

import com.createpneumatictools.CPTConfig;
import com.createpneumatictools.registry.CPTTags;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Mechanical Drill you can carry: diamond-tier, quick on anything a pickaxe or a shovel handles, and
 * paid for one block at a time.
 *
 * <p>The plainest tool in the set, and the yardstick the other three are balanced against — a full
 * Copper Backtank is 900 blocks, which is the same 900 the tank is worth in seconds of breathing.
 */
public class HandDrillItem extends PneumaticDiggerItem {

	public HandDrillItem(Properties properties) {
		super(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, CPTTags.DRILL_MINEABLE, properties);
	}

	@Override
	public int usesPerTank() {
		return CPTConfig.handDrillUsesPerTank();
	}

	@Override
	public float poweredSpeed(BlockState state, Level level, BlockPos pos) {
		return digs(state) ? CPTConfig.handDrillSpeed() : 1.0F;
	}

	@Override
	protected void dig(ItemStack stack, Level level, BlockState state, BlockPos pos, Player player) {
		if (digs(state))
			spendAir(player);
	}
}
