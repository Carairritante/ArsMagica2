package am2.client.vfx;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class LightningVFXHelperTest {

	@Test
	public void generateBoltPreservesEndpointsAndClampsForksAboveGround() {
		Random rnd = new Random(1337L);
		LightningVFXHelper.BoltPath path = LightningVFXHelper.generateBolt(
				10.0, 82.0, -5.0,
				14.5, 64.0, 3.5,
				12, 3.2, 3, true, rnd
		);

		assertEquals(13, path.x.length);
		assertEquals(13, path.y.length);
		assertEquals(13, path.z.length);
		assertEquals(10.0, path.x[0], 1e-6);
		assertEquals(82.0, path.y[0], 1e-6);
		assertEquals(-5.0, path.z[0], 1e-6);
		assertEquals(14.5, path.x[12], 1e-6);
		assertEquals(64.0, path.y[12], 1e-6);
		assertEquals(3.5, path.z[12], 1e-6);

		for (int i = 0; i < path.x.length; i++) {
			assertFalse(Double.isNaN(path.x[i]) || Double.isInfinite(path.x[i]));
			assertFalse(Double.isNaN(path.y[i]) || Double.isInfinite(path.y[i]));
			assertFalse(Double.isNaN(path.z[i]) || Double.isInfinite(path.z[i]));
		}

		// 3 primary forks + 1 secondary sub-fork
		assertEquals(4, path.forkX.length);
		for (int f = 0; f < path.forkX.length; f++) {
			assertTrue(path.forkAttachFrac[f] > 0.0f && path.forkAttachFrac[f] < 1.0f);
			assertTrue(path.forkWidthRatio[f] > 0.1f && path.forkWidthRatio[f] < 0.7f);
			for (int k = 0; k < path.forkY[f].length; k++) {
				assertTrue("Fork vertex dropped below ground target", path.forkY[f][k] >= 64.34);
			}
		}
	}

	@Test
	public void resolvePaletteProducesValidHeavyStormAndCustomColors() {
		float[] p = new float[12];
		LightningVFXHelper.resolvePalette(-1, p);
		for (int i = 0; i < p.length; i++) {
			assertTrue("Palette entry out of [0,1]: " + p[i], p[i] >= 0.0f && p[i] <= 1.0f);
		}
		// Dry white core brighter than cobalt outer halo
		assertTrue(p[6] > p[0] && p[7] > p[1]);
		// Dark brooding storm cloud tint
		assertTrue(p[9] < 0.2f && p[10] < 0.2f && p[11] < 0.25f);

		// Very dark custom color still lifts into readable electric plasma
		LightningVFXHelper.resolvePalette(0x120518, p);
		for (int i = 0; i < p.length; i++) {
			assertTrue("Custom palette entry out of [0,1]: " + p[i], p[i] >= 0.0f && p[i] <= 1.0f);
		}
		assertTrue(p[6] >= 0.70f && p[7] >= 0.70f && p[8] >= 0.70f);
	}

	@Test
	public void heavyStormFlashCurveProducesDoubleRestrikeEnvelope() {
		assertEquals(1.0f, LightningVFXHelper.heavyStormFlashCurve(0.0f), 1e-4f);
		assertEquals(0.0f, LightningVFXHelper.heavyStormFlashCurve(1.0f), 1e-4f);

		float initial = LightningVFXHelper.heavyStormFlashCurve(0.10f);
		float dip = LightningVFXHelper.heavyStormFlashCurve(0.35f);
		float restrikePeak = LightningVFXHelper.heavyStormFlashCurve(0.44f);
		float tail = LightningVFXHelper.heavyStormFlashCurve(0.85f);

		assertTrue("Initial strike should be at full intensity", initial > 0.95f);
		assertTrue("Brief dip expected before re-strike", dip < initial);
		assertTrue("Secondary re-strike should surge above dip", restrikePeak > dip + 0.15f);
		assertTrue("Tail should decay smoothly", tail < restrikePeak && tail > 0.0f);
	}
}
