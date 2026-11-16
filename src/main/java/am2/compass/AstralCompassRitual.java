package am2.compass;

import am2.AMCore;
import am2.RitualShapeHelper;
import am2.api.blocks.MultiblockStructureDefinition;
import am2.api.math.AMVector3;
import am2.api.spell.component.interfaces.IRitualInteraction;
import am2.items.ItemEssence;
import am2.items.ItemOre;
import am2.items.ItemsCommonProxy;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import java.util.List;

/**
 * Ritual of Astral Divination: binds a standard compass with spatial/ender essences
 * and moonstone over a Lapis Lazuli focus to forge the Astral Structure Compass.
 * Invoked by casting Mark on the ritual center.
 */
public final class AstralCompassRitual implements IRitualInteraction {

	public static final AstralCompassRitual INSTANCE = new AstralCompassRitual();

	private AstralCompassRitual() {
	}

	@Override
	public ItemStack[] getReagents() {
		return new ItemStack[]{
				new ItemStack(Items.compass, 1, 0),
				new ItemStack(ItemsCommonProxy.essence, 1, ItemEssence.META_ENDER),
				new ItemStack(ItemsCommonProxy.essence, 1, ItemEssence.META_ARCANE),
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemOre.META_MOONSTONE)
		};
	}

	@Override
	public int getReagentSearchRadius() {
		return 3;
	}

	@Override
	public MultiblockStructureDefinition getRitualShape() {
		return RitualShapeHelper.instance.astralDivination;
	}

	public static AMVector3 resolveRitualCenter(World world, int x, int y, int z) {
		if (world == null || INSTANCE.getRitualShape() == null) {
			return null;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					int cx = x + dx;
					int cy = y + dy;
					int cz = z + dz;
					if (world.getBlock(cx, cy, cz) == Blocks.lapis_block
							&& INSTANCE.getRitualShape().checkStructure(world, cx, cy, cz)) {
						return new AMVector3(cx, cy, cz);
					}
				}
			}
		}
		return null;
	}

	public static boolean tryExecute(World world, int cx, int cy, int cz, EntityLivingBase caster) {
		if (world == null || INSTANCE.getRitualShape() == null) {
			return false;
		}
		if (world.getBlock(cx, cy, cz) != Blocks.lapis_block) {
			return false;
		}

		ItemStack[] match = RitualShapeHelper.instance.checkForRitual(INSTANCE, world, cx, cy, cz);
		if (match == null) {
			if (!world.isRemote && caster instanceof EntityPlayer) {
				((EntityPlayer) caster).addChatMessage(new ChatComponentTranslation("am2.astral_compass.ritual_missing_reagents"));
			}
			return false;
		}

		if (!world.isRemote) {
			consumeMatchedReagents(world, cx, cy, cz, INSTANCE.getReagentSearchRadius(), INSTANCE.getReagents());
			RitualShapeHelper.instance.consumeRitualShape(INSTANCE, world, cx, cy, cz);
			world.setBlockToAir(cx, cy, cz);

			ItemStack output = new ItemStack(ItemsCommonProxy.astralCompass, 1, 0);
			if (caster instanceof EntityPlayer) {
				EntityPlayer player = (EntityPlayer) caster;
				if (!player.inventory.addItemStackToInventory(output)) {
					spawnOutputItem(world, cx + 0.5, cy + 1.15, cz + 0.5, output);
				} else {
					player.openContainer.detectAndSendChanges();
				}
				player.addChatMessage(new ChatComponentTranslation("am2.astral_compass.ritual_success"));
				if (player instanceof net.minecraft.entity.player.EntityPlayerMP) {
					am2.network.AMNetHandler.INSTANCE.sendCompendiumUnlockPacket((net.minecraft.entity.player.EntityPlayerMP) player, "astral_divination", false);
				}
			} else {
				spawnOutputItem(world, cx + 0.5, cy + 1.15, cz + 0.5, output);
			}

			world.playSoundEffect(cx + 0.5, cy + 0.5, cz + 0.5, "mob.endermen.portal", 0.9F, 1.15F);
			world.playSoundEffect(cx + 0.5, cy + 0.5, cz + 0.5, "random.levelup", 0.85F, 1.35F);
		} else {
			for (int i = 0; i < 24; i++) {
				double angle = (Math.PI * 2.0 * i) / 24.0;
				double ox = Math.cos(angle) * 1.4;
				double oz = Math.sin(angle) * 1.4;
				world.spawnParticle("portal", cx + 0.5 + ox, cy + 1.1, cz + 0.5 + oz, -ox * 0.25, 0.18, -oz * 0.25);
			}
		}

		return true;
	}

	private static void spawnOutputItem(World world, double x, double y, double z, ItemStack stack) {
		EntityItem drop = new EntityItem(world, x, y, z, stack);
		drop.motionX = 0;
		drop.motionY = 0.22;
		drop.motionZ = 0;
		drop.delayBeforeCanPickup = 10;
		world.spawnEntityInWorld(drop);
	}

	@SuppressWarnings("unchecked")
	private static void consumeMatchedReagents(World world, int cx, int cy, int cz, int radius, ItemStack[] reagents) {
		List<EntityItem> items = world.getEntitiesWithinAABB(
				EntityItem.class,
				AxisAlignedBB.getBoundingBox(cx - radius, cy, cz - radius, cx + radius + 1, cy + 2, cz + radius + 1));
		for (ItemStack required : reagents) {
			for (EntityItem entityItem : items) {
				if (entityItem.isDead) continue;
				ItemStack ground = entityItem.getEntityItem();
				if (ground == null || ground.stackSize <= 0) continue;
				if (ground.getItem() == required.getItem()
						&& (required.getItemDamage() == 32767 || ground.getItemDamage() == required.getItemDamage())) {
					ground.stackSize--;
					if (ground.stackSize <= 0) {
						entityItem.setDead();
					} else {
						entityItem.setEntityItemStack(ground);
					}
					break;
				}
			}
		}
	}
}
