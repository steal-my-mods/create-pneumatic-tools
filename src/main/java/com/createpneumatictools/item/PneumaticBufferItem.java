package com.createpneumatictools.item;

import com.createpneumatictools.CPTConfig;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.sandPaper.SandPaperItem;
import com.simibubi.create.content.equipment.sandPaper.SandPaperPolishingRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.DataMapHooks;

/**
 * The additive half of the pair: a soft pad spun fast enough that the finish it leaves behind is a
 * seal. Polishes Rose Quartz, and buffs copper into its waxed form with no Honeycomb involved.
 *
 * <p>Waxing without honeycomb is the one liberty this mod takes with vanilla's economy, and it is a
 * deliberate one: friction sealing is a real finishing process, and a tool that can take wax off (the
 * Grinder) with no honeycomb coming back is only half a pair. The cost moved from an item to the tank.
 *
 * <p>Polishing does the whole stack in one click, which is the only reason it is worth carrying over a
 * Sand Paper: the paper already polishes quartz, one item per thirty-two ticks. The air pays per item,
 * and when the tank empties partway the loop stops, leaving the remainder in hand where you can see
 * how far it got.
 *
 * <p>The two jobs are on different clicks and cannot collide. Waxing is {@link #useOn}, which vanilla
 * only calls when a block was clicked; polishing is {@link #use}, which vanilla only reaches when the
 * block click was <em>not</em> consumed. Hence the {@code PASS} on a block that will not wax — a
 * {@code FAIL} there would swallow the click and stop you polishing while facing a wall.
 */
public class PneumaticBufferItem extends PneumaticToolItem {

	public PneumaticBufferItem(Properties properties) {
		super(properties);
	}

	@Override
	public int usesPerTank() {
		return CPTConfig.bufferUsesPerTank();
	}

	// --- waxing ------------------------------------------------------------------------------------

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		if (player == null)
			return InteractionResult.PASS;

		// The NeoForge data map, not HoneycombItem's hardcoded BiMap: the map is what a datapack or
		// another mod adds its own waxable pair to, and the vanilla field is deprecated for reading.
		Block waxed = DataMapHooks.getBlockWaxed(state.getBlock());
		if (waxed == null)
			return InteractionResult.PASS;

		if (!isPowered(player)) {
			refuse(player);
			return InteractionResult.FAIL;
		}
		if (!level.isClientSide) {
			if (!spendAir(player)) {
				refuse(player);
				return InteractionResult.FAIL;
			}
			BlockState sealed = waxed.withPropertiesOf(state);
			level.setBlockAndUpdate(pos, sealed);
			level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, sealed));
		}
		AllSoundEvents.SANDING_LONG.play(level, player, pos, 1.0F,
			1.35F + level.random.nextFloat() * 0.1F);
		level.playSound(player, pos, SoundEvents.HONEYCOMB_WAX_ON, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.levelEvent(player, LevelEvent.PARTICLES_AND_SOUND_WAX_ON, pos, 0);
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	// --- polishing ---------------------------------------------------------------------------------

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack buffer = player.getItemInHand(hand);
		InteractionHand otherHand =
			hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack feedstock = player.getItemInHand(otherHand);

		if (feedstock.isEmpty() || !SandPaperPolishingRecipe.canPolish(level, feedstock))
			return InteractionResultHolder.pass(buffer);
		if (!isPowered(player)) {
			refuse(player);
			return InteractionResultHolder.fail(buffer);
		}
		if (level.isClientSide) {
			SandPaperItem.spawnParticles(player.getEyePosition(1.0F)
				.add(player.getLookAngle()
					.scale(0.5)),
				feedstock, level);
			return InteractionResultHolder.success(buffer);
		}

		int done = 0;
		// spendAir last in the condition: it charges, so it must not run once more than it works.
		while (!feedstock.isEmpty() && SandPaperPolishingRecipe.canPolish(level, feedstock)
			&& spendAir(player)) {
			ItemStack one = feedstock.split(1);
			ItemStack polished =
				SandPaperPolishingRecipe.applyPolish(level, player.position(), one, ItemStack.EMPTY);
			if (!polished.isEmpty())
				player.getInventory()
					.placeItemBackInInventory(polished);
			if (one.hasCraftingRemainingItem())
				player.getInventory()
					.placeItemBackInInventory(one.getCraftingRemainingItem());
			done++;
		}
		// split() leaves an empty-but-present stack in the slot; hand the slot a real EMPTY instead.
		if (feedstock.isEmpty())
			player.setItemInHand(otherHand, ItemStack.EMPTY);

		if (done == 0) {
			refuse(player);
			return InteractionResultHolder.fail(buffer);
		}
		AllSoundEvents.SANDING_LONG.play(level, player, player.blockPosition(), 1.0F, 1.35F);
		return InteractionResultHolder.success(buffer);
	}
}
