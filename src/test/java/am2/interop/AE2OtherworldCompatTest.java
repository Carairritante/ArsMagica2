package am2.interop;

import am2.AMCore;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IFacadeContainer;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.LayerFlags;
import appeng.api.parts.SelectedPart;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageHelper;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IAETagCompound;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class AE2OtherworldCompatTest {

	private static final Item TEST_GEM = new Item();
	private static final ItemPotion TEST_POTION = new ItemPotion();

	@Test
	public void nonTerminalPartsAreRejected() {
		TileEntity plainTile = new TileEntity() {};
		assertFalse(AE2OtherworldCompat.isTerminalHost(plainTile));

		StubPartHostTile cableOnlyHost = new StubPartHostTile();
		cableOnlyHost.parts.put(ForgeDirection.UNKNOWN, proxyOf(IPart.class));
		assertFalse(AE2OtherworldCompat.isTerminalHost(cableOnlyHost));

		// Simulate Interface Terminal or Storage Monitor missing ITerminalHost / power / action host
		StubPartHostTile monitorOnlyHost = new StubPartHostTile();
		monitorOnlyHost.parts.put(ForgeDirection.NORTH, proxyOf(IPart.class, IPowerChannelState.class));
		assertFalse(AE2OtherworldCompat.isTerminalHost(monitorOnlyHost));
	}

	@Test
	public void terminalDiscoveryAndPoweredExtractionWorkEndToEnd() {
		FakeNetwork network = new FakeNetwork(10.0D);
		network.addItem(new ItemStack(TEST_GEM, 5, 2));

		StubPartHostTile host = new StubPartHostTile();
		host.parts.put(ForgeDirection.NORTH, createTerminalPart(false, false, true, network)); // unpowered side
		host.parts.put(ForgeDirection.WEST, createTerminalPart(true, true, true, network));   // powered side

		assertTrue(AE2OtherworldCompat.isTerminalHost(host));

		ItemStack search = new ItemStack(TEST_GEM, 1, 2);
		ItemStack sim = AE2OtherworldCompat.queryOrExtract(host, search, 2, false, network.storageHelper);
		assertNotNull(sim);
		assertEquals(2, sim.stackSize);
		assertEquals(5L, network.getCount(TEST_GEM, 2));
		assertEquals(10.0D, network.energy, 1e-6D);

		ItemStack extracted = AE2OtherworldCompat.queryOrExtract(host, search, 2, true, network.storageHelper);
		assertNotNull(extracted);
		assertEquals(2, extracted.stackSize);
		assertEquals(2, extracted.getItemDamage());
		assertEquals(3L, network.getCount(TEST_GEM, 2));
		assertEquals(8.0D, network.energy, 1e-6D);
	}

	@Test
	public void unpoweredOrNoChannelTerminalsDoNotSupplyItems() {
		FakeNetwork network = new FakeNetwork(100.0D);
		network.addItem(new ItemStack(TEST_GEM, 10, 0));

		StubPartHostTile noPower = new StubPartHostTile();
		noPower.parts.put(ForgeDirection.UP, createTerminalPart(false, true, true, network));
		assertNull(AE2OtherworldCompat.queryOrExtract(noPower, new ItemStack(TEST_GEM, 1, 0), 1, true, network.storageHelper));

		StubPartHostTile noChannel = new StubPartHostTile();
		noChannel.parts.put(ForgeDirection.UP, createTerminalPart(true, false, true, network));
		assertNull(AE2OtherworldCompat.queryOrExtract(noChannel, new ItemStack(TEST_GEM, 1, 0), 1, true, network.storageHelper));

		StubPartHostTile inactiveNode = new StubPartHostTile();
		inactiveNode.parts.put(ForgeDirection.UP, createTerminalPart(true, true, false, network));
		assertNull(AE2OtherworldCompat.queryOrExtract(inactiveNode, new ItemStack(TEST_GEM, 1, 0), 1, true, network.storageHelper));

		assertEquals(10L, network.getCount(TEST_GEM, 0));
	}

	@Test
	public void insufficientEnergyOrPartialExtractionRollsBackWithoutLoss() {
		FakeNetwork lowPower = new FakeNetwork(1.0D);
		lowPower.addItem(new ItemStack(TEST_GEM, 4, 0));

		StubPartHostTile host = new StubPartHostTile();
		host.parts.put(ForgeDirection.EAST, createTerminalPart(true, true, true, lowPower));

		ItemStack search = new ItemStack(TEST_GEM, 1, 0);
		assertNull(AE2OtherworldCompat.queryOrExtract(host, search, 3, false, lowPower.storageHelper));
		assertNull(AE2OtherworldCompat.queryOrExtract(host, search, 3, true, lowPower.storageHelper));
		assertEquals(4L, lowPower.getCount(TEST_GEM, 0));
		assertEquals(1.0D, lowPower.energy, 1e-6D);

		// Simulate a buggy partial extract where network yields only 1 of 3 requested
		FakeNetwork partialNet = new FakeNetwork(50.0D);
		partialNet.forcePartialExtractAmount = 1L;
		partialNet.addItem(new ItemStack(TEST_GEM, 4, 0));
		StubPartHostTile partialHost = new StubPartHostTile();
		partialHost.parts.put(ForgeDirection.SOUTH, createTerminalPart(true, true, true, partialNet));

		assertNull(AE2OtherworldCompat.queryOrExtract(partialHost, search, 3, true, partialNet.storageHelper));
		assertEquals("Partial extraction must roll back cleanly", 4L, partialNet.getCount(TEST_GEM, 0));
	}

	@Test
	public void wildcardMetaAndPotionLowNibbleAndNbtGuardsWork() {
		FakeNetwork network = new FakeNetwork(50.0D);
		ItemStack damagedPlain = new ItemStack(TEST_GEM, 2, 7);
		ItemStack damagedTagged = new ItemStack(TEST_GEM, 5, 8);
		damagedTagged.setTagCompound(new NBTTagCompound());
		damagedTagged.getTagCompound().setString("Owner", "Player");

		// Vanilla potion damage: low 4 bits = potion type (5 = Healing), upper bits = splash/extended flags
		ItemStack splashPotion = new ItemStack(TEST_POTION, 3, 16389); // 0x4005 & 0xF == 5

		network.addItem(damagedTagged);
		network.addItem(damagedPlain);
		network.addItem(splashPotion);

		StubPartHostTile host = new StubPartHostTile();
		host.parts.put(ForgeDirection.WEST, createTerminalPart(true, true, true, network));

		// Wildcard matches plain damaged item, NOT NBT-tagged item
		ItemStack wildExtracted = AE2OtherworldCompat.queryOrExtract(
				host, new ItemStack(TEST_GEM, 1, AMCore.ANY_META), 1, true, network.storageHelper);
		assertNotNull(wildExtracted);
		assertEquals(7, wildExtracted.getItemDamage());
		assertEquals(1L, network.getCount(TEST_GEM, 7));
		assertEquals(5L, network.getCount(TEST_GEM, 8));

		// Potion matches low-4-bit potion effect even with different upper flags (8197 -> 0x2005 & 0xF == 5)
		ItemStack potionSearch = new ItemStack(TEST_POTION, 1, 8197);
		ItemStack potExtracted = AE2OtherworldCompat.queryOrExtract(host, potionSearch, 1, true, network.storageHelper);
		assertNotNull(potExtracted);
		assertEquals(16389, potExtracted.getItemDamage());
		assertEquals(2L, network.getCount(TEST_POTION, 16389));
	}

	private static IPart createTerminalPart(final boolean powered, final boolean active, final boolean nodeActive, final FakeNetwork network) {
		final IGrid grid = proxyOf(IGrid.class);
		final IGridNode node = proxyOf(IGridNode.class);
		final IMEMonitor<IAEItemStack> monitor = network.monitor;
		final IEnergyGrid energyGrid = network.energyGrid;

		InvocationHandler partHandler = new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) {
				String name = method.getName();
				if ("isPowered".equals(name)) return powered;
				if ("isActive".equals(name)) return active;
				if ("getItemInventory".equals(name)) return monitor;
				if ("getActionableNode".equals(name)) {
					return Proxy.newProxyInstance(
							AE2OtherworldCompatTest.class.getClassLoader(),
							new Class<?>[]{IGridNode.class},
							new InvocationHandler() {
								@Override
								public Object invoke(Object p, Method m, Object[] a) {
									if ("isActive".equals(m.getName())) return nodeActive;
									if ("getGrid".equals(m.getName())) {
										return Proxy.newProxyInstance(
												AE2OtherworldCompatTest.class.getClassLoader(),
												new Class<?>[]{IGrid.class},
												new InvocationHandler() {
													@Override
													public Object invoke(Object gp, Method gm, Object[] ga) {
														if ("getCache".equals(gm.getName()) && ga != null && ga.length == 1 && ga[0] == IEnergyGrid.class) {
															return energyGrid;
														}
														return defaultValue(gm.getReturnType());
													}
												});
									}
									return defaultValue(m.getReturnType());
								}
							});
				}
				return defaultValue(method.getReturnType());
			}
		};
		return (IPart) Proxy.newProxyInstance(
				AE2OtherworldCompatTest.class.getClassLoader(),
				new Class<?>[]{IPart.class, ITerminalHost.class, IPowerChannelState.class, IActionHost.class},
				partHandler);
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxyOf(Class<?>... ifaces) {
		return (T) Proxy.newProxyInstance(
				AE2OtherworldCompatTest.class.getClassLoader(),
				ifaces,
				new InvocationHandler() {
					@Override
					public Object invoke(Object proxy, Method method, Object[] args) {
						return defaultValue(method.getReturnType());
					}
				});
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) return null;
		if (type == boolean.class) return Boolean.FALSE;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == double.class) return 0.0D;
		if (type == float.class) return 0.0F;
		return null;
	}

	private static final class FakeNetwork {
		final List<StoredEntry> entries = new ArrayList<StoredEntry>();
		double energy;
		long forcePartialExtractAmount = -1L;

		final IEnergyGrid energyGrid;
		@SuppressWarnings("unchecked")
		final IMEMonitor<IAEItemStack> monitor;
		final IStorageHelper storageHelper;

		FakeNetwork(double initialEnergy) {
			this.energy = initialEnergy;
			this.energyGrid = (IEnergyGrid) Proxy.newProxyInstance(
					AE2OtherworldCompatTest.class.getClassLoader(),
					new Class<?>[]{IEnergyGrid.class},
					new InvocationHandler() {
						@Override
						public Object invoke(Object proxy, Method method, Object[] args) {
							if ("extractAEPower".equals(method.getName())) {
								double amt = ((Number) args[0]).doubleValue();
								Actionable mode = (Actionable) args[1];
								double extracted = Math.min(amt, energy);
								if (mode == Actionable.MODULATE) {
									energy -= extracted;
								}
								return extracted;
							}
							return defaultValue(method.getReturnType());
						}
					});

			final IItemList<IAEItemStack> itemList = createItemListProxy();
			this.monitor = (IMEMonitor<IAEItemStack>) Proxy.newProxyInstance(
					AE2OtherworldCompatTest.class.getClassLoader(),
					new Class<?>[]{IMEMonitor.class},
					new InvocationHandler() {
						@Override
						public Object invoke(Object proxy, Method method, Object[] args) {
							String name = method.getName();
							if ("getStorageList".equals(name)) {
								return itemList;
							}
							if ("extractItems".equals(name)) {
								IAEItemStack req = (IAEItemStack) args[0];
								Actionable mode = (Actionable) args[1];
								StoredEntry found = findExactEntry(req.getItemStack());
								if (found == null || found.count <= 0) return null;
								long take = Math.min(req.getStackSize(), found.count);
								if (forcePartialExtractAmount > 0 && mode == Actionable.MODULATE) {
									take = Math.min(take, forcePartialExtractAmount);
								}
								if (mode == Actionable.MODULATE) {
									found.count -= take;
								}
								return wrapAEStack(found.template, take);
							}
							if ("injectItems".equals(name)) {
								IAEItemStack input = (IAEItemStack) args[0];
								Actionable mode = (Actionable) args[1];
								if (input != null && input.getStackSize() > 0 && mode == Actionable.MODULATE) {
									addItem(input.getItemStack(), input.getStackSize());
								}
								return null;
							}
							return defaultValue(method.getReturnType());
						}
					});

			this.storageHelper = (IStorageHelper) Proxy.newProxyInstance(
					AE2OtherworldCompatTest.class.getClassLoader(),
					new Class<?>[]{IStorageHelper.class},
					new InvocationHandler() {
						@Override
						public Object invoke(Object proxy, Method method, Object[] args) {
							String name = method.getName();
							if ("createItemStack".equals(name)) {
								ItemStack stack = (ItemStack) args[0];
								return wrapAEStack(stack, stack.stackSize);
							}
							if ("poweredExtraction".equals(name)) {
								IEnergyGrid e = (IEnergyGrid) args[0];
								IMEMonitor<IAEItemStack> inv = (IMEMonitor<IAEItemStack>) args[1];
								IAEItemStack req = (IAEItemStack) args[2];
								BaseActionSource src = (BaseActionSource) args[3];
								IAEItemStack sim = inv.extractItems(req, Actionable.SIMULATE, src);
								if (sim == null) return null;
								double need = (double) sim.getStackSize();
								if (e.extractAEPower(need, Actionable.SIMULATE, PowerMultiplier.CONFIG) < need - 1.0e-6D) {
									return null;
								}
								e.extractAEPower(need, Actionable.MODULATE, PowerMultiplier.CONFIG);
								return inv.extractItems(req, Actionable.MODULATE, src);
							}
							return defaultValue(method.getReturnType());
						}
					});
		}

		void addItem(ItemStack stack) {
			addItem(stack, stack.stackSize);
		}

		void addItem(ItemStack stack, long amount) {
			StoredEntry existing = findExactEntry(stack);
			if (existing != null) {
				existing.count += amount;
			} else {
				ItemStack copy = stack.copy();
				copy.stackSize = 1;
				entries.add(new StoredEntry(copy, amount));
			}
		}

		long getCount(Item item, int meta) {
			long sum = 0;
			for (StoredEntry e : entries) {
				if (e.template.getItem() == item && e.template.getItemDamage() == meta) {
					sum += e.count;
				}
			}
			return sum;
		}

		StoredEntry findExactEntry(ItemStack stack) {
			for (StoredEntry e : entries) {
				if (e.template.getItem() == stack.getItem()
						&& e.template.getItemDamage() == stack.getItemDamage()
						&& ItemStack.areItemStackTagsEqual(e.template, stack)) {
					return e;
				}
			}
			return null;
		}

		@SuppressWarnings("unchecked")
		private IItemList<IAEItemStack> createItemListProxy() {
			return (IItemList<IAEItemStack>) Proxy.newProxyInstance(
					AE2OtherworldCompatTest.class.getClassLoader(),
					new Class<?>[]{IItemList.class},
					new InvocationHandler() {
						@Override
						public Object invoke(Object proxy, Method method, Object[] args) {
							String name = method.getName();
							if ("findPrecise".equals(name)) {
								IAEItemStack target = (IAEItemStack) args[0];
								StoredEntry e = findExactEntry(target.getItemStack());
								return (e != null && e.count > 0) ? wrapAEStack(e.template, e.count) : null;
							}
							if ("findFuzzy".equals(name)) {
								IAEItemStack target = (IAEItemStack) args[0];
								List<IAEItemStack> out = new ArrayList<IAEItemStack>();
								for (StoredEntry e : entries) {
									if (e.count > 0 && e.template.getItem() == target.getItemStack().getItem()) {
										out.add(wrapAEStack(e.template, e.count));
									}
								}
								return out;
							}
							return defaultValue(method.getReturnType());
						}
					});
		}

		private static IAEItemStack wrapAEStack(final ItemStack template, final long count) {
			return new StubAEItemStack(template, count);
		}
	}

	private static final class StubAEItemStack implements IAEItemStack {
		private final ItemStack template;
		private long count;

		StubAEItemStack(ItemStack template, long count) {
			this.template = template.copy();
			this.template.stackSize = 1;
			this.count = count;
		}

		@Override
		public ItemStack getItemStack() {
			ItemStack copy = template.copy();
			copy.stackSize = (int) Math.min(Integer.MAX_VALUE, count);
			return copy;
		}

		@Override public boolean hasTagCompound() { return template.hasTagCompound(); }
		@Override public void add(IAEItemStack option) { if (option != null) count += option.getStackSize(); }
		@Override public long getStackSize() { return count; }
		@Override public IAEItemStack setStackSize(long stackSize) { this.count = stackSize; return this; }
		@Override public long getCountRequestable() { return 0; }
		@Override public IAEItemStack setCountRequestable(long countRequestable) { return this; }
		@Override public boolean isCraftable() { return false; }
		@Override public IAEItemStack setCraftable(boolean isCraftable) { return this; }
		@Override public IAEItemStack reset() { count = 0; return this; }
		@Override public boolean isMeaningful() { return count > 0; }
		@Override public void incStackSize(long i) { count += i; }
		@Override public void decStackSize(long i) { count -= i; }
		@Override public void incCountRequestable(long i) {}
		@Override public void decCountRequestable(long i) {}
		@Override public void writeToNBT(NBTTagCompound i) {}
		@Override public void writeToPacket(ByteBuf i) {}
		@Override public IAEItemStack copy() { return new StubAEItemStack(template, count); }
		@Override public IAEItemStack empty() { return new StubAEItemStack(template, 0); }
		@Override public IAETagCompound getTagCompound() { return null; }
		@Override public boolean isItem() { return true; }
		@Override public boolean isFluid() { return false; }
		@Override public StorageChannel getChannel() { return StorageChannel.ITEMS; }
		@Override public Item getItem() { return template.getItem(); }
		@Override public int getItemDamage() { return template.getItemDamage(); }
		@Override public boolean sameOre(IAEItemStack is) { return false; }
		@Override public boolean isSameType(IAEItemStack otherStack) { return otherStack != null && isSameType(otherStack.getItemStack()); }
		@Override public boolean isSameType(ItemStack otherStack) { return otherStack != null && template.getItem() == otherStack.getItem() && template.getItemDamage() == otherStack.getItemDamage(); }
		@Override public boolean isSameType(Object other) { return other instanceof ItemStack ? isSameType((ItemStack) other) : (other instanceof IAEItemStack && isSameType((IAEItemStack) other)); }
		@Override public boolean fuzzyComparison(Object st, FuzzyMode mode) { return false; }
		@Override public String getLocalizedName() { return ""; }
		@Override public String getUnlocalizedName() { return ""; }
		@Override public String getDisplayName() { return ""; }
		@Override public String getModId() { return ""; }
		@Override public void setTagCompound(NBTTagCompound tag) { template.setTagCompound(tag); }
		@Override public ItemStack getItemStackForNEI() { return getItemStack(); }
		@Override public void drawInGui(Minecraft mc, int x, int y) {}
		@Override public void drawOverlayInGui(Minecraft mc, int x, int y, boolean showAmount, boolean showAmountAlways, boolean showCraftable, boolean showCraftableAlways) {}
		@Override public void drawOnBlockFace(World world) {}
		@Override public int getAmountPerUnit() { return 1; }
		@Override public IAEStackType<IAEItemStack> getStackType() { return null; }
	}

	private static final class StoredEntry {
		final ItemStack template;
		long count;

		StoredEntry(ItemStack template, long count) {
			this.template = template;
			this.count = count;
		}
	}

	private static final class StubPartHostTile extends TileEntity implements IPartHost {
		final Map<ForgeDirection, IPart> parts = new EnumMap<ForgeDirection, IPart>(ForgeDirection.class);

		@Override
		public IPart getPart(ForgeDirection dir) {
			return parts.get(dir);
		}

		@Override public IFacadeContainer getFacadeContainer() { return null; }
		@Override public boolean canAddPart(ItemStack part, ForgeDirection side) { return false; }
		@Override public ForgeDirection addPart(ItemStack part, ForgeDirection side, EntityPlayer owner) { return null; }
		@Override public void removePart(ForgeDirection side, boolean suppressUpdate) {}
		@Override public void markForUpdate() {}
		@Override public DimensionalCoord getLocation() { return null; }
		@Override public TileEntity getTile() { return this; }
		@Override public AEColor getColor() { return AEColor.Transparent; }
		@Override public void clearContainer() {}
		@Override public boolean isBlocked(ForgeDirection side) { return false; }
		@Override public SelectedPart selectPart(Vec3 pos) { return null; }
		@Override public void markForSave() {}
		@Override public void partChanged() {}
		@Override public boolean hasRedstone(ForgeDirection side) { return false; }
		@Override public boolean isEmpty() { return parts.isEmpty(); }
		@Override public Set<LayerFlags> getLayerFlags() { return null; }
		@Override public void cleanup() {}
		@Override public void notifyNeighbors() {}
		@Override public boolean isInWorld() { return false; }
	}
}
