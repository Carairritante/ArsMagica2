package am2.guis.controls;

import am2.blocks.BlocksCommonProxy;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class ArcaneChalkStyle extends Gui {
    private static final ArcaneChalkStyle PAINTER = new ArcaneChalkStyle();
    private ArcaneChalkStyle() {}

    public static void panel(int x, int y, int width, int height) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, y, 0);
            Gui.drawRect(2, 6, width + 3, height + 3, 0x48000000);
            PAINTER.drawGradientRect(1, 6, width - 1, height - 6, 0xf70b1c35, 0xf7050e20);
            Gui.drawRect(7, 1, width - 7, 6, 0xf70b1c35);
            Gui.drawRect(7, height - 6, width - 7, height - 1, 0xf7050e20);
            Gui.drawRect(7, 0, width - 7, 1, 0xff4a9dde);
            Gui.drawRect(7, height - 1, width - 7, height, 0xff284f83);
            Gui.drawRect(0, 7, 1, height - 7, 0xff386fa9);
            Gui.drawRect(width - 1, 7, width, height - 7, 0xff284f83);
            Gui.drawRect(3, 8, 4, height - 8, 0x3d63adf4);
            Gui.drawRect(12, 3, Math.min(112, width - 12), 5, 0xff51bbff);
            Gui.drawRect(width - 76, height - 4, width - 12, height - 3, 0xff328bd1);
            line(0, 7, 7, 0, 0x65caff, 1f);
            line(width - 7, 0, width, 7, 0x4a9dde, 1f);
            line(0, height - 7, 7, height, 0x386fa9, 1f);
            line(width - 7, height, width, height - 7, 0x386fa9, 1f);
        } finally {
            GL11.glPopMatrix();
        }
    }

    public static void glyph(Minecraft mc, int variant, int x, int y, int size, int color, float alpha) {
        IIcon icon = BlocksCommonProxy.wizardChalk.getIcon(1, variant & 15);
        if (icon == null) return;
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        color(color, alpha);
        PAINTER.drawTexturedModelRectFromIcon(x, y, icon, size, size);
        GL11.glColor4f(1, 1, 1, 1);
    }

    public static void seal(Minecraft mc, int variant, int x, int y) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        for (int ring = 0; ring < 2; ring++) {
            color(ring == 0 ? 0x245394 : 0x5dcaff, ring == 0 ? 0.8f : 1f);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i < 32; i++) {
                double angle = i * Math.PI / 16;
                int radius = ring == 0 ? 22 : 19;
                GL11.glVertex2d(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius);
            }
            GL11.glEnd();
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        glyph(mc, variant, x - 14, y - 14, 28, 0xa1e5ff, 1f);
    }

    public static void gear(int x, int y, int rgb) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        color(rgb, 1f);
        for (int tooth = 0; tooth < 8; tooth++) {
            double a = tooth * Math.PI / 4;
            GL11.glBegin(GL11.GL_QUADS);
            for (int i = 0; i < 4; i++) {
                double angle = a + (i < 2 ? -0.17 : 0.17);
                double radius = i == 0 || i == 3 ? 5 : 7;
                GL11.glVertex2d(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius);
            }
            GL11.glEnd();
        }
        for (int radius : new int[]{5, 2}) {
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i < 24; i++) {
                double angle = i * Math.PI / 12;
                GL11.glVertex2d(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius);
            }
            GL11.glEnd();
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void color(int rgb, float alpha) {
        GL11.glColor4f((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f,
                (rgb & 255) / 255f, alpha);
    }

    private static void line(float x1, float y1, float x2, float y2, int rgb, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        color(rgb, alpha);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1); GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1, 1, 1, 1);
    }
}
