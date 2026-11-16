package am2.compass.client;

import am2.guis.controls.ArcaneChalkStyle;
import am2.items.ItemAstralCompass;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;

/**
 * Renders an Arcane Chalk style parchment overlay when holding a locked Astral Structure Compass,
 * showing live directional needle bearing, block distance, coordinates, and dimension match.
 */
@SideOnly(Side.CLIENT)
public final class AstralCompassHudRenderer extends Gui {

	private final Minecraft mc = Minecraft.getMinecraft();

	@SubscribeEvent
	public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
		if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
			return;
		}
		if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.gameSettings.showDebugInfo) {
			return;
		}
		EntityPlayer player = mc.thePlayer;
		ItemStack held = player.getCurrentEquippedItem();
		ItemAstralCompass.TargetData target = ItemAstralCompass.getTarget(held);
		if (target == null) {
			return;
		}

		ScaledResolution res = event.resolution;
		int width = 204;
		int height = 66;
		int left = 12;
		int top = 12;

		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glPushMatrix();
		try {
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glDepthMask(false);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

			ArcaneChalkStyle.panel(left, top, width, height);
			ArcaneChalkStyle.seal(mc, 11, left + 31, top + 33);

			boolean sameDim = player.worldObj.provider.dimensionId == target.dimension;
			double dx = (target.x + 0.5D) - player.posX;
			double dz = (target.z + 0.5D) - player.posZ;
			int distance = (int) Math.round(Math.hypot(dx, dz));
			boolean arrived = sameDim && distance <= 24;

			// Relative angle needle on seal
			if (sameDim) {
				double worldAngle = Math.atan2(dz, dx) - Math.PI / 2.0D;
				double playerYaw = Math.toRadians(MathHelper.wrapAngleTo180_float(player.rotationYaw));
				double rel = worldAngle - playerYaw;
				drawDirectionNeedle(left + 31, top + 33, rel, arrived ? 0x78e2ba : 0x67ccff);
			}

			String header = StatCollector.translateToLocal("am2.astral_compass.hud.title");
			mc.fontRenderer.drawStringWithShadow(header, left + 60, top + 10, 0x77caff);

			String nameLine = mc.fontRenderer.trimStringToWidth(target.name, width - 72);
			mc.fontRenderer.drawStringWithShadow(nameLine, left + 60, top + 22, 0xf0f6ff);

			if (sameDim) {
				String distLine = arrived
						? StatCollector.translateToLocal("am2.astral_compass.hud.arrived")
						: StatCollector.translateToLocalFormatted("am2.astral_compass.hud.distance", distance);
				mc.fontRenderer.drawStringWithShadow(distLine, left + 60, top + 35, arrived ? 0x78e2ba : 0xa1e5ff);
				String coordLine = StatCollector.translateToLocalFormatted("am2.astral_compass.hud.coords", target.x, target.z);
				mc.fontRenderer.drawStringWithShadow(coordLine, left + 60, top + 47, 0x9ab8d9);
			} else {
				String wrongDim = StatCollector.translateToLocalFormatted("am2.astral_compass.hud.dimension", target.dimension);
				mc.fontRenderer.drawStringWithShadow(wrongDim, left + 60, top + 35, 0xffce86);
				String coordLine = StatCollector.translateToLocalFormatted("am2.astral_compass.hud.coords", target.x, target.z);
				mc.fontRenderer.drawStringWithShadow(coordLine, left + 60, top + 47, 0x88a3c4);
			}
		} finally {
			GL11.glPopMatrix();
			GL11.glPopAttrib();
		}
	}

	private static void drawDirectionNeedle(float cx, float cy, double angleRad, int color) {
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glPushMatrix();
		GL11.glTranslatef(cx, cy, 0);
		GL11.glRotatef((float) Math.toDegrees(angleRad), 0, 0, 1);

		float r = ((color >> 16) & 255) / 255f;
		float g = ((color >> 8) & 255) / 255f;
		float b = (color & 255) / 255f;
		GL11.glColor4f(r, g, b, 0.95f);

		GL11.glBegin(GL11.GL_TRIANGLES);
		GL11.glVertex2f(0f, -19f);
		GL11.glVertex2f(-4f, -4f);
		GL11.glVertex2f(4f, -4f);
		GL11.glEnd();

		GL11.glPopMatrix();
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glColor4f(1f, 1f, 1f, 1f);
	}
}
