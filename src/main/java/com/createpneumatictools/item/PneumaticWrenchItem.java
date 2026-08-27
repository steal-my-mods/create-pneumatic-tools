package com.createpneumatictools.item;

import com.createpneumatictools.CPTConfig;
import com.createpneumatictools.registry.CPTBlocks;
import com.createpneumatictools.source.PneumaticSourceBlock;
import com.createpneumatictools.source.PneumaticSourceBlockEntity;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Portable torque: hold it against a shaft and it drives the network for as long as you hold the
 * button.
 *
 * <p>It works by putting a real generator into the world — see {@link PneumaticSourceBlock} for why
 * there is no other option — in the empty space against the face you clicked, pointing into it, which
 * is exactly where you would have placed a motor by hand. Let go, walk away, switch item, run out of
 * air, or fall off a cliff, and it goes: the block holds a short lease and this is the only thing
 * that renews it.
 *
 * <p>The hold is a {@code startUsingItem}, not vanilla's repeat-every-four-ticks on a held
 * right-click. That matters for more than tidiness: a use gives {@link #onUseTick} every tick, which
 * is what the lease wants, and it gives {@link #releaseUsing}, which is what lets the wrench stop
 * instantly rather than after the lease runs down.
 */
public class PneumaticWrenchItem extends PneumaticToolItem {

	/** Long enough that the button, not the clock, decides when it stops. Bows use the same. */
	private static final int HELD_INDEFINITELY = 72000;

	public PneumaticWrenchItem(Properties properties) {
		super(properties);
	}

	@Override
	public int usesPerTank() {
		return CPTConfig.wrenchUsesPerTank();
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.NONE;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return HELD_INDEFINITELY;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		Level level = context.getLevel();
		if (player == null)
			return InteractionResult.PASS;

		BlockPos target = mountingSpot(context);
		if (target == null)
			return InteractionResult.PASS;
		// The wrench is the one tool here that puts a block in the world, so it is the one tool that
		// has to ask first. Vanilla's own check covers spawn protection and adventure mode; PASS
		// rather than FAIL, so a wrench aimed somewhere it may not build behaves like an item with
		// nothing to do rather than eating the click.
		if (!player.mayUseItemAt(target, context.getClickedFace(), context.getItemInHand()))
			return InteractionResult.PASS;
		if (!isPowered(player)) {
			refuse(player);
			return InteractionResult.FAIL;
		}

		if (!level.isClientSide) {
			if (!place(level, target, context.getClickedFace(), player))
				return InteractionResult.FAIL;
			AllSoundEvents.STEAM.playOnServer(level, target, 0.4F, 1.2F);
		}
		player.startUsingItem(context.getHand());
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public void onUseTick(Level level, LivingEntity holder, ItemStack wrench, int remaining) {
		if (!(holder instanceof Player player))
			return;
		BlockPos target = driving(player);
		if (target == null) {
			player.stopUsingItem();
			return;
		}
		if (level.isClientSide) {
			exhaust(level, target);
			return;
		}

		// One charge every few ticks rather than every tick: the tank should last minutes of running,
		// and the smallest charge Create's own model can make is one whole air unit.
		int elapsed = HELD_INDEFINITELY - remaining;
		if (elapsed % CPTConfig.wrenchInterval() == 0 && !spendAir(player)) {
			refuse(player);
			player.stopUsingItem();
			return;
		}
		if (level.getBlockEntity(target) instanceof PneumaticSourceBlockEntity source)
			source.renew();
	}

	@Override
	public void releaseUsing(ItemStack wrench, Level level, LivingEntity holder, int timeLeft) {
		if (holder instanceof Player player)
			letGo(level, player);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack wrench, Level level, LivingEntity holder) {
		if (holder instanceof Player player)
			letGo(level, player);
		return wrench;
	}

	/**
	 * Stops driving immediately rather than waiting for the lease to lapse.
	 *
	 * <p>Purely for feel — the lease would clear it within half a second either way — but half a
	 * second of a shaft still turning after you let go reads as the tool being laggy.
	 */
	private void letGo(Level level, Player player) {
		if (level.isClientSide)
			return;
		BlockPos target = driving(player);
		if (target != null)
			level.removeBlock(target, false);
	}

	/**
	 * The source this player is currently driving, or null if there is not one within reach any more.
	 *
	 * <p>Found by looking rather than remembered, which is what keeps this stateless: there is no
	 * field, no data component and no map to get out of step with the world. Walking away, or the
	 * block being gone for any other reason, is simply a search that comes back empty.
	 */
	private static BlockPos driving(Player player) {
		double reach = CPTConfig.wrenchRange();
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		int span = (int) Math.ceil(reach);
		BlockPos best = null;
		double nearest = reach * reach;
		for (BlockPos pos : BlockPos.betweenClosed(eye.offset(-span, -span, -span),
			eye.offset(span, span, span))) {
			if (!(player.level()
				.getBlockState(pos)
				.getBlock() instanceof PneumaticSourceBlock))
				continue;
			double distance = player.getEyePosition()
				.distanceToSqr(Vec3.atCenterOf(pos));
			if (distance <= nearest) {
				nearest = distance;
				best = pos.immutable();
			}
		}
		return best;
	}

	/** The empty space against the clicked face, or null if there is nothing there to hold it in. */
	private static BlockPos mountingSpot(UseOnContext context) {
		BlockPos against = context.getClickedPos();
		Level level = context.getLevel();
		// Nothing to drive: an empty face in mid-air is not a shaft.
		if (level.getBlockState(against)
			.isAir())
			return null;
		BlockPos spot = against.relative(context.getClickedFace());
		BlockState occupant = level.getBlockState(spot);
		if (occupant.getBlock() instanceof PneumaticSourceBlock)
			return spot;
		return occupant.canBeReplaced() ? spot : null;
	}

	/**
	 * Puts the source down, if everything that gets a say agrees.
	 *
	 * <p>Placed first and rolled back on a veto, which is the shape {@code BlockItem.place} uses and
	 * the reason {@code EntityPlaceEvent} carries a snapshot: a listener wants to see the world as it
	 * would be, not be asked to imagine it. Claim and protection mods mostly cancel
	 * {@code RightClickBlock} long before this, but vanilla spawn protection does not, and neither
	 * does anything that only listens for placement.
	 */
	private static boolean place(Level level, BlockPos spot, Direction clickedFace, Player player) {
		if (level.getBlockState(spot)
			.getBlock() instanceof PneumaticSourceBlock)
			return true;
		// FACING is where the shaft comes out, so it points back into the block that was clicked.
		BlockState state = CPTBlocks.PNEUMATIC_SOURCE.get()
			.defaultBlockState()
			.setValue(DirectionalKineticBlock.FACING, clickedFace.getOpposite());
		BlockSnapshot before = BlockSnapshot.create(level.dimension(), level, spot);
		if (!level.setBlockAndUpdate(spot, state))
			return false;
		if (EventHooks.onBlockPlace(player, before, clickedFace)) {
			before.restore();
			return false;
		}
		return true;
	}

	/** A little steam where the source is, because otherwise nothing at all shows it is there. */
	private static void exhaust(Level level, BlockPos at) {
		if (level.random.nextInt(3) != 0)
			return;
		Vec3 centre = Vec3.atCenterOf(at);
		Vec3 drift = new Vec3(level.random.nextFloat() - 0.5F, level.random.nextFloat() * 0.4F,
			level.random.nextFloat() - 0.5F).scale(0.06);
		level.addParticle(ParticleTypes.CLOUD, centre.x, centre.y, centre.z, drift.x, drift.y, drift.z);
	}
}
