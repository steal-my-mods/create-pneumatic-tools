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
 *
 * <p><b>It generates nothing at all on its first tick, and that is the whole of how it avoids
 * fighting a network that is already turning.</b> See {@link #matchOrDrive}.
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
	 * How near two speeds have to be to count as the same. Create compares its own at 1e-4; this is a
	 * little looser, because the figure being compared here has been through a gearbox ratio or two.
	 */
	private static final float SAME_SPEED = 1.0E-3F;

	/**
	 * The speed this source has settled on, or null while it has not settled on one yet.
	 *
	 * <p>Null is not "zero" with extra steps: it is the state in which the block is deliberately
	 * <em>not a generator</em>, because {@code isSource()} is defined as {@code getGeneratedSpeed() !=
	 * 0}. That is what buys the one tick of looking before leaping that {@link #matchOrDrive}
	 * describes.
	 */
	private Float generating;

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
		// Only a source restored from disk has anything to assert here. One that has just been placed
		// has not decided what it is generating yet, and must not: see matchOrDrive.
		if (generating == null)
			return;
		// The signed comparison is Create's, verbatim from CreativeMotorBlockEntity.initialize. Left
		// signed rather than tidied to an absolute value so that this block behaves exactly like
		// Create's own generator on load, whatever that behaviour turns out to be worth.
		if (!hasSource() || getGeneratedSpeed() > getTheoreticalSpeed())
			updateGeneratedRotation();
	}

	@Override
	public float getGeneratedSpeed() {
		return generating == null ? 0.0F : generating;
	}

	/**
	 * Falls in behind whatever is already driving this network, or drives it if nothing is.
	 *
	 * <p>The problem this solves is that a generator which disagrees with its network does not
	 * degrade — it is <em>destroyed</em>. Create's {@code RotationPropagator} breaks the block whose
	 * conveyed speed opposes its neighbour's sign, and {@code applyNewSpeed} breaks one that is
	 * overruled into turning the other way. Since the wrench's direction comes from which face you
	 * clicked, roughly half of all clicks onto a running shaft used to put a source down and have
	 * Create delete it on the same tick — whereupon the wrench, which re-places it every tick it is
	 * held, put another one there. An invisible block flickering in and out with a break sound, and a
	 * wrench that appeared to do nothing on one side of a shaft and work on the other.
	 *
	 * <p>Matching is not done by working out what the network is doing, which cannot be done from
	 * inside it. It is done by <b>generating nothing for one tick and letting Create answer</b>. A
	 * block whose {@code getGeneratedSpeed} is zero is not a source, so the propagator adopts it as an
	 * ordinary member and sets its speed to whatever this position runs at — computed with the full
	 * geometry of whatever it is bolted to, gearbox ratios, cog reversals, speed controllers and all.
	 * Reading that back the next tick and generating exactly it cannot conflict, and the reason is
	 * arithmetic rather than luck: for an axis connection Create's own modifier obeys
	 * {@code modifier(a→b) == 1 / modifier(b→a)}, so a block driven at {@code s = s_neighbour × m}
	 * that generates {@code s} conveys {@code s × 1/m = s_neighbour} straight back. Equal speeds are
	 * the one case the propagator leaves alone.
	 *
	 * <p>What that buys is a wrench that <em>adds torque</em> rather than argues about speed: matched,
	 * it registers its stress capacity into the network it joined and changes nothing else. Helping an
	 * overstressed line along is the case it is most wanted for, and note that
	 * {@link #getTheoreticalSpeed} is what has to be read to find it — {@code getSpeed()} reports zero
	 * on an overstressed network, so matching on that would decide there was nothing to match at
	 * exactly the moment there was.
	 *
	 * <p>With no source, the network is this block's own and it runs at the configured RPM in the
	 * direction the clicked face implies, which is what it always did. That is also the fallback when
	 * whatever was driving the network stops: the propagator clears the source, and the wrench takes
	 * over on the next tick rather than inheriting a speed nothing is producing any more.
	 */
	private void matchOrDrive() {
		float target = hasSource() ? getTheoreticalSpeed()
			: convertToDirection(CPTConfig.wrenchRpm(), facing());
		if (generating != null && Math.abs(generating - target) <= SAME_SPEED)
			return;
		generating = target;
		updateGeneratedRotation();
	}

	/**
	 * The capacity this source hands its network, as the per-RPM figure Create bills in.
	 *
	 * <p>Create charges a generator's capacity as {@code per-RPM × |generated speed|}
	 * ({@code KineticNetwork.getActualCapacityOf}), so a fixed per-RPM figure is a fixed
	 * <em>torque</em> and the Stress Units it is worth then rise with speed. That is right for
	 * Create's own generators, whose speed is a property of the generator — a Water Wheel turns at 8
	 * and that is that. It is wrong here, because since {@link #matchOrDrive} this source's speed can
	 * be somebody else's: matched onto a network already running at 192 RPM, a fixed 16 SU/RPM was
	 * worth 3072 Stress Units for exactly the air that buys 1024 on a shaft of your own. The faster
	 * the network you found, the more you were paid for joining it.
	 *
	 * <p>So the fixed quantity here is the <em>product</em>, not the factor: divide the configured
	 * Stress Units by the speed and the wrench is worth the same at any speed. That is also what an
	 * air motor does — a tank breathes at one rate, so it delivers one amount of power, and gearing
	 * decides whether it arrives as speed or as torque. Half the speed, twice the torque.
	 *
	 * <p>Zero below a whisker of speed rather than a division: a source turning at nothing is not a
	 * source at all, and {@code capacity × 0} with an infinite capacity is {@code NaN} — which would
	 * not throw, it would quietly make the network's whole capacity NaN and every comparison against
	 * it false.
	 */
	@Override
	public float calculateAddedStressCapacity() {
		float speed = Math.abs(getGeneratedSpeed());
		float capacity =
			speed <= SAME_SPEED ? 0.0F : (float) (CPTConfig.wrenchStressUnits() / speed);
		// Create's own field, kept up to date because KineticNetwork.addSilently reads it back to undo
		// the contribution this source made before its chunk was unloaded.
		lastCapacityProvided = capacity;
		return capacity;
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide)
			return;
		// After super.tick(), because that is where KineticBlockEntity attaches a freshly placed block
		// to the network and so where the propagator gets its chance to tell this one what speed its
		// own position turns at.
		matchOrDrive();
		if (--lease > 0)
			return;
		// removeBlock, not destroyBlock: no drops, no break particles, no sound. The wrench plays its
		// own, and a puff of nothing appearing in mid-air would be the only sign this block exists.
		level.removeBlock(worldPosition, false);
	}

	@Override
	public void write(CompoundTag compound, Provider registries, boolean clientPacket) {
		compound.putInt("Lease", lease);
		// Saved so that a source reloaded mid-hold asserts the speed it had settled on rather than
		// spending another tick generating nothing, which on a network it had matched would read as
		// the shaft stuttering.
		if (generating != null)
			compound.putFloat("Generating", generating);
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
		generating = compound.contains("Generating") ? compound.getFloat("Generating") : null;
		driver = compound.hasUUID("Driver") ? compound.getUUID("Driver") : null;
		super.read(compound, registries, clientPacket);
	}

	/** Which way the shaft points, for the wrench's own particles. */
	public Direction facing() {
		return getBlockState().getValue(PneumaticSourceBlock.FACING);
	}
}
