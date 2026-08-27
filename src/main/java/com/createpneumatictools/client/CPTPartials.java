package com.createpneumatictools.client;

import com.createpneumatictools.CreatePneumaticTools;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;

/**
 * The moving pieces of every tool, and where each one is mounted.
 *
 * <p>A {@link Mount} is read exactly the way {@code tools/generate_models.py} laid the geometry out.
 * The tool points along +Y with its grip hanging toward -Z, so mounting a part is a translation
 * {@code forward} along the barrel, {@code across} it sideways, and always up by {@link #BARREL_UP} —
 * the barrel sits above the middle of the model because the grip has to hang under it. Nothing here
 * rotates: the part is authored centred on the middle of the item, so spinning it is one call.
 *
 * <p><b>These numbers are duplicated in the generator, on purpose, and checked.</b>
 * {@code generate_models.py} lays the geometry out from its own copy and then greps this file for
 * every one of them, failing the build if they disagree. Move a mount in one place and the other
 * stops the build rather than shipping a drill bit hanging in front of a tool.
 */
public class CPTPartials {

	/**
	 * How far above the middle of the model the barrel's axis sits. Every part shares it, so it is not
	 * repeated on each mount — a part left at zero would spin inside the grip.
	 */
	public static final float BARREL_UP = 2.5F;

	public static final Mount HAND_DRILL_BIT = axial("hand_drill", "bit", 4.0F, 0.0F, BARREL_UP);

	public static final Mount JACKHAMMER_CHISEL =
		axial("pneumatic_jackhammer", "chisel", 4.0F, 0.0F, BARREL_UP);

	/**
	 * Three in a triangle, each turning on its own axis rather than orbiting the middle one.
	 *
	 * <p>A triangle and not a row because the icon is a side view: three bits abreast collapse into
	 * one from there, and the tool stops looking like three drills.
	 */
	public static final Mount[] TUNNEL_DRILL_BITS = {
		axial("tunnel_drill", "bit", 4.0F, -3.0F, BARREL_UP - 2.0F),
		axial("tunnel_drill", "bit", 4.0F, 3.0F, BARREL_UP - 2.0F),
		axial("tunnel_drill", "bit", 4.0F, 0.0F, BARREL_UP + 2.0F),
	};

	/**
	 * The saw's blade hangs below the barrel as well as out to the side, which is where a circular
	 * saw's blade is — and is what leaves room above the axis for the hood over it. A wheel on the
	 * centre line has a radius that reaches back through the body, the collar and the trigger, so half
	 * a turn of it is spent inside the tool it is bolted to.
	 *
	 * <p><b>{@code across} is negative on both wheeled tools, and that is what makes them watchable.</b>
	 * The first-person transform lays the tool pointing away and to the left, so model {@code +X} comes
	 * out of it toward the right of the screen <em>and away from the camera</em> — a wheel there sits
	 * behind the barrel, at the edge of the screen, where the one part of the tool that moves cannot be
	 * seen at all. Negative puts it on the near side and toward the middle. Flipping the sign back
	 * costs nothing that any check would notice and hides the animation completely.
	 */
	public static final Mount SAW_BLADE =
		axial("pneumatic_saw", "blade", 2.0F, -4.0F, BARREL_UP - 2.0F);

	/** An angle grinder's disc: small, thick, and right at the nose on a stub arbor. Left flank, as above. */
	public static final Mount GRINDER_WHEEL =
		axial("pneumatic_grinder", "wheel", 4.0F, -4.0F, BARREL_UP);

	/**
	 * The one wheel that is not out to the side. A polisher's pad faces the work, so it sits on the
	 * barrel's own axis past the end of the chuck and turns about it — which is also the only place it
	 * can go: any static box past the chuck face would stand inside the pad's swept circle.
	 */
	public static final Mount BUFFER_PAD =
		axial("pneumatic_buffer", "buffing_pad", 5.5F, 0.0F, BARREL_UP);

	/**
	 * The Borer's whole head is the moving part — a face plate carrying four bits round its rim,
	 * turning as one. That is what a boring head is, and it is the one thing that stops a 5x5 tool
	 * being the 3x3 one drawn wider.
	 */
	public static final Mount BORER_HEAD =
		axial("pneumatic_borer", "borer_head", 4.2F, 0.0F, BARREL_UP);

	public static final Mount VACUUM_IMPELLER =
		axial("pneumatic_vacuum_wand", "impeller", 7.0F, 0.0F, BARREL_UP);
	public static final Mount WRENCH_SOCKET =
		axial("pneumatic_wrench", "socket", 4.5F, 0.0F, BARREL_UP);

	private static Mount axial(String tool, String part, float forward, float across, float up) {
		return new Mount(PartialModel.of(CreatePneumaticTools.asResource("item/" + tool + "/" + part)),
			forward, across, up);
	}

	/**
	 * One moving piece and where it sits.
	 *
	 * @param forward along the barrel from the middle of the item, in model units (sixteenths)
	 * @param across  sideways from the barrel's centre line
	 * @param up      from the middle of the model; {@link #BARREL_UP} for everything on the axis
	 */
	public record Mount(PartialModel model, float forward, float across, float up) {}

	/** Every mount in the mod, for the startup check that they all baked. */
	public static java.util.List<Mount> all() {
		java.util.List<Mount> mounts = new java.util.ArrayList<>(java.util.List.of(HAND_DRILL_BIT,
			JACKHAMMER_CHISEL, SAW_BLADE, GRINDER_WHEEL, BUFFER_PAD, BORER_HEAD, VACUUM_IMPELLER,
			WRENCH_SOCKET));
		mounts.addAll(java.util.List.of(TUNNEL_DRILL_BITS));
		return mounts;
	}

	/**
	 * Touching this class is the registration — {@code PartialModel.of} adds itself to the set Flywheel
	 * asks the game to bake. This method exists so that the touch is deliberate at the call site rather
	 * than an accident of which class happened to load first.
	 */
	public static void init() {}
}
