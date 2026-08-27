package com.createpneumatictools.item;

import com.createpneumatictools.CPTConfig;
import com.createpneumatictools.tool.Excavation;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.kinetics.saw.TreeCutter;
import com.simibubi.create.foundation.utility.AbstractBlockBreakQueue;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Mechanical Saw in your hand. Cut one log and the whole tree comes down, canopy and all.
 *
 * <p>The felling is Create's own {@link TreeCutter}, reached exactly the way {@link SawBlockEntity}
 * reaches it, so a hand saw and a mounted saw agree about what a tree is — including the awkward
 * cases, which is most of the value: bamboo and sugar cane are walked upwards, chorus is flood-filled,
 * a Dynamic Trees branch is handed back to that mod, and a log with more log underneath is refused
 * rather than dropping a treehouse on your head.
 *
 * <p>Timing matters here. {@code TreeCutter.findTree} is called with the position of the block being
 * broken and deliberately excludes it, so it has to run while that block is still standing — which it
 * is, because vanilla calls {@code ItemStack.mineBlock} before {@code removeBlock}. Felling from a
 * break event afterwards would need the state passing back in by hand.
 *
 * <p>The tank pays once per tree, not once per log. There is no cap on how big a tree may be: Create's
 * own saw has none either, and a capped saw that silently leaves half a canopy standing is worse to
 * use than a slow one.
 */
public class PneumaticSawItem extends PneumaticDiggerItem {

	public PneumaticSawItem(Properties properties) {
		super(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, BlockTags.MINEABLE_WITH_AXE, properties);
	}

	@Override
	public int usesPerTank() {
		return CPTConfig.sawUsesPerTank();
	}

	@Override
	public float poweredSpeed(BlockState state, Level level, BlockPos pos) {
		return digs(state) ? CPTConfig.sawSpeed() : 1.0F;
	}

	@Override
	protected void dig(ItemStack stack, Level level, BlockState state, BlockPos pos, Player player) {
		// Every log the felling takes down is reported back to this tool as a block it mined.
		if (Excavation.cascading())
			return;
		if (!SawBlockEntity.isSawable(state))
			return;
		// Charged per cut, and deliberately before the search rather than after it. Making the charge
		// conditional on the felling having found something looks fairer and is not: a tree taken
		// down from the top is a run of cuts that each fell nothing, so it would be a whole tree at
		// saw speed for no air at all. One use per cut is the same bargain the Hand Drill strikes,
		// and "once per tree, not per log" is kept by the cascade guard above, not by this.
		if (!spendAir(player))
			return;

		Excavation.cascade(() -> {
			Optional<AbstractBlockBreakQueue> dynamic =
				TreeCutter.findDynamicTree(state.getBlock(), pos);
			AbstractBlockBreakQueue tree =
				dynamic.orElseGet(() -> TreeCutter.findTree(level, pos, state));
			tree.destroyBlocks(level, stack, player, Excavation.dropInPlace(level));
		});
	}
}
