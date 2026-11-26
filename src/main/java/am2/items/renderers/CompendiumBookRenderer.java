package am2.items.renderers;

import am2.client.vfx.BlockbenchRigModel;
import am2.guis.grimoire.GrimoirePrefs;
import am2.lore.ArcaneCompendium;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.HashMap;
import java.util.Map;

/**
 * Arcane Compendium as a 3D book in the hand and in the world, opening towards the reader,
 * with a floating circle and sparks. The hotbar uses the flat icon plus a discovery sparkle.
 * Model and textures come from tools/generate_compendium_book.py.
 */
@SideOnly(Side.CLIENT)
public final class CompendiumBookRenderer implements IItemRenderer {
	private static final ResourceLocation MODEL = new ResourceLocation("arsmagica2", "models/items/arcane_compendium.bbmodel");
	private static final ResourceLocation CIRCLE = new ResourceLocation("arsmagica2", "textures/models/compendium_circle.png");
	private static final int CYAN = 0x7AE8FF, VIOLET = 0xBC84FF, GOLD = 0xFFE082;

	private final Map<String, BlockbenchRigModel.BoneOverride> pose = new HashMap<String, BlockbenchRigModel.BoneOverride>();
	private final ModelBiped biped = new ModelBiped();
	private BlockbenchRigModel model;
	private boolean loadAttempted;

	public CompendiumBookRenderer() {
		CompendiumBookState.init();
		for (String bone : new String[]{"back_half", "front_half", "spine", "leaf1", "leaf2", "leaf3"})
			pose.put(bone, new BlockbenchRigModel.BoneOverride());
	}

	@Override
	public boolean handleRenderType(ItemStack item, ItemRenderType type) {
		return type == ItemRenderType.INVENTORY || type == ItemRenderType.EQUIPPED
				|| type == ItemRenderType.EQUIPPED_FIRST_PERSON || type == ItemRenderType.ENTITY;
	}

	@Override
	public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
		if (type == ItemRenderType.INVENTORY) return false;
		if (type == ItemRenderType.ENTITY)
			return helper == ItemRendererHelper.ENTITY_ROTATION || helper == ItemRendererHelper.ENTITY_BOBBING;
		// Same hand frame as the animated spellbooks.
		return true;
	}

	@Override
	public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
		if (type == ItemRenderType.INVENTORY) {
			renderIcon(item);
			return;
		}
		if (!loadAttempted) {
			loadAttempted = true;
			model = BlockbenchRigModel.load(MODEL, "am2_arcane_compendium");
		}
		if (model == null) return;

		Minecraft mc = Minecraft.getMinecraft();
		EntityLivingBase holder = data.length > 1 && data[1] instanceof EntityLivingBase ? (EntityLivingBase) data[1] : null;
		if (holder == null && type == ItemRenderType.EQUIPPED_FIRST_PERSON) holder = mc.thePlayer;
		if (holder != null && holder.isPotionActive(Potion.invisibility.id)) return;

		boolean held = type != ItemRenderType.ENTITY;
		boolean local = held && holder != null && holder == mc.thePlayer;
		float open = local ? CompendiumBookState.localOpenAmount()
				: held && holder instanceof EntityPlayer ? CompendiumBookState.remoteOpenAmount(holder.getEntityId()) : 0;
		float time = (Minecraft.getSystemTime() % 3600000L) / 1000f;
		boolean effects = GrimoirePrefs.get().effects;
		float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;

		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		try {
			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
			GL11.glColor4f(1, 1, 1, 1);
			GL11.glPushMatrix();
			if (type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
				float bob = effects ? (float) Math.sin(time * 1.8f) * 0.015f : 0;
				GL11.glTranslatef(0.85f - 0.55f * open, 0.65f + 0.45f * open + bob, -0.05f - 0.30f * open);
				GL11.glRotatef(35.0f - 13.0f * open, 1, 0, 0);
				GL11.glRotatef(-45.0f + 10.0f * open, 0, 1, 0);
				GL11.glRotatef(180.0f, 0, 0, 1);
				float scale = 0.98f + 0.14f * open;
				GL11.glScalef(scale, scale, scale);
			} else if (type == ItemRenderType.EQUIPPED) {
				GL11.glTranslatef(0.5f, 0.5f, 0.5f);
				GL11.glScalef(-1.0f / 0.375f, -1.0f / 0.375f, 1.0f / 0.375f);
				GL11.glRotatef(-45, 0, 1, 0);
				GL11.glRotatef(-20, 1, 0, 0);
				GL11.glTranslatef(0, -0.1875f, 0.3125f);
				GL11.glTranslatef(0, 0, -0.12f - 0.08f * open);
				GL11.glRotatef(180, 0, 1, 0);
				float scale = 0.72f + 0.08f * open;
				GL11.glScalef(scale, scale, scale);
			} else {
				float scale = 0.7f;
				GL11.glTranslatef(0, 0.12f, 0);
				GL11.glScalef(scale, scale, scale);
			}
			if (held) {
				// Model space (y up, cover towards -Z) into the hand frame (y down, reader at +Z).
				GL11.glTranslatef(7 * (1 - open) / 16f, 0, 0);
				GL11.glRotatef(180, 1, 0, 0);
			} else {
				GL11.glTranslatef(4 / 16f, 0, 0);
			}
			applyPose(open);
			model.render(null, 0, pose, 1, 1, 1, 1);
			if (effects) drawMagic(open, time, held);
			GL11.glPopMatrix();

			if (type == ItemRenderType.EQUIPPED_FIRST_PERSON && holder == mc.thePlayer) drawArm(mc, open);
		} finally {
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
			GL11.glPopAttrib();
			GL11.glPopMatrix();
		}
	}

	/** Keep in step with pose() in the preview script used to design the model. */
	private void applyPose(float open) {
		for (BlockbenchRigModel.BoneOverride bone : pose.values()) bone.reset();
		pose.get("back_half").dry = -10 * open;
		pose.get("front_half").dry = -170 * open;
		BlockbenchRigModel.BoneOverride spine = pose.get("spine");
		spine.sx = 1 + 2.4f * open;
		spine.sz = 1 - 0.875f * open;
		spine.dx = -1.2f * open;
		spine.dz = 1.4f * open;
		boolean flying = open > 0.03f && open < 0.97f;
		for (int i = 1; i <= 3; i++) {
			BlockbenchRigModel.BoneOverride leaf = pose.get("leaf" + i);
			float progress = Math.max(0, Math.min(1, open * (1.9f - i * 0.22f) - 0.12f * i));
			if (flying) leaf.dry = -10 - 160 * progress;
			else leaf.sx = 0;
		}
	}

	/** Gem glow while closed; floating circle and rising sparks while open. */
	private void drawMagic(float open, float time, boolean held) {
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glDepthMask(false);
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
		float px = 1 / 16f;
		float closed = Math.max(0, 1 - open * 3);

		GL11.glDisable(GL11.GL_TEXTURE_2D);
		if (closed > 0) {
			float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.6f);
			diamond(-4.2f * px, 0, -2.35f * px, (1.5f + pulse * 0.9f) * px, VIOLET, closed * (0.25f + 0.3f * pulse));
			diamond(-4.2f * px, 0, -2.4f * px, (0.7f + pulse * 0.3f) * px, CYAN, closed * 0.5f);
		}
		if (held) {
			int count = open > 0.5f ? 14 : 4;
			for (int i = 0; i < count; i++) {
				float life = (time * (0.35f + (i % 5) * 0.06f) + i * 0.173f) % 1f;
				float spread = open > 0.5f ? 7f : 3.5f;
				float x = (float) Math.sin(i * 12.9898f) * spread - 4.2f * (1 - open);
				float y = -3 + life * 12 + (float) Math.sin(i * 3.7f);
				float z = -2.6f - (i % 4) * 0.6f;
				float size = (0.18f + (i % 3) * 0.08f) * (1 - life);
				diamond(x * px, y * px, z * px, size * px * 1.6f, i % 3 == 0 ? GOLD : i % 3 == 1 ? CYAN : VIOLET,
						(float) Math.sin(life * Math.PI) * 0.9f);
			}
		}
		GL11.glEnable(GL11.GL_TEXTURE_2D);

		float ring = Math.max(0, (open - 0.55f) / 0.45f);
		if (ring > 0) {
			Minecraft.getMinecraft().renderEngine.bindTexture(CIRCLE);
			circle(0, 1.2f * px, -3.4f * px, 5.6f * px * ring, time * 22, ring * 0.85f);
			circle(0, 1.2f * px, -4.6f * px, 3.1f * px * ring, -time * 40, ring * 0.7f);
		}
		GL11.glDepthMask(true);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glColor4f(1, 1, 1, 1);
	}

	private void circle(float x, float y, float z, float radius, float degrees, float alpha) {
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

	private void diamond(float x, float y, float z, float radius, int color, float alpha) {
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		t.setColorRGBA_I(color, Math.max(0, Math.min(255, (int) (alpha * 255))));
		t.addVertex(x - radius, y, z);
		t.addVertex(x, y - radius, z);
		t.addVertex(x + radius, y, z);
		t.addVertex(x, y + radius, z);
		t.draw();
	}

	/** Vanilla hides the arm while an item is held; draw it under the book like the spellbooks do. */
	private void drawArm(Minecraft mc, float open) {
		GL11.glPushMatrix();
		GL11.glTranslatef(0.55f - 0.10f * open, -0.40f + 0.075f * open, 0.52f);
		GL11.glRotatef(-30, 1, 0, 0);
		GL11.glScalef(1.7f, 1.7f, 1.7f);
		GL11.glTranslatef(0.3125f, -0.125f, 0);
		mc.renderEngine.bindTexture(mc.thePlayer.getLocationSkin());
		// Go through the vanilla entry point so replacement player models (Futura3D,
		// AkashicPlayer) draw their own arm instead of the default one.
		Render renderer = RenderManager.instance.getEntityRenderObject(mc.thePlayer);
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
		if (renderer instanceof RenderPlayer) {
			((RenderPlayer) renderer).renderFirstPersonArm(mc.thePlayer);
		} else {
			biped.bipedRightArm.rotateAngleX = 0;
			biped.bipedRightArm.rotateAngleY = 0;
			biped.bipedRightArm.rotateAngleZ = 0;
			biped.bipedRightArm.render(0.0625f);
		}
		GL11.glPopAttrib();
		GL11.glPopMatrix();
	}

	private void renderIcon(ItemStack item) {
		IIcon icon = item.getIconIndex();
		if (icon == null) return;
		Minecraft mc = Minecraft.getMinecraft();
		mc.renderEngine.bindTexture(TextureMap.locationItemsTexture);
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glColor4f(1, 1, 1, 1);
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		t.addVertexWithUV(0, 16, 0, icon.getMinU(), icon.getMaxV());
		t.addVertexWithUV(16, 16, 0, icon.getMaxU(), icon.getMaxV());
		t.addVertexWithUV(16, 0, 0, icon.getMaxU(), icon.getMinV());
		t.addVertexWithUV(0, 0, 0, icon.getMinU(), icon.getMinV());
		t.draw();
		if (ArcaneCompendium.freshDiscovery) {
			float time = (Minecraft.getSystemTime() % 100000L) / 1000f;
			float pulse = GrimoirePrefs.get().effects ? 0.5f + 0.5f * (float) Math.sin(time * 4) : 1;
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glDisable(GL11.GL_CULL_FACE);
			diamond(13, 3, 0, 2.2f + pulse * 1.3f, GOLD, 0.55f + 0.4f * pulse);
			diamond(13, 3, 0, 1.2f, 0xFFFFFF, 0.9f);
		}
		GL11.glPopAttrib();
	}
}
