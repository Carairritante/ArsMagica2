package am2.soulbound;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

public class TrueSoulboundVault extends WorldSavedData {

	public static final String DATA_NAME = "am2_true_soulbound_vault";
	public static final String PERSISTED_KEY = "am2_true_soulbound_vault_entries";
	private static final String TAG_ENTRIES = "Entries";
	private static final String TAG_UID = "UID";
	private static final String TAG_OWNER = "Owner";
	private static final String TAG_SLOT = "Slot";
	private static final String TAG_STACK = "Stack";

	public static final class VaultEntry {
		public final String uid;
		public final UUID ownerUUID;
		public final int preferredSlot;
		public final ItemStack stack;

		public VaultEntry(String uid, UUID ownerUUID, int preferredSlot, ItemStack stack) {
			this.uid = uid;
			this.ownerUUID = ownerUUID;
			this.preferredSlot = preferredSlot;
			this.stack = stack;
		}
	}

	private final Map<String, VaultEntry> entriesByUid = new LinkedHashMap<String, VaultEntry>();

	public TrueSoulboundVault() {
		super(DATA_NAME);
	}

	public TrueSoulboundVault(String name) {
		super(name);
	}

	public static TrueSoulboundVault get(World world) {
		World storageWorld = resolveGlobalWorld(world);
		if (storageWorld == null || storageWorld.isRemote || storageWorld.mapStorage == null) {
			return null;
		}
		WorldSavedData existing = storageWorld.mapStorage.loadData(TrueSoulboundVault.class, DATA_NAME);
		if (existing instanceof TrueSoulboundVault) {
			return (TrueSoulboundVault) existing;
		}
		TrueSoulboundVault created = new TrueSoulboundVault(DATA_NAME);
		storageWorld.mapStorage.setData(DATA_NAME, created);
		return created;
	}

	private static World resolveGlobalWorld(World fallback) {
		try {
			MinecraftServer server = MinecraftServer.getServer();
			if (server != null && server.worldServers != null && server.worldServers.length > 0 && server.worldServers[0] != null) {
				return server.worldServers[0];
			}
		} catch (Throwable ignored) {
		}
		return fallback;
	}

	public boolean store(UUID ownerUUID, ItemStack stack, int preferredSlot) {
		if (ownerUUID == null || stack == null || stack.stackSize <= 0) {
			return false;
		}
		String uid = TrueSoulboundHelper.getItemUID(stack);
		if (uid == null || uid.isEmpty()) {
			return false;
		}
		ItemStack copy = stack.copy();
		copy.stackSize = 1;
		entriesByUid.put(uid, new VaultEntry(uid, ownerUUID, preferredSlot, copy));
		markDirty();
		return true;
	}

	public boolean containsUID(String uid) {
		return uid != null && entriesByUid.containsKey(uid);
	}

	public boolean removeByUID(String uid) {
		if (uid != null && entriesByUid.remove(uid) != null) {
			markDirty();
			return true;
		}
		return false;
	}

	public int size() {
		return entriesByUid.size();
	}

	public List<VaultEntry> getEntriesForOwner(UUID ownerUUID) {
		List<VaultEntry> result = new ArrayList<VaultEntry>();
		if (ownerUUID == null) {
			return result;
		}
		for (VaultEntry entry : entriesByUid.values()) {
			if (ownerUUID.equals(entry.ownerUUID)) {
				result.add(entry);
			}
		}
		return result;
	}

	public int deliverToPlayer(EntityPlayer player) {
		if (player == null || player.inventory == null) {
			return 0;
		}
		UUID ownerUUID = player.getUniqueID();
		int delivered = 0;
		Iterator<Map.Entry<String, VaultEntry>> it = entriesByUid.entrySet().iterator();
		while (it.hasNext()) {
			VaultEntry entry = it.next().getValue();
			if (!ownerUUID.equals(entry.ownerUUID)) {
				continue;
			}
			if (TrueSoulboundHelper.inventoryContainsUID(player.inventory, entry.uid)
					|| TrueSoulboundHelper.deliverToPlayerInventory(player, entry.stack, entry.preferredSlot)) {
				it.remove();
				removePersistedEntry(player, entry.uid);
				markDirty();
				delivered++;
			}
		}
		return delivered;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		entriesByUid.clear();
		if (nbt == null || !nbt.hasKey(TAG_ENTRIES, 9)) {
			return;
		}
		NBTTagList list = nbt.getTagList(TAG_ENTRIES, 10);
		for (int i = 0; i < list.tagCount(); i++) {
			VaultEntry entry = readEntry(list.getCompoundTagAt(i));
			if (entry != null) {
				entriesByUid.put(entry.uid, entry);
			}
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		NBTTagList list = new NBTTagList();
		for (VaultEntry entry : entriesByUid.values()) {
			list.appendTag(writeEntry(entry));
		}
		nbt.setTag(TAG_ENTRIES, list);
	}

	public static void storeInPersistedPlayer(EntityPlayer player, ItemStack stack, int preferredSlot) {
		if (player == null || stack == null || stack.stackSize <= 0) {
			return;
		}
		String uid = TrueSoulboundHelper.getItemUID(stack);
		UUID ownerUUID = TrueSoulboundHelper.getOwnerUUID(stack);
		if (uid == null || ownerUUID == null) {
			return;
		}
		NBTTagCompound persisted = getOrCreatePersistedTag(player);
		NBTTagList list = persisted.hasKey(PERSISTED_KEY, 9) ? persisted.getTagList(PERSISTED_KEY, 10) : new NBTTagList();
		NBTTagList updated = new NBTTagList();
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound existing = list.getCompoundTagAt(i);
			if (!uid.equals(existing.getString(TAG_UID))) {
				updated.appendTag(existing);
			}
		}
		ItemStack copy = stack.copy();
		copy.stackSize = 1;
		updated.appendTag(writeEntry(new VaultEntry(uid, ownerUUID, preferredSlot, copy)));
		persisted.setTag(PERSISTED_KEY, updated);
	}

	public static int drainPersistedPlayer(EntityPlayer player) {
		if (player == null || player.inventory == null) {
			return 0;
		}
		NBTTagCompound entityData = player.getEntityData();
		if (entityData == null || !entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) {
			return 0;
		}
		NBTTagCompound persisted = entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
		if (!persisted.hasKey(PERSISTED_KEY, 9)) {
			return 0;
		}
		NBTTagList list = persisted.getTagList(PERSISTED_KEY, 10);
		NBTTagList remaining = new NBTTagList();
		int restored = 0;
		UUID playerUUID = player.getUniqueID();

		for (int i = 0; i < list.tagCount(); i++) {
			VaultEntry entry = readEntry(list.getCompoundTagAt(i));
			if (entry == null || !playerUUID.equals(entry.ownerUUID)) {
				continue;
			}
			if (TrueSoulboundHelper.inventoryContainsUID(player.inventory, entry.uid)
					|| TrueSoulboundHelper.deliverToPlayerInventory(player, entry.stack, entry.preferredSlot)) {
				restored++;
			} else {
				remaining.appendTag(writeEntry(entry));
			}
		}

		if (remaining.tagCount() == 0) {
			persisted.removeTag(PERSISTED_KEY);
		} else {
			persisted.setTag(PERSISTED_KEY, remaining);
		}
		return restored;
	}

	public static void removePersistedEntry(EntityPlayer player, String uid) {
		if (player == null || uid == null || uid.isEmpty()) {
			return;
		}
		NBTTagCompound entityData = player.getEntityData();
		if (entityData == null || !entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) {
			return;
		}
		NBTTagCompound persisted = entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
		if (!persisted.hasKey(PERSISTED_KEY, 9)) {
			return;
		}
		NBTTagList list = persisted.getTagList(PERSISTED_KEY, 10);
		NBTTagList kept = new NBTTagList();
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound entryTag = list.getCompoundTagAt(i);
			if (!uid.equals(entryTag.getString(TAG_UID))) {
				kept.appendTag(entryTag);
			}
		}
		if (kept.tagCount() == 0) {
			persisted.removeTag(PERSISTED_KEY);
		} else {
			persisted.setTag(PERSISTED_KEY, kept);
		}
	}

	private static NBTTagCompound getOrCreatePersistedTag(EntityPlayer player) {
		NBTTagCompound entityData = player.getEntityData();
		if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) {
			entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
		}
		return entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
	}

	private static NBTTagCompound writeEntry(VaultEntry entry) {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setString(TAG_UID, entry.uid);
		tag.setString(TAG_OWNER, entry.ownerUUID.toString());
		tag.setInteger(TAG_SLOT, entry.preferredSlot);
		tag.setTag(TAG_STACK, entry.stack.writeToNBT(new NBTTagCompound()));
		return tag;
	}

	private static VaultEntry readEntry(NBTTagCompound tag) {
		if (tag == null) {
			return null;
		}
		String uid = tag.getString(TAG_UID);
		String ownerStr = tag.getString(TAG_OWNER);
		if (uid == null || uid.isEmpty() || ownerStr == null || ownerStr.isEmpty() || !tag.hasKey(TAG_STACK, 10)) {
			return null;
		}
		UUID ownerUUID;
		try {
			ownerUUID = UUID.fromString(ownerStr);
		} catch (IllegalArgumentException ex) {
			return null;
		}
		ItemStack stack = ItemStack.loadItemStackFromNBT(tag.getCompoundTag(TAG_STACK));
		if (stack == null || stack.getItem() == null) {
			return null;
		}
		return new VaultEntry(uid, ownerUUID, tag.getInteger(TAG_SLOT), stack);
	}
}
