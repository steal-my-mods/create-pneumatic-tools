package com.createpneumatictools.client;

import com.createpneumatictools.registry.CPTItems;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;

import net.createmod.catnip.lang.FontHelper.Palette;
import net.minecraft.world.item.Item;

/**
 * Gives every tool the tooltip a Create item has: a summary line and the behaviour list behind Hold
 * Shift.
 *
 * <p>None of that is Create-only machinery. {@code TooltipModifier.REGISTRY} is a public registry
 * keyed by item, Create's client reads it for every item regardless of namespace, and
 * {@code ItemDescription} derives its keys from the item's own description id. Registrate wires this
 * up for Create's own items; doing it by hand is the whole difference.
 */
public class CPTTooltips {

	public static void register() {
		for (var item : CPTItems.all())
			add(item.get());
	}

	private static void add(Item item) {
		TooltipModifier.REGISTRY.register(item, new ItemDescription.Modifier(item, Palette.STANDARD_CREATE));
	}
}
