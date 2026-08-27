package com.createpneumatictools.item;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base for the five tools that break blocks.
 *
 * <p>The split between the item and {@link com.createpneumatictools.tool.DiggingHandler} is the thing
 * to understand here. The item's {@code TOOL} data component carries only <em>which blocks this tool
 * is allowed to drop</em>, at a mining speed of exactly 1 — every scrap of actual speed is added by
 * the event, and only while the tank has air. That is not a stylistic choice: the component is baked
 * into {@code Item.Properties} at registration, which happens long before the config file is read, so
 * a speed put there could never be configurable. Keeping the component at 1 also makes "empty tank"
 * mean "the handler adds nothing" rather than a subtraction that has to be got exactly right.
 *
 * <p>Pinning the component at 1 does shut vanilla's Efficiency out — it adds
 * {@code MINING_EFFICIENCY} only once the tool's own contribution exceeds 1 — so
 * {@code DiggingHandler.efficiencyFactor} applies that bonus itself, on the near side of the gate,
 * where the rest of the speed already lives. The component stays at 1,
 * an empty tank still means bare hands, and the book still does something. Haste and Mining Fatigue
 * need no help: they are multiplicative and land before the event.
 *
 * <p>{@code damagePerBlock} is 0 and no durability is declared: these wear out by running dry, not by
 * being used.
 */
public abstract class PneumaticDiggerItem extends PneumaticToolItem {

	/**
	 * What the tool digs. Speed is granted on tag membership alone, matching vanilla — a pickaxe swings
	 * at pickaxe speed on Obsidian whether or not it is good enough to drop it.
	 */
	private final TagKey<Block> mineable;

	protected PneumaticDiggerItem(TagKey<Block> incorrectForDrops, TagKey<Block> mineable,
		Properties properties) {
		super(properties.component(DataComponents.TOOL,
			new Tool(List.of(Tool.Rule.deniesDrops(incorrectForDrops),
				Tool.Rule.minesAndDrops(mineable, 1.0F)), 1.0F, 0)));
		this.mineable = mineable;
	}

	/** Whether {@code state} is this tool's business at all, tier aside. */
	public boolean digs(BlockState state) {
		return state.is(mineable);
	}

	/**
	 * How much faster than bare hands this tool digs {@code state} while it has air, as a multiplier on
	 * the speed vanilla worked out. 1 means "not what this tool is for".
	 *
	 * @param pos where the block is, or null when the caller could not say — the break-speed event
	 *            still allows a missing position, and hardness is read from it
	 */
	public abstract float poweredSpeed(BlockState state, Level level, BlockPos pos);

	/**
	 * Charges the tank and does whatever else the tool does to a block it has just broken.
	 *
	 * <p>Called from {@link #mineBlock} with the block still standing: vanilla runs
	 * {@code ItemStack.mineBlock} before {@code removeBlock}, which is what lets the saw hand the
	 * position straight to Create's tree search.
	 */
	protected abstract void dig(ItemStack stack, Level level, BlockState state, BlockPos pos,
		Player player);

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos,
		LivingEntity miner) {
		if (level.isClientSide || !(miner instanceof Player player))
			return false;
		// Blocks that pop the instant you touch them -- grass, torches, flowers -- are free. Charging
		// air for those would drain a tank walking through a meadow.
		if (state.getDestroySpeed(level, pos) == 0.0F)
			return false;
		dig(stack, level, state, pos, player);
		return true;
	}

	/** Hardness at a position, falling back to the block's declared time when there is no position. */
	protected static float hardness(BlockState state, Level level, BlockPos pos) {
		return pos == null ? state.getBlock()
			.defaultDestroyTime() : state.getDestroySpeed(level, pos);
	}

	/** Whether this tool would drop {@code state}, the way the held stack decides it. */
	protected static boolean canHarvest(ItemStack stack, BlockState state) {
		return !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
	}
}
