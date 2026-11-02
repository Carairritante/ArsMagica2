package am2.guis.controls;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.opengl.GL11;

import java.util.List;

@SideOnly(Side.CLIENT)
public final class GuiButtonArcaneChalk extends GuiButton {
    public static final int GEAR = -2;
    private final int glyph;
    private final boolean selected;

    public GuiButtonArcaneChalk(int id, int x, int y, int width, int height, String label) {
        this(id, x, y, width, height, label, -1, false);
    }

    public GuiButtonArcaneChalk(int id, int x, int y, int width, int height, String label,
                               int glyph, boolean selected) {
        super(id, x, y, width, height, label);
        this.glyph = glyph;
        this.selected = selected;
    }

    public boolean labelClipped(Minecraft mc) {
        return glyph >= 0 && mc.fontRenderer.getStringWidth(displayString) > width - 56;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;
        field_146123_n = mouseX >= xPosition && mouseY >= yPosition
                && mouseX < xPosition + width && mouseY < yPosition + height;
        boolean hover = field_146123_n && enabled;
        int border = !enabled ? 0xff20334b : selected ? 0xff67ccff : hover ? 0xff428dcc : 0xff294d74;
        int top = !enabled ? 0xff101c2d : selected ? 0xff173f69 : hover ? 0xff133354 : 0xff10273f;
        int bottom = !enabled ? 0xff0c1625 : selected ? 0xff102b4b : 0xff0a192d;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            drawRect(xPosition + 2, yPosition, xPosition + width - 2, yPosition + 1, border);
            drawRect(xPosition + 2, yPosition + height - 1, xPosition + width - 2, yPosition + height, border);
            drawRect(xPosition, yPosition + 2, xPosition + 1, yPosition + height - 2, border);
            drawRect(xPosition + width - 1, yPosition + 2, xPosition + width, yPosition + height - 2, border);
            drawGradientRect(xPosition + 1, yPosition + 1, xPosition + width - 1,
                    yPosition + height - 1, top, bottom);
            if (selected) {
                drawRect(xPosition + 1, yPosition + 3, xPosition + 3, yPosition + height - 3, 0xff69d4ff);
                drawRect(xPosition + 5, yPosition + 2, xPosition + width - 5, yPosition + 3, 0x2849bbff);
            }
            int foreground = !enabled ? 0x7c91ac : selected || hover ? 0xecf7ff : 0xbdd4ed;
            if (glyph >= 0) {
                drawRect(xPosition + 28, yPosition + 5, xPosition + 29, yPosition + height - 5, 0x454f82b6);
                ArcaneChalkStyle.glyph(mc, glyph, xPosition + 9, yPosition + (height - 14) / 2,
                        14, selected ? 0xa1e5ff : 0x59a9df, enabled ? 1f : 0.4f);
                String label = mc.fontRenderer.trimStringToWidth(displayString, width - 56);
                GL11.glColor4f(1, 1, 1, 1);
                mc.fontRenderer.drawStringWithShadow(label, xPosition + 37, yPosition + (height - 8) / 2, foreground);
                if (selected) mc.fontRenderer.drawStringWithShadow("<", xPosition + width - 14,
                        yPosition + (height - 8) / 2, 0x77d4ff);
            } else if (glyph == GEAR) {
                ArcaneChalkStyle.gear(xPosition + width / 2, yPosition + height / 2, foreground);
            } else {
                List<String> lines = mc.fontRenderer.listFormattedStringToWidth(displayString, width - 12);
                int count = Math.min(2, lines.size());
                int y = yPosition + (height - count * 9) / 2;
                GL11.glColor4f(1, 1, 1, 1);
                for (int i = 0; i < count; i++) {
                    String label = lines.get(i);
                    mc.fontRenderer.drawStringWithShadow(label,
                            xPosition + (width - mc.fontRenderer.getStringWidth(label)) / 2, y, foreground);
                    y += 9;
                }
            }
            mouseDragged(mc, mouseX, mouseY);
        } finally {
            GL11.glPopAttrib();
        }
    }
}
