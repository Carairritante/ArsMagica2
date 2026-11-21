package am2.entities;

import am2.AMCore;
import am2.api.math.AMLineSegment;
import am2.api.math.AMVector3;
import am2.api.spell.component.interfaces.ISpellModifier;
import am2.api.spell.enums.Affinity;
import am2.api.spell.enums.SpellModifiers;
import am2.buffs.BuffEffectFrostSlowed;
import am2.damage.DamageSources;
import net.minecraft.util.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import am2.network.AMNetHandler;
import am2.particles.*;
import am2.spell.SpellHelper;
import am2.spell.SpellUtils;
import am2.spell.components.SpellDropConsolidator;
import am2.spell.modifiers.Colour;
import am2.utility.DummyEntityPlayer;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.monster.IMob;
import net.minecraft.util.MathHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class EntitySpellEffect extends Entity{

	private float rotation;
	private final float rotationSpeed;

	private int ticksToEffect = 20;
	private int maxTicksToEffect = 20;
	private int maxTicksToEffect_wall = 5;

	private int ticksToExist = 100;

	private ItemStack spellStack;
	private EntityPlayer dummycaster;
	private int casterEntityID = -1;
	private float moveSpeed;    //used by waves only

	private static final int WATCHER_STACK = 22;
	private static final int WATCHER_RADIUS = 23;
	private static final int WATCHER_GRAVITY = 24;
	private static final int WATCHER_TYPE = 25; //0 == zone, 1 == rain of fire, 2 == blizzard, 3 == wall, 4 == wave
	private static final int WATCHER_ROF_IGNITE = 26;
	private static final int WATCHER_DAMAGEBONUS = 27;
	private static final int WATCHER_TICKS_TO_EXIST = 28;

	private static final int TYPE_ZONE = 0;
	private static final int TYPE_ROF = 1;
	private static final int TYPE_BLIZ = 2;
	private static final int TYPE_WALL = 3;
	private static final int TYPE_WAVE = 4;
	public static final int TYPE_STARFALL_IMPACT = 5;
	public static final int TYPE_GROW_MANDALA = 6;
	public static final int TYPE_EXPLOSION_IMPACT = 7;
	public static final int TYPE_EXPLOSION_FUSION = 8;
	public static final int TYPE_LIGHTNING_FUSION = 9;
	public static final int TYPE_LIGHTNING_IMPACT = 10;
	public static final int TYPE_ORBITAL_STORM = 11;
	public static final int TYPE_AOE_SHOCKWAVE = 12;
	public static final int TYPE_TORNADO = 13;

	private int explicitColor = -1;

	private boolean firstApply = true;

	public EntitySpellEffect(World par1World){
		super(par1World);
		this.rotation = 0;
		this.rotationSpeed = 10f;
		this.setSize(0.25f, 0.25f);
		this.ignoreFrustumCheck = true;
		this.renderDistanceWeight = 10.0D;
	}

	public void SetCasterAndStack(EntityLivingBase caster, ItemStack spellScroll){
		this.spellStack = spellScroll;
		// Unowned effects use the same neutral context as Zone/Wall. Do not pass
		// null into DummyEntityPlayer or associate the effect with entity zero.
		this.dummycaster = DummyEntityPlayer.fromEntityLiving(caster != null ? caster : new EntityDummyCaster(worldObj));
		casterEntityID = caster != null ? caster.getEntityId() : -1;
		if (spellStack != null)
			this.dataWatcher.updateObject(WATCHER_STACK, spellStack);
	}

	public void setRadius(float newRadius){
		this.dataWatcher.updateObject(WATCHER_RADIUS, newRadius);
	}

	public float getRadius(){
		return this.dataWatcher.getWatchableObjectFloat(WATCHER_RADIUS);
	}

	public void setTickRate(int newTickRate){
		this.maxTicksToEffect = newTickRate;
		this.ticksToEffect = newTickRate;
	}
	private int wavePulsesRemaining = -1;
	private int wavePulseInterval = 1;

	public void setWavePulseLimit(int maxPulses){
		this.wavePulsesRemaining = maxPulses;
		int duration = this.getTicksToExist();
		this.wavePulseInterval = Math.max(1, duration / maxPulses);
		this.ticksToEffect = 0;
	}


	public void setTicksToExist(int ticks){
		this.ticksToExist = ticks;
		this.dataWatcher.updateObject(WATCHER_TICKS_TO_EXIST, ticks);
	}

	public int getTicksToExist(){
		int dw = this.dataWatcher.getWatchableObjectInt(WATCHER_TICKS_TO_EXIST);
		if (dw > 0) return dw;
		if (this.spellStack == null){
			this.spellStack = this.dataWatcher.getWatchableObjectItemStack(WATCHER_STACK);
		}
		if (this.spellStack != null){
			try{
				int calcDuration = SpellUtils.instance.getModifiedInt_Mul(100, this.spellStack, this.dummycaster, null, worldObj, 0, SpellModifiers.DURATION);
				if (calcDuration > 0) return calcDuration;
			}catch (Throwable ignored){}
		}
		return this.ticksToExist > 0 ? this.ticksToExist : 100;
	}

	public void setExplicitColor(int color){
		this.explicitColor = color;
	}

	public Affinity getZoneAffinity(){
		if (spellStack == null){
			spellStack = this.dataWatcher.getWatchableObjectItemStack(WATCHER_STACK);
		}
		if (spellStack != null){
			return SpellUtils.instance.mainAffinityFor(spellStack);
		}
		return Affinity.NONE;
	}

	public int getSpellColor(){
		if (this.explicitColor != -1){
			return this.explicitColor;
		}
		if (spellStack == null){
			spellStack = this.dataWatcher.getWatchableObjectItemStack(WATCHER_STACK);
		}
		if (spellStack != null && SpellUtils.instance.modifierIsPresent(SpellModifiers.COLOR, spellStack, 0)){
			ISpellModifier[] mods = SpellUtils.instance.getModifiersForStage(spellStack, 0);
			int ordinalCount = 0;
			for (ISpellModifier mod : mods){
				if (mod instanceof Colour){
					byte[] meta = SpellUtils.instance.getModifierMetadataFromStack(spellStack, mod, 0, ordinalCount++);
					return (int)mod.getModifier(SpellModifiers.COLOR, null, null, null, meta);
				}
			}
		}
		return -1;
	}

	public void setGravity(double gravity){
		dataWatcher.updateObject(WATCHER_GRAVITY, (int)Math.floor(gravity * 100));
	}

	public void setDamageBonus(float damageBonus){
		this.dataWatcher.updateObject(WATCHER_DAMAGEBONUS, damageBonus);
	}

	public float getRotation(){
		return this.rotation;
	}

	public void setWall(float rotation){
		this.setRotation(rotation, 0);
		this.dataWatcher.updateObject(WATCHER_TYPE, TYPE_WALL);
	}

	public void setWave(float rotation, float speed){
		this.setRotation(rotation, 0);
		this.dataWatcher.updateObject(WATCHER_TYPE, TYPE_WAVE);
		this.moveSpeed = WaveTravelLimiter.limitSpeed(speed);
		this.ticksToExist = WaveTravelLimiter.limitDuration(this.ticksToExist, this.moveSpeed);
		this.stepHeight = 0.5f;
		maxTicksToEffect_wall = 1;
	}

	@Override
	protected void entityInit(){
		this.dataWatcher.addObject(WATCHER_RADIUS, 3f);
		this.dataWatcher.addObject(WATCHER_STACK, new ItemStack(Items.golden_apple));
		this.dataWatcher.addObject(WATCHER_GRAVITY, 0);
		this.dataWatcher.addObject(WATCHER_TYPE, 0);
		this.dataWatcher.addObject(WATCHER_ROF_IGNITE, (byte)0);
		this.dataWatcher.addObject(WATCHER_DAMAGEBONUS, 1.0f);
		this.dataWatcher.addObject(WATCHER_TICKS_TO_EXIST, -1);
	}

	@Override
	public void onUpdate(){

		if (dummycaster != null && dummycaster instanceof DummyEntityPlayer)
			dummycaster.onUpdate();

		switch (this.dataWatcher.getWatchableObjectInt(WATCHER_TYPE)){
		case TYPE_ZONE:
			zoneUpdate();
			break;
		case TYPE_ROF:
			rainOfFireUpdate();
			break;
		case TYPE_BLIZ:
			blizzardUpdate();
			break;
		case TYPE_WALL:
			wallUpdate();
			break;
		case TYPE_WAVE:
			waveUpdate();
			break;
		case TYPE_EXPLOSION_FUSION:
			detonateExplosionFusion();
			break;
		case TYPE_LIGHTNING_FUSION:
			detonateLightningFusion();
			break;
		case TYPE_LIGHTNING_IMPACT:
			lightningImpactUpdate();
			break;
		case TYPE_ORBITAL_STORM:
			orbitalStormUpdate();
			break;
		case TYPE_TORNADO:
			tornadoUpdate();
			break;
		}

		if ((!worldObj.isRemote || isStarfallImpact() || isGrowMandala() || isExplosionImpact() || isLightningImpact() || isAoEShockwave()) && this.ticksExisted >= this.getTicksToExist()){
			this.setDead();
		}
	}

	@Override
	public void setDead(){
		if (dummycaster instanceof DummyEntityPlayer)
			dummycaster.setDead();
		super.setDead();
	}

	private void zoneUpdate(){
		if (this.worldObj.isRemote){
			if (!AMCore.config.NoGFX()){
				this.rotation += this.rotationSpeed;
				this.rotation %= 360;
			}
		}

		this.moveEntity(0, this.dataWatcher.getWatchableObjectInt(WATCHER_GRAVITY) / 100.0f, 0);

		ticksToEffect--;
		if (spellStack == null){
			if (!worldObj.isRemote){
				this.setDead();
			}
			return;
		}
		if (dummycaster == null){
			dummycaster = DummyEntityPlayer.fromEntityLiving(new EntityDummyCaster(worldObj));
		}
		if (ticksToEffect <= 0){
			ticksToEffect = maxTicksToEffect;
			float radius = this.dataWatcher.getWatchableObjectFloat(WATCHER_RADIUS);
			if (!worldObj.isRemote){
				List<Entity> possibleTargets = worldObj.getEntitiesWithinAABB(Entity.class, AxisAlignedBB.getBoundingBox(posX - radius, posY - 1, posZ - radius, posX + radius, posY + 3, posZ + radius));
				for (Entity e : possibleTargets){
					if (e instanceof EntityDragonPart && ((EntityDragonPart)e).entityDragonObj instanceof EntityLivingBase)
						e = (EntityLivingBase)((EntityDragonPart)e).entityDragonObj;

					if (e instanceof EntityLivingBase)
						SpellHelper.instance.applyStageToEntity(spellStack, dummycaster, worldObj, e, 0, false);
				}
				if (this.dataWatcher.getWatchableObjectInt(WATCHER_GRAVITY) < 0 && !firstApply)
					SpellHelper.instance.applyStackStage(spellStack, dummycaster, null, posX, posY - 1, posZ, 0, worldObj, false, false, this.ticksExisted);
				else
					SpellHelper.instance.applyStackStage(spellStack, dummycaster, null, posX, posY, posZ, 0, worldObj, false, false, this.ticksExisted);
				firstApply = false;
			}
		}
	}

	private void rainOfFireUpdate(){
		float radius = this.dataWatcher.getWatchableObjectFloat(WATCHER_RADIUS);
		if (worldObj.isRemote){

			if (spellStack == null){
				spellStack = this.dataWatcher.getWatchableObjectItemStack(22);
				if (spellStack == null){
					return;
				}
			}

			int color = 0xFFFFFF;
			if (SpellUtils.instance.modifierIsPresent(SpellModifiers.COLOR, spellStack, 0)){
				ISpellModifier[] mods = SpellUtils.instance.getModifiersForStage(spellStack, 0);
				int ordinalCount = 0;
				for (ISpellModifier mod : mods){
					if (mod instanceof Colour){
						byte[] meta = SpellUtils.instance.getModifierMetadataFromStack(spellStack, mod, 0, ordinalCount++);
						color = (int)mod.getModifier(SpellModifiers.COLOR, null, null, null, meta);
					}
				}
			}

			if (rand.nextInt(3) == 0){
				double x = this.posX - radius + (rand.nextDouble() * radius * 2);
				double z = this.posZ - radius + (rand.nextDouble() * radius * 2);
				double y = this.posY + 0.5 + rand.nextDouble() * 1.5;

				AMParticle particle = (AMParticle)AMCore.proxy.particleManager.spawn(worldObj, "ember", x, y, z);
				if (particle != null){
					particle.setMaxAge(16);
					particle.addVelocity((rand.nextDouble() - 0.5) * 0.05f, 0.04f, (rand.nextDouble() - 0.5) * 0.05f);
					particle.setDontRequireControllers();
					int col = getSpellColor();
					if (col != -1) particle.setRGBColorI(col);
					else particle.setRGBColorI(0xFF6600);
					particle.noClip = false;
				}
			}

			//TODO: SoundHelper.instance.loopSound(worldObj, (float)posX, (float)posY, (float)posZ, "arsmagica2:spell.loop.fire", 1.0f);
		}else{
			List<Entity> possibleTargets = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, AxisAlignedBB.getBoundingBox(posX - radius, posY - 1, posZ - radius, posX + radius, posY + 3, posZ + radius));
			for (Entity e : possibleTargets){
				if (e != dummycaster){
					if (e instanceof EntityDragonPart && ((EntityDragonPart)e).entityDragonObj instanceof EntityLivingBase)
						e = (EntityLivingBase)((EntityDragonPart)e).entityDragonObj;

					double lastVelX = e.motionX;
					double lastVelY = e.motionY;
					double lastVelZ = e.motionZ;

					float damage = 0.75f * this.dataWatcher.getWatchableObjectFloat(WATCHER_DAMAGEBONUS);

					if (SpellHelper.instance.attackTargetSpecial(null, e, DamageSources.causeEntityFireDamage(dummycaster), damage) && !(e instanceof EntityPlayer))
						e.hurtResistantTime = 10;
					e.addVelocity(-(e.motionX - lastVelX), -(e.motionY - lastVelY), -(e.motionZ - lastVelZ));
				}
			}
			if (this.dataWatcher.getWatchableObjectByte(WATCHER_ROF_IGNITE) == 1 && rand.nextInt(10) < 2){
				int pX = (int)(posX - radius + rand.nextInt((int)Math.ceil(radius) * 2));
				int pY = (int)posY;
				int pZ = (int)(posZ - radius + rand.nextInt((int)Math.ceil(radius) * 2));
				if (worldObj.isAirBlock(pX, pY, pZ))
					worldObj.setBlock(pX, pY, pZ, Blocks.fire);
			}
		}
	}

	private void blizzardUpdate(){
		float radius = this.dataWatcher.getWatchableObjectFloat(WATCHER_RADIUS);
		if (worldObj.isRemote){

			if (spellStack == null){
				spellStack = this.dataWatcher.getWatchableObjectItemStack(22);
				if (spellStack == null){
					return;
				}
			}

			int color = 0xFFFFFF;
			if (SpellUtils.instance.modifierIsPresent(SpellModifiers.COLOR, spellStack, 0)){
				ISpellModifier[] mods = SpellUtils.instance.getModifiersForStage(spellStack, 0);
				int ordinalCount = 0;
				for (ISpellModifier mod : mods){
					if (mod instanceof Colour){
						byte[] meta = SpellUtils.instance.getModifierMetadataFromStack(spellStack, mod, 0, ordinalCount++);
						color = (int)mod.getModifier(SpellModifiers.COLOR, null, null, null, meta);
					}
				}
			}

			if (rand.nextInt(3) == 0){
				double x = this.posX - radius + (rand.nextDouble() * radius * 2);
				double z = this.posZ - radius + (rand.nextDouble() * radius * 2);
				double y = this.posY + 0.5 + rand.nextDouble() * 2.5;

				AMParticle particle = (AMParticle)AMCore.proxy.particleManager.spawn(worldObj, "snowflakes", x, y, z);
				if (particle != null){
					particle.setMaxAge(18);
					particle.setParticleScale(0.12f);
					particle.addVelocity((rand.nextDouble() - 0.5) * 0.08f, -0.04f, (rand.nextDouble() - 0.5) * 0.08f);
					particle.setDontRequireControllers();
					int col = getSpellColor();
					if (col != -1) particle.setRGBColorI(col);
					else particle.setRGBColorI(0x99EEFF);
					particle.noClip = false;
				}
			}

			//TODO: SoundHelper.instance.loopSound(worldObj, (float)posX, (float)posY, (float)posZ, "arsmagica2:spell.loop.air", 1.0f);
		}else{
			List<Entity> possibleTargets = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, AxisAlignedBB.getBoundingBox(posX - radius, posY - 1, posZ - radius, posX + radius, posY + 3, posZ + radius));
			for (Entity e : possibleTargets){
				if (e != dummycaster){
					if (e instanceof EntityDragonPart && ((EntityDragonPart)e).entityDragonObj instanceof EntityLivingBase)
						e = (EntityLivingBase)((EntityDragonPart)e).entityDragonObj;

					if (e instanceof EntityLivingBase)
						((EntityLivingBase)e).addPotionEffect(new BuffEffectFrostSlowed(80, 3));

					float damage = 1 * this.dataWatcher.getWatchableObjectFloat(WATCHER_DAMAGEBONUS);

					double lastVelX = e.motionX;
					double lastVelY = e.motionY;
					double lastVelZ = e.motionZ;
					if (SpellHelper.instance.attackTargetSpecial(null, e, DamageSources.causeEntityFrostDamage(dummycaster), damage) && !(e instanceof EntityPlayer))
						e.hurtResistantTime = 15;
					e.addVelocity(-(e.motionX - lastVelX), -(e.motionY - lastVelY), -(e.motionZ - lastVelZ));
				}
			}

			if (rand.nextInt(10) < 2){
				int pX = (int)(posX - radius + rand.nextInt((int)Math.ceil(radius) * 2));
				int pY = (int)posY + rand.nextInt(2);
				int pZ = (int)(posZ - radius + rand.nextInt((int)Math.ceil(radius) * 2));
				if (worldObj.isAirBlock(pX, pY, pZ) && !worldObj.isAirBlock(pX, pY - 1, pZ) && worldObj.getBlock(pX, pY, pZ).isOpaqueCube())
					worldObj.setBlock(pX, pY, pZ, Blocks.snow);
			}
		}
	}

	private void wallUpdate(){
		if (worldObj.isRemote){
			if (spellStack == null){
				spellStack = this.dataWatcher.getWatchableObjectItemStack(22);
				if (spellStack == null){
					return;
				}
			}

			double dist = this.dataWatcher.getWatchableObjectFloat(WATCHER_RADIUS);

			int color = 0xFFFFFF;
			if (SpellUtils.instance.modifierIsPresent(SpellModifiers.COLOR, spellStack, 0)){
				ISpellModifier[] mods = SpellUtils.instance.getModifiersForStage(spellStack, 0);
				int ordinalCount = 0;
				for (ISpellModifier mod : mods){
					if (mod instanceof Colour){
						byte[] meta = SpellUtils.instance.getModifierMetadataFromStack(spellStack, mod, 0, ordinalCount++);
						color = (int)mod.getModifier(SpellModifiers.COLOR, null, null, null, meta);
					}
				}
			}

			double px = Math.cos(3.141 / 180 * (rotationYaw + 90)) * 0.1f;
			double pz = Math.sin(3.141 / 180 * (rotationYaw + 90)) * 0.1f;
			double py = 0.1f;

			for (float i = 0; i < dist; i += 0.5f){
				double x = this.posX - Math.cos(3.141 / 180 * (rotationYaw)) * i;
				double z = this.posZ - Math.sin(3.141 / 180 * (rotationYaw)) * i;

				AMParticle effect = (AMParticle)AMCore.instance.proxy.particleManager.spawn(worldObj, AMParticleIcons.instance.getParticleForAffinity(SpellUtils.instance.mainAffinityFor(spellStack)), x, posY, z);
				if (effect != null){
					effect.setIgnoreMaxAge(false);
					effect.setMaxAge(20);
					effect.addRandomOffset(1, 1, 1);
					effect.setParticleScale(0.15f);
					effect.setRGBColorI(color);
					if (dataWatcher.getWatchableObjectInt(WATCHER_TYPE) == TYPE_WALL){
						effect.AddParticleController(new ParticleFloatUpward(effect, 0, 0.07f, 1, false));
					}else{
						effect.setAffectedByGravity();
						effect.setDontRequireControllers();
						effect.addVelocity(px, py, pz);
					}
				}

				x = this.posX - Math.cos(Math.toRadians(rotationYaw)) * -i;
				z = this.posZ - Math.sin(Math.toRadians(rotationYaw)) * -i;

				effect = (AMParticle)AMCore.instance.proxy.particleManager.spawn(worldObj, AMParticleIcons.instance.getParticleForAffinity(SpellUtils.instance.mainAffinityFor(spellStack)), x, posY, z);
				if (effect != null){
					effect.setIgnoreMaxAge(false);
					effect.addRandomOffset(1, 1, 1);
					effect.setMaxAge(20);
					effect.setParticleScale(0.15f);
					effect.setRGBColorI(color);
					if (dataWatcher.getWatchableObjectInt(WATCHER_TYPE) == TYPE_WALL){
						effect.AddParticleController(new ParticleFloatUpward(effect, 0, 0.07f, 1, false));
					}else{
						effect.setAffectedByGravity();
						effect.setDontRequireControllers();
						effect.addVelocity(px, py, pz);
					}
				}
			}

		}else{

			ticksToEffect--;
			if (spellStack == null){
				if (!worldObj.isRemote){
					this.setDead();
				}
				return;
			}

			if (dummycaster == null){
				dummycaster = DummyEntityPlayer.fromEntityLiving(new EntityDummyCaster(worldObj));
			}
			if (ticksToEffect <= 0){
				ticksToEffect = maxTicksToEffect_wall;
				float radius = this.dataWatcher.getWatchableObjectFloat(WATCHER_RADIUS);
				List<Entity> possibleTargets = worldObj.getEntitiesWithinAABB(Entity.class, AxisAlignedBB.getBoundingBox(posX - radius, posY - 1, posZ - radius, posX + radius, posY + 3, posZ + radius));
				ItemStack newStack = SpellUtils.instance.popStackStage(spellStack);
				for (Entity e : possibleTargets){
					if (e == this || e == dummycaster || e.getEntityId() == casterEntityID) continue;

					if (e instanceof EntityDragonPart && ((EntityDragonPart)e).entityDragonObj instanceof EntityLivingBase)
						e = (EntityLivingBase)((EntityDragonPart)e).entityDragonObj;

					AMVector3 target = new AMVector3(e);

					double dirX = Math.cos(3.141 / 180 * (rotationYaw));
					double dirZ = Math.sin(3.141 / 180 * (rotationYaw));

					AMVector3 a = new AMVector3(this.posX - dirX * radius, this.posY, this.posZ - dirZ * radius);
					AMVector3 b = new AMVector3(this.posX - dirX * -radius, this.posY, this.posZ - dirZ * -radius);

					AMVector3 closest = new AMLineSegment(a, b).closestPointOnLine(target);

					closest.y = 0;
					target.y = 0;

					double hDistance = closest.distanceTo(target);
					double vDistance = Math.abs(this.posY - e.posY);

					if (e instanceof EntityLivingBase && hDistance < 0.75f && vDistance < 2){
						//commented out in favor of line below so as to apply subsequent shapes as well
						//uncomment and comment out below line to revert to direct target only, but mark wave/wall as terminus
						//SpellHelper.instance.applyStageToEntity(spellStack, dummycaster, worldObj, e, 0, false);
						SpellHelper.instance.applyStackStage(spellStack, dummycaster, (EntityLivingBase)e, this.posX, this.posY, this.posZ, 0, worldObj, false, false, 0);
					}
				}
			}
		}
	}

	private void waveUpdate(){
		boolean isLimited = this.wavePulsesRemaining >= 0;
		boolean doPulse = false;

		if (isLimited){
			if (wavePulsesRemaining > 0){
				ticksToEffect--;
				if (ticksToEffect <= 0){
					doPulse = true;
					wavePulsesRemaining--;
				}
			}
		} else {
			doPulse = true;
		}

		if (worldObj.isRemote){
			wallUpdate();
		} else if (doPulse){
			ticksToEffect = 0;
			wallUpdate();
			ticksToEffect = isLimited ? wavePulseInterval : 0;
		}

		double dx = Math.cos(Math.toRadians(this.rotationYaw + 90));
		double dz = Math.sin(Math.toRadians(this.rotationYaw + 90));

		this.moveEntity(dx * moveSpeed, 0, dz * moveSpeed);

		double dxH = Math.cos(Math.toRadians(this.rotationYaw));
		double dzH = Math.sin(Math.toRadians(this.rotationYaw));

		float radius = this.dataWatcher.getWatchableObjectFloat(WATCHER_RADIUS);

		AMVector3 a = new AMVector3((this.posX + dx) - dxH * radius, this.posY, (this.posZ + dz) - dzH * radius);
		AMVector3 b = new AMVector3((this.posX + dx) - dxH * -radius, this.posY, (this.posZ + dz) - dzH * -radius);

		if (dummycaster == null){
			dummycaster = DummyEntityPlayer.fromEntityLiving(new EntityDummyCaster(worldObj));
		}

		if (!worldObj.isRemote && doPulse){
			AMVector3[] vecs = getAllBlockLocationsBetween(a, b);
			SpellDropConsolidator.beginBatch(worldObj, this.posX + dx, this.posY + 0.5D, this.posZ + dz, false);
			try {
				for (AMVector3 vec : vecs){
					SpellHelper.instance.applyStageToGround(spellStack, dummycaster, worldObj, (int)vec.x, (int)vec.y, (int)vec.z, 0, vec.x + 0.5, vec.y + 0.5, vec.z + 0.5, 0, false);
				}
			} finally {
				SpellDropConsolidator.endBatch(worldObj);
			}
		}

	}

	private AMVector3[] getAllBlockLocationsBetween(AMVector3 a, AMVector3 b){
		a.floorToI();
		b.floorToI();

		double stepX = a.x < b.x ? 0.2f : -0.2f;
		double stepZ = a.z < b.z ? 0.2f : -0.2f;
		ArrayList<AMVector3> vecList = new ArrayList<AMVector3>();
		AMVector3 curPos = new AMVector3(a.x, a.y, a.z);
		for (int i = 0; i < this.height; ++i){
			vecList.add(new AMVector3(curPos.x, curPos.y + i, curPos.z));
		}

		while (stepX != 0 || stepZ != 0){
			if ((stepX < 0 && curPos.x <= b.x) || (stepX > 0 && curPos.x >= b.x))
				stepX = 0;
			if ((stepZ < 0 && curPos.z <= b.z) || (stepZ > 0 && curPos.z >= b.z))
				stepZ = 0;
			curPos = new AMVector3(curPos.x + stepX, curPos.y, curPos.z + stepZ);
			AMVector3 tempPos = curPos.copy();
			tempPos.roundToI();
			if (!vecList.contains(tempPos)){
				for (int i = 0; i < this.height; ++i){
					vecList.add(new AMVector3(tempPos.x, tempPos.y + i, tempPos.z));
				}
			}
		}

		return vecList.toArray(new AMVector3[vecList.size()]);
	}

	@Override
	protected void fall(float par1){
	}

	@Override
	public void onEntityUpdate(){
	}

	@Override
	protected void readEntityFromNBT(NBTTagCompound var1){
	}

	@Override
	protected void writeEntityToNBT(NBTTagCompound var1){
	}

	public void setRainOfFire(boolean ignite){
		this.dataWatcher.updateObject(WATCHER_TYPE, TYPE_ROF);
		if (ignite)
			this.dataWatcher.updateObject(WATCHER_ROF_IGNITE, (byte)1);
	}

	@Override
	@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
	public boolean isInRangeToRenderDist(double distanceSq){
		return distanceSq < 65536.0D;
	}

	@Override
	@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
	public boolean isInRangeToRender3d(double x, double y, double z){
		double dx = this.posX - x;
		double dy = this.posY - y;
		double dz = this.posZ - z;
		return (dx * dx + dy * dy + dz * dz) < 65536.0D;
	}



	public void setBlizzard(){
		this.dataWatcher.updateObject(WATCHER_TYPE, TYPE_BLIZ);
		this.ignoreFrustumCheck = true;
	}

	public boolean isBlizzard(){
		return this.dataWatcher.getWatchableObjectInt(WATCHER_TYPE) == TYPE_BLIZ;
	}

	public boolean isZone(){
		return this.dataWatcher.getWatchableObjectInt(WATCHER_TYPE) == TYPE_ZONE;
	}

	public boolean isRainOfFire(){
		return this.dataWatcher.getWatchableObjectInt(WATCHER_TYPE) == TYPE_ROF;
	}

	public void setStarfallImpact(ItemStack stack, int mergeCount){
		this.dataWatcher.updateObject(WATCHER_TYPE, TYPE_STARFALL_IMPACT);
		this.setTicksToExist(16);
		this.ignoreFrustumCheck = true;
		this.renderDistanceWeight = 10.0D;
		float scale = 1.0f;
		if (mergeCount >= 25){
			scale = 4.2f + (float)Math.min(1.0f, (mergeCount - 25) * 0.03f);
		}else if (mergeCount >= 10){
			scale = 2.8f + (float)Math.min(0.9f, (mergeCount - 10) * 0.05f);
		}else if (mergeCount >= 5){
			scale = 1.8f + (mergeCount - 5) * 0.18f;
		}else if (mergeCount > 1){
			scale = 1.0f + (mergeCount - 1) * 0.15f;
		}
		this.setRadius(scale);
		if (stack != null) {
			this.dataWatcher.updateObject(WATCHER_STACK, stack);
		}
	}

	public void setStarfallImpact(ItemStack stack){
		setStarfallImpact(stack, 1);
	}

	public boolean isStarfallImpact(){
		return this.dataWatcher.getWatchableObjectInt(WATCHER_TYPE) == TYPE_STARFALL_IMPACT;
	}

	private int mergeCount = 1;
	private long growBurstTick = Long.MIN_VALUE;
	private int growBeamStartTick = Integer.MIN_VALUE;
	private boolean growBurstFromChannelOrZone = false;

	public void addMerged(int count){
		this.mergeCount += count;
	}

	public int getMergeCount(){
		return this.mergeCount;
	}

	public void initGrowBurstMetadata(long worldTick, int beamStartTick, boolean fromChannelOrZone){
		this.growBurstTick = worldTick;
		this.growBeamStartTick = beamStartTick;
		this.growBurstFromChannelOrZone = fromChannelOrZone;
	}

	/** Allows same-tick AoE blocks or separate manual casts to merge, while blocking repeated ticks from one held Beam or Zone pulse. */
	public boolean canAcceptGrowMerge(long worldTick, int beamStartTick, boolean fromBeam, boolean fromZone){
		if (worldTick == this.growBurstTick){
			return true;
		}
		if (fromBeam){
			if (beamStartTick != Integer.MIN_VALUE && this.growBeamStartTick != Integer.MIN_VALUE){
				if (Math.abs(beamStartTick - this.growBeamStartTick) <= 2){
					return false;
				}
			}else if (this.growBurstFromChannelOrZone){
				return false;
			}
		}
		if (fromZone && this.growBurstFromChannelOrZone){
			return false;
		}
		this.growBurstTick = worldTick;
		this.growBeamStartTick = beamStartTick;
		this.growBurstFromChannelOrZone = fromBeam || fromZone;
		return true;
	}

	public int getGrowTier(){
		if (this.mergeCount >= 25) return 4;
		if (this.mergeCount >= 10) return 3;
		if (this.mergeCount >= 5) return 2;
		return 1;
	}

	public float getGrowVisualScale(){
		int count = this.mergeCount;
		if (count >= 25){
			return 4.5f + (float)Math.min(1.5f, (count - 25) * 0.02f);
		}else if (count >= 10){
			return 3.0f + (float)Math.min(1.2f, (count - 10) * 0.08f);
		}else if (count >= 5){
			return 1.8f + (count - 5) * 0.20f;
		}else if (count > 1){
			return 1.0f + (count - 1) * 0.15f;
		}
		return 1.0f;
	}

	public void setGrowMandala(float radius, int colorModifier){
		this.dataWatcher.updateObject(WATCHER_TYPE, TYPE_GROW_MANDALA);
		this.setTicksToExist(32);
		this.setRadius(radius);
		this.mergeCount = 1;
		this.ignoreFrustumCheck = true;
		this.renderDistanceWeight = 10.0D;
		if (colorModifier != -1){
			this.setExplicitColor(colorModifier);
		}
	}

	public boolean isGrowMandala(){
		return this.dataWatcher.getWatchableObjectInt(WATCHER_TYPE) == TYPE_GROW_MANDALA;
	}

	private final List<int[]> blocksToDestroy = new ArrayList<int[]>();

	public int getCasterEntityID(){
		return this.casterEntityID;
	}

	public void setExplosionFusion(EntityLivingBase caster, ItemStack stack, float basePower, int bx, int by, int bz){
		this.dataWatcher.updateObject(WATCHER_TYPE, TYPE_EXPLOSION_FUSION);
		this.setTicksToExist(5);
		this.setRadius(basePower);
		this.mergeCount = 1;
		this.SetCasterAndStack(caster, stack);
		this.ignoreFrustumCheck = true;
		this.blocksToDestroy.clear();
		this.blocksToDestroy.add(new int[]{bx, by, bz});
	}

	public void addMergedExplosion(double x, double y, double z, float extraPower, int bx, int by, int bz){
		this.mergeCount++;
		double count = this.mergeCount;
		this.posX = (this.posX * (count - 1.0) + x) / count;
		this.posY = (this.posY * (count - 1.0) + y) / count;
		this.posZ = (this.posZ * (count - 1.0) + z) / count;
		this.setPosition(this.posX, this.posY, this.posZ);
		this.blocksToDestroy.add(new int[]{bx, by, bz});
	}

	public boolean isExplosionFusion(){
		return this.dataWatcher.getWatchableObjectInt(WATCHER_TYPE) == TYPE_EXPLOSION_FUSION;
	}

	private void detonateExplosionFusion(){
		if (this.worldObj.isRemote) return;

		int count = this.mergeCount;
		float basePower = this.dataWatcher.getWatchableObjectFloat(WATCHER_RADIUS);
		if (basePower <= 0.5f) basePower = 2.5f;

		// 4 Tiers of Blast Power
		float finalPower = basePower;
		if (count >= 25){
			finalPower = basePower * 2.8f + 4.5f; // Tier 4 (Primordial Supernova)
		}else if (count >= 10){
			finalPower = basePower * 2.0f + 2.5f; // Tier 3 (Heavy Blast)
		}else if (count >= 5){
			finalPower = basePower * 1.4f + 1.2f; // Tier 2 (Medium Blast)
		}else if (count > 1){
			finalPower = basePower + (count - 1) * 0.15f;
		}

		finalPower = Math.min(14.0f, finalPower);

		SpellDropConsolidator.beginBatch(this.worldObj, this.posX, this.posY + 0.5D, this.posZ, true);
		try {
			if (count <= 1){
				// Single target cast: standard vanilla explosion with consolidated drops at center
				this.worldObj.newExplosion(dummycaster != null ? dummycaster : this, this.posX, this.posY, this.posZ, finalPower, false, false);
			}else{
				// Multi-block AoE cast: break all targeted blocks across the entire area!
				int miningPower = 0;
				if (this.spellStack != null){
					miningPower = SpellUtils.instance.countModifiers(SpellModifiers.MINING_POWER, this.spellStack, 0);
				}

				int maxDepth = 2 + miningPower;
				for (int[] pos : this.blocksToDestroy){
					int bx = pos[0];
					int by = pos[1];
					int bz = pos[2];

					for (int dy = 0; dy < maxDepth; dy++){
						int curY = by - dy;
						if (curY < 1) break;
						Block block = this.worldObj.getBlock(bx, curY, bz);
						if (block != Blocks.air && block.getBlockHardness(this.worldObj, bx, curY, bz) >= 0){
							if (block.getExplosionResistance(this) <= 45.0f){
								int meta = this.worldObj.getBlockMetadata(bx, curY, bz);
								SpellDropConsolidator.recordBrokenBlock(this.worldObj, block, meta, bx, curY, bz);
								block.dropBlockAsItem(this.worldObj, bx, curY, bz, meta, 0);
								this.worldObj.setBlock(bx, curY, bz, Blocks.air, 0, 2);
							}
						}
					}
				}

				// Single centralized explosion for entity damage, knockback and central crater
				this.worldObj.newExplosion(dummycaster != null ? dummycaster : this, this.posX, this.posY, this.posZ, Math.min(8.0f, finalPower), false, false);
			}
		} finally {
			SpellDropConsolidator.endBatch(this.worldObj);
		}

		// Send visual effect packet to all clients in 96-block radius!
		AMNetHandler.INSTANCE.sendExplosionImpactToClients(this.posX, this.posY, this.posZ, this.worldObj, this.spellStack, count);

		this.setDead();
	}

	public void setExplosionImpact(ItemStack stack, int mergeCount){
		this.dataWatcher.updateObject(WATCHER_TYPE, TYPE_EXPLOSION_IMPACT);
		this.setTicksToExist(20);
		this.ignoreFrustumCheck = true;
		this.renderDistanceWeight = 10.0D;
		float scale = 1.0f;
		if (mergeCount >= 25){
			scale = 4.5f + (float)Math.min(1.5f, (mergeCount - 25) * 0.03f);
		}else if (mergeCount >= 10){
			scale = 3.0f + (float)Math.min(1.0f, (mergeCount - 10) * 0.06f);
		}else if (mergeCount >= 5){
			scale = 1.8f + (mergeCount - 5) * 0.20f;
		}else if (mergeCount > 1){
			scale = 1.0f + (mergeCount - 1) * 0.15f;
		}
		this.setRadius(scale);
		this.mergeCount = mergeCount;
		if (stack != null){
			this.dataWatcher.updateObject(WATCHER_STACK, stack);
		}
	}

	public boolean isExplosionImpact(){
		return this.dataWatcher.getWatchableObjectInt(WATCHER_TYPE) == TYPE_EXPLOSION_IMPACT;
	}

	public void setAoEShockwave(ItemStack stack, float radius, int side){
		this.dataWatcher.updateObject(WATCHER_TYPE, TYPE_AOE_SHOCKWAVE);
		this.setTicksToExist(14);
		this.ignoreFrustumCheck = true;
		this.renderDistanceWeight = 10.0D;
		this.setRadius(Math.max(0.5f, radius));
		this.dataWatcher.updateObject(WATCHER_ROF_IGNITE, (byte) side);
		if (stack != null){
			this.spellStack = stack;
			this.dataWatcher.updateObject(WATCHER_STACK, stack);
		}
	}

	public boolean isAoEShockwave(){
		return this.dataWatcher.getWatchableObjectInt(WATCHER_TYPE) == TYPE_AOE_SHOCKWAVE;
	}

	public int getAoESide(){
		return this.dataWatcher.getWatchableObjectByte(WATCHER_ROF_IGNITE);
	}

	private double lightningAoERadius = 4.0;

	public void setLightningFusion(EntityLivingBase caster, ItemStack stack, double x, double y, double z, int bx, int by, int bz){
		this.dataWatcher.updateObject(WATCHER_TYPE, TYPE_LIGHTNING_FUSION);
		this.setTicksToExist(5);
		this.mergeCount = 1;
		this.SetCasterAndStack(caster, stack);
		this.ignoreFrustumCheck = true;
		this.lightningAoERadius = 4.0;
		this.blocksToDestroy.clear();
		this.blocksToDestroy.add(new int[]{bx, by, bz});
	}

	public void addMergedLightning(double x, double y, double z, int bx, int by, int bz){
		this.mergeCount++;
		double count = this.mergeCount;
		this.posX = (this.posX * (count - 1.0) + x) / count;
		this.posY = (this.posY * (count - 1.0) + y) / count;
		this.posZ = (this.posZ * (count - 1.0) + z) / count;
		this.setPosition(this.posX, this.posY, this.posZ);
		this.blocksToDestroy.add(new int[]{bx, by, bz});

		double distSq = (x - this.posX)*(x - this.posX) + (z - this.posZ)*(z - this.posZ);
		double dist = Math.sqrt(distSq);
		if (dist > this.lightningAoERadius){
			this.lightningAoERadius = Math.min(64.0, dist + 2.0);
		}
	}

	public boolean isLightningFusion(){
		return this.dataWatcher.getWatchableObjectInt(WATCHER_TYPE) == TYPE_LIGHTNING_FUSION;
	}

	private void detonateLightningFusion(){
		if (this.worldObj.isRemote) return;

		int count = this.mergeCount;
		double radius = Math.max(4.0, this.lightningAoERadius);

		int damageMods = 0;
		if (this.spellStack != null){
			damageMods = SpellUtils.instance.countModifiers(SpellModifiers.DAMAGE, this.spellStack, 0);
		}

		float baseDmg = 8.0f + damageMods * 3.0f;
		float tierMultiplier = count >= 30 ? 3.5f : (count >= 15 ? 2.4f : (count >= 5 ? 1.6f : 1.0f));
		float finalDmg = baseDmg * tierMultiplier;

		AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(
				this.posX - radius, this.posY - 6.0, this.posZ - radius,
				this.posX + radius, this.posY + 16.0, this.posZ + radius
		);
		@SuppressWarnings("unchecked")
		List<EntityLivingBase> targets = this.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, bb);
		EntityLivingBase sourceCaster = (dummycaster != null) ? dummycaster : null;

		for (EntityLivingBase target : targets){
			if (target == sourceCaster) continue;
			double dSq = target.getDistanceSq(this.posX, target.posY, this.posZ);
			if (dSq <= (radius + 1.5) * (radius + 1.5)){
				int fireSeconds = count >= 30 ? 10 : (count >= 15 ? 8 : (count >= 5 ? 6 : 5));
				target.setFire(fireSeconds);
				DamageSource impactDmg = DamageSources.causeEntityLightningDamage(sourceCaster);
				target.attackEntityFrom(impactDmg, finalDmg);
				if (count >= 15){
					target.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, count >= 30 ? 100 : 60, count >= 30 ? 3 : 1));
				}
			}
		}

		AMNetHandler.INSTANCE.sendLightningImpactToClients(this.posX, this.posY, this.posZ, this.worldObj, this.spellStack, count, radius);
		this.setDead();
	}

	public void setLightningImpact(ItemStack stack, int mergeCount, double radius){
		this.dataWatcher.updateObject(WATCHER_TYPE, TYPE_LIGHTNING_IMPACT);
		int duration = mergeCount >= 30 ? 70 : (mergeCount >= 15 ? 50 : (mergeCount >= 5 ? 35 : 20));
		this.setTicksToExist(duration);
		this.mergeCount = mergeCount;
		this.lightningAoERadius = radius;
		this.setRadius((float)radius);
		this.ignoreFrustumCheck = true;
		this.renderDistanceWeight = 12.0D;
		this.boundingBox.setBounds(
				this.posX - radius - 8.0, this.posY - 4.0, this.posZ - radius - 8.0,
				this.posX + radius + 8.0, this.posY + 36.0, this.posZ + radius + 8.0
		);
		if (stack != null){
			this.dataWatcher.updateObject(WATCHER_STACK, stack);
		}
	}

	public boolean isLightningImpact(){
		return this.dataWatcher.getWatchableObjectInt(WATCHER_TYPE) == TYPE_LIGHTNING_IMPACT;
	}

	private void lightningImpactUpdate(){
		if (this.worldObj.isRemote){
			int count = this.mergeCount;
			int tier = count >= 30 ? 4 : (count >= 15 ? 3 : (count >= 5 ? 2 : 1));
			if (tier >= 2){
				int interval = tier >= 4 ? 8 : (tier >= 3 ? 12 : 16);
				if (this.ticksExisted % interval == 0 && this.ticksExisted < this.getTicksToExist() * 0.85f){
					float pitch = 0.65f + this.rand.nextFloat() * 0.35f;
					float vol = tier >= 4 ? 3.5f : 2.0f;
					this.worldObj.playSound(this.posX, this.posY, this.posZ, "ambient.weather.thunder", vol, pitch, false);
				}
			}
		}
	}

	private float orbitalDamage = 5.0f;
	private float orbitalRadius = 160.0f;
	private boolean orbitalOvercharged = false;
	private boolean orbitalFollowCaster = false;
	private int orbitalDuration = 160;
	private int orbitalStrikeCursor = 0;
	private final Map<Integer, Integer> orbitalMobCooldowns = new HashMap<Integer, Integer>();

	public void setOrbitalStorm(EntityLivingBase caster, ItemStack stack, double x, double y, double z,
	                            boolean overcharged, float damage, float radius, int durationTicks, boolean followCaster){
		this.dataWatcher.updateObject(WATCHER_TYPE, TYPE_ORBITAL_STORM);
		this.SetCasterAndStack(caster, stack);
		this.orbitalOvercharged = overcharged;
		this.orbitalDamage = damage;
		this.orbitalRadius = radius;
		this.orbitalFollowCaster = followCaster;
		int dur = Math.max(60, durationTicks);
		this.orbitalDuration = dur;
		int totalDuration = dur + 25;
		if (totalDuration == 100) totalDuration = 101;
		this.setTicksToExist(totalDuration);
		this.setRadius(radius);
		this.ignoreFrustumCheck = true;
		this.renderDistanceWeight = 16.0D;
		this.setPosition(x, y, z);
		this.orbitalMobCooldowns.clear();
		dispatchOrbitalWave(true);
	}

	public void refreshOrbitalStorm(double x, double y, double z, int durationTicks,
	                                boolean overcharged, float damage, float radius, boolean followCaster){
		if (followCaster){
			this.setPosition(x, y, z);
		}
		int dur = Math.max(60, durationTicks);
		this.orbitalFollowCaster = this.orbitalFollowCaster || followCaster;
		this.orbitalDamage = Math.max(this.orbitalDamage, damage);
		this.orbitalRadius = radius;
		if (overcharged) this.orbitalOvercharged = true;

		// Keep orbital storm synchronized with the continuous spell container (e.g. Zone/Channel pulses)
		// rather than endlessly stacking full spell durations on every 30-tick pulse.
		int currentEnd = this.getTicksToExist();
		int pulseEnd = this.ticksExisted + 35;
		int nextEnd = Math.max(currentEnd, pulseEnd);
		if (nextEnd == 100) nextEnd = 101;
		this.setTicksToExist(nextEnd);
	}

	public boolean isOrbitalStorm(){
		return this.dataWatcher.getWatchableObjectInt(WATCHER_TYPE) == TYPE_ORBITAL_STORM;
	}

	private void orbitalStormUpdate(){
		if (this.worldObj.isRemote) return;

		// Decrement per-mob strike cooldowns
		if (!this.orbitalMobCooldowns.isEmpty()){
			Iterator<Map.Entry<Integer, Integer>> it = this.orbitalMobCooldowns.entrySet().iterator();
			while (it.hasNext()){
				Map.Entry<Integer, Integer> e = it.next();
				int rem = e.getValue() - 1;
				if (rem <= 0){
					it.remove();
				}else{
					e.setValue(rem);
				}
			}
		}

		EntityLivingBase liveCaster = null;
		if (this.casterEntityID != -1){
			Entity cEnt = this.worldObj.getEntityByID(this.casterEntityID);
			if (cEnt instanceof EntityLivingBase){
				liveCaster = (EntityLivingBase) cEnt;
			}
		}
		EntityLivingBase sourceCaster = liveCaster != null ? liveCaster : this.dummycaster;

		if (this.orbitalFollowCaster && liveCaster != null && !liveCaster.isDead){
			this.setPosition(liveCaster.posX, liveCaster.posY, liveCaster.posZ);
		}

		if (!this.worldObj.getWorldInfo().isRaining()){
			this.worldObj.getWorldInfo().setRaining(true);
		}

		int interval = this.orbitalOvercharged ? 2 : 3;
		if (this.ticksExisted <= 1 || this.ticksExisted % interval == 0){
			dispatchOrbitalWave(false);
		}
	}

	private void dispatchOrbitalWave(boolean syncOnlyIfEmpty){
		if (this.worldObj.isRemote) return;

		EntityLivingBase liveCaster = null;
		if (this.casterEntityID != -1){
			Entity cEnt = this.worldObj.getEntityByID(this.casterEntityID);
			if (cEnt instanceof EntityLivingBase){
				liveCaster = (EntityLivingBase) cEnt;
			}
		}
		EntityLivingBase sourceCaster = liveCaster != null ? liveCaster : this.dummycaster;

		double r = Math.min(160.0, Math.max(48.0, this.orbitalRadius));
		AxisAlignedBB scanBox = AxisAlignedBB.getBoundingBox(
				this.posX - r, Math.max(0.0, this.posY - 96.0), this.posZ - r,
				this.posX + r, Math.min(256.0, this.posY + 128.0), this.posZ + r
		);

		@SuppressWarnings("unchecked")
		List<Entity> rawEntities = this.worldObj.getEntitiesWithinAABB(IMob.class, scanBox);
		List<EntityLivingBase> candidates = new ArrayList<EntityLivingBase>();

		for (Entity ent : rawEntities){
			if (!(ent instanceof EntityLivingBase)) continue;
			if (ent == sourceCaster || ent == liveCaster || ent.isDead) continue;
			EntityLivingBase mob = (EntityLivingBase) ent;
			if (mob.getHealth() <= 0.0f) continue;

			double dx = mob.posX - this.posX;
			double dz = mob.posZ - this.posZ;
			if (dx * dx + dz * dz > r * r) continue;

			int mx = MathHelper.floor_double(mob.posX);
			int mz = MathHelper.floor_double(mob.posZ);
			if (!this.worldObj.blockExists(mx, 64, mz)) continue;

			int topY = this.worldObj.getPrecipitationHeight(mx, mz);
			if (mob.posY < topY - 12 && !this.worldObj.canBlockSeeTheSky(mx, MathHelper.floor_double(mob.posY), mz)){
				continue;
			}

			if (!this.orbitalMobCooldowns.containsKey(mob.getEntityId())){
				candidates.add(mob);
			}
		}

		double[] sx = new double[48];
		double[] sy = new double[48];
		double[] sz = new double[48];
		boolean[] hitMob = new boolean[48];
		int strikeCount = 0;

		int maxMobStrikes = this.orbitalOvercharged ? 14 : 9;
		if (!candidates.isEmpty()){
			int n = candidates.size();
			int toHit = Math.min(maxMobStrikes, n);
			int cooldownTicks = this.orbitalOvercharged ? 4 : 6;
			int fireSec = this.orbitalOvercharged ? 7 : 5;

			for (int i = 0; i < toHit && strikeCount < 34; i++){
				int idx = (this.orbitalStrikeCursor + i) % n;
				EntityLivingBase target = candidates.get(idx);

				this.orbitalMobCooldowns.put(target.getEntityId(), cooldownTicks);
				target.setFire(fireSec);
				target.hurtResistantTime = 0;

				DamageSource strikeDmg = DamageSources.causeEntityLightningDamage(sourceCaster);
				if (this.spellStack != null && sourceCaster != null){
					SpellHelper.instance.attackTargetSpecial(this.spellStack, target, strikeDmg, this.orbitalDamage);
				}else{
					target.attackEntityFrom(strikeDmg, this.orbitalDamage);
				}

				sx[strikeCount] = target.posX;
				sy[strikeCount] = target.posY;
				sz[strikeCount] = target.posZ;
				hitMob[strikeCount] = true;
				strikeCount++;

				// Cascading multi-bolt barrage on targeted mobs
				int extraBolts = this.orbitalOvercharged ? 2 : 1;
				for (int eb = 0; eb < extraBolts && strikeCount < 36; eb++){
					sx[strikeCount] = target.posX + (this.rand.nextDouble() - 0.5) * 2.8;
					sy[strikeCount] = target.posY;
					sz[strikeCount] = target.posZ + (this.rand.nextDouble() - 0.5) * 2.8;
					hitMob[strikeCount] = true;
					strikeCount++;
				}
			}
			this.orbitalStrikeCursor = (this.orbitalStrikeCursor + toHit) % n;
		}

		// Heavy simultaneous lightning barrage across the 10 chunks
		int ambientBolts = this.orbitalOvercharged ? 12 : 8;
		if (!syncOnlyIfEmpty){
			for (int a = 0; a < ambientBolts && strikeCount < 48; a++){
				double ang = this.rand.nextDouble() * Math.PI * 2.0;
				double maxHorizon = Math.min(160.0, r);
				double dist;
				if (a % 3 == 0){
					dist = 8.0 + this.rand.nextDouble() * Math.min(56.0, maxHorizon - 8.0);
				}else{
					dist = 14.0 + Math.sqrt(this.rand.nextDouble()) * (maxHorizon - 14.0);
				}
				int ax = MathHelper.floor_double(this.posX + Math.cos(ang) * dist);
				int az = MathHelper.floor_double(this.posZ + Math.sin(ang) * dist);
				if (this.worldObj.blockExists(ax, 64, az)){
					int ay = this.worldObj.getPrecipitationHeight(ax, az);
					sx[strikeCount] = ax + 0.5 + (this.rand.nextDouble() - 0.5) * 0.6;
					sy[strikeCount] = ay;
					sz[strikeCount] = az + 0.5 + (this.rand.nextDouble() - 0.5) * 0.6;
					hitMob[strikeCount] = false;
					strikeCount++;
				}
			}
		}

		int remTicks = Math.max(0, this.getTicksToExist() - this.ticksExisted);
		int customColor = this.getSpellColor();
		AMNetHandler.INSTANCE.sendOrbitalStormToClients(
				this.worldObj, this.getEntityId(),
				this.posX, this.posY, this.posZ,
				(float) r, remTicks, this.orbitalDuration,
				customColor, this.orbitalOvercharged,
				sx, sy, sz, hitMob, strikeCount
		);
	}

	private float tornadoRadius = 7.0f;
	private float tornadoDamage = 4.0f;
	private int tornadoDuration = 180;
	private int tornadoHoming = 0;
	private int tornadoSpeed = 0;
	private float tornadoMaxHardness = 2.2f;
	private double tornadoDirX = 0.0;
	private double tornadoDirZ = 0.0;
	private byte tornadoElement = 0;
	private byte tornadoActiveElement = 0;
	private int tornadoTier = 1;
	private byte tornadoCelestial = 0;
	private boolean tornadoPlayerOwned = true;
	private double tornadoSweepCursor = 0.0;
	private int tornadoBlocksLifted = 0;
	// Per-effect IDs only: never retain entities/worlds in a global collection.
	private final Map<Integer, int[]> tornadoAir = new HashMap<Integer, int[]>();

	public void setTornado(
			EntityLivingBase caster, ItemStack stack,
			double x, double y, double z,
			float radius, int durationTicks, float damage,
			int homingMods, int speedMods, float maxHardness,
			double dirX, double dirZ
	){
		setTornado(caster, stack, x, y, z, radius, durationTicks, damage, homingMods, speedMods, maxHardness, dirX, dirZ, am2.spell.components.Tornado.ELEMENT_AIR, 1, am2.spell.components.Tornado.CELESTIAL_NONE);
	}

	public void setTornado(
			EntityLivingBase caster, ItemStack stack,
			double x, double y, double z,
			float radius, int durationTicks, float damage,
			int homingMods, int speedMods, float maxHardness,
			double dirX, double dirZ, byte element
	){
		setTornado(caster, stack, x, y, z, radius, durationTicks, damage, homingMods, speedMods, maxHardness, dirX, dirZ, element, 1, am2.spell.components.Tornado.CELESTIAL_NONE);
	}

	public void setTornado(
			EntityLivingBase caster, ItemStack stack,
			double x, double y, double z,
			float radius, int durationTicks, float damage,
			int homingMods, int speedMods, float maxHardness,
			double dirX, double dirZ, byte element, int fujitaTier, byte celestialMode
	){
		this.dataWatcher.updateObject(WATCHER_TYPE, TYPE_TORNADO);
		if (caster != null || this.worldObj != null){
			this.SetCasterAndStack(caster, stack);
		}
		this.tornadoPlayerOwned = (caster == null) || (caster instanceof EntityPlayer);
		this.tornadoRadius = Math.max(4.5f, Math.min(40.0f, radius));
		this.tornadoDuration = Math.max(60, durationTicks);
		this.tornadoDamage = Math.max(1.0f, damage);
		this.tornadoHoming = Math.max(0, homingMods);
		this.tornadoSpeed = Math.max(0, speedMods);
		this.tornadoMaxHardness = Math.max(0.5f, maxHardness);
		this.tornadoDirX = dirX;
		this.tornadoDirZ = dirZ;
		this.tornadoElement = element;
		this.tornadoActiveElement = element;
		this.tornadoTier = Math.max(1, Math.min(5, fujitaTier));
		this.tornadoCelestial = celestialMode;
		this.tornadoSweepCursor = 0.0;
		this.tornadoBlocksLifted = 0;
		this.tornadoAir.clear();
		int totalDur = this.tornadoDuration;
		if (totalDur == 100) totalDur = 101;
		this.setTicksToExist(totalDur);
		this.setRadius(this.tornadoRadius);
		this.ignoreFrustumCheck = true;
		this.renderDistanceWeight = 16.0D;
		this.setPosition(x, y, z);
		if (this.worldObj != null && !this.worldObj.isRemote){
			syncTornadoState(new int[0], new int[0], new int[0], new int[0], new int[0], 0);
		}
	}

	public void refreshTornado(float radius, float damage, int homingMods, int speedMods, float maxHardness, byte element){
		refreshTornado(radius, damage, homingMods, speedMods, maxHardness, element, this.tornadoTier, this.tornadoCelestial);
	}

	public void refreshTornado(float radius, float damage, int homingMods, int speedMods, float maxHardness, byte element, int fujitaTier, byte celestialMode){
		this.tornadoRadius = Math.max(this.tornadoRadius, Math.max(4.5f, Math.min(40.0f, radius)));
		this.tornadoDamage = Math.max(this.tornadoDamage, Math.max(1.0f, damage));
		this.tornadoHoming = Math.max(this.tornadoHoming, homingMods);
		this.tornadoSpeed = Math.max(this.tornadoSpeed, speedMods);
		this.tornadoMaxHardness = Math.max(this.tornadoMaxHardness, maxHardness);
		this.tornadoTier = Math.max(this.tornadoTier, Math.max(1, Math.min(5, fujitaTier)));
		if (element != am2.spell.components.Tornado.ELEMENT_AIR){
			this.tornadoElement = element;
			this.tornadoActiveElement = element;
		}
		if (celestialMode != am2.spell.components.Tornado.CELESTIAL_NONE){
			this.tornadoCelestial = celestialMode;
		}
		int pulseEnd = this.ticksExisted + 55;
		int nextEnd = Math.max(this.getTicksToExist(), pulseEnd);
		if (nextEnd == 100) nextEnd = 101;
		this.setTicksToExist(nextEnd);
		this.setRadius(this.tornadoRadius);
		if (this.worldObj != null && !this.worldObj.isRemote){
			syncTornadoState(new int[0], new int[0], new int[0], new int[0], new int[0], 0);
		}
	}

	public byte getTornadoElement(){
		return this.tornadoActiveElement;
	}

	public int getTornadoTier(){
		return this.tornadoTier;
	}

	public byte getTornadoCelestial(){
		return this.tornadoCelestial;
	}

	public boolean isTornado(){
		return this.dataWatcher.getWatchableObjectInt(WATCHER_TYPE) == TYPE_TORNADO;
	}

	private byte resolveTerrainTornadoElement(double radius){
		if (this.tornadoElement != am2.spell.components.Tornado.ELEMENT_AIR){
			return this.tornadoElement;
		}
		int waterHits = 0;
		int lavaHits = 0;
		int sampleR = Math.max(2, Math.min(8, (int) Math.round(radius * 0.35)));
		int[][] offsets = new int[][]{
				{0, 0}, {sampleR, 0}, {-sampleR, 0}, {0, sampleR}, {0, -sampleR},
				{sampleR / 2, sampleR / 2}, {-sampleR / 2, -sampleR / 2}
		};
		for (int i = 0; i < offsets.length; i++){
			int sx = MathHelper.floor_double(this.posX) + offsets[i][0];
			int sz = MathHelper.floor_double(this.posZ) + offsets[i][1];
			if (!this.worldObj.blockExists(sx, 64, sz)) continue;
			int sy = this.worldObj.getTopSolidOrLiquidBlock(sx, sz) - 1;
			for (int dy = 0; dy <= 1; dy++){
				int cy = Math.max(1, sy + dy);
				net.minecraft.block.Block blk = this.worldObj.getBlock(sx, cy, sz);
				if (blk == net.minecraft.init.Blocks.lava || blk == net.minecraft.init.Blocks.flowing_lava
						|| blk.getMaterial() == net.minecraft.block.material.Material.lava){
					lavaHits++;
					break;
				}
				if (blk == net.minecraft.init.Blocks.water || blk == net.minecraft.init.Blocks.flowing_water
						|| blk.getMaterial() == net.minecraft.block.material.Material.water){
					waterHits++;
					break;
				}
			}
		}
		if (lavaHits >= 2) return am2.spell.components.Tornado.ELEMENT_FIRE;
		if (waterHits >= 3) return am2.spell.components.Tornado.ELEMENT_WATER;
		return am2.spell.components.Tornado.ELEMENT_AIR;
	}

	private boolean canTornadoAffect(Entity entity, EntityLivingBase source){
		if (entity == null || entity.isDead) return false;
		if (!(entity instanceof EntityPlayer)) return true;
		EntityPlayer player = (EntityPlayer) entity;
		if (player.capabilities.isCreativeMode || player.capabilities.disableDamage) return false;
		if (tornadoPlayerOwned || source instanceof EntityPlayer){
			MinecraftServer server = MinecraftServer.getServer();
			if (server == null || !server.isPVPEnabled()) return false;
			if (source instanceof EntityPlayer && !((EntityPlayer) source).canAttackPlayer(player)) return false;
		}
		return true;
	}

	private boolean canTornadoChangeBlock(EntityLivingBase source, int x, int y, int z){
		if (worldObj.isRemote || !worldObj.blockExists(x, y, z) || source == null) return false;
		EntityPlayer player = DummyEntityPlayer.fromEntityLiving(source);
		MinecraftServer server = MinecraftServer.getServer();
		if (!worldObj.canMineBlock(player, x, y, z)
				|| server == null || server.isBlockProtected(worldObj, x, y, z, player)) return false;
		Block block = worldObj.getBlock(x, y, z);
		int meta = worldObj.getBlockMetadata(x, y, z);
		return !SpellDropConsolidator.fireBlockBreakEvent(worldObj, player, block, meta, x, y, z).isCanceled()
				&& worldObj.getBlock(x, y, z) == block && worldObj.getBlockMetadata(x, y, z) == meta;
	}

	private boolean tornadoThrownBlocksMayHurt(EntityLivingBase source){
		if (!tornadoPlayerOwned) return true;
		MinecraftServer server = MinecraftServer.getServer();
		if (server == null || !server.isPVPEnabled() || casterEntityID == -1 || !(source instanceof EntityPlayer)) return false;
		EntityPlayer player = (EntityPlayer) source;
		return player.getTeam() == null || player.getTeam().getAllowFriendlyFire();
	}

	private static DamageSource tornadoDamageSource(EntityLivingBase source, byte element){
		// Preserve elemental subclasses used by guardian resistances and other handlers.
		DamageSource damage = element == am2.spell.components.Tornado.ELEMENT_FIRE ? new am2.damage.DamageSourceFire(source, false)
				: element == am2.spell.components.Tornado.ELEMENT_LIGHTNING ? new am2.damage.DamageSourceLightning(source, false)
				: element == am2.spell.components.Tornado.ELEMENT_ICE ? new am2.damage.DamageSourceFrost(source, false)
				: new am2.damage.DamageSourceWind(source, false);
		return damage.setDifficultyScaled();
	}

	private void drainTornadoAir(EntityLivingBase victim, EntityLivingBase source){
		int id = victim.getEntityId();
		if (victim.canBreatheUnderwater() || victim.isPotionActive(Potion.waterBreathing)
				|| victim instanceof net.minecraft.entity.monster.EntityIronGolem
				|| victim.getCreatureAttribute() == net.minecraft.entity.EnumCreatureAttribute.UNDEAD){
			tornadoAir.remove(id);
			return;
		}
		int[] state = tornadoAir.get(id);
		if (state == null || ticksExisted - state[1] > 20){
			state = new int[]{300, ticksExisted};
			tornadoAir.put(id, state);
		}
		state[0] = Math.max(0, state[0] - 35);
		state[1] = ticksExisted;
		victim.setAir(Math.min(victim.getAir(), state[0]));
		if (state[0] == 0){
			victim.hurtResistantTime = 0;
			SpellHelper.instance.attackTargetSpecial(spellStack, victim,
					DamageSources.causeEntityDrownDamage(source), 2.0f + tornadoTier * 1.2f);
		}
	}

	private void tornadoUpdate(){
		if (this.worldObj.isRemote) return;

		EntityLivingBase liveCaster = null;
		if (this.casterEntityID != -1){
			Entity cEnt = this.worldObj.getEntityByID(this.casterEntityID);
			if (cEnt instanceof EntityLivingBase){
				liveCaster = (EntityLivingBase) cEnt;
			}
		}
		EntityLivingBase sourceCaster = liveCaster != null ? liveCaster : this.dummycaster;

		double radius = Math.max(4.5, this.tornadoRadius);
		double height = 26.0 + this.tornadoTier * 7.5 + radius * 1.25;
		boolean isSolar = (this.tornadoCelestial == am2.spell.components.Tornado.CELESTIAL_SOLAR);
		boolean isLunar = (this.tornadoCelestial == am2.spell.components.Tornado.CELESTIAL_LUNAR);

		if (this.ticksExisted % 5 == 0){
			this.tornadoActiveElement = resolveTerrainTornadoElement(radius);
		}

		// 1. Movement: stationary by default; slowly chases enemies with Homing; accelerated by Speed/VelocityAdded
		if (this.tornadoHoming > 0){
			double seekRange = radius * 2.8 + 14.0;
			AxisAlignedBB seekBox = AxisAlignedBB.getBoundingBox(
					this.posX - seekRange, Math.max(0.0, this.posY - 24.0), this.posZ - seekRange,
					this.posX + seekRange, Math.min(256.0, this.posY + 32.0), this.posZ + seekRange
			);
			@SuppressWarnings("unchecked")
			List<Entity> candidates = this.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, seekBox);
			EntityLivingBase nearest = null;
			double bestDistSq = Double.MAX_VALUE;
			for (Entity ent : candidates){
				if (!(ent instanceof EntityLivingBase) || ent == liveCaster || ent == sourceCaster || ent.isDead) continue;
				EntityLivingBase living = (EntityLivingBase) ent;
				if (living.getHealth() <= 0.0f || !canTornadoAffect(living, sourceCaster)) continue;
				double dx = living.posX - this.posX;
				double dz = living.posZ - this.posZ;
				double dSq = dx * dx + dz * dz;
				if (dSq < bestDistSq && dSq > 1.5){
					bestDistSq = dSq;
					nearest = living;
				}
			}
			if (nearest != null){
				double dx = nearest.posX - this.posX;
				double dz = nearest.posZ - this.posZ;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 0.2){
					double step = Math.min(0.24, 0.065 + this.tornadoHoming * 0.015 + this.tornadoSpeed * 0.035);
					double nx = this.posX + (dx / d) * step;
					double nz = this.posZ + (dz / d) * step;
					int ix = MathHelper.floor_double(nx);
					int iz = MathHelper.floor_double(nz);
					double ny = this.posY;
					if (this.worldObj.blockExists(ix, 64, iz)){
						int groundY = this.worldObj.getTopSolidOrLiquidBlock(ix, iz);
						ny = this.posY + Math.max(-0.5, Math.min(0.5, groundY - this.posY));
					}
					this.setPosition(nx, ny, nz);
				}
			}
		}else if (this.tornadoSpeed > 0){
			double len = Math.sqrt(this.tornadoDirX * this.tornadoDirX + this.tornadoDirZ * this.tornadoDirZ);
			if (len > 0.001){
				double step = Math.min(0.22, 0.055 + this.tornadoSpeed * 0.032);
				double nx = this.posX + (this.tornadoDirX / len) * step;
				double nz = this.posZ + (this.tornadoDirZ / len) * step;
				int ix = MathHelper.floor_double(nx);
				int iz = MathHelper.floor_double(nz);
				double ny = this.posY;
				if (this.worldObj.blockExists(ix, 64, iz)){
					int groundY = this.worldObj.getTopSolidOrLiquidBlock(ix, iz);
					ny = this.posY + Math.max(-0.5, Math.min(0.5, groundY - this.posY));
				}
				this.setPosition(nx, ny, nz);
			}
		}

		// 2. Vortex Suction, Tangential Swirl, Lift & Elemental/Celestial Synergy Damage on nearby entities
		double pullMult = 1.38 + (this.tornadoTier - 1) * 0.14 + (isLunar ? 0.35 : 0.0);
		double pullRadius = radius * pullMult;
		AxisAlignedBB vortexBox = AxisAlignedBB.getBoundingBox(
				this.posX - pullRadius, this.posY - 3.0, this.posZ - pullRadius,
				this.posX + pullRadius, Math.min(256.0, this.posY + height + 4.0), this.posZ + pullRadius
		);
		@SuppressWarnings("unchecked")
		List<Entity> affected = this.worldObj.getEntitiesWithinAABB(Entity.class, vortexBox);
		boolean endingFling = (this.ticksExisted >= this.getTicksToExist() - 3);
		int dmgInterval = this.tornadoTier >= 5 ? 5 : (this.tornadoTier == 4 ? 7 : (this.tornadoTier == 3 ? 8 : 9));
		if (this.tornadoActiveElement == am2.spell.components.Tornado.ELEMENT_LIGHTNING){
			dmgInterval = Math.max(4, dmgInterval - 1);
		}
		int activeThrownBlocks = 0;

		for (Entity ent : affected){
			if (ent == this || ent == liveCaster || ent == sourceCaster || !canTornadoAffect(ent, sourceCaster)) continue;
			boolean isFallingBlock = (ent instanceof net.minecraft.entity.item.EntityFallingBlock);
			if (!(ent instanceof EntityLivingBase) && !(ent instanceof net.minecraft.entity.item.EntityItem) && !isFallingBlock) continue;

			if (isFallingBlock){
				net.minecraft.entity.item.EntityFallingBlock fb = (net.minecraft.entity.item.EntityFallingBlock) ent;
				if (!tornadoThrownBlocksMayHurt(sourceCaster)) fb.func_145806_a(false);
				activeThrownBlocks++;
				// Already flung outward: let natural gravity carry it away to land and re-place cleanly
				if (fb.field_145812_b > 42) continue;
			}

			double dx = ent.posX - this.posX;
			double dz = ent.posZ - this.posZ;
			double distXZ = Math.sqrt(dx * dx + dz * dz);
			if (distXZ > pullRadius) continue;

			double relY = Math.max(0.0, ent.posY - this.posY);
			double heightFrac = Math.min(1.0, relY / Math.max(6.0, height));
			double idealRing = Math.max(1.5, radius * (0.28 + heightFrac * 0.72));

			double dirX = distXZ > 0.05 ? dx / distXZ : 1.0;
			double dirZ = distXZ > 0.05 ? dz / distXZ : 0.0;
			double tangX = -dirZ;
			double tangZ = dirX;

			if (isFallingBlock){
				net.minecraft.entity.item.EntityFallingBlock fb = (net.minecraft.entity.item.EntityFallingBlock) ent;
				if (endingFling || heightFrac > 0.65 || fb.field_145812_b >= 36){
					double flingMult = isSolar ? 1.50 : (1.22 + this.tornadoTier * 0.07);
					fb.motionX = dirX * flingMult + tangX * 0.65;
					fb.motionY = Math.max(fb.motionY, 0.58 + this.tornadoTier * 0.05);
					fb.motionZ = dirZ * flingMult + tangZ * 0.65;
					fb.field_145812_b = Math.max(fb.field_145812_b, 43);
					fb.velocityChanged = true;
					fb.fallDistance = 0.0f;
					continue;
				}
			}

			boolean flingingOut = (endingFling || heightFrac > 0.85);
			if (flingingOut){
				double flingMult = isSolar ? 1.42 : (1.15 + this.tornadoTier * 0.07);
				ent.motionX = dirX * flingMult + tangX * 0.72;
				ent.motionY = Math.max(ent.motionY, 0.58 + this.tornadoTier * 0.05);
				ent.motionZ = dirZ * flingMult + tangZ * 0.72;
				// Preserve & seed fall distance on ejection so high-altitude throws inflict real landing damage
				ent.fallDistance = Math.max(ent.fallDistance, 4.0f + this.tornadoTier * 1.5f);
			}else{
				double lunarPullBoost = isLunar ? 1.32 : 1.0;
				double pullStrength = ((1.0 - distXZ / pullRadius) * 0.22 + 0.08) * lunarPullBoost;
				double radialError = idealRing - distXZ;
				double radialForce = Math.max(-0.30, Math.min(0.30, radialError * 0.14));
				double swirlSpeed = 0.40 + this.tornadoTier * 0.03 + (1.0 - Math.min(1.0, distXZ / pullRadius)) * 0.34;
				double liftBoost = isFallingBlock ? 0.30 : (this.tornadoActiveElement == am2.spell.components.Tornado.ELEMENT_LIGHTNING ? 0.28 : 0.24);

				ent.motionX = ent.motionX * 0.55 + (tangX * swirlSpeed + dirX * radialForce - dirX * pullStrength * 0.38);
				ent.motionZ = ent.motionZ * 0.55 + (tangZ * swirlSpeed + dirZ * radialForce - dirZ * pullStrength * 0.38);
				ent.motionY = Math.min(0.68, ent.motionY * 0.65 + (distXZ < radius * 1.05 ? liftBoost : 0.10));
				ent.fallDistance = 0.0f;
			}
			ent.velocityChanged = true;

			if (ent instanceof EntityLivingBase && this.ticksExisted % dmgInterval == 0 && distXZ <= radius * (isLunar ? 1.30 : 1.15)){
				EntityLivingBase victim = (EntityLivingBase) ent;
				DamageSource dmgSrc;
				float tierMult = am2.spell.components.Tornado.getTierDamageMultiplier(this.tornadoTier);
				float proxMult = am2.spell.components.Tornado.getProximityDamageMultiplier(distXZ, radius);
				float hitDmg = this.tornadoDamage * tierMult * proxMult;

				// Bonus slam damage when hurled into walls or catapulted at high tier
				if (victim.isCollidedHorizontally || (flingingOut && this.tornadoTier >= 3)){
					hitDmg *= 1.25f;
				}

				if (this.tornadoActiveElement == am2.spell.components.Tornado.ELEMENT_FIRE){
					boolean alreadyBurning = victim.isBurning();
					victim.setFire(6 + this.tornadoTier);
					dmgSrc = tornadoDamageSource(sourceCaster, am2.spell.components.Tornado.ELEMENT_FIRE);
					hitDmg *= alreadyBurning ? 1.50f : 1.25f;
				}else if (this.tornadoActiveElement == am2.spell.components.Tornado.ELEMENT_LIGHTNING){
					dmgSrc = tornadoDamageSource(sourceCaster, am2.spell.components.Tornado.ELEMENT_LIGHTNING);
					hitDmg *= 1.22f;
				}else if (this.tornadoActiveElement == am2.spell.components.Tornado.ELEMENT_ICE){
					boolean alreadySlowed = victim.isPotionActive(Potion.moveSlowdown);
					int slowAmp = this.tornadoTier >= 4 ? 2 : 1;
					victim.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 80, slowAmp));
					dmgSrc = tornadoDamageSource(sourceCaster, am2.spell.components.Tornado.ELEMENT_ICE);
					hitDmg *= alreadySlowed ? 1.32f : 1.12f;
				}else if (this.tornadoActiveElement == am2.spell.components.Tornado.ELEMENT_WATER){
					victim.extinguish();
					victim.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 60, 1));
					drainTornadoAir(victim, sourceCaster);
					dmgSrc = tornadoDamageSource(sourceCaster, am2.spell.components.Tornado.ELEMENT_AIR);
				}else{
					dmgSrc = tornadoDamageSource(sourceCaster, am2.spell.components.Tornado.ELEMENT_AIR);
				}

				// High-tier Celestial specializations (F4-F5)
				if (this.tornadoTier >= 4){
					if (isSolar){
						victim.setFire(4);
					}else if (isLunar){
						victim.addPotionEffect(new PotionEffect(Potion.weakness.id, 80, 1));
					}
				}

				// Controlled i-frame bypass so rapid F4/F5 & Lightning hits connect reliably
				victim.hurtResistantTime = 0;

				// Partial armor penetration in high calamity tiers (F4: 25%, F5: 40%)
				float pierceFrac = this.tornadoTier >= 5 ? 0.40f : (this.tornadoTier == 4 ? 0.25f : 0.0f);
				float mainDmg = hitDmg * (1.0f - pierceFrac);
				if (this.spellStack != null && sourceCaster != null){
					SpellHelper.instance.attackTargetSpecial(this.spellStack, victim, dmgSrc, mainDmg);
				}else{
					victim.attackEntityFrom(dmgSrc, mainDmg);
				}
				if (pierceFrac > 0.0f && !victim.isDead && victim.getHealth() > 0.0f){
					victim.hurtResistantTime = 0;
					DamageSource pierceSrc = DamageSources.causeEntityMagicDamage(sourceCaster);
					float pierceDmg = hitDmg * pierceFrac;
					if (this.spellStack != null && sourceCaster != null){
						SpellHelper.instance.attackTargetSpecial(this.spellStack, victim, pierceSrc, pierceDmg);
					}else{
						victim.attackEntityFrom(pierceSrc, pierceDmg);
					}
				}

				// Lightning Tornado micro-chain arc to 1 nearby target inside the funnel
				if (this.tornadoActiveElement == am2.spell.components.Tornado.ELEMENT_LIGHTNING
						&& (this.ticksExisted / dmgInterval) % 2 == 0){
					for (Entity other : affected){
						if (other == victim || other == this || other == liveCaster || other == sourceCaster || other.isDead) continue;
						if (!(other instanceof EntityLivingBase) || !canTornadoAffect(other, sourceCaster)) continue;
						if (other.getDistanceSqToEntity(victim) <= 49.0D){
							EntityLivingBase arcTarget = (EntityLivingBase) other;
							arcTarget.hurtResistantTime = 0;
							DamageSource arcSrc = tornadoDamageSource(sourceCaster, am2.spell.components.Tornado.ELEMENT_LIGHTNING);
							float arcDmg = hitDmg * 0.45f;
							if (this.spellStack != null && sourceCaster != null){
								SpellHelper.instance.attackTargetSpecial(this.spellStack, arcTarget, arcSrc, arcDmg);
							}else{
								arcTarget.attackEntityFrom(arcSrc, arcDmg);
							}
							break;
						}
					}
				}
			}
		}

		for (Iterator<int[]> it = tornadoAir.values().iterator(); it.hasNext();){
			int[] state = it.next();
			if (tornadoActiveElement != am2.spell.components.Tornado.ELEMENT_WATER
					|| ticksExisted - state[1] > 20){
				it.remove();
			}
		}

		boolean canBreakBlocks = AMCore.config.TornadoBreaksBlocks()
				&& (this.tornadoPlayerOwned ? AMCore.config.PlayerSpellsDamageTerrain() : AMCore.config.NPCSpellsDamageTerrain());

		// Glacial Tornado synergy: freeze surface water along the touchdown footprint
		if (canBreakBlocks && this.tornadoActiveElement == am2.spell.components.Tornado.ELEMENT_ICE && this.ticksExisted % 4 == 0){
			for (int f = 0; f < 4; f++){
				double ang = this.rand.nextDouble() * Math.PI * 2.0;
				double dist = Math.sqrt(this.rand.nextDouble()) * (radius * 0.85);
				int fx = MathHelper.floor_double(this.posX + Math.cos(ang) * dist);
				int fz = MathHelper.floor_double(this.posZ + Math.sin(ang) * dist);
				if (!this.worldObj.blockExists(fx, 64, fz)) continue;
				int fy = this.worldObj.getTopSolidOrLiquidBlock(fx, fz) - 1;
				if (fy >= 1 && fy < 252 && Math.abs(fy - this.posY) <= 6.0){
					net.minecraft.block.Block wBlk = this.worldObj.getBlock(fx, fy, fz);
					if ((wBlk == net.minecraft.init.Blocks.water || wBlk == net.minecraft.init.Blocks.flowing_water)
							&& this.worldObj.getBlockMetadata(fx, fy, fz) == 0
							&& canTornadoChangeBlock(sourceCaster, fx, fy, fz)){
						this.worldObj.setBlock(fx, fy, fz, net.minecraft.init.Blocks.ice);
					}
				}
			}
		}

		// Waterspout Tornado synergy: extinguish fires in its path
		if (canBreakBlocks && this.tornadoActiveElement == am2.spell.components.Tornado.ELEMENT_WATER && this.ticksExisted % 4 == 0){
			for (int f = 0; f < 8; f++){
				double ang = this.rand.nextDouble() * Math.PI * 2.0;
				double dist = Math.sqrt(this.rand.nextDouble()) * (radius * 1.15);
				int fx = MathHelper.floor_double(this.posX + Math.cos(ang) * dist);
				int fz = MathHelper.floor_double(this.posZ + Math.sin(ang) * dist);
				if (!this.worldObj.blockExists(fx, 64, fz)) continue;
				int fy = this.worldObj.getTopSolidOrLiquidBlock(fx, fz);
				if (fy >= 1 && fy < 254 && this.worldObj.getBlock(fx, fy, fz) == net.minecraft.init.Blocks.fire
						&& canTornadoChangeBlock(sourceCaster, fx, fy, fz)){
					this.worldObj.setBlockToAir(fx, fy, fz);
				}
			}
		}

		// 3. Calamity Destruction: Sweeping Spiral Ground Stripping + Deep Core Trench + Tree Canopy Devouring
		int[] bIds = new int[12];
		int[] bMetas = new int[12];
		int[] bXs = new int[12];
		int[] bYs = new int[12];
		int[] bZs = new int[12];
		int sampledNow = 0;

		if (this.ticksExisted % 2 == 0 && this.ticksExisted < this.getTicksToExist() - 6){
			int tickBudget = this.tornadoTier >= 5 ? 160 : (this.tornadoTier == 4 ? 110 : (this.tornadoTier == 3 ? 72 : (this.tornadoTier == 2 ? 44 : 26)));
			int maxScanY = Math.min(252, MathHelper.floor_double(this.posY + height * 0.85));
			int brokenThisCycle = 0;
			int mutationAttempts = 0;
			int dropsThisCycle = 0;
			int thrownThisCycle = 0;
			int maxDropsPerCycle = 18;
			int maxThrownPerCycle = this.tornadoTier >= 4 ? 4 : 2;
			int maxActiveThrown = 36;
			boolean dropItems = AMCore.config.TornadoDropsItems();

			double groundSwathR = Math.max(3.5, radius * 0.56);
			int swathAttempts = 22 + this.tornadoTier * 14;
			double sweepBase = this.tornadoSweepCursor;
			this.tornadoSweepCursor += 1.85;

			for (int a = 0; a < swathAttempts && brokenThisCycle < tickBudget && mutationAttempts < tickBudget; a++){
				// Golden-angle spiral sweep guarantees even, thorough swath clearing instead of isolated pinholes
				double frac = (double) a / (double) swathAttempts;
				double ang = sweepBase + a * 2.399963229728653;
				double scanR;
				int mode; // 0 = touchdown ground furrow (shallow 1-2 layer wide clear), 1 = upper canopy/roofs, 2 = outer fragile wind pressure
				if (a % 4 == 3){
					mode = 2;
					scanR = radius * (0.55 + 0.85 * Math.sqrt(frac));
				}else if (a % 4 == 1){
					mode = 1;
					scanR = radius * (0.15 + 0.90 * Math.sqrt(frac));
				}else{
					mode = 0;
					scanR = groundSwathR * Math.sqrt((a % Math.max(1, swathAttempts)) / (double) swathAttempts) * 1.15;
					scanR = Math.max(0.5, Math.min(groundSwathR, scanR + this.rand.nextDouble() * 1.5));
				}

				int bx = MathHelper.floor_double(this.posX + Math.cos(ang) * scanR);
				int bz = MathHelper.floor_double(this.posZ + Math.sin(ang) * scanR);
				if (!this.worldObj.blockExists(bx, 64, bz)) continue;

				int startY = -1;
				if (mode == 1){
					int precipY = Math.min(maxScanY, this.worldObj.getHeightValue(bx, bz) - 1);
					int minCanopyY = Math.max(1, MathHelper.floor_double(this.posY - 6.0));
					for (int sy = precipY; sy >= minCanopyY && sy >= precipY - 10; sy--){
						net.minecraft.block.Block test = this.worldObj.getBlock(bx, sy, bz);
						if (test != null && test != net.minecraft.init.Blocks.air && !test.getMaterial().isLiquid()){
							startY = sy;
							break;
						}
					}
				}else{
					int groundTop = this.worldObj.getTopSolidOrLiquidBlock(bx, bz) - 1;
					if (groundTop >= 1 && groundTop <= maxScanY && Math.abs(groundTop - this.posY) <= 24.0){
						startY = groundTop;
					}
				}
				if (startY < 1 || startY > maxScanY) continue;

				// Clear any top cover (tallgrass, flowers, crops, snow, rails, fences) at startY + 1 so nothing floats
				int scanTopY = Math.min(253, startY + 1);
				int depthLimit = (mode == 2) ? 2 : (mode == 1 ? 3 : (scanR < groundSwathR * 0.55 && this.tornadoTier >= 3 ? 4 : 3));

				for (int dy = 0; dy < depthLimit && brokenThisCycle < tickBudget && mutationAttempts < tickBudget; dy++){
					int cy = scanTopY - dy;
					if (cy <= 0) break;

					net.minecraft.block.Block block = this.worldObj.getBlock(bx, cy, bz);
					if (block == null || block == net.minecraft.init.Blocks.air) continue;
					if (block.getMaterial().isLiquid()){
						// Waterspout/Firenado still scoops visual spray/splashes from ocean/lava pools
						if (!canBreakBlocks || dy > 0){
							if (sampledNow < bIds.length && this.rand.nextInt(3) == 0){
								int lid = net.minecraft.block.Block.getIdFromBlock(block);
								if (lid > 0){
									bIds[sampledNow] = lid;
									bMetas[sampledNow] = this.worldObj.getBlockMetadata(bx, cy, bz);
									bXs[sampledNow] = bx;
									bYs[sampledNow] = cy;
									bZs[sampledNow] = bz;
									sampledNow++;
								}
							}
						}
						break;
					}

					int meta = this.worldObj.getBlockMetadata(bx, cy, bz);
					if (block.hasTileEntity(meta) || this.worldObj.getTileEntity(bx, cy, bz) != null) continue;

					float hardness = block.getBlockHardness(this.worldObj, bx, cy, bz);
					if (hardness < 0.0f) break;
					float allowedHardness = (mode == 2) ? 0.75f : this.tornadoMaxHardness;
					if (hardness > allowedHardness) continue;

					int blockId = net.minecraft.block.Block.getIdFromBlock(block);
					if (blockId <= 0) continue;

					boolean isDisposable = am2.spell.components.Tornado.isDisposableTerrainBlock(block, meta);
					if (!isDisposable){
						// Valuable / modded non-TileEntity blocks are NEVER destroyed: fling them intact as real EntityFallingBlock
						if (!canBreakBlocks){
							continue;
						}
						if (thrownThisCycle < maxThrownPerCycle
								&& activeThrownBlocks < maxActiveThrown
								&& am2.spell.components.Tornado.isThrowableValuableBlock(block, meta)){
							// Failed permission/spawn attempts consume the same finite budget.
							thrownThisCycle++;
							mutationAttempts++;
							if (!canTornadoChangeBlock(sourceCaster, bx, cy, bz)) continue;
							{
								double vdx = (bx + 0.5) - this.posX;
								double vdz = (bz + 0.5) - this.posZ;
								double vd = Math.sqrt(vdx * vdx + vdz * vdz);
								double rDirX = vd > 0.05 ? vdx / vd : 1.0;
								double rDirZ = vd > 0.05 ? vdz / vd : 0.0;
								double rTangX = -rDirZ;
								double rTangZ = rDirX;

								net.minecraft.entity.item.EntityFallingBlock falling = new net.minecraft.entity.item.EntityFallingBlock(
										this.worldObj, bx + 0.5, cy + 0.5, bz + 0.5, block, meta
								);
								falling.field_145812_b = 1; // source is cleared synchronously only after spawn acceptance
								falling.field_145813_c = true; // guaranteed drop as item if landing cell is obstructed
								if (block != net.minecraft.init.Blocks.anvil
										&& tornadoThrownBlocksMayHurt(sourceCaster)){
									falling.func_145806_a(true); // thrown heavy blocks crush mobs/entities they fall on
								}
								falling.motionX = rTangX * 0.45 + rDirX * 0.16;
								falling.motionY = 0.68 + this.rand.nextDouble() * 0.24;
								falling.motionZ = rTangZ * 0.45 + rDirZ * 0.16;
								falling.velocityChanged = true;
								if (!this.worldObj.spawnEntityInWorld(falling) || falling.isDead) continue;
								if (this.worldObj.getBlock(bx, cy, bz) != block
										|| this.worldObj.getBlockMetadata(bx, cy, bz) != meta
										|| !this.worldObj.setBlockToAir(bx, cy, bz)){
									falling.setDead();
									continue;
								}

								activeThrownBlocks++;
								brokenThisCycle++;
								this.tornadoBlocksLifted++;
							}
						}
						continue;
					}

					boolean captured = true;
					if (canBreakBlocks){
						mutationAttempts++;
						if (!canTornadoChangeBlock(sourceCaster, bx, cy, bz)) continue;
						captured = this.worldObj.setBlockToAir(bx, cy, bz);
						if (captured && dropItems && dropsThisCycle < maxDropsPerCycle){
							float dropChance = this.tornadoTier >= 4 ? 0.14f : 0.28f;
							if (this.rand.nextFloat() < dropChance){
								block.dropBlockAsItem(this.worldObj, bx, cy, bz, meta, 0);
								dropsThisCycle++;
							}
						}
						if (captured && this.tornadoActiveElement == am2.spell.components.Tornado.ELEMENT_FIRE
								&& this.tornadoTier >= 4 && dy == depthLimit - 1
								&& this.rand.nextFloat() < 0.12f
								&& this.worldObj.isAirBlock(bx, cy, bz)
								&& this.worldObj.doesBlockHaveSolidTopSurface(this.worldObj, bx, cy - 1, bz)
								&& canTornadoChangeBlock(sourceCaster, bx, cy, bz)){
							this.worldObj.setBlock(bx, cy, bz, net.minecraft.init.Blocks.fire);
						}
					}

					if (captured){
						brokenThisCycle++;
						this.tornadoBlocksLifted++;
						if (sampledNow < bIds.length && (brokenThisCycle <= 5 || this.rand.nextInt(6) == 0)){
							bIds[sampledNow] = blockId;
							bMetas[sampledNow] = meta;
							bXs[sampledNow] = bx;
							bYs[sampledNow] = cy;
							bZs[sampledNow] = bz;
							sampledNow++;
						}
						if (!canBreakBlocks){
							break;
						}
					}
				}
			}
		}

		if (sampledNow > 0 || this.ticksExisted % 3 == 0){
			syncTornadoState(bIds, bMetas, bXs, bYs, bZs, sampledNow);
		}
	}

	private void syncTornadoState(int[] bIds, int[] bMetas, int[] bXs, int[] bYs, int[] bZs, int count){
		int remTicks = Math.max(0, this.getTicksToExist() - this.ticksExisted);
		AMNetHandler.INSTANCE.sendTornadoDebrisToClients(
				this.worldObj, this.getEntityId(),
				this.posX, this.posY, this.posZ,
				this.tornadoRadius, remTicks, this.tornadoDuration,
				this.getSpellColor(), this.tornadoActiveElement,
				(byte) this.tornadoTier, this.tornadoCelestial,
				bIds, bMetas, bXs, bYs, bZs, count
		);
	}

	@Override
	public boolean canBePushed(){
		return false;
	}

	@Override
	public boolean canBeCollidedWith(){
		return false;
	}
}
