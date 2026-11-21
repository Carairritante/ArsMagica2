package am2.entities.renderers;

import am2.AMCore;
import am2.api.spell.enums.Affinity;
import am2.client.vfx.BlockbenchZoneModel;
import am2.client.vfx.LightningVFXHelper;
import am2.entities.EntitySpellEffect;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@SideOnly(Side.CLIENT)
public class RenderSpellEffect extends Render {

	// Firestorm textures
	private static final ResourceLocation texOuter = new ResourceLocation("arsmagica2", "textures/misc/vfx/circle_outer.png");
	private static final ResourceLocation texMiddle = new ResourceLocation("arsmagica2", "textures/misc/vfx/circle_middle.png");
	private static final ResourceLocation texCore = new ResourceLocation("arsmagica2", "textures/misc/vfx/circle_core.png");
	private static final ResourceLocation texGround = new ResourceLocation("arsmagica2", "textures/misc/vfx/circle_ground.png");
	private static final ResourceLocation texBeam = new ResourceLocation("arsmagica2", "textures/misc/vfx/fire_beam.png");
	private static final ResourceLocation texShockwave = new ResourceLocation("arsmagica2", "textures/misc/vfx/shockwave_ring.png");

	// Blizzard textures
	private static final ResourceLocation texBlizzardOuter = new ResourceLocation("arsmagica2", "textures/misc/vfx/blizzard_outer.png");
	private static final ResourceLocation texBlizzardMiddle = new ResourceLocation("arsmagica2", "textures/misc/vfx/blizzard_middle.png");
	private static final ResourceLocation texBlizzardCore = new ResourceLocation("arsmagica2", "textures/misc/vfx/blizzard_core.png");
	private static final ResourceLocation texBlizzardGround = new ResourceLocation("arsmagica2", "textures/misc/vfx/blizzard_ground.png");
	private static final ResourceLocation texBlizzardShard = new ResourceLocation("arsmagica2", "textures/misc/vfx/blizzard_ice_shard.png");
	private static final ResourceLocation texBlizzardVortex = new ResourceLocation("arsmagica2", "textures/misc/vfx/blizzard_vortex.png");
	private static final ResourceLocation texBlizzardShatter = new ResourceLocation("arsmagica2", "textures/misc/vfx/blizzard_shatter.png");

	// Starfall Impact textures
	private static final ResourceLocation texStarfallCore = new ResourceLocation("arsmagica2", "textures/misc/vfx/starfall_core.png");
	private static final ResourceLocation texStarfallShockwave = new ResourceLocation("arsmagica2", "textures/misc/vfx/starfall_shockwave.png");
	private static final ResourceLocation texStarfallPillar = new ResourceLocation("arsmagica2", "textures/misc/vfx/starfall_pillar.png");
	private static final ResourceLocation texStarfallGround = new ResourceLocation("arsmagica2", "textures/misc/vfx/starfall_ground.png");

	// Grow Mandala textures
	private static final ResourceLocation texGrowGround = new ResourceLocation("arsmagica2", "textures/misc/vfx/grow_ground.png");
	private static final ResourceLocation texGrowPulse = new ResourceLocation("arsmagica2", "textures/misc/vfx/grow_pulse.png");

	// Explosion Impact textures
	private static final ResourceLocation texExplosionGround = new ResourceLocation("arsmagica2", "textures/misc/vfx/explosion_ground.png");
	private static final ResourceLocation texExplosionShockwave = new ResourceLocation("arsmagica2", "textures/misc/vfx/explosion_shockwave.png");

	// Lightning Impact textures
	private static final ResourceLocation texLightningSigil = new ResourceLocation("arsmagica2", "textures/misc/vfx/lightning_sigil.png");
	private static final ResourceLocation texLightningShockwave = new ResourceLocation("arsmagica2", "textures/misc/vfx/lightning_shockwave.png");
	private static final ResourceLocation texStormCloud = new ResourceLocation("arsmagica2", "textures/misc/vfx/storm_cloud.png");

	@Override
	public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
		if (!(entity instanceof EntitySpellEffect)) {
			return;
		}

		EntitySpellEffect effect = (EntitySpellEffect) entity;

		if (effect.isRainOfFire()) {
			renderFirestorm(effect, x, y, z, partialTicks);
		} else if (effect.isBlizzard()) {
			renderBlizzard(effect, x, y, z, partialTicks);
		} else if (effect.isStarfallImpact()) {
			renderStarfallImpact(effect, x, y, z, partialTicks);
		} else if (effect.isGrowMandala()) {
			renderGrowMandala(effect, x, y, z, partialTicks);
		} else if (effect.isExplosionImpact()) {
			renderExplosionImpact(effect, x, y, z, partialTicks);
		} else if (effect.isLightningImpact()) {
			renderLightningImpact(effect, x, y, z, partialTicks);
		} else if (effect.isZone()) {
			renderZone(effect, x, y, z, partialTicks);
		} else if (effect.isAoEShockwave()) {
			renderAoEShockwave(effect, x, y, z, partialTicks);
		}
	}

	private static final double ZONE_CLUSTER_MIN_DIST_XZ = 3.0D;
	private static final double ZONE_CLUSTER_RADIUS_FRAC = 0.90D;
	private static final double ZONE_CLUSTER_DIST_Y = 2.5D;
	private static final double ZONE_SEARCH_RANGE_XZ = 32.0D;
	private static final double ZONE_SEARCH_RANGE_Y = 8.0D;
	private static final int MAX_ZONE_SPIKE_TIERS = 8;

	private void renderZone(EntitySpellEffect effect, double x, double y, double z, float partialTicks) {
		if (AMCore.config.NoGFX() || effect.isDead || effect.worldObj == null) {
			return;
		}

		List<EntitySpellEffect> cluster = collectZoneCluster(effect);
		EntitySpellEffect renderOwner = selectZoneClusterOwner(cluster);
		if (renderOwner != effect) {
			return; // Defer rendering to the deterministic cluster owner
		}

		if (cluster.size() > 1) {
			Collections.sort(cluster, new Comparator<EntitySpellEffect>() {
				@Override
				public int compare(EntitySpellEffect a, EntitySpellEffect b) {
					return Integer.compare(a.getEntityId(), b.getEntityId());
				}
			});
		}

		BlockbenchZoneModel model = BlockbenchZoneModel.get();
		if (model == null) {
			return;
		}

		float maxRadius = 2.0f;
		float bestAlpha = 0.0f;
		for (int i = 0; i < cluster.size(); i++) {
			EntitySpellEffect member = cluster.get(i);
			float r = member.getRadius();
			if (r > maxRadius) {
				maxRadius = r;
			}
			float a = computeZoneFade(member, partialTicks);
			if (a > bestAlpha) {
				bestAlpha = a;
			}
		}
		if (bestAlpha <= 0.01f) {
			return;
		}

		int tierCount = Math.min(MAX_ZONE_SPIKE_TIERS, cluster.size());
		int[] tierColors = new int[tierCount];
		float[] tierPopY = new float[tierCount];
		for (int i = 0; i < tierCount; i++) {
			EntitySpellEffect member = cluster.get(i);
			tierColors[i] = resolveZoneColorRgb(member);
			float age = member.ticksExisted + partialTicks;
			float intro = Math.min(1.0f, age / 7.0f);
			float fade = computeZoneFade(member, partialTicks);
			tierPopY[i] = (float) Math.sin(intro * Math.PI * 0.5) * (0.25f + 0.75f * fade);
		}

		EntitySpellEffect latestMember = cluster.get(cluster.size() - 1);
		int ringColor = resolveZoneColorRgb(latestMember);
		float ringRot = ((effect.worldObj.getTotalWorldTime() + partialTicks) * 1.15f) % 360.0f;
		boolean lowGfx = AMCore.config.LowGFX();

		float prevBrightX = OpenGlHelper.lastBrightnessX;
		float prevBrightY = OpenGlHelper.lastBrightnessY;
		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_POLYGON_BIT);

		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDepthMask(false);
		RenderHelper.disableStandardItemLighting();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);
		GL11.glEnable(GL11.GL_BLEND);

		GL11.glTranslated(x, y + 0.025D, z);
		model.render(maxRadius, ringRot, tierCount, tierColors, tierPopY, bestAlpha * 0.92f, ringColor, lowGfx);

		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBrightX, prevBrightY);
		GL11.glPopAttrib();
		GL11.glPopMatrix();
	}

	private static List<EntitySpellEffect> collectZoneCluster(EntitySpellEffect seed) {
		double searchXZ = Math.max(ZONE_SEARCH_RANGE_XZ, seed.getRadius() * 2.0D);
		AxisAlignedBB searchBox = AxisAlignedBB.getBoundingBox(
				seed.posX - searchXZ, seed.posY - ZONE_SEARCH_RANGE_Y, seed.posZ - searchXZ,
				seed.posX + searchXZ, seed.posY + ZONE_SEARCH_RANGE_Y, seed.posZ + searchXZ
		);
		List<?> raw = seed.worldObj.getEntitiesWithinAABB(EntitySpellEffect.class, searchBox);
		List<EntitySpellEffect> candidates = new ArrayList<EntitySpellEffect>(raw.size());
		for (int i = 0; i < raw.size(); i++) {
			Object obj = raw.get(i);
			if (!(obj instanceof EntitySpellEffect)) {
				continue;
			}
			EntitySpellEffect other = (EntitySpellEffect) obj;
			if (!other.isDead && other.isZone() && other != seed) {
				candidates.add(other);
			}
		}

		List<EntitySpellEffect> cluster = new ArrayList<EntitySpellEffect>(8);
		cluster.add(seed);
		if (candidates.isEmpty()) {
			return cluster;
		}

		boolean[] visited = new boolean[candidates.size()];
		for (int q = 0; q < cluster.size(); q++) {
			EntitySpellEffect cur = cluster.get(q);
			for (int i = 0; i < candidates.size(); i++) {
				if (visited[i]) {
					continue;
				}
				EntitySpellEffect cand = candidates.get(i);
				if (zonesShouldMerge(cur, cand)) {
					visited[i] = true;
					cluster.add(cand);
				}
			}
		}
		return cluster;
	}

	private static boolean zonesShouldMerge(EntitySpellEffect a, EntitySpellEffect b) {
		double dy = Math.abs(a.posY - b.posY);
		if (dy > ZONE_CLUSTER_DIST_Y) {
			return false;
		}
		double maxRadius = Math.max(a.getRadius(), b.getRadius());
		double thresholdXZ = Math.max(ZONE_CLUSTER_MIN_DIST_XZ, maxRadius * ZONE_CLUSTER_RADIUS_FRAC);
		double dx = a.posX - b.posX;
		double dz = a.posZ - b.posZ;
		return (dx * dx + dz * dz) <= (thresholdXZ * thresholdXZ);
	}

	private static EntitySpellEffect selectZoneClusterOwner(List<EntitySpellEffect> cluster) {
		EntitySpellEffect owner = cluster.get(0);
		float bestRadius = owner.getRadius();
		int bestId = owner.getEntityId();
		for (int i = 1; i < cluster.size(); i++) {
			EntitySpellEffect cand = cluster.get(i);
			float r = cand.getRadius();
			int id = cand.getEntityId();
			if (r > bestRadius + 0.01f || (Math.abs(r - bestRadius) <= 0.01f && id < bestId)) {
				owner = cand;
				bestRadius = r;
				bestId = id;
			}
		}
		return owner;
	}

	private static float computeZoneFade(EntitySpellEffect zone, float partialTicks) {
		float age = zone.ticksExisted + partialTicks;
		int maxTicks = 100;
		try {
			int dwTicks = zone.getTicksToExist();
			if (dwTicks > 0) {
				maxTicks = dwTicks;
			}
		} catch (Throwable ignored) {
		}
		float intro = Math.min(1.0f, age / 7.0f);
		intro = (float) Math.sin(intro * Math.PI * 0.5);
		float remaining = maxTicks - age;
		float outro = 1.0f;
		if (remaining < 14.0f) {
			outro = Math.max(0.0f, remaining / 14.0f);
		}
		return intro * outro;
	}

	private static int resolveZoneColorRgb(EntitySpellEffect zone) {
		int explicit = zone.getSpellColor();
		if (explicit != -1) {
			return explicit;
		}
		Affinity aff = zone.getZoneAffinity();
		if (aff == null) {
			return 0x80D0FF;
		}
		switch (aff) {
			case ARCANE:    return 0xD633FF;
			case WATER:     return 0x2277FF;
			case FIRE:      return 0xFF4518;
			case EARTH:     return 0xFF8C1A;
			case AIR:       return 0x6BEAFF;
			case LIGHTNING: return 0xFFEE22;
			case ICE:       return 0x22DDFF;
			case NATURE:    return 0x22E855;
			case LIFE:      return 0xFFD42A;
			case ENDER:     return 0x3D066B;
			case NONE:
			default:        return 0x80D0FF;
		}
	}

	private void renderFirestorm(EntitySpellEffect effect, double x, double y, double z, float partialTicks) {
		float age = effect.ticksExisted + partialTicks;
		int maxTicks = 100;
		try {
			maxTicks = effect.getTicksToExist();
		} catch (Throwable t) {
			maxTicks = 100;
		}
		if (maxTicks <= 0) maxTicks = 100;

		// 1. Smooth intro / outro scaling and alpha
		float intro = Math.min(1.0f, age / 14.0f);
		intro = (float) Math.sin(intro * Math.PI / 2.0); // smooth ease-out

		float remaining = maxTicks - age;
		float outro = 1.0f;
		if (remaining < 18.0f && remaining >= 0.0f) {
			outro = (float) Math.sin(Math.max(0.0f, remaining / 18.0f) * Math.PI / 2.0);
		} else if (remaining < 0.0f) {
			// Grace period if server tick pacing differs by a few frames before kill packet
			outro = Math.max(0.0f, 1.0f + (remaining / 12.0f));
		}

		float globalScale = intro * outro;
		if (globalScale <= 0.002f) return;

		float masterAlpha = globalScale;
		float radius = effect.getRadius();
		if (radius <= 0.1f) radius = 3.0f;
		float baseRadius = radius * 1.35f;

		// 2. Color determination
		int customColor = effect.getSpellColor();
		float rOut = 1.0f, gOut = 0.45f, bOut = 0.08f;
		float rMid = 1.0f, gMid = 0.78f, bMid = 0.15f;
		float rCore = 1.0f, gCore = 0.95f, bCore = 0.65f;
		float rBeam = 1.0f, gBeam = 0.60f, bBeam = 0.12f;

		if (customColor != -1) {
			float cr = ((customColor >> 16) & 0xFF) / 255.0f;
			float cg = ((customColor >> 8) & 0xFF) / 255.0f;
			float cb = (customColor & 0xFF) / 255.0f;
			rOut = cr * 0.9f; gOut = cg * 0.9f; bOut = cb * 0.9f;
			rMid = Math.min(1.0f, cr * 1.15f); gMid = Math.min(1.0f, cg * 1.15f); bMid = Math.min(1.0f, cb * 1.15f);
			rCore = Math.min(1.0f, cr * 1.3f + 0.2f); gCore = Math.min(1.0f, cg * 1.3f + 0.2f); bCore = Math.min(1.0f, cb * 1.3f + 0.2f);
			rBeam = cr; gBeam = cg; bBeam = cb;
		}

		Tessellator t = Tessellator.instance;

		// 3. Setup OpenGL state for clean additive glow
		float prevBrightX = OpenGlHelper.lastBrightnessX;
		float prevBrightY = OpenGlHelper.lastBrightnessY;
		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_POLYGON_BIT);

		GL11.glDisable(GL11.GL_LIGHTING);
		RenderHelper.disableStandardItemLighting();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_CULL_FACE);

		// 4. Ground targeting projection (horizontal at y + 0.03)
		this.bindTexture(texGround);
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, (float) y + 0.03f, (float) z);
		GL11.glRotatef(age * 0.25f, 0.0f, 1.0f, 0.0f);
		float groundPulse = 0.97f + 0.03f * (float) Math.sin(age * 0.12f);
		float gScale = baseRadius * globalScale * groundPulse;
		GL11.glColor4f(rOut, gOut, bOut, 0.55f * masterAlpha);
		renderHorizontalQuad(t, -gScale, gScale, -gScale, gScale, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
		GL11.glPopMatrix();

		// 5. Descending Fire Pillars & Shockwaves
		float skyHeight = 11.0f;
		this.bindTexture(texBeam);
		int numPillars = 4;
		for (int i = 0; i < numPillars; i++) {
			float pTime = (age * 0.30f + i * 2.15f);
			float cycle = pTime % 5.0f;
			if (cycle < 3.8f) {
				float strikeProgress = cycle / 3.8f;
				float beamAlpha = (float) Math.sin(strikeProgress * Math.PI) * masterAlpha;

				int cycleIdx = (int) (pTime / 5.0f);
				double seedAngle = ((effect.getEntityId() * 41 + i * 89 + cycleIdx * 127) % 360);
				double pRad = Math.toRadians(seedAngle);
				double pDist = (((effect.getEntityId() * 17 + i * 47 + cycleIdx * 61) % 75) / 100.0) * (radius * 0.85);

				double px = x + pDist * Math.cos(pRad);
				double pz = z + pDist * Math.sin(pRad);

				// Render vertical pillar
				GL11.glColor4f(rBeam, gBeam, bBeam, beamAlpha * 0.85f);
				renderBeam(t, px, y, y + skyHeight, pz, 0.65f, -age * 0.12f);

				// Ground impact shockwave ring
				float shockSize = 0.2f + strikeProgress * 1.6f;
				float shockAlpha = (1.0f - strikeProgress) * beamAlpha;
				if (shockAlpha > 0.03f) {
					this.bindTexture(texShockwave);
					GL11.glColor4f(rBeam, gBeam, bBeam, shockAlpha * 0.75f);
					renderHorizontalQuad(t, (float) (px - shockSize), (float) (px + shockSize),
							(float) (pz - shockSize), (float) (pz + shockSize),
							(float) (y + 0.05f), 0.0f, 1.0f, 0.0f, 1.0f);
					this.bindTexture(texBeam);
				}
			}
		}

		// 6. Sky Arcane Magic Array (floating at y + skyHeight)
		GL11.glPushMatrix();
		float hover = 0.15f * (float) Math.sin(age * 0.07f);
		GL11.glTranslatef((float) x, (float) (y + skyHeight + hover), (float) z);

		// Layer A: Outer Dial (rotates clockwise)
		this.bindTexture(texOuter);
		GL11.glPushMatrix();
		GL11.glRotatef(age * 0.5f, 0.0f, 1.0f, 0.0f);
		float outerScale = baseRadius * globalScale;
		GL11.glColor4f(rOut, gOut, bOut, 0.88f * masterAlpha);
		renderHorizontalQuad(t, -outerScale, outerScale, -outerScale, outerScale, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
		GL11.glPopMatrix();

		// Layer B: Middle Solar Star Array (rotates counter-clockwise)
		this.bindTexture(texMiddle);
		GL11.glPushMatrix();
		GL11.glRotatef(-age * 0.85f, 0.0f, 1.0f, 0.0f);
		float midScale = baseRadius * globalScale * 0.92f;
		GL11.glColor4f(rMid, gMid, bMid, 0.95f * masterAlpha);
		renderHorizontalQuad(t, -midScale, midScale, -midScale, midScale, 0.02f, 0.0f, 1.0f, 0.0f, 1.0f);
		GL11.glPopMatrix();

		// Layer C: Core Flame Sigil (pulsates and rotates slowly)
		this.bindTexture(texCore);
		GL11.glPushMatrix();
		GL11.glRotatef(age * 0.65f, 0.0f, 1.0f, 0.0f);
		float corePulse = 1.0f + 0.06f * (float) Math.sin(age * 0.22f);
		float coreScale = baseRadius * globalScale * 0.52f * corePulse;
		GL11.glColor4f(rCore, gCore, bCore, 1.0f * masterAlpha);
		renderHorizontalQuad(t, -coreScale, coreScale, -coreScale, coreScale, 0.04f, 0.0f, 1.0f, 0.0f, 1.0f);
		GL11.glPopMatrix();

		// Layer D: Vector Gyroscope Rings (smooth 3D mathematical circle curves)
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glPushMatrix();
		GL11.glRotatef(age * 1.2f, 0.0f, 1.0f, 0.0f);
		GL11.glRotatef(18.0f, 1.0f, 0.0f, 0.0f);
		GL11.glColor4f(rMid, gMid, bMid, 0.65f * masterAlpha);
		renderSmoothVectorRing(outerScale * 0.98f, 72, 2.0f);
		GL11.glPopMatrix();

		GL11.glPushMatrix();
		GL11.glRotatef(-age * 1.4f, 0.0f, 1.0f, 0.0f);
		GL11.glRotatef(-18.0f, 0.0f, 0.0f, 1.0f);
		GL11.glColor4f(rOut, gOut, bOut, 0.55f * masterAlpha);
		renderSmoothVectorRing(outerScale * 1.04f, 72, 1.5f);
		GL11.glPopMatrix();

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glPopMatrix();

		// 7. Restore OpenGL state
		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_LIGHTING);
		RenderHelper.enableStandardItemLighting();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBrightX, prevBrightY);
		GL11.glPopAttrib();
		GL11.glPopMatrix();
	}

	private void renderBlizzard(EntitySpellEffect effect, double x, double y, double z, float partialTicks) {
		float age = effect.ticksExisted + partialTicks;
		int maxTicks = 100;
		try {
			maxTicks = effect.getTicksToExist();
		} catch (Throwable t) {
			maxTicks = 100;
		}
		if (maxTicks <= 0) maxTicks = 100;

		// 1. Smooth intro / outro scaling and alpha
		float intro = Math.min(1.0f, age / 14.0f);
		intro = (float) Math.sin(intro * Math.PI / 2.0); // smooth ease-out

		float remaining = maxTicks - age;
		float outro = 1.0f;
		if (remaining < 18.0f && remaining >= 0.0f) {
			outro = (float) Math.sin(Math.max(0.0f, remaining / 18.0f) * Math.PI / 2.0);
		} else if (remaining < 0.0f) {
			outro = Math.max(0.0f, 1.0f + (remaining / 12.0f));
		}

		float globalScale = intro * outro;
		if (globalScale <= 0.002f) return;

		float masterAlpha = globalScale;
		float radius = effect.getRadius();
		if (radius <= 0.1f) radius = 3.0f;
		float baseRadius = radius * 1.35f;

		// 2. Cryogenic Color Palette: Ice blue, Cyan, and Pure Frost White
		int customColor = effect.getSpellColor();
		float rFrost = 0.45f, gFrost = 0.82f, bFrost = 1.0f;   // Vibrant ice cyan
		float rStar  = 0.70f, gStar  = 0.92f, bStar  = 1.0f;   // Cold pale cyan
		float rCore  = 0.90f, gCore  = 0.98f, bCore  = 1.0f;   // Pure frost luminescence
		float rShard = 0.80f, gShard = 0.95f, bShard = 1.0f;   // Diamond ice crystal
		float rWind  = 0.50f, gWind  = 0.85f, bWind  = 1.0f;   // Arctic vortex wind

		if (customColor != -1) {
			float cr = ((customColor >> 16) & 0xFF) / 255.0f;
			float cg = ((customColor >> 8) & 0xFF) / 255.0f;
			float cb = (customColor & 0xFF) / 255.0f;
			rFrost = cr * 0.9f; gFrost = cg * 0.9f; bFrost = cb * 0.9f;
			rStar  = Math.min(1.0f, cr * 1.15f); gStar  = Math.min(1.0f, cg * 1.15f); bStar  = Math.min(1.0f, cb * 1.15f);
			rCore  = Math.min(1.0f, cr * 1.3f + 0.2f); gCore = Math.min(1.0f, cg * 1.3f + 0.2f); bCore = Math.min(1.0f, cb * 1.3f + 0.2f);
			rShard = cr; gShard = cg; bShard = cb;
			rWind  = cr * 0.85f; gWind = cg * 0.85f; bWind = cb * 0.85f;
		}

		Tessellator t = Tessellator.instance;

		// 3. Setup OpenGL state
		float prevBrightX = OpenGlHelper.lastBrightnessX;
		float prevBrightY = OpenGlHelper.lastBrightnessY;
		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_POLYGON_BIT);

		GL11.glDisable(GL11.GL_LIGHTING);
		RenderHelper.disableStandardItemLighting();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_CULL_FACE);

		// 4. Ground Permafrost Mandala (hexagonal ice cracks and cryogenic glyphs at y + 0.03)
		this.bindTexture(texBlizzardGround);
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, (float) y + 0.03f, (float) z);
		GL11.glRotatef(-age * 0.20f, 0.0f, 1.0f, 0.0f);
		float groundPulse = 0.98f + 0.02f * (float) Math.sin(age * 0.15f);
		float gScale = baseRadius * globalScale * groundPulse;
		GL11.glColor4f(rFrost, gFrost, bFrost, 0.60f * masterAlpha);
		renderHorizontalQuad(t, -gScale, gScale, -gScale, gScale, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
		GL11.glPopMatrix();

		float skyHeight = 11.0f;

		// 5. 3D Volumetric Blizzard Vortex Funnel (swirling conical cyclone of arctic winds)
		this.bindTexture(texBlizzardVortex);

		// Vortex Layer 1: Inner swirling funnel (accelerated downward spiral)
		GL11.glColor4f(rWind, gWind, bWind, 0.42f * masterAlpha);
		float vScroll1 = -age * 0.08f;
		float uScroll1 = age * 0.035f;
		renderVortexCylinder(t, x, y + 0.05, y + skyHeight, z,
				baseRadius * 0.65f * globalScale,
				baseRadius * 0.90f * globalScale,
				18, uScroll1, vScroll1, 2.5f);

		// Vortex Layer 2: Outer counter-swirling howling winds (broader boundary)
		GL11.glColor4f(rStar, gStar, bStar, 0.28f * masterAlpha);
		float vScroll2 = -age * 0.05f;
		float uScroll2 = -age * 0.025f;
		renderVortexCylinder(t, x, y + 0.05, y + skyHeight, z,
				baseRadius * 0.85f * globalScale,
				baseRadius * 1.05f * globalScale,
				18, uScroll2, vScroll2, 2.0f);

		// 6. Descending Crystalline Ice Spears / Stalactites & Ground Snowflake Shatter
		int numShards = 5;
		for (int i = 0; i < numShards; i++) {
			float pTime = (age * 0.35f + i * 1.82f);
			float cycle = pTime % 4.5f;
			if (cycle < 3.5f) {
				float strikeProgress = cycle / 3.5f;
				float shardAlpha = (float) Math.sin(strikeProgress * Math.PI) * masterAlpha;

				int cycleIdx = (int) (pTime / 4.5f);
				double seedAngle = ((effect.getEntityId() * 53 + i * 97 + cycleIdx * 139) % 360);
				double pRad = Math.toRadians(seedAngle);
				double pDist = (((effect.getEntityId() * 23 + i * 43 + cycleIdx * 71) % 80) / 100.0) * (radius * 0.80);

				double px = x + pDist * Math.cos(pRad);
				double pz = z + pDist * Math.sin(pRad);

				// Fall with acceleration (gravity arc)
				float fallProgress = strikeProgress * strikeProgress;
				double shardY = (y + skyHeight) - (skyHeight * fallProgress);

				// Render 3D faceted hexagonal ice spear
				this.bindTexture(texBlizzardShard);
				GL11.glColor4f(rShard, gShard, bShard, shardAlpha * 0.95f);
				renderIceSpear(t, px, shardY, pz, 2.6f, 0.65f);

				// Ground Snowflake Shatter Ring on impact
				float shatterThreshold = 0.55f;
				if (strikeProgress > shatterThreshold) {
					float shatterNorm = (strikeProgress - shatterThreshold) / (1.0f - shatterThreshold);
					float shatterSize = 0.4f + shatterNorm * 1.8f;
					float shatterAlpha = (1.0f - shatterNorm) * shardAlpha;
					if (shatterAlpha > 0.02f) {
						this.bindTexture(texBlizzardShatter);
						GL11.glColor4f(rFrost, gFrost, bFrost, shatterAlpha * 0.80f);
						renderHorizontalQuad(t, (float) (px - shatterSize), (float) (px + shatterSize),
								(float) (pz - shatterSize), (float) (pz + shatterSize),
								(float) (y + 0.06f), 0.0f, 1.0f, 0.0f, 1.0f);
					}
				}
			}
		}

		// 7. Sky Hexagonal Frost Array (floating at y + skyHeight)
		GL11.glPushMatrix();
		float hover = 0.18f * (float) Math.sin(age * 0.08f);
		GL11.glTranslatef((float) x, (float) (y + skyHeight + hover), (float) z);

		// Layer A: Outer Hexagonal Dendrite Dial (rotates clockwise)
		this.bindTexture(texBlizzardOuter);
		GL11.glPushMatrix();
		GL11.glRotatef(age * 0.40f, 0.0f, 1.0f, 0.0f);
		float outerScale = baseRadius * globalScale;
		GL11.glColor4f(rFrost, gFrost, bFrost, 0.90f * masterAlpha);
		renderHorizontalQuad(t, -outerScale, outerScale, -outerScale, outerScale, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
		GL11.glPopMatrix();

		// Layer B: Middle Double 6-Pointed Hexagram Ice Star (rotates counter-clockwise)
		this.bindTexture(texBlizzardMiddle);
		GL11.glPushMatrix();
		GL11.glRotatef(-age * 0.70f, 0.0f, 1.0f, 0.0f);
		float midScale = baseRadius * globalScale * 0.88f;
		GL11.glColor4f(rStar, gStar, bStar, 0.95f * masterAlpha);
		renderHorizontalQuad(t, -midScale, midScale, -midScale, midScale, 0.02f, 0.0f, 1.0f, 0.0f, 1.0f);
		GL11.glPopMatrix();

		// Layer C: Core Frost Lotus & AM2 Ice Affinity Rune (pulsates in cold light)
		this.bindTexture(texBlizzardCore);
		GL11.glPushMatrix();
		GL11.glRotatef(age * 0.50f, 0.0f, 1.0f, 0.0f);
		float corePulse = 1.0f + 0.07f * (float) Math.sin(age * 0.20f);
		float coreScale = baseRadius * globalScale * 0.50f * corePulse;
		GL11.glColor4f(rCore, gCore, bCore, 1.0f * masterAlpha);
		renderHorizontalQuad(t, -coreScale, coreScale, -coreScale, coreScale, 0.04f, 0.0f, 1.0f, 0.0f, 1.0f);
		GL11.glPopMatrix();

		// Layer D: Vector Cryogenic Hexagonal Lattice Rings (3D tilted mathematical polygons)
		GL11.glDisable(GL11.GL_TEXTURE_2D);

		// Outer tilted 6-sided crystal frame
		GL11.glPushMatrix();
		GL11.glRotatef(age * 0.90f, 0.0f, 1.0f, 0.0f);
		GL11.glRotatef(22.0f, 1.0f, 0.0f, 0.0f);
		GL11.glColor4f(rStar, gStar, bStar, 0.70f * masterAlpha);
		renderSmoothVectorPolygon(outerScale * 0.96f, 6, 2.0f);
		GL11.glPopMatrix();

		// Secondary tilted 6-sided frame (offset by 30 degrees to form a 12-star gyroscopic cage)
		GL11.glPushMatrix();
		GL11.glRotatef(-age * 1.10f, 0.0f, 1.0f, 0.0f);
		GL11.glRotatef(30.0f, 0.0f, 1.0f, 0.0f);
		GL11.glRotatef(-22.0f, 0.0f, 0.0f, 1.0f);
		GL11.glColor4f(rFrost, gFrost, bFrost, 0.60f * masterAlpha);
		renderSmoothVectorPolygon(outerScale * 1.02f, 6, 1.8f);
		GL11.glPopMatrix();

		// Outermost 12-sided dodecagonal binding frame
		GL11.glPushMatrix();
		GL11.glRotatef(age * 0.60f, 0.0f, 1.0f, 0.0f);
		GL11.glColor4f(rCore, gCore, bCore, 0.50f * masterAlpha);
		renderSmoothVectorPolygon(outerScale * 1.08f, 12, 1.2f);
		GL11.glPopMatrix();

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glPopMatrix();

		// 8. Restore OpenGL state
		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_LIGHTING);
		RenderHelper.enableStandardItemLighting();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBrightX, prevBrightY);
		GL11.glPopAttrib();
		GL11.glPopMatrix();
	}

	private void renderHorizontalQuad(Tessellator t, float minX, float maxX, float minZ, float maxZ, float y,
	                                   float minU, float maxU, float minV, float maxV) {
		t.startDrawingQuads();
		t.addVertexWithUV(minX, y, maxZ, minU, maxV);
		t.addVertexWithUV(maxX, y, maxZ, maxU, maxV);
		t.addVertexWithUV(maxX, y, minZ, maxU, minV);
		t.addVertexWithUV(minX, y, minZ, minU, minV);
		t.draw();
	}

	private void renderBeam(Tessellator t, double x, double yStart, double yEnd, double z, float width, float vOffset) {
		float hw = width * 0.5f;

		// Plane 1 (X axis)
		t.startDrawingQuads();
		t.addVertexWithUV(x - hw, yStart, z, 0.0, 0.0 + vOffset);
		t.addVertexWithUV(x + hw, yStart, z, 1.0, 0.0 + vOffset);
		t.addVertexWithUV(x + hw, yEnd, z, 1.0, 1.0 + vOffset);
		t.addVertexWithUV(x - hw, yEnd, z, 0.0, 1.0 + vOffset);
		t.draw();

		// Plane 2 (Z axis)
		t.startDrawingQuads();
		t.addVertexWithUV(x, yStart, z - hw, 0.0, 0.0 + vOffset);
		t.addVertexWithUV(x, yStart, z + hw, 1.0, 0.0 + vOffset);
		t.addVertexWithUV(x, yEnd, z + hw, 1.0, 1.0 + vOffset);
		t.addVertexWithUV(x, yEnd, z - hw, 0.0, 1.0 + vOffset);
		t.draw();
	}

	private void renderIceSpear(Tessellator t, double px, double py, double pz, float length, float width) {
		float hw = width * 0.5f;
		float hl = length * 0.5f;

		// 3 intersecting planes at 60 degree intervals (hexagonal crystal symmetry)
		for (int k = 0; k < 3; k++) {
			double angle = Math.toRadians(k * 60.0);
			double dx = Math.cos(angle) * hw;
			double dz = Math.sin(angle) * hw;

			t.startDrawingQuads();
			t.addVertexWithUV(px - dx, py - hl, pz - dz, 0.0, 1.0);
			t.addVertexWithUV(px + dx, py - hl, pz + dz, 1.0, 1.0);
			t.addVertexWithUV(px + dx, py + hl, pz + dz, 1.0, 0.0);
			t.addVertexWithUV(px - dx, py + hl, pz - dz, 0.0, 0.0);
			t.draw();
		}
	}

	private void renderVortexCylinder(Tessellator t, double x, double yBottom, double yTop, double z,
	                                  float radiusBottom, float radiusTop, int segments,
	                                  float uOffset, float vOffset, float vScale) {
		t.startDrawingQuads();
		for (int i = 0; i < segments; i++) {
			double angle1 = 2.0 * Math.PI * i / segments;
			double angle2 = 2.0 * Math.PI * (i + 1) / segments;

			float u1 = (float) i / segments + uOffset;
			float u2 = (float) (i + 1) / segments + uOffset;

			double cos1 = Math.cos(angle1);
			double sin1 = Math.sin(angle1);
			double cos2 = Math.cos(angle2);
			double sin2 = Math.sin(angle2);

			t.addVertexWithUV(x + cos1 * radiusBottom, yBottom, z + sin1 * radiusBottom, u1, vOffset + vScale);
			t.addVertexWithUV(x + cos2 * radiusBottom, yBottom, z + sin2 * radiusBottom, u2, vOffset + vScale);
			t.addVertexWithUV(x + cos2 * radiusTop, yTop, z + sin2 * radiusTop, u2, vOffset);
			t.addVertexWithUV(x + cos1 * radiusTop, yTop, z + sin1 * radiusTop, u1, vOffset);
		}
		t.draw();
	}

	private void renderSmoothVectorRing(float ringRadius, int segments, float lineWidth) {
		GL11.glLineWidth(lineWidth);
		GL11.glBegin(GL11.GL_LINE_LOOP);
		for (int i = 0; i < segments; i++) {
			double angle = 2.0 * Math.PI * i / segments;
			GL11.glVertex3d(ringRadius * Math.cos(angle), 0, ringRadius * Math.sin(angle));
		}
		GL11.glEnd();
	}

	private void renderSmoothVectorPolygon(float ringRadius, int sides, float lineWidth) {
		GL11.glLineWidth(lineWidth);
		GL11.glBegin(GL11.GL_LINE_LOOP);
		for (int i = 0; i < sides; i++) {
			double angle = 2.0 * Math.PI * i / sides;
			GL11.glVertex3d(ringRadius * Math.cos(angle), 0, ringRadius * Math.sin(angle));
		}
		GL11.glEnd();
	}

	private void renderStarfallImpact(EntitySpellEffect effect, double x, double y, double z, float partialTicks) {
		float age = effect.ticksExisted + partialTicks;
		int maxTicks = effect.getTicksToExist();
		if (maxTicks <= 0) maxTicks = 16;

		float progress = Math.min(1.0f, age / (float) maxTicks);
		float fadeAlpha = (float) Math.pow(1.0f - progress, 1.3);
		if (fadeAlpha <= 0.005f) return;

		float scale = effect.getRadius();
		if (scale <= 0.1f) scale = 1.0f;

		int customColor = effect.getSpellColor();
		float rStar = 0.70f, gStar = 0.90f, bStar = 1.0f;
		float rPillar = 0.60f, gPillar = 0.85f, bPillar = 1.0f;
		float rShock = 0.80f, gShock = 0.95f, bShock = 1.0f;

		if (customColor != -1) {
			float cr = ((customColor >> 16) & 0xFF) / 255.0f;
			float cg = ((customColor >> 8) & 0xFF) / 255.0f;
			float cb = (customColor & 0xFF) / 255.0f;
			rStar = cr; gStar = cg; bStar = cb;
			rPillar = cr; gPillar = cg; bPillar = cb;
			rShock = Math.min(1.0f, cr * 1.15f); gShock = Math.min(1.0f, cg * 1.15f); bShock = Math.min(1.0f, cb * 1.15f);
		}

		Tessellator t = Tessellator.instance;

		float prevBrightX = OpenGlHelper.lastBrightnessX;
		float prevBrightY = OpenGlHelper.lastBrightnessY;
		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_POLYGON_BIT);

		GL11.glDisable(GL11.GL_LIGHTING);
		RenderHelper.disableStandardItemLighting();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_CULL_FACE);

		// 1. Ground Scorched Celestial Reticle (dimming slowly)
		this.bindTexture(texStarfallGround);
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, (float) y + 0.03f, (float) z);
		GL11.glRotatef(age * 0.8f, 0.0f, 1.0f, 0.0f);
		float gRadius = 3.8f * Math.min(2.8f, 0.75f + scale * 0.45f);
		GL11.glColor4f(rStar, gStar, bStar, 0.65f * fadeAlpha);
		renderHorizontalQuad(t, -gRadius, gRadius, -gRadius, gRadius, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
		GL11.glPopMatrix();

		// 2. Expanding Celestial Supernova Blastwave Ring
		this.bindTexture(texStarfallShockwave);
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, (float) y + 0.05f, (float) z);
		float shockExpansion = (float) Math.sin(progress * Math.PI * 0.5);
		float shockRadius = (0.8f + shockExpansion * 8.5f) * (0.75f + scale * 0.40f);
		GL11.glColor4f(rShock, gShock, bShock, 0.90f * fadeAlpha);
		renderHorizontalQuad(t, -shockRadius, shockRadius, -shockRadius, shockRadius, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);

		// Tier 4 Secondary Accretion Shockwave
		if (scale >= 4.0f){
			float shockRadius2 = shockRadius * 0.65f;
			GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.75f * fadeAlpha);
			renderHorizontalQuad(t, -shockRadius2, shockRadius2, -shockRadius2, shockRadius2, 0.02f, 0.0f, 1.0f, 0.0f, 1.0f);
		}
		GL11.glPopMatrix();

		// 3. Ascending Pillar of Holy Starlight (shoots straight up)
		float pillarDuration = 0.70f;
		if (progress < pillarDuration) {
			float pNorm = progress / pillarDuration;
			float pAlpha = (float) Math.sin((1.0f - pNorm) * Math.PI * 0.5);
			float pWidth = 3.2f * Math.min(4.5f, scale) * (1.0f - pNorm * 0.35f);
			float pillarHeight = 36.0f + (scale - 1.0f) * 10.0f;

			this.bindTexture(texStarfallPillar);
			GL11.glColor4f(rPillar, gPillar, bPillar, pAlpha * 0.92f);
			renderVerticalQuadCross3(t, x, y, y + pillarHeight, z, pWidth, 1.0f, 0.0f);
		}

		// 4. Initial Supernova Diamond Flash (bursts and vanishes in first 5 ticks)
		float flashDuration = 0.30f;
		if (progress < flashDuration) {
			float fNorm = progress / flashDuration;
			float fAlpha = 1.0f - fNorm;
			float fRadius = (1.2f + fNorm * 3.5f) * scale;

			this.bindTexture(texStarfallCore);
			GL11.glPushMatrix();
			GL11.glTranslatef((float) x, (float) y + 1.2f * scale, (float) z);
			GL11.glRotatef(age * 5.0f, 0.0f, 1.0f, 0.0f);
			GL11.glColor4f(1.0f, 1.0f, 1.0f, fAlpha);
			renderVerticalQuadCross3(t, 0.0, -fRadius, fRadius, 0.0, fRadius * 2.0f, 0.0f, 1.0f);
			renderHorizontalQuad(t, -fRadius, fRadius, -fRadius, fRadius, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
			GL11.glPopMatrix();
		}

		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_LIGHTING);
		RenderHelper.enableStandardItemLighting();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBrightX, prevBrightY);
		GL11.glPopAttrib();
		GL11.glPopMatrix();
	}

	private void renderVerticalQuadCross3(Tessellator t, double x, double yStart, double yEnd, double z,
	                                      float width, float vBottom, float vTop) {
		float hw = width * 0.5f;
		for (int k = 0; k < 3; k++) {
			double angle = Math.toRadians(k * 60.0);
			double dx = Math.cos(angle) * hw;
			double dz = Math.sin(angle) * hw;

			t.startDrawingQuads();
			t.addVertexWithUV(x - dx, yStart, z - dz, 0.0, vBottom);
			t.addVertexWithUV(x + dx, yStart, z + dz, 1.0, vBottom);
			t.addVertexWithUV(x + dx, yEnd, z + dz, 1.0, vTop);
			t.addVertexWithUV(x - dx, yEnd, z - dz, 0.0, vTop);
			t.draw();
		}
	}

	private void renderGrowMandala(EntitySpellEffect effect, double x, double y, double z, float partialTicks) {
		float age = effect.ticksExisted + partialTicks;
		int maxTicks = effect.getTicksToExist();
		if (maxTicks <= 0) maxTicks = 32;

		// 1. Smooth intro & outro easing
		float intro = Math.min(1.0f, age / 7.0f);
		intro = (float) Math.sin(intro * Math.PI / 2.0); // smooth ease-in bloom

		float remaining = maxTicks - age;
		float outro = 1.0f;
		if (remaining < 10.0f && remaining >= 0.0f) {
			outro = (float) Math.sin(Math.max(0.0f, remaining / 10.0f) * Math.PI / 2.0);
		} else if (remaining < 0.0f) {
			outro = Math.max(0.0f, 1.0f + (remaining / 6.0f));
		}

		float globalAlpha = intro * outro;
		if (globalAlpha <= 0.005f) return;

		int tier = effect.getGrowTier();
		float tierScale = effect.getGrowVisualScale();

		float baseRadius = effect.getRadius();
		if (baseRadius <= 0.1f) baseRadius = 2.8f;
		float radius = baseRadius * tierScale * (0.85f + 0.15f * intro);

		// Colors: default light spring green / mint white
		int customColor = effect.getSpellColor();
		float rLight = 0.40f, gLight = 1.0f, bLight = 0.55f;
		float rLeaf = 0.25f, gLeaf = 0.95f, bLeaf = 0.45f;
		float rCore = 0.85f, gCore = 1.0f, bCore = 0.88f;

		if (customColor != -1) {
			float cr = ((customColor >> 16) & 0xFF) / 255.0f;
			float cg = ((customColor >> 8) & 0xFF) / 255.0f;
			float cb = (customColor & 0xFF) / 255.0f;
			rLeaf = cr; gLeaf = cg; bLeaf = cb;
			rLight = Math.min(1.0f, cr * 1.25f); gLight = Math.min(1.0f, cg * 1.25f); bLight = Math.min(1.0f, cb * 1.25f);
			rCore = Math.min(1.0f, cr * 1.4f + 0.2f); gCore = Math.min(1.0f, cg * 1.4f + 0.2f); bCore = Math.min(1.0f, cb * 1.4f + 0.2f);
		}

		Tessellator t = Tessellator.instance;

		float prevBrightX = OpenGlHelper.lastBrightnessX;
		float prevBrightY = OpenGlHelper.lastBrightnessY;
		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_POLYGON_BIT);

		GL11.glDisable(GL11.GL_LIGHTING);
		RenderHelper.disableStandardItemLighting();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_CULL_FACE);

		float rot = age * 1.8f;

		// 1. Primary Sacred Botanical Mandala
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, (float) y + 0.04f, (float) z);
		GL11.glRotatef(rot, 0.0f, 1.0f, 0.0f);
		this.bindTexture(texGrowGround);
		GL11.glColor4f(rLight, gLight, bLight, globalAlpha * 0.95f);
		renderHorizontalQuad(t, -radius, radius, -radius, radius, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
		GL11.glPopMatrix();

		// 2. Inner Lotus Core (counter-rotating with bright mint core)
		float innerCoreRadius = radius * (tier >= 3 ? 0.60f : 0.52f);
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, (float) y + 0.05f, (float) z);
		GL11.glRotatef(-rot * 0.7f, 0.0f, 1.0f, 0.0f);
		this.bindTexture(texGrowGround);
		GL11.glColor4f(rCore, gCore, bCore, globalAlpha * (tier >= 3 ? 0.95f : 0.85f));
		renderHorizontalQuad(t, -innerCoreRadius, innerCoreRadius, -innerCoreRadius, innerCoreRadius, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
		GL11.glPopMatrix();

		// 3. Expanding Floral Life Ripple
		float pulseDuration = 22.0f;
		if (age < pulseDuration) {
			float pNorm = age / pulseDuration;
			float pScale = (0.25f + 1.20f * (float) Math.sqrt(pNorm)) * radius;
			float pAlpha = (float) Math.sin(pNorm * Math.PI) * (1.0f - pNorm * 0.35f) * globalAlpha;

			GL11.glPushMatrix();
			GL11.glTranslatef((float) x, (float) y + 0.06f, (float) z);
			GL11.glRotatef(-age * 2.5f, 0.0f, 1.0f, 0.0f);
			this.bindTexture(texGrowPulse);
			GL11.glColor4f(rLight, gLight, bLight, pAlpha * 0.90f);
			renderHorizontalQuad(t, -pScale, pScale, -pScale, pScale, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
			GL11.glPopMatrix();
		}

		// 4. Concentric Sacred Geometry Vector Rings & Sacred Stars based on Tier
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, (float) y + 0.05f, (float) z);

		// Outer boundary circle (All Tiers)
		float outerWidth = tier >= 4 ? 3.0f : (tier >= 3 ? 2.5f : 2.0f);
		GL11.glColor4f(rLight, gLight, bLight, globalAlpha * 0.80f);
		renderSmoothVectorRing(radius * 0.98f, 54, outerWidth);

		// Rotating star polygon (Tier 1: 8-point, Tier 2: 12-point, Tier 3+: 16-point)
		int sides = tier >= 3 ? 16 : (tier == 2 ? 12 : 8);
		GL11.glPushMatrix();
		GL11.glRotatef(rot * 0.5f, 0.0f, 1.0f, 0.0f);
		GL11.glColor4f(rCore, gCore, bCore, globalAlpha * 0.65f);
		renderSmoothVectorPolygon(radius * 0.72f, sides, 1.8f);
		GL11.glPopMatrix();

		// Tier 2+: Second Concentric Ring & Secondary Counter-Rotating Star
		if (tier >= 2) {
			GL11.glColor4f(rLight, gLight, bLight, globalAlpha * 0.70f);
			renderSmoothVectorRing(radius * 0.84f, 48, 1.9f);

			GL11.glPushMatrix();
			GL11.glRotatef(-rot * 0.4f, 0.0f, 1.0f, 0.0f);
			GL11.glColor4f(rLeaf, gLeaf, bLeaf, globalAlpha * 0.55f);
			renderSmoothVectorPolygon(radius * 0.58f, 8, 1.6f);
			GL11.glPopMatrix();
		}

		// Tier 3+: Third Concentric Ring & Sacred Hexagram Flower of Life
		if (tier >= 3) {
			GL11.glColor4f(rCore, gCore, bCore, globalAlpha * 0.75f);
			renderSmoothVectorRing(radius * 0.68f, 42, 2.0f);

			GL11.glPushMatrix();
			GL11.glRotatef(rot * 0.8f, 0.0f, 1.0f, 0.0f);
			GL11.glColor4f(rLight, gLight, bLight, globalAlpha * 0.60f);
			renderSmoothVectorPolygon(radius * 0.44f, 6, 2.0f);
			GL11.glPopMatrix();
		}

		// Tier 4: Fourth Concentric Ring & Sacred Inner Core Star
		if (tier >= 4) {
			GL11.glColor4f(rLight, gLight, bLight, globalAlpha * 0.85f);
			renderSmoothVectorRing(radius * 0.50f, 36, 2.2f);

			GL11.glPushMatrix();
			GL11.glRotatef(-rot * 1.0f, 0.0f, 1.0f, 0.0f);
			GL11.glColor4f(rCore, gCore, bCore, globalAlpha * 0.75f);
			renderSmoothVectorPolygon(radius * 0.32f, 16, 2.0f);
			GL11.glPopMatrix();
		}

		GL11.glPopMatrix();
		GL11.glEnable(GL11.GL_TEXTURE_2D);

		// Tier 4: Ascending Pillar of Holy Verdant Starlight (Nature reaching for the heavens)
		if (tier >= 4) {
			float pAlpha = (float) Math.sin(intro * Math.PI * 0.5) * outro;
			float pWidth = 3.4f * (0.85f + 0.15f * intro);
			float pillarHeight = 30.0f + tierScale * 3.5f;

			this.bindTexture(texStarfallPillar);
			GL11.glColor4f(rLight, gLight, bLight, pAlpha * 0.88f);
			renderVerticalQuadCross3(t, x, y + 0.05f, y + pillarHeight, z, pWidth, 1.0f, 0.0f);
		}

		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBrightX, prevBrightY);
		GL11.glPopAttrib();
		GL11.glPopMatrix();
	}

	private void renderExplosionImpact(EntitySpellEffect effect, double x, double y, double z, float partialTicks) {
		float age = effect.ticksExisted + partialTicks;
		int maxTicks = effect.getTicksToExist();
		if (maxTicks <= 0) maxTicks = 20;

		float progress = Math.min(1.0f, age / (float) maxTicks);
		float fadeAlpha = (float) Math.pow(1.0f - progress, 1.4);
		if (fadeAlpha <= 0.005f) return;

		float scale = effect.getRadius();
		if (scale <= 0.1f) scale = 1.0f;
		int count = effect.getMergeCount();
		int tier = count >= 25 ? 4 : (count >= 10 ? 3 : (count >= 5 ? 2 : 1));

		// Palette: default solar blast orange & gold
		int customColor = effect.getSpellColor();
		float rLight = 1.0f, gLight = 0.58f, bLight = 0.15f;
		float rCore = 1.0f, gCore = 0.95f, bCore = 0.80f;
		float rEdge = 0.95f, gEdge = 0.22f, bEdge = 0.05f;

		if (customColor != -1) {
			float cr = ((customColor >> 16) & 0xFF) / 255.0f;
			float cg = ((customColor >> 8) & 0xFF) / 255.0f;
			float cb = (customColor & 0xFF) / 255.0f;
			rLight = cr; gLight = cg; bLight = cb;
			rEdge = Math.min(1.0f, cr * 0.8f); gEdge = Math.min(1.0f, cg * 0.8f); bEdge = Math.min(1.0f, cb * 0.8f);
			rCore = Math.min(1.0f, cr * 1.4f + 0.2f); gCore = Math.min(1.0f, cg * 1.4f + 0.2f); bCore = Math.min(1.0f, cb * 1.4f + 0.2f);
		}

		Tessellator t = Tessellator.instance;

		float prevBrightX = OpenGlHelper.lastBrightnessX;
		float prevBrightY = OpenGlHelper.lastBrightnessY;
		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_POLYGON_BIT);

		GL11.glDisable(GL11.GL_LIGHTING);
		RenderHelper.disableStandardItemLighting();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_CULL_FACE);

		// 1. Ground Blast Rune & Scorched Crater
		float groundRadius = (2.6f + progress * 0.7f) * scale;
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, (float) y + 0.03f, (float) z);
		GL11.glRotatef(age * 3.5f, 0.0f, 1.0f, 0.0f);
		this.bindTexture(texExplosionGround);
		GL11.glColor4f(rLight, gLight, bLight, fadeAlpha * 0.95f);
		renderHorizontalQuad(t, -groundRadius, groundRadius, -groundRadius, groundRadius, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
		GL11.glPopMatrix();

		// 2. Rapidly Expanding Fiery Shockwave
		float shockRadius = (0.4f + 3.6f * (float) Math.sqrt(progress)) * scale;
		float shockAlpha = (float) Math.sin((1.0f - progress) * Math.PI * 0.5) * fadeAlpha;

		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, (float) y + 0.05f, (float) z);
		this.bindTexture(texExplosionShockwave);
		GL11.glColor4f(rLight, gLight, bLight, shockAlpha * 0.92f);
		renderHorizontalQuad(t, -shockRadius, shockRadius, -shockRadius, shockRadius, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);

		// Secondary harmonic wave for Tier 2+
		if (tier >= 2) {
			float shockRadius2 = shockRadius * 0.75f;
			GL11.glColor4f(rCore, gCore, bCore, shockAlpha * 0.70f);
			renderHorizontalQuad(t, -shockRadius2, shockRadius2, -shockRadius2, shockRadius2, 0.01f, 0.0f, 1.0f, 0.0f, 1.0f);
		}
		GL11.glPopMatrix();

		// 3. Supernova Flash Core (White-hot fireball flash during first 7 ticks)
		float flashDuration = 0.35f;
		if (progress < flashDuration) {
			float fNorm = progress / flashDuration;
			float fAlpha = (1.0f - fNorm);
			float fRadius = (1.4f + fNorm * 3.2f) * scale;

			this.bindTexture(texStarfallCore);
			GL11.glPushMatrix();
			GL11.glTranslatef((float) x, (float) y + 0.8f * scale, (float) z);
			GL11.glRotatef(age * 8.0f, 0.0f, 1.0f, 0.0f);
			GL11.glColor4f(rCore, gCore, bCore, fAlpha);
			renderVerticalQuadCross3(t, 0.0, -fRadius, fRadius, 0.0, fRadius * 2.0f, 0.0f, 1.0f);
			renderHorizontalQuad(t, -fRadius, fRadius, -fRadius, fRadius, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
			GL11.glPopMatrix();
		}

		// 4. Concentric Flaming Vector Blast Rings
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, (float) y + 0.04f, (float) z);

		// Outer shock ring (All Tiers)
		float outerWidth = tier >= 4 ? 3.2f : (tier >= 3 ? 2.6f : 2.0f);
		GL11.glColor4f(rLight, gLight, bLight, shockAlpha * 0.85f);
		renderSmoothVectorRing(shockRadius * 0.96f, 48, outerWidth);

		// Rotating Star Polygon (Tier 1: 8-pt, Tier 2: 12-pt, Tier 3+: 16-pt)
		int sides = tier >= 3 ? 16 : (tier == 2 ? 12 : 8);
		GL11.glPushMatrix();
		GL11.glRotatef(age * 4.0f, 0.0f, 1.0f, 0.0f);
		GL11.glColor4f(rCore, gCore, bCore, fadeAlpha * 0.70f);
		renderSmoothVectorPolygon(groundRadius * 0.75f, sides, 2.0f);
		GL11.glPopMatrix();

		// Tier 2+: 2nd Concentric Ring & Secondary Star
		if (tier >= 2) {
			GL11.glColor4f(rEdge, gEdge, bEdge, shockAlpha * 0.75f);
			renderSmoothVectorRing(shockRadius * 0.78f, 42, 2.0f);

			GL11.glPushMatrix();
			GL11.glRotatef(-age * 3.0f, 0.0f, 1.0f, 0.0f);
			GL11.glColor4f(rLight, gLight, bLight, fadeAlpha * 0.60f);
		renderSmoothVectorPolygon(groundRadius * 0.52f, 8, 1.8f);
			GL11.glPopMatrix();
		}

		// Tier 3+: 3rd Concentric Ring & Molten Solar Corona
		if (tier >= 3) {
			GL11.glColor4f(rCore, gCore, bCore, shockAlpha * 0.80f);
			renderSmoothVectorRing(shockRadius * 0.60f, 36, 2.2f);

			GL11.glPushMatrix();
			GL11.glRotatef(age * 5.5f, 0.0f, 1.0f, 0.0f);
			GL11.glColor4f(rCore, gCore, bCore, fadeAlpha * 0.65f);
		renderSmoothVectorPolygon(groundRadius * 0.36f, 12, 2.0f);
			GL11.glPopMatrix();
		}

		// Tier 4: 4th Concentric Ring & Primordial Solar Seal
		if (tier >= 4) {
			GL11.glColor4f(rLight, gLight, bLight, shockAlpha * 0.90f);
			renderSmoothVectorRing(shockRadius * 0.44f, 32, 2.4f);

			GL11.glPushMatrix();
			GL11.glRotatef(-age * 6.0f, 0.0f, 1.0f, 0.0f);
			GL11.glColor4f(rCore, gCore, bCore, fadeAlpha * 0.80f);
		renderSmoothVectorPolygon(groundRadius * 0.24f, 16, 2.2f);
			GL11.glPopMatrix();
		}

		GL11.glPopMatrix();
		GL11.glEnable(GL11.GL_TEXTURE_2D);

		// 5. Tier 4: Ascending Column of Holy Blast Fire / Eruption Pillar
		if (tier >= 4) {
			float pDuration = 0.75f;
			if (progress < pDuration) {
				float pNorm = progress / pDuration;
				float pAlpha = (float) Math.sin((1.0f - pNorm) * Math.PI * 0.5);
				float pWidth = 4.2f * (1.0f - pNorm * 0.35f);
				float pillarHeight = 34.0f + scale * 4.0f;

				this.bindTexture(texStarfallPillar);
				GL11.glColor4f(rLight, gLight, bLight, pAlpha * 0.92f);
				renderVerticalQuadCross3(t, x, y + 0.05f, y + pillarHeight, z, pWidth, 1.0f, 0.0f);
			}
		}

		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBrightX, prevBrightY);
		GL11.glPopAttrib();
		GL11.glPopMatrix();
	}

	private static final int MAX_LIGHTNING_IMPACT_CACHE = 32;
	private static final java.util.LinkedHashMap<Long, LightningImpactData> LIGHTNING_IMPACT_CACHE =
			new java.util.LinkedHashMap<Long, LightningImpactData>(40, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(java.util.Map.Entry<Long, LightningImpactData> eldest) {
					return size() > MAX_LIGHTNING_IMPACT_CACHE;
				}
			};
	private static final float[] LIGHTNING_PALETTE = new float[12];
	private static final int HELIX_STEPS = 22;
	private static final double[] HELIX_X = new double[HELIX_STEPS + 1];
	private static final double[] HELIX_Y = new double[HELIX_STEPS + 1];
	private static final double[] HELIX_Z = new double[HELIX_STEPS + 1];

	private static final class LightningImpactData {
		final int tier;
		final float stormHeight;
		final float cloudRadius;
		final LightningVFXHelper.BoltPath[] bolts;
		final float[] boltStartTick;
		final float[] boltDuration;
		final float[] boltWidthScale;
		final double[] boltEndX;
		final double[] boltEndZ;

		final double[] puffDist;
		final double[] puffAngle;
		final double[] puffYOffset;
		final float[] puffRadius;
		final float[] puffOrbitSpeed;
		final float[] puffRollSpeed;
		final float[] puffShade;

		final double[][] groundCrackX;
		final double[][] groundCrackY;
		final double[][] groundCrackZ;

		final double[] sparkVx;
		final double[] sparkVy;
		final double[] sparkVz;
		final float[] sparkSize;
		final float[] sparkRollSpeed;
		final float[] sparkStartTick;
		final boolean[] sparkDark;

		LightningImpactData(int entityId, int tier, float aoeRadius, int maxTicks) {
			this.tier = tier;
			this.stormHeight = tier == 1 ? 17.5f : (15.0f + Math.min(9.0f, aoeRadius * 0.38f));
			this.cloudRadius = tier == 1 ? 4.2f : Math.max(5.0f, aoeRadius * 0.95f);

			java.util.Random rnd = new java.util.Random(entityId * 31337L ^ (tier * 7919L));

			// 1. Pre-compute cloud puff layout (zero per-frame Random)
			int puffCount = tier >= 4 ? 22 : (tier == 3 ? 16 : (tier == 2 ? 12 : 4));
			this.puffDist = new double[puffCount];
			this.puffAngle = new double[puffCount];
			this.puffYOffset = new double[puffCount];
			this.puffRadius = new float[puffCount];
			this.puffOrbitSpeed = new float[puffCount];
			this.puffRollSpeed = new float[puffCount];
			this.puffShade = new float[puffCount];

			for (int i = 0; i < puffCount; i++) {
				if (tier == 1) {
					this.puffDist[i] = i == 0 ? 0.0 : (1.1 + rnd.nextDouble() * 1.9);
					this.puffAngle[i] = i * (Math.PI * 2.0 / Math.max(1, puffCount - 1)) + rnd.nextDouble() * 0.5;
					this.puffYOffset[i] = (i == 0 ? 0.4 : -0.3 + rnd.nextDouble() * 1.1);
					this.puffRadius[i] = 2.6f + rnd.nextFloat() * 1.2f;
				} else {
					// Ring-balanced distribution reduces center overdraw while preserving thick dark canopy
					double radialFrac = Math.sqrt((i + 0.5) / (double) puffCount);
					this.puffDist[i] = radialFrac * this.cloudRadius * 0.92;
					this.puffAngle[i] = i * 2.399963229728653 + rnd.nextDouble() * 0.35;
					this.puffYOffset[i] = ((i % 3) - 1.0) * 1.25 + (rnd.nextDouble() - 0.5) * 0.6;
					this.puffRadius[i] = (tier >= 4 ? 6.6f : (tier == 3 ? 5.6f : 4.6f)) + (i % 3) * 0.85f;
				}
				this.puffOrbitSpeed[i] = (i % 2 == 0 ? 1.0f : -1.0f) * (0.009f + rnd.nextFloat() * 0.008f);
				this.puffRollSpeed[i] = (i % 2 == 0 ? 1.0f : -1.0f) * (0.010f + rnd.nextFloat() * 0.010f);
				this.puffShade[i] = 0.78f + (i % 4) * 0.08f;
			}

			// 2. Pre-compute heavy-storm jagged bolts (hero bolt for Tier 1, rhythmic storm barrage for Tiers 2-4)
			int boltCount = tier >= 4 ? 34 : (tier == 3 ? 22 : (tier == 2 ? 12 : 1));
			this.bolts = new LightningVFXHelper.BoltPath[boltCount];
			this.boltStartTick = new float[boltCount];
			this.boltDuration = new float[boltCount];
			this.boltWidthScale = new float[boltCount];
			this.boltEndX = new double[boltCount];
			this.boltEndZ = new double[boltCount];

			float stormSpan = Math.max(8.0f, maxTicks * 0.78f);
			for (int i = 0; i < boltCount; i++) {
				if (tier == 1) {
					double sx = (rnd.nextDouble() - 0.5) * 2.2;
					double sz = (rnd.nextDouble() - 0.5) * 2.2;
					this.boltEndX[i] = 0.0;
					this.boltEndZ[i] = 0.0;
					this.boltStartTick[i] = 0.0f;
					this.boltDuration[i] = Math.min(14.0f, maxTicks * 0.72f);
					this.boltWidthScale[i] = 1.25f;
					this.bolts[i] = LightningVFXHelper.generateBolt(
							sx, this.stormHeight, sz,
							0.0, 0.05, 0.0,
							12, 2.6, 3, true, rnd
					);
				} else {
					float frac = (float) i / (float) boltCount;
					// Slightly organic storm burst rhythm instead of robotic uniform spacing
					this.boltStartTick[i] = Math.max(0.0f, frac * stormSpan + (rnd.nextFloat() - 0.5f) * 1.6f);
					this.boltDuration[i] = 7.5f + rnd.nextFloat() * 3.0f;
					double rLim = aoeRadius * (0.20 + 0.80 * Math.sqrt(frac));
					double dist = (0.18 + 0.82 * Math.sqrt(rnd.nextDouble())) * rLim;
					double ang = rnd.nextDouble() * Math.PI * 2.0;
					double ex = Math.cos(ang) * dist;
					double ez = Math.sin(ang) * dist;
					double sx = ex * 0.72 + (rnd.nextDouble() - 0.5) * 4.5;
					double sz = ez * 0.72 + (rnd.nextDouble() - 0.5) * 4.5;

					boolean major = (i % 3 == 0);
					this.boltEndX[i] = ex;
					this.boltEndZ[i] = ez;
					this.boltWidthScale[i] = major ? (tier >= 4 ? 1.18f : 1.05f) : 0.78f;
					this.bolts[i] = LightningVFXHelper.generateBolt(
							sx, this.stormHeight, sz,
							ex, 0.06, ez,
							major ? 11 : 8, major ? 2.8 : 2.1,
							major ? 3 : 1, major && tier >= 3, rnd
					);
				}
			}

			// 3. Pre-compute radial surface electric crack arcs around ground center
			int crackCount = tier >= 4 ? 7 : (tier >= 2 ? 5 : 3);
			this.groundCrackX = new double[crackCount][5];
			this.groundCrackY = new double[crackCount][5];
			this.groundCrackZ = new double[crackCount][5];
			double crackBaseLen = tier == 1 ? 2.6 : Math.min(9.5, aoeRadius * 0.62);
			for (int c = 0; c < crackCount; c++) {
				double ang = (c * Math.PI * 2.0 / crackCount) + (rnd.nextDouble() - 0.5) * 0.55;
				double len = crackBaseLen * (0.72 + rnd.nextDouble() * 0.42);
				double ex = Math.cos(ang) * len;
				double ez = Math.sin(ang) * len;
				this.groundCrackX[c][0] = 0.0; this.groundCrackY[c][0] = 0.10; this.groundCrackZ[c][0] = 0.0;
				this.groundCrackX[c][4] = ex; this.groundCrackY[c][4] = 0.10; this.groundCrackZ[c][4] = ez;
				for (int k = 1; k < 4; k++) {
					double f = k / 4.0;
					double env = Math.sin(f * Math.PI);
					this.groundCrackX[c][k] = ex * f + (rnd.nextDouble() - 0.5) * len * 0.24 * env;
					this.groundCrackY[c][k] = 0.12 + rnd.nextDouble() * 0.22 * env;
					this.groundCrackZ[c][k] = ez * f + (rnd.nextDouble() - 0.5) * len * 0.24 * env;
				}
			}

			// 4. Pre-compute ballistic sparks & charred debris kicked up at center/pillar base
			int sparkCount = tier >= 4 ? 26 : (tier == 3 ? 20 : (tier == 2 ? 14 : 9));
			this.sparkVx = new double[sparkCount];
			this.sparkVy = new double[sparkCount];
			this.sparkVz = new double[sparkCount];
			this.sparkSize = new float[sparkCount];
			this.sparkRollSpeed = new float[sparkCount];
			this.sparkStartTick = new float[sparkCount];
			this.sparkDark = new boolean[sparkCount];
			for (int s = 0; s < sparkCount; s++) {
				double ang = rnd.nextDouble() * Math.PI * 2.0;
				double hspd = (tier == 1 ? 0.22 : 0.30) + rnd.nextDouble() * (tier >= 3 ? 0.48 : 0.30);
				this.sparkVx[s] = Math.cos(ang) * hspd;
				this.sparkVy[s] = 0.34 + rnd.nextDouble() * (tier >= 3 ? 0.48 : 0.34);
				this.sparkVz[s] = Math.sin(ang) * hspd;
				this.sparkDark[s] = (s % 3 == 0);
				this.sparkSize[s] = this.sparkDark[s] ? (0.12f + rnd.nextFloat() * 0.10f) : (0.15f + rnd.nextFloat() * 0.18f);
				this.sparkRollSpeed[s] = (rnd.nextBoolean() ? 1.0f : -1.0f) * (0.28f + rnd.nextFloat() * 0.30f);
				this.sparkStartTick[s] = tier == 1 ? (rnd.nextFloat() * 1.2f) : (rnd.nextFloat() * maxTicks * 0.55f);
			}
		}
	}

	private static LightningImpactData getOrCreateLightningData(EntitySpellEffect effect, int tier, float aoeRadius, int maxTicks) {
		long key = (((long) effect.getEntityId()) << 32)
				^ ((long) tier << 24)
				^ ((long) maxTicks << 12)
				^ (Float.floatToIntBits(aoeRadius) & 0xFFFL);
		LightningImpactData data = LIGHTNING_IMPACT_CACHE.get(key);
		if (data == null) {
			data = new LightningImpactData(effect.getEntityId(), tier, aoeRadius, maxTicks);
			LIGHTNING_IMPACT_CACHE.put(key, data);
		}
		return data;
	}

	private void renderLightningImpact(EntitySpellEffect effect, double x, double y, double z, float partialTicks) {
		if (AMCore.config.NoGFX()) {
			return;
		}
		float age = effect.ticksExisted + partialTicks;
		int maxTicks = effect.getTicksToExist();
		if (maxTicks <= 0) maxTicks = 40;
		if (age > maxTicks + 1.0f) return;

		int count = effect.getMergeCount();
		int tier = count >= 30 ? 4 : (count >= 15 ? 3 : (count >= 5 ? 2 : 1));
		float aoeRadius = Math.max(3.5f, effect.getRadius());

		Minecraft mc = Minecraft.getMinecraft();
		int quality = (mc != null && mc.gameSettings != null) ? mc.gameSettings.particleSetting : 0;

		LightningImpactData data = getOrCreateLightningData(effect, tier, aoeRadius, maxTicks);
		LightningVFXHelper.resolvePalette(effect.getSpellColor(), LIGHTNING_PALETTE);

		float rDeep = LIGHTNING_PALETTE[0], gDeep = LIGHTNING_PALETTE[1], bDeep = LIGHTNING_PALETTE[2];
		float rLight = LIGHTNING_PALETTE[3], gLight = LIGHTNING_PALETTE[4], bLight = LIGHTNING_PALETTE[5];
		float rCore = LIGHTNING_PALETTE[6], gCore = LIGHTNING_PALETTE[7], bCore = LIGHTNING_PALETTE[8];
		float cloudR = LIGHTNING_PALETTE[9], cloudG = LIGHTNING_PALETTE[10], cloudB = LIGHTNING_PALETTE[11];

		// Smooth intro / outro easing
		float intro = Math.min(1.0f, age / (tier == 1 ? 2.5f : 7.0f));
		float remaining = maxTicks - age;
		float outroWindow = tier == 1 ? 6.0f : 11.0f;
		float outro = remaining <= 0.0f ? 0.0f : Math.min(1.0f, remaining / outroWindow);
		float stormFade = intro * outro;
		if (stormFade <= 0.005f) return;

		// Camera right/up basis for 100% batched billboard quads (zero glPushMatrix/glRotatef)
		double viewYaw = Math.toRadians(RenderManager.instance.playerViewY);
		double viewPitch = Math.toRadians(RenderManager.instance.playerViewX);
		double camRX = Math.cos(viewYaw);
		double camRZ = Math.sin(viewYaw);
		double camUX = -Math.sin(viewYaw) * Math.sin(viewPitch);
		double camUY = Math.cos(viewPitch);
		double camUZ = Math.cos(viewYaw) * Math.sin(viewPitch);

		double groundY = y + 0.05;
		double cloudBaseY = y + data.stormHeight;
		// Entity-local coords map to camera-relative via (local - (-x)) = local + x
		double localCamX = -x;
		double localCamY = -y;
		double localCamZ = -z;

		Tessellator t = Tessellator.instance;
		float prevBrightX = OpenGlHelper.lastBrightnessX;
		float prevBrightY = OpenGlHelper.lastBrightnessY;

		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_POLYGON_BIT | GL11.GL_TEXTURE_BIT);

		try {
			GL11.glDisable(GL11.GL_LIGHTING);
			RenderHelper.disableStandardItemLighting();
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glDepthMask(false);
			GL11.glDisable(GL11.GL_CULL_FACE);

			// ======================================================================
			// PASS 1: HEAVY DARK STORM CLOUD DECK (1 batched GL_QUADS draw call)
			// Tier 1: compact high-altitude origin wisp only (clean & non-obstructive)
			// Tiers 2-4: dramatic dark brooding thunderhead canopy with low overdraw
			// ======================================================================
			float cloudAlpha = tier == 1 ? Math.max(0.0f, 1.0f - age / (maxTicks * 0.70f)) * 0.78f : stormFade * 0.90f;
			if (cloudAlpha > 0.01f) {
				GL11.glEnable(GL11.GL_TEXTURE_2D);
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
				this.bindTexture(texStormCloud);

				int puffLimit = data.puffDist.length;
				if (quality == 1) puffLimit = Math.max(3, (puffLimit * 2) / 3);
				else if (quality >= 2) puffLimit = Math.max(2, puffLimit / 3);

				float expand = tier == 1 ? (0.85f + 0.25f * intro) : (0.72f + 0.34f * intro);
				t.startDrawingQuads();
				for (int i = 0; i < puffLimit; i++) {
					double ang = data.puffAngle[i] + age * data.puffOrbitSpeed[i];
					double dist = data.puffDist[i] * expand;
					double px = x + Math.cos(ang) * dist;
					double py = cloudBaseY + data.puffYOffset[i];
					double pz = z + Math.sin(ang) * dist;
					double roll = age * data.puffRollSpeed[i];
					float shade = data.puffShade[i];

					LightningVFXHelper.emitBillboardQuadUV(
							t, px, py, pz, data.puffRadius[i],
							camRX, camRZ, camUX, camUY, camUZ,
							Math.cos(roll), Math.sin(roll),
							cloudR * shade, cloudG * shade, cloudB * shade, cloudAlpha
					);
				}

				// Dark overcast core directly above Tiers 2-4 pillar throat
				if (tier >= 2) {
					double canopyR = data.cloudRadius * (0.88 + 0.22 * intro);
					double rot = age * 0.014;
					LightningVFXHelper.emitHorizontalQuadUV(
							t, x, cloudBaseY + 1.1, z, canopyR,
							Math.cos(rot), Math.sin(rot),
							cloudR * 0.85f, cloudG * 0.85f, cloudB * 0.88f, stormFade * 0.82f
					);
				}
				t.draw();

				// PASS 2: SYNCHRONIZED SHEET-LIGHTNING CLOUD FLASHES (1 batched draw call)
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
				t.startDrawingQuads();
				if (tier == 1) {
					float fProg = age / Math.max(1.0f, data.boltDuration[0]);
					float flash = LightningVFXHelper.heavyStormFlashCurve(fProg);
					if (flash > 0.02f) {
						LightningVFXHelper.emitBillboardQuadUV(
								t, x, cloudBaseY - 0.3, z, 4.2,
								camRX, camRZ, camUX, camUY, camUZ,
								1.0, 0.0,
								rLight, gLight, bLight, flash * 0.62f
						);
					}
				} else {
					int flashCount = tier >= 4 ? 4 : (tier == 3 ? 3 : 2);
					int cycle = (int) (age * 0.65f);
					for (int fp = 0; fp < flashCount; fp++) {
						int idx = Math.floorMod(cycle * 5 + fp * 7, puffLimit);
						double phase = (age * 0.65 - cycle) + fp * 0.23;
						float pulse = (float) Math.sin(Math.min(1.0, phase) * Math.PI);
						if (pulse <= 0.08f) continue;
						double ang = data.puffAngle[idx] + age * data.puffOrbitSpeed[idx];
						double dist = data.puffDist[idx] * 0.72 * expand;
						LightningVFXHelper.emitBillboardQuadUV(
								t, x + Math.cos(ang) * dist, cloudBaseY - 0.4, z + Math.sin(ang) * dist,
								data.puffRadius[idx] * 1.05,
								camRX, camRZ, camUX, camUY, camUZ,
								1.0, 0.0,
								rCore, gCore, bCore, pulse * stormFade * 0.42f
						);
					}
				}
				t.draw();
			}

			// ======================================================================
			// PASS 3: REFINED GROUND IONIZED FIELD & STORM SIGIL (Tiers 2-4 only)
			// ======================================================================
			if (tier >= 2) {
				GL11.glEnable(GL11.GL_TEXTURE_2D);
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
				GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
				GL11.glPolygonOffset(-2.0f, -2.0f);

				double fieldR = aoeRadius * (0.42 + 0.56 * intro);
				double fieldRot = age * 0.032;
				this.bindTexture(texLightningShockwave);
				t.startDrawingQuads();
				LightningVFXHelper.emitHorizontalQuadUV(
						t, x, groundY, z, fieldR,
						Math.cos(fieldRot), Math.sin(fieldRot),
						rDeep, gDeep, bDeep, stormFade * 0.52f
				);
				t.draw();

				double sigilR = aoeRadius * (0.46 + 0.36 * intro);
				double sigilRot = age * 0.022;
				this.bindTexture(texLightningSigil);
				t.startDrawingQuads();
				LightningVFXHelper.emitHorizontalQuadUV(
						t, x, groundY + 0.015, z, sigilR,
						Math.cos(sigilRot), Math.sin(sigilRot),
						rLight, gLight, bLight, stormFade * 0.58f
				);
				LightningVFXHelper.emitHorizontalQuadUV(
						t, x, groundY + 0.025, z, sigilR * 0.62,
						Math.cos(-sigilRot * 1.7), Math.sin(-sigilRot * 1.7),
						rCore, gCore, bCore, stormFade * 0.38f
				);
				t.draw();
				GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
			}

			// ======================================================================
			// PASS 4: MEGA PLASMA PILLAR + 3D HELICAL SPIRALS + ALL JAGGED BOLTS
			//         + SHOCKWAVE RINGS + SURFACE ELECTRIC ARCS
			// All rendered in ONE SINGLE untextured additive GL_TRIANGLES batch!
			// ======================================================================
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glShadeModel(GL11.GL_SMOOTH);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

			float pillarAlpha = 0.0f;
			float pillarRadius = 0.0f;
			double pillarTopY = cloudBaseY + 1.5;

			t.startDrawing(GL11.GL_TRIANGLES);

			// 4A. MEGA PLASMA PILLAR & 3D HELICAL LIGHTNING SPIRALS (Tiers 2-4, replaces Thunder Dragon!)
			if (tier >= 2) {
				float pStart = maxTicks * 0.08f;
				float pEnd = maxTicks * 0.90f;
				if (age >= pStart && age <= pEnd) {
					float pNorm = (age - pStart) / (pEnd - pStart);
					float envelope = pNorm < 0.14f
							? (pNorm / 0.14f)
							: (pNorm > 0.78f ? (1.0f - (pNorm - 0.78f) / 0.22f) : 1.0f);
					// High-voltage surge pulse during main climax
					float surge = 1.0f + 0.28f * (float) Math.sin(Math.min(1.0f, pNorm * 1.25f) * Math.PI);
					float flicker = 0.90f + 0.10f * (float) Math.sin(age * 1.9);
					pillarAlpha = LightningVFXHelper.clamp01(envelope * stormFade * flicker);

					float baseRad = tier >= 4 ? 2.45f : (tier == 3 ? 1.75f : 1.15f);
					pillarRadius = baseRad * (0.72f + 0.35f * surge) * (0.55f + 0.45f * envelope);
					boolean highCyl = (quality == 0);

					// Layer 1: Wide brooding storm-cobalt outer plasma sheath (hourglass flare at ground & cloud)
					LightningVFXHelper.emitPlasmaColumn(
							t, x, groundY, pillarTopY, z,
							pillarRadius * 1.55, 1.85, 1.95, highCyl,
							rDeep, gDeep, bDeep,
							pillarAlpha * 0.42f, pillarAlpha * 0.26f, pillarAlpha * 0.04f
					);

					// Layer 2: Pressurized high-voltage ice-blue plasma column
					LightningVFXHelper.emitPlasmaColumn(
							t, x, groundY, pillarTopY, z,
							pillarRadius * 0.88, 1.45, 1.50, highCyl,
							rLight, gLight, bLight,
							pillarAlpha * 0.68f, pillarAlpha * 0.56f, pillarAlpha * 0.10f
					);

					// Layer 3: Blazing dry-white incandescent core
					LightningVFXHelper.emitPlasmaColumn(
							t, x, groundY, pillarTopY, z,
							pillarRadius * 0.34, 1.20, 1.15, highCyl,
							rCore, gCore, bCore,
							pillarAlpha * 0.92f, pillarAlpha * 0.86f, pillarAlpha * 0.25f
					);

					// 3D Counter-Rotating Jagged Helical Lightning Spirals around the plasma column
					int helixCount = quality >= 2 ? 1 : (tier >= 4 ? 4 : (tier == 3 ? 3 : 2));
					double pillarH = pillarTopY - groundY;
					int crackleSeed = (int) (age * 0.5f);
					for (int h = 0; h < helixCount; h++) {
						double dir = (h % 2 == 0) ? 1.0 : -1.0;
						double basePhase = h * (Math.PI * 2.0 / helixCount) + age * 0.22 * dir;
						double turns = (tier >= 4 ? 2.6 : 2.1) * Math.PI * 2.0 * dir;
						double orbitBase = pillarRadius * (1.28 + (h % 2) * 0.32);

						for (int step = 0; step <= HELIX_STEPS; step++) {
							double f = step / (double) HELIX_STEPS;
							double ang = basePhase + f * turns;
							// Flared radius near ground and cloud throat + sharp electrical jitter
							double flare = 1.0 + 0.55 * Math.pow(Math.max(0.0, 1.0 - f * 4.0), 2.0)
									+ 0.45 * Math.pow(Math.max(0.0, f * 4.0 - 3.0), 2.0);
							double env = Math.sin(f * Math.PI);
							double jR = Math.sin(step * 4.7 + crackleSeed * 2.3 + h * 11.0) * 0.48 * env;
							double rad = orbitBase * flare + jR;

							HELIX_X[step] = Math.cos(ang) * rad;
							HELIX_Y[step] = 0.08 + f * pillarH;
							HELIX_Z[step] = Math.sin(ang) * rad;
						}

						double ribbonW = tier >= 4 ? 0.38 : (tier == 3 ? 0.30 : 0.24);
						LightningVFXHelper.emitPolylineRibbon(
								t, HELIX_X, HELIX_Y, HELIX_Z, HELIX_STEPS + 1,
								localCamX, localCamY, localCamZ,
								ribbonW, ribbonW * 0.85,
								rLight, gLight, bLight,
								pillarAlpha * 0.78f, pillarAlpha * 0.45f, 1.0f
						);
						LightningVFXHelper.emitPolylineRibbon(
								t, HELIX_X, HELIX_Y, HELIX_Z, HELIX_STEPS + 1,
								localCamX, localCamY, localCamZ,
								ribbonW * 0.30, ribbonW * 0.22,
								rCore, gCore, bCore,
								pillarAlpha * 0.95f, pillarAlpha * 0.60f, 1.0f
						);
					}

					// Expanding ground shockwave waves pulsing from the plasma pillar base
					int waveCount = tier >= 3 ? 2 : 1;
					for (int w = 0; w < waveCount; w++) {
						float waveNorm = (pNorm * 1.4f - w * 0.38f);
						if (waveNorm <= 0.0f || waveNorm >= 1.0f) continue;
						float wAlpha = (1.0f - waveNorm) * (1.0f - waveNorm) * stormFade;
						double shockR = pillarRadius * 1.2 + aoeRadius * 0.95 * Math.sqrt(waveNorm);
						LightningVFXHelper.emitHorizontalShockRing(
								t, x, groundY + 0.04, z,
								shockR, tier >= 4 ? 0.95 : 0.65, quality == 0,
								rLight, gLight, bLight, wAlpha * 0.75f
						);
						LightningVFXHelper.emitHorizontalShockRing(
								t, x, groundY + 0.06, z,
								shockR * 0.96, tier >= 4 ? 0.26 : 0.18, quality == 0,
								rCore, gCore, bCore, wAlpha * 0.92f
						);
					}
				}
			} else {
				// Tier 1 crisp single-strike shockwave ring
				float ringProg = age / Math.max(1.0f, maxTicks * 0.68f);
				if (ringProg < 1.0f) {
					float rAlpha = (1.0f - ringProg) * (1.0f - ringProg);
					double shockR = 0.55 + 3.2 * Math.sqrt(ringProg);
					LightningVFXHelper.emitHorizontalShockRing(
							t, x, groundY + 0.03, z,
							shockR, 0.36, quality == 0,
							rLight, gLight, bLight, rAlpha * 0.78f
					);
					LightningVFXHelper.emitHorizontalShockRing(
							t, x, groundY + 0.05, z,
							shockR * 0.94, 0.11, quality == 0,
							rCore, gCore, bCore, rAlpha * 0.92f
					);
				}
			}

			// 4B. PRE-CACHED HEAVY-STORM JAGGED BOLTS
			int boltStride = quality >= 2 ? 3 : (quality == 1 ? 2 : 1);
			for (int i = 0; i < data.bolts.length; i += boltStride) {
				float boltAge = age - data.boltStartTick[i];
				float dur = data.boltDuration[i];
				if (boltAge < 0.0f || boltAge >= dur) continue;

				float bProg = boltAge / dur;
				float flash = LightningVFXHelper.heavyStormFlashCurve(bProg);
				if (flash <= 0.01f) continue;

				// Ultra-fast downward propagation over first 1.4 ticks for punchy strike feel
				float headFrac = Math.min(1.0f, 0.20f + (boltAge / 1.4f) * 0.80f);
				double baseHalfW = (tier == 1 ? 0.44 : (tier >= 4 ? 0.46 : 0.38)) * data.boltWidthScale[i];

				LightningVFXHelper.emitHeavyStormBolt(
						t, data.bolts[i],
						localCamX, localCamY, localCamZ,
						baseHalfW, LIGHTNING_PALETTE,
						flash * (tier == 1 ? 1.0f : 0.90f),
						headFrac,
						quality < 2,
						quality == 0 || tier == 1
				);
			}

			// 4C. RADIAL SURFACE ELECTRIC CRACKS AROUND IMPACT / PILLAR BASE
			float crackWindow = tier == 1 ? (maxTicks * 0.55f) : (maxTicks * 0.82f);
			if (age < crackWindow && quality < 2) {
				float cProg = age / Math.max(1.0f, crackWindow);
				float cHead = Math.min(1.0f, cProg * (tier == 1 ? 4.5f : 2.8f));
				float cAlpha = (1.0f - cProg) * (tier == 1 ? 0.95f : stormFade * 0.80f);
				for (int c = 0; c < data.groundCrackX.length; c++) {
					LightningVFXHelper.emitPolylineRibbon(
							t, data.groundCrackX[c], data.groundCrackY[c], data.groundCrackZ[c], 5,
							localCamX, localCamY, localCamZ,
							tier >= 3 ? 0.24 : 0.16, 0.03,
							rLight, gLight, bLight,
							cAlpha * 0.72f, cAlpha * 0.12f, cHead
					);
					LightningVFXHelper.emitPolylineRibbon(
							t, data.groundCrackX[c], data.groundCrackY[c], data.groundCrackZ[c], 5,
							localCamX, localCamY, localCamZ,
							tier >= 3 ? 0.08 : 0.055, 0.015,
							rCore, gCore, bCore,
							cAlpha * 0.92f, cAlpha * 0.18f, cHead
					);
				}
			}

			t.draw();

			// ======================================================================
			// PASS 5: IMPACT BLOOMS + PILLAR BASE/THROAT FLARE + ELECTRIC SPARKS
			// Single batched GL_QUADS call on texStarfallCore (zero glPushMatrix!)
			// ======================================================================
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			this.bindTexture(texStarfallCore);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

			t.startDrawingQuads();

			// Pillar base & cloud-throat high-voltage flare (Tiers 2-4)
			if (pillarAlpha > 0.02f) {
				double baseFlareR = pillarRadius * 2.4;
				double roll = age * 0.08;
				double cr = Math.cos(roll), sr = Math.sin(roll);
				LightningVFXHelper.emitBillboardQuadUV(
						t, x, groundY + pillarRadius * 0.55, z, baseFlareR,
						camRX, camRZ, camUX, camUY, camUZ, cr, sr,
						rLight, gLight, bLight, pillarAlpha * 0.62f
				);
				LightningVFXHelper.emitBillboardQuadUV(
						t, x, groundY + pillarRadius * 0.45, z, baseFlareR * 0.48,
						camRX, camRZ, camUX, camUY, camUZ, cr, -sr,
						rCore, gCore, bCore, pillarAlpha * 0.92f
				);
				LightningVFXHelper.emitBillboardQuadUV(
						t, x, cloudBaseY - 0.5, z, baseFlareR * 1.45,
						camRX, camRZ, camUX, camUY, camUZ, 1.0, 0.0,
						rLight, gLight, bLight, pillarAlpha * 0.36f
				);
			}

			// Impact flash blooms at each active bolt's strike point
			for (int i = 0; i < data.bolts.length; i += boltStride) {
				float boltAge = age - data.boltStartTick[i];
				float bloomLife = tier == 1 ? 6.5f : 5.0f;
				if (boltAge < 0.0f || boltAge >= bloomLife) continue;

				float bNorm = boltAge / bloomLife;
				float bAlpha = (1.0f - bNorm) * (1.0f - bNorm);
				double bx = x + data.boltEndX[i];
				double bz = z + data.boltEndZ[i];
				double rad = (tier == 1 ? 2.2 : 1.7) * data.boltWidthScale[i] * (0.75 + 0.45 * bNorm);
				double roll = boltAge * 0.25;
				double cr = Math.cos(roll), sr = Math.sin(roll);

				LightningVFXHelper.emitBillboardQuadUV(
						t, bx, groundY + 0.45, bz, rad,
						camRX, camRZ, camUX, camUY, camUZ, cr, sr,
						rLight, gLight, bLight, bAlpha * 0.68f
				);
				LightningVFXHelper.emitBillboardQuadUV(
						t, bx, groundY + 0.45, bz, rad * 0.46,
						camRX, camRZ, camUX, camUY, camUZ, cr, -sr,
						rCore, gCore, bCore, bAlpha * 0.94f
				);
			}

			// Ballistic electric sparks kicking up from ground impact
			int sparkLimit = quality >= 2 ? Math.min(4, data.sparkVx.length)
					: (quality == 1 ? data.sparkVx.length / 2 : data.sparkVx.length);
			for (int s = 0; s < sparkLimit; s++) {
				if (data.sparkDark[s]) continue;
				float spAge = age - data.sparkStartTick[s];
				if (spAge < 0.0f || spAge > 14.0f) continue;
				float fade = 1.0f - (spAge / 14.0f);
				double sx = x + data.sparkVx[s] * spAge;
				double sy = groundY + 0.2 + data.sparkVy[s] * spAge - 0.032 * spAge * spAge;
				double sz = z + data.sparkVz[s] * spAge;
				if (sy < groundY) continue;
				double roll = spAge * data.sparkRollSpeed[s];
				LightningVFXHelper.emitBillboardQuadUV(
						t, sx, sy, sz, data.sparkSize[s] * (0.55f + 0.45f * fade),
						camRX, camRZ, camUX, camUY, camUZ, Math.cos(roll), Math.sin(roll),
						rCore, gCore, bCore, fade * 0.92f
				);
			}
			t.draw();

			// ======================================================================
			// PASS 6: DARK SCORCHED DEBRIS FRAGMENTS (1 batched untextured call)
			// ======================================================================
			if (quality < 2) {
				GL11.glDisable(GL11.GL_TEXTURE_2D);
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
				t.startDrawingQuads();
				for (int s = 0; s < sparkLimit; s++) {
					if (!data.sparkDark[s]) continue;
					float spAge = age - data.sparkStartTick[s];
					if (spAge < 0.0f || spAge > 13.0f) continue;
					float fade = 1.0f - (spAge / 13.0f);
					double sx = x + data.sparkVx[s] * spAge;
					double sy = groundY + 0.18 + data.sparkVy[s] * spAge - 0.040 * spAge * spAge;
					double sz = z + data.sparkVz[s] * spAge;
					if (sy < groundY) continue;
					double roll = spAge * data.sparkRollSpeed[s];
					LightningVFXHelper.emitBillboardQuadSolid(
							t, sx, sy, sz, data.sparkSize[s],
							camRX, camRZ, camUX, camUY, camUZ, Math.cos(roll), Math.sin(roll),
							0.06f, 0.075f, 0.11f, fade * 0.85f
					);
				}
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

	private void renderAoEShockwave(EntitySpellEffect effect, double x, double y, double z, float partialTicks) {
		am2.client.vfx.BlockbenchAoEModel model = am2.client.vfx.BlockbenchAoEModel.get();
		if (model == null) {
			return;
		}
		float age = effect.ticksExisted + partialTicks;
		int maxTicks = effect.getTicksToExist();
		if (maxTicks <= 0) {
			maxTicks = 14;
		}
		float progress = Math.min(1.0f, age / (float) maxTicks);
		float radius = effect.getRadius();
		if (radius < 0.5f) {
			radius = 1.0f;
		}
		int colorRgb = resolveZoneColorRgb(effect);
		boolean lowGfx = AMCore.config.LowGFX();
		int side = effect.getAoESide();
		float yawVar = ((effect.getEntityId() * 53) % 360);

		float prevBrightX = OpenGlHelper.lastBrightnessX;
		float prevBrightY = OpenGlHelper.lastBrightnessY;
		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_POLYGON_BIT);

		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDepthMask(false);
		RenderHelper.disableStandardItemLighting();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);
		GL11.glEnable(GL11.GL_BLEND);

		GL11.glTranslated(x, y, z);
		switch (side) {
			case 0:
				GL11.glRotatef(180.0f, 1.0f, 0.0f, 0.0f);
				break;
			case 2:
				GL11.glRotatef(-90.0f, 1.0f, 0.0f, 0.0f);
				break;
			case 3:
				GL11.glRotatef(90.0f, 1.0f, 0.0f, 0.0f);
				break;
			case 4:
				GL11.glRotatef(90.0f, 0.0f, 0.0f, 1.0f);
				break;
			case 5:
				GL11.glRotatef(-90.0f, 0.0f, 0.0f, 1.0f);
				break;
			case 1:
			default:
				break;
		}
		GL11.glRotatef(yawVar, 0.0f, 1.0f, 0.0f);

		model.render(radius, progress, colorRgb, lowGfx);

		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBrightX, prevBrightY);
		GL11.glPopAttrib();
		GL11.glPopMatrix();
	}

			@Override
	protected ResourceLocation getEntityTexture(Entity entity) {
		if (entity instanceof EntitySpellEffect) {
			EntitySpellEffect effect = (EntitySpellEffect) entity;
			if (effect.isBlizzard()) return texBlizzardOuter;
			if (effect.isStarfallImpact()) return texStarfallShockwave;
			if (effect.isGrowMandala()) return texGrowGround;
			if (effect.isExplosionImpact()) return texExplosionShockwave;
			if (effect.isLightningImpact()) return texLightningShockwave;
		}
		return texOuter;
	}
}
