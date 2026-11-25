package am2.blocks.renderers;

import am2.blocks.tileentities.TileEntityVocalAura;
import am2.texture.ResourceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Renders the floating cyan-gold harmonic rings of the Vocal Aura (counterpart to RenderOtherworldAura).
 */
public class RenderVocalAura extends TileEntitySpecialRenderer {

	private final ResourceLocation auraTexture;

	public RenderVocalAura() {
		auraTexture = new ResourceLocation("arsmagica2", ResourceManager.getCustomBlockTexturePath("sc_auto.png"));
	}

	@Override
	public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
		if (te instanceof TileEntityVocalAura) {
			doRender((TileEntityVocalAura) te, x, y, z, partialTicks);
		}
	}

	private void doRender(TileEntityVocalAura tile, double x, double y, double z, float partialTicks) {
		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_TEXTURE_BIT);

		GL11.glTranslatef((float) x + 0.5f, (float) y + 0.5f, (float) z + 0.5f);
		GL11.glDepthMask(false);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		RenderHelper.disableStandardItemLighting();

		Tessellator tessellator = Tessellator.instance;
		boolean inWorld = !(tile.xCoord == 0 && tile.yCoord == 0 && tile.zCoord == 0 && tile.getWorldObj() == null);

		float scale = 1.25f;
		float direction = 1.0f;
		int layer = 0;
		while (scale > 0.0f) {
			renderHarmonicLayer(tessellator, direction, scale, partialTicks, inWorld, layer);
			scale -= 0.25f;
			direction *= -1.0f;
			layer++;
		}

		RenderHelper.enableStandardItemLighting();
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glDepthMask(true);
		GL11.glPopAttrib();
		GL11.glPopMatrix();
	}

	private void renderHarmonicLayer(Tessellator tessellator, float direction, float scale, float partialTicks, boolean rotateToPlayer, int layer) {
		GL11.glPushMatrix();
		if (rotateToPlayer) {
			GL11.glRotatef(180F - RenderManager.instance.playerViewY, 0.0F, 1.0F, 0.0F);
			GL11.glRotatef(-RenderManager.instance.playerViewX, 1.0F, 0.0F, 0.0F);
		} else {
			GL11.glRotatef(35.0F, 0.0F, 1.0F, 0.0F);
			GL11.glTranslatef(0.0F, -0.25F, 0.0F);
		}

		Minecraft mc = Minecraft.getMinecraft();
		mc.renderEngine.bindTexture(auraTexture);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		// Alternating cyan-azure and warm gold resonance hues
		if ((layer & 1) == 0) {
			GL11.glColor4f(0.18F, 0.90F, 1.00F, 0.92F);
		} else {
			GL11.glColor4f(1.00F, 0.82F, 0.30F, 0.88F);
		}

		float ticks = (mc.thePlayer != null ? mc.thePlayer.ticksExisted : (System.currentTimeMillis() % 100000L) / 50.0F) + partialTicks;
		GL11.glTranslatef(0.0F, 0.25F, 0.0F);
		GL11.glRotatef(ticks * (scale * 2.2F) * direction, 0.0F, 0.0F, 1.0F);
		float pulse = (float) Math.abs(Math.sin(System.currentTimeMillis() / 900.0 * scale)) * 0.65F + 0.38F;
		GL11.glScalef(scale * pulse, scale * pulse, scale * pulse);
		GL11.glTranslatef(0.0F, -0.25F, 0.0F);
		renderSprite(tessellator);
		GL11.glPopMatrix();
	}

	private void renderSprite(Tessellator tessellator) {
		try {
			tessellator.startDrawingQuads();
			tessellator.setBrightness(15728863);
			tessellator.addVertexWithUV(-0.5D, -0.25D, 0.0D, 0.0D, 1.0D);
			tessellator.addVertexWithUV(0.5D, -0.25D, 0.0D, 1.0D, 1.0D);
			tessellator.addVertexWithUV(0.5D, 0.75D, 0.0D, 1.0D, 0.0D);
			tessellator.addVertexWithUV(-0.5D, 0.75D, 0.0D, 0.0D, 0.0D);
			tessellator.draw();
		} catch (Throwable ignored) {
		}
	}
}
