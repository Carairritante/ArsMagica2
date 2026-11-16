package am2.soulbound;

import java.util.ArrayList;
import java.util.List;

import am2.RitualShapeHelper;
import am2.api.blocks.MultiblockStructureDefinition;
import am2.api.spell.component.interfaces.IRitualInteraction;
import am2.items.ItemEssence;
import am2.items.ItemOre;
import am2.items.ItemsCommonProxy;
import am2.utility.InventoryUtilities;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

public final class TrueSoulboundRitual implements IRitualInteraction {

	public static final TrueSoulboundRitual INSTANCE = new TrueSoulboundRitual();

	private TrueSoulboundRitual() {
	}

	@Override
	public MultiblockStructureDefinition getRitualShape() {
		return RitualShapeHelper.instance.trueSoulbound;
	}

	@Override
	public ItemStack[] getReagents() {
		return new ItemStack[] {
				new ItemStack(ItemsCommonProxy.essence, 1, ItemEssence.META_PURE),
				new ItemStack(ItemsCommonProxy.essence, 1, ItemEssence.META_ENDER),
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemOre.META_PURIFIEDVINTEUM)
		};
	}

	@Override
	public int getReagentSearchRadius() {
		return 2;
	}

	public static int resolveRitualY(World world, int x, int y, int z) {
		int[] center = resolveRitualCenter(world, x, y, z);
		return center != null ? center[1] : -1;
	}

	public static int[] resolveRitualCenter(World world, int x, int y, int z) {
		if (world == null || RitualShapeHelper.instance == null || RitualShapeHelper.instance.trueSoulbound == null) {
			return null;
		}
		int[] dys = new int[] { 0, 1, -1 };
		for (int dy : dys) {
			if (RitualShapeHelper.instance.trueSoulbound.checkStructure(world, x, y + dy, z)) {
				return new int[] { x, y + dy, z };
			}
		}
		for (int dy : dys) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					if (RitualShapeHelper.instance.trueSoulbound.checkStructure(world, x + dx, y + dy, z + dz)) {
						return new int[] { x + dx, y + dy, z + dz };
					}
				}
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static boolean tryExecute(World world, int blockx, int blocky, int blockz, EntityLivingBase caster) {
		if (!(caster instanceof EntityPlayer)) {
			return false;
		}
		int[] center = resolveRitualCenter(world, blockx, blocky, blockz);
		if (center == null) {
			return false;
		}
		if (world.isRemote) {
			return true;
		}

		int cx = center[0];
		int ry = center[1];
		int cz = center[2];
		EntityPlayer player = (EntityPlayer) caster;
		int r = INSTANCE.getReagentSearchRadius();
		List<EntityItem> foundEntities = world.getEntitiesWithinAABB(
				EntityItem.class,
				AxisAlignedBB.getBoundingBox(cx - r, ry - 0.5, cz - r, cx + r + 1, ry + 1.5, cz + r + 1));

		List<ItemEntry> pool = new ArrayList<ItemEntry>();
		for (EntityItem ei : foundEntities) {
			if (ei != null && !ei.isDead && ei.getEntityItem() != null && ei.getEntityItem().stackSize > 0) {
				pool.add(new ItemEntry(ei, ei.getEntityItem().stackSize));
			}
		}

		ItemStack[] required = INSTANCE.getReagents();
		for (ItemStack catalyst : required) {
			boolean matched = false;
			for (ItemEntry entry : pool) {
				if (entry.remaining > 0 && InventoryUtilities.compareItemStacks(entry.entity.getEntityItem(), catalyst, true, false, true, true)) {
					entry.remaining--;
					entry.consumeCount++;
					matched = true;
					break;
				}
			}
			if (!matched) {
				TrueSoulboundHelper.notifyLocalized(player, "ritual_reagents", EnumChatFormatting.RED, "am2.truesoulbound.ritual_missing_reagents");
				return false;
			}
		}

		int extraStacks = 0;
		int extraTotalCount = 0;
		ItemEntry targetEntry = null;
		for (ItemEntry entry : pool) {
			if (entry.remaining > 0) {
				extraStacks++;
				extraTotalCount += entry.remaining;
				targetEntry = entry;
			}
		}

		if (extraStacks == 0 || extraTotalCount == 0 || targetEntry == null) {
			TrueSoulboundHelper.notifyLocalized(player, "ritual_target", EnumChatFormatting.RED, "am2.truesoulbound.ritual_no_target");
			return false;
		}
		if (extraStacks > 1 || extraTotalCount > 1) {
			TrueSoulboundHelper.notifyLocalized(player, "ritual_target", EnumChatFormatting.RED, "am2.truesoulbound.ritual_too_many_items");
			return false;
		}

		ItemStack rawTarget = targetEntry.entity.getEntityItem();
		if (TrueSoulboundHelper.isTrueSoulbound(rawTarget)) {
			if (TrueSoulboundHelper.isOwner(rawTarget, player)) {
				TrueSoulboundHelper.notifyLocalized(player, "ritual_target", EnumChatFormatting.YELLOW, "am2.truesoulbound.ritual_already_yours");
			} else {
				TrueSoulboundHelper.notifyLocalized(player, "ritual_target", EnumChatFormatting.DARK_RED, "am2.truesoulbound.ritual_owned_by_other");
			}
			return false;
		}

		ItemStack singleTarget = rawTarget.copy();
		singleTarget.stackSize = 1;
		if (!TrueSoulboundHelper.canBind(singleTarget)) {
			TrueSoulboundHelper.notifyLocalized(player, "ritual_target", EnumChatFormatting.RED, "am2.truesoulbound.ritual_invalid_target");
			return false;
		}

		ItemStack bound = TrueSoulboundHelper.bind(singleTarget, player, world.getTotalWorldTime());
		if (bound == null) {
			return false;
		}

		targetEntry.consumeCount++;
		for (ItemEntry entry : pool) {
			if (entry.consumeCount <= 0) {
				continue;
			}
			ItemStack stack = entry.entity.getEntityItem();
			if (stack.stackSize <= entry.consumeCount) {
				entry.entity.setDead();
			} else {
				stack.stackSize -= entry.consumeCount;
				entry.entity.setEntityItemStack(stack);
			}
		}

		RitualShapeHelper.instance.consumeRitualShape(INSTANCE, world, cx, ry, cz);

		if (!TrueSoulboundHelper.deliverToPlayerInventory(player, bound, player.inventory.currentItem)) {
			TrueSoulboundHelper.rescueToOwnerOrVault(world, bound, -1, true);
		} else {
			TrueSoulboundHelper.notifyLocalized(player, "ritual_success", EnumChatFormatting.LIGHT_PURPLE, "am2.truesoulbound.ritual_success", bound.getDisplayName());
		}

		world.playSoundAtEntity(player, "mob.endermen.portal", 1.0f, 1.2f);
		return true;
	}

	private static final class ItemEntry {
		final EntityItem entity;
		int remaining;
		int consumeCount;

		ItemEntry(EntityItem entity, int remaining) {
			this.entity = entity;
			this.remaining = remaining;
		}
	}
}
