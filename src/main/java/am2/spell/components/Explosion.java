package am2.spell.components;

import am2.AMCore;
import am2.api.spell.component.interfaces.ISpellComponent;
import am2.api.spell.enums.Affinity;
import am2.api.spell.enums.SpellModifiers;
import am2.items.ItemsCommonProxy;
import am2.particles.AMParticle;
import am2.entities.EntitySpellEffect;
import am2.spell.SpellUtils;
import net.minecraft.util.AxisAlignedBB;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFlower;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.Random;

public class Explosion implements ISpellComponent{

	private boolean triggerExplosion(ItemStack stack, World world, double x, double y, double z, EntityLivingBase caster, int bx, int by, int bz){
		int explosion = 2 + SpellUtils.instance.countModifiers(SpellModifiers.MINING_POWER, stack, 0);

		if (!world.isRemote){
			// Broad 64-block fusion radius: ensures all blocks in any size AoE merge into ONE central explosion
			double fusionRadius = 64.0;
			@SuppressWarnings("unchecked")
			List<EntitySpellEffect> nearby = world.getEntitiesWithinAABB(EntitySpellEffect.class,
					AxisAlignedBB.getBoundingBox(x - fusionRadius, y - 24.0, z - fusionRadius, x + fusionRadius, y + 24.0, z + fusionRadius));

			for (EntitySpellEffect effect : nearby){
				if (effect.isExplosionFusion() && !effect.isDead && (caster == null || effect.getCasterEntityID() == caster.getEntityId())){
					effect.addMergedExplosion(x, y, z, explosion, bx, by, bz);
					return true;
				}
			}

			EntitySpellEffect fusion = new EntitySpellEffect(world);
			fusion.setPosition(x, y, z);
			fusion.setExplosionFusion(caster, stack, explosion, bx, by, bz);
			world.spawnEntityInWorld(fusion);
		}
		return true;
	}

	@Override
	public boolean applyEffectBlock(ItemStack stack, World world, int blockx, int blocky, int blockz, int blockFace, double impactX, double impactY, double impactZ, EntityLivingBase caster){
		int targetX = blockx;
		int targetY = blocky;
		int targetZ = blockz;

		switch (blockFace){
		case 5:
			targetX++;
			break;
		case 2:
			targetZ--;
			break;
		case 3:
			targetZ++;
			break;
		case 4:
			targetX--;
			break;
		}

		return triggerExplosion(stack, world, targetX + 0.5, targetY + 0.5, targetZ + 0.5, caster, blockx, blocky, blockz);
	}

	@Override
	public boolean applyEffectEntity(ItemStack stack, World world, EntityLivingBase caster, Entity target){
		return triggerExplosion(stack, world, target.posX, target.posY, target.posZ, caster, (int)Math.floor(target.posX), (int)Math.floor(target.posY), (int)Math.floor(target.posZ));
	}

	@Override
	public float manaCost(EntityLivingBase caster){
		return 100;
	}

	@Override
	public float burnout(EntityLivingBase caster){
		return 25;
	}

	@Override
	public ItemStack[] reagents(EntityLivingBase caster){
		return null;
	}

	@Override
	public void spawnParticles(World world, double x, double y, double z, EntityLivingBase caster, Entity target, Random rand, int colorModifier){
		// Zero particle lag: custom client impact entity handles 4-tier visual effects
	}

	@Override
	public EnumSet<Affinity> getAffinity(){
		return EnumSet.of(Affinity.FIRE);
	}

	@Override
	public int getID(){
		return 92;
	}

	@Override
	public Object[] getRecipeItems(){
		return new Object[]{
				new ItemStack(ItemsCommonProxy.rune, 1, ItemsCommonProxy.rune.META_ORANGE),
				new ItemStack(ItemsCommonProxy.rune, 1, ItemsCommonProxy.rune.META_YELLOW),
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemsCommonProxy.itemOre.META_SUNSTONEFRAGMENT),
				Items.flint_and_steel,
				Items.gunpowder,
				new ItemStack(Blocks.tnt)
		};
	}

	@Override
	public float getAffinityShift(Affinity affinity){
		return 0.01f;
	}
}
