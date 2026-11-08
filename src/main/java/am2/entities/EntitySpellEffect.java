package am2.entities;

import am2.AMCore;
import am2.api.math.AMLineSegment;
import am2.api.math.AMVector3;
import am2.api.spell.component.interfaces.ISpellModifier;
import am2.api.spell.enums.SpellModifiers;
import am2.buffs.BuffEffectFrostSlowed;
import am2.damage.DamageSources;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import am2.network.AMNetHandler;
import am2.particles.*;
import am2.spell.SpellHelper;
import am2.spell.SpellUtils;
import am2.spell.modifiers.Colour;
import am2.utility.DummyEntityPlayer;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class EntitySpellEffect extends Entity{

	private float rotation;
	private final float rotationSpeed;

	private int ticksToEffect = 20;
	private int maxTicksToEffect = 20;
	private int maxTicksToEffect_wall = 5;

	private int ticksToExist = 100;

	private ItemStack spellStack;
	private EntityPlayer dummycaster;
	private int casterEntityID;
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
		this.dummycaster = DummyEntityPlayer.fromEntityLiving(caster);
		casterEntityID = caster.getEntityId();
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
		if (dw > 0 && dw != 100) return dw;
		if (this.spellStack == null){
			this.spellStack = this.dataWatcher.getWatchableObjectItemStack(WATCHER_STACK);
		}
		if (this.spellStack != null){
			int calcDuration = SpellUtils.instance.getModifiedInt_Mul(100, this.spellStack, null, null, worldObj, 0, SpellModifiers.DURATION);
			if (calcDuration > 0) return calcDuration;
		}
		return dw > 0 ? dw : this.ticksToExist;
	}

	public void setExplicitColor(int color){
		this.explicitColor = color;
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
		this.dataWatcher.addObject(WATCHER_TICKS_TO_EXIST, 100);
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
		}

		if ((!worldObj.isRemote || isStarfallImpact() || isGrowMandala() || isExplosionImpact() || isLightningImpact()) && this.ticksExisted >= this.getTicksToExist()){
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

				double dist = this.dataWatcher.getWatchableObjectFloat(WATCHER_RADIUS);
				double _rotation = rotation;

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

				if ((AMCore.config.FullGFX() && this.ticksExisted % 2 == 0) || this.ticksExisted % 8 == 0){
					for (int i = 0; i < 4; ++i){
						_rotation = (rotation + (90 * i)) % 360;
						double x = this.posX - Math.cos(3.141 / 180 * (_rotation)) * dist;
						double z = this.posZ - Math.sin(3.141 / 180 * (_rotation)) * dist;

						AMParticle effect = (AMParticle)AMCore.instance.proxy.particleManager.spawn(worldObj, AMParticleIcons.instance.getParticleForAffinity(SpellUtils.instance.mainAffinityFor(spellStack)), x, posY, z);
						if (effect != null){
							effect.setIgnoreMaxAge(false);
							effect.setMaxAge(20);
							effect.setParticleScale(0.15f);
							effect.setRGBColorI(color);
							effect.AddParticleController(new ParticleFloatUpward(effect, 0, 0.07f, 1, false));
							if (AMCore.config.LowGFX()){
								effect.AddParticleController(new ParticleOrbitPoint(effect, posX, posY, posZ, 2, false).setIgnoreYCoordinate(true).SetOrbitSpeed(0.05f).SetTargetDistance(dist).setRotateDirection(true));
							}
						}
					}
				}

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
			for (AMVector3 vec : vecs){
				SpellHelper.instance.applyStageToGround(spellStack, dummycaster, worldObj, (int)vec.x, (int)vec.y, (int)vec.z, 0, vec.x + 0.5, vec.y + 0.5, vec.z + 0.5, 0, false);
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

	public void addMerged(int count){
		this.mergeCount += count;
	}

	public int getMergeCount(){
		return this.mergeCount;
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
		this.dummycaster = DummyEntityPlayer.fromEntityLiving(caster);
		this.casterEntityID = caster != null ? caster.getEntityId() : -1;
		this.spellStack = stack;
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

		if (count <= 1){
			// Single target cast: standard vanilla explosion
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
							block.dropBlockAsItem(this.worldObj, bx, curY, bz, this.worldObj.getBlockMetadata(bx, curY, bz), 0);
							this.worldObj.setBlock(bx, curY, bz, Blocks.air, 0, 2);
						}
					}
				}
			}

			// Single centralized explosion for entity damage, knockback and central crater
			this.worldObj.newExplosion(dummycaster != null ? dummycaster : this, this.posX, this.posY, this.posZ, Math.min(8.0f, finalPower), false, false);
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

	private double lightningAoERadius = 4.0;

	public void setLightningFusion(EntityLivingBase caster, ItemStack stack, double x, double y, double z, int bx, int by, int bz){
		this.dataWatcher.updateObject(WATCHER_TYPE, TYPE_LIGHTNING_FUSION);
		this.setTicksToExist(5);
		this.mergeCount = 1;
		this.dummycaster = DummyEntityPlayer.fromEntityLiving(caster);
		this.casterEntityID = caster != null ? caster.getEntityId() : -1;
		this.spellStack = stack;
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
				target.attackEntityFrom(DamageSources.causeEntityLightningDamage(sourceCaster != null ? sourceCaster : target), finalDmg);
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
		int duration = mergeCount >= 30 ? 60 : (mergeCount >= 15 ? 45 : (mergeCount >= 5 ? 30 : 20));
		this.setTicksToExist(duration);
		this.mergeCount = mergeCount;
		this.lightningAoERadius = radius;
		this.setRadius((float)radius);
		if (stack != null){
			this.dataWatcher.updateObject(WATCHER_STACK, stack);
		}
	}

	public boolean isLightningImpact(){
		return this.dataWatcher.getWatchableObjectInt(WATCHER_TYPE) == TYPE_LIGHTNING_IMPACT;
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
