package am2.blocks.tileentities;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.*;

public class ArcaneStrikerTest {

	@Test
	public void durationIsClampedBetweenOneAndSixtySeconds() {
		TileEntityArcaneStriker tile = new TileEntityArcaneStriker();
		assertEquals(TileEntityArcaneStriker.DEFAULT_DURATION_SEC, tile.getConfiguredDurationSec());

		tile.setConfiguredDurationSec(-10);
		assertEquals(TileEntityArcaneStriker.MIN_DURATION_SEC, tile.getConfiguredDurationSec());

		tile.setConfiguredDurationSec(0);
		assertEquals(TileEntityArcaneStriker.MIN_DURATION_SEC, tile.getConfiguredDurationSec());

		tile.setConfiguredDurationSec(120);
		assertEquals(TileEntityArcaneStriker.MAX_DURATION_SEC, tile.getConfiguredDurationSec());

		tile.setConfiguredDurationSec(45);
		assertEquals(45, tile.getConfiguredDurationSec());
	}

	@Test
	public void bypassIframesTogglesCleanly() {
		TileEntityArcaneStriker tile = new TileEntityArcaneStriker();
		assertTrue(tile.isBypassIframes());
		tile.setBypassIframes(false);
		assertFalse(tile.isBypassIframes());
		tile.setBypassIframes(true);
		assertTrue(tile.isBypassIframes());
	}

	@Test
	public void meterProgressIsZeroBeforeAnyDamage() {
		TileEntityArcaneStriker tile = new TileEntityArcaneStriker();
		assertEquals(0.0f, tile.computeMeterProgress(), 0.0001f);
	}

	@Test
	public void nbtRoundTripPreservesConfigAndRecords() {
		TileEntityArcaneStriker source = new TileEntityArcaneStriker();
		source.setConfiguredDurationSec(42);
		source.setBypassIframes(false);

		NBTTagCompound tag = new NBTTagCompound();
		source.writeMachineData(tag);
		tag.setDouble("recordTotalDamage", 12500.5);
		tag.setDouble("recordDps", 833.3);
		tag.setDouble("recordPeakHit", 950.0);
		tag.setInteger("recordDurationSec", 15);
		tag.setString("recordHolderName", "Archmage");

		TileEntityArcaneStriker loaded = new TileEntityArcaneStriker();
		loaded.readMachineData(tag);

		assertEquals(42, loaded.getConfiguredDurationSec());
		assertFalse(loaded.isBypassIframes());
		assertEquals(12500.5, loaded.getRecordTotalDamage(), 0.001);
		assertEquals(833.3, loaded.getRecordDps(), 0.001);
		assertEquals(950.0, loaded.getRecordPeakHit(), 0.001);
		assertEquals("Archmage", loaded.getRecordHolderName());
	}

	@Test
	public void dummyCapturesTinyAndHugeDamageAccurately() {
		float base = am2.entities.EntityArcaneTargetDummy.DUMMY_BASE_HEALTH;
		assertEquals(0.5f, am2.entities.EntityArcaneTargetDummy.computeCapturedDamage(base - 0.5f), 0.01f);
		assertEquals(7.5f, am2.entities.EntityArcaneTargetDummy.computeCapturedDamage(base - 7.5f), 0.01f);
		assertEquals(500000.0f, am2.entities.EntityArcaneTargetDummy.computeCapturedDamage(base - 500000.0f), 0.5f);
		assertEquals(0.0f, am2.entities.EntityArcaneTargetDummy.computeCapturedDamage(base + 10.0f), 0.001f);
	}
}
