package com.createpneumatictools.registry;

import java.util.List;

import com.createpneumatictools.CreatePneumaticTools;
import com.createpneumatictools.item.HandDrillItem;
import com.createpneumatictools.item.JackhammerItem;
import com.createpneumatictools.item.PneumaticBorerItem;
import com.createpneumatictools.item.PneumaticBufferItem;
import com.createpneumatictools.item.PneumaticGrinderItem;
import com.createpneumatictools.item.PneumaticSawItem;
import com.createpneumatictools.item.PneumaticWrenchItem;
import com.createpneumatictools.item.TunnelDrillItem;
import com.createpneumatictools.item.VacuumWandItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every tool in the mod.
 *
 * <p>None of them declares durability. The bar under a pneumatic tool is its backtank's, drawn by
 * {@link com.createpneumatictools.item.PneumaticToolItem}, and an item that also wore out would give a
 * player two numbers to watch and no way to see either one clearly.
 */
public class CPTItems {

	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePneumaticTools.ID);

	public static final DeferredRegister<CreativeModeTab> TABS =
		DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreatePneumaticTools.ID);

	public static final DeferredItem<HandDrillItem> HAND_DRILL =
		ITEMS.registerItem("hand_drill", HandDrillItem::new);

	public static final DeferredItem<JackhammerItem> JACKHAMMER =
		ITEMS.registerItem("pneumatic_jackhammer", JackhammerItem::new);

	public static final DeferredItem<TunnelDrillItem> TUNNEL_DRILL =
		ITEMS.registerItem("tunnel_drill", TunnelDrillItem::new);

	public static final DeferredItem<PneumaticBorerItem> BORER =
		ITEMS.registerItem("pneumatic_borer", PneumaticBorerItem::new);

	public static final DeferredItem<PneumaticSawItem> SAW =
		ITEMS.registerItem("pneumatic_saw", PneumaticSawItem::new);

	public static final DeferredItem<PneumaticGrinderItem> GRINDER =
		ITEMS.registerItem("pneumatic_grinder", PneumaticGrinderItem::new);

	public static final DeferredItem<PneumaticBufferItem> BUFFER =
		ITEMS.registerItem("pneumatic_buffer", PneumaticBufferItem::new);

	public static final DeferredItem<VacuumWandItem> VACUUM_WAND =
		ITEMS.registerItem("pneumatic_vacuum_wand", VacuumWandItem::new);

	public static final DeferredItem<PneumaticWrenchItem> WRENCH =
		ITEMS.registerItem("pneumatic_wrench", PneumaticWrenchItem::new);

	/** Tab order, and the order everything else in the mod lists them in. */
	public static List<DeferredItem<? extends Item>> all() {
		return List.of(HAND_DRILL, JACKHAMMER, TUNNEL_DRILL, BORER, SAW, GRINDER, BUFFER,
			VACUUM_WAND, WRENCH);
	}

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
		() -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.createpneumatictools"))
			.icon(() -> HAND_DRILL.get()
				.getDefaultInstance())
			.displayItems((params, output) -> all().forEach(item -> output.accept(item.get())))
			.build());
}
