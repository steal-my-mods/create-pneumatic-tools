package com.createpneumatictools.item;

import com.createpneumatictools.CPTConfig;
import com.createpneumatictools.registry.CPTTags;
import com.createpneumatictools.tool.Excavation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Eight Mechanical Drills around a wrench: one dig clears a 3x3 slice of the wall in front of you.
 *
 * <p>The slice is perpendicular to the direction you are <em>looking</em>, not to the face you clicked
 * — vanilla does not hand {@code mineBlock} a face, and the look direction is what a player thinks
 * they are tunnelling along anyway. Look at a floor and it takes a 3x3 of floor; look at a wall and it
 * takes a 3x3 of wall.
 *
 * <p>One burst is charged once. Nine blocks or two, the tank pays the same, so the tunneller is worth
 * carrying only where the wall is actually solid — which is the trade against the Hand Drill, along
 * with a lower speed per block.
 */
public class TunnelDrillItem extends PneumaticDiggerItem {

	/** How often a broken neighbour puffs. Create's own drill contraption uses the same figure. */
	private static final float EFFECT_CHANCE = 0.25F;

	public TunnelDrillItem(Properties properties) {
		super(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, CPTTags.DRILL_MINEABLE, properties);
	}

	@Override
	public int usesPerTank() {
		return CPTConfig.tunnelDrillUsesPerTank();
	}

	@Override
	public float poweredSpeed(BlockState state, Level level, BlockPos pos) {
		return digs(state) ? CPTConfig.tunnelDrillSpeed() : 1.0F;
	}

	@Override
	protected void dig(ItemStack stack, Level level, BlockState state, BlockPos pos, Player player) {
		// A neighbour taken down by the burst goes through Create's break helper, which tells the tool
		// it was used -- so without this the first block would tunnel to the world border.
		if (Excavation.cascading())
			return;
		if (!digs(state))
			return;
		if (!spendAir(player))
			return;

		Vec3 look = player.getViewVector(1.0F);
		Direction.Axis along = Direction.getNearest(look.x, look.y, look.z)
			.getAxis();
		Excavation.cascade(() -> {
			for (BlockPos neighbour : wallAround(pos, along))
				breakIfWorthIt(stack, level, neighbour, player);
		});
	}

	/** The eight positions around {@code centre} in the plane {@code along} runs through. */
	private static Iterable<BlockPos> wallAround(BlockPos centre, Direction.Axis along) {
		java.util.List<BlockPos> wall = new java.util.ArrayList<>(8);
		for (int a = -1; a <= 1; a++)
			for (int b = -1; b <= 1; b++) {
				if (a == 0 && b == 0)
					continue;
				wall.add(switch (along) {
					case X -> centre.offset(0, a, b);
					case Y -> centre.offset(a, 0, b);
					case Z -> centre.offset(a, b, 0);
				});
			}
		return wall;
	}

	private void breakIfWorthIt(ItemStack stack, Level level, BlockPos pos, Player player) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || !digs(state))
			return;
		// Bedrock and friends report -1. Blocks that pop on contact are skipped too: sweeping up a
		// wall of torches is not what the burst is for, and they are free to break by hand.
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0F || hardness == 0.0F)
			return;
		if (!canHarvest(stack, state))
			return;
		Excavation.breakAs(level, pos, player, stack, EFFECT_CHANCE);
	}
}
