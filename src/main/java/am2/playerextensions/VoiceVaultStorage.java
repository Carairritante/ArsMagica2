package am2.playerextensions;

import am2.api.spell.ItemSpellBase;
import am2.network.AMDataReader;
import am2.network.AMDataWriter;
import am2.network.AMNetHandler;
import am2.network.AMPacketIDs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;
import net.minecraftforge.common.util.Constants;

import java.util.Locale;

/**
 * Personal 8-slot soul-bound spell vault for Vocal Resonance (modeled after RiftStorage).
 * Stores real spell scrolls (ItemSpellBase) and their per-slot bound word labels on the player.
 */
public class VoiceVaultStorage implements IExtendedEntityProperties, IInventory {

	public static final String identifier = "ArsMagicaVoiceVault";
	public static final int NUM_SLOTS = 8;

	private ItemStack[] inventory = new ItemStack[NUM_SLOTS];
	private String[] words = new String[NUM_SLOTS];

	public VoiceVaultStorage() {
		for (int i = 0; i < NUM_SLOTS; i++) {
			words[i] = "";
		}
	}

	public static VoiceVaultStorage For(EntityPlayer player) {
		if (player == null) {
			return null;
		}
		VoiceVaultStorage storage = (VoiceVaultStorage) player.getExtendedProperties(identifier);
		if (storage == null) {
			storage = new VoiceVaultStorage();
			player.registerExtendedProperties(identifier, storage);
		}
		return storage;
	}

	public String getWord(int slot) {
		if (slot < 0 || slot >= NUM_SLOTS) return "";
		return words[slot] != null ? words[slot] : "";
	}

	public void setWord(int slot, String rawWord) {
		if (slot < 0 || slot >= NUM_SLOTS) return;
		words[slot] = normalizeWord(rawWord);
	}

	public static String normalizeWord(String rawWord) {
		if (rawWord == null) return "";
		String s = rawWord.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
		return s.length() > 32 ? s.substring(0, 32) : s;
	}

	public int findSlotByWord(String rawWord) {
		String norm = normalizeWord(rawWord);
		if (norm.isEmpty()) return -1;
		for (int i = 0; i < NUM_SLOTS; i++) {
			if (norm.equals(words[i])) {
				return i;
			}
		}
		return -1;
	}

	public void syncToClient(EntityPlayerMP player) {
		if (player == null) return;
		AMDataWriter writer = new AMDataWriter();
		for (int i = 0; i < NUM_SLOTS; i++) {
			writer.add(getWord(i));
			boolean hasSpell = inventory[i] != null;
			writer.add(hasSpell);
			if (hasSpell) {
				NBTTagCompound itemTag = new NBTTagCompound();
				inventory[i].writeToNBT(itemTag);
				writer.add(itemTag);
			}
		}
		AMNetHandler.INSTANCE.sendPacketToClientPlayer(player, AMPacketIDs.VOICE_VAULT_SYNC, writer.generate());
	}

	public void handleClientSyncPacket(byte[] data) {
		if (data == null) return;
		AMDataReader rdr = new AMDataReader(data, false);
		for (int i = 0; i < NUM_SLOTS; i++) {
			words[i] = normalizeWord(rdr.getString());
			boolean hasSpell = rdr.getBoolean();
			if (hasSpell) {
				NBTTagCompound itemTag = rdr.getNBTTagCompound();
				inventory[i] = itemTag != null ? ItemStack.loadItemStackFromNBT(itemTag) : null;
			} else {
				inventory[i] = null;
			}
		}
	}

	@Override
	public void saveNBTData(NBTTagCompound compound) {
		NBTTagList list = new NBTTagList();
		for (int i = 0; i < NUM_SLOTS; i++) {
			String w = getWord(i);
			if (inventory[i] != null || !w.isEmpty()) {
				NBTTagCompound entry = new NBTTagCompound();
				entry.setByte("Slot", (byte) i);
				entry.setString("Word", w);
				if (inventory[i] != null) {
					NBTTagCompound itemTag = new NBTTagCompound();
					inventory[i].writeToNBT(itemTag);
					entry.setTag("SpellItem", itemTag);
				}
				list.appendTag(entry);
			}
		}
		compound.setTag("PlayerVoiceVault", list);
	}

	@Override
	public void loadNBTData(NBTTagCompound compound) {
		inventory = new ItemStack[NUM_SLOTS];
		words = new String[NUM_SLOTS];
		for (int i = 0; i < NUM_SLOTS; i++) {
			words[i] = "";
		}
		if (compound == null || !compound.hasKey("PlayerVoiceVault")) {
			return;
		}
		NBTTagList list = compound.getTagList("PlayerVoiceVault", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound entry = list.getCompoundTagAt(i);
			int slot = entry.getByte("Slot") & 0xFF;
			if (slot < NUM_SLOTS) {
				words[slot] = normalizeWord(entry.getString("Word"));
				if (entry.hasKey("SpellItem")) {
					inventory[slot] = ItemStack.loadItemStackFromNBT(entry.getCompoundTag("SpellItem"));
				}
			}
		}
	}

	@Override
	public void init(Entity entity, World world) {
		if (inventory == null) {
			inventory = new ItemStack[NUM_SLOTS];
		}
		if (words == null) {
			words = new String[NUM_SLOTS];
			for (int i = 0; i < NUM_SLOTS; i++) {
				words[i] = "";
			}
		}
	}

	@Override
	public int getSizeInventory() {
		return NUM_SLOTS;
	}

	@Override
	public ItemStack getStackInSlot(int i) {
		if (i < 0 || i >= NUM_SLOTS) return null;
		return inventory[i];
	}

	@Override
	public ItemStack decrStackSize(int i, int j) {
		if (i < 0 || i >= NUM_SLOTS || inventory[i] == null) {
			return null;
		}
		if (inventory[i].stackSize <= j) {
			ItemStack stack = inventory[i];
			inventory[i] = null;
			return stack;
		}
		ItemStack split = inventory[i].splitStack(j);
		if (inventory[i].stackSize == 0) {
			inventory[i] = null;
		}
		return split;
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int i) {
		if (i < 0 || i >= NUM_SLOTS || inventory[i] == null) {
			return null;
		}
		ItemStack stack = inventory[i];
		inventory[i] = null;
		return stack;
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack stack) {
		if (i < 0 || i >= NUM_SLOTS) return;
		inventory[i] = stack;
		if (stack != null && stack.stackSize > getInventoryStackLimit()) {
			stack.stackSize = getInventoryStackLimit();
		}
	}

	@Override
	public String getInventoryName() {
		return "am2.gui.vocalAura";
	}

	@Override
	public boolean hasCustomInventoryName() {
		return false;
	}

	@Override
	public int getInventoryStackLimit() {
		return 1;
	}

	@Override
	public void markDirty() {
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer player) {
		return player != null && !player.isDead && am2.skills.VocalResonance.isUnlocked(player);
	}

	@Override
	public void openInventory() {
	}

	@Override
	public void closeInventory() {
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		return acceptsScroll(stack);
	}

	/** Plain spell scrolls only; True Soulbound items stay in the player's own inventory. */
	public static boolean acceptsScroll(ItemStack stack) {
		return stack != null && stack.getItem() instanceof ItemSpellBase
				&& !am2.soulbound.TrueSoulboundHelper.isTrueSoulbound(stack);
	}
}
