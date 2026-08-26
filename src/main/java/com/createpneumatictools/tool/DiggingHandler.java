package com.createpneumatictools.tool;

import com.createpneumatictools.CreatePneumaticTools;
import com.createpneumatictools.item.PneumaticDiggerItem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Where a pneumatic digger gets its speed, and the only place it gets any.
 *
 * <p>The tools' own {@code TOOL} components sit at a mining speed of 1, so with no air a Hand Drill
 * digs at the rate of a bare fist and this handler does nothing at all. See
 * {@link PneumaticDiggerItem} for why the speed cannot live in the component.
 *
 * <p>The event fires on both the client and the server, which it must: the client draws the crack
 * overlay from its own arithmetic, and a client that disagreed with the server would show a block
 * shattering and then put it back. Both sides read the same air value, because a player's own armour
 * slots are synced to their own client.
 */
@EventBusSubscriber(modid = CreatePneumaticTools.ID)
public class DiggingHandler {

	@SubscribeEvent
	public static void pneumaticToolsRunOnAir(PlayerEvent.BreakSpeed event) {
		Player player = event.getEntity();
		ItemStack held = player.getMainHandItem();
		if (!(held.getItem() instanceof PneumaticDiggerItem digger))
			return;
		if (!digger.isPowered(player))
			return;

		// The event still permits a missing position -- some callers only know the state -- and hardness
		// is read from the position, so the jackhammer has to cope with not being given one.
		BlockPos pos = event.getPosition()
			.orElse(null);
		float multiplier = digger.poweredSpeed(event.getState(), player.level(), pos);
		if (multiplier <= 1.0F)
			return;
		event.setNewSpeed(event.getOriginalSpeed() * multiplier);
	}
}
