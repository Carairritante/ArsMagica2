package am2.items.renderers;

import am2.guis.AMGuiHelper;
import am2.guis.GuiArcaneCompendium;
import am2.guis.GuiCompendiumIndex;
import am2.guis.grimoire.GuiGrimoire;
import am2.items.ItemsCommonProxy;
import am2.network.AMNetHandler;
import am2.network.AMPacketIDs;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Client state of held Compendiums: plays the local opening motion before the screen appears,
 * keeps the book open while it is being read, tells the server about it and eases the books
 * of other players that the server reports as open.
 */
@SideOnly(Side.CLIENT)
public final class CompendiumBookState {
	private static final long OPEN_DELAY_MS = 380L;
	/** Repeated while reading; viewers forget a reader after REMOTE_TIMEOUT_MS of silence. */
	private static final long REPEAT_MS = 5000L;
	private static final long REMOTE_TIMEOUT_MS = 12000L;
	private static final CompendiumBookState INSTANCE = new CompendiumBookState();

	private static final class Remote {
		long openUntil;
		long lastFrame;
		float open;
	}

	private final Map<Integer, Remote> remotes = new HashMap<Integer, Remote>();
	private World world;
	private boolean reported;
	private long reportedAt;

	private boolean registered;
	private long requestedAt;
	private ItemStack requested;
	private float open;
	private long lastFrame;

	private CompendiumBookState() {}

	/** Starts the opening motion; the screen is shown a moment later. */
	public static void requestOpen(ItemStack stack) {
		INSTANCE.begin(stack);
	}

	private void begin(ItemStack stack) {
		register();
		if (requested != null) return;
		requested = stack;
		requestedAt = Minecraft.getSystemTime();
		Minecraft.getMinecraft().getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.func_147674_a(
				new net.minecraft.util.ResourceLocation("arsmagica2:misc.craftingaltar.create_item"), 1.7f));
	}

	static void init() {
		INSTANCE.register();
	}

	private void register() {
		if (!registered) {
			registered = true;
			FMLCommonHandler.instance().bus().register(this);
		}
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld != world) { // entity ids belong to one world
			world = mc.theWorld;
			synchronized (this) {
				remotes.clear();
			}
			reported = false;
		}
		advanceRequest(mc);
		report(mc);
	}

	private static boolean holdsBook(Minecraft mc) {
		ItemStack held = mc.thePlayer == null ? null : mc.thePlayer.getCurrentEquippedItem();
		return held != null && held.getItem() == ItemsCommonProxy.arcaneCompendium;
	}

	private boolean isReading() {
		GuiScreen screen = Minecraft.getMinecraft().currentScreen;
		return requested != null || screen instanceof GuiGrimoire
				|| screen instanceof GuiArcaneCompendium || screen instanceof GuiCompendiumIndex;
	}

	private void report(Minecraft mc) {
		if (mc.thePlayer == null) return;
		boolean reading = isReading() && holdsBook(mc);
		long now = Minecraft.getSystemTime();
		if (reading == reported && !(reading && now - reportedAt >= REPEAT_MS)) return;
		reported = reading;
		reportedAt = now;
		AMNetHandler.INSTANCE.sendPacketToServer(AMPacketIDs.COMPENDIUM_BOOK_STATE, new byte[]{(byte) (reading ? 1 : 0)});
	}

	private void advanceRequest(Minecraft mc) {
		if (requested == null) return;
		if (mc.currentScreen != null || !holdsBook(mc)) {
			requested = null; // switched item or opened something else meanwhile
			return;
		}
		if (Minecraft.getSystemTime() - requestedAt >= OPEN_DELAY_MS) {
			ItemStack stack = requested;
			requested = null;
			AMGuiHelper.showCompendium(stack);
		}
	}

	/** How open the local player's book is, eased once per rendered frame. */
	static float localOpenAmount() {
		return INSTANCE.advance();
	}

	private float advance() {
		long now = Minecraft.getSystemTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		open = approach(open, isReading(), dt);
		return smooth(open);
	}

	/** Server report about another player's book. */
	public static void onRemoteState(int entityId, boolean opened) {
		INSTANCE.remoteState(entityId, opened);
	}

	private synchronized void remoteState(int entityId, boolean opened) {
		Remote remote = remotes.get(entityId);
		if (remote == null) {
			if (!opened) return;
			remote = new Remote();
			remote.lastFrame = Minecraft.getSystemTime();
			remotes.put(entityId, remote);
		}
		remote.openUntil = opened ? Minecraft.getSystemTime() + REMOTE_TIMEOUT_MS : 0;
	}

	/** How open another player's book is, eased once per rendered frame. */
	static float remoteOpenAmount(int entityId) {
		return INSTANCE.advanceRemote(entityId);
	}

	private synchronized float advanceRemote(int entityId) {
		Remote remote = remotes.get(entityId);
		if (remote == null) return 0;
		long now = Minecraft.getSystemTime();
		float dt = Math.min(0.1f, (now - remote.lastFrame) / 1000f);
		remote.lastFrame = now;
		boolean reading = now < remote.openUntil;
		remote.open = approach(remote.open, reading, dt);
		if (!reading && remote.open <= 0) remotes.remove(entityId);
		return smooth(remote.open);
	}

	private static float approach(float value, boolean opening, float dt) {
		float step = dt / (OPEN_DELAY_MS / 1000f);
		return opening ? Math.min(1, value + step) : Math.max(0, value - step);
	}

	private static float smooth(float value) {
		return value * value * (3 - 2 * value);
	}
}
