package com.createpneumatictools.source;

import com.createpneumatictools.CPTConfig;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
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

	public PneumaticSourceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** Called by the wrench, every tick it is still driving this position. */
	public void renew() {
		lease = LEASE_TICKS;
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
		super.write(compound, registries, clientPacket);
	}

	@Override
	protected void read(CompoundTag compound, Provider registries, boolean clientPacket) {
		lease = compound.getInt("Lease");
		super.read(compound, registries, clientPacket);
	}

	/** Which way the shaft points, for the wrench's own particles. */
	public Direction facing() {
		return getBlockState().getValue(PneumaticSourceBlock.FACING);
	}
}
