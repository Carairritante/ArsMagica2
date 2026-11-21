package am2.client.vfx;

import am2.spell.components.Tornado;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side renderer for the Golden Spell: Tornado / Hurricane.
 *
 * Visual identity keeps the natural brooding storm palette across all F1-F5 tiers
 * (Solar/Lunar scale power and Fujita tier without tinting the tornado yellow/silver),
 * transforming cleanly only on elemental spell synergies or terrain immersion:
 * - Natural Storm (Air / Earth / Default)
 * - Firenado (Fire synergy or over Lava)
 * - Lightning Storm Tornado (Lightning synergy)
 * - Glacial Vortex (Ice synergy)
 * - Waterspout (Water synergy or over Water)
 */
@SideOnly(Side.CLIENT)
public final class TornadoVFX {

	private static final ResourceLocation texStormCloud = new ResourceLocation("arsmagica2", "textures/misc/vfx/storm_cloud.png");

	private static final int MAX_BLOCKS_ALL = 160;
	private static final int MAX_BLOCKS_DECREASED = 84;
	private static final int MAX_BLOCKS_MINIMAL = 36;
	private static final double CULL_DIST_SQ = 320.0 * 320.0;
	private static final double GRAVITY = 22.0;

	private static final Map<Integer, ActiveTornado> activeTornadoes = new ConcurrentHashMap<Integer, ActiveTornado>();

	private static final int FUNNEL_MAX_GRID = 28 * 18;
	private static final double[] F_VX = new double[FUNNEL_MAX_GRID];
	private static final double[] F_VY = new double[FUNNEL_MAX_GRID];
	private static final double[] F_VZ = new double[FUNNEL_MAX_GRID];
	private static final float[] F_VR = new float[FUNNEL_MAX_GRID];
	private static final float[] F_VG = new float[FUNNEL_MAX_GRID];
	private static final float[] F_VB = new float[FUNNEL_MAX_GRID];
	private static final float[] F_VA = new float[FUNNEL_MAX_GRID];

	private static final int WIND_STEPS = 24;
	private static final double[] WIND_X = new double[WIND_STEPS + 1];
	private static final double[] WIND_Y = new double[WIND_STEPS + 1];
	private static final double[] WIND_Z = new double[WIND_STEPS + 1];

	private static final float[] PALETTE = new float[12];

	private TornadoVFX() {}

	private static final class ActiveTornado {
		int entityId;
		double x, y, z;
		double prevX, prevY, prevZ;
		float radius;
		int remainingTicks;
		int totalDuration;
		int customColor;
		byte element;
		int tier;
		byte celestial;
		long spawnTimeMs;
		long lastSyncTimeMs;
		final List<OrbitingBlock> blocks = new ArrayList<OrbitingBlock>();
	}

	public static final class OrbitingBlock {
		public Block block;
		public int meta;
		public float tintR, tintG, tintB;
		public double origX, origY, origZ;
		public double startHeightFrac;
		public double orbitAngle;
		public double angularVel;
		public double radialOffset;
		public double riseSpeed;
		public double rotX, rotY, rotZ;
		public double spinX, spinY, spinZ;
		public float scale;
		public long spawnTimeMs;
		public long lifeMs;

		public boolean ejected;
		public long ejectTimeMs;
		public double ejectX, ejectY, ejectZ;
		public double ejectVx, ejectVy, ejectVz;
		public double groundY;
		public boolean bounced;
		public long bounceTimeMs;
		public double bounceX, bounceY, bounceZ;
		public double bounceVx, bounceVy, bounceVz;
	}

	public static synchronized void clearAll() {
		activeTornadoes.clear();
	}

	public static boolean hasCataclysmicTornado() {
		for (ActiveTornado t : activeTornadoes.values()) {
			if (t.tier >= 4) return true;
		}
		return false;
	}

	private static int getQuality() {
		Minecraft mc = Minecraft.getMinecraft();
		return (mc != null && mc.gameSettings != null) ? mc.gameSettings.particleSetting : 0;
	}

	public static synchronized void handleTornadoPacket(
			int entityId,
			double cx, double cy, double cz,
			float radius, int remainingTicks, int totalDuration, int customColor, byte element,
			int[] blockIds, int[] metas, int[] bx, int[] by, int[] bz, int count
	) {
		handleTornadoPacket(
				entityId, cx, cy, cz, radius, remainingTicks, totalDuration, customColor, element,
				(byte) 1, Tornado.CELESTIAL_NONE,
				blockIds, metas, bx, by, bz, count
		);
	}

	public static synchronized void handleTornadoPacket(
			int entityId,
			double cx, double cy, double cz,
			float radius, int remainingTicks, int totalDuration, int customColor, byte element,
			byte fujitaTier, byte celestialMode,
			int[] blockIds, int[] metas, int[] bx, int[] by, int[] bz, int count
	) {
		long now = Minecraft.getSystemTime();
		ActiveTornado tornado = activeTornadoes.get(entityId);
		if (tornado == null) {
			tornado = new ActiveTornado();
			tornado.entityId = entityId;
			tornado.x = tornado.prevX = cx;
			tornado.y = tornado.prevY = cy;
			tornado.z = tornado.prevZ = cz;
			tornado.spawnTimeMs = now;
			activeTornadoes.put(entityId, tornado);
		} else {
			tornado.prevX = tornado.x;
			tornado.prevY = tornado.y;
			tornado.prevZ = tornado.z;
			tornado.x = cx;
			tornado.y = cy;
			tornado.z = cz;
		}
		tornado.radius = Math.max(4.0f, radius);
		tornado.remainingTicks = Math.max(0, remainingTicks);
		tornado.totalDuration = Math.max(40, totalDuration);
		tornado.customColor = customColor;
		tornado.element = element;
		tornado.tier = Math.max(1, Math.min(5, fujitaTier));
		tornado.celestial = celestialMode;
		tornado.lastSyncTimeMs = now;

		int quality = getQuality();
		int tierCap = quality >= 2 ? MAX_BLOCKS_MINIMAL : (quality == 1 ? MAX_BLOCKS_DECREASED : MAX_BLOCKS_ALL);
		int maxBlocks = Math.min(tierCap, 50 + tornado.tier * 22);
		double estHeight = 26.0 + tornado.tier * 7.5 + tornado.radius * 1.25;
		Random rnd = new Random(now ^ (entityId * 7919L));

		for (int i = 0; i < count; i++) {
			Block blk = Block.getBlockById(blockIds[i]);
			if (blk == null || blk == Blocks.air) continue;

			while (tornado.blocks.size() >= maxBlocks) {
				tornado.blocks.remove(0);
			}

			OrbitingBlock ob = new OrbitingBlock();
			ob.block = blk;
			ob.meta = metas[i] & 0xF;
			int col = 0xFFFFFF;
			try {
				col = blk.getBlockColor();
			} catch (Throwable ignored) {}
			ob.tintR = ((col >> 16) & 0xFF) / 255.0f;
			ob.tintG = ((col >> 8) & 0xFF) / 255.0f;
			ob.tintB = (col & 0xFF) / 255.0f;

			ob.origX = bx[i] + 0.5;
			ob.origY = by[i] + 0.5;
			ob.origZ = bz[i] + 0.5;
			ob.startHeightFrac = Math.max(0.03, Math.min(0.75, (ob.origY - cy) / Math.max(12.0, estHeight)));

			ob.orbitAngle = Math.atan2(ob.origZ - cz, ob.origX - cx);
			ob.angularVel = (3.0 + tornado.tier * 0.25) + rnd.nextDouble() * 3.2;
			ob.radialOffset = 0.6 + rnd.nextDouble() * (tornado.radius * 0.44);
			ob.riseSpeed = 0.13 + rnd.nextDouble() * 0.13;
			ob.rotX = rnd.nextDouble() * Math.PI * 2.0;
			ob.rotY = rnd.nextDouble() * Math.PI * 2.0;
			ob.rotZ = rnd.nextDouble() * Math.PI * 2.0;
			ob.spinX = (rnd.nextDouble() - 0.5) * 6.5;
			ob.spinY = (rnd.nextDouble() - 0.5) * 6.5;
			ob.spinZ = (rnd.nextDouble() - 0.5) * 6.5;
			ob.scale = (0.68f + tornado.tier * 0.04f) + rnd.nextFloat() * 0.30f;
			ob.spawnTimeMs = now;
			ob.lifeMs = 5600L + rnd.nextInt(3200);
			ob.groundY = cy;
			tornado.blocks.add(ob);
		}
	}

	public static void evalSpineOffset(double f, double height, double timeSec, double[] outXZ) {
		double env = Math.sin(f * Math.PI);
		double amp = height * 0.085 * env;
		outXZ[0] = Math.sin(f * Math.PI * 1.35 + timeSec * 1.15) * amp;
		outXZ[1] = Math.cos(f * Math.PI * 1.15 - timeSec * 0.92) * amp;
	}

	public static double evalFunnelRadius(double f, double baseRadius) {
		double rTouch = Math.max(2.6, baseRadius * 0.48);
		double rTop = baseRadius * 1.75;
		return rTouch + (rTop - rTouch) * Math.pow(Math.max(0.0, Math.min(1.0, f)), 1.15);
	}

	public static void evalBallisticPos(
			double ox, double oy, double oz,
			double vx, double vy, double vz,
			double dt, double[] outXYZ
	) {
		outXYZ[0] = ox + vx * dt;
		outXYZ[1] = oy + vy * dt - 0.5 * GRAVITY * dt * dt;
		outXYZ[2] = oz + vz * dt;
	}

	public static synchronized void renderAll(float partialTicks) {
		if (activeTornadoes.isEmpty()) return;
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.theWorld == null || mc.thePlayer == null) return;

		long now = Minecraft.getSystemTime();
		net.minecraft.entity.EntityLivingBase camera = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
		double camX = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks;
		double camY = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks;
		double camZ = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks;

		for (Iterator<Map.Entry<Integer, ActiveTornado>> it = activeTornadoes.entrySet().iterator(); it.hasNext(); ) {
			ActiveTornado t = it.next().getValue();
			long baseGrace = Math.max(1200L, t.remainingTicks * 55L + 900L);
			long maxGrace = t.blocks.isEmpty() ? baseGrace : (baseGrace + 2600L);
			if (now - t.lastSyncTimeMs > maxGrace) {
				it.remove();
			}
		}
		if (activeTornadoes.isEmpty()) return;

		double viewYaw = Math.toRadians(RenderManager.instance.playerViewY);
		double viewPitch = Math.toRadians(RenderManager.instance.playerViewX);
		double camRX = Math.cos(viewYaw);
		double camRZ = Math.sin(viewYaw);
		double camUX = -Math.sin(viewYaw) * Math.sin(viewPitch);
		double camUY = Math.cos(viewPitch);
		double camUZ = Math.cos(viewYaw) * Math.sin(viewPitch);

		int quality = getQuality();
		Tessellator tess = Tessellator.instance;
		float prevBrightX = OpenGlHelper.lastBrightnessX;
		float prevBrightY = OpenGlHelper.lastBrightnessY;

		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_POLYGON_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_LIGHTING_BIT);

		try {
			GL11.glDisable(GL11.GL_LIGHTING);
			RenderHelper.disableStandardItemLighting();
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 220.0f, 220.0f);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glEnable(GL11.GL_BLEND);

			for (ActiveTornado tornado : activeTornadoes.values()) {
				double dx = tornado.x - camX;
				double dz = tornado.z - camZ;
				if (dx * dx + dz * dz > CULL_DIST_SQ) continue;

				float timeSec = (now - tornado.spawnTimeMs) * 0.001f;
				float sinceSync = (now - tornado.lastSyncTimeMs) * 0.001f;
				float remainSec = tornado.remainingTicks * 0.05f - sinceSync;
				float intro = Math.min(1.0f, timeSec / 0.85f);
				float outro = Math.min(1.0f, Math.max(0.0f, remainSec / 1.15f));
				float alpha = intro * outro;
				boolean collapsing = remainSec < 0.70f;

				double syncFrac = Math.min(1.0, sinceSync / 0.15);
				double wx = tornado.prevX + (tornado.x - tornado.prevX) * syncFrac;
				double wy = tornado.prevY + (tornado.y - tornado.prevY) * syncFrac;
				double wz = tornado.prevZ + (tornado.z - tornado.prevZ) * syncFrac;
				double rx = wx - camX;
				double ry = wy - camY;
				double rz = wz - camZ;

				double height = (26.0 + tornado.tier * 7.5 + tornado.radius * 1.25) * (0.45 + 0.55 * intro);
				LightningVFXHelper.resolvePalette(tornado.customColor, PALETTE);

				// ==============================================================
				// PASS 1 & 2: VOLUMETRIC FUNNEL + SATELLITE VORTICES + SUPERCELL
				// ==============================================================
				if (alpha > 0.01f) {
					GL11.glDisable(GL11.GL_TEXTURE_2D);
					GL11.glShadeModel(GL11.GL_SMOOTH);
					GL11.glDepthMask(false);
					GL11.glDisable(GL11.GL_CULL_FACE);
					GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

					tess.startDrawing(GL11.GL_TRIANGLES);
					int slices = quality == 0 ? 22 : (quality == 1 ? 16 : 12);
					int rows = quality == 0 ? 14 : (quality == 1 ? 10 : 8);

					// F4-F5 Cataclysmic Outer Atmospheric Sky Veil under the supercell
					if (tornado.tier >= 4) {
						double veilRad = tornado.tier >= 5 ? (tornado.radius * 3.8) : (tornado.radius * 2.8);
						emitSupercellSkyVeil(tess, rx, ry + height * 0.96, rz, veilRad, alpha, tornado.element);
					}

					// Inner low-pressure core
					emitCurvedFunnelLayer(tess, rx, ry, rz, tornado.radius, height, timeSec, alpha, slices, rows, 0.56, 0.64f, false, tornado.customColor, tornado.element, true);
					// Main swirling storm funnel wall
					emitCurvedFunnelLayer(tess, rx, ry, rz, tornado.radius, height, timeSec, alpha, slices, rows, 1.00, 0.54f, false, tornado.customColor, tornado.element, false);
					// Outer turbulent wind sheath (F3+ gets thicker outer debris wall!)
					if (quality < 2) {
						double outerMult = tornado.tier >= 5 ? 1.42 : (tornado.tier >= 4 ? 1.32 : 1.22);
						float outerAlpha = tornado.tier >= 5 ? 0.34f : 0.24f;
						emitCurvedFunnelLayer(tess, rx, ry, rz, tornado.radius, height, timeSec, alpha, Math.max(12, slices - 4), Math.max(8, rows - 2), outerMult, outerAlpha, true, tornado.customColor, tornado.element, false);
					}

					// Brooding overhead supercell cloud cap (scales up in F4-F5)
					double capMult = 2.05 + tornado.tier * 0.14;
					emitSupercellCap(tess, rx, ry + height, rz, tornado.radius * capMult, tornado.radius * 0.62, timeSec, alpha, quality == 0 ? 18 : 12, quality == 0 ? 7 : 5, tornado.customColor, tornado.element, tornado.tier);
					tess.draw();

					// PASS 2: High-Speed Helical Wind / Elemental Arcs + F4/F5 Satellite Sub-Vortices + Ground Shockwaves
					GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
					tess.startDrawing(GL11.GL_TRIANGLES);
					emitHelicalAndTierVortices(tess, wx, wy, wz, rx, ry, rz, camX, camY, camZ, tornado.radius, height, timeSec, alpha, quality, tornado.customColor, tornado.element, tornado.tier);
					tess.draw();
				}

				// ==============================================================
				// PASS 3: REAL 3D ORBITING & BALLISTIC-FALLING TERRAIN BLOCKS
				// ==============================================================
				if (!tornado.blocks.isEmpty()) {
					GL11.glEnable(GL11.GL_TEXTURE_2D);
					mc.renderEngine.bindTexture(TextureMap.locationBlocksTexture);
					GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
					GL11.glDepthMask(true);
					GL11.glEnable(GL11.GL_CULL_FACE);

					tess.startDrawingQuads();
					emitBatchedOrbitingAndFallingBlocks(tess, tornado, wx, wy, wz, camX, camY, camZ, height, timeSec, now, collapsing);
					tess.draw();

					GL11.glDepthMask(false);
					GL11.glDisable(GL11.GL_CULL_FACE);
				}

				// ==============================================================
				// PASS 4: BASE DEBRIS / DUST / SPRAY / EMBER COLLAR PUFFS
				// ==============================================================
				if (alpha > 0.01f) {
					GL11.glEnable(GL11.GL_TEXTURE_2D);
					mc.renderEngine.bindTexture(texStormCloud);
					boolean glowingCollar = (tornado.element == Tornado.ELEMENT_FIRE);
					GL11.glBlendFunc(GL11.GL_SRC_ALPHA, glowingCollar ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);

					int collarPuffs = (quality == 0 ? 12 : (quality == 1 ? 8 : 5)) + tornado.tier;
					double baseCollarR = Math.max(3.2, tornado.radius * 0.56);
					float cr = 0.18f, cg = 0.20f, cb = 0.24f;
					if (tornado.element == Tornado.ELEMENT_FIRE) {
						cr = 0.92f; cg = 0.34f; cb = 0.06f;
					} else if (tornado.element == Tornado.ELEMENT_ICE) {
						cr = 0.62f; cg = 0.85f; cb = 0.96f;
					} else if (tornado.element == Tornado.ELEMENT_WATER) {
						cr = 0.45f; cg = 0.82f; cb = 0.96f;
					} else if (tornado.element == Tornado.ELEMENT_LIGHTNING) {
						cr = 0.14f; cg = 0.20f; cb = 0.32f;
					}

					tess.startDrawingQuads();
					for (int p = 0; p < collarPuffs; p++) {
						double ang = p * (Math.PI * 2.0 / collarPuffs) + timeSec * (2.6 + (p % 3) * 0.4);
						double dist = baseCollarR * (0.70 + 0.48 * ((p % 3) * 0.5));
						double px = rx + Math.cos(ang) * dist;
						double py = ry + 0.9 + (p % 3) * 1.1;
						double pz = rz + Math.sin(ang) * dist;
						double pRad = 2.2 + tornado.radius * 0.17 + (p % 2) * 0.8;
						double roll = timeSec * (p % 2 == 0 ? 2.2 : -2.2) + p;
						float shade = 0.78f + (p % 3) * 0.08f;

						LightningVFXHelper.emitBillboardQuadUV(
								tess, px, py, pz, pRad,
								camRX, camRZ, camUX, camUY, camUZ,
								Math.cos(roll), Math.sin(roll),
								cr * shade, cg * shade, cb * shade,
								alpha * (glowingCollar ? 0.38f : 0.60f)
						);
					}
					tess.draw();
				}
			}
		} finally {
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glDepthMask(true);
			GL11.glEnable(GL11.GL_CULL_FACE);
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBrightX, prevBrightY);
			GL11.glPopAttrib();
			GL11.glPopMatrix();
		}
	}

	private static void emitSupercellSkyVeil(Tessellator t, double rx, double ry, double rz, double rad, float alpha, byte element) {
		float vr = 0.015f, vg = 0.020f, vb = 0.036f;
		if (element == Tornado.ELEMENT_FIRE) {
			vr = 0.055f; vg = 0.018f; vb = 0.010f;
		} else if (element == Tornado.ELEMENT_WATER) {
			vr = 0.012f; vg = 0.032f; vb = 0.058f;
		}
		int segs = 24;
		for (int i = 0; i < segs; i++) {
			double a = i * (Math.PI * 2.0 / segs);
			double b = (i + 1) * (Math.PI * 2.0 / segs);
			t.setColorRGBA_F(vr, vg, vb, alpha * 0.68f);
			t.addVertex(rx, ry, rz);
			t.setColorRGBA_F(vr, vg, vb, 0.0f);
			t.addVertex(rx + Math.cos(a) * rad, ry, rz + Math.sin(a) * rad);
			t.addVertex(rx + Math.cos(b) * rad, ry, rz + Math.sin(b) * rad);
		}
	}

	private static void emitCurvedFunnelLayer(
			Tessellator t,
			double rx, double ry, double rz,
			double baseRadius, double height,
			float timeSec, float alpha,
			int slices, int rows,
			double radiusMult, float layerAlpha,
			boolean vapourSheath, int customColor, byte element, boolean innerCore
	) {
		int stride = slices + 1;
		double[] spine = new double[2];

		// Natural moody storm funnel by default; shifts only on elemental spell combo or Water/Lava terrain
		float tintR = innerCore ? 0.16f : 0.24f;
		float tintG = innerCore ? 0.19f : 0.27f;
		float tintB = innerCore ? 0.24f : 0.32f;

		if (element == Tornado.ELEMENT_FIRE) {
			tintR = innerCore ? 0.95f : 0.55f;
			tintG = innerCore ? 0.44f : 0.18f;
			tintB = innerCore ? 0.08f : 0.06f;
		} else if (element == Tornado.ELEMENT_LIGHTNING) {
			tintR = innerCore ? 0.14f : 0.12f;
			tintG = innerCore ? 0.26f : 0.18f;
			tintB = innerCore ? 0.48f : 0.30f;
		} else if (element == Tornado.ELEMENT_ICE) {
			tintR = innerCore ? 0.58f : 0.44f;
			tintG = innerCore ? 0.86f : 0.72f;
			tintB = innerCore ? 0.98f : 0.88f;
		} else if (element == Tornado.ELEMENT_WATER) {
			tintR = innerCore ? 0.12f : 0.14f;
			tintG = innerCore ? 0.48f : 0.42f;
			tintB = innerCore ? 0.82f : 0.72f;
		} else if (customColor != -1) {
			tintR = 0.14f + PALETTE[3] * 0.22f;
			tintG = 0.14f + PALETTE[4] * 0.22f;
			tintB = 0.16f + PALETTE[5] * 0.24f;
		}

		for (int row = 0; row <= rows; row++) {
			double f = row / (double) rows;
			evalSpineOffset(f, height, timeSec, spine);
			double rBase = evalFunnelRadius(f, baseRadius) * radiusMult;
			double y = ry + f * height;
			double cx = rx + spine[0];
			double cz = rz + spine[1];
			double verticalFade = Math.sin(Math.min(1.0, f * 1.15) * Math.PI * 0.85) * (0.65 + 0.35 * (1.0 - f));
			if (row == 0) verticalFade = 0.58;
			if (row == rows) verticalFade = 0.08;

			int rowBase = row * stride;
			for (int i = 0; i <= slices; i++) {
				double ang = i * (Math.PI * 2.0 / slices);
				double ca = Math.cos(ang), sa = Math.sin(ang);
				double swirlWave = Math.sin(ang * 4.0 + f * 14.0 - timeSec * 7.5);
				double ripple = 1.0 + 0.075 * swirlWave + 0.03 * Math.cos(ang * 7.0 - f * 9.0 + timeSec * 5.2);
				double r = rBase * ripple;

				double shade = 0.45 + 0.55 * Math.max(0.0, ca * 0.45 + sa * 0.55 + 0.35);
				double dustMix = (element == Tornado.ELEMENT_AIR) ? Math.max(0.0, 1.0 - f * 3.0) * 0.38 : 0.0;
				float cr = (float) Math.min(1.0, (tintR * (1.0 - dustMix) + 0.28 * dustMix) * (0.65 + 0.55 * shade));
				float cg = (float) Math.min(1.0, (tintG * (1.0 - dustMix) + 0.26 * dustMix) * (0.65 + 0.55 * shade));
				float cb = (float) Math.min(1.0, (tintB * (1.0 - dustMix) + 0.22 * dustMix) * (0.65 + 0.55 * shade));

				float a;
				if (vapourSheath) {
					double band = Math.max(0.0, swirlWave * 0.5 + 0.5);
					a = (float) (alpha * layerAlpha * verticalFade * band * band);
				} else {
					a = (float) (alpha * layerAlpha * verticalFade);
				}

				int idx = rowBase + i;
				F_VX[idx] = cx + ca * r;
				F_VY[idx] = y;
				F_VZ[idx] = cz + sa * r;
				F_VR[idx] = cr;
				F_VG[idx] = cg;
				F_VB[idx] = cb;
				F_VA[idx] = a;
			}
		}
		emitGridTriangles(t, rows, slices, stride);
	}

	private static void emitSupercellCap(
			Tessellator t,
			double rx, double ry, double rz,
			double radXZ, double radY,
			float timeSec, float alpha,
			int slices, int rows, int customColor, byte element, int tier
	) {
		int stride = slices + 1;
		float baseR = 0.075f, baseG = 0.090f, baseB = 0.125f;
		if (element == Tornado.ELEMENT_FIRE) {
			baseR = 0.16f; baseG = 0.075f; baseB = 0.055f;
		} else if (element == Tornado.ELEMENT_ICE) {
			baseR = 0.16f; baseG = 0.24f; baseB = 0.32f;
		} else if (element == Tornado.ELEMENT_WATER) {
			baseR = 0.065f; baseG = 0.14f; baseB = 0.22f;
		} else if (customColor != -1) {
			baseR = PALETTE[9]; baseG = PALETTE[10]; baseB = PALETTE[11];
		}

		for (int j = 0; j <= rows; j++) {
			double p = -Math.PI * 0.45 + j * (Math.PI * 0.90 / rows);
			double cp = Math.cos(p), sp = Math.sin(p);
			int rowBase = j * stride;
			for (int i = 0; i <= slices; i++) {
				double a = i * (Math.PI * 2.0 / slices) + timeSec * 0.45;
				double ca = Math.cos(a), sa = Math.sin(a);
				double nx = cp * ca, ny = sp, nz = cp * sa;
				double billow = 1.0 + 0.06 * Math.sin(a * 5.0 + p * 4.0) + 0.03 * Math.cos(a * 8.0 - timeSec * 1.2);
				double shade = 0.28 + 0.72 * Math.max(0.0, -nx * 0.35 + ny * 0.65 + nz * 0.55);

				float extraR = 0.0f, extraG = 0.0f, extraB = 0.0f;
				if (element == Tornado.ELEMENT_FIRE && ny < 0.0) {
					float under = (float) (-ny * 0.28);
					extraR = under; extraG = under * 0.35f;
				} else if (element == Tornado.ELEMENT_WATER && ny < 0.0) {
					float under = (float) (-ny * 0.18);
					extraR = under * 0.25f; extraG = under * 0.70f; extraB = under;
				}

				if (element == Tornado.ELEMENT_LIGHTNING || tier >= 4) {
					double sFlash = Math.max(0.0, Math.sin(timeSec * (tier >= 5 ? 5.8 : 4.2) + a * 2.1));
					float f = (float) (Math.pow(sFlash, 16.0) * (tier >= 5 ? 0.48 : 0.34));
					extraR += f * 0.55f; extraG += f * 0.80f; extraB += f;
				}

				int idx = rowBase + i;
				F_VX[idx] = rx + nx * radXZ * billow;
				F_VY[idx] = ry + ny * radY * billow;
				F_VZ[idx] = rz + nz * radXZ * billow;
				F_VR[idx] = Math.min(1.0f, (float) (baseR * (0.6 + 0.9 * shade)) + extraR);
				F_VG[idx] = Math.min(1.0f, (float) (baseG * (0.6 + 0.9 * shade)) + extraG);
				F_VB[idx] = Math.min(1.0f, (float) (baseB * (0.6 + 0.9 * shade)) + extraB);
				F_VA[idx] = alpha * 0.88f;
			}
		}
		emitGridTriangles(t, rows, slices, stride);
	}

	private static void emitGridTriangles(Tessellator t, int rows, int slices, int stride) {
		for (int r = 0; r < rows; r++) {
			int r0 = r * stride;
			int r1 = (r + 1) * stride;
			for (int i = 0; i < slices; i++) {
				int a = r0 + i, b = r1 + i, c = r1 + i + 1, d = r0 + i + 1;
				t.setColorRGBA_F(F_VR[a], F_VG[a], F_VB[a], F_VA[a]); t.addVertex(F_VX[a], F_VY[a], F_VZ[a]);
				t.setColorRGBA_F(F_VR[b], F_VG[b], F_VB[b], F_VA[b]); t.addVertex(F_VX[b], F_VY[b], F_VZ[b]);
				t.setColorRGBA_F(F_VR[c], F_VG[c], F_VB[c], F_VA[c]); t.addVertex(F_VX[c], F_VY[c], F_VZ[c]);
				t.setColorRGBA_F(F_VR[a], F_VG[a], F_VB[a], F_VA[a]); t.addVertex(F_VX[a], F_VY[a], F_VZ[a]);
				t.setColorRGBA_F(F_VR[c], F_VG[c], F_VB[c], F_VA[c]); t.addVertex(F_VX[c], F_VY[c], F_VZ[c]);
				t.setColorRGBA_F(F_VR[d], F_VG[d], F_VB[d], F_VA[d]); t.addVertex(F_VX[d], F_VY[d], F_VZ[d]);
			}
		}
	}

	private static void emitHelicalAndTierVortices(
			Tessellator t,
			double wx, double wy, double wz,
			double rx, double ry, double rz,
			double camX, double camY, double camZ,
			double baseRadius, double height,
			float timeSec, float alpha, int quality, int customColor,
			byte element, int tier
	) {
		int baseArms = tier >= 5 ? 6 : (tier >= 3 ? 5 : 3);
		int arms = quality >= 2 ? 2 : (quality == 1 ? Math.max(3, baseArms - 1) : baseArms);
		double[] spine = new double[2];

		// Natural storm wind streaks by default; only element/terrain synergies tint them
		float wr = 0.68f, wg = 0.78f, wb = 0.88f;
		float cr = 0.88f, cg = 0.94f, cb = 0.98f;
		float baseRibbonAlpha = 0.24f + tier * 0.025f;
		float coreRibbonAlpha = 0.34f + tier * 0.03f;

		if (element == Tornado.ELEMENT_FIRE) {
			wr = 1.00f; wg = 0.36f; wb = 0.05f;
			cr = 1.00f; cg = 0.88f; cb = 0.32f;
			baseRibbonAlpha = 0.56f; coreRibbonAlpha = 0.82f;
		} else if (element == Tornado.ELEMENT_LIGHTNING) {
			wr = 0.38f; wg = 0.76f; wb = 1.00f;
			cr = 0.95f; cg = 0.99f; cb = 1.00f;
			baseRibbonAlpha = 0.40f; coreRibbonAlpha = 0.60f;
		} else if (element == Tornado.ELEMENT_ICE) {
			wr = 0.55f; wg = 0.90f; wb = 1.00f;
			cr = 0.94f; cg = 0.99f; cb = 1.00f;
			baseRibbonAlpha = 0.44f; coreRibbonAlpha = 0.64f;
		} else if (element == Tornado.ELEMENT_WATER) {
			wr = 0.22f; wg = 0.72f; wb = 1.00f;
			cr = 0.84f; cg = 0.96f; cb = 1.00f;
			baseRibbonAlpha = 0.46f; coreRibbonAlpha = 0.68f;
		} else if (customColor != -1) {
			wr = PALETTE[3]; wg = PALETTE[4]; wb = PALETTE[5];
			cr = PALETTE[6]; cg = PALETTE[7]; cb = PALETTE[8];
		}

		// 1. Main Funnel Helical Wind / Elemental Streams
		for (int arm = 0; arm < arms; arm++) {
			double phase = arm * (Math.PI * 2.0 / arms) - timeSec * (5.0 + tier * 0.35 + (arm % 2) * 1.1);
			double radMult = 1.08 + (arm % 3) * 0.14;
			for (int step = 0; step <= WIND_STEPS; step++) {
				double f = step / (double) WIND_STEPS;
				evalSpineOffset(f, height, timeSec, spine);
				double ang = phase + f * Math.PI * 5.5;
				double rad = evalFunnelRadius(f, baseRadius) * radMult;
				WIND_X[step] = wx + spine[0] + Math.cos(ang) * rad;
				WIND_Y[step] = wy + 0.25 + f * height * 0.96;
				WIND_Z[step] = wz + spine[1] + Math.sin(ang) * rad;
			}
			double wScale = 0.85 + tier * 0.10;
			LightningVFXHelper.emitPolylineRibbon(
					t, WIND_X, WIND_Y, WIND_Z, WIND_STEPS + 1,
					camX, camY, camZ,
					(element == Tornado.ELEMENT_FIRE ? 0.56 : 0.36) * wScale,
					(element == Tornado.ELEMENT_FIRE ? 1.15 : 0.78) * wScale,
					wr, wg, wb,
					alpha * baseRibbonAlpha, alpha * 0.08f, 1.0f
			);
			LightningVFXHelper.emitPolylineRibbon(
					t, WIND_X, WIND_Y, WIND_Z, WIND_STEPS + 1,
					camX, camY, camZ,
					(element == Tornado.ELEMENT_FIRE ? 0.18 : 0.10) * wScale,
					(element == Tornado.ELEMENT_FIRE ? 0.34 : 0.22) * wScale,
					cr, cg, cb,
					alpha * coreRibbonAlpha, alpha * 0.10f, 1.0f
			);
		}

		// 2. Tier F3-F5 Ground Suction & Shockwave Rings
		if (tier >= 3) {
			float pulse = (timeSec * 0.9f) % 1.0f;
			double ringR = baseRadius * (0.45 + 1.15 * pulse);
			float ringAlpha = (1.0f - pulse) * (1.0f - pulse) * alpha * (tier >= 5 ? 0.80f : 0.52f);
			LightningVFXHelper.emitHorizontalShockRing(
					t, rx, ry + 0.06, rz,
					ringR, tier >= 5 ? 0.65 : 0.42, quality == 0,
					wr, wg, wb, ringAlpha
			);
			if (tier >= 5) {
				float pulse2 = ((timeSec * 0.9f) + 0.5f) % 1.0f;
				double ringR2 = baseRadius * (0.45 + 1.15 * pulse2);
				float ringAlpha2 = (1.0f - pulse2) * (1.0f - pulse2) * alpha * 0.70f;
				LightningVFXHelper.emitHorizontalShockRing(
						t, rx, ry + 0.08, rz,
						ringR2, 0.32, quality == 0,
						cr, cg, cb, ringAlpha2
				);
			}
		}

		// 3. Tier F4-F5 Multi-Vortex Orbiting Satellite Sub-Vortices (2 in F4, 3 in F5)
		if (tier >= 4 && quality < 2) {
			int satCount = tier >= 5 ? 3 : 2;
			double satHeight = height * 0.86;
			for (int s = 0; s < satCount; s++) {
				double satOrbitAng = s * (Math.PI * 2.0 / satCount) + timeSec * 1.85;
				double satOrbitDist = baseRadius * (0.95 + (s % 2) * 0.22);
				for (int step = 0; step <= WIND_STEPS; step++) {
					double f = step / (double) WIND_STEPS;
					evalSpineOffset(f * 0.86, height, timeSec, spine);
					double orbitX = wx + spine[0] + Math.cos(satOrbitAng + f * 1.2) * (satOrbitDist * (0.65 + 0.55 * f));
					double orbitZ = wz + spine[1] + Math.sin(satOrbitAng + f * 1.2) * (satOrbitDist * (0.65 + 0.55 * f));
					double subSpinAng = -timeSec * 9.5 + f * Math.PI * 7.0 + s * 2.1;
					double subRad = (0.45 + f * 1.35) * (tier >= 5 ? 1.25 : 1.0);

					WIND_X[step] = orbitX + Math.cos(subSpinAng) * subRad;
					WIND_Y[step] = wy + 0.15 + f * satHeight;
					WIND_Z[step] = orbitZ + Math.sin(subSpinAng) * subRad;
				}
				LightningVFXHelper.emitPolylineRibbon(
						t, WIND_X, WIND_Y, WIND_Z, WIND_STEPS + 1,
						camX, camY, camZ,
						0.32, 0.62,
						wr, wg, wb,
						alpha * 0.48f, alpha * 0.12f, 1.0f
				);
				LightningVFXHelper.emitPolylineRibbon(
						t, WIND_X, WIND_Y, WIND_Z, WIND_STEPS + 1,
						camX, camY, camZ,
						0.11, 0.20,
						cr, cg, cb,
						alpha * 0.72f, alpha * 0.18f, 1.0f
				);
			}
		}

		// 4. Lightning Tornado OR Tier F5 Storm Arcs (natural storm cyan-white arcs)
		if (element == Tornado.ELEMENT_LIGHTNING || tier >= 5) {
			int boltArms = quality >= 2 ? 1 : (tier >= 5 && element == Tornado.ELEMENT_LIGHTNING ? 3 : 2);
			int snapSeed = (int) (timeSec * 8.0f);
			float bR = 0.38f, bG = 0.76f, bB = 1.00f;
			for (int b = 0; b < boltArms; b++) {
				double bPhase = b * (Math.PI * 2.0 / boltArms) + snapSeed * 1.37;
				for (int step = 0; step <= WIND_STEPS; step++) {
					double f = step / (double) WIND_STEPS;
					evalSpineOffset(f, height, timeSec, spine);
					double env = Math.sin(f * Math.PI);
					double kinkAng = Math.sin(step * 4.3 + snapSeed * 3.1 + b * 7.0) * 0.36 * env;
					double kinkR = Math.cos(step * 5.7 - snapSeed * 2.7 + b * 5.0) * 1.15 * env;
					double ang = bPhase + f * Math.PI * 3.2 + kinkAng;
					double rad = evalFunnelRadius(f, baseRadius) * 1.18 + kinkR;
					WIND_X[step] = wx + spine[0] + Math.cos(ang) * rad;
					WIND_Y[step] = wy + 0.15 + f * height * 0.98;
					WIND_Z[step] = wz + spine[1] + Math.sin(ang) * rad;
				}
				float pulse = alpha * (0.65f + 0.35f * (float) Math.sin(timeSec * 22.0 + b * 2.1));
				LightningVFXHelper.emitPolylineRibbon(
						t, WIND_X, WIND_Y, WIND_Z, WIND_STEPS + 1,
						camX, camY, camZ,
						0.48, 0.42,
						bR, bG, bB,
						pulse * 0.78f, pulse * 0.65f, 1.0f
				);
				LightningVFXHelper.emitPolylineRibbon(
						t, WIND_X, WIND_Y, WIND_Z, WIND_STEPS + 1,
						camX, camY, camZ,
						0.14, 0.11,
						0.95f, 0.99f, 1.00f,
						pulse * 0.98f, pulse * 0.88f, 1.0f
				);
			}
		}
	}

	private static void emitBatchedOrbitingAndFallingBlocks(
			Tessellator t, ActiveTornado tornado,
			double wx, double wy, double wz,
			double camX, double camY, double camZ,
			double height, float timeSec, long now, boolean collapsing
	) {
		double[] spine = new double[2];
		double[] pos = new double[3];

		for (Iterator<OrbitingBlock> it = tornado.blocks.iterator(); it.hasNext(); ) {
			OrbitingBlock ob = it.next();
			long ageMs = now - ob.spawnTimeMs;
			if (ageMs < 0L) {
				it.remove();
				continue;
			}

			float ageSec = ageMs * 0.001f;
			double bx, by, bz;
			double half;

			if (!ob.ejected) {
				double liftRaw = Math.min(1.0, ageSec / 0.55);
				double liftBlend = liftRaw * liftRaw * (3.0 - 2.0 * liftRaw);
				double heightFrac = ob.startHeightFrac + ageSec * ob.riseSpeed;
				evalSpineOffset(Math.min(1.0, heightFrac), height, timeSec, spine);

				double angle = ob.orbitAngle + ageSec * ob.angularVel;
				double orbitR = evalFunnelRadius(Math.min(1.0, heightFrac), tornado.radius) + ob.radialOffset;
				double targetX = wx + spine[0] + Math.cos(angle) * orbitR;
				double targetY = wy + Math.min(1.0, heightFrac) * height;
				double targetZ = wz + spine[1] + Math.sin(angle) * orbitR;
				double popY = Math.sin(liftRaw * Math.PI) * 1.5;

				double curWorldX = ob.origX + (targetX - ob.origX) * liftBlend;
				double curWorldY = ob.origY + (targetY - ob.origY) * liftBlend + popY;
				double curWorldZ = ob.origZ + (targetZ - ob.origZ) * liftBlend;

				if (collapsing || heightFrac >= 0.88 || ageMs >= ob.lifeMs - 1400L) {
					ob.ejected = true;
					ob.ejectTimeMs = now;
					ob.ejectX = curWorldX;
					ob.ejectY = curWorldY;
					ob.ejectZ = curWorldZ;
					ob.groundY = wy;

					double cosA = Math.cos(angle), sinA = Math.sin(angle);
					double tangSpeed = Math.min(19.0, Math.max(7.5, orbitR * ob.angularVel * 0.36));
					double outward = 5.5 + tornado.radius * 0.36;
					ob.ejectVx = -sinA * tangSpeed + cosA * outward;
					ob.ejectVz = cosA * tangSpeed + sinA * outward;
					ob.ejectVy = 3.5 + (heightFrac >= 0.88 ? 4.5 : 2.0);
				}

				bx = curWorldX - camX;
				by = curWorldY - camY;
				bz = curWorldZ - camZ;
				half = 0.48 * ob.scale;
			} else if (!ob.bounced) {
				double dt = (now - ob.ejectTimeMs) * 0.001;
				evalBallisticPos(ob.ejectX, ob.ejectY, ob.ejectZ, ob.ejectVx, ob.ejectVy, ob.ejectVz, dt, pos);
				if (pos[1] <= ob.groundY + 0.42 || dt > 3.2) {
					ob.bounced = true;
					ob.bounceTimeMs = now;
					ob.bounceX = pos[0];
					ob.bounceY = Math.max(ob.groundY + 0.42, pos[1]);
					ob.bounceZ = pos[2];
					ob.bounceVx = ob.ejectVx * 0.36;
					ob.bounceVz = ob.ejectVz * 0.36;
					ob.bounceVy = 4.2;
				}
				bx = pos[0] - camX;
				by = pos[1] - camY;
				bz = pos[2] - camZ;
				half = 0.48 * ob.scale;
			} else {
				double bdt = (now - ob.bounceTimeMs) * 0.001;
				if (bdt >= 0.48) {
					it.remove();
					continue;
				}
				evalBallisticPos(ob.bounceX, ob.bounceY, ob.bounceZ, ob.bounceVx, ob.bounceVy, ob.bounceVz, bdt, pos);
				if (pos[1] < ob.groundY + 0.25) pos[1] = ob.groundY + 0.25;
				float shrink = (float) (1.0 - bdt / 0.48);
				bx = pos[0] - camX;
				by = pos[1] - camY;
				bz = pos[2] - camZ;
				half = 0.48 * ob.scale * shrink;
			}

			if (half <= 0.03) continue;

			emitRotatedBlockCube(
					t, ob.block, ob.meta,
					bx, by, bz, half,
					ob.rotX + ageSec * ob.spinX,
					ob.rotY + ageSec * ob.spinY,
					ob.rotZ + ageSec * ob.spinZ,
					ob.tintR, ob.tintG, ob.tintB
			);
		}
	}

	private static void emitRotatedBlockCube(
			Tessellator t, Block block, int meta,
			double cx, double cy, double cz, double h,
			double rx, double ry, double rz,
			float tintR, float tintG, float tintB
	) {
		double cxr = Math.cos(rx), sxr = Math.sin(rx);
		double cyr = Math.cos(ry), syr = Math.sin(ry);
		double czr = Math.cos(rz), szr = Math.sin(rz);

		double ux = (cyr * czr) * h;
		double uy = (cxr * szr + sxr * syr * czr) * h;
		double uz = (sxr * szr - cxr * syr * czr) * h;

		double vx = (-cyr * szr) * h;
		double vy = (cxr * czr - sxr * syr * szr) * h;
		double vz = (sxr * czr + cxr * syr * szr) * h;

		double wx = (syr) * h;
		double wy = (-sxr * cyr) * h;
		double wz = (cxr * cyr) * h;

		emitBlockFace(t, safeIcon(block, 1, meta), cx, cy, cz,  vx, vy, vz,  ux, uy, uz, -wx, -wy, -wz, tintR, tintG, tintB, 1.00f);
		emitBlockFace(t, safeIcon(block, 0, meta), cx, cy, cz, -vx,-vy,-vz,  ux, uy, uz,  wx,  wy,  wz, 1.0f, 1.0f, 1.0f, 0.55f);
		emitBlockFace(t, safeIcon(block, 2, meta), cx, cy, cz, -wx,-wy,-wz, -ux,-uy,-uz,  vx,  vy,  vz, 1.0f, 1.0f, 1.0f, 0.80f);
		emitBlockFace(t, safeIcon(block, 3, meta), cx, cy, cz,  wx, wy, wz,  ux, uy, uz,  vx,  vy,  vz, 1.0f, 1.0f, 1.0f, 0.80f);
		emitBlockFace(t, safeIcon(block, 4, meta), cx, cy, cz, -ux,-uy,-uz,  wx, wy, wz,  vx,  vy,  vz, 1.0f, 1.0f, 1.0f, 0.70f);
		emitBlockFace(t, safeIcon(block, 5, meta), cx, cy, cz,  ux, uy, uz, -wx,-wy,-wz,  vx,  vy,  vz, 1.0f, 1.0f, 1.0f, 0.70f);
	}

	private static IIcon safeIcon(Block block, int side, int meta) {
		try {
			IIcon icon = block.getIcon(side, meta);
			return icon != null ? icon : Blocks.stone.getIcon(side, 0);
		} catch (Throwable ignored) {
			return Blocks.stone.getIcon(side, 0);
		}
	}

	private static void emitBlockFace(
			Tessellator t, IIcon icon,
			double cx, double cy, double cz,
			double nx, double ny, double nz,
			double tx, double ty, double tz,
			double bx, double by, double bz,
			float r, float g, float b, float shade
	) {
		if (icon == null) return;
		double u0 = icon.getMinU(), u1 = icon.getMaxU();
		double v0 = icon.getMinV(), v1 = icon.getMaxV();
		double fx = cx + nx, fy = cy + ny, fz = cz + nz;

		t.setColorRGBA_F(r * shade, g * shade, b * shade, 1.0f);
		t.addVertexWithUV(fx - tx - bx, fy - ty - by, fz - tz - bz, u0, v1);
		t.addVertexWithUV(fx + tx - bx, fy + ty - by, fz + tz - bz, u1, v1);
		t.addVertexWithUV(fx + tx + bx, fy + ty + by, fz + tz + bz, u1, v0);
		t.addVertexWithUV(fx - tx + bx, fy - ty + by, fz - tz + bz, u0, v0);
	}
}
