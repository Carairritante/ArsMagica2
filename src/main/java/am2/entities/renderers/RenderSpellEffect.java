package am2.entities.renderers;

import am2.entities.EntitySpellEffect;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

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
		}
	}

	private void renderFirestorm(EntitySpellEffect effect, double x, double y, double z, float partialTicks) {
		float age = effect.ticksExisted + partialTicks;
		int maxTicks = effect.getTicksToExist();
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
		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

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
		GL11.glPopAttrib();
		GL11.glPopMatrix();
	}

	private void renderBlizzard(EntitySpellEffect effect, double x, double y, double z, float partialTicks) {
		float age = effect.ticksExisted + partialTicks;
		int maxTicks = effect.getTicksToExist();
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
		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

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

		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

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

	@Override
	protected ResourceLocation getEntityTexture(Entity entity) {
		if (entity instanceof EntitySpellEffect) {
			EntitySpellEffect effect = (EntitySpellEffect) entity;
			if (effect.isBlizzard()) return texBlizzardOuter;
			if (effect.isStarfallImpact()) return texStarfallShockwave;
		}
		return texOuter;
	}
}
