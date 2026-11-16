package am2.soulbound;

import java.lang.reflect.Method;
import java.util.UUID;

import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class TrueSoulboundTest {

	private static final Item TEST_ITEM = new Item().setUnlocalizedName("am2_test_relic");

	@BeforeClass
	public static void registerTestItem() throws Exception {
		if (Item.itemRegistry.getObject("am2test:relic") == null) {
			Method addObjectRaw = FMLControlledNamespacedRegistry.class.getDeclaredMethod(
					"addObjectRaw", int.class, String.class, Object.class);
			addObjectRaw.setAccessible(true);
			addObjectRaw.invoke(Item.itemRegistry, 4095, "am2test:relic", TEST_ITEM);
		}
	}

	@Test
	public void bindAndUnbindPreserveExistingNbtAndRejectStacks() {
		UUID ownerId = UUID.randomUUID();
		ItemStack single = new ItemStack(TEST_ITEM, 1, 0);
		NBTTagCompound extra = new NBTTagCompound();
		extra.setInteger("spellbookactiveslot", 3);
		single.setTagCompound(extra);

		assertTrue(TrueSoulboundHelper.canBind(single));
		ItemStack bound = TrueSoulboundHelper.bind(single, ownerId, "ArsMage", 1200L);
		assertNotNull(bound);
		assertTrue(TrueSoulboundHelper.isTrueSoulbound(bound));
		assertEquals(ownerId, TrueSoulboundHelper.getOwnerUUID(bound));
		assertEquals("ArsMage", TrueSoulboundHelper.getOwnerName(bound));
		assertEquals(1200L, TrueSoulboundHelper.getBoundAt(bound));
		assertNotNull(TrueSoulboundHelper.getItemUID(bound));
		assertEquals(3, bound.getTagCompound().getInteger("spellbookactiveslot"));

		assertTrue(TrueSoulboundHelper.unbind(bound));
		assertFalse(TrueSoulboundHelper.isTrueSoulbound(bound));
		assertNull(TrueSoulboundHelper.getOwnerUUID(bound));
		assertEquals(3, bound.getTagCompound().getInteger("spellbookactiveslot"));

		ItemStack plain = TrueSoulboundHelper.bind(new ItemStack(TEST_ITEM, 1, 0), ownerId, "ArsMage", 5L);
		assertNotNull(plain);
		assertTrue(TrueSoulboundHelper.unbind(plain));
		assertNull(plain.getTagCompound());

		ItemStack stacked = new ItemStack(TEST_ITEM, 2, 0);
		assertFalse(TrueSoulboundHelper.canBind(stacked));
		assertNull(TrueSoulboundHelper.bind(stacked, ownerId, "ArsMage", 5L));
	}

	@Test
	public void sneakDropDoubleConfirmWindowExpiresAfterSixtyTicks() {
		UUID ownerId = UUID.randomUUID();
		String uid = UUID.randomUUID().toString();
		String otherUid = UUID.randomUUID().toString();

		assertFalse(TrueSoulboundHelper.consumePendingUnbind(ownerId, uid, 100L));

		TrueSoulboundHelper.recordPendingUnbind(ownerId, uid, 100L);
		assertFalse(TrueSoulboundHelper.consumePendingUnbind(ownerId, otherUid, 120L));
		assertTrue(TrueSoulboundHelper.consumePendingUnbind(ownerId, uid, 150L));
		assertFalse(TrueSoulboundHelper.consumePendingUnbind(ownerId, uid, 155L));

		TrueSoulboundHelper.recordPendingUnbind(ownerId, uid, 200L);
		assertFalse(TrueSoulboundHelper.consumePendingUnbind(ownerId, uid, 261L));
	}

	@Test
	public void inventoryDeliveryHonorsPreferredSlotAndPreventsDuplicates() {
		UUID ownerId = UUID.randomUUID();
		ItemStack bound = TrueSoulboundHelper.bind(new ItemStack(TEST_ITEM, 1, 0), ownerId, "MageOne", 40L);
		String uid = TrueSoulboundHelper.getItemUID(bound);

		InventoryPlayer inv = new InventoryPlayer(null);
		assertTrue(TrueSoulboundHelper.deliverToInventory(inv, null, bound, 4));
		assertNotNull(inv.mainInventory[4]);
		assertEquals(uid, TrueSoulboundHelper.getItemUID(inv.mainInventory[4]));

		// Calling delivery again with the same UID must not create a second copy.
		assertTrue(TrueSoulboundHelper.deliverToInventory(inv, null, bound, 0));
		assertNull(inv.mainInventory[0]);
		int count = 0;
		for (ItemStack s : inv.mainInventory) {
			if (uid.equals(TrueSoulboundHelper.getItemUID(s))) {
				count++;
			}
		}
		assertEquals(1, count);

		// When preferred slot is occupied, falls back cleanly to first open slot.
		ItemStack secondBound = TrueSoulboundHelper.bind(new ItemStack(TEST_ITEM, 1, 0), ownerId, "MageOne", 41L);
		assertTrue(TrueSoulboundHelper.deliverToInventory(inv, null, secondBound, 4));
		assertNotNull(inv.mainInventory[0]);
		assertEquals(TrueSoulboundHelper.getItemUID(secondBound), TrueSoulboundHelper.getItemUID(inv.mainInventory[0]));

		// When all slots are full, delivery returns false cleanly without dropping/duping.
		for (int i = 0; i < inv.mainInventory.length; i++) {
			if (inv.mainInventory[i] == null) {
				inv.mainInventory[i] = new ItemStack(TEST_ITEM, 1, 0);
			}
		}
		ItemStack thirdBound = TrueSoulboundHelper.bind(new ItemStack(TEST_ITEM, 1, 0), ownerId, "MageOne", 42L);
		assertFalse(TrueSoulboundHelper.deliverToInventory(inv, null, thirdBound, -1));
	}

	@Test
	public void vaultRoundTripsAcrossNbtAndDeduplicatesByUid() {
		UUID ownerA = UUID.randomUUID();
		UUID ownerB = UUID.randomUUID();

		ItemStack stackA = TrueSoulboundHelper.bind(new ItemStack(TEST_ITEM, 1, 0), ownerA, "Alice", 10L);
		ItemStack stackB = TrueSoulboundHelper.bind(new ItemStack(TEST_ITEM, 1, 0), ownerB, "Bob", 20L);

		TrueSoulboundVault vault = new TrueSoulboundVault();
		assertTrue(vault.store(ownerA, stackA, 2));
		assertTrue(vault.store(ownerA, stackA, 5)); // Same UID updates slot, does not duplicate
		assertTrue(vault.store(ownerB, stackB, 7));
		assertEquals(2, vault.size());

		NBTTagCompound tag = new NBTTagCompound();
		vault.writeToNBT(tag);

		TrueSoulboundVault reloaded = new TrueSoulboundVault();
		reloaded.readFromNBT(tag);
		assertEquals(2, reloaded.size());
		assertEquals(1, reloaded.getEntriesForOwner(ownerA).size());
		assertEquals(5, reloaded.getEntriesForOwner(ownerA).get(0).preferredSlot);
		assertEquals(TrueSoulboundHelper.getItemUID(stackA), reloaded.getEntriesForOwner(ownerA).get(0).uid);
		assertEquals(1, reloaded.getEntriesForOwner(ownerB).size());
		assertEquals(ownerB, TrueSoulboundHelper.getOwnerUUID(reloaded.getEntriesForOwner(ownerB).get(0).stack));

		assertTrue(reloaded.removeByUID(TrueSoulboundHelper.getItemUID(stackA)));
		assertFalse(reloaded.containsUID(TrueSoulboundHelper.getItemUID(stackA)));
		assertEquals(1, reloaded.size());
	}

	@Test
	public void cursorHeldStackStillDeliversToMainInventoryOnCloseOrOutsideClick() {
		UUID ownerId = UUID.randomUUID();
		ItemStack bound = TrueSoulboundHelper.bind(new ItemStack(TEST_ITEM, 1, 0), ownerId, "MageCursor", 99L);
		String uid = TrueSoulboundHelper.getItemUID(bound);

		InventoryPlayer inv = new InventoryPlayer(null);
		// Simulate Container.onContainerClosed / slotClick(-999) where cursor still holds the item during ItemTossEvent
		inv.setItemStack(bound);
		assertTrue(TrueSoulboundHelper.inventoryContainsUID(inv, uid, true));
		assertFalse(TrueSoulboundHelper.inventoryContainsUID(inv, uid, false));

		assertTrue(TrueSoulboundHelper.deliverToInventory(inv, null, bound, 2));
		// Container then clears cursor right after dropPlayerItemWithRandomChoice returns
		inv.setItemStack(null);

		assertNotNull(inv.mainInventory[2]);
		assertEquals(uid, TrueSoulboundHelper.getItemUID(inv.mainInventory[2]));
	}

	@Test
	public void spellBookContainsUidRecognizesNestedBoundScrolls() {
		UUID ownerId = UUID.randomUUID();
		ItemStack boundScroll = TrueSoulboundHelper.bind(new ItemStack(TEST_ITEM, 1, 0), ownerId, "ScrollOwner", 50L);
		String scrollUid = TrueSoulboundHelper.getItemUID(boundScroll);

		ItemStack book = new ItemStack(TEST_ITEM, 1, 0);
		NBTTagCompound bookRoot = new NBTTagCompound();
		NBTTagList spellList = new NBTTagList();
		NBTTagCompound entry = new NBTTagCompound();
		entry.setInteger("meta", 0);
		entry.setInteger("index", 0);
		entry.setTag("data", boundScroll.getTagCompound().copy());
		spellList.appendTag(entry);
		bookRoot.setTag("spell_book_inventory", spellList);
		book.setTagCompound(bookRoot);

		InventoryPlayer inv = new InventoryPlayer(null);
		inv.mainInventory[0] = book;

		assertTrue(TrueSoulboundHelper.spellBookContainsUID(book, scrollUid));
		assertTrue(TrueSoulboundHelper.inventoryContainsUID(inv, scrollUid, false));
	}

	@Test
	public void clampDurabilityPreventsItemBreakage() {
		Item damageableItem = new Item().setMaxDamage(100);
		UUID ownerId = UUID.randomUUID();
		ItemStack bound = TrueSoulboundHelper.bind(new ItemStack(damageableItem, 1, 100), ownerId, "WarriorMage", 77L);

		assertTrue(TrueSoulboundHelper.clampDurability(bound));
		assertEquals(99, bound.getItemDamage());
		assertFalse(TrueSoulboundHelper.clampDurability(bound));
	}
}
