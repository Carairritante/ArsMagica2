package am2.entities.ai;

import am2.AMCore;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.Test;

import static org.junit.Assert.*;

public class EntityAISpellmakingInventoryTest {

	private static final Item TEST_ITEM = new Item();

	@Test
	public void deductConcreteFromInventorySplitsAcrossSlotsAndMarksDirty() {
		StubInventory inv = new StubInventory(5);
		inv.setInventorySlotContents(0, new ItemStack(TEST_ITEM, 1, 4));
		inv.setInventorySlotContents(2, new ItemStack(TEST_ITEM, 3, 4));

		ItemStack result = EntityAISpellmaking.deductConcreteFromInventory(
				inv, new ItemStack(TEST_ITEM, 1, AMCore.ANY_META), 3);
		assertNotNull(result);
		assertEquals(3, result.stackSize);
		assertEquals(4, result.getItemDamage());
		assertNull(inv.getStackInSlot(0));
		assertNotNull(inv.getStackInSlot(2));
		assertEquals(1, inv.getStackInSlot(2).stackSize);
		assertTrue(inv.dirty);
	}

	@Test
	public void deductConcreteFromInventoryIsAtomicWhenShort() {
		StubInventory inv = new StubInventory(3);
		inv.setInventorySlotContents(1, new ItemStack(TEST_ITEM, 2, 0));

		ItemStack result = EntityAISpellmaking.deductConcreteFromInventory(
				inv, new ItemStack(TEST_ITEM, 1, 0), 3);
		assertNull(result);
		assertEquals(2, inv.getStackInSlot(1).stackSize);
		assertFalse(inv.dirty);
	}

	private static final class StubInventory implements IInventory {
		private final ItemStack[] slots;
		boolean dirty = false;

		StubInventory(int size) {
			this.slots = new ItemStack[size];
		}

		@Override public int getSizeInventory() { return slots.length; }
		@Override public ItemStack getStackInSlot(int slot) { return slots[slot]; }

		@Override
		public ItemStack decrStackSize(int slot, int amount) {
			if (slots[slot] == null) return null;
			if (slots[slot].stackSize <= amount) {
				ItemStack out = slots[slot];
				slots[slot] = null;
				return out;
			}
			ItemStack split = slots[slot].splitStack(amount);
			if (slots[slot].stackSize == 0) slots[slot] = null;
			return split;
		}

		@Override public ItemStack getStackInSlotOnClosing(int slot) { return null; }
		@Override public void setInventorySlotContents(int slot, ItemStack stack) { slots[slot] = stack; }
		@Override public String getInventoryName() { return "test"; }
		@Override public boolean hasCustomInventoryName() { return false; }
		@Override public int getInventoryStackLimit() { return 64; }
		@Override public void markDirty() { dirty = true; }
		@Override public boolean isUseableByPlayer(EntityPlayer player) { return true; }
		@Override public void openInventory() {}
		@Override public void closeInventory() {}
		@Override public boolean isItemValidForSlot(int slot, ItemStack stack) { return true; }
	}
}
