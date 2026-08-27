package com.createpneumatictools.tool;

import java.util.function.BiConsumer;

import com.createpneumatictools.CreatePneumaticTools;
import com.simibubi.create.foundation.utility.BlockHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The guard that stops a tool that breaks extra blocks from breaking them forever, and keeps the
 * extra blocks inside the rules that only ever applied to the one the player swung at.
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
@EventBusSubscriber(modid = CreatePneumaticTools.ID)
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
	 * Vetoes a cascaded break the player would not have been allowed to make by hand, and counts the
	 * ones that go through.
	 *
	 * <p>Vanilla checks spawn protection and the world border in {@code ServerGamePacketListenerImpl},
	 * against the position in the packet — so they cover the block a player actually swung at and
	 * nothing else. The tunneller's eight neighbours and every log in the saw's canopy are broken
	 * through Create's helper instead, which no packet ever described. Without this, standing one
	 * block outside a spawn radius and drilling the boundary takes a 3x3 out of the middle of it.
	 *
	 * <p>Claim and protection mods were already safe: {@code destroyBlockAs} posts this same event and
	 * honours a cancellation. It is vanilla's own two gates, which are not listeners at all, that
	 * needed asking on the extra blocks' behalf.
	 *
	 * <p>{@code LOWEST} on purpose: there is no point asking whether a block is inside the world
	 * border when a claim mod has already said no, and a listener only sees a cancelled event if it
	 * asks to — which this one does not.
	 */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void aCascadeStaysInsideTheRules(BlockEvent.BreakEvent event) {
		if (!cascading())
			return;
		if (event.getLevel() instanceof Level level
			&& !level.mayInteract(event.getPlayer(), event.getPos()))
			event.setCanceled(true);
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
