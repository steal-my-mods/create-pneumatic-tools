package com.createpneumatictools.client;

import java.util.IdentityHashMap;
import java.util.Map;

import com.createpneumatictools.client.PneumaticToolRenderer.Motion;
import com.createpneumatictools.registry.CPTItems;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * Which renderer each tool gets, and how fast its part moves.
 *
 * <p>The speeds are multipliers on the shared clock, not RPM, and they are set by ear rather than by
 * physics: a grinding wheel has to look faster than a socket driver or the two read as the same
 * mechanism in different colours. The one rule they follow is that nothing turns fast enough to
 * alias — past about two and a half turns a second a spinning wheel starts to look like it is going
 * backwards, and a saw blade that appears to run in reverse is worse than one that turns slowly.
 *
 * <p>Built once, on the first call, because registering an item is what tells Create to swap its baked
 * model for one it will call back into — so every tool has to pass through here before models are
 * baked, not just the ones a player happens to hold.
 */
public class CPTRenderers {

	private static Map<Item, IClientItemExtensions> extensions;

	public static IClientItemExtensions of(Item item) {
		if (extensions == null)
			extensions = build();
		return extensions.get(item);
	}

	private static Map<Item, IClientItemExtensions> build() {
		Map<Item, IClientItemExtensions> map = new IdentityHashMap<>();
		add(map, CPTItems.HAND_DRILL.get(), Motion.SPIN_AXIAL, 1.4F, CPTPartials.HAND_DRILL_BIT);
		add(map, CPTItems.TUNNEL_DRILL.get(), Motion.SPIN_AXIAL, 1.0F,
			CPTPartials.TUNNEL_DRILL_BITS);
		// The jackhammer is the one tool that does not turn. A breaker that spins is a drill, and the
		// pair of them are otherwise the same silhouette in the same colours.
		add(map, CPTItems.JACKHAMMER.get(), Motion.HAMMER, 2.2F, CPTPartials.JACKHAMMER_CHISEL);
		// The Borer's plate is the widest moving thing in the mod, and a wide wheel aliases at a lower
		// rate than a narrow one: what matters is how fast the rim travels, not how fast the hub turns.
		add(map, CPTItems.BORER.get(), Motion.SPIN_AXIAL, 0.7F, CPTPartials.BORER_HEAD);
		add(map, CPTItems.SAW.get(), Motion.SPIN_FACE, 1.6F, CPTPartials.SAW_BLADE);
		add(map, CPTItems.GRINDER.get(), Motion.SPIN_FACE, 1.8F, CPTPartials.GRINDER_WHEEL);
		// SPIN_AXIAL, not SPIN_FACE: the pad faces the work rather than standing up beside the barrel,
		// so it turns about the barrel. Slow, because a polishing pad is not an abrasive wheel.
		add(map, CPTItems.BUFFER.get(), Motion.SPIN_AXIAL, 1.0F, CPTPartials.BUFFER_PAD);
		add(map, CPTItems.VACUUM_WAND.get(), Motion.SPIN_AXIAL, 1.5F, CPTPartials.VACUUM_IMPELLER);
		add(map, CPTItems.WRENCH.get(), Motion.SPIN_AXIAL, 0.8F, CPTPartials.WRENCH_SOCKET);
		return map;
	}

	private static void add(Map<Item, IClientItemExtensions> map, Item item, Motion motion,
		float speed, CPTPartials.Mount... mounts) {
		map.put(item, PneumaticItemExtensions.create(item,
			new PneumaticToolRenderer(motion, speed, mounts), motion, speed));
	}
}
