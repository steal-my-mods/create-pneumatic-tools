package com.createpneumatictools.item;

import java.util.ArrayList;
import java.util.List;

import com.createpneumatictools.tool.Excavation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Base for the two tools that take a whole square of blocks down at once.
 *
 * <p>The square lies flat against the <em>face you drilled</em>. Drill the top of a block and the
 * burst takes a slice of floor; drill the side of one and it takes a slice of wall. See {@link #plane}
 * for how that face is found, and for what happens when it cannot be.
 *
 * <p>One burst is charged once, whatever it found. Twenty-five blocks or two, the tank pays the same,
 * which is what makes these worth carrying only where the wall is actually solid — that, and a lower
 * speed per block, is the whole trade against the Hand Drill.
 *
 * <p>The two subclasses differ in exactly three things: their rating, their speed, and {@link
 * #radius}. Everything that is hard about a burst — finding the face, not recursing, and asking
 * vanilla's own two gates on the extra blocks' behalf — is the same problem at any width, so it is
 * solved once here rather than twice badly.
 */
public abstract class BurstDiggerItem extends PneumaticDiggerItem {

	/** How often a broken neighbour puffs. Create's own drill contraption uses the same figure. */
	private static final float EFFECT_CHANCE = 0.25F;

	protected BurstDiggerItem(TagKey<Block> incorrectForDrops, TagKey<Block> mineable,
		Properties properties) {
		super(incorrectForDrops, mineable, properties);
	}

	/**
	 * How far out from the block you broke the burst reaches, in blocks. 1 is a 3x3, 2 a 5x5.
	 *
	 * <p>There is no upper bound enforced here and there does not need to be: the two tools that
	 * extend this are the only callers, and a burst is one charge whatever it costs the tank, so the
	 * ceiling on how wide one may sensibly be is a design question rather than a safety one.
	 */
	protected abstract int radius();

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
	 * The axis the slice stands perpendicular to: the one running through the face just drilled.
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

	/** Every position around {@code centre} in the plane {@code along} runs through, out to the reach. */
	private Iterable<BlockPos> wallAround(BlockPos centre, Direction.Axis along) {
		int reach = radius();
		int width = 2 * reach + 1;
		List<BlockPos> wall = new ArrayList<>(width * width - 1);
		for (int a = -reach; a <= reach; a++)
			for (int b = -reach; b <= reach; b++) {
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
		// Vanilla checks spawn protection and the world border against the position in the packet, so
		// they cover the block the player actually swung at and nothing else. The ones around it have
		// to ask on their own account. Excavation's veto catches this too, but only after Create's
		// helper has already thrown a break particle at the block -- and a puff of debris off a block
		// that then does not break reads as the tool being broken.
		if (!level.mayInteract(player, pos))
			return;
		Excavation.breakAs(level, pos, player, stack, EFFECT_CHANCE);
	}
}
