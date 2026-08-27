package com.createpneumatictools.client;

import java.util.List;

import com.createpneumatictools.CreatePneumaticTools;
import com.createpneumatictools.registry.CPTItems;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.armor.BacktankUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Fills the hotbar with every tool, straps on a full backtank, and photographs the result.
 *
 * <p>A dev tool, off unless {@code -Dcreatepneumatictools.photos=true} is passed, and inert in
 * production. It exists because the alternative was worse: the only ways to see what these models
 * actually look like are to drive the client by hand, or to drive it by sending it keystrokes from
 * outside — and on macOS the second needs Accessibility permission the build has no business asking
 * for. The game can photograph itself; {@link Screenshot#grab} is the same call F2 makes.
 *
 * <p>Worth keeping rather than deleting after one use: the README's images are of nine tools that a
 * generator draws, so they go stale exactly when the generator changes, and a one-command way to
 * retake them is the difference between updating them and not.
 *
 *     ./gradlew runClient -PquickPlay=&lt;world&gt; -Dcreatepneumatictools.photos=true
 */
public class CPTPhotoShoot {

	private static final String FLAG = "createpneumatictools.photos";
	/** Long enough for the world to finish loading and the first frame with the items to be drawn. */
	private static final int SETTLE_TICKS = 140;
	/** Frames, and the gap between them. Three of them a few ticks apart is what shows the motion:
	 *  a still cannot tell a spinning blade from a stopped one, which is the half of this that is
	 *  easiest to ship broken. */
	private static final int FRAMES = 3;
	private static final int FRAME_GAP = 4;

	private static int ticks;
	private static int taken;
	private static boolean done;

	/**
	 * A hotbar slot to hold for every frame, or -1 to show a different tool in each.
	 *
	 * <p>Holding one is what proves the animation: three photographs of three tools cannot tell a
	 * spinning blade from a stopped one, and three of the same blade four ticks apart can.
	 */
	private static int pinnedSlot() {
		String value = System.getProperty(FLAG, "");
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException notASlot) {
			return -1;
		}
	}

	public static void register() {
		if (FMLEnvironment.production || System.getProperty(FLAG) == null)
			return;
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CPTPhotoShoot::onClientTick);
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		if (done)
			return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null)
			return;

		ticks++;
		if (ticks == 1)
			equip(minecraft);
		if (ticks < SETTLE_TICKS || (ticks - SETTLE_TICKS) % FRAME_GAP != 0)
			return;

		// A different tool in hand each frame, so three photos cover three of them rather than three
		// moments of one.
		int pinned = pinnedSlot();
		hold(minecraft, pinned >= 0 ? pinned : taken * 3 % CPTItems.all().size());
		// Pinned to one tool means the run is about how that tool *behaves*, so hold the trigger: the
		// parts spool up, the tool leans into the work, and vanilla fires a swing that the hand
		// transform is supposed to be ignoring. A photograph of a tool at rest proves none of that.
		if (pinned >= 0)
			minecraft.options.keyAttack.setDown(true);
		taken++;
		done = taken >= FRAMES;
		if (done)
			minecraft.options.keyAttack.setDown(false);
		Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(),
			message -> CreatePneumaticTools.LOGGER.info("photo: {}", message.getString()));
	}

	/**
	 * Puts the tools in the hotbar and a charged backtank on the player's back.
	 *
	 * <p>Server side, through the integrated server, because an inventory edited on the client alone
	 * is undone by the next sync — and the backtank has to be real, or every tool photographs itself
	 * stopped: the animation clock reads the wearer's air.
	 */
	private static void equip(Minecraft minecraft) {
		List<ServerPlayer> players = minecraft.getSingleplayerServer()
			.getPlayerList()
			.getPlayers();
		if (players.isEmpty())
			return;
		ServerPlayer player = players.getFirst();

		int slot = 0;
		for (var entry : CPTItems.all())
			player.getInventory()
				.setItem(slot++, new ItemStack(entry.get()));

		ItemStack backtank = new ItemStack(AllItems.COPPER_BACKTANK.get());
		backtank.set(AllDataComponents.BACKTANK_AIR, BacktankUtil.maxAir(backtank));
		player.setItemSlot(EquipmentSlot.CHEST, backtank);
		// Chosen here rather than at the first frame: swapping the held item lowers the hand for a few
		// ticks, so a slot picked late photographs an empty fist.
		hold(minecraft, Math.max(0, pinnedSlot()));

		// Daylight, clear skies, and a clock that does not move: a tool photographed at midnight is a
		// silhouette, and a scene that changes between frames makes it impossible to tell which pixels
		// moved because the tool did.
		for (String command : new String[] {"gamerule doDaylightCycle false",
			"gamerule doWeatherCycle false", "gamerule randomTickSpeed 0", "time set noon",
			"weather clear", "gamemode creative"})
			run(minecraft, command);
	}

	private static void run(Minecraft minecraft, String command) {
		var server = minecraft.getSingleplayerServer();
		server.getCommands()
			.performPrefixedCommand(server.createCommandSourceStack()
				.withSuppressedOutput(), command);
	}

	/**
	 * Picks the hotbar slot to photograph, on <em>both</em> sides.
	 *
	 * <p>The client one is the one that matters and the one that is easy to miss: which item a player
	 * is holding is drawn from the client's own `selected`, and a server-side change to it is never
	 * synced back. Setting only the server's leaves the camera pointing at whatever slot the world was
	 * saved on, which looks exactly like the photo tool working.
	 */
	private static void hold(Minecraft minecraft, int slot) {
		if (minecraft.player != null)
			minecraft.player.getInventory().selected = slot;
		List<ServerPlayer> players = minecraft.getSingleplayerServer()
			.getPlayerList()
			.getPlayers();
		if (!players.isEmpty())
			players.getFirst()
				.getInventory().selected = slot;
	}
}
