package am2.guis;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Look of the spell book wheel. Purely visual: it receives everything it shows and returns
 * nothing, so it can be redesigned without touching selection or networking.
 * Slot i is centred at angle -90 + i * 45 degrees (slot 0 at the top, clockwise).
 */
@SideOnly(Side.CLIENT)
public final class SpellWheelStyle {
	private static final float INNER = 26f, OUTER = 74f, ICON_RADIUS = 50f;
	private static final int PANEL = 0x10142B, EDGE = 0x3D4A86, LIT = 0x7FE3FF, GOLD = 0xFFD98A;
	private static final int TEXT = 0xE6E9FF, MUTED = 0x8F98C9;

	private SpellWheelStyle() {}

	static void draw(GuiScreen screen, FontRenderer font, RenderItem items, float cx, float cy, float appear,
					 ItemStack[] scrolls, String[] names, float[] manaCosts, int activeSlot, int hovered) {
		int slots = scrolls.length;
		float scale = 0.85f + 0.15f * appear;
		GL11.glPushMatrix();
		GL11.glTranslatef(cx, cy, 0);
		GL11.glScalef(scale, scale, 1);
		for (int i = 0; i < slots; i++) {
			boolean empty = scrolls[i] == null;
			int fill = i == hovered ? 0x253067 : i == activeSlot ? 0x1A2250 : PANEL;
			float alpha = (empty ? 0.45f : 0.82f) * appear;
			sector(i, slots, INNER, OUTER, fill, alpha);
			sector(i, slots, OUTER - 1.5f, OUTER, i == hovered ? LIT : i == activeSlot ? GOLD : EDGE, appear);
		}
		ring(INNER, 1.2f, EDGE, appear);
		for (int i = 0; i < slots; i++) {
			if (scrolls[i] == null) continue;
			double angle = Math.toRadians(-90 + i * 360.0 / slots);
			float x = (float) Math.cos(angle) * ICON_RADIUS, y = (float) Math.sin(angle) * ICON_RADIUS;
			float iconScale = i == hovered ? 1.35f : 1f;
			icon(screen, font, items, scrolls[i], x, y, iconScale);
		}
		int shown = hovered >= 0 ? hovered : activeSlot;
		if (shown >= 0 && shown < slots && names[shown] != null) {
			String name = font.trimStringToWidth(names[shown], 120);
			font.drawStringWithShadow(name, -font.getStringWidth(name) / 2, -OUTERTEXT, hovered >= 0 ? TEXT : GOLD);
			if (manaCosts[shown] >= 0) {
				String mana = StatCollector.translateToLocalFormatted("am2.gui.spellwheel.mana", Math.round(manaCosts[shown]));
				font.drawStringWithShadow(mana, -font.getStringWidth(mana) / 2, (int) OUTER + 6, MUTED);
			}
		}
		GL11.glPopMatrix();
		GL11.glColor4f(1, 1, 1, 1);
	}

	private static final int OUTERTEXT = (int) OUTER + 14;

	private static void icon(GuiScreen screen, FontRenderer font, RenderItem items, ItemStack stack, float x, float y, float scale) {
		GL11.glPushMatrix();
		GL11.glTranslatef(x, y, 0);
		GL11.glScalef(scale, scale, 1);
		RenderHelper.enableGUIStandardItemLighting();
		GL11.glEnable(GL12.GL_RESCALE_NORMAL);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		try {
			items.renderItemAndEffectIntoGUI(font, Minecraft.getMinecraft().getTextureManager(), stack, -8, -8);
		} catch (Throwable ignored) {
		}
		RenderHelper.disableStandardItemLighting();
		GL11.glDisable(GL12.GL_RESCALE_NORMAL);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glPopMatrix();
	}

	private static void sector(int index, int slots, float inner, float outer, int rgb, float alpha) {
		double span = Math.PI * 2 / slots, gap = 0.035;
		double from = Math.toRadians(-90) + index * span - span / 2 + gap, to = from + span - gap * 2;
		begin();
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		t.setColorRGBA_I(rgb, Math.max(0, Math.min(255, (int) (alpha * 255))));
		int steps = 8;
		for (int s = 0; s < steps; s++) {
			double a0 = from + (to - from) * s / steps, a1 = from + (to - from) * (s + 1) / steps;
			t.addVertex(Math.cos(a0) * outer, Math.sin(a0) * outer, 0);
			t.addVertex(Math.cos(a0) * inner, Math.sin(a0) * inner, 0);
			t.addVertex(Math.cos(a1) * inner, Math.sin(a1) * inner, 0);
			t.addVertex(Math.cos(a1) * outer, Math.sin(a1) * outer, 0);
		}
		t.draw();
		end();
	}

	private static void ring(float radius, float thickness, int rgb, float alpha) {
		begin();
		Tessellator t = Tessellator.instance;
		t.startDrawingQuads();
		t.setColorRGBA_I(rgb, Math.max(0, Math.min(255, (int) (alpha * 255))));
		int steps = 48;
		for (int s = 0; s < steps; s++) {
			double a0 = Math.PI * 2 * s / steps, a1 = Math.PI * 2 * (s + 1) / steps;
			t.addVertex(Math.cos(a0) * radius, Math.sin(a0) * radius, 0);
			t.addVertex(Math.cos(a0) * (radius - thickness), Math.sin(a0) * (radius - thickness), 0);
			t.addVertex(Math.cos(a1) * (radius - thickness), Math.sin(a1) * (radius - thickness), 0);
			t.addVertex(Math.cos(a1) * radius, Math.sin(a1) * radius, 0);
		}
		t.draw();
		end();
	}

	private static void begin() {
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
	}

	private static void end() {
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glColor4f(1, 1, 1, 1);
	}
}
