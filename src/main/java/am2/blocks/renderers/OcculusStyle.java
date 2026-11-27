package am2.blocks.renderers;

import am2.client.vfx.BlockbenchRigModel;
import am2.models.ModelOcculus;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

/**
 * Look of the Oculus block. Purely visual: it receives an {@link OcculusView} and draws,
 * with the block texture already bound and the origin at the block's bottom centre
 * (no rotation applied). The classic look (Classic_Models) stays in {@link #drawClassic}.
 * The remastered look renders the faithful AM2 spherical Blockbench Oculus ({@code models/blocks/occulus.bbmodel})
 * preserving the original stone/indigo/crimson/amber palette while adding smooth eye tracking,
 * independent slit-pupil focus/saccades, curved organic eyelid blinking, and a subtle real
 * additive amber iris glow ({@code textures/blocks/custom/occulus_glow.png}).
 */
@SideOnly(Side.CLIENT)
public final class OcculusStyle {
	private static final ModelOcculus MODEL = new ModelOcculus();
	private static final ResourceLocation RIG_LOC = new ResourceLocation("arsmagica2", "models/blocks/occulus.bbmodel");
	private static final ResourceLocation GLOW_LOC = new ResourceLocation("arsmagica2", "textures/blocks/custom/occulus_glow.png");
	private static final float EYE_CENTER_Y = 12.85F / 16.0F;
	private static final float IRIS_GLOW_Z = -2.62F / 16.0F;

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
				loaded.setEmissiveBones("eye_iris");
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

		float glowAlpha = 0.0F;

		if (view.inventory) {
			EYE_PIVOT_OV.dy = 0.12F;
			LID_TOP_OV.drx = 2.0F;
			LID_BOT_OV.drx = -2.0F;
		} else {
			float t = view.time;
			float open = clamp01(view.open);
			float awake = clamp01(view.awake);
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

			// Curved organic eyelids: gentle upper relaxation when idle (never a straight angry visor!),
			// wide open when watching, plus a quick natural blink every ~5.8s
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

			// Real in-game subtle additive amber iris aura (dims cleanly during blink)
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

		if (!view.inventory && glowAlpha > 0.01F) {
			drawIrisGlow(glowAlpha);
		}
		GL11.glPopMatrix();
	}

	/**
	 * Renders a subtle, honest additive amber glow right over the iris plane in the eye's local
	 * space (only visible from the front hemisphere, fading automatically when the eye blinks).
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

			float half = 0.225F;
			Tessellator tess = Tessellator.instance;
			tess.startDrawingQuads();
			tess.setColorRGBA_F(1.0F, 0.72F, 0.22F, alpha);
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
