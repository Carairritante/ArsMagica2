package am2.interop;

import am2.AMCore;
import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageHelper;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.Collection;

/**
 * Optional Applied Energistics 2 bridge for Otherworld Aura rituals.
 * Uses strictly public {@code appeng.api.*} interfaces and limits lookup
 * and extraction to active, powered ME terminal parts (ME Terminal,
 * ME Crafting Terminal, Pattern Terminal, etc.).
 */
public final class AE2OtherworldCompat {

	private static final ForgeDirection[] SCAN_DIRS = new ForgeDirection[]{
			ForgeDirection.NORTH,
			ForgeDirection.SOUTH,
			ForgeDirection.EAST,
			ForgeDirection.WEST,
			ForgeDirection.UP,
			ForgeDirection.DOWN,
			ForgeDirection.UNKNOWN
	};

	private AE2OtherworldCompat() {
	}

	private static boolean isValidTerminalPart(IPart part) {
		return part instanceof ITerminalHost
				&& part instanceof IPowerChannelState
				&& part instanceof IActionHost;
	}

	/**
	 * Returns true if the given TileEntity hosts at least one AE2 terminal part.
	 */
	public static boolean isTerminalHost(TileEntity te) {
		if (!(te instanceof IPartHost)) {
			return false;
		}
		IPartHost host = (IPartHost) te;
		for (ForgeDirection dir : SCAN_DIRS) {
			if (isValidTerminalPart(host.getPart(dir))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks whether any powered, active AE2 terminal attached to {@code te}
	 * can supply at least {@code quantity} of an item matching {@code search}.
	 */
	public static boolean hasItemInTerminalHost(TileEntity te, ItemStack search, int quantity) {
		return queryOrExtract(te, search, quantity, false, AEApi.instance().storage()) != null;
	}

	/**
	 * Extracts {@code quantity} matching items from an active, powered AE2 terminal
	 * hosted on {@code te}, consuming AE network power cleanly.
	 * Returns null if the item or energy is unavailable.
	 */
	public static ItemStack extractFromTerminalHost(TileEntity te, ItemStack search, int quantity) {
		return queryOrExtract(te, search, quantity, true, AEApi.instance().storage());
	}

	static ItemStack queryOrExtract(TileEntity te, ItemStack search, int quantity, boolean extract, IStorageHelper storage) {
		if (!(te instanceof IPartHost) || search == null || search.getItem() == null || quantity <= 0 || storage == null) {
			return null;
		}
		IPartHost host = (IPartHost) te;
		for (ForgeDirection dir : SCAN_DIRS) {
			IPart part = host.getPart(dir);
			if (!isValidTerminalPart(part)) {
				continue;
			}
			IPowerChannelState powerState = (IPowerChannelState) part;
			if (!powerState.isPowered() || !powerState.isActive()) {
				continue;
			}
			IActionHost actionHost = (IActionHost) part;
			IGridNode node = actionHost.getActionableNode();
			if (node == null || !node.isActive()) {
				continue;
			}
			IGrid grid = node.getGrid();
			if (grid == null) {
				continue;
			}
			IEnergyGrid energy = grid.getCache(IEnergyGrid.class);
			IMEMonitor<IAEItemStack> monitor = ((ITerminalHost) part).getItemInventory();
			if (energy == null || monitor == null) {
				continue;
			}

			IAEItemStack match = findMatchingStack(monitor.getStorageList(), search, quantity, storage);
			if (match == null) {
				continue;
			}

			IAEItemStack request = match.copy();
			request.setStackSize(quantity);
			BaseActionSource source = new MachineSource(actionHost);
			if (!extract) {
				IAEItemStack simulated = monitor.extractItems(request, Actionable.SIMULATE, source);
				if (simulated == null || simulated.getStackSize() < quantity) {
					continue;
				}
				double simPower = energy.extractAEPower(quantity, Actionable.SIMULATE, PowerMultiplier.CONFIG);
				if (simPower >= quantity - 1.0e-6D) {
					ItemStack preview = match.getItemStack();
					if (preview != null) {
						ItemStack out = preview.copy();
						out.stackSize = quantity;
						return out;
					}
				}
				continue;
			}

			IAEItemStack extracted = storage.poweredExtraction(energy, monitor, request, source);
			if (extracted != null && extracted.getStackSize() >= quantity) {
				ItemStack stack = extracted.getItemStack();
				if (stack != null) {
					ItemStack out = stack.copy();
					out.stackSize = quantity;
					return out;
				}
			}
			// Never drop or lose partial extractions when quantity > 1
			if (extracted != null && extracted.getStackSize() > 0) {
				monitor.injectItems(extracted, Actionable.MODULATE, source);
			}
		}
		return null;
	}

	private static IAEItemStack findMatchingStack(IItemList<IAEItemStack> storageList, ItemStack search, int quantity, IStorageHelper storage) {
		if (storageList == null || storage == null) {
			return null;
		}
		ItemStack lookup = search.copy();
		lookup.stackSize = 1;
		IAEItemStack aeSearch = storage.createItemStack(lookup);
		if (aeSearch == null) {
			return null;
		}

		boolean isWildcard = search.getItemDamage() == AMCore.ANY_META;
		boolean isPotion = search.getItem() instanceof ItemPotion;
		if (!isWildcard && !isPotion) {
			IAEItemStack exact = storageList.findPrecise(aeSearch);
			return matchesSearch(exact, search, quantity) ? exact : null;
		}

		Collection<IAEItemStack> fuzzy = storageList.findFuzzy(aeSearch, FuzzyMode.IGNORE_ALL);
		if (fuzzy != null) {
			for (IAEItemStack cand : fuzzy) {
				if (matchesSearch(cand, search, quantity)) {
					return cand;
				}
			}
		}
		return null;
	}

	static boolean matchesSearch(IAEItemStack cand, ItemStack search, int quantity) {
		if (cand == null || cand.getStackSize() < quantity) {
			return false;
		}
		ItemStack candStack = cand.getItemStack();
		if (candStack == null || candStack.getItem() != search.getItem()) {
			return false;
		}
		if (search.getItem() instanceof ItemPotion) {
			if ((candStack.getItemDamage() & 0xF) != (search.getItemDamage() & 0xF)) {
				return false;
			}
		} else if (search.getItemDamage() != AMCore.ANY_META && candStack.getItemDamage() != search.getItemDamage()) {
			return false;
		}

		if (search.hasTagCompound()) {
			return ItemStack.areItemStackTagsEqual(candStack, search);
		}
		return !candStack.hasTagCompound() || candStack.stackTagCompound.hasNoTags();
	}
}
