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
import net.minecraft.world.level.block.ChorusPlantBlock;
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
 * <p>The tank pays once per <em>cut</em> — which on a tree is once rather than once per log — and only
 * for a cut on something a felling can actually be started from. See {@link #fells}. There is no cap on
 * how big a tree may be: Create's own saw has none either, and a capped saw that silently leaves half a
 * canopy standing is worse to use than a slow one.
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

	/**
	 * Whether a cut here is the saw doing its job — starting a felling or a harvest — rather than just
	 * breaking one block quickly.
	 *
	 * <p>Deliberately <b>narrower than {@link SawBlockEntity#isSawable}</b>, which is Create's contract
	 * for what a <em>mounted</em> saw will process and also covers leaves, pumpkins and melons. That is
	 * the right list for a saw bolted to a contraption, which meets those as it sweeps past and is not
	 * billed for them; it is the wrong list for a tool charged per cut, where every entry is a block
	 * that costs a full use — a sixtieth of a tank at the defaults — to break by hand while felling
	 * nothing. Leaves were the worst of it: they come down free <em>inside</em> a felling, through the
	 * cascade guard, so a leaf broken on its own was the tool's highest price for its least work.
	 * Below this predicate the saw is simply a fast axe, which is what it already was on planks.
	 *
	 * <p>This stays a question about the block's <b>type</b>, and must never become a question about
	 * what the search found. A cut that fells nothing still has to be charged wherever a cut
	 * <em>could</em> have felled something: a tree taken down from the top is a run of cuts that each
	 * fell nothing, and an outcome-based charge would bring the whole thing down for no air at all.
	 *
	 * <p>{@code ChorusPlantBlock} directly rather than Create's own {@code TreeCutter.isChorus}, which
	 * also matches a chorus <em>flower</em>. A flower is not in {@code isSawable}, so accepting one here
	 * would quietly widen what this tool touches at the moment of narrowing it.
	 */
	private static boolean fells(BlockState state) {
		return TreeCutter.isLog(state) || TreeCutter.isRoot(state) || TreeCutter.isVerticalPlant(state)
			|| state.getBlock() instanceof ChorusPlantBlock
			|| TreeCutter.canDynamicTreeCutFrom(state.getBlock());
	}

	@Override
	protected void dig(ItemStack stack, Level level, BlockState state, BlockPos pos, Player player) {
		// Every log the felling takes down is reported back to this tool as a block it mined.
		if (Excavation.cascading())
			return;
		if (!fells(state))
			return;
		// Charged per cut, and deliberately before the search rather than after it. Making the charge
		// conditional on the felling having found something looks fairer and is not: a tree taken
		// down from the top is a run of cuts that each fell nothing, so it would be a whole tree at
		// saw speed for no air at all. One use per cut is the same bargain the Hand Drill strikes,
		// and "once per tree, not per log" is kept by the cascade guard above, not by this. What
		// stops that unconditional charge reaching cuts that could never fell anything is fells(),
		// which is a test of the block's type and so is safe to ask before the search.
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
