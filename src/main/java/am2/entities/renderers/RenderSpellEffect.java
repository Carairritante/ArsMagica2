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

	// Grow Mandala textures
	private static final ResourceLocation texGrowGround = new ResourceLocation("arsmagica2", "textures/misc/vfx/grow_ground.png");
	private static final ResourceLocation texGrowPulse = new ResourceLocation("arsmagica2", "textures/misc/vfx/grow_pulse.png");

	// Explosion Impact textures
	private static final ResourceLocation texExplosionGround = new ResourceLocation("arsmagica2", "textures/misc/vfx/explosion_ground.png");
	private static final ResourceLocation texExplosionShockwave = new ResourceLocation("arsmagica2", "textures/misc/vfx/explosion_shockwave.png");

	// Lightning Impact textures
	private static final ResourceLocation texLightningSigil = new ResourceLocation("arsmagica2", "textures/misc/vfx/lightning_sigil.png");
	private static final ResourceLocation texLightningShockwave = new ResourceLocation("arsmagica2", "textures/misc/vfx/lightning_shockwave.png");
	private static final ResourceLocation texLightningBolt = new ResourceLocation("arsmagica2", "textures/misc/vfx/lightning_bolt.png");
	private static final ResourceLocation texLightningDragon = new ResourceLocation("arsmagica2", "textures/misc/vfx/lightning_dragon.png");
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

		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

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

		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

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

		GL11.glPopAttrib();
		GL11.glPopMatrix();
	}

	private void renderLightningImpact(EntitySpellEffect effect, double x, double y, double z, float partialTicks) {
		float age = effect.ticksExisted + partialTicks;
		int maxTicks = effect.getTicksToExist();
		if (maxTicks <= 0) maxTicks = 40;
		float progress = Math.min(1.0f, age / (float) maxTicks);

		int count = effect.getMergeCount();
		int tier = count >= 30 ? 4 : (count >= 15 ? 3 : (count >= 5 ? 2 : 1));
		float aoeRadius = effect.getRadius();
		if (aoeRadius < 3.5f) aoeRadius = 3.5f;

		// Pure Dark Blue / Deep Cobalt Plasma Palette (As requested: "azul escuro")
		int customColor = effect.getSpellColor();
		float rDeep = 0.04f, gDeep = 0.10f, bDeep = 0.55f;
		float rLight = 0.08f, gLight = 0.28f, bLight = 0.95f;
		float rEdge = 0.15f, gEdge = 0.40f, bEdge = 1.0f;
		float rCore = 0.65f, gCore = 0.85f, bCore = 1.0f;

		if (customColor != -1) {
			rLight = ((customColor >> 16) & 0xFF) / 255.0f;
			gLight = ((customColor >> 8) & 0xFF) / 255.0f;
			bLight = (customColor & 0xFF) / 255.0f;
			rDeep = Math.max(0.0f, rLight * 0.4f);
			gDeep = Math.max(0.0f, gLight * 0.4f);
			bDeep = Math.max(0.0f, bLight * 0.4f);
			rCore = Math.min(1.0f, rLight * 1.3f + 0.2f);
			gCore = Math.min(1.0f, gLight * 1.3f + 0.2f);
			bCore = Math.min(1.0f, bLight * 1.3f + 0.2f);
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

		// 1. NUVENS ESCURAS DE TEMPESTADE EM CIMA (Volumetric Dark Thundercloud)
		if (tier >= 2) {
			float stormGrowth = Math.min(1.0f, progress / 0.55f);
			float cloudRadius = aoeRadius * (0.6f + 0.75f * stormGrowth);
			float cloudAlpha = (float) Math.sin((1.0f - progress) * Math.PI * 0.5) * 0.85f;

			this.bindTexture(texStormCloud);
			java.util.Random cloudRnd = new java.util.Random(effect.getEntityId() + 777);

			int numPuffs = tier >= 4 ? 18 : (tier >= 3 ? 14 : 9);
			for (int cp = 0; cp < numPuffs; cp++) {
				float cDist = (float) Math.sqrt(cloudRnd.nextFloat()) * (cloudRadius * 0.80f);
				float cAng = cloudRnd.nextFloat() * (float) Math.PI * 2.0f + age * 0.015f * (cp % 2 == 0 ? 1 : -1);
				float cx = (float) (x + cDist * Math.cos(cAng));
				float cz = (float) (z + cDist * Math.sin(cAng));
				float cy = (float) y + 26.0f + (cp % 5) * 1.8f;
				float puffR = (tier >= 4 ? 9.5f : 6.5f) + (cp % 3) * 1.5f;

				float puffRot = age * (0.8f + (cp % 4) * 0.4f) * (cp % 2 == 0 ? 1 : -1);

				GL11.glPushMatrix();
				GL11.glTranslatef(cx, cy, cz);
				GL11.glRotatef(puffRot, 0.0f, 1.0f, 0.0f);

				GL11.glColor4f(rDeep * 0.8f, gDeep * 0.8f, bDeep * 0.8f, cloudAlpha * 0.90f);
				renderHorizontalQuad(t, -puffR, puffR, -puffR, puffR, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);

				if ((effect.ticksExisted + cp * 3) % 7 < 2) {
					GL11.glColor4f(rLight, gLight, bLight, cloudAlpha * 0.65f);
					renderHorizontalQuad(t, -puffR * 0.85f, puffR * 0.85f, -puffR * 0.85f, puffR * 0.85f, 0.05f, 0.0f, 1.0f, 0.0f, 1.0f);
				}
				GL11.glPopMatrix();
			}
		}

		// 2. AURA ELÉTRICA NO CHÃO (Natural Ionized Ground Storm Field)
		float fieldGrowth = Math.min(1.0f, progress / 0.60f);
		float fieldR = aoeRadius * (0.25f + 0.85f * fieldGrowth);
		float fieldAlpha = (float) Math.sin((1.0f - progress) * Math.PI * 0.5) * 0.70f;

		GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
		GL11.glPolygonOffset(-2.0f, -2.0f);
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, (float) y + 0.05f, (float) z);
		GL11.glRotatef(age * 2.5f, 0.0f, 1.0f, 0.0f);
		this.bindTexture(texLightningShockwave);
		GL11.glColor4f(rDeep, gDeep, bDeep, fieldAlpha * 0.85f);
		renderHorizontalQuad(t, -fieldR, fieldR, -fieldR, fieldR, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);

		GL11.glRotatef(-age * 4.0f, 0.0f, 1.0f, 0.0f);
		GL11.glColor4f(rLight, gLight, bLight, fieldAlpha * 0.50f);
		renderHorizontalQuad(t, -fieldR * 0.65f, fieldR * 0.65f, -fieldR * 0.65f, fieldR * 0.65f, 0.01f, 0.0f, 1.0f, 0.0f, 1.0f);
		GL11.glPopMatrix();
		GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);

		// 3. CHUVA MASSIVA DE RAIOS ZIG-ZAG (Procedural 3D Jagged Lightning)
		int totalBolts = tier >= 4 ? 220 : (tier >= 3 ? 110 : (tier == 2 ? 45 : 12));
		java.util.Random rnd = new java.util.Random(effect.getEntityId());
		float stormDuration = (float) maxTicks * 0.82f;

		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_LINE_SMOOTH);
		GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

		float[] sparkX = new float[32];
		float[] sparkZ = new float[32];
		float[] sparkAlpha = new float[32];
		int sparkCount = 0;

		float cloudBaseY = (float) y + (tier >= 2 ? 28.0f : 24.0f);
		float groundSurfaceY = (float) y + 0.05f;

		for (int i = 0; i < totalBolts; i++) {
			float growFrac = (float) i / (float) totalBolts;
			float strikeStartTick = growFrac * stormDuration;
			float boltAge = age - strikeStartTick;

			float currentRadiusLimit = aoeRadius * (0.12f + 0.88f * (float) Math.sqrt(growFrac));
			float dist = (float) Math.sqrt(rnd.nextFloat()) * currentRadiusLimit;
			float ang = rnd.nextFloat() * (float) Math.PI * 2.0f;
			float targetX = (float) (x + dist * Math.cos(ang));
			float targetZ = (float) (z + dist * Math.sin(ang));

			float startX = targetX + (rnd.nextFloat() - 0.5f) * 4.0f;
			float startZ = targetZ + (rnd.nextFloat() - 0.5f) * 4.0f;

			if (boltAge >= 0.0f && boltAge < 4.5f) {
				float bNorm = boltAge / 4.5f;
				float flicker = (1.0f - bNorm) * (0.80f + 0.20f * (float) Math.sin(boltAge * 16.0));

				int numSegs = 10;
				float[] segX = new float[numSegs + 1];
				float[] segY = new float[numSegs + 1];
				float[] segZ = new float[numSegs + 1];

				segX[0] = startX; segY[0] = cloudBaseY; segZ[0] = startZ;
				segX[numSegs] = targetX; segY[numSegs] = groundSurfaceY; segZ[numSegs] = targetZ;

				for (int k = 1; k < numSegs; k++) {
					float frac = (float) k / (float) numSegs;
					float vertY = cloudBaseY - (cloudBaseY - groundSurfaceY) * frac;
					float jitter = 2.4f * (float) Math.sin(frac * Math.PI);
					float jx = (rnd.nextFloat() - 0.5f) * jitter * 2.4f;
					float jz = (rnd.nextFloat() - 0.5f) * jitter * 2.4f;

					segX[k] = startX + (targetX - startX) * frac + jx;
					segY[k] = vertY;
					segZ[k] = startZ + (targetZ - startZ) * frac + jz;
				}

				// Pass A: Outer Dark Blue Glow
				GL11.glLineWidth(tier >= 4 ? 7.5f : (tier >= 3 ? 6.0f : 4.5f));
				GL11.glColor4f(rLight, gLight, bLight, flicker * 0.85f);
				GL11.glBegin(GL11.GL_LINE_STRIP);
				for (int k = 0; k <= numSegs; k++) {
					GL11.glVertex3f(segX[k], segY[k], segZ[k]);
				}
				GL11.glEnd();

				// Pass B: Inner Electric Azure-White Core
				GL11.glLineWidth(tier >= 4 ? 2.8f : (tier >= 3 ? 2.2f : 1.6f));
				GL11.glColor4f(rCore, gCore, bCore, flicker * 0.95f);
				GL11.glBegin(GL11.GL_LINE_STRIP);
				for (int k = 0; k <= numSegs; k++) {
					GL11.glVertex3f(segX[k], segY[k], segZ[k]);
				}
				GL11.glEnd();

				// Forked Branches splitting off from segments 3 and 6
				for (int forkIdx : new int[]{3, 6}) {
					if (forkIdx < numSegs) {
						float fx0 = segX[forkIdx], fy0 = segY[forkIdx], fz0 = segZ[forkIdx];
						float fx1 = fx0 + (rnd.nextFloat() - 0.5f) * 3.5f;
						float fy1 = fy0 - 3.5f;
						float fz1 = fz0 + (rnd.nextFloat() - 0.5f) * 3.5f;
						float fx2 = fx1 + (rnd.nextFloat() - 0.5f) * 2.5f;
						float fy2 = fy1 - 3.0f;
						float fz2 = fz1 + (rnd.nextFloat() - 0.5f) * 2.5f;

						GL11.glLineWidth(tier >= 4 ? 4.5f : 3.0f);
						GL11.glColor4f(rLight, gLight, bLight, flicker * 0.70f);
						GL11.glBegin(GL11.GL_LINE_STRIP);
						GL11.glVertex3f(fx0, fy0, fz0);
						GL11.glVertex3f(fx1, fy1, fz1);
						GL11.glVertex3f(fx2, fy2, fz2);
						GL11.glEnd();
					}
				}
			}

			if (boltAge >= 0.0f && boltAge < 7.0f) {
				if (sparkCount < 32) {
					sparkX[sparkCount] = targetX;
					sparkZ[sparkCount] = targetZ;
					sparkAlpha[sparkCount] = (1.0f - boltAge / 7.0f) * 0.85f;
					sparkCount++;
				}
			}
		}

		GL11.glDisable(GL11.GL_LINE_SMOOTH);
		GL11.glEnable(GL11.GL_TEXTURE_2D);

		// 4. FAÍSCAS DE IMPACTO NO CHÃO PARA CADA RAIO (Ground Sparks)
		if (sparkCount > 0) {
			this.bindTexture(texLightningShockwave);
			for (int s = 0; s < sparkCount; s++) {
				float spX = sparkX[s];
				float spZ = sparkZ[s];
				float spA = sparkAlpha[s];
				float spR = 0.5f + (1.0f - spA) * 1.8f;

				GL11.glPushMatrix();
				GL11.glTranslatef(spX, groundSurfaceY, spZ);
				GL11.glColor4f(rLight, gLight, bLight, spA);
			renderHorizontalQuad(t, -spR, spR, -spR, spR, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
				GL11.glPopMatrix();
			}
		}

		// 5. CLÍMAX & MEGA PILAR CENTRAL DE PLASMA (Tiers 2, 3, 4)
		if (tier >= 2) {
			float peakStart = (float) maxTicks * 0.35f;
			float peakEnd = (float) maxTicks * 0.70f;
			if (age >= peakStart && age <= peakEnd) {
				float peakNorm = (age - peakStart) / (peakEnd - peakStart);
				float peakAlpha = (float) Math.sin(peakNorm * Math.PI);
				float pWidth = tier >= 4 ? 6.2f : (tier >= 3 ? 4.2f : 2.5f);
				float pHeight = 44.0f + aoeRadius * 1.5f;

				this.bindTexture(texLightningBolt);
				GL11.glColor4f(rLight, gLight, bLight, peakAlpha * 0.85f);
				renderVerticalQuadCross3(t, x, y, y + pHeight, z, pWidth, 1.0f, 0.0f);

				GL11.glColor4f(rCore, gCore, bCore, peakAlpha * 0.95f);
				renderVerticalQuadCross3(t, x, y, y + pHeight, z, pWidth * 0.45f, 1.0f, 0.0f);

				float shockR = aoeRadius * (0.3f + 0.85f * (float) Math.sqrt(peakNorm));
				GL11.glPushMatrix();
				GL11.glTranslatef((float) x, (float) y + 0.05f, (float) z);
				this.bindTexture(texLightningShockwave);
				GL11.glColor4f(rLight, gLight, bLight, peakAlpha * 0.90f);
				renderHorizontalQuad(t, -shockR, shockR, -shockR, shockR, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);

				if (tier >= 4) {
					float shockR2 = shockR * 0.75f;
					GL11.glColor4f(rDeep, gDeep, bDeep, peakAlpha * 0.75f);
					renderHorizontalQuad(t, -shockR2, shockR2, -shockR2, shockR2, 0.01f, 0.0f, 1.0f, 0.0f, 1.0f);
				}
				GL11.glPopMatrix();
			}
		}

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
