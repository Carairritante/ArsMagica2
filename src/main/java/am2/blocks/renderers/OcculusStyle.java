package am2.blocks.renderers;

import am2.models.ModelOcculus;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * Look of the Oculus block. Purely visual: it receives an {@link OcculusView} and draws,
 * with the block texture already bound and the origin at the block's bottom centre
 * (no rotation applied). The classic look stays in {@link #drawClassic}.
 * The remastered look starts as the classic model with an eye that follows the player;
 * a new model can replace {@link #drawRemaster} freely.
 */
@SideOnly(Side.CLIENT)
public final class OcculusStyle {
	private static final ModelOcculus MODEL = new ModelOcculus();

	private OcculusStyle() {}

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
		GL11.glPushMatrix();
		GL11.glTranslatef(0, 0.9F, 0);
		GL11.glRotatef(view.facing, 0.0F, 1.0F, 0.0F);
		GL11.glScalef(1.0F, -1F, -1F);
		GL11.glScalef(1.0F, 0.6F, 1.0F);
		// A slow drift while resting, so an idle Oculus still looks alive.
		float idle = 1 - view.open;
		float yaw = view.eyeYaw + idle * 8F * (float) Math.sin(view.time * 0.6F);
		float pitch = view.eyePitch + idle * 4F * (float) Math.sin(view.time * 0.43F);
		MODEL.renderModel(0.0625F, yaw, pitch);
		GL11.glPopMatrix();
	}
}
