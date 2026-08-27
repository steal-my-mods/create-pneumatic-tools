package com.createpneumatictools.client;

import com.createpneumatictools.item.PneumaticToolItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The clock every moving part runs off: one throttle and one free-running phase, ticked once per
 * client tick.
 *
 * <p>One shared pair rather than a state object per tool, because there is only ever one thing in a
 * player's hand and the alternative is a map keyed by something — the item, the stack, the hand —
 * that has to be cleaned up when the player stops holding it. Two numbers need no cleaning up. The
 * cost is that a tool in each hand runs in phase with the other, which nobody will ever see.
 *
 * <p>The throttle is <em>eased</em> towards its target rather than snapped to it, and that is the
 * whole difference between a tool and a texture: real air tools spool up and coast down, and a drill
 * that reaches full speed on the frame you press the button reads as a looping animation. The phase
 * accumulates the eased speed, so changing the throttle never jumps the rotation.
 */
public class ToolAnimation {

	/** Degrees per tick at full throttle, before each tool's own multiplier. */
	private static final float FULL_SPEED = 30.0F;
	/** What a tool with air in the line does while you are just carrying it. */
	private static final float IDLE_THROTTLE = 0.3F;
	/** How much of the gap to the target is closed each tick. A quarter is about half a second. */
	private static final float SPOOL = 0.25F;

	private static float throttle;
	private static float previousThrottle;
	/**
	 * Degrees turned at the base rate, never wrapped, which is why it is a double.
	 *
	 * <p>Wrapping it to 360 here is the obvious thing and is wrong: each tool multiplies this by its
	 * own speed, and {@code 360 * multiplier} is a whole number of turns only when the multiplier is
	 * an integer. So a wrap snapped every part by {@code 360 * frac(multiplier)} — 216 degrees on the
	 * Saw at 1.6, twice a second at full throttle — and only the two tools set to exactly 1.0 came out
	 * of it looking continuous. The wrap belongs after the multiplication instead, in {@link #angle}.
	 *
	 * <p>Which leaves the precision this used to accumulate without bound: a float runs out of it in a
	 * long session and the spin visibly stutters, at 600 degrees a second. A double has fifty-three
	 * bits of mantissa, so the same drift needs longer than any world will run.
	 */
	private static double phase;
	private static double previousPhase;

	private ToolAnimation() {}

	public static void register() {
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ToolAnimation::tick);
	}

	private static void tick(ClientTickEvent.Post event) {
		previousThrottle = throttle;
		previousPhase = phase;
		throttle += (target() - throttle) * SPOOL;
		phase += (double) throttle * FULL_SPEED;
	}

	private static float target() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.isPaused())
			return 0.0F;
		if (!holdingAPoweredTool(player))
			return 0.0F;
		return working(minecraft, player) ? 1.0F : IDLE_THROTTLE;
	}

	/**
	 * Whether either hand holds one of this mod's tools with air behind it.
	 *
	 * <p>The air check is what makes an empty backtank visible before you swing at anything: the tool
	 * goes quiet in your hand. It reads the player's own armour slots, which the client has.
	 */
	private static boolean holdingAPoweredTool(LocalPlayer player) {
		for (InteractionHand hand : InteractionHand.values())
			if (player.getItemInHand(hand)
				.getItem() instanceof PneumaticToolItem tool && tool.isPowered(player))
				return true;
		return false;
	}

	/**
	 * Whether the tool is being asked to do something, as opposed to being carried.
	 *
	 * <p><b>Both mouse buttons.</b> Half these tools are worked with the right one — the wrench, the
	 * vacuum, the grinder, the buffer — and half with the left, because breaking a block is an attack.
	 * The first version of this watched the use key and {@code isDestroying}, which sounds like it
	 * covers the second half and does not: {@code isDestroying} is only true once a block has actually
	 * begun to give, so a drill pointed at bedrock, at air, or at a block that pops in one tick never
	 * spun up. Holding the button is the honest signal — that is when a real air tool is running,
	 * whether or not it is achieving anything.
	 */
	private static boolean working(Minecraft minecraft, LocalPlayer player) {
		if (player.isUsingItem())
			return true;
		if (minecraft.screen != null)
			return false;
		return minecraft.options.keyAttack.isDown() || minecraft.options.keyUse.isDown();
	}

	/**
	 * Degrees turned so far, for a part whose own speed is {@code multiplier} times the base.
	 *
	 * <p>Interpolate, then scale, then wrap — in that order. {@link #phase} only ever rises, so the
	 * interpolation is a plain lerp with no wrap to step over, and taking the whole turns off the
	 * scaled figure is what keeps the part continuous at any multiplier. See {@link #phase}.
	 */
	public static float angle(float multiplier, float partialTicks) {
		double turned = Mth.lerp(partialTicks, previousPhase, phase);
		return (float) (turned * multiplier % 360.0);
	}

	/** 0 while stopped, 1 at full speed. Used by the reciprocating parts to damp their travel. */
	public static float throttle(float partialTicks) {
		return Mth.lerp(partialTicks, previousThrottle, throttle);
	}

	/**
	 * 0 while merely carried, 1 while working. The throttle with the idle part taken off.
	 *
	 * <p>Separate from {@link #throttle} because the two are wanted for different things: the parts
	 * turn over whenever there is air in the line, but the tool should only lean into the work when
	 * there is work. Derived rather than tracked, so there is no second clock to keep in step.
	 */
	public static float load(float partialTicks) {
		return Mth.clamp((throttle(partialTicks) - IDLE_THROTTLE) / (1.0F - IDLE_THROTTLE), 0.0F,
			1.0F);
	}
}
