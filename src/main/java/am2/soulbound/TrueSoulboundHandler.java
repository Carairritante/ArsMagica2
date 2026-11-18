package am2.soulbound;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import am2.blocks.BlockLectern;
import am2.blocks.tileentities.TileEntityLectern;
import am2.containers.ContainerSpellBook;
import am2.containers.InventorySpellBook;
import am2.entities.EntityTrueSoulboundItem;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFlowerPot;
import net.minecraft.block.BlockJukebox;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemBed;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBoat;
import net.minecraft.item.ItemDoor;
import net.minecraft.item.ItemHangingEntity;
import net.minecraft.item.ItemMinecart;
import net.minecraft.item.ItemReed;
import net.minecraft.item.ItemSign;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;

public class TrueSoulboundHandler {

	public static final class TrackedSoulbound {
		public final String uid;
		public final ItemStack stack;
		public final int preferredSlot;

		public TrackedSoulbound(String uid, ItemStack stack, int preferredSlot) {
			this.uid = uid;
			this.stack = stack.copy();
			this.stack.stackSize = 1;
			this.preferredSlot = preferredSlot;
		}
	}

	private static final class BlockPosRecord {
		final int dim;
		final int x;
		final int y;
		final int z;
		final long tick;

		BlockPosRecord(int dim, int x, int y, int z, long tick) {
			this.dim = dim;
			this.x = x;
			this.y = y;
			this.z = z;
			this.tick = tick;
		}
	}

	private static final Map<UUID, Map<String, TrackedSoulbound>> LIVE_INVENTORY_SNAPSHOT =
			new ConcurrentHashMap<UUID, Map<String, TrackedSoulbound>>();
	private static final Set<String> FORGOTTEN_UIDS =
			 java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	private static final Map<UUID, BlockPosRecord> LAST_INTERACTED_BLOCK =
			new ConcurrentHashMap<UUID, BlockPosRecord>();
	private static final Map<Class<?>, Field[]> ENTITY_INVENTORY_FIELDS =
			new ConcurrentHashMap<Class<?>, Field[]>();
	private static final Field[] NO_FIELDS = new Field[0];
	private static final double NEARBY_ENTITY_PURGE_RADIUS = 16.0D;

	public static void forgetTrackedUID(String uid) {
		if (uid == null || uid.isEmpty()) {
			return;
		}
		FORGOTTEN_UIDS.add(uid);
		for (Map<String, TrackedSoulbound> playerMap : LIVE_INVENTORY_SNAPSHOT.values()) {
			if (playerMap != null) {
				playerMap.remove(uid);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onEntityJoinWorld(EntityJoinWorldEvent event) {
		if (event.world == null || event.world.isRemote || !(event.entity instanceof EntityItem)) {
			return;
		}
		if (event.entity instanceof EntityTrueSoulboundItem) {
			return;
		}
		EntityItem itemEntity = (EntityItem) event.entity;
		ItemStack stack = itemEntity.getEntityItem();
		if (!TrueSoulboundHelper.isTrueSoulbound(stack)) {
			return;
		}

		event.setCanceled(true);
		itemEntity.setDead();

		if (!TrueSoulboundHelper.rescueToOwnerOrVault(event.world, stack, -1, true)) {
			EntityTrueSoulboundItem replacement = new EntityTrueSoulboundItem(
					event.world, itemEntity.posX, itemEntity.posY, itemEntity.posZ, stack.copy());
			replacement.motionX = itemEntity.motionX;
			replacement.motionY = itemEntity.motionY;
			replacement.motionZ = itemEntity.motionZ;
			event.world.spawnEntityInWorld(replacement);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onItemExpire(ItemExpireEvent event) {
		if (event.entity == null || event.entity.worldObj == null || event.entity.worldObj.isRemote || event.entityItem == null) {
			return;
		}
		ItemStack stack = event.entityItem.getEntityItem();
		if (!TrueSoulboundHelper.isTrueSoulbound(stack)) {
			return;
		}
		event.extraLife = 6000;
		event.setCanceled(true);
		if (TrueSoulboundHelper.rescueToOwnerOrVault(event.entity.worldObj, stack, -1, true)) {
			event.entityItem.setDead();
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onItemToss(ItemTossEvent event) {
		if (event.player == null || event.player.worldObj == null || event.player.worldObj.isRemote || event.entityItem == null) {
			return;
		}
		ItemStack tossed = event.entityItem.getEntityItem();
		if (!TrueSoulboundHelper.isTrueSoulbound(tossed)) {
			return;
		}

		EntityPlayer player = event.player;
		World world = player.worldObj;
		String uid = TrueSoulboundHelper.getItemUID(tossed);

		if (player.inventory != null && uid != null && uid.equals(TrueSoulboundHelper.getItemUID(player.inventory.getItemStack()))) {
			player.inventory.setItemStack(null);
		}

		if (!TrueSoulboundHelper.isOwner(tossed, player)) {
			event.setCanceled(true);
			event.entityItem.setDead();
			TrueSoulboundHelper.punishThief(player, world);
			TrueSoulboundHelper.rescueToOwnerOrVault(world, tossed, -1, true);
			return;
		}

		boolean isExplicitDropKey = event.entityItem.func_145800_j() != null;

		if (isExplicitDropKey && player.isSneaking()
				&& TrueSoulboundHelper.consumePendingUnbind(player.getUniqueID(), uid, world.getTotalWorldTime())) {
			forgetTrackedUID(uid);
			TrueSoulboundVault vault = TrueSoulboundVault.get(world);
			if (vault != null) {
				vault.removeByUID(uid);
			}
			TrueSoulboundVault.removePersistedEntry(player, uid);
			TrueSoulboundHelper.unbind(tossed);
			event.entityItem.setEntityItemStack(tossed);
			TrueSoulboundHelper.notifyLocalized(player, "unbound", EnumChatFormatting.GOLD, "am2.truesoulbound.unbound");
			return;
		}

		event.setCanceled(true);
		event.entityItem.setDead();

		if (isExplicitDropKey && player.isSneaking()) {
			TrueSoulboundHelper.recordPendingUnbind(player.getUniqueID(), uid, world.getTotalWorldTime());
			TrueSoulboundHelper.notifyLocalized(player, "unbind_warn", EnumChatFormatting.YELLOW, "am2.truesoulbound.unbind_warn");
		} else {
			TrueSoulboundHelper.notifyLocalized(player, "returned", EnumChatFormatting.AQUA, "am2.truesoulbound.returned");
		}

		if (!TrueSoulboundHelper.deliverToPlayerInventory(player, tossed, player.inventory.currentItem)) {
			TrueSoulboundHelper.rescueToOwnerOrVault(world, tossed, player.inventory.currentItem, false);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onEntityItemPickup(EntityItemPickupEvent event) {
		if (event.entityPlayer == null || event.entityPlayer.worldObj == null || event.entityPlayer.worldObj.isRemote || event.item == null) {
			return;
		}
		ItemStack stack = event.item.getEntityItem();
		if (!TrueSoulboundHelper.isTrueSoulbound(stack)) {
			return;
		}
		if (!TrueSoulboundHelper.isOwner(stack, event.entityPlayer)) {
			event.setCanceled(true);
			event.item.setDead();
			TrueSoulboundHelper.punishThief(event.entityPlayer, event.entityPlayer.worldObj);
			TrueSoulboundHelper.rescueToOwnerOrVault(event.entityPlayer.worldObj, stack, -1, true);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onEntityInteract(EntityInteractEvent event) {
		if (event.entityPlayer == null) {
			return;
		}
		ItemStack held = event.entityPlayer.getHeldItem();
		if (!TrueSoulboundHelper.isTrueSoulbound(held)) {
			return;
		}
		if (!TrueSoulboundHelper.isOwner(held, event.entityPlayer)) {
			event.setCanceled(true);
			if (event.entityPlayer.worldObj != null && !event.entityPlayer.worldObj.isRemote) {
				int slot = event.entityPlayer.inventory.currentItem;
				event.entityPlayer.inventory.setInventorySlotContents(slot, null);
				event.entityPlayer.inventory.markDirty();
				TrueSoulboundHelper.punishThief(event.entityPlayer, event.entityPlayer.worldObj);
				TrueSoulboundHelper.rescueToOwnerOrVault(event.entityPlayer.worldObj, held, -1, true);
			}
			return;
		}
		if (event.target instanceof EntityItemFrame) {
			event.setCanceled(true);
			if (event.entityPlayer.worldObj != null && !event.entityPlayer.worldObj.isRemote) {
				TrueSoulboundHelper.notifyLocalized(event.entityPlayer, "container_blocked", EnumChatFormatting.RED, "am2.truesoulbound.container_blocked");
				TrueSoulboundHelper.syncPlayerContainers(event.entityPlayer);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.entityPlayer == null) {
			return;
		}
		ItemStack held = event.entityPlayer.getHeldItem();
		if (!TrueSoulboundHelper.isTrueSoulbound(held)) {
			return;
		}
		if (!TrueSoulboundHelper.isOwner(held, event.entityPlayer)) {
			event.setCanceled(true);
			if (event.world != null && !event.world.isRemote) {
				int slot = event.entityPlayer.inventory.currentItem;
				event.entityPlayer.inventory.setInventorySlotContents(slot, null);
				event.entityPlayer.inventory.markDirty();
				TrueSoulboundHelper.punishThief(event.entityPlayer, event.world);
				TrueSoulboundHelper.rescueToOwnerOrVault(event.world, held, -1, true);
			}
			return;
		}

		if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK && event.world != null) {
			if (!event.world.isRemote) {
				LAST_INTERACTED_BLOCK.put(
						event.entityPlayer.getUniqueID(),
						new BlockPosRecord(event.world.provider.dimensionId, event.x, event.y, event.z, event.world.getTotalWorldTime()));
			}
			Block clicked = event.world.getBlock(event.x, event.y, event.z);
			if (clicked instanceof BlockLectern || clicked instanceof BlockJukebox || clicked instanceof BlockFlowerPot) {
				event.setCanceled(true);
				if (!event.world.isRemote) {
					TrueSoulboundHelper.notifyLocalized(event.entityPlayer, "container_blocked", EnumChatFormatting.RED, "am2.truesoulbound.container_blocked");
					TrueSoulboundHelper.syncPlayerContainers(event.entityPlayer);
				}
				return;
			}
			if (isWorldPlaceableItem(held)) {
				event.useItem = Event.Result.DENY;
				if (!event.world.isRemote) {
					TrueSoulboundHelper.notifyLocalized(event.entityPlayer, "container_blocked", EnumChatFormatting.RED, "am2.truesoulbound.container_blocked");
					TrueSoulboundHelper.syncPlayerContainers(event.entityPlayer);
				}
			}
		}
	}

	private static boolean isWorldPlaceableItem(ItemStack stack) {
		if (stack == null || stack.getItem() == null) {
			return false;
		}
		Object item = stack.getItem();
		return item instanceof ItemBlock
				|| item instanceof ItemReed
				|| item instanceof ItemSkull
				|| item instanceof ItemHangingEntity
				|| item instanceof ItemDoor
				|| item instanceof ItemBed
				|| item instanceof ItemSign
				|| item instanceof ItemMinecart
				|| item instanceof ItemBoat;
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onPlayerDestroyItem(PlayerDestroyItemEvent event) {
		if (event.entityPlayer == null || event.entityPlayer.worldObj == null || event.entityPlayer.worldObj.isRemote) {
			return;
		}
		if (TrueSoulboundHelper.isTrueSoulbound(event.original) && TrueSoulboundHelper.isOwner(event.original, event.entityPlayer)) {
			ItemStack rescued = event.original.copy();
			rescued.stackSize = 1;
			TrueSoulboundHelper.clampDurability(rescued);
			if (!TrueSoulboundHelper.deliverToPlayerInventory(event.entityPlayer, rescued, event.entityPlayer.inventory.currentItem)) {
				TrueSoulboundHelper.rescueToOwnerOrVault(event.entityPlayer.worldObj, rescued, event.entityPlayer.inventory.currentItem, true);
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onPlayerUseItemStart(PlayerUseItemEvent.Start event) {
		if (event.entityPlayer == null) {
			return;
		}
		if (TrueSoulboundHelper.isTrueSoulbound(event.item) && !TrueSoulboundHelper.isOwner(event.item, event.entityPlayer)) {
			event.setCanceled(true);
			confiscateAndRescueHeldItem(event.entityPlayer, event.item);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onPlayerUseItemTick(PlayerUseItemEvent.Tick event) {
		if (event.entityPlayer == null) {
			return;
		}
		if (TrueSoulboundHelper.isTrueSoulbound(event.item) && !TrueSoulboundHelper.isOwner(event.item, event.entityPlayer)) {
			event.setCanceled(true);
			confiscateAndRescueHeldItem(event.entityPlayer, event.item);
		}
	}

	private static void confiscateAndRescueHeldItem(EntityPlayer player, ItemStack item) {
		if (player == null || player.worldObj == null || player.worldObj.isRemote || item == null) {
			return;
		}
		if (player.inventory != null) {
			int slot = player.inventory.currentItem;
			if (slot >= 0 && slot < player.inventory.mainInventory.length
					&& TrueSoulboundHelper.getItemUID(item) != null
					&& TrueSoulboundHelper.getItemUID(item).equals(TrueSoulboundHelper.getItemUID(player.inventory.mainInventory[slot]))) {
				player.inventory.setInventorySlotContents(slot, null);
				player.inventory.markDirty();
			}
		}
		TrueSoulboundHelper.punishThief(player, player.worldObj);
		TrueSoulboundHelper.rescueToOwnerOrVault(player.worldObj, item, -1, true);
		TrueSoulboundHelper.syncPlayerContainers(player);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onAttackEntity(AttackEntityEvent event) {
		if (event.entityPlayer == null) {
			return;
		}
		ItemStack held = event.entityPlayer.getHeldItem();
		if (TrueSoulboundHelper.isTrueSoulbound(held) && !TrueSoulboundHelper.isOwner(held, event.entityPlayer)) {
			event.setCanceled(true);
			if (event.entityPlayer.worldObj != null && !event.entityPlayer.worldObj.isRemote) {
				int slot = event.entityPlayer.inventory.currentItem;
				event.entityPlayer.inventory.setInventorySlotContents(slot, null);
				event.entityPlayer.inventory.markDirty();
				TrueSoulboundHelper.punishThief(event.entityPlayer, event.entityPlayer.worldObj);
				TrueSoulboundHelper.rescueToOwnerOrVault(event.entityPlayer.worldObj, held, -1, true);
			}
		}
	}

	@SubscribeEvent
	public void onPlayerTick(PlayerTickEvent event) {
		if (event.side != Side.SERVER || event.phase != Phase.END || event.player == null || event.player.worldObj == null) {
			return;
		}
		EntityPlayer player = event.player;
		enforceInventoryOwnership(player);
		enforceExternalContainerBlock(player);

		if (player.isEntityAlive()) {
			reconcileLiveInventorySnapshot(player);
			if (player.ticksExisted % 20 == 0) {
				deliverVaultForPlayer(player);
			}
		}
	}

	@SubscribeEvent
	public void onLivingUpdate(LivingUpdateEvent event) {
		EntityLivingBase living = event.entityLiving;
		if (living == null || living instanceof EntityPlayer || living.worldObj == null || living.worldObj.isRemote) {
			return;
		}
		if (living.ticksExisted % 10 != 0) {
			return;
		}
		purgeStolenSoulboundFromEntity(living, null, true, true);
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onNonPlayerLivingDeath(LivingDeathEvent event) {
		EntityLivingBase living = event.entityLiving;
		if (living == null || living instanceof EntityPlayer || living.worldObj == null || living.worldObj.isRemote) {
			return;
		}
		purgeStolenSoulboundFromEntity(living, null, true, false);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onLivingDeath(LivingDeathEvent event) {
		if (event.isCanceled() || !(event.entityLiving instanceof EntityPlayer)) {
			return;
		}
		EntityPlayer player = (EntityPlayer) event.entityLiving;
		if (player.worldObj == null || player.worldObj.isRemote) {
			return;
		}
		boolean keepInventory = player.worldObj.getGameRules().getGameRuleBooleanValue("keepInventory");
		capturePlayerSoulboundItems(player, !keepInventory);
		LIVE_INVENTORY_SNAPSHOT.remove(player.getUniqueID());
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onPlayerDrops(PlayerDropsEvent event) {
		if (event.entityPlayer == null || event.entityPlayer.worldObj == null || event.entityPlayer.worldObj.isRemote || event.drops == null) {
			return;
		}
		World world = event.entityPlayer.worldObj;
		TrueSoulboundVault vault = TrueSoulboundVault.get(world);
		Iterator<EntityItem> it = event.drops.iterator();
		while (it.hasNext()) {
			EntityItem ei = it.next();
			if (ei == null) {
				continue;
			}
			ItemStack stack = ei.getEntityItem();
			if (!TrueSoulboundHelper.isTrueSoulbound(stack)) {
				continue;
			}
			UUID ownerUUID = TrueSoulboundHelper.getOwnerUUID(stack);
			if (ownerUUID != null) {
				if (vault != null) {
					vault.store(ownerUUID, stack, -1);
				}
				if (ownerUUID.equals(event.entityPlayer.getUniqueID())) {
					TrueSoulboundVault.storeInPersistedPlayer(event.entityPlayer, stack, -1);
				} else {
					TrueSoulboundHelper.rescueToOwnerOrVault(world, stack, -1, true);
				}
			}
			it.remove();
			ei.setDead();
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onPlayerClone(PlayerEvent.Clone event) {
		if (event.entityPlayer == null || event.original == null || event.entityPlayer.worldObj == null || event.entityPlayer.worldObj.isRemote) {
			return;
		}
		copyPersistedSoulboundTag(event.original, event.entityPlayer);
		deliverVaultForPlayer(event.entityPlayer);
		updateSnapshotFromInventory(event.entityPlayer);
	}

	@SubscribeEvent
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		if (event.player != null && event.player.worldObj != null && !event.player.worldObj.isRemote) {
			deliverVaultForPlayer(event.player);
			updateSnapshotFromInventory(event.player);
		}
	}

	@SubscribeEvent
	public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
		if (event.player != null && event.player.worldObj != null && !event.player.worldObj.isRemote) {
			deliverVaultForPlayer(event.player);
			updateSnapshotFromInventory(event.player);
		}
	}

	@SubscribeEvent
	public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
		if (event.player != null) {
			LIVE_INVENTORY_SNAPSHOT.remove(event.player.getUniqueID());
			LAST_INTERACTED_BLOCK.remove(event.player.getUniqueID());
		}
	}

	public static void enforceInventoryOwnership(EntityPlayer player) {
		if (player == null || player.inventory == null || player.worldObj == null || player.worldObj.isRemote) {
			return;
		}
		InventoryPlayer inv = player.inventory;
		boolean dirty = false;

		for (int i = 0; i < inv.mainInventory.length; i++) {
			ItemStack stack = inv.mainInventory[i];
			if (TrueSoulboundHelper.isTrueSoulbound(stack)) {
				if (!TrueSoulboundHelper.isOwner(stack, player)) {
					inv.mainInventory[i] = null;
					dirty = true;
					TrueSoulboundHelper.punishThief(player, player.worldObj);
					TrueSoulboundHelper.rescueToOwnerOrVault(player.worldObj, stack, -1, true);
				} else if (TrueSoulboundHelper.clampDurability(stack)) {
					dirty = true;
				}
			}
		}

		for (int i = 0; i < inv.armorInventory.length; i++) {
			ItemStack stack = inv.armorInventory[i];
			if (TrueSoulboundHelper.isTrueSoulbound(stack)) {
				if (!TrueSoulboundHelper.isOwner(stack, player)) {
					inv.armorInventory[i] = null;
					dirty = true;
					TrueSoulboundHelper.punishThief(player, player.worldObj);
					TrueSoulboundHelper.rescueToOwnerOrVault(player.worldObj, stack, -1, true);
				} else if (TrueSoulboundHelper.clampDurability(stack)) {
					dirty = true;
				}
			}
		}

		IInventory baubles = TrueSoulboundHelper.getPlayerBaubles(player);
		if (baubles != null) {
			boolean baublesDirty = false;
			for (int i = 0; i < baubles.getSizeInventory(); i++) {
				ItemStack stack = baubles.getStackInSlot(i);
				if (TrueSoulboundHelper.isTrueSoulbound(stack)) {
					if (!TrueSoulboundHelper.isOwner(stack, player)) {
						baubles.setInventorySlotContents(i, null);
						baublesDirty = true;
						dirty = true;
						TrueSoulboundHelper.punishThief(player, player.worldObj);
						TrueSoulboundHelper.rescueToOwnerOrVault(player.worldObj, stack, -1, true);
					} else if (TrueSoulboundHelper.clampDurability(stack)) {
						baublesDirty = true;
						dirty = true;
					}
				}
			}
			if (baublesDirty) {
				baubles.markDirty();
			}
		}

		ItemStack cursor = inv.getItemStack();
		if (TrueSoulboundHelper.isTrueSoulbound(cursor) && !TrueSoulboundHelper.isOwner(cursor, player)) {
			inv.setItemStack(null);
			dirty = true;
			TrueSoulboundHelper.punishThief(player, player.worldObj);
			TrueSoulboundHelper.rescueToOwnerOrVault(player.worldObj, cursor, -1, true);
		}

		if (dirty) {
			inv.markDirty();
			TrueSoulboundHelper.syncPlayerContainers(player);
		}
	}

	private static boolean isVirtualNetworkContainer(Container container) {
		if (container == null) {
			return false;
		}
		String cls = container.getClass().getName();
		if (cls.startsWith("appeng.") || cls.startsWith("thaumicenergistics.") || cls.startsWith("com.glodblock.")) {
			return true;
		}
		try {
			container.getClass().getMethod("getItemMonitor");
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static void enforceExternalContainerBlock(EntityPlayer player) {
		if (player == null || player.openContainer == null || player.openContainer == player.inventoryContainer
				|| player.worldObj == null || player.worldObj.isRemote) {
			return;
		}

		boolean isOwnSpellBookGui = (player.openContainer instanceof ContainerSpellBook)
				&& TrueSoulboundHelper.isOwnerOrUnbound(player.getCurrentEquippedItem(), player);
		IInventory playerBaubles = TrueSoulboundHelper.getPlayerBaubles(player);

		if (isVirtualNetworkContainer(player.openContainer) && player.inventory != null) {
			ItemStack cursor = player.inventory.getItemStack();
			if (TrueSoulboundHelper.isTrueSoulbound(cursor)) {
				player.inventory.setItemStack(null);
				if (TrueSoulboundHelper.isOwner(cursor, player)) {
					TrueSoulboundHelper.notifyLocalized(player, "container_blocked", EnumChatFormatting.RED, "am2.truesoulbound.container_blocked");
					if (!TrueSoulboundHelper.deliverToPlayerInventory(player, cursor, -1)) {
						TrueSoulboundHelper.rescueToOwnerOrVault(player.worldObj, cursor, -1, false);
					}
				} else {
					TrueSoulboundHelper.punishThief(player, player.worldObj);
					TrueSoulboundHelper.rescueToOwnerOrVault(player.worldObj, cursor, -1, true);
				}
				TrueSoulboundHelper.syncPlayerContainers(player);
			}
		}

		for (Object obj : player.openContainer.inventorySlots) {
			if (!(obj instanceof Slot)) {
				continue;
			}
			Slot slot = (Slot) obj;
			IInventory slotInv = slot.inventory;
			if (slotInv == null) {
				continue;
			}
			ItemStack stack = slot.getStack();
			if (!TrueSoulboundHelper.isTrueSoulbound(stack)) {
				continue;
			}

			if (slotInv instanceof InventoryPlayer) {
				EntityPlayer slotOwner = ((InventoryPlayer) slotInv).player;
				if (slotOwner != null && !TrueSoulboundHelper.isOwner(stack, slotOwner)) {
					slot.putStack(null);
					slot.onSlotChanged();
					TrueSoulboundHelper.punishThief(player, player.worldObj);
					TrueSoulboundHelper.rescueToOwnerOrVault(player.worldObj, stack, -1, true);
				}
				continue;
			}

			if (playerBaubles != null && slotInv == playerBaubles) {
				if (!TrueSoulboundHelper.isOwner(stack, player)) {
					slot.putStack(null);
					slot.onSlotChanged();
					TrueSoulboundHelper.punishThief(player, player.worldObj);
					TrueSoulboundHelper.rescueToOwnerOrVault(player.worldObj, stack, -1, true);
				}
				continue;
			}

			if (isOwnSpellBookGui && slotInv instanceof InventorySpellBook && TrueSoulboundHelper.isOwner(stack, player)) {
				continue;
			}

			slot.putStack(null);
			slot.onSlotChanged();
			if (TrueSoulboundHelper.isOwner(stack, player)) {
				TrueSoulboundHelper.notifyLocalized(player, "container_blocked", EnumChatFormatting.RED, "am2.truesoulbound.container_blocked");
				if (!TrueSoulboundHelper.deliverToPlayerInventory(player, stack, -1)) {
					TrueSoulboundHelper.rescueToOwnerOrVault(player.worldObj, stack, -1, false);
				}
			} else {
				TrueSoulboundHelper.punishThief(player, player.worldObj);
				TrueSoulboundHelper.rescueToOwnerOrVault(player.worldObj, stack, -1, true);
			}
		}
	}

	public static void reconcileLiveInventorySnapshot(EntityPlayer player) {
		if (player == null || player.inventory == null || player.worldObj == null || player.worldObj.isRemote || !player.isEntityAlive()) {
			return;
		}
		UUID playerUUID = player.getUniqueID();
		Map<String, TrackedSoulbound> previous = LIVE_INVENTORY_SNAPSHOT.get(playerUUID);
		if (previous != null && !previous.isEmpty()) {
			TrueSoulboundVault vault = TrueSoulboundVault.get(player.worldObj);
			boolean allowCursor = !isVirtualNetworkContainer(player.openContainer);

			for (TrackedSoulbound tracked : previous.values()) {
				String uid = tracked.uid;
				if (uid == null || FORGOTTEN_UIDS.remove(uid)) {
					continue;
				}
				if (TrueSoulboundHelper.inventoryContainsUID(player.inventory, uid, allowCursor)) {
					continue;
				}
				if (vault != null && vault.containsUID(uid)) {
					continue;
				}
				if (isWorldEntityPresentForUID(player.worldObj, uid)) {
					continue;
				}

				purgeFromAE2Container(player.openContainer, tracked.stack);
				purgeFromLastInteractedTile(player, uid);
				purgeFromNearbyEntities(player, uid);

				TrueSoulboundHelper.notifyLocalized(player, "container_blocked", EnumChatFormatting.RED, "am2.truesoulbound.container_blocked");
				if (!TrueSoulboundHelper.deliverToPlayerInventory(player, tracked.stack, tracked.preferredSlot)) {
					TrueSoulboundHelper.rescueToOwnerOrVault(player.worldObj, tracked.stack, tracked.preferredSlot, false);
				}
			}
		}
		updateSnapshotFromInventory(player);
	}

	private static void updateSnapshotFromInventory(EntityPlayer player) {
		if (player == null || player.inventory == null) {
			return;
		}
		InventoryPlayer inv = player.inventory;
		Map<String, TrackedSoulbound> current = new LinkedHashMap<String, TrackedSoulbound>();

		for (int i = 0; i < inv.mainInventory.length; i++) {
			ItemStack s = inv.mainInventory[i];
			if (s != null && s.stackSize > 0 && TrueSoulboundHelper.isTrueSoulbound(s) && TrueSoulboundHelper.isOwner(s, player)) {
				String uid = TrueSoulboundHelper.getItemUID(s);
				if (uid != null) {
					FORGOTTEN_UIDS.remove(uid);
					current.put(uid, new TrackedSoulbound(uid, s, i));
				}
			}
		}
		for (int i = 0; i < inv.armorInventory.length; i++) {
			ItemStack s = inv.armorInventory[i];
			if (s != null && s.stackSize > 0 && TrueSoulboundHelper.isTrueSoulbound(s) && TrueSoulboundHelper.isOwner(s, player)) {
				String uid = TrueSoulboundHelper.getItemUID(s);
				if (uid != null) {
					FORGOTTEN_UIDS.remove(uid);
					current.put(uid, new TrackedSoulbound(uid, s, inv.mainInventory.length + i));
				}
			}
		}
		IInventory baubles = TrueSoulboundHelper.getPlayerBaubles(player);
		if (baubles != null) {
			for (int i = 0; i < baubles.getSizeInventory(); i++) {
				ItemStack s = baubles.getStackInSlot(i);
				if (s != null && s.stackSize > 0 && TrueSoulboundHelper.isTrueSoulbound(s) && TrueSoulboundHelper.isOwner(s, player)) {
					String uid = TrueSoulboundHelper.getItemUID(s);
					if (uid != null) {
						FORGOTTEN_UIDS.remove(uid);
						current.put(uid, new TrackedSoulbound(uid, s, TrueSoulboundHelper.BAUBLES_SLOT_OFFSET + i));
					}
				}
			}
		}
		ItemStack cursor = inv.getItemStack();
		if (cursor != null && cursor.stackSize > 0 && TrueSoulboundHelper.isTrueSoulbound(cursor) && TrueSoulboundHelper.isOwner(cursor, player)) {
			String uid = TrueSoulboundHelper.getItemUID(cursor);
			if (uid != null && !current.containsKey(uid)) {
				FORGOTTEN_UIDS.remove(uid);
				current.put(uid, new TrackedSoulbound(uid, cursor, inv.currentItem));
			}
		}

		LIVE_INVENTORY_SNAPSHOT.put(player.getUniqueID(), current);
	}

	private static boolean isWorldEntityPresentForUID(World world, String uid) {
		if (world == null || world.loadedEntityList == null || uid == null) {
			return false;
		}
		for (Object obj : world.loadedEntityList) {
			if (obj instanceof EntityItem) {
				EntityItem ei = (EntityItem) obj;
				if (!ei.isDead && uid.equals(TrueSoulboundHelper.getItemUID(ei.getEntityItem()))) {
					return true;
				}
			}
		}
		return false;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void purgeFromAE2Container(Container container, ItemStack stack) {
		if (container == null || stack == null) {
			return;
		}
		try {
			Method getItemMonitor = container.getClass().getMethod("getItemMonitor");
			Method getActionSource = container.getClass().getMethod("getActionSource");
			Object monitor = getItemMonitor.invoke(container);
			Object actionSource = getActionSource.invoke(container);
			if (monitor == null || actionSource == null) {
				return;
			}
			Class<?> aeApiClass = Class.forName("appeng.api.AEApi");
			Object api = aeApiClass.getMethod("instance").invoke(null);
			Object storageHelper = api.getClass().getMethod("storage").invoke(api);
			ItemStack one = stack.copy();
			one.stackSize = 1;
			Object aeStack = storageHelper.getClass().getMethod("createItemStack", ItemStack.class).invoke(storageHelper, one);
			Class<? extends Enum> actionableClass = (Class<? extends Enum>) Class.forName("appeng.api.config.Actionable");
			Object modulate = Enum.valueOf(actionableClass, "MODULATE");
			for (Method m : monitor.getClass().getMethods()) {
				if ("extractItems".equals(m.getName()) && m.getParameterTypes().length == 3) {
					m.invoke(monitor, aeStack, modulate, actionSource);
					break;
				}
			}
		} catch (Throwable ignored) {
		}
	}

	private static void purgeFromLastInteractedTile(EntityPlayer player, String uid) {
		if (player == null || player.worldObj == null || uid == null) {
			return;
		}
		BlockPosRecord rec = LAST_INTERACTED_BLOCK.get(player.getUniqueID());
		if (rec == null || rec.dim != player.worldObj.provider.dimensionId
				|| (player.worldObj.getTotalWorldTime() - rec.tick) > 20L) {
			return;
		}
		if (!player.worldObj.blockExists(rec.x, rec.y, rec.z)) {
			return;
		}
		TileEntity te = player.worldObj.getTileEntity(rec.x, rec.y, rec.z);
		if (te instanceof TileEntityLectern) {
			TileEntityLectern lectern = (TileEntityLectern) te;
			if (lectern.hasStack() && uid.equals(TrueSoulboundHelper.getItemUID(lectern.getStack()))) {
				lectern.setStack(null);
			}
		}
		if (te instanceof IInventory) {
			IInventory inv = (IInventory) te;
			boolean changed = false;
			for (int i = 0; i < inv.getSizeInventory(); i++) {
				ItemStack slotStack = inv.getStackInSlot(i);
				if (uid.equals(TrueSoulboundHelper.getItemUID(slotStack))) {
					inv.setInventorySlotContents(i, null);
					changed = true;
				}
			}
			if (changed) {
				inv.markDirty();
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void purgeFromNearbyEntities(EntityPlayer player, String uid) {
		if (player == null || player.worldObj == null || player.worldObj.isRemote || player.boundingBox == null || uid == null) {
			return;
		}
		List<EntityLivingBase> nearby = player.worldObj.getEntitiesWithinAABB(
				EntityLivingBase.class,
				player.boundingBox.expand(NEARBY_ENTITY_PURGE_RADIUS, NEARBY_ENTITY_PURGE_RADIUS, NEARBY_ENTITY_PURGE_RADIUS));
		if (nearby == null || nearby.isEmpty()) {
			return;
		}
		for (EntityLivingBase living : nearby) {
			if (living == null || living instanceof EntityPlayer) {
				continue;
			}
			purgeStolenSoulboundFromEntity(living, uid, false, true);
		}
	}

	static int purgeStolenSoulboundFromEntity(EntityLivingBase living, String onlyUid, boolean rescueToOwner, boolean punishThief) {
		if (living == null || living instanceof EntityPlayer) {
			return 0;
		}
		int purged = 0;

		for (int i = 0; i < 5; i++) {
			ItemStack equipped = living.getEquipmentInSlot(i);
			if (matchesStolenSoulbound(equipped, living, onlyUid)) {
				living.setCurrentItemOrArmor(i, null);
				purged++;
				handlePurgedMobStack(living, equipped, rescueToOwner, punishThief);
			}
		}

		if (living instanceof IInventory) {
			purged += purgeFromInventoryObject(living, (IInventory) living, onlyUid, rescueToOwner, punishThief);
		}

		Field[] fields = resolveEntityInventoryFields(living.getClass());
		for (Field field : fields) {
			try {
				Object value = field.get(living);
				if (value == null) {
					continue;
				}
				if (value instanceof ItemStack[]) {
					ItemStack[] array = (ItemStack[]) value;
					for (int i = 0; i < array.length; i++) {
						ItemStack stack = array[i];
						if (matchesStolenSoulbound(stack, living, onlyUid)) {
							array[i] = null;
							purged++;
							handlePurgedMobStack(living, stack, rescueToOwner, punishThief);
						}
					}
				} else if (value instanceof IInventory) {
					purged += purgeFromInventoryObject(living, (IInventory) value, onlyUid, rescueToOwner, punishThief);
				} else if (value instanceof ItemStack) {
					ItemStack stack = (ItemStack) value;
					if (matchesStolenSoulbound(stack, living, onlyUid)) {
						field.set(living, null);
						purged++;
						handlePurgedMobStack(living, stack, rescueToOwner, punishThief);
					}
				}
			} catch (Throwable ignored) {
			}
		}

		return purged;
	}

	private static int purgeFromInventoryObject(EntityLivingBase living, IInventory inv, String onlyUid, boolean rescueToOwner, boolean punishThief) {
		if (inv == null) {
			return 0;
		}
		int purged = 0;
		int size = inv.getSizeInventory();
		for (int i = 0; i < size; i++) {
			ItemStack stack = inv.getStackInSlot(i);
			if (matchesStolenSoulbound(stack, living, onlyUid)) {
				inv.setInventorySlotContents(i, null);
				purged++;
				handlePurgedMobStack(living, stack, rescueToOwner, punishThief);
			}
		}
		if (purged > 0) {
			inv.markDirty();
		}
		return purged;
	}

	private static boolean matchesStolenSoulbound(ItemStack stack, EntityLivingBase holder, String onlyUid) {
		if (!TrueSoulboundHelper.isTrueSoulbound(stack) || TrueSoulboundHelper.isOwner(stack, holder)) {
			return false;
		}
		return onlyUid == null || onlyUid.equals(TrueSoulboundHelper.getItemUID(stack));
	}

	private static void handlePurgedMobStack(EntityLivingBase living, ItemStack stack, boolean rescueToOwner, boolean punishThief) {
		if (living.worldObj == null || living.worldObj.isRemote) {
			return;
		}
		if (punishThief) {
			TrueSoulboundHelper.punishThief(living, living.worldObj);
		}
		if (rescueToOwner) {
			TrueSoulboundHelper.rescueToOwnerOrVault(living.worldObj, stack, -1, true);
		}
	}

	private static Field[] resolveEntityInventoryFields(Class<?> entityClass) {
		if (entityClass == null) {
			return NO_FIELDS;
		}
		Field[] cached = ENTITY_INVENTORY_FIELDS.get(entityClass);
		if (cached != null) {
			return cached;
		}
		List<Field> found = new ArrayList<Field>();
		for (Class<?> c = entityClass;
				c != null && c != EntityLiving.class && c != EntityLivingBase.class && c != Entity.class && c != Object.class;
				c = c.getSuperclass()) {
			Field[] declared;
			try {
				declared = c.getDeclaredFields();
			} catch (Throwable ignored) {
				continue;
			}
			for (Field f : declared) {
				int mods = f.getModifiers();
				if (Modifier.isStatic(mods)) {
					continue;
				}
				Class<?> type = f.getType();
				boolean isItemArray = type == ItemStack[].class;
				boolean isInv = IInventory.class.isAssignableFrom(type);
				boolean isMutableStack = type == ItemStack.class && !Modifier.isFinal(mods);
				if (!isItemArray && !isInv && !isMutableStack) {
					continue;
				}
				try {
					f.setAccessible(true);
					found.add(f);
				} catch (Throwable ignored) {
				}
			}
		}
		Field[] result = found.isEmpty() ? NO_FIELDS : found.toArray(new Field[found.size()]);
		ENTITY_INVENTORY_FIELDS.put(entityClass, result);
		return result;
	}

	public static void capturePlayerSoulboundItems(EntityPlayer player, boolean removeFromInventory) {
		if (player == null || player.inventory == null || player.worldObj == null || player.worldObj.isRemote) {
			return;
		}
		InventoryPlayer inv = player.inventory;
		TrueSoulboundVault vault = TrueSoulboundVault.get(player.worldObj);
		boolean dirty = false;

		for (int i = 0; i < inv.mainInventory.length; i++) {
			ItemStack stack = inv.mainInventory[i];
			if (!TrueSoulboundHelper.isTrueSoulbound(stack)) {
				continue;
			}
			dirty |= storeDeathStack(player, vault, stack, i);
			if (removeFromInventory) {
				inv.mainInventory[i] = null;
			}
		}

		for (int i = 0; i < inv.armorInventory.length; i++) {
			ItemStack stack = inv.armorInventory[i];
			if (!TrueSoulboundHelper.isTrueSoulbound(stack)) {
				continue;
			}
			int slotIndex = inv.mainInventory.length + i;
			dirty |= storeDeathStack(player, vault, stack, slotIndex);
			if (removeFromInventory) {
				inv.armorInventory[i] = null;
			}
		}

		IInventory baubles = TrueSoulboundHelper.getPlayerBaubles(player);
		if (baubles != null) {
			boolean baublesDirty = false;
			for (int i = 0; i < baubles.getSizeInventory(); i++) {
				ItemStack stack = baubles.getStackInSlot(i);
				if (!TrueSoulboundHelper.isTrueSoulbound(stack)) {
					continue;
				}
				int slotIndex = TrueSoulboundHelper.BAUBLES_SLOT_OFFSET + i;
				dirty |= storeDeathStack(player, vault, stack, slotIndex);
				if (removeFromInventory) {
					baubles.setInventorySlotContents(i, null);
					baublesDirty = true;
				}
			}
			if (baublesDirty) {
				baubles.markDirty();
			}
		}

		ItemStack cursor = inv.getItemStack();
		if (TrueSoulboundHelper.isTrueSoulbound(cursor)) {
			dirty |= storeDeathStack(player, vault, cursor, -1);
			if (removeFromInventory) {
				inv.setItemStack(null);
			}
		}

		if (dirty && removeFromInventory) {
			inv.markDirty();
		}
	}

	private static boolean storeDeathStack(EntityPlayer player, TrueSoulboundVault vault, ItemStack stack, int slotIndex) {
		UUID ownerUUID = TrueSoulboundHelper.getOwnerUUID(stack);
		if (ownerUUID == null) {
			return false;
		}
		if (vault != null) {
			vault.store(ownerUUID, stack, ownerUUID.equals(player.getUniqueID()) ? slotIndex : -1);
		}
		if (ownerUUID.equals(player.getUniqueID())) {
			TrueSoulboundVault.storeInPersistedPlayer(player, stack, slotIndex);
		} else {
			TrueSoulboundHelper.rescueToOwnerOrVault(player.worldObj, stack, -1, true);
		}
		return true;
	}

	private static void copyPersistedSoulboundTag(EntityPlayer original, EntityPlayer target) {
		NBTTagCompound origData = original.getEntityData();
		if (origData == null || !origData.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) {
			return;
		}
		NBTTagCompound origPersisted = origData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
		if (!origPersisted.hasKey(TrueSoulboundVault.PERSISTED_KEY, 9)) {
			return;
		}
		NBTTagCompound targetData = target.getEntityData();
		if (!targetData.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) {
			targetData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
		}
		targetData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).setTag(
				TrueSoulboundVault.PERSISTED_KEY,
				origPersisted.getTag(TrueSoulboundVault.PERSISTED_KEY).copy());
	}

	public static void deliverVaultForPlayer(EntityPlayer player) {
		if (player == null || player.worldObj == null || player.worldObj.isRemote) {
			return;
		}
		int restored = TrueSoulboundVault.drainPersistedPlayer(player);
		TrueSoulboundVault vault = TrueSoulboundVault.get(player.worldObj);
		if (vault != null) {
			restored += vault.deliverToPlayer(player);
		}
		if (restored > 0) {
			TrueSoulboundHelper.notifyLocalized(player, "returned", EnumChatFormatting.AQUA, "am2.truesoulbound.returned");
		}
	}
}
