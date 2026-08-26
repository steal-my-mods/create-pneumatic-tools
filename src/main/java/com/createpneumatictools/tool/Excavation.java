package com.createpneumatictools.tool;

import java.util.function.BiConsumer;

import com.simibubi.create.foundation.utility.BlockHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The guard that stops a tool that breaks extra blocks from breaking them forever.
 *
 * <p>Create's {@link BlockHelper#destroyBlockAs} calls {@code usedTool.mineBlock(...)} for every block
 * it takes down — which is the right thing for it to do, since that is how a tool gets told it was
 * used — but it means a tool whose own {@code mineBlock} calls back into it recurses. The 3x3 driller
 * would tunnel to the world border; the saw would fell a forest one log at a time. Both call through
 * here instead, and both refuse to expand while {@link #cascading()} is true.
 *
 * <p>A {@link ThreadLocal} rather than a field: block breaking is a server-thread affair, but a
 * dedicated server and an integrated one differ in which thread that is, and one static boolean shared
 * between a mis-set flag and a stuck tool is not worth the bytes saved.
 */
public class Excavation {

	private static final ThreadLocal<Boolean> CASCADING = ThreadLocal.withInitial(() -> false);

	private Excavation() {}

	/** True while a tool is already taking down the blocks around one it broke. */
	public static boolean cascading() {
		return CASCADING.get();
	}

	/** Runs {@code body} with {@link #cascading()} set, so nothing inside it expands again. */
	public static void cascade(Runnable body) {
		if (CASCADING.get()) {
			body.run();
			return;
		}
		CASCADING.set(true);
		try {
			body.run();
		} finally {
			CASCADING.set(false);
		}
	}

	/**
	 * Breaks one extra block as the player, dropping what it drops where it stood.
	 *
	 * <p>Goes through Create's helper rather than {@code Level.destroyBlock} so that the tool's
	 * enchantments are honoured, a break event is posted for whatever protection mod is listening, and
	 * the particle rate matches what Create's own drills and saws look like.
	 */
	public static void breakAs(Level level, BlockPos pos, Player player, ItemStack tool,
		float effectChance) {
		BlockHelper.destroyBlockAs(level, pos, player, tool, effectChance,
			drop -> Block.popResource(level, pos, drop));
	}

	/** A drop sink that pops each stack where its own block stood. Used with Create's tree cutter. */
	public static BiConsumer<BlockPos, ItemStack> dropInPlace(Level level) {
		return (pos, stack) -> Block.popResource(level, pos, stack);
	}
}
