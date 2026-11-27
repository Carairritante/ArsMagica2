package am2.cinematics.client;

import am2.cinematics.scene.Scene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import org.lwjgl.opengl.GL11;

/** Camada 2D: fades, barras, títulos e a dica de pular. */
final class CinematicOverlay {
    private static final int BAR_TICKS = 10;

    private CinematicOverlay() {}

    static void draw(Minecraft mc, Scene scene, double time, String skipKeyName) {
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int width = res.getScaledWidth(), height = res.getScaledHeight();
        mc.entityRenderer.setupOverlayRendering();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);

            for (Scene.Event e : scene.events) {
                if (e.type != Scene.Type.FADE || !isActive(e, time)) continue;
                double u = (time - e.t) / e.duration;
                int alpha = (int) Math.round((e.from + (e.to - e.from) * u) * 255);
                if (alpha > 0) Gui.drawRect(0, 0, width, height, alpha << 24 | e.color);
            }

            double slide = Math.max(0, Math.min(1, Math.min(time, scene.duration - time) / BAR_TICKS));
            int bar = (int) Math.round(height * scene.letterbox * slide);
            if (bar > 0) {
                Gui.drawRect(0, 0, width, bar, 0xFF000000);
                Gui.drawRect(0, height - bar, width, height, 0xFF000000);
            }

            for (Scene.Event e : scene.events) {
                if (e.type == Scene.Type.TITLE && isActive(e, time)) title(mc.fontRenderer, e, time - e.t, width, height);
            }

            // Quem faz o ritual não pode pular, então não vê a dica.
            if (skipKeyName != null) {
                String hint = I18n.format("amcinematics.skip_hint", skipKeyName);
                GL11.glEnable(GL11.GL_BLEND);
                mc.fontRenderer.drawStringWithShadow(hint, width - mc.fontRenderer.getStringWidth(hint) - 6,
                        height - 12, 0x90FFFFFF);
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glColor4f(1, 1, 1, 1);
        }
    }

    private static boolean isActive(Scene.Event e, double time) {
        return time >= e.t && time < e.t + e.duration;
    }

    private static void title(FontRenderer font, Scene.Event e, double local, int width, int height) {
        double in = Math.min(1, local / 10), out = Math.min(1, (e.duration - local) / 10);
        int alpha = (int) (255 * Math.max(0, Math.min(in, out)));
        // O FontRenderer trata alfa quase zero como opaco.
        if (alpha < 8) return;
        // Efeito de digitação: o título aparece letra por letra.
        int visible = Math.min(e.text.length(), (int) local + 1);
        int y = (int) (height * e.y);
        line(font, e.text, visible, width, y, 2.0F, alpha, e.glitch, local);
        if (!e.subtitle.isEmpty() && local > e.text.length()) {
            line(font, e.subtitle, e.subtitle.length(), width, y + 24, 1.0F, alpha, e.glitch, local);
        }
    }

    private static void line(FontRenderer font, String text, int visible, int width, int y, float scale, int alpha,
            boolean glitch, double local) {
        int fullWidth = font.getStringWidth(text);
        if (fullWidth == 0) return;
        float fit = Math.min(scale, (width - 20) / (float) fullWidth);
        String shown = text.substring(0, visible);
        float x = (width - fullWidth * fit) / 2;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        GL11.glScalef(fit, fit, 1);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (glitch) {
            int shift = 1 + (int) (Math.abs(Math.sin(local * 2.3)) * 2);
            font.drawString(shown, -shift, 0, (alpha * 3 / 4) << 24 | 0xFF2060);
            GL11.glEnable(GL11.GL_BLEND);
            font.drawString(shown, shift, 0, (alpha * 3 / 4) << 24 | 0x20E0FF);
            GL11.glEnable(GL11.GL_BLEND);
        }
        font.drawStringWithShadow(shown, 0, 0, alpha << 24 | 0xFFFFFF);
        GL11.glPopMatrix();
    }
}
