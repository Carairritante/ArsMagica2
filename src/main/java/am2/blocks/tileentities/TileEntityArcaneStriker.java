package am2.blocks.tileentities;

import am2.entities.EntityArcaneTargetDummy;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;

import java.util.List;
import java.util.UUID;

/**
 * Authoritative controller for the Arcane High Striker testing machine and its summoned training dummy.
 * Tracks live test duration (1..60s), bypass-iframes mode, current session metrics, last result, and record.
 */
public class TileEntityArcaneStriker extends TileEntity {

	public static final int MIN_DURATION_SEC = 1;
	public static final int MAX_DURATION_SEC = 60;
	public static final int DEFAULT_DURATION_SEC = 15;

	private int configuredDurationSec = DEFAULT_DURATION_SEC;
	private boolean bypassIframes = true;

	private boolean testActive = false;
	private int remainingTicks = 0;
	private int elapsedTicks = 0;
	private int activeDummyEntityId = -1;
	private UUID activeDummyUuid = null;

	private double currentTotalDamage = 0.0;
	private double currentPeakHit = 0.0;
	private double lastHitDamage = 0.0;
	private int currentHitCount = 0;
	private String activeTesterName = "";

	private double lastTotalDamage = 0.0;
	private double lastDps = 0.0;
	private double lastPeakHit = 0.0;
	private int lastHitCount = 0;
	private int lastDurationSec = 0;
	private String lastTesterName = "";

	private double recordTotalDamage = 0.0;
	private double recordDps = 0.0;
	private double recordPeakHit = 0.0;
	private int recordDurationSec = 0;
	private String recordHolderName = "";

	private int bellTimerTicks = 0;
	private int syncCooldown = 0;
	private boolean dirtySync = false;
	private long lastStartWorldTick = -100L;

	// Client-only smoothed visual state for TESR slider & bell bounce
	public float clientSliderProgress = 0.0f;
	public float clientBellBounce = 0.0f;

	@Override
	public void updateEntity() {
		if (worldObj == null) {
			return;
		}
		if (worldObj.isRemote) {
			updateClientVisuals();
			return;
		}

		if (bellTimerTicks > 0) {
			bellTimerTicks--;
			if (bellTimerTicks == 0) {
				dirtySync = true;
			}
		}

		if (testActive) {
			EntityArcaneTargetDummy dummy = findActiveDummy();
			if (dummy == null && elapsedTicks > 5) {
				// Dummy was removed externally (e.g. kill/unload) -> conclude safely cleanly with accrued totals.
				finishTest(true);
				return;
			}
			elapsedTicks++;
			if (remainingTicks > 0) {
				remainingTicks--;
			}
			if (dummy != null && !dummy.isDead) {
				double liveDps = elapsedTicks > 0 ? (currentTotalDamage / Math.max(0.05, elapsedTicks / 20.0)) : 0.0;
				dummy.syncLiveStats(remainingTicks, currentTotalDamage, liveDps, lastHitDamage, currentHitCount);
			}
			if (remainingTicks <= 0) {
				finishTest(false);
			} else if (elapsedTicks % 4 == 0) {
				dirtySync = true;
			}
		}

		if (syncCooldown > 0) {
			syncCooldown--;
		}
		if (dirtySync && syncCooldown <= 0) {
			dirtySync = false;
			syncCooldown = 2;
			markDirty();
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
	}

	private void updateClientVisuals() {
		float targetProgress = computeMeterProgress();
		clientSliderProgress += (targetProgress - clientSliderProgress) * 0.25f;
		if (Math.abs(targetProgress - clientSliderProgress) < 0.002f) {
			clientSliderProgress = targetProgress;
		}
		if (bellTimerTicks > 0) {
			bellTimerTicks--;
			clientBellBounce = bellTimerTicks / 30.0f;
		} else {
			clientBellBounce *= 0.8f;
		}
	}

	public float computeMeterProgress() {
		double metric = testActive ? currentTotalDamage : lastTotalDamage;
		if (metric <= 0.0) {
			return 0.0f;
		}
		if (recordTotalDamage > 0.0) {
			return (float) Math.min(1.0, metric / recordTotalDamage);
		}
		return (float) Math.min(1.0, Math.log10(1.0 + metric) / 4.0);
	}

	public boolean startTest(EntityPlayer requester) {
		if (worldObj == null || worldObj.isRemote) {
			return false;
		}
		long nowTick = worldObj.getTotalWorldTime();
		if (nowTick - lastStartWorldTick < 5L) {
			return false;
		}
		// Stopping a test or failing to find space must not reset the start throttle.
		lastStartWorldTick = nowTick;
		if (testActive) {
			finishTest(true);
		} else {
			discardActiveDummy();
		}

		double[] spawnPos = resolveDummySpawnPosition();
		if (spawnPos == null) {
			if (requester != null) {
				requester.addChatMessage(new ChatComponentTranslation("am2.arcane_striker.no_space"));
			}
			return false;
		}

		configuredDurationSec = MathHelper.clamp_int(configuredDurationSec, MIN_DURATION_SEC, MAX_DURATION_SEC);
		testActive = true;
		remainingTicks = configuredDurationSec * 20;
		elapsedTicks = 0;
		currentTotalDamage = 0.0;
		currentPeakHit = 0.0;
		lastHitDamage = 0.0;
		currentHitCount = 0;
		activeTesterName = requester != null ? requester.getCommandSenderName() : "";

		EntityArcaneTargetDummy dummy = new EntityArcaneTargetDummy(worldObj);
		dummy.bindToMachine(xCoord, yCoord, zCoord, worldObj.provider.dimensionId, bypassIframes, remainingTicks);
		float yaw = getFacingYaw();
		dummy.setLocationAndAngles(spawnPos[0], spawnPos[1], spawnPos[2], yaw, 0.0f);
		dummy.rotationYawHead = yaw;
		dummy.renderYawOffset = yaw;
		worldObj.spawnEntityInWorld(dummy);

		activeDummyEntityId = dummy.getEntityId();
		activeDummyUuid = dummy.getUniqueID();

		worldObj.playSoundEffect(xCoord + 0.5, yCoord + 1.0, zCoord + 0.5, "note.pling", 0.8f, 1.05f);
		requestSyncImmediate();
		return true;
	}

	public void stopTestEarly() {
		if (worldObj == null || worldObj.isRemote || !testActive) {
			return;
		}
		finishTest(false);
	}

	public void finishTest(boolean silent) {
		if (!testActive) {
			return;
		}
		testActive = false;
		discardActiveDummy();

		double elapsedSec = Math.max(0.05, elapsedTicks / 20.0);
		double dps = currentTotalDamage / elapsedSec;

		lastTotalDamage = currentTotalDamage;
		lastDps = dps;
		lastPeakHit = currentPeakHit;
		lastHitCount = currentHitCount;
		lastDurationSec = Math.max(1, (int) Math.round(elapsedSec));
		lastTesterName = activeTesterName;

		boolean newRecord = currentTotalDamage > 0.0 && currentTotalDamage >= recordTotalDamage;
		if (newRecord) {
			recordTotalDamage = currentTotalDamage;
			recordDps = dps;
			recordPeakHit = currentPeakHit;
			recordDurationSec = lastDurationSec;
			recordHolderName = activeTesterName;
			bellTimerTicks = 30;
			if (worldObj != null && !silent) {
				worldObj.playSoundEffect(xCoord + 0.5, yCoord + 1.9, zCoord + 0.5, "random.orb", 0.9f, 1.35f);
				worldObj.playSoundEffect(xCoord + 0.5, yCoord + 1.9, zCoord + 0.5, "note.pling", 1.0f, 1.8f);
			}
		} else if (worldObj != null && !silent) {
			worldObj.playSoundEffect(xCoord + 0.5, yCoord + 1.6, zCoord + 0.5, "note.pling", 0.7f, 1.25f);
		}

		if (worldObj != null && !silent) {
			notifyNearbyPlayersResult(newRecord);
		}
		remainingTicks = 0;
		requestSyncImmediate();
	}

	@SuppressWarnings("unchecked")
	private void notifyNearbyPlayersResult(boolean newRecord) {
		AxisAlignedBB box = AxisAlignedBB.getBoundingBox(xCoord - 14, yCoord - 8, zCoord - 14, xCoord + 15, yCoord + 10, zCoord + 15);
		List<EntityPlayer> players = worldObj.getEntitiesWithinAABB(EntityPlayer.class, box);
		String key = newRecord ? "am2.arcane_striker.result_record" : "am2.arcane_striker.result";
		EnumChatFormatting color = newRecord ? EnumChatFormatting.GOLD : EnumChatFormatting.AQUA;
		ChatComponentTranslation msg = new ChatComponentTranslation(
				key,
				String.format("%.1f", lastTotalDamage),
				String.format("%.1f", lastDps),
				String.format("%.1f", lastPeakHit),
				lastDurationSec
		);
		msg.getChatStyle().setColor(color);
		for (EntityPlayer p : players) {
			p.addChatMessage(msg);
		}
	}

	public void recordHit(float damage, String attackerName) {
		if (worldObj == null || worldObj.isRemote || !testActive || damage <= 0.0f) {
			return;
		}
		currentTotalDamage += damage;
		currentHitCount++;
		if (damage > currentPeakHit) {
			currentPeakHit = damage;
		}
		lastHitDamage = damage;
		if (attackerName != null && !attackerName.isEmpty()) {
			activeTesterName = attackerName;
		}
		if (recordTotalDamage > 0.0 && currentTotalDamage >= recordTotalDamage && bellTimerTicks <= 0) {
			bellTimerTicks = 24;
			worldObj.playSoundEffect(xCoord + 0.5, yCoord + 1.0, zCoord + 0.5, "note.pling", 0.85f, 1.95f);
		}
		requestSyncImmediate();
	}

	public void setConfiguredDurationSec(int seconds) {
		this.configuredDurationSec = MathHelper.clamp_int(seconds, MIN_DURATION_SEC, MAX_DURATION_SEC);
		requestSyncImmediate();
	}

	public void setBypassIframes(boolean bypass) {
		this.bypassIframes = bypass;
		EntityArcaneTargetDummy dummy = findActiveDummy();
		if (dummy != null) {
			dummy.setBypassIframes(bypass);
		}
		requestSyncImmediate();
	}

	public void resetRecord() {
		this.recordTotalDamage = 0.0;
		this.recordDps = 0.0;
		this.recordPeakHit = 0.0;
		this.recordDurationSec = 0;
		this.recordHolderName = "";
		requestSyncImmediate();
	}

	public void discardActiveDummy() {
		if (worldObj == null || worldObj.isRemote) {
			activeDummyEntityId = -1;
			activeDummyUuid = null;
			return;
		}
		EntityArcaneTargetDummy dummy = findActiveDummy();
		if (dummy != null) {
			dummy.discardDummy();
		}
		// Sweep any leftover dummy linked to this pedestal or sitting on top of it
		AxisAlignedBB box = AxisAlignedBB.getBoundingBox(xCoord - 6, yCoord - 2, zCoord - 6, xCoord + 7, yCoord + 6, zCoord + 7);
		@SuppressWarnings("unchecked")
		List<EntityArcaneTargetDummy> nearby = worldObj.getEntitiesWithinAABB(EntityArcaneTargetDummy.class, box);
		for (EntityArcaneTargetDummy d : nearby) {
			boolean onTop = Math.abs(d.posX - (xCoord + 0.5)) < 0.75
					&& Math.abs(d.posZ - (zCoord + 0.5)) < 0.75
					&& Math.abs(d.posY - (yCoord + 1.0)) < 1.2;
			if (onTop || d.isBoundTo(xCoord, yCoord, zCoord, worldObj.provider.dimensionId)) {
				d.discardDummy();
			}
		}
		activeDummyEntityId = -1;
		activeDummyUuid = null;
	}

	public boolean isExpectedDummy(EntityArcaneTargetDummy dummy) {
		if (dummy == null) {
			return false;
		}
		if (activeDummyUuid != null) {
			return activeDummyUuid.equals(dummy.getUniqueID());
		}
		return activeDummyEntityId >= 0 && activeDummyEntityId == dummy.getEntityId();
	}

	public EntityArcaneTargetDummy findActiveDummy() {
		if (worldObj == null) {
			return null;
		}
		if (activeDummyEntityId >= 0) {
			Entity e = worldObj.getEntityByID(activeDummyEntityId);
			if (e instanceof EntityArcaneTargetDummy && !e.isDead) {
				return (EntityArcaneTargetDummy) e;
			}
		}
		if (activeDummyUuid != null) {
			@SuppressWarnings("unchecked")
			List<Entity> loaded = worldObj.loadedEntityList;
			for (int i = 0; i < loaded.size(); i++) {
				Entity e = loaded.get(i);
				if (e instanceof EntityArcaneTargetDummy && !e.isDead && activeDummyUuid.equals(e.getUniqueID())) {
					activeDummyEntityId = e.getEntityId();
					return (EntityArcaneTargetDummy) e;
				}
			}
		}
		return null;
	}

	private double[] resolveDummySpawnPosition() {
		if (worldObj == null || yCoord < 0 || yCoord >= 253) {
			return null;
		}
		if (worldObj.isAirBlock(xCoord, yCoord + 1, zCoord) && worldObj.isAirBlock(xCoord, yCoord + 2, zCoord)) {
			return new double[] { xCoord + 0.5, yCoord + 1.0, zCoord + 0.5 };
		}
		return null;
	}

	private float getFacingYaw() {
		int meta = worldObj != null ? (worldObj.getBlockMetadata(xCoord, yCoord, zCoord) & 3) : 0;
		switch (meta) {
			case 0: return 0.0f;
			case 1: return 90.0f;
			case 2: return 180.0f;
			case 3: return 270.0f;
			default: return 0.0f;
		}
	}

	private void requestSyncImmediate() {
		if (worldObj != null && !worldObj.isRemote) {
			markDirty();
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
	}

	@Override
	public void onChunkUnload() {
		if (worldObj != null && !worldObj.isRemote && testActive) {
			finishTest(true);
		}
		super.onChunkUnload();
	}

	@Override
	public void invalidate() {
		if (worldObj != null && !worldObj.isRemote && testActive) {
			finishTest(true);
		}
		super.invalidate();
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return AxisAlignedBB.getBoundingBox(xCoord - 1, yCoord, zCoord - 1, xCoord + 2, yCoord + 4.5, zCoord + 2);
	}

	@Override
	public void writeToNBT(NBTTagCompound tag) {
		super.writeToNBT(tag);
		writeMachineData(tag);
	}

	public void writeMachineData(NBTTagCompound tag) {
		tag.setInteger("configuredDurationSec", configuredDurationSec);
		tag.setBoolean("bypassIframes", bypassIframes);
		tag.setBoolean("testActive", testActive);
		tag.setInteger("remainingTicks", remainingTicks);
		tag.setInteger("elapsedTicks", elapsedTicks);
		tag.setInteger("activeDummyEntityId", activeDummyEntityId);
		if (activeDummyUuid != null) {
			tag.setLong("dummyUuidMost", activeDummyUuid.getMostSignificantBits());
			tag.setLong("dummyUuidLeast", activeDummyUuid.getLeastSignificantBits());
		}

		tag.setDouble("currentTotalDamage", currentTotalDamage);
		tag.setDouble("currentPeakHit", currentPeakHit);
		tag.setDouble("lastHitDamage", lastHitDamage);
		tag.setInteger("currentHitCount", currentHitCount);
		tag.setString("activeTesterName", activeTesterName);

		tag.setDouble("lastTotalDamage", lastTotalDamage);
		tag.setDouble("lastDps", lastDps);
		tag.setDouble("lastPeakHit", lastPeakHit);
		tag.setInteger("lastHitCount", lastHitCount);
		tag.setInteger("lastDurationSec", lastDurationSec);
		tag.setString("lastTesterName", lastTesterName);

		tag.setDouble("recordTotalDamage", recordTotalDamage);
		tag.setDouble("recordDps", recordDps);
		tag.setDouble("recordPeakHit", recordPeakHit);
		tag.setInteger("recordDurationSec", recordDurationSec);
		tag.setString("recordHolderName", recordHolderName);

		tag.setInteger("bellTimerTicks", bellTimerTicks);
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
		readMachineData(tag);
	}

	public void readMachineData(NBTTagCompound tag) {
		configuredDurationSec = MathHelper.clamp_int(tag.getInteger("configuredDurationSec"), MIN_DURATION_SEC, MAX_DURATION_SEC);
		if (!tag.hasKey("configuredDurationSec")) {
			configuredDurationSec = DEFAULT_DURATION_SEC;
		}
		bypassIframes = !tag.hasKey("bypassIframes") || tag.getBoolean("bypassIframes");
		testActive = tag.getBoolean("testActive");
		remainingTicks = tag.getInteger("remainingTicks");
		elapsedTicks = tag.getInteger("elapsedTicks");
		activeDummyEntityId = tag.hasKey("activeDummyEntityId") ? tag.getInteger("activeDummyEntityId") : -1;
		if (tag.hasKey("dummyUuidMost") && tag.hasKey("dummyUuidLeast")) {
			activeDummyUuid = new UUID(tag.getLong("dummyUuidMost"), tag.getLong("dummyUuidLeast"));
		} else {
			activeDummyUuid = null;
		}

		currentTotalDamage = tag.getDouble("currentTotalDamage");
		currentPeakHit = tag.getDouble("currentPeakHit");
		lastHitDamage = tag.getDouble("lastHitDamage");
		currentHitCount = tag.getInteger("currentHitCount");
		activeTesterName = tag.getString("activeTesterName");

		lastTotalDamage = tag.getDouble("lastTotalDamage");
		lastDps = tag.getDouble("lastDps");
		lastPeakHit = tag.getDouble("lastPeakHit");
		lastHitCount = tag.getInteger("lastHitCount");
		lastDurationSec = tag.getInteger("lastDurationSec");
		lastTesterName = tag.getString("lastTesterName");

		recordTotalDamage = tag.getDouble("recordTotalDamage");
		recordDps = tag.getDouble("recordDps");
		recordPeakHit = tag.getDouble("recordPeakHit");
		recordDurationSec = tag.getInteger("recordDurationSec");
		recordHolderName = tag.getString("recordHolderName");

		bellTimerTicks = tag.getInteger("bellTimerTicks");
	}

	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound tag = new NBTTagCompound();
		writeToNBT(tag);
		int meta = worldObj != null ? worldObj.getBlockMetadata(xCoord, yCoord, zCoord) : 0;
		return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, meta, tag);
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
		readFromNBT(pkt.func_148857_g());
	}

	public int getConfiguredDurationSec() { return configuredDurationSec; }
	public boolean isBypassIframes() { return bypassIframes; }
	public boolean isTestActive() { return testActive; }
	public int getRemainingTicks() { return remainingTicks; }
	public int getElapsedTicks() { return elapsedTicks; }
	public double getCurrentTotalDamage() { return currentTotalDamage; }
	public double getCurrentDps() { return elapsedTicks > 0 ? (currentTotalDamage / Math.max(0.05, elapsedTicks / 20.0)) : 0.0; }
	public double getCurrentPeakHit() { return currentPeakHit; }
	public double getLastHitDamage() { return lastHitDamage; }
	public int getCurrentHitCount() { return currentHitCount; }
	public String getActiveTesterName() { return activeTesterName; }
	public double getLastTotalDamage() { return lastTotalDamage; }
	public double getLastDps() { return lastDps; }
	public double getLastPeakHit() { return lastPeakHit; }
	public int getLastHitCount() { return lastHitCount; }
	public int getLastDurationSec() { return lastDurationSec; }
	public String getLastTesterName() { return lastTesterName; }
	public double getRecordTotalDamage() { return recordTotalDamage; }
	public double getRecordDps() { return recordDps; }
	public double getRecordPeakHit() { return recordPeakHit; }
	public int getRecordDurationSec() { return recordDurationSec; }
	public String getRecordHolderName() { return recordHolderName; }
	public int getBellTimerTicks() { return bellTimerTicks; }
}
