package am2.spell.components;

import am2.api.spell.component.interfaces.ISpellComponent;
import am2.api.spell.enums.Affinity;
import am2.api.spell.enums.SpellModifiers;
import am2.blocks.BlocksCommonProxy;
import am2.entities.EntityThrownRock;
import am2.items.ItemsCommonProxy;
import am2.spell.SpellUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

public class FallingStar implements ISpellComponent{

	@Override
	public Object[] getRecipeItems(){
		return new Object[]{
				new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_ARCANE),
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemsCommonProxy.itemOre.META_ARCANEASH),
				new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_ARCANE),
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemsCommonProxy.itemOre.META_COSMICDUST),
				BlocksCommonProxy.manaBattery,
				Items.lava_bucket
		};
	}

	@Override
	public int getID(){
		return 65;
	}

	private boolean spawnStar(ItemStack spellStack, EntityLivingBase caster, Entity target, World world, double x, double y, double z){
		int damageMultiplier = SpellUtils.instance.getModifiedInt_Mul(15, spellStack, caster, target, world, 0, SpellModifiers.DAMAGE);

		if (!world.isRemote){
			double targetY = y - 50.0;
			float starDamage = 2.0f * damageMultiplier;

			// Star Fusion: Check if a shooting star already exists in this area (12-block radius)
			double fusionRadius = 12.0;
			List<EntityThrownRock> nearbyStars = world.getEntitiesWithinAABB(EntityThrownRock.class,
					AxisAlignedBB.getBoundingBox(x - fusionRadius, targetY - 10.0, z - fusionRadius, x + fusionRadius, targetY + 80.0, z + fusionRadius));

			for (EntityThrownRock rock : nearbyStars){
				if (rock.getIsShootingStar() && !rock.isDead){
					rock.addMergedStar(starDamage);
					return true;
				}
			}

			double maxSpawnY = Math.min((double)world.getActualHeight() - 5.0, targetY + 65.0);
			double spawnY = maxSpawnY;

			for (int checkY = (int)Math.floor(targetY) + 2; checkY <= (int)Math.floor(maxSpawnY); checkY++){
				if (!world.isAirBlock((int)Math.floor(x), checkY, (int)Math.floor(z))){
					spawnY = Math.max(targetY + 4.0, (double)checkY - 1.5);
					break;
				}
			}

			EntityThrownRock star = new EntityThrownRock(world);
			star.setPosition(x, spawnY, z);
			star.setShootingStar(starDamage);
			star.setThrowingEntity(caster);
			star.setSpellStack(spellStack.copy());
			star.motionY = -1.0;
			world.spawnEntityInWorld(star);
		}
		return true;
	}

	@Override
	public boolean applyEffectBlock(ItemStack stack, World world, int blockx, int blocky, int blockz, int blockFace, double impactX, double impactY, double impactZ, EntityLivingBase caster){
		return spawnStar(stack, caster, caster, world, impactX, impactY + 50, impactZ);
	}

	@Override
	public boolean applyEffectEntity(ItemStack stack, World world, EntityLivingBase caster, Entity target){
		return spawnStar(stack, caster, target, world, target.posX, target.posY + 50, target.posZ);
	}

	@Override
	public float manaCost(EntityLivingBase caster){
		return 400;
	}

	@Override
	public float burnout(EntityLivingBase caster){
		return 120;
	}

	@Override
	public ItemStack[] reagents(EntityLivingBase caster){
		return null;
	}

	@Override
	public void spawnParticles(World world, double x, double y, double z, EntityLivingBase caster, Entity target, Random rand, int colorModifier){
	}

	@Override
	public EnumSet<Affinity> getAffinity(){
		return EnumSet.of(Affinity.ARCANE);
	}

	@Override
	public float getAffinityShift(Affinity affinity){
		return 0.05f;
	}

}
