package com.createpneumatictools.item;

import com.createpneumatictools.CPTConfig;
import com.createpneumatictools.registry.CPTBlocks;
import com.createpneumatictools.source.PneumaticSourceBlock;
import com.createpneumatictools.source.PneumaticSourceBlockEntity;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
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
		// has to ask first, and it has to ask twice. mayUseItemAt covers adventure mode; mayInteract
		// covers spawn protection and the world border, which vanilla checks only against the block
		// named in the packet -- and the source goes in the space *next* to that one, which no packet
		// ever described. PASS rather than FAIL, so a wrench aimed somewhere it may not build behaves
		// like an item with nothing to do rather than eating the click.
		if (!player.mayUseItemAt(target, context.getClickedFace(), context.getItemInHand())
			|| !level.mayInteract(player, target))
			return InteractionResult.PASS;
		if (!isPowered(player)) {
			refuse(player);
			return InteractionResult.FAIL;
		}

		if (!level.isClientSide) {
			// Only a source that was not already standing there gets the hiss. Vanilla re-fires a use
			// every four ticks whenever the last one ended early, and a re-click on a face already
			// being driven would otherwise broadcast one of these five times a second.
			boolean fresh = !(level.getBlockState(target)
				.getBlock() instanceof PneumaticSourceBlock);
			if (!place(level, target, context.getClickedFace(), player))
				return InteractionResult.FAIL;
			if (fresh)
				AllSoundEvents.STEAM.playOnServer(level, target, 0.4F, 1.2F);
		}
		player.startUsingItem(context.getHand());
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public void onUseTick(Level level, LivingEntity holder, ItemStack wrench, int remaining) {
		if (!(holder instanceof Player player))
			return;
		if (level.isClientSide) {
			// The client gets no say in whether the wrench is still driving. place() is server side,
			// so the block does not exist here for at least a tick and for however long the connection
			// costs -- and a client that ended its own use over that would have vanilla re-firing
			// startUseItem every four ticks for the whole window, re-placing and re-hissing all the
			// way. It draws what it can see and waits for the rest.
			//
			// The roll comes before the search rather than inside exhaust(): two ticks in three draw
			// nothing at all, and there is no reason to go looking for a position nobody will use.
			if (level.random.nextInt(3) == 0) {
				BlockPos at = driving(player);
				if (at != null)
					exhaust(level, at);
			}
			return;
		}

		BlockPos target = driving(player);
		if (target == null) {
			player.stopUsingItem();
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
	 *
	 * <p>It looks through the <em>block entities</em> of the chunks in range rather than at the blocks
	 * in them, and the difference is not small. A source always has a block entity, so the two
	 * searches find the same thing; but reading every block state in the cube is {@code (2r+1)^3}
	 * palette lookups a tick — 4,913 at the default range, and 274,625 at the largest the config
	 * allows — on the client as well as the server, for every tick the button is down. Nine chunks'
	 * worth of block entities is a few dozen map entries. The knob was quietly cubic; now it is not.
	 *
	 * <p>And it only answers with sources this player claimed. See
	 * {@link PneumaticSourceBlockEntity#isDrivenBy} for what the nearest-block-wins version did to two
	 * people working in the same room.
	 */
	private static BlockPos driving(Player player) {
		double reach = CPTConfig.wrenchRange();
		Level level = player.level();
		Vec3 eye = player.getEyePosition();
		int fromX = SectionPos.blockToSectionCoord(Mth.floor(eye.x - reach));
		int toX = SectionPos.blockToSectionCoord(Mth.ceil(eye.x + reach));
		int fromZ = SectionPos.blockToSectionCoord(Mth.floor(eye.z - reach));
		int toZ = SectionPos.blockToSectionCoord(Mth.ceil(eye.z + reach));

		BlockPos best = null;
		double nearest = reach * reach;
		for (int x = fromX; x <= toX; x++)
			for (int z = fromZ; z <= toZ; z++) {
				// load = false: a source is only ever placed within arm's reach of somebody standing
				// there, so a chunk that is not loaded cannot be holding one -- and asking for it
				// would generate terrain to answer a question about a block that lasts half a second.
				LevelChunk chunk = level.getChunkSource()
					.getChunk(x, z, false);
				if (chunk == null)
					continue;
				for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities()
					.entrySet()) {
					if (!(entry.getValue() instanceof PneumaticSourceBlockEntity source))
						continue;
					if (!source.isDrivenBy(player))
						continue;
					double distance = eye.distanceToSqr(Vec3.atCenterOf(entry.getKey()));
					if (distance > nearest)
						continue;
					nearest = distance;
					best = entry.getKey()
						.immutable();
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
		if (!(level.getBlockState(spot)
			.getBlock() instanceof PneumaticSourceBlock)) {
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
		}
		// Whoever clicks a face is the one driving it from here, even if a source was already standing
		// in the space. Refusing to hand one over would be the safer-looking rule and the worse one:
		// the block is invisible, so a player who could not take a spot somebody else had claimed
		// would be holding a wrench that did nothing, with nothing to look at that said why.
		if (level.getBlockEntity(spot) instanceof PneumaticSourceBlockEntity source)
			source.drivenBy(player);
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
