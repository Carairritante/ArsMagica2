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

	private static final ResourceLocation texStormCloud = new ResourceLocation("arsmagica2", "textures/misc/vfx/storm_cloud.png");
	private static final ResourceLocation texLightningBolt = new ResourceLocation("arsmagica2", "textures/misc/vfx/lightning_bolt.png");
	private static final ResourceLocation texLightningShockwave = new ResourceLocation("arsmagica2", "textures/misc/vfx/lightning_shockwave.png");
	private static final ResourceLocation texStarfallCore = new ResourceLocation("arsmagica2", "textures/misc/vfx/starfall_core.png");

	private static final int MAX_ACTIVE_BOLTS = 240;
	private static final int MAX_ACTIVE_RINGS = 180;
	private static final int MAX_ACTIVE_SPARKS = 650;

	private static final Map<Integer, ActiveStorm> activeStorms = new ConcurrentHashMap<Integer, ActiveStorm>();
	private static final List<OrbitalBolt> activeBolts = new ArrayList<OrbitalBolt>();
	private static final List<GroundShockRing> activeRings = new ArrayList<GroundShockRing>();
	private static final List<DebrisSpark> activeSparks = new ArrayList<DebrisSpark>();

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
		boolean hitMob;
		boolean overcharged;
		int customColor;
		long seed;
		long spawnTimeMs;
		long lifeMs;
	}

	private static final class GroundShockRing {
		double x, y, z;
		float maxRadius;
		boolean overcharged;
		int customColor;
		long seed;
		long spawnTimeMs;
		long lifeMs;
	}

	private static final class DebrisSpark {
		double x, y, z;
		double vx, vy, vz;
		float size;
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

		Random rand = new Random(now ^ (stormId * 31337L));

		for (int i = 0; i < strikeX.length; i++) {
			double tx = strikeX[i];
			double ty = strikeY[i];
			double tz = strikeZ[i];
			boolean hitMob = strikeHitMob[i];

			double dx = tx - centerX;
			double dz = tz - centerZ;
			double dist = Math.sqrt(dx * dx + dz * dz);

			// Anchor start high inside the storm deck / funnel throat so bolts sweep diagonally
			double funnelBaseY = Math.min(centerY + 42.0, 218.0);
			double deckY = Math.min(centerY + 62.0, 238.0);

			double startX, startY, startZ;
			if (dist < 42.0) {
				double ang = rand.nextDouble() * Math.PI * 2.0;
				double r = 3.0 + rand.nextDouble() * 16.0;
				startX = centerX + Math.cos(ang) * r;
				startY = funnelBaseY + rand.nextDouble() * 14.0;
				startZ = centerZ + Math.sin(ang) * r;
			} else {
				double dirX = dx / dist;
				double dirZ = dz / dist;
				double perpX = -dirZ;
				double perpZ = dirX;
				double deckRadius = Math.min(dist * 0.45, 62.0);
				double lat = (rand.nextDouble() - 0.5) * 24.0;
				startX = centerX + dirX * deckRadius + perpX * lat;
				startY = deckY - 6.0 + (rand.nextDouble() - 0.5) * 8.0;
				startZ = centerZ + dirZ * deckRadius + perpZ * lat;
			}

			if (activeBolts.size() >= MAX_ACTIVE_BOLTS) {
				activeBolts.remove(0);
			}
			OrbitalBolt bolt = new OrbitalBolt();
			bolt.startX = startX;
			bolt.startY = startY;
			bolt.startZ = startZ;
			bolt.endX = tx;
			bolt.endY = ty;
			bolt.endZ = tz;
			bolt.hitMob = hitMob;
			bolt.overcharged = overcharged;
			bolt.customColor = customColor;
			bolt.seed = rand.nextLong();
			bolt.spawnTimeMs = now;
			bolt.lifeMs = hitMob ? 360L : 290L;
			activeBolts.add(bolt);

			// Add ground shockwave & electric cracks
			if (activeRings.size() >= MAX_ACTIVE_RINGS) {
				activeRings.remove(0);
			}
			GroundShockRing ring = new GroundShockRing();
			ring.x = tx;
			ring.y = ty + 0.08;
			ring.z = tz;
			ring.maxRadius = hitMob ? (overcharged ? 4.8f : 3.6f) : 2.6f;
			ring.overcharged = overcharged;
			ring.customColor = customColor;
			ring.seed = rand.nextLong();
			ring.spawnTimeMs = now;
			ring.lifeMs = hitMob ? 560L : 380L;
			activeRings.add(ring);

			// Spawn flying debris & electric sparks on mob strikes or close ambient hits
			if (hitMob || dist < 110.0) {
				int sparkCount = hitMob ? (overcharged ? 14 : 9) : 5;
				for (int s = 0; s < sparkCount; s++) {
					if (activeSparks.size() >= MAX_ACTIVE_SPARKS) {
						activeSparks.remove(0);
					}
					DebrisSpark sp = new DebrisSpark();
					double sang = rand.nextDouble() * Math.PI * 2.0;
					double hspd = 1.8 + rand.nextDouble() * 5.2;
					sp.x = tx + (rand.nextDouble() - 0.5) * 0.8;
					sp.y = ty + 0.25;
					sp.z = tz + (rand.nextDouble() - 0.5) * 0.8;
					sp.vx = Math.cos(sang) * hspd;
					sp.vy = 4.2 + rand.nextDouble() * 6.8;
					sp.vz = Math.sin(sang) * hspd;
					sp.darkDebris = (s % 3 == 0);
					sp.size = sp.darkDebris ? (0.14f + rand.nextFloat() * 0.14f) : (0.18f + rand.nextFloat() * 0.22f);
					sp.customColor = customColor;
					sp.spawnTimeMs = now;
					sp.lifeMs = 420L + rand.nextInt(320);
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

		// Prune expired storms cleanly
		Iterator<Map.Entry<Integer, ActiveStorm>> stormIt = activeStorms.entrySet().iterator();
		while (stormIt.hasNext()) {
			ActiveStorm s = stormIt.next().getValue();
			long maxGrace = Math.max(1500L, (long) s.remainingTicks * 55L + 900L);
			if (now - s.lastSyncTimeMs > maxGrace) {
				stormIt.remove();
			}
		}

		Tessellator t = Tessellator.instance;

		float prevBrightX = OpenGlHelper.lastBrightnessX;
		float prevBrightY = OpenGlHelper.lastBrightnessY;
		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_POLYGON_BIT);

		GL11.glDisable(GL11.GL_LIGHTING);
		RenderHelper.disableStandardItemLighting();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);
		GL11.glDisable(GL11.GL_FOG);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_BLEND);

		// 1. Render Giant Dark Swirling Sky Vortices (Option A Cloud Tornado & Canopy)
		for (ActiveStorm storm : activeStorms.values()) {
			renderSkyVortex(mc, t, storm, camX, camY, camZ, now);
		}

		// 2. Render Expanding Ground Impact Rings & Electric Surface Cracks
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		renderGroundShockRings(mc, t, camX, camY, camZ, now);

		// 3. Render Diagonal Jagged Orbital Lightning Bolts across 15 Chunks
		renderOrbitalBolts(mc, t, camX, camY, camZ, now);

		// 4. Render Debris & Cyan Spark Bursts
		renderDebrisSparks(mc, t, camX, camY, camZ, now);

		GL11.glDepthMask(true);
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBrightX, prevBrightY);
		GL11.glPopAttrib();
		GL11.glPopMatrix();
	}

    private static void renderSkyVortex(Minecraft mc,Tessellator t,ActiveStorm storm,double camX,double camY,double camZ,long now){
        float elapsed=(now-storm.lastSyncTimeMs)/1000.0f,time=(now-storm.spawnTimeMs)/1000.0f;
        float remain=storm.remainingTicks*.05f-elapsed;
        float alpha=Math.min(1,time/.7f)*Math.min(1,Math.max(0,remain/1.1f));
        if(elapsed<.25f)alpha=Math.min(1,time/.7f);
        if(alpha<=.01f)return;
        CastingAtmosphereVFX.renderActiveStormCanopy(mc,t,storm.centerX,Math.min(storm.centerY+64,240),
                storm.centerZ,camX,camY,camZ,alpha,time,storm.overcharged);
    }

    public static boolean hasActiveStorms(){return !activeStorms.isEmpty();}


	private static void renderOrbitalBolts(Minecraft mc, Tessellator t, double camX, double camY, double camZ, long now) {
		if (activeBolts.isEmpty()) return;

		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

		Iterator<OrbitalBolt> it = activeBolts.iterator();
		while (it.hasNext()) {
			OrbitalBolt bolt = it.next();
			long ageMs = now - bolt.spawnTimeMs;
			if (ageMs >= bolt.lifeMs) {
				it.remove();
				continue;
			}

			float progress = (float) ageMs / (float) bolt.lifeMs;
			float flicker = (1.0f - progress) * (0.78f + 0.22f * (float) Math.sin(ageMs * 0.085));

			float rLight = 0.10f, gLight = 0.42f, bLight = 1.0f;
			float rCore = 0.85f, gCore = 0.96f, bCore = 1.0f;
			if (bolt.customColor != -1) {
				rLight = ((bolt.customColor >> 16) & 0xFF) / 255.0f;
				gLight = ((bolt.customColor >> 8) & 0xFF) / 255.0f;
				bLight = (bolt.customColor & 0xFF) / 255.0f;
				rCore = Math.min(1.0f, rLight * 1.3f + 0.25f);
				gCore = Math.min(1.0f, gLight * 1.3f + 0.25f);
				bCore = Math.min(1.0f, bLight * 1.3f + 0.25f);
			}

			double sx = bolt.startX - camX;
			double sy = bolt.startY - camY;
			double sz = bolt.startZ - camZ;
			double ex = bolt.endX - camX;
			double ey = bolt.endY - camY;
			double ez = bolt.endZ - camZ;

			double lenSq = (ex - sx) * (ex - sx) + (ey - sy) * (ey - sy) + (ez - sz) * (ez - sz);
			double len = Math.sqrt(lenSq);

			int segs = 14;
			double[] px = new double[segs + 1];
			double[] py = new double[segs + 1];
			double[] pz = new double[segs + 1];
			px[0] = sx; py[0] = sy; pz[0] = sz;
			px[segs] = ex; py[segs] = ey; pz[segs] = ez;

			Random rnd = new Random(bolt.seed);
			float maxJitter = (float) Math.min(9.5, 2.2 + len * 0.055);
			for (int k = 1; k < segs; k++) {
				float frac = (float) k / (float) segs;
				float env = (float) Math.sin(frac * Math.PI);
				float jit = maxJitter * env;

				px[k] = sx + (ex - sx) * frac + (rnd.nextFloat() - 0.5f) * 2.0f * jit;
				py[k] = sy + (ey - sy) * frac + (rnd.nextFloat() - 0.5f) * 0.9f * jit;
				pz[k] = sz + (ez - sz) * frac + (rnd.nextFloat() - 0.5f) * 2.0f * jit;
			}

			// 1. Textured Camera-Facing Plasma Ribbon (looks thick and gorgeous at ALL distances & GPUs!)
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			mc.renderEngine.bindTexture(texLightningBolt);

			float distScale = (float) Math.min(2.8, Math.max(1.0, Math.sqrt(px[segs] * px[segs] + py[segs] * py[segs] + pz[segs] * pz[segs]) * 0.05));
			float auraHalfW = (bolt.hitMob ? (bolt.overcharged ? 1.55f : 1.15f) : 0.95f) * (0.7f + 0.3f * distScale);
			float coreHalfW = auraHalfW * 0.32f;

			// Outer glow ribbon
			GL11.glColor4f(rLight, gLight, bLight, flicker * 0.78f);
			drawBoltRibbon(t, px, py, pz, segs, auraHalfW);

			// White-cyan core ribbon
			GL11.glColor4f(rCore, gCore, bCore, flicker * 0.96f);
			drawBoltRibbon(t, px, py, pz, segs, coreHalfW);

			// 2. Crisp Vector Jagged Core & Mid-Air Forked Branches
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glEnable(GL11.GL_LINE_SMOOTH);
			GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

			GL11.glLineWidth(bolt.overcharged ? 5.5f : 4.0f);
			GL11.glColor4f(rLight, gLight, bLight, flicker * 0.85f);
			GL11.glBegin(GL11.GL_LINE_STRIP);
			for (int k = 0; k <= segs; k++) GL11.glVertex3d(px[k], py[k], pz[k]);
			GL11.glEnd();

			GL11.glLineWidth(2.0f);
			GL11.glColor4f(rCore, gCore, bCore, flicker);
			GL11.glBegin(GL11.GL_LINE_STRIP);
			for (int k = 0; k <= segs; k++) GL11.glVertex3d(px[k], py[k], pz[k]);
			GL11.glEnd();

			// Forked Sub-Branches splitting mid-flight
			for (int forkIdx : new int[]{4, 7, 10}) {
				double bx0 = px[forkIdx], by0 = py[forkIdx], bz0 = pz[forkIdx];
				double spread = 4.0 + len * 0.04;
				double drop = 5.0 + len * 0.06;
				double bx1 = bx0 + (rnd.nextFloat() - 0.5f) * spread;
				double by1 = by0 - drop * 0.45;
				double bz1 = bz0 + (rnd.nextFloat() - 0.5f) * spread;
				double bx2 = bx1 + (rnd.nextFloat() - 0.5f) * spread;
				double by2 = by1 - drop * 0.45;
				double bz2 = bz1 + (rnd.nextFloat() - 0.5f) * spread;
				double bx3 = bx2 + (rnd.nextFloat() - 0.5f) * spread * 0.7;
				double by3 = by2 - drop * 0.35;
				double bz3 = bz2 + (rnd.nextFloat() - 0.5f) * spread * 0.7;

				GL11.glLineWidth(bolt.overcharged ? 3.5f : 2.5f);
				GL11.glColor4f(rLight, gLight, bLight, flicker * 0.75f);
				GL11.glBegin(GL11.GL_LINE_STRIP);
				GL11.glVertex3d(bx0, by0, bz0);
				GL11.glVertex3d(bx1, by1, bz1);
				GL11.glVertex3d(bx2, by2, bz2);
				GL11.glVertex3d(bx3, by3, bz3);
				GL11.glEnd();

				GL11.glLineWidth(1.3f);
				GL11.glColor4f(rCore, gCore, bCore, flicker * 0.85f);
				GL11.glBegin(GL11.GL_LINE_STRIP);
				GL11.glVertex3d(bx0, by0, bz0);
				GL11.glVertex3d(bx1, by1, bz1);
				GL11.glVertex3d(bx2, by2, bz2);
				GL11.glVertex3d(bx3, by3, bz3);
				GL11.glEnd();
			}

			// Impact flash bloom right where the bolt strikes!
			if (progress < 0.45f) {
				float fAlpha = 1.0f - (progress / 0.45f);
				float fRad = (bolt.hitMob ? 2.4f : 1.5f) * (0.7f + 0.6f * progress);
				GL11.glEnable(GL11.GL_TEXTURE_2D);
				mc.renderEngine.bindTexture(texStarfallCore);
				GL11.glColor4f(rCore, gCore, bCore, fAlpha * 0.95f);
				drawBillboardQuad(t, ex, ey + 0.6, ez, fRad, ageMs * 0.6f);
			}
		}

		GL11.glDisable(GL11.GL_LINE_SMOOTH);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
	}

	private static void renderGroundShockRings(Minecraft mc, Tessellator t, double camX, double camY, double camZ, long now) {
		if (activeRings.isEmpty()) return;

		GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
		GL11.glPolygonOffset(-2.0f, -2.0f);

		Iterator<GroundShockRing> it = activeRings.iterator();
		while (it.hasNext()) {
			GroundShockRing ring = it.next();
			long ageMs = now - ring.spawnTimeMs;
			if (ageMs >= ring.lifeMs) {
				it.remove();
				continue;
			}

			float progress = (float) ageMs / (float) ring.lifeMs;
			float alpha = (float) Math.sin((1.0f - progress) * Math.PI * 0.5);
			float r = ring.maxRadius * (0.25f + 0.75f * (float) Math.sqrt(progress));

			float rLight = 0.10f, gLight = 0.45f, bLight = 1.0f;
			float rCore = 0.85f, gCore = 0.96f, bCore = 1.0f;
			if (ring.customColor != -1) {
				rLight = ((ring.customColor >> 16) & 0xFF) / 255.0f;
				gLight = ((ring.customColor >> 8) & 0xFF) / 255.0f;
				bLight = (ring.customColor & 0xFF) / 255.0f;
				rCore = Math.min(1.0f, rLight * 1.3f + 0.2f);
				gCore = Math.min(1.0f, gLight * 1.3f + 0.2f);
				bCore = Math.min(1.0f, bLight * 1.3f + 0.2f);
			}

			double rx = ring.x - camX;
			double ry = ring.y - camY;
			double rz = ring.z - camZ;

			// Expanding ionized ground ring
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			mc.renderEngine.bindTexture(texLightningShockwave);
			GL11.glPushMatrix();
			GL11.glTranslated(rx, ry, rz);
			GL11.glRotatef(ageMs * 0.18f, 0.0f, 1.0f, 0.0f);
			GL11.glColor4f(rLight, gLight, bLight, alpha * 0.90f);
			t.startDrawingQuads();
			t.addVertexWithUV(-r, 0, r, 0, 1);
			t.addVertexWithUV(r, 0, r, 1, 1);
			t.addVertexWithUV(r, 0, -r, 1, 0);
			t.addVertexWithUV(-r, 0, -r, 0, 0);
			t.draw();

			GL11.glColor4f(rCore, gCore, bCore, alpha * 0.75f);
			float rIn = r * 0.60f;
			t.startDrawingQuads();
			t.addVertexWithUV(-rIn, 0.02, rIn, 0, 1);
			t.addVertexWithUV(rIn, 0.02, rIn, 1, 1);
			t.addVertexWithUV(rIn, 0.02, -rIn, 1, 0);
			t.addVertexWithUV(-rIn, 0.02, -rIn, 0, 0);
			t.draw();
			GL11.glPopMatrix();

			// Crackling horizontal ground surface electric arcs spreading around the strike
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glEnable(GL11.GL_LINE_SMOOTH);
			Random cRnd = new Random(ring.seed);
			int cracks = ring.overcharged ? 6 : 4;
			for (int c = 0; c < cracks; c++) {
				double ang = (c * Math.PI * 2.0 / cracks) + cRnd.nextDouble() * 0.8;
				double crackLen = ring.maxRadius * (0.65 + 0.55 * progress);
				drawJaggedLineStrip(
						rx, ry + 0.06, rz,
						rx + Math.cos(ang) * crackLen, ry + 0.08, rz + Math.sin(ang) * crackLen,
						4, 0.45f, cRnd, rLight, gLight, bLight, rCore, gCore, bCore,
						alpha * 0.85f, 3.2f, 1.5f
				);
			}
			GL11.glDisable(GL11.GL_LINE_SMOOTH);
		}

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
	}

	private static void renderDebrisSparks(Minecraft mc, Tessellator t, double camX, double camY, double camZ, long now) {
		if (activeSparks.isEmpty()) return;

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		mc.renderEngine.bindTexture(texStarfallCore);

		// Draw glow sparks additive, then dark debris fragments with alpha blend
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		Iterator<DebrisSpark> it = activeSparks.iterator();
		while (it.hasNext()) {
			DebrisSpark sp = it.next();
			long ageMs = now - sp.spawnTimeMs;
			if (ageMs >= sp.lifeMs) {
				it.remove();
				continue;
			}
			if (sp.darkDebris) continue;

			float tSec = ageMs / 1000.0f;
			float fade = 1.0f - ((float) ageMs / (float) sp.lifeMs);

			double wx = sp.x + sp.vx * tSec;
			double wy = sp.y + sp.vy * tSec - 7.5 * tSec * tSec;
			double wz = sp.z + sp.vz * tSec;

			float rLight = 0.25f, gLight = 0.72f, bLight = 1.0f;
			if (sp.customColor != -1) {
				rLight = Math.min(1.0f, (((sp.customColor >> 16) & 0xFF) / 255.0f) * 1.2f + 0.1f);
				gLight = Math.min(1.0f, (((sp.customColor >> 8) & 0xFF) / 255.0f) * 1.2f + 0.1f);
				bLight = Math.min(1.0f, ((sp.customColor & 0xFF) / 255.0f) * 1.2f + 0.1f);
			}

			GL11.glColor4f(rLight, gLight, bLight, fade * 0.95f);
			drawBillboardQuad(t, wx - camX, wy - camY, wz - camZ, sp.size * (0.6f + 0.4f * fade), tSec * 360.0f);
		}

		// Dark earth/cinder tumbling fragments
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		for (DebrisSpark sp : activeSparks) {
			if (!sp.darkDebris) continue;
			long ageMs = now - sp.spawnTimeMs;
			if (ageMs >= sp.lifeMs) continue;
			float tSec = ageMs / 1000.0f;
			float fade = 1.0f - ((float) ageMs / (float) sp.lifeMs);

			double wx = sp.x + sp.vx * tSec;
			double wy = sp.y + sp.vy * tSec - 9.0 * tSec * tSec;
			double wz = sp.z + sp.vz * tSec;

			GL11.glColor4f(0.08f, 0.10f, 0.16f, fade * 0.90f);
			drawBillboardQuad(t, wx - camX, wy - camY, wz - camZ, sp.size, tSec * 420.0f);
		}
		GL11.glEnable(GL11.GL_TEXTURE_2D);
	}

	private static void drawBillboardQuad(Tessellator t, double x, double y, double z, float halfSize, float rollDeg) {
		GL11.glPushMatrix();
		GL11.glTranslated(x, y, z);
		GL11.glRotatef(180.0F - RenderManager.instance.playerViewY, 0.0F, 1.0F, 0.0F);
		GL11.glRotatef(-RenderManager.instance.playerViewX, 1.0F, 0.0F, 0.0F);
		if (rollDeg != 0.0f) {
			GL11.glRotatef(rollDeg, 0.0F, 0.0F, 1.0F);
		}

		t.startDrawingQuads();
		t.addVertexWithUV(-halfSize, -halfSize, 0.0, 0.0, 1.0);
		t.addVertexWithUV(halfSize, -halfSize, 0.0, 1.0, 1.0);
		t.addVertexWithUV(halfSize, halfSize, 0.0, 1.0, 0.0);
		t.addVertexWithUV(-halfSize, halfSize, 0.0, 0.0, 0.0);
		t.draw();

		GL11.glPopMatrix();
	}

	private static void drawBoltRibbon(Tessellator t, double[] px, double[] py, double[] pz, int segs, float halfWidth) {
		t.startDrawingQuads();
		for (int k = 0; k < segs; k++) {
			double dx = px[k + 1] - px[k];
			double dy = py[k + 1] - py[k];
			double dz = pz[k + 1] - pz[k];

			double mx = (px[k] + px[k + 1]) * 0.5;
			double my = (py[k] + py[k + 1]) * 0.5;
			double mz = (pz[k] + pz[k + 1]) * 0.5;

			// Vector from midpoint to camera is (-mx, -my, -mz) since we're in cam-relative coords!
			double sx = dy * (-mz) - dz * (-my);
			double sy = dz * (-mx) - dx * (-mz);
			double sz = dx * (-my) - dy * (-mx);
			double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
			if (len < 1e-5) {
				sx = 1.0; sy = 0.0; sz = 0.0; len = 1.0;
			}
			double scale = halfWidth / len;
			sx *= scale; sy *= scale; sz *= scale;

			float v0 = (float) k / (float) segs;
			float v1 = (float) (k + 1) / (float) segs;

			t.addVertexWithUV(px[k] - sx, py[k] - sy, pz[k] - sz, 0.0, v0);
			t.addVertexWithUV(px[k] + sx, py[k] + sy, pz[k] + sz, 1.0, v0);
			t.addVertexWithUV(px[k + 1] + sx, py[k + 1] + sy, pz[k + 1] + sz, 1.0, v1);
			t.addVertexWithUV(px[k + 1] - sx, py[k + 1] - sy, pz[k + 1] - sz, 0.0, v1);
		}
		t.draw();
	}

	private static void drawJaggedLineStrip(
			double x1, double y1, double z1,
			double x2, double y2, double z2,
			int segs, float jitter, Random rnd,
			float rL, float gL, float bL,
			float rC, float gC, float bC,
			float alpha, float wOut, float wCore
	) {
		double[] lx = new double[segs + 1];
		double[] ly = new double[segs + 1];
		double[] lz = new double[segs + 1];
		lx[0] = x1; ly[0] = y1; lz[0] = z1;
		lx[segs] = x2; ly[segs] = y2; lz[segs] = z2;

		for (int k = 1; k < segs; k++) {
			float f = (float) k / (float) segs;
			float env = (float) Math.sin(f * Math.PI);
			lx[k] = x1 + (x2 - x1) * f + (rnd.nextFloat() - 0.5f) * 2.0f * jitter * env;
			ly[k] = y1 + (y2 - y1) * f + (rnd.nextFloat() - 0.5f) * 0.8f * jitter * env;
			lz[k] = z1 + (z2 - z1) * f + (rnd.nextFloat() - 0.5f) * 2.0f * jitter * env;
		}

		GL11.glLineWidth(wOut);
		GL11.glColor4f(rL, gL, bL, alpha * 0.85f);
		GL11.glBegin(GL11.GL_LINE_STRIP);
		for (int k = 0; k <= segs; k++) GL11.glVertex3d(lx[k], ly[k], lz[k]);
		GL11.glEnd();

		GL11.glLineWidth(wCore);
		GL11.glColor4f(rC, gC, bC, alpha);
		GL11.glBegin(GL11.GL_LINE_STRIP);
		for (int k = 0; k <= segs; k++) GL11.glVertex3d(lx[k], ly[k], lz[k]);
		GL11.glEnd();
	}
}
