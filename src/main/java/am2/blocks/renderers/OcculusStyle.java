package am2.blocks.renderers;

import am2.AMCore;
import am2.client.vfx.BlockbenchRigModel;
import am2.models.ModelOcculus;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

/**
 * Look of the Oculus block. Purely visual: it receives an {@link OcculusView} and draws,
 * with the block texture already bound and the origin at the block's bottom centre
 * (no rotation applied). The classic look (Classic_Models) stays in {@link #drawClassic}.
 * The remastered look renders the faithful AM2 spherical Blockbench Oculus ({@code models/blocks/occulus.bbmodel})
 * preserving the original stone/indigo/crimson/amber palette, plus subtle real in-game cosmic
 * effects using AM2 WizardChalk runes and the Oculus's own sapphire/amber palette:
 *   - Floating WizardChalk rune circle above the base plinth (awakens when a mage approaches)
 *   - Subtle additive amber iris glow (dims cleanly during organic blinks)
 *   - Orbiting mini-constellation starlight motes & sapphire claw-to-eye channeling wisps
 */
@SideOnly(Side.CLIENT)
public final class OcculusStyle {
	private static final ModelOcculus MODEL = new ModelOcculus();
	private static final ResourceLocation RIG_LOC = new ResourceLocation("arsmagica2", "models/blocks/occulus.bbmodel");
	private static final ResourceLocation GLOW_LOC = new ResourceLocation("arsmagica2", "textures/blocks/custom/occulus_glow.png");
	private static final ResourceLocation RUNES_LOC = new ResourceLocation("arsmagica2", "textures/blocks/custom/occulus_runes.png");

	// Must match EYE_Y and the iris depth of the almond lens in tools/generate_occulus.py
	private static final float EYE_CENTER_Y = 14.35F / 16.0F;
	private static final float IRIS_GLOW_Z = -0.80F / 16.0F;
	private static final float RUNE_RING_Y = 2.32F / 16.0F;
	private static final float RUNE_RING_HALF = 7.85F / 16.0F;

	private static BlockbenchRigModel rig;
	private static boolean rigLoadAttempted = false;

	private static final Map<String, BlockbenchRigModel.BoneOverride> OVERRIDES = new HashMap<String, BlockbenchRigModel.BoneOverride>();
	private static final BlockbenchRigModel.BoneOverride EYE_PIVOT_OV = new BlockbenchRigModel.BoneOverride();
	private static final BlockbenchRigModel.BoneOverride EYE_BODY_OV = new BlockbenchRigModel.BoneOverride();
	private static final BlockbenchRigModel.BoneOverride EYE_PUPIL_OV = new BlockbenchRigModel.BoneOverride();
	private static final BlockbenchRigModel.BoneOverride LID_TOP_OV = new BlockbenchRigModel.BoneOverride();
	private static final BlockbenchRigModel.BoneOverride LID_BOT_OV = new BlockbenchRigModel.BoneOverride();

	static {
		OVERRIDES.put("eye_pivot", EYE_PIVOT_OV);
		OVERRIDES.put("eye_body", EYE_BODY_OV);
		OVERRIDES.put("eye_pupil", EYE_PUPIL_OV);
		OVERRIDES.put("eyelid_top", LID_TOP_OV);
		OVERRIDES.put("eyelid_bot", LID_BOT_OV);
	}

	private OcculusStyle() {}

	private static BlockbenchRigModel getRig() {
		if (!rigLoadAttempted) {
			rigLoadAttempted = true;
			BlockbenchRigModel loaded = BlockbenchRigModel.load(RIG_LOC, "am2_occulus");
			if (loaded != null) {
				loaded.setEmissiveBones("eye_iris", "pillar_rune");
			}
			rig = loaded;
		}
		return rig;
	}

	/** The original AM2 Oculus, unchanged. */
	static void drawClassic(OcculusView view) {
		GL11.glPushMatrix();
		GL11.glTranslatef(0, 0.9F, 0);
		GL11.glRotatef(view.facing, 0.0F, 1.0F, 0.0F);
		GL11.glScalef(1.0F, -1F, -1F);
		GL11.glScalef(1.0F, 0.6F, 1.0F);
		MODEL.renderModel(0.0625F, 0, 0);
		GL11.glPopMatrix();
	}

	static void drawRemaster(OcculusView view) {
		BlockbenchRigModel model = getRig();
		if (model == null) {
			drawClassicFallback(view);
			return;
		}

		EYE_PIVOT_OV.reset();
		EYE_BODY_OV.reset();
		EYE_PUPIL_OV.reset();
		LID_TOP_OV.reset();
		LID_BOT_OV.reset();

		float open = 0.0F;
		float awake = 0.0F;
		float glowAlpha = 0.0F;

		if (view.inventory) {
			EYE_PIVOT_OV.dy = 0.12F;
			LID_TOP_OV.drx = 2.0F;
			LID_BOT_OV.drx = -2.0F;
		} else {
			float t = view.time;
			open = clamp01(view.open);
			awake = clamp01(view.awake);
			float idle = 1.0F - open;

			// Subtle levitation breathing inside the 4 curved indigo cradle claws
			EYE_PIVOT_OV.dy = 0.16F * open + 0.14F * (float) Math.sin(t * 1.35F);

			// Live player eye tracking + calm idle gaze drift when resting
			float yaw = view.eyeYaw + idle * 10.0F * (float) Math.sin(t * 0.55F);
			float pitch = view.eyePitch + idle * 4.5F * (float) Math.sin(t * 0.41F);
			EYE_BODY_OV.dry = -yaw;
			EYE_BODY_OV.drx = pitch;

			// Independent vertical slit pupil: subtle directional shift + focus narrowing when awake
			EYE_PUPIL_OV.dx = clamp(-yaw / 70.0F, -1.0F, 1.0F) * 0.16F;
			EYE_PUPIL_OV.dy = clamp(pitch / 40.0F, -1.0F, 1.0F) * 0.12F;
			EYE_PUPIL_OV.sx = 1.0F - 0.18F * awake;

			// Curved organic eyelids: gentle upper relaxation when idle, wide open when watching,
			// plus a quick natural blink every ~5.8s
			float restTop = -14.0F * idle + 2.0F * open;
			float restBot = 6.0F * idle - 2.0F * open;
			float blinkPhase = t % 5.8F;
			float blinkClose = 0.0F;
			if (blinkPhase > 5.50F) {
				float u = (blinkPhase - 5.50F) / 0.30F;
				blinkClose = (float) Math.sin(u * Math.PI);
			}
			LID_TOP_OV.drx = restTop - blinkClose * (54.0F + restTop);
			LID_BOT_OV.drx = restBot + blinkClose * (50.0F - restBot);

			// Subtle additive amber iris aura (dims cleanly during blink)
			float pulse = 0.92F + 0.08F * (float) Math.sin(t * 2.1F);
			glowAlpha = (0.14F + 0.08F * open + 0.12F * awake) * pulse * (1.0F - blinkClose);
		}

		GL11.glPushMatrix();
		if (view.inventory) {
			GL11.glTranslatef(0.0F, -0.02F, 0.0F);
			GL11.glScalef(0.90F, 0.90F, 0.90F);
		}
		GL11.glRotatef(view.facing, 0.0F, 1.0F, 0.0F);
		model.render(null, 0.0F, OVERRIDES, 1.0F, 1.0F, 1.0F, 1.0F);

		boolean allowEffects = !view.inventory && !AMCore.config.NoGFX();
		if (allowEffects) {
			drawBaseRuneCircle(view.time, awake);
			if (glowAlpha > 0.01F) {
				drawIrisGlow(glowAlpha);
			}
		}
		GL11.glPopMatrix();

		// World-aligned billboarded mini-constellation & claw channeling wisps (outside facing rotation)
		if (allowEffects) {
			drawCosmicMotes(view.time, open, awake);
		}
	}

	/**
	 * Floating AM2 WizardChalk rune circle hovering cleanly above the grey stone base plinth.
	 * Calm and faint when resting; illuminates in sapphire-starlight when a mage awakens the Oculus.
	 */
	private static void drawBaseRuneCircle(float time, float awake) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.renderEngine == null) {
			return;
		}
		float prevBx = OpenGlHelper.lastBrightnessX;
		float prevBy = OpenGlHelper.lastBrightnessY;
		float pulse = 0.90F + 0.10F * (float) Math.sin(time * 1.8F);
		float alpha = (0.14F + 0.40F * awake) * pulse;
		float angle = (time * (6.0F + 10.0F * awake)) % 360.0F;

		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		try {
			GL11.glTranslatef(0.0F, RUNE_RING_Y + 0.004F * (float) Math.sin(time * 1.4F), 0.0F);
			GL11.glRotatef(angle, 0.0F, 1.0F, 0.0F);

			mc.renderEngine.bindTexture(RUNES_LOC);
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
			GL11.glDepthMask(false);
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

			float r = 0.50F + 0.22F * awake;
			float g = 0.72F + 0.08F * awake;
			float b = 1.0F;
			Tessellator tess = Tessellator.instance;
			tess.startDrawingQuads();
			tess.setColorRGBA_F(r, g, b, alpha);
			tess.addVertexWithUV(-RUNE_RING_HALF, 0.0D, -RUNE_RING_HALF, 0.0D, 0.0D);
			tess.addVertexWithUV( RUNE_RING_HALF, 0.0D, -RUNE_RING_HALF, 1.0D, 0.0D);
			tess.addVertexWithUV( RUNE_RING_HALF, 0.0D,  RUNE_RING_HALF, 1.0D, 1.0D);
			tess.addVertexWithUV(-RUNE_RING_HALF, 0.0D,  RUNE_RING_HALF, 0.0D, 1.0D);
			tess.draw();
		} finally {
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBx, prevBy);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			GL11.glPopAttrib();
			GL11.glPopMatrix();
		}
	}

	/**
	 * Subtle additive amber glow right over the iris plane in the eye's local space
	 * (only visible from the front hemisphere, fading automatically when the eye blinks).
	 */
	private static void drawIrisGlow(float alpha) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.renderEngine == null) {
			return;
		}
		float prevBx = OpenGlHelper.lastBrightnessX;
		float prevBy = OpenGlHelper.lastBrightnessY;

		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		try {
			GL11.glTranslatef(0.0F, EYE_CENTER_Y + EYE_PIVOT_OV.dy * BlockbenchRigModel.BLOCK_SCALE, 0.0F);
			GL11.glRotatef(EYE_BODY_OV.dry, 0.0F, 1.0F, 0.0F);
			GL11.glRotatef(EYE_BODY_OV.drx, 1.0F, 0.0F, 0.0F);
			GL11.glTranslatef(0.0F, 0.0F, IRIS_GLOW_Z);

			mc.renderEngine.bindTexture(GLOW_LOC);
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
			GL11.glDepthMask(false);
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

			float half = 0.22F;
			Tessellator tess = Tessellator.instance;
			tess.startDrawingQuads();
			tess.setColorRGBA_F(1.0F, 0.70F, 0.20F, alpha);
			tess.addVertexWithUV( half,  half, 0.0D, 0.0D, 0.0D);
			tess.addVertexWithUV(-half,  half, 0.0D, 1.0D, 0.0D);
			tess.addVertexWithUV(-half, -half, 0.0D, 1.0D, 1.0D);
			tess.addVertexWithUV( half, -half, 0.0D, 0.0D, 1.0D);
			tess.draw();
		} finally {
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBx, prevBy);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			GL11.glPopAttrib();
			GL11.glPopMatrix();
		}
	}

	/**
	 * Renders the orbiting mini-constellation starlight motes (claw sapphire + iris amber)
	 * and the 4 sapphire mana wisps channeling from the 4 diagonal claw tips into the floating eye.
	 */
	private static void drawCosmicMotes(float time, float open, float awake) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.renderEngine == null) {
			return;
		}
		boolean lowGfx = AMCore.config.LowGFX();
		int starCount = lowGfx ? 4 : 8;
		float eyeY = EYE_CENTER_Y + EYE_PIVOT_OV.dy * BlockbenchRigModel.BLOCK_SCALE;
		float camYaw = -RenderManager.instance.playerViewY;
		float camPitch = RenderManager.instance.playerViewX;

		float prevBx = OpenGlHelper.lastBrightnessX;
		float prevBy = OpenGlHelper.lastBrightnessY;
		Tessellator tess = Tessellator.instance;

		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		try {
			mc.renderEngine.bindTexture(GLOW_LOC);
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
			GL11.glDepthMask(false);
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

			// 1. Orbiting mini-constellation starlight motes
			float speedMul = 0.55F + 0.45F * awake;
			for (int i = 0; i < starCount; i++) {
				float orbitR = (5.4F + (i % 3) * 0.75F + 0.45F * awake) * BlockbenchRigModel.BLOCK_SCALE;
				double ang = time * (0.50 + (i % 3) * 0.16) * speedMul + i * (Math.PI * 2.0 / starCount);
				float tilt = (i - (starCount - 1) * 0.5F) * 0.35F;
				float mx = (float) Math.cos(ang) * orbitR;
				float mz = (float) Math.sin(ang) * orbitR;
				float my = eyeY + (float) Math.sin(ang + i) * (1.75F + Math.abs(tilt)) * BlockbenchRigModel.BLOCK_SCALE;

				boolean amber = (i % 3 == 0);
				float twinkle = 0.65F + 0.35F * (float) Math.sin(time * 2.8F + i * 1.9F);
				float alpha = (0.24F + 0.52F * awake) * twinkle;
				float half = (amber ? 0.68F : 0.58F) * BlockbenchRigModel.BLOCK_SCALE;

				drawBillboardQuad(tess, mx, my, mz, camYaw, camPitch, half,
						amber ? 1.0F : 0.51F,
						amber ? 0.76F : 0.70F,
						amber ? 0.30F : 1.0F,
						alpha);
			}

			// 2. 4 Sapphire mana wisps channeling from the 4 diagonal indigo claw tips into the globe
			if (!lowGfx) {
				float wispStrength = 0.22F + 0.20F * open + 0.45F * awake;
				for (int i = 0; i < 4; i++) {
					float prog = (time * (0.48F + 0.30F * awake) + i * 0.25F) % 1.0F;
					double ang = Math.toRadians(45.0 + i * 90.0) + prog * 0.45;
					float r = 3.55F * (1.0F - prog * 0.35F) * BlockbenchRigModel.BLOCK_SCALE;
					float wx = (float) Math.sin(ang) * r;
					float wz = (float) Math.cos(ang) * r;
					float wy = (14.9F - prog * 2.1F + (float) Math.sin(prog * Math.PI) * 0.85F) * BlockbenchRigModel.BLOCK_SCALE;

					float fade = (float) Math.sin(prog * Math.PI);
					float half = (0.78F - 0.22F * prog) * BlockbenchRigModel.BLOCK_SCALE;
					drawBillboardQuad(tess, wx, wy, wz, camYaw, camPitch, half,
							0.48F, 0.68F, 1.0F, fade * wispStrength);
				}
			}
		} finally {
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, prevBx, prevBy);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			GL11.glPopAttrib();
		}
	}

	private static void drawBillboardQuad(Tessellator tess, float x, float y, float z,
			float camYaw, float camPitch, float half, float r, float g, float b, float alpha) {
		GL11.glPushMatrix();
		GL11.glTranslatef(x, y, z);
		GL11.glRotatef(camYaw, 0.0F, 1.0F, 0.0F);
		GL11.glRotatef(camPitch, 1.0F, 0.0F, 0.0F);
		tess.startDrawingQuads();
		tess.setColorRGBA_F(r, g, b, alpha);
		tess.addVertexWithUV(-half,  half, 0.0D, 0.0D, 0.0D);
		tess.addVertexWithUV( half,  half, 0.0D, 1.0D, 0.0D);
		tess.addVertexWithUV( half, -half, 0.0D, 1.0D, 1.0D);
		tess.addVertexWithUV(-half, -half, 0.0D, 0.0D, 1.0D);
		tess.draw();
		GL11.glPopMatrix();
	}

	private static void drawClassicFallback(OcculusView view) {
		GL11.glPushMatrix();
		GL11.glTranslatef(0, 0.9F, 0);
		GL11.glRotatef(view.facing, 0.0F, 1.0F, 0.0F);
		GL11.glScalef(1.0F, -1F, -1F);
		GL11.glScalef(1.0F, 0.6F, 1.0F);
		float idle = 1 - view.open;
		float yaw = view.eyeYaw + idle * 8F * (float) Math.sin(view.time * 0.6F);
		float pitch = view.eyePitch + idle * 4F * (float) Math.sin(view.time * 0.43F);
		MODEL.renderModel(0.0625F, yaw, pitch);
		GL11.glPopMatrix();
	}

	private static float clamp01(float v) {
		return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
	}

	private static float clamp(float v, float min, float max) {
		return v < min ? min : (v > max ? max : v);
	}
}
