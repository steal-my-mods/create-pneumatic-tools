package com.createpneumatictools.item;

import com.createpneumatictools.CPTConfig;
import com.createpneumatictools.registry.CPTTags;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Tunnelling Drill with sixteen more drills bolted round it: one dig clears a 5x5 slice.
 *
 * <p>The only tool in the mod that cannot be made at a workbench. Five blocks across is a recipe five
 * blocks across, and Create already has the machine for that — so the Borer is assembled in a
 * Mechanical Crafter, which is both where a tool of this size belongs on the progression and the one
 * thing in the mod that asks you to have built something first.
 *
 * <p>Its numbers all move the same way against the Tunnelling Drill's: <em>wider, slower, dearer</em>.
 * Twenty-five blocks a burst against nine, at a lower speed per block, out of a tank that holds half
 * as many bursts. What that buys is throughput; what it costs is control, because a 5x5 takes down
 * two blocks of wall either side of whatever you were actually aiming at. It is a tool for moving
 * mountains, and the Tunnelling Drill stays the one you carry for corridors you want to keep.
 *
 * <p>Diamond tier, like the Tunnelling Drill, and deliberately not netherite: diamond already breaks
 * every block in the game that a burst could sensibly want, so the only thing a higher tier would add
 * is a speed multiplier this mod does not read.
 */
public class PneumaticBorerItem extends BurstDiggerItem {

	public PneumaticBorerItem(Properties properties) {
		super(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, CPTTags.DRILL_MINEABLE, properties);
	}

	@Override
	protected int radius() {
		return 2;
	}

	@Override
	public int usesPerTank() {
		return CPTConfig.borerUsesPerTank();
	}

	@Override
	public float poweredSpeed(BlockState state, Level level, BlockPos pos) {
		return digs(state) ? CPTConfig.borerSpeed() : 1.0F;
	}
}
