package am2.entities;

import am2.blocks.tileentities.TileEntityArcaneStriker;
import am2.network.AMNetHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/**
 * Summoned magical training target dummy linked to a {@link TileEntityArcaneStriker} pedestal.
 * Uses calibrated base health (1024.0F) with setHealth-hooked delta capture so both tiny (0.1)
 * and massive overkill hits are measured accurately without float precision loss or lingering ghosts.
 */
public class EntityArcaneTargetDummy extends EntityLiving {

	public static final float DUMMY_BASE_HEALTH = 1024.0F;
	public static final float DUMMY_MAX_HEALTH = 2048.0F;

	private static final int DW_REMAINING_TICKS = 22;
	private static final int DW_TOTAL_DAMAGE_X10 = 23;
	private static final int DW_DPS_X10 = 24;
	private static final int DW_LAST_HIT_X10 = 25;
	private static final int DW_HIT_SEQ = 26;

	private int machineX;
	private int machineY = -1;
	private int machineZ;
	private int machineDim;
	private boolean hasMachineLink = false;
	private boolean bypassIframes = true;
	private int maxLifetimeTicks = 1200;
	private double anchorX;
	private double anchorY;
	private double anchorZ;
	private float anchorYaw;
	private boolean hasAnchor = false;

	private boolean capturingDamage = false;
	private float capturedDamage = 0.0F;

	// Client-side recoil visual tracking
	public int clientLastHitSeq = 0;
	public float clientRecoil = 0.0f;
	public float clientRecoilDirYaw = 0.0f;

	public EntityArcaneTargetDummy(World world) {
		super(world);
		this.setSize(0.75F, 1.95F);
		this.tasks.taskEntries.clear();
		this.targetTasks.taskEntries.clear();
		this.experienceValue = 0;
		this.stepHeight = 0.0F;
		super.setHealth(DUMMY_BASE_HEALTH);
	}

	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		this.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(DUMMY_MAX_HEALTH);
		this.getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1.0D);
		this.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.0D);
	}

	@Override
	protected void entityInit() {
		super.entityInit();
		this.dataWatcher.addObject(DW_REMAINING_TICKS, Integer.valueOf(0));
		this.dataWatcher.addObject(DW_TOTAL_DAMAGE_X10, Integer.valueOf(0));
		this.dataWatcher.addObject(DW_DPS_X10, Integer.valueOf(0));
		this.dataWatcher.addObject(DW_LAST_HIT_X10, Integer.valueOf(0));
		this.dataWatcher.addObject(DW_HIT_SEQ, Integer.valueOf(0));
	}

	public void bindToMachine(int x, int y, int z, int dim, boolean bypassIframes, int maxTicks) {
		this.machineX = x;
		this.machineY = y;
		this.machineZ = z;
		this.machineDim = dim;
		this.hasMachineLink = true;
		this.bypassIframes = bypassIframes;
		this.maxLifetimeTicks = Math.max(20, maxTicks);
		super.setHealth(DUMMY_BASE_HEALTH);
		syncLiveStats(maxTicks, 0.0, 0.0, 0.0, 0);
	}

	public boolean isBoundTo(int x, int y, int z, int dim) {
		return hasMachineLink && machineX == x && machineY == y && machineZ == z && machineDim == dim;
	}

	public void setBypassIframes(boolean bypass) {
		this.bypassIframes = bypass;
	}

	public void syncLiveStats(int remainingTicks, double totalDmg, double dps, double lastHit, int hitCount) {
		this.dataWatcher.updateObject(DW_REMAINING_TICKS, Integer.valueOf(Math.max(0, remainingTicks)));
		this.dataWatcher.updateObject(DW_TOTAL_DAMAGE_X10, Integer.valueOf(clampScaled(totalDmg)));
		this.dataWatcher.updateObject(DW_DPS_X10, Integer.valueOf(clampScaled(dps)));
		this.dataWatcher.updateObject(DW_LAST_HIT_X10, Integer.valueOf(clampScaled(lastHit)));
	}

	private static int clampScaled(double val) {
		long scaled = Math.round(val * 10.0);
		if (scaled > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		if (scaled < 0L) {
			return 0;
		}
		return (int) scaled;
	}

	public int getRemainingTicks() { return dataWatcher.getWatchableObjectInt(DW_REMAINING_TICKS); }
	public double getTotalDamage() { return dataWatcher.getWatchableObjectInt(DW_TOTAL_DAMAGE_X10) / 10.0; }
	public double getDps() { return dataWatcher.getWatchableObjectInt(DW_DPS_X10) / 10.0; }
	public double getLastHitDamage() { return dataWatcher.getWatchableObjectInt(DW_LAST_HIT_X10) / 10.0; }
	public int getHitSeq() { return dataWatcher.getWatchableObjectInt(DW_HIT_SEQ); }

	@Override
	public void setLocationAndAngles(double x, double y, double z, float yaw, float pitch) {
		super.setLocationAndAngles(x, y, z, yaw, pitch);
		this.anchorX = x;
		this.anchorY = y;
		this.anchorZ = z;
		this.anchorYaw = yaw;
		this.hasAnchor = true;
	}

	@Override
	public void onUpdate() {
		super.onUpdate();

		this.motionX = 0.0D;
		this.motionY = 0.0D;
		this.motionZ = 0.0D;
		this.fallDistance = 0.0F;

		if (worldObj.isRemote) {
			int seq = getHitSeq();
			if (seq != clientLastHitSeq) {
				clientLastHitSeq = seq;
				clientRecoil = 1.0f;
			} else if (clientRecoil > 0.0f) {
				clientRecoil = Math.max(0.0f, clientRecoil - 0.14f);
			}
			return;
		}

		if (hasAnchor && (Math.abs(posX - anchorX) > 0.02 || Math.abs(posY - anchorY) > 0.02 || Math.abs(posZ - anchorZ) > 0.02)) {
			this.setPosition(anchorX, anchorY, anchorZ);
			this.rotationYaw = this.anchorYaw;
			this.rotationYawHead = this.anchorYaw;
			this.renderYawOffset = this.anchorYaw;
		}

		if (!hasMachineLink || this.ticksExisted > maxLifetimeTicks + 20) {
			this.setDead();
			return;
		}
		if (this.ticksExisted % 2 == 0) {
			if (worldObj.provider.dimensionId != machineDim || !worldObj.blockExists(machineX, machineY, machineZ)) {
				this.setDead();
				return;
			}
			TileEntity te = worldObj.getTileEntity(machineX, machineY, machineZ);
			if (!(te instanceof TileEntityArcaneStriker)
					|| !((TileEntityArcaneStriker) te).isTestActive()
					|| !((TileEntityArcaneStriker) te).isExpectedDummy(this)) {
				this.setDead();
			}
		}
	}

	@Override
	protected boolean isAIEnabled() {
		return false;
	}

	@Override
	protected boolean canDespawn() {
		return false;
	}

	@Override
	public boolean canBePushed() {
		return false;
	}

	@Override
	protected void collideWithEntity(Entity entity) {
	}

	@Override
	public void applyEntityCollision(Entity entity) {
	}

	@Override
	public void knockBack(Entity attacker, float strength, double dx, double dz) {
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean isCreatureType(EnumCreatureType type, boolean forSpawnCount) {
		return false;
	}

	@Override
	public boolean attackEntityFrom(DamageSource source, float amount) {
		if (source == DamageSource.inWall || source == DamageSource.drown || source == DamageSource.outOfWorld || amount <= 0.0F) {
			return false;
		}
		if (worldObj == null || worldObj.isRemote) {
			return false;
		}
		if (bypassIframes) {
			this.hurtResistantTime = 0;
			this.lastDamage = 0.0F;
		}
		boolean hit = super.attackEntityFrom(source, amount);
		if (bypassIframes) {
			this.hurtResistantTime = 0;
			this.lastDamage = 0.0F;
		}
		return hit;
	}

	@Override
	protected void damageEntity(DamageSource source, float amount) {
		super.setHealth(DUMMY_BASE_HEALTH);
		capturedDamage = 0.0F;
		capturingDamage = true;
		try {
			super.damageEntity(source, amount);
		} finally {
			capturingDamage = false;
			super.setHealth(DUMMY_BASE_HEALTH);
		}

		float dealt = capturedDamage;
		if (dealt <= 0.0F) {
			return;
		}

		if (worldObj != null && !worldObj.isRemote) {
			this.dataWatcher.updateObject(DW_HIT_SEQ, Integer.valueOf(getHitSeq() + 1));
			String attackerName = resolveAttackerName(source);
			TileEntityArcaneStriker te = resolveMachine();
			if (te != null) {
				te.recordHit(dealt, attackerName);
			}
			AMNetHandler.INSTANCE.sendDummyDamagePopup(
					worldObj.provider.dimensionId,
					this.posX,
					this.posY + 1.45,
					this.posZ,
					dealt,
					source != null ? source.damageType : "generic"
			);
		}
	}

	private static String resolveAttackerName(DamageSource source) {
		if (source == null) {
			return "";
		}
		Entity attacker = source.getEntity();
		if (attacker instanceof EntityPlayer) {
			return ((EntityPlayer) attacker).getCommandSenderName();
		}
		Entity direct = source.getSourceOfDamage();
		if (direct instanceof EntityPlayer) {
			return ((EntityPlayer) direct).getCommandSenderName();
		}
		return "";
	}

	/**
	 * Computes captured hit damage from the target health value passed to {@link #setHealth(float)}.
	 */
	public static float computeCapturedDamage(float newHealth) {
		return Math.max(0.0F, DUMMY_BASE_HEALTH - newHealth);
	}

	@Override
	public void setHealth(float health) {
		if (capturingDamage) {
			float delta = computeCapturedDamage(health);
			if (delta > capturedDamage) {
				capturedDamage = delta;
			}
			super.setHealth(DUMMY_BASE_HEALTH);
			return;
		}
		super.setHealth(DUMMY_BASE_HEALTH);
	}

	@Override
	public void onDeath(DamageSource cause) {
		super.setHealth(DUMMY_BASE_HEALTH);
	}

	public void discardDummy() {
		this.setDead();
	}

	@Override
	protected boolean interact(EntityPlayer player) {
		if (player == null) {
			return false;
		}
		if (!worldObj.isRemote && player.isSneaking()) {
			TileEntityArcaneStriker te = resolveMachine();
			if (te != null) {
				te.stopTestEarly();
			} else {
				this.setDead();
			}
			return true;
		}
		return super.interact(player);
	}

	private TileEntityArcaneStriker resolveMachine() {
		if (!hasMachineLink || worldObj == null || worldObj.provider.dimensionId != machineDim) {
			return null;
		}
		if (!worldObj.blockExists(machineX, machineY, machineZ)) {
			return null;
		}
		TileEntity te = worldObj.getTileEntity(machineX, machineY, machineZ);
		return te instanceof TileEntityArcaneStriker ? (TileEntityArcaneStriker) te : null;
	}

	@Override
	protected void dropFewItems(boolean recentlyHit, int looting) {
	}

	@Override
	protected String getHurtSound() {
		return "dig.wood";
	}

	@Override
	protected String getDeathSound() {
		return "dig.wood";
	}

	@Override
	public String getCommandSenderName() {
		return StatCollector.translateToLocal("entity.arsmagica2.ArcaneTargetDummy.name");
	}

	@Override
	public void writeEntityToNBT(NBTTagCompound tag) {
		super.writeEntityToNBT(tag);
		tag.setBoolean("hasMachineLink", hasMachineLink);
		tag.setInteger("machineX", machineX);
		tag.setInteger("machineY", machineY);
		tag.setInteger("machineZ", machineZ);
		tag.setInteger("machineDim", machineDim);
		tag.setBoolean("bypassIframes", bypassIframes);
		tag.setInteger("maxLifetimeTicks", maxLifetimeTicks);
		tag.setBoolean("hasAnchor", hasAnchor);
		tag.setDouble("anchorX", anchorX);
		tag.setDouble("anchorY", anchorY);
		tag.setDouble("anchorZ", anchorZ);
		tag.setFloat("anchorYaw", anchorYaw);
	}

	@Override
	public void readEntityFromNBT(NBTTagCompound tag) {
		super.readEntityFromNBT(tag);
		hasMachineLink = tag.getBoolean("hasMachineLink");
		machineX = tag.getInteger("machineX");
		machineY = tag.getInteger("machineY");
		machineZ = tag.getInteger("machineZ");
		machineDim = tag.getInteger("machineDim");
		bypassIframes = !tag.hasKey("bypassIframes") || tag.getBoolean("bypassIframes");
		maxLifetimeTicks = Math.max(20, tag.getInteger("maxLifetimeTicks"));
		hasAnchor = tag.getBoolean("hasAnchor");
		if (hasAnchor) {
			anchorX = tag.getDouble("anchorX");
			anchorY = tag.getDouble("anchorY");
			anchorZ = tag.getDouble("anchorZ");
			anchorYaw = tag.getFloat("anchorYaw");
		}
		super.setHealth(DUMMY_BASE_HEALTH);
	}
}
