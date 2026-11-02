package am2.ritual.client;

import am2.blocks.BlocksCommonProxy;
import am2.guis.GuiRitualArchitect;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/** A read-only parchment of light, using the actual Wizard Chalk glyphs. */
@SideOnly(Side.CLIENT)
final class RitualArchitectHud extends Gui {
    static final int TEXT = 0xd4e4f6, MUTED = 0x88a3c4, BLUE = 0x77caff;
    static final int GOOD = 0x78e2ba, WARN = 0xffce86, BAD = 0xff929f;
    private final Minecraft mc;

    static final class Row {
        final String text;
        final int color;
        final boolean heading;
        private Row(String text, int color, boolean heading) {
            this.text = text; this.color = color; this.heading = heading;
        }
        static Row text(String text, int color) { return new Row(text, color, false); }
        static Row heading(String key) { return new Row(GuiRitualArchitect.text(key), BLUE, true); }
    }

    RitualArchitectHud(Minecraft mc) { this.mc = mc; }

    int[] draw(ScaledResolution screen, String ritual, int glyph, int correct, int total, int blocked,
              List<Row> content) {
        int width = Math.min(252, Math.max(170, (int)((screen.getScaledWidth() / 2f - 24) / 0.9f)));
        int textWidth = width - 42;
        List<String> title = mc.fontRenderer.listFormattedStringToWidth(ritual, width - 70);
        int titleLines = Math.min(3, title.size());
        int headerBottom = Math.max(58, 27 + titleLines * 11);
        int bodyTop = headerBottom + (total >= 0 ? 33 : 10);
        List<Row> rows = new ArrayList<Row>();
        for (Row row : content) {
            if (row.heading) rows.add(row);
            else {
                List<String> wrapped = mc.fontRenderer.listFormattedStringToWidth(row.text, textWidth);
                for (String line : wrapped) rows.add(Row.text(line, row.color));
            }
        }
        int maxHeight = Math.max(140, (int)((screen.getScaledHeight() - 82) / 0.76f));
        int available = maxHeight - bodyTop - 40;
        int used = 0, count = 0;
        for (Row row : rows) {
            int step = row.heading ? 17 : 11;
            if (used + step > available - 11) break;
            used += step; count++;
        }
        if (count < rows.size()) {
            while (count > 0 && rows.get(count - 1).heading) { used -= 17; count--; }
            rows = new ArrayList<Row>(rows.subList(0, count));
            rows.add(Row.text(GuiRitualArchitect.text("details_compendium"), MUTED));
            used += 11;
        }
        int height = bodyTop + used + 37;
        ArcaneChalkHudSettings settings = ArcaneChalkHudSettings.get();
        float scale = Math.min(0.9f * settings.percent() / 100f,
                Math.min((screen.getScaledHeight() - 82f) / height, (screen.getScaledWidth() - 18f) / width));
        int panelWidth = (int)Math.ceil(width * scale), panelHeight = (int)Math.ceil(height * scale);
        int left = settings.left(screen.getScaledWidth(), panelWidth);
        int top = settings.top(screen.getScaledHeight(), panelHeight);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(left, top, 0);
            GL11.glScalef(scale, scale, 1);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glLineWidth(1f);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            panel(width, height);

            // The reserved rune rail is outside the text column.
            Gui.drawRect(width - 23, 15, width - 22, height - 17, 0x383a8fc9);
            for (int y = 23, n = 0; y < height - 23; y += 25, n++)
                glyph((glyph + n * 3) & 15, width - 19, y, 12, 0x55baff, 0.28f);
            glyph(glyph, 18, 16, 31, 0x31a9ff, 0.13f);
            ring(33.5f, 31.5f, 21, 0x245394, 0.7f);
            ring(33.5f, 31.5f, 18, 0x5dcaff, 0.85f);
            glyph(glyph, 20, 18, 27, 0xa1e5ff, 1f);
            text(GuiRitualArchitect.text("hud_title"), 62, 13, BLUE);
            for (int i = 0; i < titleLines; i++)
                text(title.get(i), 62, 27 + i * 11, 0xf0f6ff);
            if (title.size() > titleLines)
                text("...", width - 38, 27 + (titleLines - 1) * 11, MUTED);
            Gui.drawRect(14, headerBottom + 2, width - 31, headerBottom + 3, 0x55446eab);

            if (total >= 0) {
                String progress = GuiRitualArchitect.text("structure", correct, total);
                text(progress, 15, headerBottom + 10, TEXT);
                String percent = (total == 0 ? 0 : correct * 100 / total) + "%";
                text(percent, width - 33 - mc.fontRenderer.getStringWidth(percent), headerBottom + 10,
                        total > 0 && correct == total ? GOOD : BLUE);
                int barLeft = 15, barRight = width - 32, barTop = headerBottom + 23;
                Gui.drawRect(barLeft, barTop, barRight, barTop + 4, 0xff050d1b);
                int complete = total == 0 ? 0 : (barRight - barLeft) * correct / total;
                if (complete > 0) {
                    Gui.drawRect(barLeft, barTop, barLeft + complete, barTop + 4,
                            correct == total ? 0xff48cfa1 : 0xff247de4);
                    Gui.drawRect(barLeft, barTop, barLeft + complete, barTop + 1,
                            correct == total ? 0xffb0ffe1 : 0xff9be8ff);
                }
                if (blocked > 0 && total > 0) {
                    int obstruction = Math.min(barRight - barLeft - complete,
                            Math.max(1, (barRight - barLeft) * blocked / total));
                    Gui.drawRect(barRight - obstruction, barTop, barRight, barTop + 4, 0xffd66a82);
                }
            }
            int y = bodyTop;
            for (Row row : rows) {
                if (row.heading) {
                    y += 5;
                    int labelWidth = mc.fontRenderer.getStringWidth(row.text);
                    text(row.text, 15, y, BLUE);
                    Gui.drawRect(21 + labelWidth, y + 4, width - 32, y + 5, 0x453f7db6);
                    y += 12;
                } else {
                    text(row.text, 15, y, row.color);
                    y += 11;
                }
            }
            int footer = height - 30;
            Gui.drawRect(14, footer - 5, width - 31, footer - 4, 0x45446eab);
            int legendWidth = (width - 42) / 3;
            String[] labels = {"hud_correct", "hud_missing", "hud_blocked"};
            int[] colors = {GOOD, BLUE, BAD};
            for (int i = 0; i < labels.length; i++) {
                int legendX = 15 + i * legendWidth;
                String label = mc.fontRenderer.trimStringToWidth(GuiRitualArchitect.text(labels[i]), legendWidth - 10);
                Gui.drawRect(legendX, footer + 2, legendX + 3, footer + 5, 0xff000000 | colors[i]);
                text(label, legendX + 7, footer, MUTED);
            }
            glyph((glyph + 7) & 15, 15, height - 16, 8, 0x80c8ff, 0.85f);
            String hint = GuiRitualArchitect.text("menu_hint");
            float hintScale = Math.min(1f, (width - 42f) / Math.max(1, mc.fontRenderer.getStringWidth(hint)));
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(28, height - 17, 0);
                GL11.glScalef(hintScale, hintScale, 1);
                text(hint, 0, 0, 0x91b4d9);
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
        return new int[]{left, top, panelWidth, panelHeight};
    }

    private void panel(int width, int height) {
        int opacity = 100 - ArcaneChalkHudSettings.get().transparency();
        int alpha = Math.round(opacity * 255f / 100f) << 24;
        Gui.drawRect(1, 5, width + 3, height + 3, Math.round(opacity * 0.44f) << 24);
        drawGradientRect(1, 5, width - 1, height - 5, alpha | 0x0b1c35, alpha | 0x050e20);
        Gui.drawRect(6, 1, width - 6, 5, alpha | 0x0b1c35);
        Gui.drawRect(6, height - 5, width - 6, height - 1, alpha | 0x050e20);
        Gui.drawRect(7, 0, width - 7, 1, 0xff4a9dde);
        Gui.drawRect(7, height - 1, width - 7, height, 0xff284f83);
        Gui.drawRect(0, 7, 1, height - 7, 0xff386fa9);
        Gui.drawRect(width - 1, 7, width, height - 7, 0xff284f83);
        Gui.drawRect(3, 7, 4, height - 7, 0x3d63adf4);
        Gui.drawRect(10, 3, Math.min(88, width - 10), 5, 0xff51bbff);
        Gui.drawRect(width - 58, height - 4, width - 10, height - 3, 0xff328bd1);
        line(0, 7, 7, 0, 0x65caff);
        line(width - 7, 0, width, 7, 0x4a9dde);
        line(0, height - 7, 7, height, 0x386fa9);
        line(width - 7, height, width, height - 7, 0x386fa9);
    }

    private void text(String value, int x, int y, int color) {
        GL11.glColor4f(1, 1, 1, 1);
        mc.fontRenderer.drawStringWithShadow(value, x, y, color);
    }

    private void glyph(int variant, int x, int y, int size, int color, float alpha) {
        IIcon icon = BlocksCommonProxy.wizardChalk.getIcon(1, variant & 15);
        if (icon == null) return;
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f,
                (color & 255) / 255f, alpha);
        drawTexturedModelRectFromIcon(x, y, icon, size, size);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void line(float x1, float y1, float x2, float y2, int color) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f,
                (color & 255) / 255f, 1);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1); GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void ring(float x, float y, float radius, int color, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f,
                (color & 255) / 255f, alpha);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 32; i++) {
            double angle = i * Math.PI / 16;
            GL11.glVertex2d(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius);
        }
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
