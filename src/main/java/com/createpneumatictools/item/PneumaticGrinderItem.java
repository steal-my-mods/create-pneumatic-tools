package com.createpneumatictools.item;

import com.createpneumatictools.CPTConfig;
import com.simibubi.create.AllSoundEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * The subtractive half of the pair: a coarse abrasive wheel that takes a layer off whatever it is held
 * against. Bark off a log, oxide off copper, wax off a sealed block.
 *
 * <p>All three of those are one vanilla concept — an {@link ItemAbility} — so the tool does not carry
 * three lists of blocks. It asks the block itself, in order, what it would become under a strip, a
 * scrape and a wax-off, and applies the first answer that is not null. Modded logs and modded copper
 * therefore work on the day they are added, without this mod knowing they exist.
 *
 * <p>{@link #canPerformAction} is not decoration: NeoForge's {@code getToolModifiedState} asks the
 * held stack whether it can perform the ability before it will answer at all, so leaving it out makes
 * every one of those three queries return null and the tool do nothing.
 */
public class PneumaticGrinderItem extends PneumaticToolItem {

	/**
	 * In the order the grinder tries them. Strip first: a waxed <em>copper</em> log does not exist, so
	 * nothing overlaps, and stripping is the one a player reaches for most.
	 */
	private static final ItemAbility[] TREATMENTS =
		{ItemAbilities.AXE_STRIP, ItemAbilities.AXE_SCRAPE, ItemAbilities.AXE_WAX_OFF};

	public PneumaticGrinderItem(Properties properties) {
		super(properties);
	}

	@Override
	public int usesPerTank() {
		return CPTConfig.grinderUsesPerTank();
	}

	@Override
	public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
		for (ItemAbility treatment : TREATMENTS)
			if (treatment == ability)
				return true;
		return false;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		if (player == null)
			return InteractionResult.PASS;

		for (ItemAbility treatment : TREATMENTS) {
			BlockState treated = state.getToolModifiedState(context, treatment, false);
			if (treated == null)
				continue;
			if (!isPowered(player)) {
				refuse(player);
				// FAIL rather than PASS: the click found something to do and could not do it, and
				// passing here would let the click fall through to whatever else the hand is holding.
				return InteractionResult.FAIL;
			}
			if (!level.isClientSide) {
				if (!spendAir(player)) {
					refuse(player);
					return InteractionResult.FAIL;
				}
				level.setBlockAndUpdate(pos, treated);
				level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, treated));
			}
			announce(level, player, pos, treatment);
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		return InteractionResult.PASS;
	}

	/**
	 * The sound and the sparks. Both sides call this with the acting player passed in, which is the
	 * vanilla convention: the server sends it to everyone <em>except</em> that player, and that
	 * player's own client plays it immediately instead of a tick and a half later.
	 */
	private static void announce(Level level, Player player, BlockPos pos, ItemAbility treatment) {
		AllSoundEvents.SANDING_LONG.play(level, player, pos, 1.0F,
			0.75F + level.random.nextFloat() * 0.1F);
		if (treatment == ItemAbilities.AXE_STRIP) {
			level.playSound(player, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
			return;
		}
		// Vanilla's own scrape/wax-off flourishes: the sound plus the little cloud of particles.
		boolean scrape = treatment == ItemAbilities.AXE_SCRAPE;
		level.playSound(player, pos, scrape ? SoundEvents.AXE_SCRAPE : SoundEvents.AXE_WAX_OFF,
			SoundSource.BLOCKS, 1.0F, 1.0F);
		level.levelEvent(player,
			scrape ? LevelEvent.PARTICLES_SCRAPE : LevelEvent.PARTICLES_WAX_OFF, pos, 0);
	}
}
