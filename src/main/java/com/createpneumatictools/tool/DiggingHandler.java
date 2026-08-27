package com.createpneumatictools.tool;

import com.createpneumatictools.CreatePneumaticTools;
import com.createpneumatictools.item.PneumaticDiggerItem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
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
 *
 * <p>Efficiency is applied here too, and it has to be, because vanilla will not. See
 * {@link #efficiencyFactor}.
 */
@EventBusSubscriber(modid = CreatePneumaticTools.ID)
public class DiggingHandler {

	/** What one level of Efficiency is worth, taken from vanilla's per-level Haste step. */
	private static final float PER_LEVEL = 0.2F;

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
		// Efficiency multiplies the work the air is doing, so a tool with nothing to contribute on this
		// block gains nothing from a book either: a jackhammer on soft stone is slow and free with or
		// without Efficiency V on it, which is the same bargain the air strikes.
		if (multiplier <= 1.0F)
			return;
		event.setNewSpeed(event.getOriginalSpeed() * multiplier * efficiencyFactor(held));
	}

	/**
	 * How much faster Efficiency makes a pneumatic digger: one level is worth one level of Haste.
	 *
	 * <p>Vanilla refuses to apply Efficiency to these tools at all, and not by accident.
	 * {@code Player.getDigSpeed} adds the {@code MINING_EFFICIENCY} attribute only when the tool's own
	 * mining speed <em>already exceeds 1</em>, and the {@code TOOL} component here is pinned at exactly
	 * 1 — deliberately, because that is what makes an empty tank mean "bare hands" rather than a
	 * subtraction that has to come out right. Raising the component to let vanilla in would break that
	 * promise and could never be configurable anyway, since the component is baked at registration
	 * before any config is read. So the bonus is applied on this side of the gate instead, next to the
	 * rest of the speed these tools have.
	 *
	 * <p><b>Multiplicative, not vanilla's additive.</b> Vanilla adds {@code level² + 1} to a tool's own
	 * speed, which works because a pickaxe's speed is a speed. The jackhammer's is not: its config is a
	 * <em>time</em>, and {@code poweredSpeed} solves backwards from it, so a fixed amount added to that
	 * would shorten the break time by an amount that depended on the block's hardness — Deepslate would
	 * gain far more than Obsidian, which is exactly the difference the tool exists to erase. A factor
	 * shortens every break by the same proportion and leaves the premise intact.
	 *
	 * <p>0.2 per level is not invented: it is vanilla's own per-level step for dig speed, the constant
	 * in {@code getDigSpeed}'s Haste term. So a level of Efficiency and a level of Haste are worth the
	 * same, they stack, and Efficiency V doubles the speed. That is less than the 4.25x it would give a
	 * diamond pickaxe, and deliberately: the air is meant to be what makes these tools fast, and an
	 * Efficiency V jackhammer at vanilla's rate would shatter Obsidian in a single tick.
	 *
	 * <p>Read off the stack's own enchantments rather than out of the {@code MINING_EFFICIENCY}
	 * attribute, which would otherwise be the tidier source: the attribute carries {@code level² + 1}
	 * rather than the level, and a rule written in levels wants the level. Create reads its own Capacity
	 * enchantment the same way, in {@code BacktankUtil.maxAir}.
	 */
	private static float efficiencyFactor(ItemStack held) {
		for (var enchantment : held.getTagEnchantments()
			.entrySet())
			if (enchantment.getKey()
				.is(Enchantments.EFFICIENCY))
				return 1.0F + PER_LEVEL * enchantment.getIntValue();
		return 1.0F;
	}
}
