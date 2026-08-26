package com.createpneumatictools.air;

import com.simibubi.create.content.equipment.armor.BacktankUtil;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The one place this mod touches a backtank.
 *
 * <p>Every tool here is rated in <em>uses per tank</em> rather than in air units, which is the unit
 * Create already states its own equipment in — the Extendo Grip is 1000 actions per tank, the Potato
 * Cannon 200 shots — so the numbers in this mod's config can be read against Create's without
 * converting anything. {@link BacktankUtil#canAbsorbDamage} turns a rating into a cost the same way
 * for all of them: {@code max(airInBacktank / usesPerTank, 1)}, charged against the <em>emptiest</em>
 * tank the player is wearing, so a second backtank is a second tank rather than a spare.
 *
 * <p>A rating of zero means free, matching what Create's own config comments promise for
 * {@code maxExtendoGripActions} and {@code maxPotatoCannonShots}: "set to 0 makes them unbreakable".
 * A tool set to 0 needs no backtank at all — which is why {@link #isPowered} has to special-case it
 * rather than just asking whether a tank is present.
 */
public class AirSupply {

	private AirSupply() {}

	/**
	 * Whether the tool would work right now, without spending anything.
	 *
	 * <p>Called from mining-speed code, which runs on both sides. That is safe: a player's own armour
	 * slots are synced to their own client, so the client's copy of the backtank carries the same air
	 * value the server is charging against.
	 */
	public static boolean isPowered(LivingEntity entity, int usesPerTank) {
		if (usesPerTank <= 0)
			return true;
		if (entity instanceof Player player && player.isCreative())
			return true;
		return !BacktankUtil.getAllWithAir(entity)
			.isEmpty();
	}

	/**
	 * Charges one use against the wearer's backtanks, and says whether it went through.
	 *
	 * <p>Server side only. Spending on the client would edit a stack the next inventory sync
	 * overwrites, and would double-charge nothing but the player's own display.
	 */
	public static boolean spend(LivingEntity entity, int usesPerTank) {
		if (usesPerTank <= 0)
			return true;
		if (entity.level().isClientSide)
			return isPowered(entity, usesPerTank);
		return BacktankUtil.canAbsorbDamage(entity, usesPerTank);
	}
}
