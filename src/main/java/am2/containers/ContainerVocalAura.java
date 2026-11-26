package am2.containers;

import am2.api.spell.ItemSpellBase;
import am2.playerextensions.VoiceVaultStorage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Container for the 8-slot personal Vocal Resonance Aura Vault.
 * Only accepts real spell scrolls (ItemSpellBase), 1 per slot.
 */
public class ContainerVocalAura extends Container {

	private static final int VAULT_SLOTS = VoiceVaultStorage.NUM_SLOTS; // 8
	private static final int PLAYER_INV_START = VAULT_SLOTS;           // 8
	private static final int PLAYER_HOTBAR_START = PLAYER_INV_START + 27; // 35
	private static final int PLAYER_HOTBAR_END = PLAYER_HOTBAR_START + 9; // 44

	private final VoiceVaultStorage vault;

	private static final class SlotSpellScroll extends Slot {
		SlotSpellScroll(IInventory inv, int index, int x, int y) {
			super(inv, index, x, y);
		}

		@Override
		public boolean isItemValid(ItemStack stack) {
			return VoiceVaultStorage.acceptsScroll(stack);
		}

		@Override
		public int getSlotStackLimit() {
			return 1;
		}
	}

	public ContainerVocalAura(InventoryPlayer playerInv, VoiceVaultStorage vault) {
		this.vault = vault;

		// 8 spell vault slots arranged in 2 columns x 4 rows (aligned with vocalAuraGui.png 256x238)
		for (int i = 0; i < VAULT_SLOTS; i++) {
			int col = i / 4;
			int row = i % 4;
			int sx = (col == 0) ? 11 : 131;
			int sy = 21 + row * 20;
			addSlotToContainer(new SlotSpellScroll(vault, i, sx, sy));
		}

		// Player main inventory (3x9), centered horizontally inside 256px width (x = 48)
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 48 + col * 18, 159 + row * 18));
			}
		}

		// Player hotbar (1x9)
		for (int col = 0; col < 9; col++) {
			addSlotToContainer(new Slot(playerInv, col, 48 + col * 18, 216));
		}
	}

	public VoiceVaultStorage getVault() {
		return vault;
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int index) {
		ItemStack copy = null;
		Slot slot = (Slot) this.inventorySlots.get(index);
		if (slot != null && slot.getHasStack()) {
			ItemStack stack = slot.getStack();
			copy = stack.copy();

			if (index < PLAYER_INV_START) {
				if (!this.mergeItemStack(stack, PLAYER_INV_START, PLAYER_HOTBAR_END, true)) {
					return null;
				}
			} else {
				if (!VoiceVaultStorage.acceptsScroll(stack)) {
					return null;
				}
				// Find first empty vault slot for single-scroll insertion
				boolean placed = false;
				for (int v = 0; v < VAULT_SLOTS; v++) {
					Slot target = (Slot) this.inventorySlots.get(v);
					if (!target.getHasStack()) {
						ItemStack one = stack.splitStack(1);
						target.putStack(one);
						target.onSlotChanged();
						placed = true;
						break;
					}
				}
				if (!placed) {
					return null;
				}
			}

			if (stack.stackSize <= 0) {
				slot.putStack(null);
			} else {
				slot.onSlotChanged();
			}
			if (stack.stackSize == copy.stackSize) {
				return null;
			}
			slot.onPickupFromSlot(player, stack);
		}
		return copy;
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return vault != null && vault.isUseableByPlayer(player);
	}
}
