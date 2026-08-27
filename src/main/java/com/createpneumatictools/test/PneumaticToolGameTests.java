package com.createpneumatictools.test;

import java.util.List;

import com.createpneumatictools.CPTConfig;
import com.createpneumatictools.CreatePneumaticTools;
import com.createpneumatictools.item.PneumaticDiggerItem;
import com.createpneumatictools.item.PneumaticToolItem;
import com.createpneumatictools.registry.CPTBlocks;
import com.createpneumatictools.registry.CPTItems;
import com.createpneumatictools.source.PneumaticSourceBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.crank.HandCrankBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.AbstractSimpleShaftBlock;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The real check on this mod. There is no unit-test suite: almost everything here is a question
 * about a block in a world or an item in a hand, and a mock of either is a mock of the thing under
 * test.
 *
 * <p>Every test builds its own arrangement into an empty box, because nine hand tools have no
 * standing rig to share. What they do share is {@link #worker}: a mock player wearing a full Copper
 * Backtank, standing in the middle of the plate. Air is the one thing every tool in this mod reads,
 * so nearly every test is really a question about how much of it went.
 *
 * <p>Note {@link GameType#SURVIVAL} everywhere. A creative player short-circuits
 * {@code BacktankUtil.canAbsorbDamage} before it has looked at a tank, so a test that hired a
 * creative mock would pass with the whole air system deleted.
 */
@GameTestHolder(CreatePneumaticTools.ID)
@PrefixGameTestTemplate(false)
public class PneumaticToolGameTests {

	/** Where the mock player stands, and where every test builds. The template is 13x12x13. */
	private static final BlockPos SITE = new BlockPos(6, 2, 6);

	/** Long enough for Create's rotation propagator to have reached the far end of a shaft. */
	private static final int SETTLE_TICKS = 12;

	/** Long enough after the renewals stop for an unrenewed source to have removed itself. */
	private static final int LEASE_LAPSE = PneumaticSourceBlockEntity.LEASE_TICKS + 5;

	// --- the air budget ---------------------------------------------------------------------------

	@GameTest(template = "workshop")
	public static void theHandDrillSpendsOneUsePerBlock(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.HAND_DRILL.get());
		BlockPos stone = SITE.above();
		helper.setBlock(stone, Blocks.STONE);

		int before = air(player);
		mine(helper, player, stone);
		int spent = before - air(player);

		int expected = costOf(CPTConfig.handDrillUsesPerTank());
		if (spent != expected)
			throw new GameTestAssertException(
				"drilling one block spent " + spent + " air, expected " + expected);
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void instantlyBreakingBlocksAreFree(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.HAND_DRILL.get());
		BlockPos torch = SITE.above();
		helper.setBlock(SITE, Blocks.STONE);
		helper.setBlock(torch, Blocks.TORCH);

		int before = air(player);
		mine(helper, player, torch);

		// A meadow of grass and a cave of torches would otherwise drain a tank on the way past.
		if (air(player) != before)
			throw new GameTestAssertException("breaking a torch cost " + (before - air(player)) + " air");
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void anEmptyTankLeavesTheDrillNoFasterThanBareHands(GameTestHelper helper) {
		// No backtank at all, which is the same case as an empty one: isPowered says no either way.
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setPos(Vec3.atCenterOf(helper.absolutePos(SITE)));
		player.setOnGround(true);
		player.setItemInHand(InteractionHand.MAIN_HAND,
			new ItemStack(CPTItems.HAND_DRILL.get()));

		PneumaticDiggerItem drill = CPTItems.HAND_DRILL.get();
		if (drill.isPowered(player))
			throw new GameTestAssertException("a player with no backtank reads as powered");

		// The item component itself must contribute nothing, or an empty tank would still be quick.
		float bare = player.getDestroySpeed(Blocks.STONE.defaultBlockState());
		if (bare > 1.01F)
			throw new GameTestAssertException(
				"an unpowered Hand Drill digs stone at " + bare + ", expected about 1");
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void aChargedTankMakesTheDrillQuick(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.HAND_DRILL.get());
		float speed = player.getDestroySpeed(Blocks.STONE.defaultBlockState());
		float wanted = CPTConfig.handDrillSpeed();

		// getDestroySpeed runs the whole vanilla chain and posts the break-speed event at the end of
		// it, so this asserts the handler is actually reached rather than that a method returns.
		if (Math.abs(speed - wanted) > 0.01F)
			throw new GameTestAssertException(
				"a powered Hand Drill digs stone at " + speed + ", expected " + wanted);
		helper.succeed();
	}

	// --- the jackhammer ---------------------------------------------------------------------------

	@GameTest(template = "workshop")
	public static void theJackhammerBreaksEveryHardBlockInTheSameTime(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.JACKHAMMER.get());
		int wanted = CPTConfig.jackhammerBreakTicks();

		// Obsidian is 50 hardness and Deepslate is 3 -- seventeen times apart, and the point of the
		// tool is that they are not. A speed-multiplier config could not express that.
		for (Block block : List.of(Blocks.OBSIDIAN, Blocks.DEEPSLATE)) {
			BlockState state = block.defaultBlockState();
			float speed = player.getDestroySpeed(state);
			float ticks = state.getDestroySpeed(helper.getLevel(), helper.absolutePos(SITE)) * 30.0F
				/ speed;
			if (Math.abs(ticks - wanted) > 0.5F)
				throw new GameTestAssertException(
					block.getName()
						.getString() + " takes " + ticks + " ticks, expected " + wanted);
		}
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void theJackhammerIgnoresSoftBlocks(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.JACKHAMMER.get());
		BlockPos stone = SITE.above();
		helper.setBlock(stone, Blocks.STONE);

		float speed = player.getDestroySpeed(Blocks.STONE.defaultBlockState());
		if (speed > 1.01F)
			throw new GameTestAssertException(
				"the jackhammer digs stone at " + speed + ", expected about 1");

		int before = air(player);
		mine(helper, player, stone);
		// No boost, so no charge: the air is what does the shattering.
		if (air(player) != before)
			throw new GameTestAssertException("breaking stone cost the jackhammer air");
		helper.succeed();
	}

	// --- enchanting ------------------------------------------------------------------------------

	@GameTest(template = "workshop")
	public static void aSilkTouchedDrillDropsWhatItBreaks(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.HAND_DRILL.get());
		Holder<Enchantment> silkTouch = helper.getLevel()
			.registryAccess()
			.registryOrThrow(Registries.ENCHANTMENT)
			.getHolderOrThrow(Enchantments.SILK_TOUCH);
		ItemStack drill = player.getMainHandItem();
		// Two separate things, and the first is the one a data file can silently break: an item is a
		// legal target for Fortune and Silk Touch only through #minecraft:enchantable/mining_loot.
		if (!silkTouch.value()
			.canEnchant(drill))
			throw new GameTestAssertException("Silk Touch will not go on the Hand Drill -- it is not in "
				+ "#minecraft:enchantable/mining_loot");
		drill.enchant(silkTouch, 1);

		BlockPos stone = SITE.above();
		helper.setBlock(stone, Blocks.STONE);
		mine(helper, player, stone);

		// Cobblestone here would mean the enchantment reached the item but not the drop, which is what
		// a break path that forgot to pass the tool along looks like.
		helper.assertItemEntityPresent(Items.STONE, stone, 2.0);
		helper.succeed();
	}

	// --- the tunnelling drill ---------------------------------------------------------------------

	@GameTest(template = "workshop")
	public static void theTunnellingDrillClearsTheWallItFaces(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.TUNNEL_DRILL.get());
		BlockPos centre = SITE.above(2)
			.north(2);
		buildWall(helper, centre, 2);
		lookAt(player, Direction.NORTH);

		mine(helper, player, centre);

		for (int x = -1; x <= 1; x++)
			for (int y = -1; y <= 1; y++)
				helper.assertBlockNotPresent(Blocks.STONE, centre.offset(x, y, 0));
		// The ring one further out has to survive, or the burst is not a 3x3.
		helper.assertBlockPresent(Blocks.STONE, centre.offset(2, 0, 0));
		helper.assertBlockPresent(Blocks.STONE, centre.offset(0, 2, 0));
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void theTunnellingDrillDoesNotTunnelForever(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.TUNNEL_DRILL.get());
		BlockPos centre = SITE.above(2)
			.north(2);
		buildWall(helper, centre, 2);
		lookAt(player, Direction.NORTH);

		int before = air(player);
		mine(helper, player, centre);

		// Create's break helper tells the tool about every block it takes down, so without the
		// re-entrancy guard each of the eight neighbours fires its own burst -- which both clears the
		// 5x5 and charges for it. One burst, one charge; and the wall's outer ring is the witness.
		int spent = before - air(player);
		int expected = costOf(CPTConfig.tunnelDrillUsesPerTank());
		if (spent != expected)
			throw new GameTestAssertException(
				"one burst spent " + spent + " air, expected " + expected + " -- the cascade guard "
					+ "is not holding");
		helper.assertBlockPresent(Blocks.STONE, centre.offset(2, 2, 0));
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void theTunnellingDrillFollowsWhereYouLook(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.TUNNEL_DRILL.get());
		BlockPos centre = SITE.above(2)
			.north(2);
		buildWall(helper, centre, 2);
		// Looking straight down takes a slice of floor, not of wall.
		lookAt(player, Direction.DOWN);

		mine(helper, player, centre);

		helper.assertBlockNotPresent(Blocks.STONE, centre.east());
		helper.assertBlockNotPresent(Blocks.STONE, centre.south());
		// The vertical neighbours are in the plane the player is looking *along*, so they stay.
		helper.assertBlockPresent(Blocks.STONE, centre.above());
		helper.assertBlockPresent(Blocks.STONE, centre.below());
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void theTunnellingDrillFollowsTheFaceYouDrilled(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.TUNNEL_DRILL.get());
		// Two courses of floor three blocks ahead. Two, because the discriminating question is what
		// the burst did to the layer underneath.
		BlockPos centre = SITE.below()
			.north(3);
		for (int x = -2; x <= 2; x++)
			for (int z = -2; z <= 2; z++) {
				helper.setBlock(centre.offset(x, 0, z), Blocks.STONE);
				helper.setBlock(centre.offset(x, -1, z), Blocks.STONE);
			}
		// Down the slope onto the top face. At this range the look vector is more horizontal than
		// vertical, so the nearest axis of the *look* is Z while the face is UP -- which is the whole
		// case this test exists for, and the everyday one: cutting a trench along the ground.
		aimAt(helper, player, centre, Direction.UP);

		mine(helper, player, centre);

		// North and south only come out for a slice lying flat in the floor ...
		helper.assertBlockNotPresent(Blocks.STONE, centre.north());
		helper.assertBlockNotPresent(Blocks.STONE, centre.south());
		// ... and the course underneath only comes out for one standing on end, which is what taking
		// the nearest look axis would have built here.
		helper.assertBlockPresent(Blocks.STONE, centre.below());
		helper.succeed();
	}

	// --- the borer ---------------------------------------------------------------------------------

	@GameTest(template = "workshop")
	public static void theBorerClearsFiveByFive(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.BORER.get());
		BlockPos centre = SITE.above(2)
			.north(2);
		buildWall(helper, centre, 3);
		lookAt(player, Direction.NORTH);

		mine(helper, player, centre);

		for (int x = -2; x <= 2; x++)
			for (int y = -2; y <= 2; y++)
				helper.assertBlockNotPresent(Blocks.STONE, centre.offset(x, y, 0));
		// The ring one further out has to survive, or the burst is not a 5x5 -- and this is the
		// assertion that would still pass if the Borer were quietly running the Tunnelling Drill's
		// radius, so the 5x5 sweep above is the one that separates them.
		helper.assertBlockPresent(Blocks.STONE, centre.offset(3, 0, 0));
		helper.assertBlockPresent(Blocks.STONE, centre.offset(0, 3, 0));
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void theBorerChargesOncePerBurst(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.BORER.get());
		BlockPos centre = SITE.above(2)
			.north(2);
		buildWall(helper, centre, 3);
		lookAt(player, Direction.NORTH);

		int before = air(player);
		mine(helper, player, centre);

		// Twenty-four neighbours, each of which Create's break helper reports back to the tool. The
		// cascade guard is what makes that one charge instead of twenty-five, and it is worth a test
		// of its own at this width: a 5x5 that recursed would clear the whole box in one click.
		int spent = before - air(player);
		int expected = costOf(CPTConfig.borerUsesPerTank());
		if (spent != expected)
			throw new GameTestAssertException(
				"one burst spent " + spent + " air, expected " + expected + " -- the cascade guard "
					+ "is not holding");
		helper.assertBlockPresent(Blocks.STONE, centre.offset(3, 3, 0));
		helper.succeed();
	}

	// --- the saw ----------------------------------------------------------------------------------

	@GameTest(template = "workshop")
	public static void theSawFellsTheWholeTree(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.SAW.get());
		BlockPos root = SITE.above();
		for (int i = 0; i < 5; i++)
			helper.setBlock(root.above(i), Blocks.OAK_LOG);

		mine(helper, player, root);

		for (int i = 1; i < 5; i++)
			helper.assertBlockNotPresent(Blocks.OAK_LOG, root.above(i));
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void theSawChargesOncePerTreeRatherThanPerLog(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.SAW.get());
		BlockPos root = SITE.above();
		for (int i = 0; i < 5; i++)
			helper.setBlock(root.above(i), Blocks.OAK_LOG);

		int before = air(player);
		mine(helper, player, root);

		int spent = before - air(player);
		int expected = costOf(CPTConfig.sawUsesPerTank());
		if (spent != expected)
			throw new GameTestAssertException("felling a five-log tree spent " + spent + " air, "
				+ "expected " + expected + " -- every log is billing the tank");
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void theSawLeavesPlanksAlone(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.SAW.get());
		BlockPos plank = SITE.above();
		helper.setBlock(plank, Blocks.OAK_PLANKS);
		helper.setBlock(plank.above(), Blocks.OAK_PLANKS);

		int before = air(player);
		mine(helper, player, plank);

		if (air(player) != before)
			throw new GameTestAssertException("breaking a plank cost the saw air");
		helper.assertBlockPresent(Blocks.OAK_PLANKS, plank.above());
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void theTunnellingDrillStopsAtTheWorldBorder(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.TUNNEL_DRILL.get());
		BlockPos centre = SITE.above();
		buildWall(helper, centre, 1);
		lookAt(player, Direction.NORTH);

		// Vanilla checks the border in the packet handler, against the position the packet named --
		// so it covers the block a player swings at and nothing else. The eight the burst takes with
		// it were never described to anybody. A border one block wide, centred on the block being
		// drilled, puts the two neighbours directly above and below it inside and the six either side
		// of it out; that asymmetry is the point, because a blanket refusal would pass this too.
		WorldBorder border = helper.getLevel()
			.getWorldBorder();
		double wasX = border.getCenterX();
		double wasZ = border.getCenterZ();
		double wasSize = border.getSize();
		Vec3 at = Vec3.atCenterOf(helper.absolutePos(centre));
		try {
			border.setCenter(at.x, at.z);
			border.setSize(1.0);
			mine(helper, player, centre);
		} finally {
			border.setCenter(wasX, wasZ);
			border.setSize(wasSize);
		}

		for (int y = -1; y <= 1; y++) {
			if (y != 0)
				helper.assertBlockNotPresent(Blocks.STONE, centre.offset(0, y, 0));
			for (int x : new int[] {-1, 1})
				helper.assertBlockPresent(Blocks.STONE, centre.offset(x, y, 0));
		}
		helper.succeed();
	}

	// --- the surface pair -------------------------------------------------------------------------

	@GameTest(template = "workshop")
	public static void theGrinderStripsScrapesAndUnwaxes(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.GRINDER.get());
		BlockPos at = SITE.above();

		expectTreatment(helper, player, at, Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG);
		expectTreatment(helper, player, at, Blocks.EXPOSED_COPPER, Blocks.COPPER_BLOCK);
		expectTreatment(helper, player, at, Blocks.WAXED_COPPER_BLOCK, Blocks.COPPER_BLOCK);
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void theBufferWaxesCopperWithoutHoneycomb(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.BUFFER.get());
		BlockPos at = SITE.above();
		expectTreatment(helper, player, at, Blocks.COPPER_BLOCK, Blocks.WAXED_COPPER_BLOCK);
		helper.succeed();
	}

	@GameTest(template = "workshop", timeoutTicks = 200)
	public static void theWandPullsItemsAndExperienceIn(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.VACUUM_WAND.get());
		Vec3 away = Vec3.atCenterOf(helper.absolutePos(SITE.above()
			.east(5)));
		ItemEntity dropped = new ItemEntity(helper.getLevel(), away.x, away.y, away.z,
			new ItemStack(Items.DIAMOND));
		dropped.setNoPickUpDelay();
		// That constructor gives the stack the little upward hop a dropped item has. Left in, it is
		// motion this test would read as the wand's doing.
		dropped.setDeltaMovement(Vec3.ZERO);
		helper.getLevel()
			.addFreshEntity(dropped);
		ExperienceOrb orb = new ExperienceOrb(helper.getLevel(), away.x, away.y, away.z, 3);
		orb.setDeltaMovement(Vec3.ZERO);
		helper.getLevel()
			.addFreshEntity(orb);

		double startedAt = dropped.position()
			.distanceTo(player.position());
		// The wand ticks off the use clock, so drive it the way vanilla would rather than calling the
		// pull directly -- that is the half of it that could silently stop being reached.
		player.startUsingItem(InteractionHand.MAIN_HAND);
		for (int tick = 0; tick < 10; tick++)
			CPTItems.VACUUM_WAND.get()
				.onUseTick(helper.getLevel(), player, player.getMainHandItem(),
					player.getUseItemRemainingTicks() - tick);

		if (dropped.getDeltaMovement()
			.lengthSqr() < 0.01)
			throw new GameTestAssertException("the dropped item was not pulled");
		if (orb.getDeltaMovement()
			.lengthSqr() < 0.01)
			throw new GameTestAssertException("the experience orb was not pulled");
		// Pulled *towards*, not merely shoved.
		Vec3 next = dropped.position()
			.add(dropped.getDeltaMovement());
		if (next.distanceTo(player.position()) >= startedAt)
			throw new GameTestAssertException("the item was pushed away rather than drawn in");
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void theWandChargesNothingWithNothingInRange(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.VACUUM_WAND.get());
		int before = air(player);

		player.startUsingItem(InteractionHand.MAIN_HAND);
		for (int tick = 0; tick < 20; tick++)
			CPTItems.VACUUM_WAND.get()
				.onUseTick(helper.getLevel(), player, player.getMainHandItem(),
					player.getUseItemRemainingTicks() - tick);

		if (air(player) != before)
			throw new GameTestAssertException(
				"twenty ticks of empty suction spent " + (before - air(player)) + " air");
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void theWandLeavesFreshlyDroppedItemsAlone(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.VACUUM_WAND.get());
		Vec3 away = Vec3.atCenterOf(helper.absolutePos(SITE.above()
			.east(3)));
		// setDefaultPickUpDelay is what Player.drop does to a thrown stack; a bare ItemEntity has none,
		// so without this the test would be asserting against the wrong entity entirely. Pulling one
		// of these back would undo the player's own Q press while they hold the button down.
		ItemEntity dropped = new ItemEntity(helper.getLevel(), away.x, away.y, away.z,
			new ItemStack(Items.DIAMOND));
		dropped.setDefaultPickUpDelay();
		dropped.setDeltaMovement(Vec3.ZERO);
		helper.getLevel()
			.addFreshEntity(dropped);

		player.startUsingItem(InteractionHand.MAIN_HAND);
		for (int tick = 0; tick < 8; tick++)
			CPTItems.VACUUM_WAND.get()
				.onUseTick(helper.getLevel(), player, player.getMainHandItem(),
					player.getUseItemRemainingTicks() - tick);

		if (dropped.getDeltaMovement()
			.lengthSqr() > 0.01)
			throw new GameTestAssertException("an item still on its pickup delay was pulled in");
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void theWandCanBeToldToLeaveOtherPeoplesDropsAlone(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.VACUUM_WAND.get());
		Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 away = Vec3.atCenterOf(helper.absolutePos(SITE.above()
			.east(3)));

		// A thrower is set on a stack somebody chose to part with -- pressing Q, or dying. Block and
		// mob drops have none, and are still fair game with the knob on, or a vacuum would have
		// nothing left to pick up.
		ItemEntity theirs = new ItemEntity(helper.getLevel(), away.x, away.y, away.z,
			new ItemStack(Items.DIAMOND));
		theirs.setNoPickUpDelay();
		theirs.setDeltaMovement(Vec3.ZERO);
		theirs.setThrower(stranger);
		ItemEntity nobodys = new ItemEntity(helper.getLevel(), away.x, away.y, away.z,
			new ItemStack(Items.IRON_INGOT));
		nobodys.setNoPickUpDelay();
		nobodys.setDeltaMovement(Vec3.ZERO);
		helper.getLevel()
			.addFreshEntity(theirs);
		helper.getLevel()
			.addFreshEntity(nobodys);

		CPTConfig.INSTANCE.vacuumOnlyOwnDrops.set(true);
		try {
			player.startUsingItem(InteractionHand.MAIN_HAND);
			for (int tick = 0; tick < 10; tick++)
				CPTItems.VACUUM_WAND.get()
					.onUseTick(helper.getLevel(), player, player.getMainHandItem(),
						player.getUseItemRemainingTicks() - tick);
		} finally {
			CPTConfig.INSTANCE.vacuumOnlyOwnDrops.set(false);
		}

		if (theirs.getDeltaMovement()
			.lengthSqr() > 0.01)
			throw new GameTestAssertException("the wand took a stack another player had dropped");
		if (nobodys.getDeltaMovement()
			.lengthSqr() < 0.01)
			throw new GameTestAssertException(
				"the wand also stopped taking ownerless drops, which is everything it is for");
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void theWrenchPutsASourceAgainstTheFaceYouAimAt(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.WRENCH.get());
		BlockPos shaft = SITE.above();
		helper.setBlock(shaft, AllBlocks.SHAFT.getDefaultState()
			.setValue(AbstractSimpleShaftBlock.AXIS, Direction.Axis.Y));

		int before = air(player);
		useOn(helper, player, shaft);

		// Against the clicked face -- the top -- with its shaft pointing back down into the block, the
		// same place and orientation a player would have put a motor by hand.
		BlockPos spot = shaft.above();
		helper.assertBlockPresent(CPTBlocks.PNEUMATIC_SOURCE.get(), spot);
		BlockState placed = helper.getBlockState(spot);
		if (placed.getValue(DirectionalKineticBlock.FACING) != Direction.DOWN)
			throw new GameTestAssertException("the source faces " + placed.getValue(
				DirectionalKineticBlock.FACING) + ", so its shaft does not meet the block it was put on");

		// The click places; the hold pays. Driving the use tick is the only way to see the charge,
		// and it is also the half of the wrench that a test calling useOn alone would never reach.
		drive(helper, player, 1);
		if (air(player) != before - costOf(CPTConfig.wrenchUsesPerTank()))
			throw new GameTestAssertException("a tick of driving did not charge the tank");
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void theWrenchDoesNotBuildWhereYouMayNot(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.WRENCH.get());
		// Adventure mode, vanilla spawn protection, and any protection mod leaning on vanilla's own
		// check all arrive at the wrench as mayBuild == false.
		player.getAbilities().mayBuild = false;
		BlockPos shaft = SITE.above();
		helper.setBlock(shaft, AllBlocks.SHAFT.getDefaultState()
			.setValue(AbstractSimpleShaftBlock.AXIS, Direction.Axis.Y));

		int before = air(player);
		InteractionResult result = useOn(helper, player, shaft);

		helper.assertBlockNotPresent(CPTBlocks.PNEUMATIC_SOURCE.get(), shaft.above());
		// PASS, not FAIL: a wrench aimed somewhere it may not build should behave like an item with
		// nothing to do rather than eat the click.
		if (result != InteractionResult.PASS)
			throw new GameTestAssertException("expected PASS so the click falls through, got " + result);
		if (air(player) != before)
			throw new GameTestAssertException("a refused placement still cost air");
		helper.succeed();
	}

	@GameTest(template = "workshop", timeoutTicks = 200)
	public static void theSourceTurnsTheShaftItIsPutAgainst(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.WRENCH.get());
		BlockPos shaft = SITE.above();
		helper.setBlock(shaft, AllBlocks.SHAFT.getDefaultState()
			.setValue(AbstractSimpleShaftBlock.AXIS, Direction.Axis.Y));
		useOn(helper, player, shaft);
		// The lease is shorter than the settle delay, so without holding the button the source is gone
		// before the shaft has finished spinning up -- which is the tool working, not failing.
		holdOpen(helper, shaft.above(), SETTLE_TICKS);

		// Rotation propagates over a few ticks, so this cannot be asserted in the same tick.
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			if (!(helper.getBlockEntity(shaft) instanceof KineticBlockEntity kinetic))
				throw new GameTestAssertException("no shaft to read");
			float speed = Math.abs(kinetic.getSpeed());
			if (speed < 0.01F)
				throw new GameTestAssertException(
					"the shaft is not turning -- the source is in the world but not in the network");
			if (Math.abs(speed - CPTConfig.wrenchRpm()) > 0.01F)
				throw new GameTestAssertException(
					"the shaft turns at " + speed + " RPM, expected " + CPTConfig.wrenchRpm());
			helper.succeed();
		});
	}

	@GameTest(template = "workshop", timeoutTicks = 200)
	public static void theWrenchOutMusclesAHandCrank(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.WRENCH.get());
		BlockPos shaft = SITE.above();
		helper.setBlock(shaft, AllBlocks.SHAFT.getDefaultState()
			.setValue(AbstractSimpleShaftBlock.AXIS, Direction.Axis.Y));
		useOn(helper, player, shaft);
		holdOpen(helper, shaft.above(), SETTLE_TICKS);

		helper.runAfterDelay(SETTLE_TICKS, () -> {
			if (!(helper.getBlockEntity(shaft) instanceof KineticBlockEntity kinetic))
				throw new GameTestAssertException("no shaft to read");
			// Read off the network rather than out of the config: this is the number the Stressometer
			// shows, and it is nonzero only if the source actually joined the network and registered
			// its capacity there.
			float supplied = kinetic.getOrCreateNetwork()
				.calculateCapacity();
			HandCrankBlock crank = AllBlocks.HAND_CRANK.get();
			double byHand = BlockStressValues.getCapacity(crank) * crank.getRotationSpeed();
			if (supplied <= byHand)
				throw new GameTestAssertException("the wrench supplies " + supplied
					+ " Stress Units and a Hand Crank supplies " + byHand
					+ " -- an air tool that is no stronger than a handle you turn by hand is not worth "
					+ "the backtank");
			// And the figure is exactly the configured one, which is the same invariant
			// aMatchedWrenchIsWorth* asserts -- here on a shaft the wrench is driving itself rather
			// than one it joined, because that is the other path through calculateAddedStressCapacity.
			float expected = (float) CPTConfig.wrenchStressUnits();
			if (Math.abs(supplied - expected) > expected / 1000.0F)
				throw new GameTestAssertException("the wrench supplies " + supplied
					+ " Stress Units driving a shaft of its own, expected " + expected);
			helper.succeed();
		});
	}

	@GameTest(template = "workshop", timeoutTicks = 200)
	public static void theWrenchFallsInBehindATurningNetwork(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.WRENCH.get());
		// A Creative Motor pointing up into a shaft, so the shaft turns at a speed and a sign this
		// test knows: +16, where the wrench left to itself would produce -64. Both halves of that
		// disagreement matter -- the magnitude proves the wrench did not overspeed the network, and
		// the sign is the half that used to have Create delete the source on the tick it appeared.
		BlockPos motor = SITE.above();
		BlockPos shaft = motor.above();
		helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.setBlock(shaft, AllBlocks.SHAFT.getDefaultState()
			.setValue(AbstractSimpleShaftBlock.AXIS, Direction.Axis.Y));

		float driven = drivenSpeed(helper, motor);
		useOn(helper, player, shaft);
		holdOpen(helper, shaft.above(), SETTLE_TICKS);

		helper.runAfterDelay(SETTLE_TICKS, () -> {
			// The source has to still be there. Create's answer to a generator that disagrees with its
			// network is to destroy it, so a missing block here is the whole bug rather than a detail.
			helper.assertBlockPresent(CPTBlocks.PNEUMATIC_SOURCE.get(), shaft.above());
			if (!(helper.getBlockEntity(shaft) instanceof KineticBlockEntity kinetic))
				throw new GameTestAssertException("no shaft to read");
			float speed = kinetic.getTheoreticalSpeed();
			if (Math.abs(speed - driven) > 0.01F)
				throw new GameTestAssertException("the shaft was turning at " + driven
					+ " RPM and the wrench left it at " + speed
					+ " -- a wrench joining a network should add torque, not argue about speed");
			helper.succeed();
		});
	}

	@GameTest(template = "workshop", timeoutTicks = 200)
	public static void aMatchedWrenchIsWorthItsFullTorqueOnASlowNetwork(GameTestHelper helper) {
		expectMatchedContribution(helper, 16);
	}

	@GameTest(template = "workshop", timeoutTicks = 200)
	public static void aMatchedWrenchIsWorthNoMoreOnAFastOne(GameTestHelper helper) {
		// The pair is the point. Create bills a generator as per-RPM times speed, so a fixed per-RPM
		// figure made the wrench worth three times as much for joining a 192 RPM network as for
		// driving its own at 64 -- the same air, the same tool, paid by how fast somebody else's
		// shaft happened to be turning. These two tests differ only in that number and must agree.
		expectMatchedContribution(helper, 192);
	}

	/**
	 * Drives a shaft with a Creative Motor at {@code motorRpm}, matches a wrench onto it, and asserts
	 * the wrench added exactly the configured Stress Units — no more and no less, whatever the speed.
	 *
	 * <p>Measured twice on the same network rather than against a figure computed here: once while the
	 * wrench is on it, and again after the lease has lapsed and the source has gone. That is what makes
	 * it a test of the capacity reaching Create rather than of this mod's arithmetic agreeing with
	 * itself.
	 */
	private static void expectMatchedContribution(GameTestHelper helper, int motorRpm) {
		Player player = worker(helper, CPTItems.WRENCH.get());
		BlockPos motor = SITE.above();
		BlockPos shaft = motor.above();
		helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState()
			.setValue(DirectionalKineticBlock.FACING, Direction.UP));
		helper.setBlock(shaft, AllBlocks.SHAFT.getDefaultState()
			.setValue(AbstractSimpleShaftBlock.AXIS, Direction.Axis.Y));
		if (!(helper.getBlockEntity(motor) instanceof CreativeMotorBlockEntity driver))
			throw new GameTestAssertException("no motor to set the speed of");
		driver.generatedSpeed.setValue(motorRpm);

		useOn(helper, player, shaft);
		holdOpen(helper, shaft.above(), SETTLE_TICKS);

		float[] withWrench = new float[1];
		helper.runAfterDelay(SETTLE_TICKS, () -> withWrench[0] = capacityAt(helper, shaft));
		helper.runAfterDelay(SETTLE_TICKS + LEASE_LAPSE, () -> {
			helper.assertBlockNotPresent(CPTBlocks.PNEUMATIC_SOURCE.get(), shaft.above());
			float alone = capacityAt(helper, shaft);
			float added = withWrench[0] - alone;
			float expected = (float) CPTConfig.wrenchStressUnits();
			// One part in a thousand: the figure has been through a float divide by the speed and a
			// float multiply back by it, and at 192 RPM that does not land on the same bit pattern.
			if (Math.abs(added - expected) > expected / 1000.0F)
				throw new GameTestAssertException("at " + motorRpm + " RPM the network supplies "
					+ alone + " Stress Units on its own and " + withWrench[0]
					+ " with a matched wrench on it, so the wrench added " + added + " where "
					+ expected + " was expected -- a wrench is worth the same at every speed");
			helper.succeed();
		});
	}

	/** The speed a generator is producing, once it has had a moment to reach its own network. */
	private static float drivenSpeed(GameTestHelper helper, BlockPos generator) {
		if (!(helper.getBlockEntity(generator) instanceof KineticBlockEntity kinetic))
			throw new GameTestAssertException("no generator at " + generator);
		return kinetic.getGeneratedSpeed();
	}

	private static float capacityAt(GameTestHelper helper, BlockPos member) {
		if (!(helper.getBlockEntity(member) instanceof KineticBlockEntity kinetic))
			throw new GameTestAssertException("no kinetic block at " + member);
		return kinetic.getOrCreateNetwork()
			.calculateCapacity();
	}

	@GameTest(template = "workshop", timeoutTicks = 200)
	public static void anUnrenewedSourceRemovesItself(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.WRENCH.get());
		BlockPos shaft = SITE.above();
		helper.setBlock(shaft, AllBlocks.SHAFT.getDefaultState()
			.setValue(AbstractSimpleShaftBlock.AXIS, Direction.Axis.Y));
		useOn(helper, player, shaft);
		helper.assertBlockPresent(CPTBlocks.PNEUMATIC_SOURCE.get(), shaft.above());

		// Nothing renews it, which stands in for every way a player can stop driving one: letting go,
		// walking off, dying, logging out, the chunk reloading, the game crashing. The block is the
		// only thing that has to be right, and it is right by counting down.
		helper.runAfterDelay(PneumaticSourceBlockEntity.LEASE_TICKS + 5, () -> {
			helper.assertBlockNotPresent(CPTBlocks.PNEUMATIC_SOURCE.get(), shaft.above());
			helper.succeed();
		});
	}

	@GameTest(template = "workshop", timeoutTicks = 200)
	public static void aRenewedSourceStays(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.WRENCH.get());
		BlockPos shaft = SITE.above();
		helper.setBlock(shaft, AllBlocks.SHAFT.getDefaultState()
			.setValue(AbstractSimpleShaftBlock.AXIS, Direction.Axis.Y));
		useOn(helper, player, shaft);
		BlockPos spot = shaft.above();

		// Without this the previous test would pass with the lease broken shut instead of broken open.
		holdOpen(helper, spot, PneumaticSourceBlockEntity.LEASE_TICKS + 5);
		helper.runAfterDelay(PneumaticSourceBlockEntity.LEASE_TICKS + 5, () -> {
			helper.assertBlockPresent(CPTBlocks.PNEUMATIC_SOURCE.get(), spot);
			helper.succeed();
		});
	}

	@GameTest(template = "workshop")
	public static void theWrenchNeedsSomethingToPushAgainst(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.WRENCH.get());
		BlockPos shaft = SITE.above();
		helper.setBlock(shaft, AllBlocks.SHAFT.getDefaultState()
			.setValue(AbstractSimpleShaftBlock.AXIS, Direction.Axis.Y));
		// The space the source would go in is taken, so there is nowhere to mount it.
		helper.setBlock(shaft.above(), Blocks.STONE);

		int before = air(player);
		useOn(helper, player, shaft);

		helper.assertBlockPresent(Blocks.STONE, shaft.above());
		if (air(player) != before)
			throw new GameTestAssertException("the wrench charged for a source it could not place");
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void anEmptyTankDrivesNothing(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.WRENCH.get());
		setAir(player, 0);
		BlockPos shaft = SITE.above();
		helper.setBlock(shaft, AllBlocks.SHAFT.getDefaultState()
			.setValue(AbstractSimpleShaftBlock.AXIS, Direction.Axis.Y));

		useOn(helper, player, shaft);

		helper.assertBlockNotPresent(CPTBlocks.PNEUMATIC_SOURCE.get(), shaft.above());
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void aSourceAnswersOnlyToTheWrenchThatPlacedIt(GameTestHelper helper) {
		Player mine = worker(helper, CPTItems.WRENCH.get());
		BlockPos shaft = SITE.above();
		helper.setBlock(shaft, AllBlocks.SHAFT.getDefaultState()
			.setValue(AbstractSimpleShaftBlock.AXIS, Direction.Axis.Y));
		useOn(helper, mine, shaft);
		BlockPos spot = shaft.above();
		helper.assertBlockPresent(CPTBlocks.PNEUMATIC_SOURCE.get(), spot);

		// A second player standing in the same place, which is where two people wrenching in one
		// workshop actually stand. The search used to answer with the nearest source whoever put it
		// there, so this player's renewals kept somebody else's block alive while their own lease ran
		// out -- and letting go took down a generator that was still in use.
		Player theirs = worker(helper, CPTItems.WRENCH.get());
		int before = air(theirs);
		drive(helper, theirs, 3);
		CPTItems.WRENCH.get()
			.releaseUsing(theirs.getMainHandItem(), helper.getLevel(), theirs, 0);

		helper.assertBlockPresent(CPTBlocks.PNEUMATIC_SOURCE.get(), spot);
		if (air(theirs) != before)
			throw new GameTestAssertException("the second wrench paid " + (before - air(theirs))
				+ " air to drive a source it does not own");
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void aRefusalSaysSoOnceRatherThanEveryTick(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.GRINDER.get());
		setAir(player, 0);
		BlockPos log = SITE.above();
		helper.setBlock(log, Blocks.OAK_LOG);

		useOn(helper, player, log);

		// Vanilla re-fires a use that returned FAIL every four ticks, and the deny is played to
		// everybody in earshot rather than to the one person it is for -- so an empty tank held
		// against a block was ten sound packets a second to the whole room.
		if (!player.getCooldowns()
			.isOnCooldown(CPTItems.GRINDER.get()))
			throw new GameTestAssertException(
				"a refusal left nothing behind to keep the next four ticks quiet");
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void anEmptyTankGrindsNothing(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.GRINDER.get());
		setAir(player, 0);
		BlockPos log = SITE.above();
		helper.setBlock(log, Blocks.OAK_LOG);

		useOn(helper, player, log);

		helper.assertBlockPresent(Blocks.OAK_LOG, log);
		helper.succeed();
	}

	// --- the rig ----------------------------------------------------------------------------------

	/**
	 * A survival player standing on the plate with the named tool in hand and a full Copper Backtank
	 * on their back.
	 *
	 * <p>The tank goes in the chest slot because that is where {@code BacktankUtil}'s own supplier
	 * looks -- it walks the armour slots for anything tagged {@code create:pressurized_air_sources}.
	 * Put it in the inventory instead and every one of these tests reads as an empty tank.
	 */
	private static Player worker(GameTestHelper helper, PneumaticToolItem tool) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setPos(Vec3.atCenterOf(helper.absolutePos(SITE)));
		// A mock player is never ticked, so it believes it is falling -- and vanilla divides the dig
		// speed of anyone in the air by five. Every speed assertion below would be off by that factor
		// and read as a broken multiplier.
		player.setOnGround(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));

		ItemStack backtank = new ItemStack(AllItems.COPPER_BACKTANK.get());
		backtank.set(AllDataComponents.BACKTANK_AIR, BacktankUtil.maxAir(backtank));
		player.setItemSlot(EquipmentSlot.CHEST, backtank);
		return player;
	}

	private static int air(Player player) {
		return BacktankUtil.getAir(player.getItemBySlot(EquipmentSlot.CHEST));
	}

	private static void setAir(Player player, int air) {
		player.getItemBySlot(EquipmentSlot.CHEST)
			.set(AllDataComponents.BACKTANK_AIR, air);
	}

	/** What one use of a tool at this rating costs, worked out the way Create works it out. */
	private static int costOf(int usesPerTank) {
		return Math.max(BacktankUtil.maxAirWithoutEnchants() / usesPerTank, 1);
	}

	/**
	 * Breaks a block the way a survival player does, in {@code ServerPlayerGameMode.destroyBlock}'s
	 * order and with its arguments.
	 *
	 * <p>{@code mineBlock} before the block is removed is vanilla's order, not a convenience: the saw
	 * hands the still-standing block's position to Create's tree search, so a test that removed it
	 * first would be testing a different code path from the game.
	 *
	 * <p>The reason this is not simply {@code Level.destroyBlock(pos, true, player)}, which is what it
	 * used to be, is that that overload drops with {@code ItemStack.EMPTY} as the tool. Nothing here
	 * noticed until a Silk Touch drill was asked to drop Stone and dropped Cobblestone: with an empty
	 * tool the loot table never sees the enchantments, the tier, or anything else about what broke the
	 * block. {@code playerDestroy} takes the pre-mine copy of the stack, as vanilla does.
	 */
	private static void mine(GameTestHelper helper, Player player, BlockPos pos) {
		BlockPos absolute = helper.absolutePos(pos);
		ServerLevel level = helper.getLevel();
		BlockState state = level.getBlockState(absolute);
		BlockEntity blockEntity = level.getBlockEntity(absolute);
		ItemStack tool = player.getMainHandItem();
		ItemStack used = tool.copy();
		boolean harvests = state.canHarvestBlock(level, absolute, player);
		tool.mineBlock(level, state, absolute, player);
		if (!state.onDestroyedByPlayer(level, absolute, player, harvests,
			level.getFluidState(absolute)))
			return;
		state.getBlock()
			.destroy(level, absolute, state);
		if (harvests)
			state.getBlock()
				.playerDestroy(level, player, absolute, state, blockEntity, used);
	}

	/** Renews a source's lease every tick for a while, standing in for a held button. */
	private static void holdOpen(GameTestHelper helper, BlockPos source, int ticks) {
		for (int tick = 1; tick <= ticks; tick++)
			helper.runAfterDelay(tick, () -> {
				// The level's own getBlockEntity, not the helper's: the helper's throws when there is
				// nothing there, which turns "something destroyed the source" into a failure reported
				// from inside a renewal loop instead of from whichever assertion was looking for it.
				BlockEntity be = helper.getLevel()
					.getBlockEntity(helper.absolutePos(source));
				if (be instanceof PneumaticSourceBlockEntity live)
					live.renew();
			});
	}

	/** Ticks the held item's use clock the way vanilla does while the button is down. */
	private static void drive(GameTestHelper helper, Player player, int ticks) {
		player.startUsingItem(InteractionHand.MAIN_HAND);
		for (int tick = 0; tick < ticks; tick++)
			player.getMainHandItem()
				.getItem()
				.onUseTick(helper.getLevel(), player, player.getMainHandItem(),
					player.getUseItemRemainingTicks() - tick);
	}

	private static InteractionResult useOn(GameTestHelper helper, Player player, BlockPos pos) {
		BlockPos absolute = helper.absolutePos(pos);
		BlockHitResult hit =
			new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
		return player.getMainHandItem()
			.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
	}

	/** Grinds or buffs one block and asserts what it turned into, leaving the site clear after. */
	private static void expectTreatment(GameTestHelper helper, Player player, BlockPos at,
		Block from, Block to) {
		helper.setBlock(at, from);
		useOn(helper, player, at);
		helper.assertBlockPresent(to, at);
		helper.setBlock(at, Blocks.AIR);
	}

	/** A stone wall in the XY plane, {@code radius} out from {@code centre}. */
	private static void buildWall(GameTestHelper helper, BlockPos centre, int radius) {
		for (int x = -radius; x <= radius; x++)
			for (int y = -radius; y <= radius; y++)
				helper.setBlock(centre.offset(x, y, 0), Blocks.STONE);
	}

	/**
	 * Points the player at the middle of one face of a block, which is what the tunnelling drill's own
	 * cast from the player's eyes will find.
	 */
	private static void aimAt(GameTestHelper helper, Player player, BlockPos pos, Direction face) {
		Vec3 target = Vec3.atCenterOf(helper.absolutePos(pos))
			.add(Vec3.atLowerCornerOf(face.getNormal())
				.scale(0.5));
		player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
	}

	/** Points the player squarely along one axis, which is what the tunnelling drill reads. */
	private static void lookAt(Player player, Direction facing) {
		player.setYRot(facing.toYRot());
		player.setXRot(facing == Direction.DOWN ? 90.0F : facing == Direction.UP ? -90.0F : 0.0F);
	}
}
