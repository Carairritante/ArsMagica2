package am2.network;

import am2.items.ItemsCommonProxy;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server relay telling nearby players that someone is reading the Arcane Compendium, so their
 * held book can be drawn open. Purely visual: nothing is saved and nothing runs per tick.
 * Readers repeat the open state every few seconds and viewers expire it, which also covers
 * players who arrive later or a close message that never came.
 */
public final class CompendiumBookSync {
	static final long MIN_OPEN_INTERVAL_MS = 500L;
	private static final double RADIUS = 48.0;
	private static final Map<UUID, Reader> READERS = new ConcurrentHashMap<UUID, Reader>();

	static final class Reader {
		private boolean open;
		private boolean seen;
		private long lastOpen;

		/** Whether this message should be relayed; bounds what a modified client can cause. */
		synchronized boolean accept(boolean wantsOpen, long now) {
			if (wantsOpen) {
				if (seen && now - lastOpen < MIN_OPEN_INTERVAL_MS) return false;
				seen = true;
				open = true;
				lastOpen = now;
				return true;
			}
			if (!open) return false;
			open = false;
			return true;
		}
	}

	private CompendiumBookSync() {}

	static void handle(byte[] data, EntityPlayerMP player) {
		if (player == null || player.isDead || data == null || data.length != 1) return;
		boolean open = data[0] != 0;
		if (open) {
			ItemStack held = player.getCurrentEquippedItem();
			if (held == null || held.getItem() != ItemsCommonProxy.arcaneCompendium) return;
		}
		Reader reader = READERS.get(player.getUniqueID());
		if (reader == null) {
			reader = new Reader();
			READERS.put(player.getUniqueID(), reader);
		}
		if (!reader.accept(open, System.currentTimeMillis())) return;
		AMDataWriter writer = new AMDataWriter().add(player.getEntityId()).add(open);
		AMNetHandler.INSTANCE.sendPacketToAllClientsNear(player.dimension, player.posX, player.posY, player.posZ,
				RADIUS, AMPacketIDs.COMPENDIUM_BOOK_STATE, writer.generate());
	}

	public static void forget(UUID player) {
		READERS.remove(player);
	}
}
