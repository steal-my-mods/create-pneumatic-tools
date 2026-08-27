package com.createpneumatictools.item;

import com.createpneumatictools.CPTConfig;
import com.simibubi.create.AllSoundEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.DataMapHooks;

/**
 * The additive half of the pair: a pad loaded with wax and spun fast enough that the finish it leaves
 * behind is a seal. Held against copper, it waxes it.
 *
 * <p>No Honeycomb is consumed, and the reason is in the recipe rather than in the physics: the tool
 * is built around a Honeycomb Block, so the wax was paid for once when it was made. That is the same
 * bargain every other tool here strikes — a Mechanical Drill goes into the Hand Drill and is not
 * consumed per block — and it is what lets the Grinder take wax off without any coming back.
 *
 * <p>Everything this tool does, it does to a block in the world. It used to polish a stack of Rose
 * Quartz out of the other hand as well, which was a second job on a second click that had nothing to
 * do with the first; a Sand Paper already does that, and doing it from a hotbar slot made the tool
 * read as a crafting shortcut rather than as something you point at the work.
 */
public class PneumaticBufferItem extends PneumaticToolItem {

	public PneumaticBufferItem(Properties properties) {
		super(properties);
	}

	@Override
	public int usesPerTank() {
		return CPTConfig.bufferUsesPerTank();
	}

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
		// PASS, not FAIL: a click that found nothing to do should fall through to whatever else could
		// answer it, which is the same thing the Grinder does with a block it cannot treat.
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
}
