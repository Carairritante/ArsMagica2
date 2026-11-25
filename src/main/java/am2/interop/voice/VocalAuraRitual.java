package am2.interop.voice;

import am2.AMCore;
import am2.RitualShapeHelper;
import am2.api.blocks.MultiblockStructureDefinition;
import am2.api.math.AMVector3;
import am2.api.spell.component.interfaces.IRitualInteraction;
import am2.blocks.BlocksCommonProxy;
import am2.blocks.tileentities.TileEntityVocalAura;
import am2.items.ItemEssence;
import am2.items.ItemOre;
import am2.items.ItemsCommonProxy;
import am2.particles.AMParticle;
import am2.particles.ParticleFloatUpward;
import am2.skills.VocalResonance;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import java.util.List;

/**
 * Ritual of Vocal Resonance: awakens a floating Vocal Aura over a Quartz Block focus
 * surrounded by Wizard Chalk and Warding Candles when Silence is cast by a Level 90+ mage
 * who has unlocked the VocalResonance talent.
 */
public final class VocalAuraRitual implements IRitualInteraction {

	public static final VocalAuraRitual INSTANCE = new VocalAuraRitual();

	private VocalAuraRitual() {
	}

	@Override
	public ItemStack[] getReagents() {
		return new ItemStack[]{
				new ItemStack(ItemsCommonProxy.essence, 1, ItemEssence.META_ARCANE),
				new ItemStack(ItemsCommonProxy.essence, 1, ItemEssence.META_AIR),
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemOre.META_PURIFIEDVINTEUM),
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemOre.META_CHIMERITE)
		};
	}

	@Override
	public int getReagentSearchRadius() {
		return 3;
	}

	@Override
	public MultiblockStructureDefinition getRitualShape() {
		return RitualShapeHelper.instance.vocalResonance;
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
					if (world.getBlock(cx, cy, cz) == Blocks.quartz_block
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
		if (world.getBlock(cx, cy, cz) != Blocks.quartz_block) {
			return false;
		}

		if (!(caster instanceof EntityPlayer) || !VocalResonance.isUnlocked((EntityPlayer) caster)) {
			if (!world.isRemote && caster instanceof EntityPlayer) {
				((EntityPlayer) caster).addChatMessage(new ChatComponentTranslation("am2.vocal_aura.locked"));
			}
			return false;
		}

		ItemStack[] match = RitualShapeHelper.instance.checkForRitual(INSTANCE, world, cx, cy, cz);
		if (match == null) {
			if (!world.isRemote && caster instanceof EntityPlayer) {
				((EntityPlayer) caster).addChatMessage(new ChatComponentTranslation("am2.vocal_aura.ritual_missing_reagents"));
			}
			return false;
		}

		if (!world.isRemote) {
			consumeMatchedReagents(world, cx, cy, cz, INSTANCE.getReagentSearchRadius(), INSTANCE.getReagents());
			RitualShapeHelper.instance.consumeRitualShape(INSTANCE, world, cx, cy, cz);
			world.setBlockToAir(cx, cy, cz);

			int placeY = world.isAirBlock(cx, cy + 1, cz) ? (cy + 1) : cy;
			world.setBlock(cx, placeY, cz, BlocksCommonProxy.vocalAura, 0, 3);
			TileEntity te = world.getTileEntity(cx, placeY, cz);
			if (te instanceof TileEntityVocalAura) {
				((TileEntityVocalAura) te).setPlacedByUsername(((EntityPlayer) caster).getCommandSenderName());
			}
			world.playSoundEffect(cx + 0.5, placeY + 0.5, cz + 0.5, "arsmagica2:misc.craftingaltar.create_item", 1.0F, 1.15F);
		} else {
			for (int i = 0; i < 28; i++) {
				AMParticle particle = (AMParticle) AMCore.proxy.particleManager.spawn(
						world, "sparkle",
						cx + 0.5 + (world.rand.nextDouble() - 0.5) * 1.4,
						cy + 0.4 + world.rand.nextDouble() * 1.2,
						cz + 0.5 + (world.rand.nextDouble() - 0.5) * 1.4);
				if (particle != null) {
					particle.AddParticleController(new ParticleFloatUpward(particle, 0.02f, 0.04f, 1, false));
					particle.setMaxAge(30);
					particle.setRGBColorF(0.25f, 0.92f, 1.0f);
				}
			}
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	private static void consumeMatchedReagents(World world, int x, int y, int z, int r, ItemStack[] reagents) {
		List<EntityItem> entities = world.getEntitiesWithinAABB(
				EntityItem.class,
				AxisAlignedBB.getBoundingBox(x - r, y, z - r, x + r + 1, y + 1, z + r + 1));
		for (ItemStack req : reagents) {
			for (EntityItem entity : entities) {
				if (entity.isDead) continue;
				ItemStack stack = entity.getEntityItem();
				if (stack == null || stack.stackSize <= 0) continue;
				boolean metaMatch = (req.getItemDamage() == 32767) || (stack.getItemDamage() == req.getItemDamage());
				if (stack.getItem() == req.getItem() && metaMatch) {
					stack.stackSize--;
					if (stack.stackSize <= 0) {
						entity.setDead();
					} else {
						entity.setEntityItemStack(stack);
					}
					break;
				}
			}
		}
	}
}
