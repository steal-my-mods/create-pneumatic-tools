package com.createpneumatictools.item;

import java.util.List;

import com.createpneumatictools.CPTConfig;
import com.simibubi.create.AllSoundEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hold the button and loose items and experience within reach come to you.
 *
 * <p>Held down rather than clicked, which is why it goes through {@code startUsingItem}: a wand you
 * have to spam is a wand nobody uses, and a use-item is the one hook vanilla ticks for as long as the
 * button is down. The animation is {@link UseAnim#NONE} on purpose — a raised-arm pose would hide the
 * wand behind the player's own hand.
 *
 * <p>Two details keep it from being a nuisance. A pulse with nothing in range is <em>free</em>, so
 * standing still with the button held does not quietly empty a tank; and an item still inside its
 * pickup delay is left where it is, so pressing Q and then vacuuming does not hand the item straight
 * back. Neither is a special case for its own sake — both are the difference between a tool you leave
 * on the hotbar and one you take off it.
 */
public class VacuumWandItem extends PneumaticToolItem {

	/** Long enough that the button, not the clock, decides when the wand stops. Bows use the same. */
	private static final int HELD_INDEFINITELY = 72000;
	/** Blocks per tick a caught entity is dragged at. Gravity is 0.04, so this comfortably wins. */
	private static final double PULL_SPEED = 0.5;
	/** Inside this, stop dragging: the entity is on top of the player and vanilla will collect it. */
	private static final double ARRIVED = 0.6;
	/** Pulses between hisses, so a held button is audible without being a drone. */
	private static final int PULSES_PER_HISS = 5;

	public VacuumWandItem(Properties properties) {
		super(properties);
	}

	@Override
	public int usesPerTank() {
		return CPTConfig.vacuumUsesPerTank();
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
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack wand = player.getItemInHand(hand);
		if (!isPowered(player)) {
			refuse(player);
			return InteractionResultHolder.fail(wand);
		}
		player.startUsingItem(hand);
		return InteractionResultHolder.consume(wand);
	}

	@Override
	public void onUseTick(Level level, LivingEntity holder, ItemStack wand, int remaining) {
		if (!(holder instanceof Player player))
			return;
		int interval = CPTConfig.vacuumInterval();
		int elapsed = HELD_INDEFINITELY - remaining;
		if (elapsed % interval != 0)
			return;

		double radius = CPTConfig.vacuumRadius();
		Vec3 mouth = player.position()
			.add(0.0, player.getBbHeight() * 0.5, 0.0);
		List<Entity> caught = level.getEntities(player,
			new AABB(mouth, mouth).inflate(radius), VacuumWandItem::vacuumable);

		if (level.isClientSide) {
			for (Entity entity : caught)
				trail(level, entity, mouth);
			return;
		}
		// A pulse that catches nothing is free. Without this the wand is a slow leak in the pocket of
		// anyone who leans on the right mouse button while walking.
		if (caught.isEmpty())
			return;
		if (!spendAir(player)) {
			refuse(player);
			player.stopUsingItem();
			return;
		}

		for (Entity entity : caught)
			drag(entity, mouth);
		if ((elapsed / interval) % PULSES_PER_HISS == 0)
			AllSoundEvents.STEAM.playOnServer(level, player.blockPosition(), 0.2F, 1.6F);
	}

	private static boolean vacuumable(Entity entity) {
		if (!entity.isAlive())
			return false;
		// A dropped stack keeps its pickup delay so that pressing Q while holding the wand does not
		// simply undo itself; once the delay lapses the next pulse takes it.
		if (entity instanceof ItemEntity item)
			return !item.hasPickUpDelay();
		return entity instanceof ExperienceOrb;
	}

	private static void drag(Entity entity, Vec3 mouth) {
		Vec3 toMouth = mouth.subtract(entity.position());
		if (toMouth.lengthSqr() < ARRIVED * ARRIVED)
			return;
		entity.setDeltaMovement(toMouth.normalize()
			.scale(PULL_SPEED));
		// Without this the server never decides the velocity is worth a packet and the client draws the
		// entity drifting on its own physics while the server has already moved it.
		entity.hasImpulse = true;
	}

	private static void trail(Level level, Entity entity, Vec3 mouth) {
		Vec3 from = entity.position();
		Vec3 toMouth = mouth.subtract(from);
		if (toMouth.lengthSqr() < ARRIVED * ARRIVED)
			return;
		Vec3 drift = toMouth.normalize()
			.scale(0.15);
		level.addParticle(ParticleTypes.POOF, from.x, from.y + 0.15, from.z, drift.x, drift.y, drift.z);
	}
}
