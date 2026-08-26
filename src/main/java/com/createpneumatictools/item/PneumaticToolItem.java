package com.createpneumatictools.item;

import com.createpneumatictools.air.AirSupply;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.armor.BacktankUtil;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * Shared parts of a tool that runs off a backtank.
 *
 * <p>Three of them, and two are about the tank rather than the tool. The durability bar is handed
 * over to {@link BacktankUtil}, so what a player sees under a Hand Drill is how much air they have
 * left — the same bar Create draws under an Extendo Grip, and the reason none of these items carry
 * durability of their own. And {@link #refuse} is the one place a tool says "no air": a short deny
 * chirp and a puff of steam, matching what Create plays when a backtank runs dry, so an empty tank
 * sounds the same whatever is in your hand.
 */
public abstract class PneumaticToolItem extends Item {

	protected PneumaticToolItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	/** How many times a full Copper Backtank drives this tool. Read from the config every call. */
	public abstract int usesPerTank();

	public boolean isPowered(LivingEntity holder) {
		return AirSupply.isPowered(holder, usesPerTank());
	}

	public boolean spendAir(LivingEntity holder) {
		return AirSupply.spend(holder, usesPerTank());
	}

	/**
	 * Tells the player the tank is empty. Server side; the sound carries to everyone nearby, which is
	 * the same thing Create's own low-air warning does.
	 */
	public void refuse(Player player) {
		if (player.level().isClientSide)
			return;
		BlockPos at = player.blockPosition();
		AllSoundEvents.DENY.playOnServer(player.level(), at, 1.0F, 1.25F);
		AllSoundEvents.STEAM.playOnServer(player.level(), at, 0.35F, 0.6F);
	}

	/**
	 * Hands every tool its 3D renderer. One override serves all nine because the table of which part
	 * moves how lives in {@link com.createpneumatictools.client.CPTRenderers}, on the client side where
	 * it belongs.
	 *
	 * <p>Only ever called on a client, which is what makes it safe for a common class to name client
	 * types here: the method body is never reached on a server, so those classes are never loaded.
	 */
	@OnlyIn(Dist.CLIENT)
	@Override
	public void initializeClient(Consumer<IClientItemExtensions> consumer) {
		IClientItemExtensions extensions = com.createpneumatictools.client.CPTRenderers.of(this);
		if (extensions != null)
			consumer.accept(extensions);
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return BacktankUtil.isBarVisible(stack, usesPerTank());
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return BacktankUtil.getBarWidth(stack, usesPerTank());
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return BacktankUtil.getBarColor(stack, usesPerTank());
	}
}
