package com.createpneumatictools.client;

import com.createpneumatictools.client.CPTPartials.Mount;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws one tool: its static body, then its moving parts where {@link CPTPartials} says they go.
 *
 * <p>One renderer for all nine rather than nine bespoke ones. Create writes a class per item because
 * each of its animated items moves in a genuinely different way; every tool here is one of four
 * motions applied to one part, so a class apiece would be eight copies of the same twelve lines with
 * a different constant in them.
 *
 * <p>Two things about the transform order are load-bearing. The pose already has its origin at the
 * middle of the item when {@code render} is called, and every part is authored centred there, so a
 * part drawn with no transform at all lands in the middle of the tool — which is why placing one is a
 * translate and spinning it is a rotate, with nothing to undo in between. And the translate comes
 * <em>first</em>: rotating first and translating second would swing the part around the tool instead
 * of turning it on its own axis, which is the difference between a drill and a fairground ride.
 */
public class PneumaticToolRenderer extends CustomRenderedItemModelRenderer {

	public enum Motion {
		/** Turns about the barrel: bits, impellers, sockets. */
		SPIN_AXIAL,
		/** Turns on a spindle across the barrel: blades, grinding wheels, buffing pads. */
		SPIN_FACE,
		/**
		 * Recoils and strikes along the tool. Travel is negative only — the part's rest position is
		 * its furthest forward — so a hammer can never punch its way out of the item's box.
		 */
		HAMMER,
	}

	/** How far a hammering part draws back, in model units. */
	private static final float RECOIL = 0.55F;

	private final Motion motion;
	private final float speed;
	private final Mount[] mounts;

	public PneumaticToolRenderer(Motion motion, float speed, Mount... mounts) {
		this.motion = motion;
		this.speed = speed;
		this.mounts = mounts;
	}

	@Override
	protected void render(ItemStack stack, CustomRenderedItemModel model,
		PartialItemModelRenderer renderer, ItemDisplayContext transformType, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {

		renderer.renderSolid(model.getOriginalModel(), light);

		float partialTicks = AnimationTickHolder.getPartialTicks();
		// Still in a slot, on the ground and in an item frame. A hotbar icon that never stops moving
		// is a distraction, and it is the one place the tool is not in anybody's hand.
		boolean live = inHand(transformType);
		float angle = live ? ToolAnimation.angle(speed, partialTicks) : 0.0F;
		float travel = live ? travel(partialTicks) : 0.0F;

		for (Mount mount : mounts) {
			ms.pushPose();
			// The tool points along +Y, so forward is Y, across is X and up is Z.
			ms.translate(mount.across() / 16.0F, (mount.forward() + travel) / 16.0F,
				mount.up() / 16.0F);
			switch (motion) {
				case SPIN_AXIAL -> ms.mulPose(Axis.YP.rotationDegrees(angle));
				case SPIN_FACE -> ms.mulPose(Axis.XP.rotationDegrees(angle));
				case HAMMER -> {
				}
			}
			renderer.renderSolid(mount.model()
				.get(), light);
			ms.popPose();
		}
	}

	private float travel(float partialTicks) {
		return switch (motion) {
			// A sine of the spin phase, damped by the throttle, so the hammer slows and stops with
			// everything else rather than beating on at full stroke while the tool idles.
			case HAMMER -> (Mth.sin(ToolAnimation.angle(speed, partialTicks) * Mth.DEG_TO_RAD) - 1.0F)
				* 0.5F * RECOIL * ToolAnimation.throttle(partialTicks);
			default -> 0.0F;
		};
	}

	private static boolean inHand(ItemDisplayContext transformType) {
		return transformType.firstPerson()
			|| transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
			|| transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
	}
}
