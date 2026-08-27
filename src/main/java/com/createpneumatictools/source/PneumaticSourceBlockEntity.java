package com.createpneumatictools.source;

import com.createpneumatictools.CPTConfig;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A generator that has to be told to keep existing.
 *
 * <p>The lease is the whole safety argument for this block. A wrench that <em>removed</em> its source
 * on release would need to also remove it on: releasing the button, switching hotbar slot, walking
 * out of range, dying, disconnecting, changing dimension, the chunk unloading mid-hold, and the
 * client crashing. Miss any one of those and an invisible generator is left turning somebody's
 * factory forever, and it is invisible, so nobody will find it.
 *
 * <p>Inverted, there is one rule and no list: the block counts down and deletes itself, and the
 * wrench pushes the counter back up every tick it is still legitimately running. Every case above is
 * then the same case — the renewals stopped — including the ones nobody thought of.
 *
 * <p>The lease deliberately survives a save and reload rather than being cleared on load. A block
 * that vanished on world load would be tidy, but it would also make the failure silent during the one
 * situation worth testing; instead the reloaded block keeps turning for the last half-second of its
 * lease and then goes, which is the same behaviour as every other way of letting go.
 */
public class PneumaticSourceBlockEntity extends GeneratingKineticBlockEntity {

	/**
	 * Ticks a renewal buys. Long enough to ride out a lag spike or a slow tick, short enough that an
	 * orphan is gone before a player could notice it — and comfortably longer than the interval the
	 * wrench renews at.
	 */
	public static final int LEASE_TICKS = 10;

	private int lease = LEASE_TICKS;

	/**
	 * Whose wrench is allowed to renew this one, or null for nobody's.
	 *
	 * <p>It narrows a search; it is emphatically not what keeps the block alive. Without it
	 * {@code PneumaticWrenchItem.driving} answered with the nearest source to the holder's eye whoever
	 * put it there, so two people wrenching in the same workshop drove each other's blocks: one
	 * player's renewals kept the other's alive while their own lease ran out, and letting go removed a
	 * block somebody else was still using.
	 *
	 * <p>Null therefore fails closed, and safely: nobody can renew an unowned source, so the lease
	 * runs down and the block goes. A driver that failed to save, or a block from a world older than
	 * this field, costs its holder one more click rather than leaving a generator nobody can find.
	 */
	private UUID driver;

	public PneumaticSourceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** Called by the wrench, every tick it is still driving this position. */
	public void renew() {
		lease = LEASE_TICKS;
	}

	/** Claims this source for {@code player}: from now on only their wrench can renew it. */
	public void drivenBy(Player player) {
		driver = player.getUUID();
		setChanged();
	}

	/** Whether {@code player}'s wrench is the one allowed to renew this source. */
	public boolean isDrivenBy(Player player) {
		return player.getUUID()
			.equals(driver);
	}

	/**
	 * Starts generating. A generator that never calls this sits in the world doing nothing at all:
	 * {@code getGeneratedSpeed} is only consulted once something asks the network to recalculate, and
	 * placing a block is not by itself such a thing. Create's own Creative Motor does exactly this,
	 * for exactly this reason, and leaving it out looks precisely like a block that failed to connect.
	 */
	@Override
	public void initialize() {
		super.initialize();
		if (!hasSource() || getGeneratedSpeed() > getTheoreticalSpeed())
			updateGeneratedRotation();
	}

	@Override
	public float getGeneratedSpeed() {
		return convertToDirection(CPTConfig.wrenchRpm(), getBlockState().getValue(PneumaticSourceBlock.FACING));
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;
		if (--lease > 0)
			return;
		// removeBlock, not destroyBlock: no drops, no break particles, no sound. The wrench plays its
		// own, and a puff of nothing appearing in mid-air would be the only sign this block exists.
		level.removeBlock(worldPosition, false);
	}

	@Override
	public void write(CompoundTag compound, Provider registries, boolean clientPacket) {
		compound.putInt("Lease", lease);
		// Written to the client packet as well as to disk: the wrench looks for the block it is
		// driving on both sides -- the client to know where to draw the exhaust -- and a client that
		// did not know who owned one could not tell its own source from the next player's.
		if (driver != null)
			compound.putUUID("Driver", driver);
		super.write(compound, registries, clientPacket);
	}

	@Override
	protected void read(CompoundTag compound, Provider registries, boolean clientPacket) {
		lease = compound.getInt("Lease");
		driver = compound.hasUUID("Driver") ? compound.getUUID("Driver") : null;
		super.read(compound, registries, clientPacket);
	}

	/** Which way the shaft points, for the wrench's own particles. */
	public Direction facing() {
		return getBlockState().getValue(PneumaticSourceBlock.FACING);
	}
}
