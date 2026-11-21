package am2.client.vfx;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@SideOnly(Side.CLIENT)
public class OrbitalStormVFX {

	private static final ResourceLocation texLightningShockwave = new ResourceLocation("arsmagica2", "textures/misc/vfx/lightning_shockwave.png");
	private static final ResourceLocation texStarfallCore = new ResourceLocation("arsmagica2", "textures/misc/vfx/starfall_core.png");

	private static final int MAX_BOLTS_ALL = 64;
	private static final int MAX_BOLTS_DECREASED = 36;
	private static final int MAX_BOLTS_MINIMAL = 18;

	private static final int MAX_RINGS_ALL = 44;
	private static final int MAX_RINGS_DECREASED = 24;
	private static final int MAX_RINGS_MINIMAL = 12;

	private static final int MAX_SPARKS_ALL = 180;
	private static final int MAX_SPARKS_DECREASED = 90;
	private static final int MAX_SPARKS_MINIMAL = 36;

	private static final double CULL_DIST_SQ = 190.0 * 190.0;
	private static final double LOD_DETAIL_DIST_SQ = 75.0 * 75.0;
	private static final double SPARK_CULL_DIST_SQ = 110.0 * 110.0;

	private static final Map<Integer, ActiveStorm> activeStorms = new ConcurrentHashMap<Integer, ActiveStorm>();
	private static final List<OrbitalBolt> activeBolts = new ArrayList<OrbitalBolt>();
	private static final List<GroundShockRing> activeRings = new ArrayList<GroundShockRing>();
	private static final List<DebrisSpark> activeSparks = new ArrayList<DebrisSpark>();
	private static final float[] PALETTE = new float[12];

	private static final class ActiveStorm {
		int entityId;
		double centerX, centerY, centerZ;
		float radius;
		int remainingTicks;
		int totalDuration;
		int customColor;
		boolean overcharged;
		long spawnTimeMs;
		long lastSyncTimeMs;
	}

	private static final class OrbitalBolt {
		double startX, startY, startZ;
		double endX, endY, endZ;
		LightningVFXHelper.BoltPath path;
		boolean hitMob;
		boolean overcharged;
		float intensityScale;
		int customColor;
		long spawnTimeMs;
		long lifeMs;
	}

	private static final class GroundShockRing {
		double x, y, z;
		float maxRadius;
		boolean hitMob;
		boolean overcharged;
		int customColor;
		double[][] crackX;
		double[][] crackY;
		double[][] crackZ;
		long spawnTimeMs;
		long lifeMs;
	}

	private static final class DebrisSpark {
		double x, y, z;
		double vx, vy, vz;
		float size;
		float rollSpeed;
		boolean darkDebris;
		int customColor;
		long spawnTimeMs;
		long lifeMs;
	}

	public static synchronized void clearAll() {
		activeStorms.clear();
		activeBolts.clear();
		activeRings.clear();
		activeSparks.clear();
	}

	private static int getQuality() {
		Minecraft mc = Minecraft.getMinecraft();
		return (mc != null && mc.gameSettings != null) ? mc.gameSettings.particleSetting : 0;
	}

	public static synchronized void handleOrbitalStormPacket(
			int stormId,
			double centerX, double centerY, double centerZ,
			float radius, int remainingTicks, int totalDuration,
			int customColor, boolean overcharged,
			double[] strikeX, double[] strikeY, double[] strikeZ, boolean[] strikeHitMob
	) {
		long now = Minecraft.getSystemTime();
		ActiveStorm storm = activeStorms.get(stormId);
		if (storm == null) {
			storm = new ActiveStorm();
			storm.entityId = stormId;
			storm.spawnTimeMs = now;
			activeStorms.put(stormId, storm);
		}
		storm.centerX = centerX;
		storm.centerY = centerY;
		storm.centerZ = centerZ;
		storm.radius = radius;
		storm.remainingTicks = Math.max(remainingTicks, 15);
		storm.totalDuration = Math.max(totalDuration, 40);
		storm.customColor = customColor;
		storm.overcharged = storm.overcharged || overcharged;
		storm.lastSyncTimeMs = now;

		int quality = getQuality();
		int maxBolts = quality >= 2 ? MAX_BOLTS_MINIMAL : (quality == 1 ? MAX_BOLTS_DECREASED : MAX_BOLTS_ALL);
		int maxRings = quality >= 2 ? MAX_RINGS_MINIMAL : (quality == 1 ? MAX_RINGS_DECREASED : MAX_RINGS_ALL);
		int maxSparks = quality >= 2 ? MAX_SPARKS_MINIMAL : (quality == 1 ? MAX_SPARKS_DECREASED : MAX_SPARKS_ALL);

		Minecraft mc = Minecraft.getMinecraft();
		double refX = centerX, refZ = centerZ;
		if (mc != null && mc.thePlayer != null) {
			refX = mc.thePlayer.posX;
			refZ = mc.thePlayer.posZ;
		}

		Random rand = new Random(now ^ (stormId * 31337L));
		int packetSpawned = 0;
		int packetCap = quality >= 2 ? 4 : (quality == 1 ? 8 : 14);

		for (int i = 0; i < strikeX.length && packetSpawned < packetCap; i++) {
			double tx = strikeX[i];
			double ty = strikeY[i];
			double tz = strikeZ[i];
			boolean hitMob = strikeHitMob[i];

			double playerDx = tx - refX;
			double playerDz = tz - refZ;
			double playerDistSq = playerDx * playerDx + playerDz * playerDz;
			if (playerDistSq > CULL_DIST_SQ) {
				continue;
			}

			// Coalesce tightly clustered redundant strikes into a single thicker hero bolt
			// instead of stacking noisy overlapping lines on the same spot.
			OrbitalBolt mergedWith = null;
			for (int b = activeBolts.size() - 1; b >= 0; b--) {
				OrbitalBolt existing = activeBolts.get(b);
				if (now - existing.spawnTimeMs > 110L) break;
				double mdx = existing.endX - tx;
				double mdz = existing.endZ - tz;
				if (mdx * mdx + mdz * mdz < 3.2 * 3.2) {
					mergedWith = existing;
					break;
				}
			}
			if (mergedWith != null) {
				mergedWith.intensityScale = Math.min(1.45f, mergedWith.intensityScale + 0.22f);
				mergedWith.hitMob = mergedWith.hitMob || hitMob;
				continue;
			}

			double dx = tx - centerX;
			double dz = tz - centerZ;
			double dist = Math.sqrt(dx * dx + dz * dz);

			double funnelBaseY = Math.min(centerY + 42.0, 218.0);
			double deckY = Math.min(centerY + 62.0, 238.0);

			double startX, startY, startZ;
			if (dist < 42.0) {
				double ang = rand.nextDouble() * Math.PI * 2.0;
				double r = 3.0 + rand.nextDouble() * 15.0;
				startX = centerX + Math.cos(ang) * r;
				startY = funnelBaseY + rand.nextDouble() * 14.0;
				startZ = centerZ + Math.sin(ang) * r;
			} else {
				double dirX = dx / dist;
				double dirZ = dz / dist;
				double perpX = -dirZ;
				double perpZ = dirX;
				double deckRadius = Math.min(dist * 0.45, 62.0);
				double lat = (rand.nextDouble() - 0.5) * 20.0;
				startX = centerX + dirX * deckRadius + perpX * lat;
				startY = deckY - 6.0 + (rand.nextDouble() - 0.5) * 7.0;
				startZ = centerZ + dirZ * deckRadius + perpZ * lat;
			}

			while (activeBolts.size() >= maxBolts) {
				activeBolts.remove(0);
			}

			double len = Math.sqrt((tx - startX) * (tx - startX) + (ty - startY) * (ty - startY) + (tz - startZ) * (tz - startZ));
			boolean closeHero = hitMob || playerDistSq < LOD_DETAIL_DIST_SQ;
			int segs = closeHero ? (quality == 0 ? 12 : 9) : 7;
			int forks = quality >= 2 ? (hitMob ? 1 : 0) : (closeHero ? (hitMob ? 3 : 2) : 1);
			double maxJitter = Math.min(7.5, 2.0 + len * 0.048);

			OrbitalBolt bolt = new OrbitalBolt();
			bolt.startX = startX;
			bolt.startY = startY;
			bolt.startZ = startZ;
			bolt.endX = tx;
			bolt.endY = ty;
			bolt.endZ = tz;
			bolt.path = LightningVFXHelper.generateBolt(
					startX, startY, startZ,
					tx, ty, tz,
					segs, maxJitter, forks,
					hitMob && quality == 0,
					rand
			);
			bolt.hitMob = hitMob;
			bolt.overcharged = overcharged;
			bolt.intensityScale = hitMob ? 1.15f : 0.92f;
			bolt.customColor = customColor;
			bolt.spawnTimeMs = now;
			bolt.lifeMs = hitMob ? 340L : 270L;
			activeBolts.add(bolt);
			packetSpawned++;

			// Ground shockwave ring + precomputed close-range surface electric arcs
			while (activeRings.size() >= maxRings) {
				activeRings.remove(0);
			}
			GroundShockRing ring = new GroundShockRing();
			ring.x = tx;
			ring.y = ty + 0.08;
			ring.z = tz;
			ring.maxRadius = hitMob ? (overcharged ? 4.4f : 3.4f) : 2.3f;
			ring.hitMob = hitMob;
			ring.overcharged = overcharged;
			ring.customColor = customColor;
			ring.spawnTimeMs = now;
			ring.lifeMs = hitMob ? 480L : 320L;

			int crackCount = (closeHero && quality < 2) ? (hitMob ? (overcharged ? 4 : 3) : 2) : 0;
			if (crackCount > 0) {
				ring.crackX = new double[crackCount][5];
				ring.crackY = new double[crackCount][5];
				ring.crackZ = new double[crackCount][5];
				for (int c = 0; c < crackCount; c++) {
					double ang = (c * Math.PI * 2.0 / crackCount) + rand.nextDouble() * 0.9;
					double crackLen = ring.maxRadius * (0.72 + rand.nextDouble() * 0.35);
					double exC = tx + Math.cos(ang) * crackLen;
					double ezC = tz + Math.sin(ang) * crackLen;
					ring.crackX[c][0] = tx; ring.crackY[c][0] = ring.y + 0.04; ring.crackZ[c][0] = tz;
					ring.crackX[c][4] = exC; ring.crackY[c][4] = ring.y + 0.06; ring.crackZ[c][4] = ezC;
					for (int k = 1; k < 4; k++) {
						double f = k / 4.0;
						double env = Math.sin(f * Math.PI);
						ring.crackX[c][k] = tx + (exC - tx) * f + (rand.nextDouble() - 0.5) * 0.65 * env;
						ring.crackY[c][k] = ring.y + 0.05 + rand.nextDouble() * 0.14 * env;
						ring.crackZ[c][k] = tz + (ezC - tz) * f + (rand.nextDouble() - 0.5) * 0.65 * env;
					}
				}
			}
			activeRings.add(ring);

			// Spawn crisp sparks & dark scorched debris only within visible spark range
			if (playerDistSq < SPARK_CULL_DIST_SQ) {
				int sparkCount = hitMob ? (overcharged ? 7 : 5) : 3;
				if (quality == 1) sparkCount = Math.max(1, sparkCount / 2);
				else if (quality >= 2) sparkCount = hitMob ? 2 : 0;

				for (int s = 0; s < sparkCount; s++) {
					while (activeSparks.size() >= maxSparks) {
						activeSparks.remove(0);
					}
					DebrisSpark sp = new DebrisSpark();
					double sang = rand.nextDouble() * Math.PI * 2.0;
					double hspd = 2.0 + rand.nextDouble() * 4.8;
					sp.x = tx + (rand.nextDouble() - 0.5) * 0.6;
					sp.y = ty + 0.22;
					sp.z = tz + (rand.nextDouble() - 0.5) * 0.6;
					sp.vx = Math.cos(sang) * hspd;
					sp.vy = 4.2 + rand.nextDouble() * 6.0;
					sp.vz = Math.sin(sang) * hspd;
					sp.darkDebris = (s % 3 == 0);
					sp.size = sp.darkDebris ? (0.11f + rand.nextFloat() * 0.10f) : (0.14f + rand.nextFloat() * 0.16f);
					sp.rollSpeed = (rand.nextBoolean() ? 1.0f : -1.0f) * (4.5f + rand.nextFloat() * 4.5f);
					sp.customColor = customColor;
					sp.spawnTimeMs = now;
					sp.lifeMs = 360L + rand.nextInt(260);
					activeSparks.add(sp);
				}
			}
		}
	}

	public static synchronized void renderAll(float partialTicks) {
		if (activeStorms.isEmpty() && activeBolts.isEmpty() && activeRings.isEmpty() && activeSparks.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
			return;
		}

		long now = Minecraft.getSystemTime();
		net.minecraft.entity.EntityLivingBase camera = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
		double camX = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks;
		double camY = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks;
		double camZ = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks;

		// Camera right/up basis for 100% batched billboard quads (zero glPushMatrix/glRotatef)
		double viewYaw = Math.toRadians(RenderManager.instance.playerViewY);
		double viewPitch = Math.toRadians(RenderManager.instance.playerViewX);
		double camRX = Math.cos(viewYaw);
		double camRZ = Math.sin(viewYaw);
		double camUX = -Math.sin(viewYaw) * Math.sin(viewPitch);
		double camUY = Math.cos(viewPitch);
		double camUZ = Math.cos(viewYaw) * Math.sin(viewPitch);

		// Prune expired storms cleanly
		Iterator<Map.Entry<Integer, ActiveStorm>> stormIt = activeStorms.entrySet().iterator();
		while (stormIt.hasNext()) {
			ActiveStorm s = stormIt.next().getValue();
			long maxGrace = Math.max(1500L, (long) s.remainingTicks * 55L + 900L);
			if (now - s.lastSyncTimeMs > maxGrace) {
				stormIt.remove();
			}
		}

		// Prune expired bolts, rings, and sparks once before batched passes
		int nearBoltCount = 0;
		for (Iterator<OrbitalBolt> it = activeBolts.iterator(); it.hasNext(); ) {
			OrbitalBolt b = it.next();
			if (now - b.spawnTimeMs >= b.lifeMs) {
				it.remove();
			} else {
				double dx = b.endX - camX;
				double dz = b.endZ - camZ;
				if (dx * dx + dz * dz < 24.0 * 24.0) {
					nearBoltCount++;
				}
			}
		}
		for (Iterator<GroundShockRing> it = activeRings.iterator(); it.hasNext(); ) {
			GroundShockRing ring = it.next();
			long ageMs = now - ring.spawnTimeMs;
			if (ageMs < 0L || ageMs >= ring.lifeMs) {
				it.remove();
			}
		}
		for (Iterator<DebrisSpark> it = activeSparks.iterator(); it.hasNext(); ) {
			DebrisSpark sp = it.next();
			long ageMs = now - sp.spawnTimeMs;
			if (ageMs < 0L || ageMs >= sp.lifeMs) {
				it.remove();
			}
		}

		// Anti-flashbang comfort factor when multiple bolts hit right beside the player
		float comfortScale = nearBoltCount <= 2 ? 1.0f : Math.max(0.62f, 1.0f - (nearBoltCount - 2) * 0.06f);
		int quality = getQuality();
		Tessellator t = Tessellator.instance;

		float prevBrightX = OpenGlHelper.lastBrightnessX;
		float prevBrightY = OpenGlHelper.lastBrightnessY;
		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_POLYGON_BIT | GL11.GL_TEXTURE_BIT);

		try {
			GL11.glDisable(GL11.GL_LIGHTING);
			RenderHelper.disableStandardItemLighting();
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);
			GL11.glDisable(GL11.GL_FOG);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glDepthMask(false);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glEnable(GL11.GL_BLEND);

			// PASS 1: Heavy Dark Storm Canopy & Funnel
			for (ActiveStorm storm : activeStorms.values()) {
				renderSkyVortex(mc, t, storm, camX, camY, camZ, now);
			}

			// PASS 2: ALL Orbital Lightning Bolts, Tapered Forks, Ground Shockwave Rings & Surface Arcs
			// Single untextured additive GL_TRIANGLES batch (zero GL_LINE_STRIP, zero GL_LINE_SMOOTH!)
			if (!activeBolts.isEmpty() || !activeRings.isEmpty()) {
				GL11.glDisable(GL11.GL_TEXTURE_2D);
				GL11.glShadeModel(GL11.GL_SMOOTH);
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

				t.startDrawing(GL11.GL_TRIANGLES);
				emitBatchedGroundRingsAndArcs(t, camX, camY, camZ, now, quality, comfortScale);
				emitBatchedOrbitalBolts(t, camX, camY, camZ, now, quality, comfortScale);
				t.draw();
			}

			// PASS 3: Soft Ionized Ground Pads on Hero/Mob Impacts (1 batched GL_QUADS call)
			if (!activeRings.isEmpty() && quality < 2) {
				GL11.glEnable(GL11.GL_TEXTURE_2D);
				mc.renderEngine.bindTexture(texLightningShockwave);
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
				GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
				GL11.glPolygonOffset(-2.0f, -2.0f);

				t.startDrawingQuads();
				for (int i = 0; i < activeRings.size(); i++) {
					GroundShockRing ring = activeRings.get(i);
					if (!ring.hitMob) continue;
					double rx = ring.x - camX;
					double ry = ring.y - camY;
					double rz = ring.z - camZ;
					if (rx * rx + rz * rz > LOD_DETAIL_DIST_SQ) continue;

					long ageMs = now - ring.spawnTimeMs;
					float progress = (float) ageMs / (float) ring.lifeMs;
					float alpha = (1.0f - progress) * (1.0f - progress) * 0.45f * comfortScale;
					if (alpha <= 0.01f) continue;

					LightningVFXHelper.resolvePalette(ring.customColor, PALETTE);
					double rad = ring.maxRadius * (0.28 + 0.65 * Math.sqrt(progress));
					double rot = ageMs * 0.0028;
					LightningVFXHelper.emitHorizontalQuadUV(
							t, rx, ry, rz, rad,
							Math.cos(rot), Math.sin(rot),
							PALETTE[0], PALETTE[1], PALETTE[2], alpha
					);
				}
				t.draw();
				GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
			}

			// PASS 4: ALL Impact Flash Blooms + Synchronized Cloud Sheet-Lightning Flashes + Electric Sparks
			// Single batched GL_QUADS call on texStarfallCore (zero glPushMatrix!)
			if (!activeBolts.isEmpty() || !activeSparks.isEmpty()) {
				GL11.glEnable(GL11.GL_TEXTURE_2D);
				mc.renderEngine.bindTexture(texStarfallCore);
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

				t.startDrawingQuads();
				emitBatchedBoltBlooms(t, camX, camY, camZ, camRX, camRZ, camUX, camUY, camUZ, now, comfortScale);
				emitBatchedElectricSparks(t, camX, camY, camZ, camRX, camRZ, camUX, camUY, camUZ, now);
				t.draw();
			}

			// PASS 5: Dark Scorched Debris Fragments (1 batched untextured alpha-blend GL_QUADS call)
			if (!activeSparks.isEmpty()) {
				GL11.glDisable(GL11.GL_TEXTURE_2D);
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

				t.startDrawingQuads();
				emitBatchedDarkDebris(t, camX, camY, camZ, camRX, camRZ, camUX, camUY, camUZ, now);
				t.draw();
			}
		} finally {
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glDepthMask(true);
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBrightX, prevBrightY);
			GL11.glPopAttrib();
			GL11.glPopMatrix();
		}
	}

	private static void renderSkyVortex(Minecraft mc, Tessellator t, ActiveStorm storm, double camX, double camY, double camZ, long now) {
		float elapsed = (now - storm.lastSyncTimeMs) / 1000.0f;
		float time = (now - storm.spawnTimeMs) / 1000.0f;
		float remain = storm.remainingTicks * 0.05f - elapsed;
		float alpha = Math.min(1.0f, time / 0.7f) * Math.min(1.0f, Math.max(0.0f, remain / 1.1f));
		if (elapsed < 0.25f) alpha = Math.min(1.0f, time / 0.7f);
		if (alpha <= 0.01f) return;
		CastingAtmosphereVFX.renderActiveStormCanopy(
				mc, t,
				storm.centerX, Math.min(storm.centerY + 64.0, 240.0), storm.centerZ,
				camX, camY, camZ,
				alpha, time, storm.overcharged
		);
	}

	public static boolean hasActiveStorms() {
		return !activeStorms.isEmpty();
	}

	private static void emitBatchedOrbitalBolts(
			Tessellator t, double camX, double camY, double camZ,
			long now, int quality, float comfortScale
	) {
		for (int i = 0; i < activeBolts.size(); i++) {
			OrbitalBolt bolt = activeBolts.get(i);
			long ageMs = now - bolt.spawnTimeMs;
			if (ageMs < 0L || ageMs >= bolt.lifeMs) continue;

			double edx = bolt.endX - camX;
			double edy = bolt.endY - camY;
			double edz = bolt.endZ - camZ;
			double distSq = edx * edx + edy * edy + edz * edz;
			if (distSq > CULL_DIST_SQ) continue;

			float progress = (float) ageMs / (float) bolt.lifeMs;
			// Rapid top-to-bottom strike descent over first 48ms
			float headFraction = Math.min(1.0f, 0.16f + (ageMs / 48.0f) * 0.84f);
			float flash = LightningVFXHelper.heavyStormFlashCurve(progress) * comfortScale;
			if (flash <= 0.01f) continue;

			LightningVFXHelper.resolvePalette(bolt.customColor, PALETTE);

			// Scale width slightly with distance so far storm bolts stay crisp and readable
			double dist = Math.sqrt(distSq);
			double distScale = Math.min(2.2, Math.max(1.0, dist * 0.032));
			double baseHalfW = (bolt.hitMob ? (bolt.overcharged ? 0.56 : 0.44) : 0.34) * bolt.intensityScale * distScale;

			boolean nearDetail = distSq < LOD_DETAIL_DIST_SQ;
			boolean drawForks = nearDetail && quality < 2;

			LightningVFXHelper.emitHeavyStormBolt(
					t, bolt.path,
					camX, camY, camZ,
					baseHalfW, PALETTE,
					flash, headFraction,
					drawForks, nearDetail
			);
		}
	}

	private static void emitBatchedGroundRingsAndArcs(
			Tessellator t, double camX, double camY, double camZ,
			long now, int quality, float comfortScale
	) {
		for (int i = 0; i < activeRings.size(); i++) {
			GroundShockRing ring = activeRings.get(i);
			long ageMs = now - ring.spawnTimeMs;
			if (ageMs < 0L || ageMs >= ring.lifeMs) continue;

			double rx = ring.x - camX;
			double ry = ring.y - camY;
			double rz = ring.z - camZ;
			double distSq = rx * rx + ry * ry + rz * rz;
			if (distSq > CULL_DIST_SQ) continue;

			float progress = (float) ageMs / (float) ring.lifeMs;
			float inv = 1.0f - progress;
			float alpha = inv * inv * comfortScale;
			if (alpha <= 0.01f) continue;

			LightningVFXHelper.resolvePalette(ring.customColor, PALETTE);
			boolean highDetail = distSq < LOD_DETAIL_DIST_SQ && quality == 0;
			double r = ring.maxRadius * (0.18 + 0.82 * Math.sqrt(progress));

			// Outer ice-blue shockwave ring
			LightningVFXHelper.emitHorizontalShockRing(
					t, rx, ry + 0.03, rz,
					r, ring.hitMob ? 0.42 : 0.28,
					highDetail,
					PALETTE[3], PALETTE[4], PALETTE[5], alpha * 0.72f
			);

			// Sharp inner dry-white energy crest
			if (progress < 0.65f) {
				float crestAlpha = (1.0f - progress / 0.65f) * comfortScale;
				LightningVFXHelper.emitHorizontalShockRing(
						t, rx, ry + 0.05, rz,
						r * 0.92, ring.hitMob ? 0.14 : 0.09,
						highDetail,
						PALETTE[6], PALETTE[7], PALETTE[8], crestAlpha * 0.85f
				);
			}

			// Pre-computed jagged surface electric cracks (close range only)
			if (ring.crackX != null && distSq < LOD_DETAIL_DIST_SQ && progress < 0.72f) {
				float crackHead = Math.min(1.0f, progress * 3.8f);
				float crackAlpha = (1.0f - progress / 0.72f) * comfortScale;
				for (int c = 0; c < ring.crackX.length; c++) {
					LightningVFXHelper.emitPolylineRibbon(
							t, ring.crackX[c], ring.crackY[c], ring.crackZ[c], ring.crackX[c].length,
							camX, camY, camZ,
							0.16, 0.03,
							PALETTE[3], PALETTE[4], PALETTE[5],
							crackAlpha * 0.65f, crackAlpha * 0.10f,
							crackHead
					);
					LightningVFXHelper.emitPolylineRibbon(
							t, ring.crackX[c], ring.crackY[c], ring.crackZ[c], ring.crackX[c].length,
							camX, camY, camZ,
							0.055, 0.015,
							PALETTE[6], PALETTE[7], PALETTE[8],
							crackAlpha * 0.88f, crackAlpha * 0.15f,
							crackHead
					);
				}
			}
		}
	}

	private static void emitBatchedBoltBlooms(
			Tessellator t,
			double camX, double camY, double camZ,
			double camRX, double camRZ, double camUX, double camUY, double camUZ,
			long now, float comfortScale
	) {
		for (int i = 0; i < activeBolts.size(); i++) {
			OrbitalBolt bolt = activeBolts.get(i);
			long ageMs = now - bolt.spawnTimeMs;
			if (ageMs < 0L || ageMs >= bolt.lifeMs) continue;

			float progress = (float) ageMs / (float) bolt.lifeMs;
			if (progress >= 0.55f) continue;

			double ex = bolt.endX - camX;
			double ey = bolt.endY - camY;
			double ez = bolt.endZ - camZ;
			if (ex * ex + ey * ey + ez * ez > CULL_DIST_SQ) continue;

			LightningVFXHelper.resolvePalette(bolt.customColor, PALETTE);
			float fNorm = progress / 0.55f;
			float fAlpha = (1.0f - fNorm) * (1.0f - fNorm) * comfortScale;
			double roll = ageMs * 0.008;
			double cr = Math.cos(roll), sr = Math.sin(roll);

			// 1. Crisp ground impact flash bloom
			double groundRad = (bolt.hitMob ? 2.1 : 1.35) * bolt.intensityScale * (0.75 + 0.45 * fNorm);
			LightningVFXHelper.emitBillboardQuadUV(
					t, ex, ey + 0.55, ez, groundRad,
					camRX, camRZ, camUX, camUY, camUZ,
					cr, sr,
					PALETTE[3], PALETTE[4], PALETTE[5], fAlpha * 0.65f
			);
			LightningVFXHelper.emitBillboardQuadUV(
					t, ex, ey + 0.55, ez, groundRad * 0.48,
					camRX, camRZ, camUX, camUY, camUZ,
					cr, -sr,
					PALETTE[6], PALETTE[7], PALETTE[8], fAlpha * 0.90f
			);

			// 2. Synchronized internal sheet-lightning flash in the cloud throat where the bolt originated
			double sx = bolt.startX - camX;
			double sy = bolt.startY - camY;
			double sz = bolt.startZ - camZ;
			double cloudFlashRad = (bolt.overcharged ? 7.5 : 5.6) * (0.85 + 0.35 * fNorm);
			LightningVFXHelper.emitBillboardQuadUV(
					t, sx, sy, sz, cloudFlashRad,
					camRX, camRZ, camUX, camUY, camUZ,
					1.0, 0.0,
					PALETTE[3], PALETTE[4], PALETTE[5], fAlpha * 0.32f
			);
		}
	}

	private static void emitBatchedElectricSparks(
			Tessellator t,
			double camX, double camY, double camZ,
			double camRX, double camRZ, double camUX, double camUY, double camUZ,
			long now
	) {
		for (int i = 0; i < activeSparks.size(); i++) {
			DebrisSpark sp = activeSparks.get(i);
			if (sp.darkDebris) continue;
			long ageMs = now - sp.spawnTimeMs;
			if (ageMs < 0L || ageMs >= sp.lifeMs) continue;

			float tSec = ageMs * 0.001f;
			double rx = sp.x + sp.vx * tSec - camX;
			double ry = sp.y + sp.vy * tSec - 7.8 * tSec * tSec - camY;
			double rz = sp.z + sp.vz * tSec - camZ;
			if (rx * rx + ry * ry + rz * rz > SPARK_CULL_DIST_SQ) continue;

			float fade = 1.0f - ((float) ageMs / (float) sp.lifeMs);
			LightningVFXHelper.resolvePalette(sp.customColor, PALETTE);
			double roll = tSec * sp.rollSpeed;
			LightningVFXHelper.emitBillboardQuadUV(
					t, rx, ry, rz, sp.size * (0.55f + 0.45f * fade),
					camRX, camRZ, camUX, camUY, camUZ,
					Math.cos(roll), Math.sin(roll),
					PALETTE[6], PALETTE[7], PALETTE[8], fade * 0.90f
			);
		}
	}

	private static void emitBatchedDarkDebris(
			Tessellator t,
			double camX, double camY, double camZ,
			double camRX, double camRZ, double camUX, double camUY, double camUZ,
			long now
	) {
		for (int i = 0; i < activeSparks.size(); i++) {
			DebrisSpark sp = activeSparks.get(i);
			if (!sp.darkDebris) continue;
			long ageMs = now - sp.spawnTimeMs;
			if (ageMs < 0L || ageMs >= sp.lifeMs) continue;

			float tSec = ageMs * 0.001f;
			double rx = sp.x + sp.vx * tSec - camX;
			double ry = sp.y + sp.vy * tSec - 9.2 * tSec * tSec - camY;
			double rz = sp.z + sp.vz * tSec - camZ;
			if (rx * rx + ry * ry + rz * rz > SPARK_CULL_DIST_SQ) continue;

			float fade = 1.0f - ((float) ageMs / (float) sp.lifeMs);
			double roll = tSec * sp.rollSpeed;
			LightningVFXHelper.emitBillboardQuadSolid(
					t, rx, ry, rz, sp.size,
					camRX, camRZ, camUX, camUY, camUZ,
					Math.cos(roll), Math.sin(roll),
					0.06f, 0.075f, 0.11f, fade * 0.85f
			);
		}
	}
}
