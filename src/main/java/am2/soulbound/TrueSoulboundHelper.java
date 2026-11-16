package am2.soulbound;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import am2.playerextensions.ExtendedProperties;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

public final class TrueSoulboundHelper {

	public static final String TAG_ROOT = "am2_true_soulbound";
	public static final String TAG_OWNER_UUID = "ownerUUID";
	public static final String TAG_OWNER_NAME = "ownerName";
	public static final String TAG_UID = "uid";
	public static final String TAG_BOUND_AT = "boundAt";

	public static final long UNBIND_CONFIRM_TICKS = 60L;
	public static final int BAUBLES_SLOT_OFFSET = 1000;
	private static final long NOTIFY_COOLDOWN_MS = 1500L;
	private static final long PUNISH_COOLDOWN_MS = 1000L;

	private static volatile boolean baublesResolved = false;
	private static Method baublesGetMethod = null;

	private static final class PendingUnbind {
		final String uid;
		final long expiresAtTick;

		PendingUnbind(String uid, long expiresAtTick) {
			this.uid = uid;
			this.expiresAtTick = expiresAtTick;
		}
	}

	private static final Map<UUID, PendingUnbind> PENDING_UNBINDS = new ConcurrentHashMap<UUID, PendingUnbind>();
	private static final Map<String, Long> LAST_NOTIFY_MS = new ConcurrentHashMap<String, Long>();
	private static final Map<UUID, Long> LAST_PUNISH_MS = new ConcurrentHashMap<UUID, Long>();

	private TrueSoulboundHelper() {
	}

	public static boolean canBind(ItemStack stack) {
		return stack != null && stack.getItem() != null && stack.stackSize == 1;
	}

	public static NBTTagCompound getSeal(ItemStack stack) {
		if (stack == null || !stack.hasTagCompound()) {
			return null;
		}
		NBTTagCompound tag = stack.getTagCompound();
		if (tag == null || !tag.hasKey(TAG_ROOT, 10)) {
			return null;
		}
		NBTTagCompound seal = tag.getCompoundTag(TAG_ROOT);
		if (!seal.hasKey(TAG_OWNER_UUID, 8) || !seal.hasKey(TAG_UID, 8)) {
			return null;
		}
		String uuidStr = seal.getString(TAG_OWNER_UUID);
		String uid = seal.getString(TAG_UID);
		if (uuidStr == null || uuidStr.isEmpty() || uid == null || uid.isEmpty()) {
			return null;
		}
		return seal;
	}

	public static boolean isTrueSoulbound(ItemStack stack) {
		return getSeal(stack) != null;
	}

	public static UUID getOwnerUUID(ItemStack stack) {
		NBTTagCompound seal = getSeal(stack);
		if (seal == null) {
			return null;
		}
		try {
			return UUID.fromString(seal.getString(TAG_OWNER_UUID));
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	public static String getOwnerName(ItemStack stack) {
		NBTTagCompound seal = getSeal(stack);
		if (seal == null) {
			return "";
		}
		String name = seal.getString(TAG_OWNER_NAME);
		return name != null ? name : "";
	}

	public static String getItemUID(ItemStack stack) {
		NBTTagCompound seal = getSeal(stack);
		return seal != null ? seal.getString(TAG_UID) : null;
	}

	public static long getBoundAt(ItemStack stack) {
		NBTTagCompound seal = getSeal(stack);
		return seal != null ? seal.getLong(TAG_BOUND_AT) : 0L;
	}

	public static boolean isOwner(ItemStack stack, Entity entity) {
		if (entity == null) {
			return false;
		}
		UUID owner = getOwnerUUID(stack);
		return owner != null && owner.equals(entity.getUniqueID());
	}

	public static boolean isOwnerOrUnbound(ItemStack stack, Entity entity) {
		if (!isTrueSoulbound(stack)) {
			return true;
		}
		return isOwner(stack, entity);
	}

	public static ItemStack bind(ItemStack stack, EntityPlayer owner, long worldTime) {
		if (owner == null) {
			return null;
		}
		return bind(stack, owner.getUniqueID(), owner.getCommandSenderName(), worldTime);
	}

	public static ItemStack bind(ItemStack stack, UUID ownerUUID, String ownerName, long worldTime) {
		if (!canBind(stack) || ownerUUID == null) {
			return null;
		}
		ItemStack result = stack.copy();
		NBTTagCompound root = result.getTagCompound();
		if (root == null) {
			root = new NBTTagCompound();
			result.setTagCompound(root);
		}
		NBTTagCompound seal = new NBTTagCompound();
		seal.setString(TAG_OWNER_UUID, ownerUUID.toString());
		seal.setString(TAG_OWNER_NAME, ownerName != null ? ownerName : "Unknown");
		seal.setString(TAG_UID, UUID.randomUUID().toString());
		seal.setLong(TAG_BOUND_AT, worldTime);
		root.setTag(TAG_ROOT, seal);
		return result;
	}

	public static boolean unbind(ItemStack stack) {
		if (!isTrueSoulbound(stack)) {
			return false;
		}
		String uid = getItemUID(stack);
		NBTTagCompound root = stack.getTagCompound();
		if (root == null) {
			return false;
		}
		root.removeTag(TAG_ROOT);
		if (root.hasNoTags()) {
			stack.setTagCompound(null);
		}
		if (uid != null) {
			TrueSoulboundHandler.forgetTrackedUID(uid);
		}
		return true;
	}

	public static boolean clampDurability(ItemStack stack) {
		if (stack == null || !isTrueSoulbound(stack) || !stack.isItemStackDamageable()) {
			return false;
		}
		int maxDamage = stack.getMaxDamage();
		if (maxDamage > 0 && stack.getItemDamage() >= maxDamage) {
			stack.setItemDamage(Math.max(0, maxDamage - 1));
			return true;
		}
		return false;
	}

	public static boolean inventoryContainsUID(InventoryPlayer inv, String uid) {
		return inventoryContainsUID(inv, uid, false);
	}

	public static boolean inventoryContainsUID(InventoryPlayer inv, String uid, boolean includeCursor) {
		if (inv == null || uid == null || uid.isEmpty()) {
			return false;
		}
		for (int i = 0; i < inv.mainInventory.length; i++) {
			ItemStack s = inv.mainInventory[i];
			if (s != null && s.stackSize > 0) {
				if (uid.equals(getItemUID(s)) || spellBookContainsUID(s, uid)) {
					return true;
				}
			}
		}
		for (int i = 0; i < inv.armorInventory.length; i++) {
			ItemStack s = inv.armorInventory[i];
			if (s != null && s.stackSize > 0 && uid.equals(getItemUID(s))) {
				return true;
			}
		}
		if (inv.player != null) {
			IInventory baubles = getPlayerBaubles(inv.player);
			if (baubles != null) {
				for (int i = 0; i < baubles.getSizeInventory(); i++) {
					ItemStack s = baubles.getStackInSlot(i);
					if (s != null && s.stackSize > 0 && uid.equals(getItemUID(s))) {
						return true;
					}
				}
			}
		}
		if (includeCursor) {
			ItemStack cursor = inv.getItemStack();
			if (cursor != null && cursor.stackSize > 0 && uid.equals(getItemUID(cursor))) {
				return true;
			}
		}
		return false;
	}

	public static IInventory getPlayerBaubles(EntityPlayer player) {
		if (player == null) {
			return null;
		}
		if (!baublesResolved) {
			synchronized (TrueSoulboundHelper.class) {
				if (!baublesResolved) {
					try {
						Class<?> api = Class.forName("baubles.api.BaublesApi");
						baublesGetMethod = api.getMethod("getBaubles", EntityPlayer.class);
					} catch (Throwable ignored) {
						baublesGetMethod = null;
					}
					baublesResolved = true;
				}
			}
		}
		if (baublesGetMethod == null) {
			return null;
		}
		try {
			Object res = baublesGetMethod.invoke(null, player);
			if (res instanceof IInventory) {
				return (IInventory) res;
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	public static boolean spellBookContainsUID(ItemStack bookStack, String uid) {
		if (bookStack == null || uid == null || uid.isEmpty() || !bookStack.hasTagCompound()) {
			return false;
		}
		NBTTagCompound tag = bookStack.getTagCompound();
		if (tag == null || !tag.hasKey("spell_book_inventory", 9)) {
			return false;
		}
		NBTTagList list = tag.getTagList("spell_book_inventory", 10);
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound entry = list.getCompoundTagAt(i);
			if (entry != null && entry.hasKey("data", 10)) {
				NBTTagCompound data = entry.getCompoundTag("data");
				if (data.hasKey(TAG_ROOT, 10) && uid.equals(data.getCompoundTag(TAG_ROOT).getString(TAG_UID))) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean deliverToPlayerInventory(EntityPlayer player, ItemStack stack, int preferredSlot) {
		if (player == null || player.inventory == null) {
			return false;
		}
		return deliverToInventory(player.inventory, player, stack, preferredSlot);
	}

	public static boolean deliverToInventory(InventoryPlayer inv, EntityPlayer player, ItemStack stack, int preferredSlot) {
		if (inv == null || stack == null || stack.stackSize <= 0) {
			return false;
		}
		String uid = getItemUID(stack);
		if (uid != null && inventoryContainsUID(inv, uid, false)) {
			finishInventoryDelivery(inv, player);
			return true;
		}

		ItemStack copy = stack.copy();
		copy.stackSize = 1;
		clampDurability(copy);

		if (preferredSlot >= 0 && preferredSlot < inv.mainInventory.length) {
			if (inv.mainInventory[preferredSlot] == null) {
				inv.mainInventory[preferredSlot] = copy;
				finishInventoryDelivery(inv, player);
				return true;
			}
		} else if (preferredSlot >= inv.mainInventory.length
				&& preferredSlot < inv.mainInventory.length + inv.armorInventory.length) {
			int armorIdx = preferredSlot - inv.mainInventory.length;
			int armorType = 3 - armorIdx;
			if (inv.armorInventory[armorIdx] == null
					&& copy.getItem() != null
					&& copy.getItem().isValidArmor(copy, armorType, player)) {
				inv.armorInventory[armorIdx] = copy;
				finishInventoryDelivery(inv, player);
				return true;
			}
		} else if (preferredSlot >= BAUBLES_SLOT_OFFSET && player != null) {
			int baubleIdx = preferredSlot - BAUBLES_SLOT_OFFSET;
			IInventory baubles = getPlayerBaubles(player);
			if (baubles != null && baubleIdx >= 0 && baubleIdx < baubles.getSizeInventory()
					&& baubles.getStackInSlot(baubleIdx) == null
					&& baubles.isItemValidForSlot(baubleIdx, copy)) {
				baubles.setInventorySlotContents(baubleIdx, copy);
				baubles.markDirty();
				finishInventoryDelivery(inv, player);
				return true;
			}
		}

		for (int i = 0; i < inv.mainInventory.length; i++) {
			if (inv.mainInventory[i] == null) {
				inv.mainInventory[i] = copy;
				finishInventoryDelivery(inv, player);
				return true;
			}
		}

		return false;
	}

	public static void syncPlayerContainers(EntityPlayer player) {
		if (player instanceof EntityPlayerMP) {
			EntityPlayerMP mp = (EntityPlayerMP) player;
			if (mp.openContainer != null && mp.openContainer != mp.inventoryContainer) {
				mp.sendContainerToPlayer(mp.openContainer);
			}
			if (mp.inventoryContainer != null) {
				mp.sendContainerToPlayer(mp.inventoryContainer);
			}
			mp.updateHeldItem();
		}
	}

	private static void finishInventoryDelivery(InventoryPlayer inv, EntityPlayer player) {
		inv.markDirty();
		syncPlayerContainers(player);
	}

	public static void recordPendingUnbind(UUID playerUUID, String uid, long currentTick) {
		if (playerUUID == null || uid == null || uid.isEmpty()) {
			return;
		}
		PENDING_UNBINDS.put(playerUUID, new PendingUnbind(uid, currentTick + UNBIND_CONFIRM_TICKS));
	}

	public static boolean consumePendingUnbind(UUID playerUUID, String uid, long currentTick) {
		if (playerUUID == null || uid == null || uid.isEmpty()) {
			return false;
		}
		PendingUnbind pending = PENDING_UNBINDS.get(playerUUID);
		if (pending == null) {
			return false;
		}
		if (currentTick > pending.expiresAtTick) {
			PENDING_UNBINDS.remove(playerUUID);
			return false;
		}
		if (!uid.equals(pending.uid)) {
			return false;
		}
		PENDING_UNBINDS.remove(playerUUID);
		return true;
	}

	public static void clearPendingUnbind(UUID playerUUID) {
		if (playerUUID != null) {
			PENDING_UNBINDS.remove(playerUUID);
		}
	}

	public static void punishThief(EntityLivingBase thief, World world) {
		if (thief == null || world == null || world.isRemote) {
			return;
		}
		UUID thiefId = thief.getUniqueID();
		long now = System.currentTimeMillis();
		Long last = LAST_PUNISH_MS.get(thiefId);
		if (last != null && (now - last.longValue()) < PUNISH_COOLDOWN_MS) {
			return;
		}
		LAST_PUNISH_MS.put(thiefId, Long.valueOf(now));

		thief.addPotionEffect(new PotionEffect(Potion.wither.id, 160, 1));
		thief.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 200, 3));
		thief.addPotionEffect(new PotionEffect(Potion.weakness.id, 200, 3));
		thief.addPotionEffect(new PotionEffect(Potion.blindness.id, 120, 0));

		try {
			ExtendedProperties props = ExtendedProperties.For(thief);
			if (props != null) {
				props.setCurrentMana(0f);
				props.setCurrentFatigue(props.getMaxFatigue());
				props.forceSync();
			}
		} catch (Throwable ignored) {
		}

		world.playSoundAtEntity(thief, "mob.endermen.scream", 1.0f, 0.6f);

		if (thief instanceof EntityPlayer) {
			notifyLocalized((EntityPlayer) thief, "thief_punish", EnumChatFormatting.DARK_RED, "am2.truesoulbound.thief");
		}
	}

	public static void notifyLocalized(EntityPlayer player, String channel, EnumChatFormatting color, String langKey, Object... args) {
		if (player == null || player.worldObj == null || player.worldObj.isRemote) {
			return;
		}
		String dedupKey = player.getUniqueID().toString() + ":" + channel;
		long now = System.currentTimeMillis();
		Long last = LAST_NOTIFY_MS.get(dedupKey);
		if (last != null && (now - last.longValue()) < NOTIFY_COOLDOWN_MS) {
			return;
		}
		LAST_NOTIFY_MS.put(dedupKey, Long.valueOf(now));

		String text = StatCollector.canTranslate(langKey)
				? StatCollector.translateToLocalFormatted(langKey, args)
				: formatFallback(langKey, args);
		player.addChatMessage(new ChatComponentText(color + text));
	}

	@SuppressWarnings("unchecked")
	public static EntityPlayerMP findOnlinePlayer(UUID playerUUID) {
		if (playerUUID == null) {
			return null;
		}
		try {
			MinecraftServer server = MinecraftServer.getServer();
			if (server == null || server.getConfigurationManager() == null) {
				return null;
			}
			for (Object obj : server.getConfigurationManager().playerEntityList) {
				if (obj instanceof EntityPlayerMP) {
					EntityPlayerMP mp = (EntityPlayerMP) obj;
					if (playerUUID.equals(mp.getUniqueID())) {
						return mp;
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	public static boolean rescueToOwnerOrVault(World world, ItemStack stack, int preferredSlot, boolean notifyOwner) {
		if (world == null || world.isRemote || stack == null || stack.stackSize <= 0 || !isTrueSoulbound(stack)) {
			return false;
		}
		UUID ownerUUID = getOwnerUUID(stack);
		if (ownerUUID == null) {
			return false;
		}

		EntityPlayerMP owner = findOnlinePlayer(ownerUUID);
		if (owner != null && owner.isEntityAlive()) {
			if (deliverToPlayerInventory(owner, stack, preferredSlot)) {
				TrueSoulboundVault vault = TrueSoulboundVault.get(world);
				if (vault != null) {
					String uid = getItemUID(stack);
					if (uid != null && vault.containsUID(uid)) {
						vault.deliverToPlayer(owner);
					}
				}
				TrueSoulboundVault.removePersistedEntry(owner, getItemUID(stack));
				if (notifyOwner) {
					notifyLocalized(owner, "returned", EnumChatFormatting.AQUA, "am2.truesoulbound.returned");
				}
				return true;
			}
		}

		TrueSoulboundVault vault = TrueSoulboundVault.get(world);
		if (vault != null && vault.store(ownerUUID, stack, preferredSlot)) {
			if (owner != null) {
				TrueSoulboundVault.storeInPersistedPlayer(owner, stack, preferredSlot);
				if (notifyOwner) {
					notifyLocalized(owner, "vault_stored", EnumChatFormatting.YELLOW, "am2.truesoulbound.vault_stored");
				}
			}
			return true;
		}
		return false;
	}

	private static String formatFallback(String langKey, Object... args) {
		if ("am2.truesoulbound.thief".equals(langKey)) {
			return "This True Soulbound relic rejects your soul and sears your mana!";
		}
		if ("am2.truesoulbound.container_blocked".equals(langKey)) {
			return "A True Soulbound item cannot be stored away from its master's soul.";
		}
		if ("am2.truesoulbound.unbind_warn".equals(langKey)) {
			return "Sneak and press Drop (Q) again within 3 seconds to UNBIND this True Soulbound item.";
		}
		if ("am2.truesoulbound.unbound".equals(langKey)) {
			return "True Soulbound seal removed from item.";
		}
		if ("am2.truesoulbound.returned".equals(langKey)) {
			return "Your True Soulbound relic returned to your grasp.";
		}
		if ("am2.truesoulbound.vault_stored".equals(langKey)) {
			return "Inventory full: a True Soulbound item is held in your Soul Vault until space opens.";
		}
		if ("am2.truesoulbound.ritual_missing_reagents".equals(langKey)) {
			return "Missing catalysts: drop 1 Pure Essence, 1 Ender Essence, and 1 Purified Vinteum.";
		}
		if ("am2.truesoulbound.ritual_no_target".equals(langKey)) {
			return "Drop 1 single item at the center alongside the 3 catalysts.";
		}
		if ("am2.truesoulbound.ritual_too_many_items".equals(langKey)) {
			return "Too many items in the ritual! Leave only the 3 catalysts and 1 target item.";
		}
		if ("am2.truesoulbound.ritual_already_yours".equals(langKey)) {
			return "This item is already True Soulbound to your soul.";
		}
		if ("am2.truesoulbound.ritual_owned_by_other".equals(langKey)) {
			return "This item is already bound to another soul!";
		}
		if ("am2.truesoulbound.ritual_invalid_target".equals(langKey)) {
			return "This item cannot be True Soulbound.";
		}
		if ("am2.truesoulbound.ritual_success".equals(langKey)) {
			String itemName = args != null && args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "item";
			return "True Soulbound sealed onto " + itemName + "!";
		}
		if ("am2.truesoulbound.cmd.no_item".equals(langKey)) {
			return "Hold your True Soulbound item in your main hand first.";
		}
		if ("am2.truesoulbound.cmd.not_bound".equals(langKey)) {
			return "Your held item is not True Soulbound.";
		}
		if ("am2.truesoulbound.cmd.not_owner".equals(langKey)) {
			String owner = args != null && args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "unknown";
			return "Only the soul owner (" + owner + ") can run this command on that item.";
		}
		if ("am2.truesoulbound.cmd.destroy_confirm".equals(langKey)) {
			return "To permanently destroy this True Soulbound item, run: /am2destroy confirm";
		}
		if ("am2.truesoulbound.cmd.destroyed".equals(langKey)) {
			String itemName = args != null && args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "item";
			return "True Soulbound item permanently destroyed: " + itemName + ".";
		}
		return args != null && args.length > 0 ? String.format(langKey, args) : langKey;
	}
}
