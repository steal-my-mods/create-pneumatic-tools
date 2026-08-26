package com.createpneumatictools.client;

import com.createpneumatictools.CreatePneumaticTools;
import com.createpneumatictools.registry.CPTItems;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.Item;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Checks at startup that every tool's 3D model is actually wired up, and says so in the log if it is
 * not.
 *
 * <p>Every link in that chain fails silently, which is the whole reason this exists. A tool whose
 * {@code initializeClient} never reached {@link CPTRenderers} keeps its base model and simply never
 * animates. A partial model whose file is missing bakes as the black-and-magenta placeholder, which
 * looks like a texture problem rather than a path problem. A base model that failed to parse leaves
 * the item drawn as a flat sprite. None of the three logs anything, and all three look plausible
 * enough in a hotbar to survive a glance — the first two drafts of this mod's renderer shipped a
 * static tool for exactly that reason.
 *
 * <p>Development only. In production the files are in the jar and none of this can fire, so it costs
 * one boolean per tick and stops.
 */
public class CPTModelCheck {

	private static boolean checked;

	public static void register() {
		if (FMLEnvironment.production)
			return;
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CPTModelCheck::onClientTick);
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		if (checked)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		// Models are baked during the first resource load, which finishes after the first few ticks.
		if (minecraft.getModelManager()
			.getMissingModel() == null)
			return;
		checked = true;
		check(minecraft);
	}

	private static void check(Minecraft minecraft) {
		BakedModel missing = minecraft.getModelManager()
			.getMissingModel();
		int wired = 0;

		for (var entry : CPTItems.all()) {
			Item item = entry.get();
			BakedModel baked = minecraft.getItemRenderer()
				.getItemModelShaper()
				.getItemModel(item);
			if (baked == missing) {
				CreatePneumaticTools.LOGGER.warn("{} has no baked model at all", item);
				continue;
			}
			if (!(baked instanceof CustomRenderedItemModel)) {
				CreatePneumaticTools.LOGGER.warn(
					"{} was not swapped for a CustomRenderedItemModel, so it will render as a static "
						+ "model -- CPTRenderers never saw it, or it registered too late to be baked",
					item);
				continue;
			}
			wired++;
		}

		int parts = 0;
		for (var mount : CPTPartials.all()) {
			PartialModel model = mount.model();
			BakedModel baked = model.get();
			if (baked == null || baked == missing) {
				CreatePneumaticTools.LOGGER.warn("the partial model {} did not bake", model.modelLocation());
				continue;
			}
			parts++;
		}

		// A check that passed because it inspected nothing is the failure mode this file was written
		// for.
		if (wired == 0 || parts == 0)
			CreatePneumaticTools.LOGGER.warn("model check inspected {} tools and {} parts -- "
				+ "the registries look empty, so this check proved nothing", wired, parts);
		else
			CreatePneumaticTools.LOGGER.info("{} tools and {} moving parts are wired for 3D rendering",
				wired, parts);
	}
}
