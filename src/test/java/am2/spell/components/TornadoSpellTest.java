package am2.spell.components;

import am2.api.spell.enums.Affinity;
import am2.client.vfx.TornadoVFX;
import am2.entities.EntitySpellEffect;
import am2.network.AMPacketIDs;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.EnumSet;

import static org.junit.Assert.*;

public class TornadoSpellTest {

	@Test
	public void tornadoComponentMetadataAndConstantsAreValid() {
		Tornado tornado = new Tornado();
		assertEquals(102, tornado.getID());
		assertEquals(6500.0f, tornado.manaCost(null), 0.01f);
		assertEquals(450.0f, tornado.burnout(null), 0.01f);
		assertEquals(EnumSet.of(Affinity.AIR), tornado.getAffinity());
		assertEquals((byte) 82, AMPacketIDs.TORNADO_DEBRIS);
		assertEquals(13, EntitySpellEffect.TYPE_TORNADO);
		assertEquals(1.00f, Tornado.getTierDamageMultiplier(1), 0.001f);
		assertEquals(2.45f, Tornado.getTierDamageMultiplier(5), 0.001f);
		assertTrue(Tornado.getProximityDamageMultiplier(2.0, 20.0) > Tornado.getProximityDamageMultiplier(12.0, 20.0));
		assertTrue(Tornado.getProximityDamageMultiplier(12.0, 20.0) > Tornado.getProximityDamageMultiplier(19.0, 20.0));
	}

	@Test
	public void tornadoEntitySetupClampsRadiusAndFlagsType() {
		EntitySpellEffect fx = new EntitySpellEffect(null);
		fx.setTornado(null, null, 10.0, 64.0, -20.0, 12.5f, 240, 6.0f, 1, 2, 3.5f, 1.0, 0.0, Tornado.ELEMENT_WATER, 4, Tornado.CELESTIAL_SOLAR);
		assertTrue(fx.isTornado());
		assertEquals(12.5f, fx.getRadius(), 0.001f);
		assertEquals(Tornado.ELEMENT_WATER, fx.getTornadoElement());
		assertEquals(4, fx.getTornadoTier());
		assertEquals(Tornado.CELESTIAL_SOLAR, fx.getTornadoCelestial());

		// Refreshing with a larger radius and higher tier upgrades the active tornado smoothly
		fx.refreshTornado(18.0f, 9.0f, 2, 2, 6.5f, Tornado.ELEMENT_LIGHTNING, 5, Tornado.CELESTIAL_LUNAR);
		assertEquals(18.0f, fx.getRadius(), 0.001f);
		assertEquals(Tornado.ELEMENT_LIGHTNING, fx.getTornadoElement());
		assertEquals(5, fx.getTornadoTier());
		assertEquals(Tornado.CELESTIAL_LUNAR, fx.getTornadoCelestial());

		EntitySpellEffect clamped = new EntitySpellEffect(null);
		clamped.setTornado(null, null, 0.0, 64.0, 0.0, 99.0f, 100, 4.0f, 0, 0, 2.2f, 0.0, 1.0);
		assertEquals(40.0f, clamped.getRadius(), 0.001f);
		assertEquals(Tornado.ELEMENT_AIR, clamped.getTornadoElement());
		assertEquals(1, clamped.getTornadoTier());
	}

	@Test
	public void fujitaTierScaleAndSolarLunarSpecializationBehaveCorrectly() {
		// F1: base tornado
		assertEquals(1, Tornado.computeFujitaTier(0, 0, 0, 0));
		// F2: 1 basic Radius or Damage modifier
		assertEquals(2, Tornado.computeFujitaTier(0, 0, 1, 0));
		assertEquals(2, Tornado.computeFujitaTier(0, 0, 0, 1));
		// F3: 2+ basic mods OR 1 Solar / 1 Lunar
		assertEquals(3, Tornado.computeFujitaTier(0, 0, 1, 1));
		assertEquals(3, Tornado.computeFujitaTier(1, 0, 0, 0));
		assertEquals(3, Tornado.computeFujitaTier(0, 1, 0, 0));
		// F4: 2 Solar/Lunar OR 1 Solar/Lunar + 2 basic mods
		assertEquals(4, Tornado.computeFujitaTier(2, 0, 0, 0));
		assertEquals(4, Tornado.computeFujitaTier(0, 2, 0, 0));
		assertEquals(4, Tornado.computeFujitaTier(1, 0, 1, 1));
		// F5: 3 Solar or 3 Lunar or 2 Celestial + 2 basic mods
		assertEquals(5, Tornado.computeFujitaTier(3, 0, 0, 0));
		assertEquals(5, Tornado.computeFujitaTier(0, 3, 0, 0));
		assertEquals(5, Tornado.computeFujitaTier(2, 0, 1, 1));

		assertEquals(Tornado.CELESTIAL_NONE, Tornado.computeCelestialMode(0, 0));
		assertEquals(Tornado.CELESTIAL_SOLAR, Tornado.computeCelestialMode(2, 0));
		assertEquals(Tornado.CELESTIAL_LUNAR, Tornado.computeCelestialMode(0, 2));
		assertEquals(Tornado.CELESTIAL_LUNAR, Tornado.computeCelestialMode(1, 2));

		assertEquals(8.5f, Tornado.getTierBaseHardness(5), 0.001f);
		assertEquals(32.0f, Tornado.getTierMinRadius(5), 0.001f);
		assertTrue(TornadoVFX.evalFunnelRadius(0.0, 32.0) >= 15.0);
		assertTrue(Tornado.getTierBaseHardness(5) > Tornado.getTierBaseHardness(3));
		assertTrue(Tornado.getTierMinRadius(5) > Tornado.getTierMinRadius(1));
	}

	@Test
	public void tornadoBallisticEjectionFollowsParabolicGravityArc() {
		double[] pos = new double[3];
		TornadoVFX.evalBallisticPos(0.0, 30.0, 0.0, 10.0, 6.0, -4.0, 0.0, pos);
		assertEquals(0.0, pos[0], 1e-6);
		assertEquals(30.0, pos[1], 1e-6);
		assertEquals(0.0, pos[2], 1e-6);

		// At dt=0.25s it rises slightly; at dt=1.5s gravity pulls it cleanly downward
		TornadoVFX.evalBallisticPos(0.0, 30.0, 0.0, 10.0, 6.0, -4.0, 0.25, pos);
		assertTrue("Block should initially arc upward", pos[1] > 30.5);
		assertEquals(2.5, pos[0], 1e-6);

		TornadoVFX.evalBallisticPos(0.0, 30.0, 0.0, 10.0, 6.0, -4.0, 1.5, pos);
		assertTrue("Gravity should pull ejected block back down", pos[1] < 20.0);
		assertEquals(15.0, pos[0], 1e-6);
		assertEquals(-6.0, pos[2], 1e-6);
	}

	@Test
	public void tornadoVfxGeometryCurvesAreSmoothAndFinite() {
		double rBase = TornadoVFX.evalFunnelRadius(0.0, 10.0);
		double rMid = TornadoVFX.evalFunnelRadius(0.5, 10.0);
		double rTop = TornadoVFX.evalFunnelRadius(1.0, 10.0);
		assertTrue("Funnel should widen from base to top", rBase > 1.0 && rMid > rBase && rTop > rMid);

		double[] xz = new double[2];
		TornadoVFX.evalSpineOffset(0.0, 40.0, 2.5, xz);
		assertEquals(0.0, xz[0], 1e-6);
		assertEquals(0.0, xz[1], 1e-6);

		TornadoVFX.evalSpineOffset(0.5, 40.0, 2.5, xz);
		assertFalse(Double.isNaN(xz[0]) || Double.isInfinite(xz[0]));
		assertFalse(Double.isNaN(xz[1]) || Double.isInfinite(xz[1]));
		assertTrue("Mid-funnel should sway organically", Math.abs(xz[0]) + Math.abs(xz[1]) > 0.1);
	}

	@Test
	public void disposableVsValuableBlockClassificationProtectsValuables() {
		net.minecraft.block.Block dirtLike = new net.minecraft.block.Block(net.minecraft.block.material.Material.ground) {};
		net.minecraft.block.Block plantLike = new net.minecraft.block.Block(net.minecraft.block.material.Material.plants) {};
		net.minecraft.block.Block woodLike = new net.minecraft.block.Block(net.minecraft.block.material.Material.wood) {};
		net.minecraft.block.Block leavesLike = new net.minecraft.block.Block(net.minecraft.block.material.Material.leaves) {};
		net.minecraft.block.Block diamondLike = new net.minecraft.block.Block(net.minecraft.block.material.Material.iron) {};
		net.minecraft.block.Block moddedOreLike = new net.minecraft.block.Block(net.minecraft.block.material.Material.rock) {};
		net.minecraft.block.Block nonCubeMachine = new net.minecraft.block.Block(net.minecraft.block.material.Material.iron) {
			@Override
			public boolean isOpaqueCube() { return false; }
			@Override
			public boolean renderAsNormalBlock() { return false; }
		};

		assertTrue(Tornado.isDisposableTerrainBlock(dirtLike, 0));
		assertTrue(Tornado.isDisposableTerrainBlock(plantLike, 0));
		assertTrue(Tornado.isDisposableTerrainBlock(woodLike, 0));
		assertTrue(Tornado.isDisposableTerrainBlock(leavesLike, 0));

		assertFalse("Metal/gem blocks must never be destroyed as disposable junk", Tornado.isDisposableTerrainBlock(diamondLike, 0));
		assertFalse("Ores/specialty rock must never be destroyed as disposable junk", Tornado.isDisposableTerrainBlock(moddedOreLike, 0));

		assertTrue("Solid metal/gem blocks should be thrown physically", Tornado.isThrowableValuableBlock(diamondLike, 0));
		assertTrue("Solid ores/specialty rock should be thrown physically", Tornado.isThrowableValuableBlock(moddedOreLike, 0));
		assertFalse("Non-cube blocks should not be split/thrown as falling blocks", Tornado.isThrowableValuableBlock(nonCubeMachine, 0));
	}

	@Test
	public void inscriptionTablePreviewHelpersResolveTierAndElement() {
		java.util.List<am2.api.spell.component.interfaces.ISpellPart> f5Fire = java.util.Arrays.<am2.api.spell.component.interfaces.ISpellPart>asList(
				new Tornado(),
				new FireDamage(),
				new am2.spell.modifiers.Solar(),
				new am2.spell.modifiers.Solar(),
				new am2.spell.modifiers.Solar()
		);
		int[] counts = Tornado.inspectTierModifiersFromParts(f5Fire);
		assertEquals(3, counts[0]);
		assertEquals(5, Tornado.computeFujitaTier(counts[0], counts[1], counts[2], counts[3]));
		assertEquals(Tornado.ELEMENT_FIRE, Tornado.detectElementalSynergyFromParts(f5Fire));
		assertEquals("am2.gui.tornadoElement.fire", Tornado.getElementLangKey(Tornado.ELEMENT_FIRE));

		java.util.List<am2.api.spell.component.interfaces.ISpellPart> waterParts = java.util.Arrays.<am2.api.spell.component.interfaces.ISpellPart>asList(
				new Tornado(),
				new CreateWater(),
				new am2.spell.modifiers.Radius()
		);
		int[] wCounts = Tornado.inspectTierModifiersFromParts(waterParts);
		assertEquals(2, Tornado.computeFujitaTier(wCounts[0], wCounts[1], wCounts[2], wCounts[3]));
		assertEquals(Tornado.ELEMENT_WATER, Tornado.detectElementalSynergyFromParts(waterParts));
		assertEquals("am2.gui.tornadoElement.water", Tornado.getElementLangKey(Tornado.ELEMENT_WATER));
	}

	@Test
	public void goldenSpellEndgameUnlockGateEnforcesAllRequirements() {
		assertFalse(am2.spell.SpellUnlockManager.meetsGoldenEndgameRequirements(65, 2, true));
		assertFalse(am2.spell.SpellUnlockManager.meetsGoldenEndgameRequirements(89, 2, true));
		assertFalse(am2.spell.SpellUnlockManager.meetsGoldenEndgameRequirements(90, 0, true));
		assertFalse(am2.spell.SpellUnlockManager.meetsGoldenEndgameRequirements(90, 2, false));
		assertTrue(am2.spell.SpellUnlockManager.meetsGoldenEndgameRequirements(90, 1, true));
		assertTrue(am2.spell.SpellUnlockManager.meetsGoldenEndgameRequirements(99, 5, true));
	}

	@Test
	public void tornadoIconTextureExistsAndIsValidPng() throws Exception {
		InputStream is = TornadoSpellTest.class.getResourceAsStream("/assets/arsmagica2/textures/items/spells/components/Tornado.png");
		assertNotNull("Missing Tornado.png component icon", is);
		BufferedImage img = ImageIO.read(is);
		assertNotNull("Failed to decode Tornado.png", img);
		assertEquals(64, img.getWidth());
		assertEquals(64, img.getHeight());
	}
}
