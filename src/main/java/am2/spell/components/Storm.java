package am2.spell.components;

import am2.AMCore;
import am2.api.ArsMagicaApi;
import am2.api.spell.component.interfaces.ISpellComponent;
import am2.api.spell.enums.Affinity;
import am2.api.spell.enums.SpellModifiers;
import am2.entities.EntitySpellEffect;
import am2.items.ItemsCommonProxy;
import am2.particles.AMParticle;
import am2.particles.ParticleOrbitEntity;
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

public class Storm implements ISpellComponent{

	@Override
	public boolean applyEffectBlock(ItemStack stack, World world, int blockx, int blocky, int blockz, int blockFace, double impactX, double impactY, double impactZ, EntityLivingBase caster){
		activateOrbitalStorm(stack, world, caster, impactX, impactY, impactZ, false);
		return true;
	}

	@Override
	public boolean applyEffectEntity(ItemStack stack, World world, EntityLivingBase caster, Entity target){
		double tx = target != null ? target.posX : (caster != null ? caster.posX : 0);
		double ty = target != null ? target.posY : (caster != null ? caster.posY : 64);
		double tz = target != null ? target.posZ : (caster != null ? caster.posZ : 0);
		boolean followCaster = (caster != null && target == caster);
		activateOrbitalStorm(stack, world, caster, tx, ty, tz, followCaster);
		return true;
	}

	private void activateOrbitalStorm(ItemStack stack, World world, EntityLivingBase caster, double x, double y, double z, boolean followCaster){
		if (world == null || world.isRemote) return;

		if (!world.getWorldInfo().isRaining()){
			world.getWorldInfo().setRaining(true);
		}

		boolean hasLightningDmg = hasComponentInSpell(stack, LightningDamage.class) || hasComponentInSpell(stack, LightningStrike.class);
		int dmgMods = countModifiersInSpell(stack, SpellModifiers.DAMAGE);

		float baseDamage = hasLightningDmg ? (12.0f + dmgMods * 3.0f) : (5.0f + dmgMods * 2.0f);
		float finalDamage = caster != null ? SpellUtils.instance.modifyDamage(caster, baseDamage) : baseDamage;

		// Up to 15 chunks (15 * 16 = 240 blocks)
		float radius = 160.0f;

		int duration = 100;
		if (stack != null){
			duration = SpellUtils.instance.getModifiedInt_Mul(100, stack, caster, null, world, 0, SpellModifiers.DURATION);
			if (duration < 60) duration = 60;
		}

		int casterId = caster != null ? caster.getEntityId() : -1;
		AxisAlignedBB searchBox = AxisAlignedBB.getBoundingBox(x - 200.0, 0.0, z - 200.0, x + 200.0, 256.0, z + 200.0);
		@SuppressWarnings("unchecked")
		List<EntitySpellEffect> existingEffects = world.getEntitiesWithinAABB(EntitySpellEffect.class, searchBox);
		EntitySpellEffect activeStorm = null;

		for (EntitySpellEffect eff : existingEffects){
			if (eff.isOrbitalStorm() && !eff.isDead && eff.getCasterEntityID() == casterId){
				activeStorm = eff;
				break;
			}
		}

		if (activeStorm != null){
			activeStorm.refreshOrbitalStorm(x, y, z, duration, hasLightningDmg, finalDamage, radius, followCaster);
		}else{
			EntitySpellEffect storm = new EntitySpellEffect(world);
			storm.setPosition(x, y, z);
			storm.setOrbitalStorm(caster, stack, x, y, z, hasLightningDmg, finalDamage, radius, duration, followCaster);
			world.spawnEntityInWorld(storm);

			// Trigger ground epicentre dragon & lightning impact VFX for all nearby clients
			int impactMergeCount = hasLightningDmg ? 30 : 15;
			am2.network.AMNetHandler.INSTANCE.sendLightningImpactToClients(x, y, z, world, stack, impactMergeCount, 12.0);
		}
	}

	private boolean hasComponentInSpell(ItemStack stack, Class<? extends ISpellComponent> clazz){
		if (stack == null || !stack.hasTagCompound()) return false;
		int stages = SpellUtils.instance.numStages(stack);
		for (int s = 0; s < stages; s++){
			if (SpellUtils.instance.componentIsPresent(stack, clazz, s)){
				return true;
			}
		}
		return false;
	}

	private int countModifiersInSpell(ItemStack stack, SpellModifiers modifier){
		if (stack == null || !stack.hasTagCompound()) return 0;
		int total = 0;
		int stages = SpellUtils.instance.numStages(stack);
		for (int s = 0; s < stages; s++){
			total += SpellUtils.instance.countModifiers(modifier, stack, s);
		}
		return total;
	}

	@Override
	public float manaCost(EntityLivingBase caster){
		return 15;
	}

	@Override
	public float burnout(EntityLivingBase caster){
		return ArsMagicaApi.getBurnoutFromMana(manaCost(caster));
	}

	@Override
	public ItemStack[] reagents(EntityLivingBase caster){
		return null;
	}

	@Override
	public void spawnParticles(World world, double x, double y, double z, EntityLivingBase caster, Entity target, Random rand, int colorModifier){
		AMParticle particle = (AMParticle)AMCore.proxy.particleManager.spawn(world, "symbols", x, y - 1, z);
		if (particle != null){
			particle.AddParticleController(new ParticleOrbitEntity(particle, target, 0.2f, 2, false).SetTargetDistance(1));
			particle.setMaxAge(40);
			particle.setParticleScale(0.1f);
			if (colorModifier > -1){
				particle.setRGBColorF(((colorModifier >> 16) & 0xFF) / 255.0f, ((colorModifier >> 8) & 0xFF) / 255.0f, (colorModifier & 0xFF) / 255.0f);
			}
		}
	}

	@Override
	public EnumSet<Affinity> getAffinity(){
		return EnumSet.of(Affinity.LIGHTNING, Affinity.NATURE);
	}

	@Override
	public int getID(){
		return 52;
	}

	@Override
	public Object[] getRecipeItems(){
		return new Object[]{
				new ItemStack(ItemsCommonProxy.rune, 1, ItemsCommonProxy.rune.META_YELLOW),
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemsCommonProxy.itemOre.META_BLUETOPAZ),
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemsCommonProxy.itemOre.META_RAINROCKROSE),
				Items.ghast_tear
		};
	}

	@Override
	public float getAffinityShift(Affinity affinity){
		return 0.00001f;
	}
}
