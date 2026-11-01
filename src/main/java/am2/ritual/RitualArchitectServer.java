package am2.ritual;

import am2.LogHelper;
import am2.items.ItemRitualArchitect;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Small bounded queue: a GUI/network thread never edits the world. Coalesces the latest complete GUI state per player. */
public final class RitualArchitectServer {
    private static final ConcurrentLinkedQueue<UUID> QUEUE = new ConcurrentLinkedQueue<UUID>();
    private static final ConcurrentHashMap<UUID, Action> PENDING = new ConcurrentHashMap<UUID, Action>();
    private static boolean registered;

    /** Called before sided proxy setup, including the physical client's integrated server. */
    public static synchronized void register() {
        if (registered) return;
        FMLCommonHandler.instance().bus().register(new RitualArchitectServer());
        registered = true;
    }

    private static final class Action {
        final EntityPlayer player;
        final int action, index, rotation, slot;
        final long token;
        final String entryId;
        Action(EntityPlayer player, int action, int index, int rotation, int slot, long token, String entryId) {
            this.player = player; this.action = action; this.index = index; this.rotation = rotation; this.slot = slot; this.token = token;
            this.entryId = entryId;
        }
    }

    public static void enqueue(EntityPlayer player, int action, int index, int rotation, int slot, long token) {
        enqueue(player, action, index, rotation, slot, token, null);
    }

    public static synchronized void enqueue(EntityPlayer player, int action, int index, int rotation, int slot, long token, String entryId) {
        if (player == null || action < 0 || action > 2 || rotation < 0 || rotation > 3 || slot < 0 || slot > 8
                || token == 0L || entryId != null && entryId.length() > 128
                || (!PENDING.containsKey(player.getUniqueID()) && PENDING.size() >= 128)) return;
        Action previous = PENDING.put(player.getUniqueID(), new Action(player, action, index, rotation, slot, token, entryId));
        if (previous == null) QUEUE.add(player.getUniqueID());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) drain();
    }

    public static void drain() {
        for (int i = 0; i < 64; i++) {
            UUID id = QUEUE.poll();
            if (id == null) break;
            Action action = PENDING.remove(id);
            if (action == null) continue;
            try {
                EntityPlayer player = action.player;
                if (player instanceof EntityPlayerMP && ((EntityPlayerMP)player).playerNetServerHandler != null
                        && !((EntityPlayerMP)player).playerNetServerHandler.netManager.isChannelOpen()) continue;
                ItemRitualArchitect.applyAction(player, action.action, action.index, action.rotation, action.slot, action.token, action.entryId);
            } catch (RuntimeException failure) {
                LogHelper.error("Ritual Architect request failed: " + failure);
            }
        }
    }
}
