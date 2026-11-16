package am2.spell.shapes;

import am2.AMCore;
import am2.api.power.PowerTypes;
import am2.api.spell.ItemSpellBase;
import am2.api.spell.component.interfaces.ISpellModifier;
import am2.api.spell.component.interfaces.ISpellShape;
import am2.api.spell.enums.Affinity;
import am2.api.spell.enums.SpellCastResult;
import am2.api.spell.enums.SpellModifiers;
import am2.blocks.BlocksCommonProxy;
import am2.entities.EntitySpellEffect;
import am2.items.ItemSpellBook;
import am2.items.ItemSpellStaff;
import am2.items.ItemsCommonProxy;
import am2.particles.AMParticle;
import am2.particles.AMParticleIcons;
import am2.particles.ParticleMoveOnHeading;
import am2.spell.SpellHelper;
import am2.spell.SpellUtils;
import am2.spell.modifiers.Colour;
import am2.utility.MathUtilities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.List;

public class Beam implements ISpellShape{

	public Beam(){
	}

	@Override
	public int getID(){
		return 1;
	}

	@Override
	public SpellCastResult beginStackStage(ItemSpellBase item, ItemStack stack, EntityLivingBase caster, EntityLivingBase target, World world, double x, double y, double z, int side, boolean giveXP, int useCount){

		boolean shouldApplyEffect = useCount % 10 == 0;

		double range = SpellUtils.instance.getModifiedDouble_Add(SpellModifiers.RANGE, stack, caster, target, world, 0);
		boolean targetWater = SpellUtils.instance.modifierIsPresent(SpellModifiers.TARGET_NONSOLID_BLOCKS, stack, 0);
		MovingObjectPosition mop = item.getMovingObjectPosition(caster, world, range, true, targetWater);

		SpellCastResult result = null;
		Vec3 beamHitVec = null;
		Vec3 spellVec = null;

		if (mop == null){
			beamHitVec = MathUtilities.extrapolateEntityLook(world, caster, range);
			spellVec = beamHitVec;
		}else if (mop.typeOfHit == MovingObjectType.ENTITY){
			if (shouldApplyEffect){
				Entity e = mop.entityHit;
				if (e instanceof EntityDragonPart && ((EntityDragonPart)e).entityDragonObj instanceof EntityLivingBase)
					e = (EntityLivingBase)((EntityDragonPart)e).entityDragonObj;
				result = SpellHelper.instance.applyStageToEntity(stack, caster, world, e, 0, giveXP);
				if (result != SpellCastResult.SUCCESS){
					return result;
				}
			}
			float rng = (float)mop.hitVec.distanceTo(Vec3.createVectorHelper(caster.posX, caster.posY, caster.posZ));
			beamHitVec = MathUtilities.extrapolateEntityLook(world, caster, rng);
			spellVec = beamHitVec;
		}else{
			if (shouldApplyEffect){
				result = SpellHelper.instance.applyStageToGround(stack, caster, world, mop.blockX, mop.blockY, mop.blockZ, mop.sideHit, mop.hitVec.xCoord, mop.hitVec.yCoord, mop.hitVec.zCoord, 0, giveXP);
				if (result != SpellCastResult.SUCCESS){
					return result;
				}
			}
			beamHitVec = mop.hitVec;
			spellVec = Vec3.createVectorHelper(mop.blockX, mop.blockY, mop.blockZ);
		}

		if (world.isRemote && beamHitVec != null){
			Affinity affinity = SpellUtils.instance.mainAffinityFor(stack);

			int color = -1;
			if (SpellUtils.instance.modifierIsPresent(SpellModifiers.COLOR, stack, 0)){
				ISpellModifier[] mods = SpellUtils.instance.getModifiersForStage(stack, 0);
				int ordinalCount = 0;
				for (ISpellModifier mod : mods){
					if (mod instanceof Colour){
						byte[] meta = SpellUtils.instance.getModifierMetadataFromStack(stack, mod, 0, ordinalCount++);
						color = (int)mod.getModifier(SpellModifiers.COLOR, null, null, null, meta);
					}
				}
			}

			AMCore.instance.proxy.particleManager.updateAnimeBeam(world, caster, affinity, beamHitVec.xCoord, beamHitVec.yCoord, beamHitVec.zCoord, color, useCount);
			for (int i = 0; i < AMCore.config.getGFXLevel() + 1; ++i){
				AMParticle particle = (AMParticle)AMCore.proxy.particleManager.spawn(world, AMParticleIcons.instance.getParticleForAffinity(affinity), beamHitVec.xCoord, beamHitVec.yCoord, beamHitVec.zCoord);
				if (particle != null){
					particle.setMaxAge(2);
					particle.setParticleScale(0.1f);
					particle.setIgnoreMaxAge(false);
					if (color != -1)
						particle.setRGBColorI(color);
					particle.AddParticleController(new ParticleMoveOnHeading(particle, world.rand.nextDouble() * 360, world.rand.nextDouble() * 360, world.rand.nextDouble() * 0.2 + 0.02f, 1, false));
				}
			}
		}

		if (result != null && spellVec != null && shouldApplyEffect){
			ItemStack newItemStack = SpellUtils.instance.popStackStage(stack);
			if (refreshExistingBeamZone(newItemStack, caster, target, world, spellVec.xCoord, spellVec.yCoord, spellVec.zCoord)){
				return SpellCastResult.SUCCESS_REDUCE_MANA;
			}
			return SpellHelper.instance.applyStackStage(newItemStack, caster, target, spellVec.xCoord, spellVec.yCoord, spellVec.zCoord, mop != null ? mop.sideHit : 0, world, true, giveXP, 0);
		}else{
			return SpellCastResult.SUCCESS_REDUCE_MANA;
		}
	}

	/** Refreshes an already-active Zone under a channeled Beam instead of stacking duplicate Zones every 10 ticks. */
	private boolean refreshExistingBeamZone(ItemStack nextStageStack, EntityLivingBase caster, EntityLivingBase target, World world, double x, double y, double z){
		if (nextStageStack == null || caster == null || world == null) return false;
		if (SpellUtils.instance.numStages(nextStageStack) == 0) return false;
		ISpellShape nextShape = SpellUtils.instance.getShapeForStage(nextStageStack, 0);
		if (!(nextShape instanceof Zone)) return false;

		int radius = SpellUtils.instance.getModifiedInt_Add(2, nextStageStack, caster, target, world, 0, SpellModifiers.RADIUS);
		double searchR = Math.max(3.5D, radius + 1.5D);
		@SuppressWarnings("unchecked")
		List<EntitySpellEffect> nearby = world.getEntitiesWithinAABB(
				EntitySpellEffect.class,
				AxisAlignedBB.getBoundingBox(x - searchR, y - 4.0D, z - searchR, x + searchR, y + 4.0D, z + searchR));
		int casterId = caster.getEntityId();
		for (EntitySpellEffect effect : nearby){
			if (!effect.isDead && effect.isZone() && (world.isRemote || effect.getCasterEntityID() == casterId)){
				if (!world.isRemote){
					int duration = SpellUtils.instance.getModifiedInt_Mul(100, nextStageStack, caster, target, world, 0, SpellModifiers.DURATION);
					effect.setTicksToExist(Math.max(effect.getTicksToExist(), effect.ticksExisted + Math.max(20, duration)));
				}
				return true;
			}
		}
		return false;
	}

	/** Returns true when the held spell or scroll begins with the Beam shape at stage 0. */
	public static boolean isBeamSpell(ItemStack stack){
		if (stack == null) return false;
		try{
			ItemStack spell = stack;
			if (stack.getItem() instanceof ItemSpellBook){
				spell = ((ItemSpellBook)stack.getItem()).GetActiveItemStack(stack);
			}else if (stack.getItem() instanceof ItemSpellStaff){
				if (stack.hasTagCompound() && stack.stackTagCompound.hasKey("spell_to_cast", 10)){
					spell = ItemStack.loadItemStackFromNBT(stack.stackTagCompound.getCompoundTag("spell_to_cast"));
				}else{
					return false;
				}
			}
			if (spell == null || !(spell.getItem() instanceof ItemSpellBase)) return false;
			ItemStack classic = SpellUtils.instance.constructSpellStack(spell);
			ItemStack check = classic != null ? classic : spell;
			if (SpellUtils.instance.numStages(check) > 0){
				return SpellUtils.instance.getShapeForStage(check, 0) instanceof Beam;
			}
		}catch (Throwable ignored){
		}
		return false;
	}

	@Override
	public boolean isChanneled(){
		return true;
	}

	@Override
	public Object[] getRecipeItems(){
		return new Object[]{
				ItemsCommonProxy.standardFocus,
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemsCommonProxy.itemOre.META_BLUETOPAZ),
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemsCommonProxy.itemOre.META_BLUETOPAZ),
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemsCommonProxy.itemOre.META_PURIFIEDVINTEUM),
				BlocksCommonProxy.aum,
				String.format("E:%d", PowerTypes.NEUTRAL.ID()), 500
		};
	}

	@Override
	public float manaCostMultiplier(ItemStack spellStack){
		int stages = SpellUtils.instance.numStages(spellStack);

		for (int i = 0; i < stages; ++i){
			ISpellShape shape = SpellUtils.instance.getShapeForStage(spellStack, i);
			if (shape.getClass() == Wave.class) {
				return 1.0f;
			}
		} return 0.1f;
	}

	@Override
	public boolean isTerminusShape(){
		return false;
	}

	@Override
	public boolean isPrincipumShape(){
		return false;
	}

	@Override
	public String getSoundForAffinity(Affinity affinity, ItemStack stack, World world){
		switch (affinity){
		case AIR:
			return "arsmagica2:spell.loop.air";
		case ARCANE:
			return "arsmagica2:spell.loop.arcane";
		case EARTH:
			return "arsmagica2:spell.loop.earth";
		case ENDER:
			return "arsmagica2:spell.loop.ender";
		case FIRE:
			return "arsmagica2:spell.loop.fire";
		case ICE:
			return "arsmagica2:spell.loop.ice";
		case LIFE:
			return "arsmagica2:spell.loop.life";
		case LIGHTNING:
			return "arsmagica2:spell.loop.lightning";
		case NATURE:
			return "arsmagica2:spell.loop.nature";
		case WATER:
			return "arsmagica2:spell.loop.water";
		case NONE:
		default:
			return "arsmagica2:spell.loop.none";
		}
	}
}
