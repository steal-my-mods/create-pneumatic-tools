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
 * that has to be cleaned up when the player stops holding it. Two floats need no cleaning up. The
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
	private static float phase;
	private static float previousPhase;

	private ToolAnimation() {}

	public static void register() {
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ToolAnimation::tick);
	}

	private static void tick(ClientTickEvent.Post event) {
		previousThrottle = throttle;
		previousPhase = phase;
		throttle += (target() - throttle) * SPOOL;
		phase += throttle * FULL_SPEED;
		// Wrapped, or after a long session the float runs out of precision and the spin visibly
		// stutters -- 360 is a whole turn, so wrapping to it is invisible.
		if (phase >= 360.0F)
			phase -= 360.0F;
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

	/** Degrees turned so far, for a part whose own speed is {@code multiplier} times the base. */
	public static float angle(float multiplier, float partialTicks) {
		return Mth.rotLerp(partialTicks, previousPhase, phase) * multiplier;
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
