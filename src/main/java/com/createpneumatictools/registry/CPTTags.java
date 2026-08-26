package com.createpneumatictools.registry;

import com.createpneumatictools.CreatePneumaticTools;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class CPTTags {

	/**
	 * What a drill will chew through: everything a pickaxe or a shovel handles.
	 *
	 * <p>A tag rather than two rules on the tool component, because the component's rules are checked
	 * in order and the first one with a speed wins — two overlapping rules would silently make the
	 * order load-bearing. A pack can also widen this without touching the mod.
	 */
	public static final TagKey<Block> DRILL_MINEABLE = block("drill_mineable");

	private static TagKey<Block> block(String path) {
		return TagKey.create(Registries.BLOCK, CreatePneumaticTools.asResource(path));
	}
}
