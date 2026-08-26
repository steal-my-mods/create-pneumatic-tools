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
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.AbstractSimpleShaftBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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

	@GameTest(template = "workshop")
	public static void theBufferPolishesAWholeStackInOneClick(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.BUFFER.get());
		player.setItemInHand(InteractionHand.OFF_HAND,
			new ItemStack(AllItems.ROSE_QUARTZ.get(), 16));

		int before = air(player);
		use(helper, player);

		if (!player.getOffhandItem()
			.isEmpty())
			throw new GameTestAssertException("the buffer left " + player.getOffhandItem()
				.getCount() + " unpolished");
		if (!player.getInventory()
			.contains(new ItemStack(AllItems.POLISHED_ROSE_QUARTZ.get())))
			throw new GameTestAssertException("no polished rose quartz came back");

		int spent = before - air(player);
		int expected = 16 * costOf(CPTConfig.bufferUsesPerTank());
		if (spent != expected)
			throw new GameTestAssertException(
				"polishing sixteen spent " + spent + " air, expected " + expected);
		helper.succeed();
	}

	@GameTest(template = "workshop")
	public static void anEmptyingTankStopsTheBufferPartWay(GameTestHelper helper) {
		Player player = worker(helper, CPTItems.BUFFER.get());
		// Enough for three of the sixteen, so the loop has to stop on its own rather than on running
		// out of feedstock -- and what it has not done must still be in hand.
		int affordable = 3;
		setAir(player, affordable * costOf(CPTConfig.bufferUsesPerTank()));
		player.setItemInHand(InteractionHand.OFF_HAND,
			new ItemStack(AllItems.ROSE_QUARTZ.get(), 16));

		use(helper, player);

		int left = player.getOffhandItem()
			.getCount();
		if (left != 16 - affordable)
			throw new GameTestAssertException(
				"the buffer left " + left + " in hand, expected " + (16 - affordable));
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
	public static void arenewedSourceStays(GameTestHelper helper) {
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
	 * Breaks a block the way a survival player does.
	 *
	 * <p>{@code mineBlock} before {@code destroyBlock} is vanilla's order, not a convenience: the saw
	 * hands the still-standing block's position to Create's tree search, so a test that removed it
	 * first would be testing a different code path from the game.
	 */
	private static void mine(GameTestHelper helper, Player player, BlockPos pos) {
		BlockPos absolute = helper.absolutePos(pos);
		BlockState state = helper.getLevel()
			.getBlockState(absolute);
		player.getMainHandItem()
			.mineBlock(helper.getLevel(), state, absolute, player);
		helper.getLevel()
			.destroyBlock(absolute, true, player);
	}

	/** Renews a source's lease every tick for a while, standing in for a held button. */
	private static void holdOpen(GameTestHelper helper, BlockPos source, int ticks) {
		for (int tick = 1; tick <= ticks; tick++)
			helper.runAfterDelay(tick, () -> {
				if (helper.getBlockEntity(source) instanceof PneumaticSourceBlockEntity be)
					be.renew();
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

	private static void use(GameTestHelper helper, Player player) {
		player.getMainHandItem()
			.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
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

	/** Points the player squarely along one axis, which is what the tunnelling drill reads. */
	private static void lookAt(Player player, Direction facing) {
		player.setYRot(facing.toYRot());
		player.setXRot(facing == Direction.DOWN ? 90.0F : facing == Direction.UP ? -90.0F : 0.0F);
	}
}
