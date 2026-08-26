package com.createpneumatictools.client;

import com.createpneumatictools.client.PneumaticToolRenderer.Motion;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.CustomRenderedItems;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * How a pneumatic tool is held: steadily, and pressed into the work — not swung.
 *
 * <p>Vanilla swings whatever is in your hand every time you break a block or a use succeeds, which is
 * right for a pickaxe and wrong for everything in this mod. You do not swing a drill; you hold it
 * against the thing and lean on it. {@code applyForgeHandTransform} is the hook that lets an item
 * replace that outright: return true and the whole first-person arm transform is yours, swing
 * included.
 *
 * <p>What replaces it is vanilla's own resting position — so the tool sits exactly where a pickaxe
 * would and the equip animation still plays — plus a push forward and a slight tip down while the
 * tool is under load, and, for the jackhammer, a judder along the barrel in time with its chisel.
 *
 * <p><b>This is first person only.</b> The third-person swing is driven by {@code attackAnim} on the
 * player, which {@code HumanoidModel.setupAttackAnimation} applies <em>after</em> the arm pose — so a
 * custom {@code ArmPose} cannot suppress it either. Stopping it needs a mixin into vanilla's model or
 * into {@code Minecraft.continueAttack}, and a rendering mixin is a poor trade against other mods for
 * something only bystanders see.
 */
public class PneumaticItemExtensions implements IClientItemExtensions {

	/** How far the tool presses forward under full load, in blocks. */
	private static final float PUSH = 0.10F;
	/** How far it tips down with it, in degrees. Enough to read as leaning, not as aiming elsewhere. */
	private static final float LEAN = 7.0F;
	/** Amplitude of the jackhammer's judder. Small: it should read as a tool, not as a camera shake. */
	private static final float JUDDER = 0.014F;

	private final CustomRenderedItemModelRenderer renderer;
	private final Motion motion;
	private final float speed;

	private PneumaticItemExtensions(CustomRenderedItemModelRenderer renderer, Motion motion,
		float speed) {
		this.renderer = renderer;
		this.motion = motion;
		this.speed = speed;
	}

	/**
	 * Registers {@code item} for Create's model swap and returns its extensions.
	 *
	 * <p>The registration is what {@code SimpleCustomRenderer.create} would have done; this does it by
	 * hand because it also needs to be an {@code IClientItemExtensions} of its own with a hand
	 * transform on it, and Create's class is not built to be extended that way.
	 */
	public static PneumaticItemExtensions create(Item item, CustomRenderedItemModelRenderer renderer,
		Motion motion, float speed) {
		CustomRenderedItems.register(item);
		return new PneumaticItemExtensions(renderer, motion, speed);
	}

	@Override
	public BlockEntityWithoutLevelRenderer getCustomRenderer() {
		return renderer;
	}

	@Override
	public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm,
		ItemStack held, float partialTick, float equipProcess, float swingProcess) {
		int side = arm == HumanoidArm.RIGHT ? 1 : -1;
		// Vanilla's resting position, verbatim, so a tool hangs where every other item hangs and the
		// equip animation is untouched. What is deliberately *not* copied is the attack transform.
		pose.translate(side * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);

		float load = ToolAnimation.load(partialTick);
		pose.translate(0.0F, -0.02F * load, -PUSH * load);
		pose.mulPose(Axis.XP.rotationDegrees(-LEAN * load));

		if (motion == Motion.HAMMER) {
			// In time with the chisel, so the tool and the part are one movement rather than two.
			float phase = ToolAnimation.angle(speed, partialTick) * Mth.DEG_TO_RAD;
			pose.translate(0.0F, 0.0F, Mth.sin(phase) * JUDDER * load);
		}
		return true;
	}
}
