package com.createpneumatictools.item;

import com.createpneumatictools.CPTConfig;
import com.createpneumatictools.registry.CPTTags;
import com.createpneumatictools.tool.Excavation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Eight Mechanical Drills around a wrench: one dig clears a 3x3 slice of the wall in front of you.
 *
 * <p>The slice lies flat against the <em>face you drilled</em>. Drill the top of a block and the burst
 * takes a 3x3 of floor; drill the side of one and it takes a 3x3 of wall. See {@link #plane} for how
 * that face is found, and for what happens when it cannot be.
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

		Direction.Axis along = plane(player, pos);
		Excavation.cascade(() -> {
			for (BlockPos neighbour : wallAround(pos, along))
				breakIfWorthIt(stack, level, neighbour, player);
		});
	}

	/**
	 * The axis the 3x3 slice stands perpendicular to: the one running through the face just drilled.
	 *
	 * <p>Vanilla does not hand {@code mineBlock} a face, so this re-casts the player's own aim to find
	 * one. That is worth the cast because looking and facing part company in the tool's most ordinary
	 * job: cutting a trench along the ground, you break the <em>top</em> of a block while looking mostly
	 * forwards, and taking the nearest axis of the look vector would stand the slice up as a wall and
	 * dig a hole instead of a trench.
	 *
	 * <p>When the cast does not land on the block being broken — another mod breaking blocks with this
	 * tool in hand, a queued break, a test calling {@code mineBlock} directly — the look direction is
	 * the honest fallback, and is what the tool used everywhere before.
	 */
	private static Direction.Axis plane(Player player, BlockPos pos) {
		BlockHitResult hit = getPlayerPOVHitResult(player.level(), player, ClipContext.Fluid.NONE);
		if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos()
			.equals(pos))
			return hit.getDirection()
				.getAxis();
		Vec3 look = player.getViewVector(1.0F);
		return Direction.getNearest(look.x, look.y, look.z)
			.getAxis();
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
