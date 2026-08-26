package com.createpneumatictools;

import com.createpneumatictools.registry.CPTBlockEntities;
import com.createpneumatictools.registry.CPTBlocks;
import com.createpneumatictools.registry.CPTItems;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create: Pneumatic Tools — handheld tools that run off a Create backtank.
 *
 * <p>One idea, nine items: the air a Copper Backtank already carries is a portable power supply, and
 * most of what Create does with rotation to a block on the ground it could do to a block in front of
 * you. So a Mechanical Drill, a Mechanical Saw, a Mechanical Press and a Hand Crank all get a version
 * you hold, alongside the shop-floor tools — a jackhammer, a grinder, a buffer and a vacuum — that
 * fill in the gaps between them.
 *
 * <p>Nothing here is a new mechanic. Every tool spends air through Create's own
 * {@code BacktankUtil}, at a "uses per tank" rating in the same unit Create rates the Extendo Grip
 * and the Potato Cannon in. The one block the mod registers is not a block a player ever holds: it is
 * the invisible, temporary source of rotation the Pneumatic Wrench puts down while you hold it
 * against a shaft.
 */
@Mod(CreatePneumaticTools.ID)
public class CreatePneumaticTools {

	public static final String ID = "createpneumatictools";
	public static final Logger LOGGER = LoggerFactory.getLogger("Create: Pneumatic Tools");

	public CreatePneumaticTools(IEventBus modBus, ModContainer container) {
		CPTBlocks.BLOCKS.register(modBus);
		CPTBlockEntities.BLOCK_ENTITIES.register(modBus);
		CPTItems.ITEMS.register(modBus);
		CPTItems.TABS.register(modBus);

		modBus.addListener(CPTBlocks::registerStressValues);

		if (FMLEnvironment.dist == Dist.CLIENT)
			com.createpneumatictools.client.CPTClient.init(modBus);

		container.registerConfig(ModConfig.Type.SERVER, CPTConfig.SPEC);
	}

	public static ResourceLocation asResource(String path) {
		return ResourceLocation.fromNamespaceAndPath(ID, path);
	}
}
