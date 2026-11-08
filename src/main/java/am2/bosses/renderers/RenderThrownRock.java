package am2.bosses.renderers;

import am2.api.spell.component.interfaces.ISpellModifier;
import am2.api.spell.enums.SpellModifiers;
import am2.bosses.models.ModelThrownRock;
import am2.entities.EntityThrownRock;
import am2.spell.SpellUtils;
import am2.spell.modifiers.Colour;
import am2.texture.ResourceManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class RenderThrownRock extends RenderLiving {

	private static final ResourceLocation rLoc = new ResourceLocation("arsmagica2", ResourceManager.getMobTexturePath("bosses/earth_guardian.png"));

	// Starfall in-flight VFX textures
	private static final ResourceLocation texStarCore = new ResourceLocation("arsmagica2", "textures/misc/vfx/starfall_core.png");
	private static final ResourceLocation texStarRing = new ResourceLocation("arsmagica2", "textures/misc/vfx/starfall_ring.png");
	private static final ResourceLocation texStarTrail = new ResourceLocation("arsmagica2", "textures/misc/vfx/starfall_trail.png");
	private static final ResourceLocation texStarGround = new ResourceLocation("arsmagica2", "textures/misc/vfx/starfall_ground.png");

	public RenderThrownRock() {
		super(new ModelThrownRock(), 0.5f);
	}

	@Override
	public void doRender(Entity par1Entity, double par2, double par4, double par6, float par8, float par9) {
		EntityThrownRock rock = (EntityThrownRock) par1Entity;
		if (!rock.getIsShootingStar()) {
			super.doRender(par1Entity, par2, par4, par6, par8, par9);
		} else {
			renderShootingStar(rock, par2, par4, par6, par9);
		}
	}

	private void renderShootingStar(EntityThrownRock rock, double x, double y, double z, float partialTicks) {
		float age = rock.ticksExisted + partialTicks;

		// 1. Color resolution
		int customColor = -1;
		ItemStack stack = rock.getSpellStack();
		if (stack != null && SpellUtils.instance.modifierIsPresent(SpellModifiers.COLOR, stack, 0)) {
			ISpellModifier[] mods = SpellUtils.instance.getModifiersForStage(stack, 0);
			int ordinalCount = 0;
			for (ISpellModifier mod : mods) {
				if (mod instanceof Colour) {
					byte[] meta = SpellUtils.instance.getModifierMetadataFromStack(stack, mod, 0, ordinalCount++);
					customColor = (int) mod.getModifier(SpellModifiers.COLOR, null, null, null, meta);
				}
			}
		}

		float rStar = 0.70f, gStar = 0.90f, bStar = 1.0f;
		float rTrail = 0.55f, gTrail = 0.82f, bTrail = 1.0f;
		float rGround = 0.50f, gGround = 0.85f, bGround = 1.0f;

		if (customColor != -1) {
			float cr = ((customColor >> 16) & 0xFF) / 255.0f;
			float cg = ((customColor >> 8) & 0xFF) / 255.0f;
			float cb = (customColor & 0xFF) / 255.0f;
			rStar = Math.min(1.0f, cr * 1.2f + 0.1f);
			gStar = Math.min(1.0f, cg * 1.2f + 0.1f);
			bStar = Math.min(1.0f, cb * 1.2f + 0.1f);
			rTrail = cr; gTrail = cg; bTrail = cb;
			rGround = cr * 0.9f; gGround = cg * 0.9f; bGround = cb * 0.9f;
		}

		Tessellator t = Tessellator.instance;

		// 2. Setup OpenGL State
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

		// 3. Scale computation from Star Fusion tier
		float visualScale = rock.getVisualScale();

		// Ground Astrological Targeting Reticle (directly below the falling star)
		int blockX = (int) Math.floor(rock.posX);
		int blockZ = (int) Math.floor(rock.posZ);
		int startY = (int) Math.floor(rock.posY);
		int groundY = 0;
		for (int checkY = Math.min(255, startY); checkY >= 0; checkY--) {
			if (rock.worldObj.getBlock(blockX, checkY, blockZ).getMaterial().isSolid()) {
				groundY = checkY + 1;
				break;
			}
		}
		if (groundY == 0) {
			groundY = rock.worldObj.getTopSolidOrLiquidBlock(blockX, blockZ);
		}
		double viewerY = RenderManager.instance.viewerPosY;
		double groundYRel = (double) groundY - viewerY;
		double distToGround = Math.max(0.0, rock.posY - groundY);

		// Reticle grows in brightness & spins faster as the star gets closer to impact
		float groundFactor = (float) Math.max(0.25, Math.min(1.0, 1.0 - (distToGround / 75.0)));
		float spinSpeed = 1.5f + (1.0f - (float) Math.min(1.0, distToGround / 50.0)) * 4.5f;

		this.bindTexture(texStarGround);
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, (float) groundYRel + 0.05f, (float) z);
		GL11.glRotatef(age * spinSpeed, 0.0f, 1.0f, 0.0f);
		float gRadius = 3.6f * Math.min(2.8f, 0.75f + visualScale * 0.45f);
		GL11.glColor4f(rGround, gGround, bGround, 0.75f * groundFactor);
		renderHorizontalQuad(t, -gRadius, gRadius, -gRadius, gRadius, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
		GL11.glPopMatrix();

		// 4. Volumetric Aerodynamic Comet Tail (stretches upwards along -motion)
		this.bindTexture(texStarTrail);
		float trailLength = 12.0f + (visualScale - 1.0f) * 5.0f;
		float trailWidth = 2.4f * visualScale;
		GL11.glColor4f(rTrail, gTrail, bTrail, 0.90f);
		renderVerticalQuadCross3(t, x, y, y + trailLength, z, trailWidth, 1.0f, 0.0f);

		// 5. Orbital Gyroscopic Celestial Rings (around star core)
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, (float) y, (float) z);

		this.bindTexture(texStarRing);
		// Ring 1 (tilted 28 deg, rotating fast clockwise)
		GL11.glPushMatrix();
		GL11.glRotatef(age * 4.0f, 0.0f, 1.0f, 0.0f);
		GL11.glRotatef(28.0f, 1.0f, 0.0f, 0.0f);
		GL11.glColor4f(rStar, gStar, bStar, 0.85f);
		float ringScale1 = 2.2f * visualScale;
		renderHorizontalQuad(t, -ringScale1, ringScale1, -ringScale1, ringScale1, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
		GL11.glPopMatrix();

		// Ring 2 (tilted -38 deg, counter-rotating)
		GL11.glPushMatrix();
		GL11.glRotatef(-age * 3.2f, 0.0f, 1.0f, 0.0f);
		GL11.glRotatef(-38.0f, 0.0f, 0.0f, 1.0f);
		GL11.glColor4f(rTrail, gTrail, bTrail, 0.75f);
		float ringScale2 = 2.6f * visualScale;
		renderHorizontalQuad(t, -ringScale2, ringScale2, -ringScale2, ringScale2, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
		GL11.glPopMatrix();

		// Extra Ring 3 for Cataclysmic Meteor (Tier 3)
		if (visualScale >= 2.5f){
			GL11.glPushMatrix();
			GL11.glRotatef(age * 5.0f, 0.0f, 1.0f, 0.0f);
			GL11.glRotatef(58.0f, 1.0f, 0.0f, 1.0f);
			GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.90f);
			float ringScale3 = 3.2f * visualScale;
			renderHorizontalQuad(t, -ringScale3, ringScale3, -ringScale3, ringScale3, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
			GL11.glPopMatrix();
		}

		// Tier 4 Accretion Disc (Primordial Star 25+ merged stars)
		if (visualScale >= 4.0f){
			GL11.glPushMatrix();
			GL11.glRotatef(-age * 6.5f, 0.0f, 1.0f, 0.0f);
			GL11.glRotatef(75.0f, 0.0f, 0.0f, 1.0f);
			GL11.glColor4f(rStar, gStar, bStar, 0.95f);
			float ringScale4 = 3.8f * visualScale;
			renderHorizontalQuad(t, -ringScale4, ringScale4, -ringScale4, ringScale4, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
			GL11.glPopMatrix();
		}

		// 6. 3D Vector Octagram Frame (smooth mathematical celestial lattice)
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glPushMatrix();
		GL11.glRotatef(age * 2.2f, 0.0f, 1.0f, 0.0f);
		GL11.glRotatef(15.0f, 1.0f, 0.0f, 1.0f);
		GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.80f);
		renderSmoothVectorPolygon(2.4f * visualScale, 8, 2.0f);
		if (visualScale >= 4.0f){
			// Secondary offset octagram forming a 16-pointed sacred star frame
			GL11.glRotatef(22.5f, 0.0f, 1.0f, 0.0f);
			GL11.glColor4f(rTrail, gTrail, bTrail, 0.70f);
			renderSmoothVectorPolygon(2.6f * visualScale, 8, 1.8f);
		}
		GL11.glPopMatrix();
		GL11.glEnable(GL11.GL_TEXTURE_2D);

		// 7. Radiant Celestial Diamond Star Core
		this.bindTexture(texStarCore);
		float corePulse = 1.0f + 0.10f * (float) Math.sin(age * 0.45f);
		float coreRadius = 2.1f * corePulse * visualScale;

		GL11.glPushMatrix();
		GL11.glRotatef(age * 1.8f, 0.0f, 1.0f, 0.0f);
		GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
		// 3 intersecting vertical planes for full 3D diamond radiance
		renderVerticalQuadCross3(t, 0.0, -coreRadius, coreRadius, 0.0, coreRadius * 2.0f, 0.0f, 1.0f);
		// 1 horizontal diamond plane
		renderHorizontalQuad(t, -coreRadius, coreRadius, -coreRadius, coreRadius, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f);
		GL11.glPopMatrix();

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

	private void renderSmoothVectorPolygon(float ringRadius, int sides, float lineWidth) {
		GL11.glLineWidth(lineWidth);
		GL11.glBegin(GL11.GL_LINE_LOOP);
		for (int i = 0; i < sides; i++) {
			double angle = 2.0 * Math.PI * i / sides;
			GL11.glVertex3d(ringRadius * Math.cos(angle), 0, ringRadius * Math.sin(angle));
		}
		GL11.glEnd();
	}

	@Override
	protected ResourceLocation getEntityTexture(Entity entity) {
		return rLoc;
	}
}
