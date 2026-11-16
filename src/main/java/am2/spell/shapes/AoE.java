package am2.spell.shapes;

import am2.AMCore;
import am2.api.power.PowerTypes;
import am2.api.spell.ItemSpellBase;
import am2.api.spell.component.interfaces.ISpellModifier;
import am2.api.spell.component.interfaces.ISpellShape;
import am2.api.spell.enums.Affinity;
import am2.api.spell.enums.SpellCastResult;
import am2.api.spell.enums.SpellModifiers;
import am2.entities.EntitySpellProjectile;
import am2.items.ItemsCommonProxy;
import am2.particles.*;
import am2.spell.SpellHelper;
import am2.spell.SpellUtils;
import am2.spell.components.SpellDropConsolidator;
import am2.spell.modifiers.Colour;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.List;

public class AoE implements ISpellShape{

	@Override
	public int getID(){
		return 0;
	}

	@Override
	public SpellCastResult beginStackStage(ItemSpellBase item, ItemStack stack, EntityLivingBase caster, EntityLivingBase target, World world, double x, double y, double z, int side, boolean giveXP, int useCount){
		double radius = SpellUtils.instance.getModifiedDouble_Add(1, stack, caster, target, world, 0, SpellModifiers.RADIUS);
		radius = AoERadiusGuard.sanitize(radius);
		boolean showParticles = useCount == 0;

		List<Entity> entities = world.getEntitiesWithinAABB(Entity.class, AxisAlignedBB.getBoundingBox(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius));

		boolean appliedToAtLeastOneEntity = false;

		for (Entity e : entities){
			if (e == caster || e instanceof EntitySpellProjectile) continue;
			if (e instanceof EntityDragonPart && ((EntityDragonPart)e).entityDragonObj instanceof EntityLivingBase)
				e = (EntityLivingBase)((EntityDragonPart)e).entityDragonObj;
			if (SpellHelper.instance.applyStageToEntity(stack, caster, world, e, 0, giveXP) == SpellCastResult.SUCCESS)
				appliedToAtLeastOneEntity = true;
		}

		SpellCastResult result = null;
		if (side == 0 || side == 1){ //top/bottom
			int gravityMagnitude = SpellUtils.instance.countModifiers(SpellModifiers.GRAVITY, stack, 0);
			SpellDropConsolidator.beginBatch(world, Math.floor(x) + 0.5D, Math.ceil(y) + 0.5D, Math.floor(z) + 0.5D, false);
			try {
				result = applyStageHorizontal(stack, caster, world, (int)Math.floor(x), (int)Math.ceil(y), (int)Math.floor(z), side, (int)Math.floor(radius), gravityMagnitude, giveXP);
			} finally {
				SpellDropConsolidator.endBatch(world);
			}
		}else if (side == 2 || side == 3){ // +/- x
			SpellDropConsolidator.beginBatch(world, Math.floor(x) + 0.5D, Math.ceil(y) + 0.5D, Math.floor(z) + 0.5D, false);
			try {
				result = applyStageVerticalZ(stack, caster, world, (int)Math.floor(x), (int)Math.ceil(y), (int)Math.floor(z), side, (int)Math.floor(radius), giveXP);
			} finally {
				SpellDropConsolidator.endBatch(world);
			}
		}else if (side == 4 || side == 5){ // +/- z
			SpellDropConsolidator.beginBatch(world, Math.floor(x) + 0.5D, Math.ceil(y) + 0.5D, Math.floor(z) + 0.5D, false);
			try {
				result = applyStageVerticalX(stack, caster, world, (int)Math.floor(x), (int)Math.ceil(y), (int)Math.floor(z), side, (int)Math.floor(radius), giveXP);
			} finally {
				SpellDropConsolidator.endBatch(world);
			}
		}

		if (result == null && appliedToAtLeastOneEntity){
			result = SpellCastResult.SUCCESS;
		}

		if (result != null){
			if (!world.isRemote && result == SpellCastResult.SUCCESS && useCount == 0){
				sendAoEShockwaveVisual(stack, caster, target, world, x, y, z, side, (float)radius);
			}
			return result;
		}

		return SpellCastResult.EFFECT_FAILED;
	}

	private static void sendAoEShockwaveVisual(ItemStack stack, EntityLivingBase caster, EntityLivingBase target, World world, double x, double y, double z, int side, float radius){
		double vx = x;
		double vy = y;
		double vz = z;
		int visualSide = side;
		boolean exactBlockCoords = (x == Math.floor(x) && y == Math.floor(y) && z == Math.floor(z));
		if (side == 1){
			vx = exactBlockCoords ? x + 0.5D : x;
			vy = exactBlockCoords ? y + 1.03D : y + 0.03D;
			vz = exactBlockCoords ? z + 0.5D : z;
		}else if (side == 0){
			if (exactBlockCoords && (target == null || target == caster) && !world.isAirBlock((int)x, (int)y, (int)z)){
				vx = x + 0.5D;
				vy = y - 0.03D;
				vz = z + 0.5D;
				visualSide = 0;
			}else if (target != null && target != caster){
				vx = target.posX;
				vy = (target.boundingBox != null ? target.boundingBox.minY : target.posY) + 0.04D;
				vz = target.posZ;
				visualSide = 1;
			}else{
				vx = exactBlockCoords ? x + 0.5D : x;
				vy = y + 0.04D;
				vz = exactBlockCoords ? z + 0.5D : z;
				visualSide = 1;
			}
		}else if (side == 2){
			vx = exactBlockCoords ? x + 0.5D : x;
			vy = exactBlockCoords ? y + 0.5D : y;
			vz = z - 0.03D;
		}else if (side == 3){
			vx = exactBlockCoords ? x + 0.5D : x;
			vy = exactBlockCoords ? y + 0.5D : y;
			vz = exactBlockCoords ? z + 1.03D : z + 0.03D;
		}else if (side == 4){
			vx = x - 0.03D;
			vy = exactBlockCoords ? y + 0.5D : y;
			vz = exactBlockCoords ? z + 0.5D : z;
		}else if (side == 5){
			vx = exactBlockCoords ? x + 1.03D : x + 0.03D;
			vy = exactBlockCoords ? y + 0.5D : y;
			vz = exactBlockCoords ? z + 0.5D : z;
		}else{
			vx = exactBlockCoords ? x + 0.5D : x;
			vy = y + 0.04D;
			vz = exactBlockCoords ? z + 0.5D : z;
			visualSide = 1;
		}
		am2.network.AMNetHandler.INSTANCE.sendAoEShockwaveToClients(vx, vy, vz, visualSide, Math.max(0.75f, radius), world, stack);
	}

	private void spawnAoEParticles(ItemStack stack, EntityLivingBase caster, World world, double x, double y, double z, int radius){
		String pfxName = AMParticleIcons.instance.getParticleForAffinity(SpellUtils.instance.mainAffinityFor(stack));
		float speed = 0.08f * radius;

		int color = 0xFFFFFF;
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

		for (int i = 0; i < 360; i += AMCore.config.FullGFX() ? 45 : AMCore.config.LowGFX() ? 60 : 90){
			AMParticle effect = (AMParticle)AMCore.instance.proxy.particleManager.spawn(world, pfxName, x, y + 1.5f, z);
			if (effect != null){
				effect.setIgnoreMaxAge(false);
				effect.setMaxAge(8);
				effect.AddParticleController(new ParticleMoveOnHeading(effect, i, 0, speed, 1, false));
				effect.noClip = false;
				effect.setRGBColorI(color);
				effect.AddParticleController(new ParticleFadeOut(effect, 1, false).setFadeSpeed(0.14f).setKillParticleOnFinish(true));
			}
		}
	}

	private SpellCastResult applyStageHorizontal(ItemStack stack, EntityLivingBase caster, World world, int x, int y, int z, int face, int radius, int gravityMagnitude, boolean giveXP){

		for (int i = -radius; i <= radius; ++i){
			for (int j = -radius; j <= radius; ++j){
				int searchY = y;
				Block block = world.getBlock(x + i, searchY, z + j);
				int searchDist = 0;
				if (gravityMagnitude > 0){
					while (block == Blocks.air && searchDist < gravityMagnitude){
						searchY--;
						searchDist++;
						block = world.getBlock(x + i, searchY, z + j);
					}
				}
				if (block == Blocks.air) continue;
				SpellCastResult result = SpellHelper.instance.applyStageToGround(stack, caster, world, x + i, searchY, z + j, face, x + i, searchY, z + j, 0, giveXP);
				if (result != SpellCastResult.SUCCESS)
					return result;
			}
		}
		return SpellCastResult.SUCCESS;
	}

	private SpellCastResult applyStageVerticalX(ItemStack stack, EntityLivingBase caster, World world, int x, int y, int z, int face, int radius, boolean giveXP){
		for (int i = -radius; i <= radius; ++i){
			for (int j = -radius; j <= radius; ++j){
				int searchY = y;
				Block block = world.getBlock(x, searchY + i, z + j);
				if (block == Blocks.air) continue;
				SpellCastResult result = SpellHelper.instance.applyStageToGround(stack, caster, world, x, searchY + i, z + j, face, x, searchY + i, z + j, 0, giveXP);
				if (result != SpellCastResult.SUCCESS)
					return result;
			}
		}
		return SpellCastResult.SUCCESS;
	}

	private SpellCastResult applyStageVerticalZ(ItemStack stack, EntityLivingBase caster, World world, int x, int y, int z, int face, int radius, boolean giveXP){
		for (int i = -radius; i <= radius; ++i){
			for (int j = -radius; j <= radius; ++j){
				int searchY = y;
				Block block = world.getBlock(x + j, searchY + i, z);
				if (block == Blocks.air) continue;
				SpellCastResult result = SpellHelper.instance.applyStageToGround(stack, caster, world, x + j, searchY + i, z, face, x + j, searchY + i, z, 0, giveXP);
				if (result != SpellCastResult.SUCCESS)
					return result;
			}
		}
		return SpellCastResult.SUCCESS;
	}

	@Override
	public boolean isChanneled(){
		return false;
	}

	@Override
	public Object[] getRecipeItems(){
		return new Object[]{
				new ItemStack(ItemsCommonProxy.itemOre, 1, ItemsCommonProxy.itemOre.META_MOONSTONE),
				new ItemStack(ItemsCommonProxy.essence, 1, ItemsCommonProxy.essence.META_AIR),
				String.format("E:%d|%d|%d", PowerTypes.LIGHT.ID(), PowerTypes.NEUTRAL.ID(), PowerTypes.DARK.ID()), 1000,
				Blocks.tnt
		};
	}

	@Override
	public float manaCostMultiplier(ItemStack spellStack){
		int multiplier = 2;
		int radiusMods = 0;
		int stages = SpellUtils.instance.numStages(spellStack);
		for (int i = 0; i < stages; ++i){
			if (SpellUtils.instance.getShapeForStage(spellStack, i).getID() != this.getID()) continue;

			ISpellModifier[] mods = SpellUtils.instance.getModifiersForStage(spellStack, i);
			for (ISpellModifier modifier : mods){
				if (modifier.getAspectsModified().contains(SpellModifiers.RADIUS)){
					radiusMods++;
				}
			}
		}
		return multiplier * (radiusMods + 1);
	}

	@Override
	public boolean isTerminusShape(){
		return true;
	}

	@Override
	public boolean isPrincipumShape(){
		return false;
	}

	@Override
	public String getSoundForAffinity(Affinity affinity, ItemStack stack, World world){
		switch (affinity){
		case AIR:
			return "arsmagica2:spell.cast.air";
		case ARCANE:
			return "arsmagica2:spell.cast.arcane";
		case EARTH:
			return "arsmagica2:spell.cast.earth";
		case ENDER:
			return "arsmagica2:spell.cast.ender";
		case FIRE:
			return "arsmagica2:spell.cast.fire";
		case ICE:
			return "arsmagica2:spell.cast.ice";
		case LIFE:
			return "arsmagica2:spell.cast.life";
		case LIGHTNING:
			return "arsmagica2:spell.cast.lightning";
		case NATURE:
			return "arsmagica2:spell.cast.nature";
		case WATER:
			return "arsmagica2:spell.cast.water";
		case NONE:
		default:
			return "arsmagica2:spell.cast.none";
		}
	}
}
