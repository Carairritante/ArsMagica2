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

	private static final ResourceLocation texOuter = new ResourceLocation("arsmagica2", "textures/misc/vfx/circle_outer.png");
	private static final ResourceLocation texMiddle = new ResourceLocation("arsmagica2", "textures/misc/vfx/circle_middle.png");
	private static final ResourceLocation texCore = new ResourceLocation("arsmagica2", "textures/misc/vfx/circle_core.png");
	private static final ResourceLocation texGround = new ResourceLocation("arsmagica2", "textures/misc/vfx/circle_ground.png");
	private static final ResourceLocation texBeam = new ResourceLocation("arsmagica2", "textures/misc/vfx/fire_beam.png");
	private static final ResourceLocation texShockwave = new ResourceLocation("arsmagica2", "textures/misc/vfx/shockwave_ring.png");

	@Override
	public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
		if (!(entity instanceof EntitySpellEffect)) {
			return;
		}

		EntitySpellEffect effect = (EntitySpellEffect) entity;

		if (effect.isRainOfFire()) {
			renderFirestorm(effect, x, y, z, partialTicks);
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

	private void renderSmoothVectorRing(float ringRadius, int segments, float lineWidth) {
		GL11.glLineWidth(lineWidth);
		GL11.glBegin(GL11.GL_LINE_LOOP);
		for (int i = 0; i < segments; i++) {
			double angle = 2.0 * Math.PI * i / segments;
			GL11.glVertex3d(ringRadius * Math.cos(angle), 0, ringRadius * Math.sin(angle));
		}
		GL11.glEnd();
	}

	@Override
	protected ResourceLocation getEntityTexture(Entity entity) {
		return texOuter;
	}
}
