package am2.items.renderers;

import am2.AMCore;
import am2.client.vfx.BlockbenchRigModel;
import am2.guis.grimoire.GrimoirePrefs;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.Collections;
import java.util.Map;

/**
 * The remastered Arcane Guardian Spellbook (models/items/arcane_spellbook.bbmodel, made with
 * tools/generate_arcane_spellbook.py). Plays the model's own "open" animation from the
 * casting open progress. Model space: y up, cover towards -Z, hinge at x = 0, 1 unit = 1 px.
 * With Classic_Models on, or if the model cannot load, callers draw the original book.
 */
@SideOnly(Side.CLIENT)
public final class ArcaneSpellbook3D {
	private static final ResourceLocation MODEL = new ResourceLocation("arsmagica2", "models/items/arcane_spellbook.bbmodel");
	private static final ResourceLocation CIRCLE = new ResourceLocation("arsmagica2", "textures/models/arcane_spellbook_circle.png");
	/** Length of the "open" clip; the covers are fully open at 0.85 s, the leaves settle by 1.2 s. */
	private static final float OPEN_CLIP = 1.2f;
	private static final Map<String, BlockbenchRigModel.BoneOverride> NO_POSE = Collections.emptyMap();

	private static BlockbenchRigModel model;
	private static boolean loadAttempted;

	private ArcaneSpellbook3D() {}

	/** True when the remastered book should be drawn instead of the classic one. */
	public static boolean active() {
		if (AMCore.config.classicModels()) return false;
		if (!loadAttempted) {
			loadAttempted = true;
			model = BlockbenchRigModel.load(MODEL, "am2_arcane_spellbook");
		}
		return model != null;
	}

	/**
	 * In the hand, inside the spellbook hand frame (y down, reader at +Z), so the open book
	 * lines up with the floating affinity icon. Same frame as the 3D Compendium.
	 */
	static void renderHeld(float open, float timeSec) {
		GL11.glPushMatrix();
		GL11.glScalef(0.8f, 0.8f, 0.8f);
		// Closed, the book sits on its spine; open, centred on it.
		GL11.glTranslatef(7.5f * (1 - open) / 16f, 0, 0);
		GL11.glRotatef(180, 1, 0, 0);
		model.render("open", open * OPEN_CLIP, NO_POSE, 1, 1, 1, 1);
		if (open > 0.55f && GrimoirePrefs.get().effects) drawCircle(open, timeSec);
		GL11.glPopMatrix();
	}

	/** Dropped in the world: closed, gently floating (the model's "idle" clip). */
	static void renderEntity(float timeSec) {
		GL11.glPushMatrix();
		GL11.glTranslatef(0, 0.12f, 0);
		GL11.glScalef(0.62f, 0.62f, 0.62f);
		GL11.glTranslatef(4.5f / 16f, 0, 0);
		model.render("idle", timeSec, NO_POSE, 1, 1, 1, 1);
		GL11.glPopMatrix();
	}

	/** Hotbar and inventory, inside Forge's block-style inventory frame: closed, cover to the viewer. */
	static void renderInventory() {
		GL11.glPushMatrix();
		GL11.glRotatef(180, 0, 1, 0);
		GL11.glScalef(0.95f, 0.95f, 0.95f);
		GL11.glTranslatef(4.5f / 16f, 1f / 16f, 0);
		model.render("open", 0, NO_POSE, 1, 1, 1, 1);
		GL11.glPopMatrix();
	}

	/** A rune circle behind the open pages (towards the reader is -Z in model space). */
	private static void drawCircle(float open, float timeSec) {
		float ring = Math.min(1, (open - 0.55f) / 0.45f);
		float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glDepthMask(false);
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
		Minecraft.getMinecraft().renderEngine.bindTexture(CIRCLE);
		float px = 1 / 16f;
		quad(0, 1.2f * px, -3.6f * px, 6.2f * px * ring, timeSec * 22, ring * 0.8f);
		quad(0, 1.2f * px, -4.8f * px, 3.4f * px * ring, -timeSec * 40, ring * 0.65f);
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
		GL11.glPopAttrib();
	}

	private static void quad(float x, float y, float z, float radius, float degrees, float alpha) {
		GL11.glPushMatrix();
		GL11.glTranslatef(x, y, z);
		GL11.glRotatef(degrees, 0, 0, 1);
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		t.setColorRGBA_F(1, 1, 1, alpha);
		t.addVertexWithUV(-radius, -radius, 0, 0, 1);
		t.addVertexWithUV(radius, -radius, 0, 1, 1);
		t.addVertexWithUV(radius, radius, 0, 1, 0);
		t.addVertexWithUV(-radius, radius, 0, 0, 0);
		t.draw();
		GL11.glPopMatrix();
	}
}
