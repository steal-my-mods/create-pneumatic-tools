package com.createpneumatictools.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public class CPTClient {

	public static void init(IEventBus modBus) {
		modBus.addListener(CPTClient::clientSetup);
		// Touching CPTPartials is what registers its models for baking, and that has to happen before
		// the game asks which extra models it needs -- which is during resource loading, well before
		// client setup. Mod construction is early enough; client setup is not.
		CPTPartials.init();
	}

	private static void clientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			CPTTooltips.register();
			ToolAnimation.register();
			CPTModelCheck.register();
			CPTPhotoShoot.register();
		});
	}
}
