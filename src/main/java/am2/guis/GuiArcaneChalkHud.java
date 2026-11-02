package am2.guis;

import am2.guis.controls.ArcaneChalkStyle;
import am2.guis.controls.GuiButtonArcaneChalk;
import am2.ritual.client.ArcaneChalkHudSettings;
import am2.ritual.client.RitualArchitectRenderer;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class GuiArcaneChalkHud extends GuiScreen {
    private final GuiScreen parent;
    private final RitualArchitectRenderer preview = new RitualArchitectRenderer();
    private final ArcaneChalkHudSettings settings = ArcaneChalkHudSettings.get();
    private int[] bounds;
    private int offsetX, offsetY, toolbarLeft, toolbarTop, toolbarWidth;
    private boolean dragging;

    public GuiArcaneChalkHud(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        dragging = false;
        toolbarWidth = Math.min(336, width - 16);
        toolbarLeft = (width - toolbarWidth) / 2;
        toolbarTop = height - 90;
        buttonList.clear();
        buttonList.add(new GuiButtonArcaneChalk(0, toolbarLeft + 9, toolbarTop + 28, 26, 21, "-"));
        buttonList.add(new GuiButtonArcaneChalk(1, toolbarLeft + 39, toolbarTop + 28, 26, 21, "+"));
        buttonList.add(new GuiButtonArcaneChalk(2, toolbarLeft + toolbarWidth - 144, toolbarTop + 28,
                65, 21, GuiRitualArchitect.text("hud_reset")));
        buttonList.add(new GuiButtonArcaneChalk(3, toolbarLeft + toolbarWidth - 74, toolbarTop + 28,
                65, 21, GuiRitualArchitect.text("done")));
        buttonList.add(new GuiButtonArcaneChalk(4, toolbarLeft + 9, toolbarTop + 54, 26, 21, "-"));
        buttonList.add(new GuiButtonArcaneChalk(5, toolbarLeft + 39, toolbarTop + 54, 26, 21, "+"));
        updateButtons();
    }

    private void updateButtons() {
        ((GuiButton)buttonList.get(0)).enabled = settings.percent() > 50;
        ((GuiButton)buttonList.get(1)).enabled = settings.percent() < 150;
        ((GuiButton)buttonList.get(4)).enabled = settings.transparency() > 0;
        ((GuiButton)buttonList.get(5)).enabled = settings.transparency() < 100;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) settings.resize(-10);
        else if (button.id == 1) settings.resize(10);
        else if (button.id == 2) settings.reset();
        else if (button.id == 3) { mc.displayGuiScreen(parent); return; }
        else if (button.id == 4) settings.fade(-10);
        else if (button.id == 5) settings.fade(10);
        updateButtons();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glLineWidth(1f);
            drawRect(0, 0, width, height, 0x28030b18);
            bounds = preview.drawHudPreview(new ScaledResolution(mc, mc.displayWidth, mc.displayHeight));
            ArcaneChalkStyle.panel(toolbarLeft, toolbarTop, toolbarWidth, 82);
            GL11.glColor4f(1, 1, 1, 1);
            fontRendererObj.drawSplitString(GuiRitualArchitect.text("hud_drag"), toolbarLeft + 10,
                    toolbarTop + 6, toolbarWidth - 20, 0xc5e4ff);
            drawString(fontRendererObj, GuiRitualArchitect.text("hud_size", settings.percent()),
                    toolbarLeft + 75, toolbarTop + 35, 0x9bd9ff);
            drawString(fontRendererObj, GuiRitualArchitect.text("hud_transparency", settings.transparency()),
                    toolbarLeft + 75, toolbarTop + 61, 0x9bd9ff);
            super.drawScreen(mouseX, mouseY, partialTicks);
        } finally { GL11.glPopAttrib(); }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        if (mc.currentScreen != this || button != 0 || bounds == null) return;
        if (y >= toolbarTop && x >= toolbarLeft && x < toolbarLeft + toolbarWidth) return;
        if (x >= bounds[0] && y >= bounds[1] && x < bounds[0] + bounds[2] && y < bounds[1] + bounds[3]) {
            dragging = true; offsetX = x - bounds[0]; offsetY = y - bounds[1];
        }
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long time) {
        if (dragging && button == 0 && bounds != null)
            settings.move(x - offsetX, y - offsetY, width, height, bounds[2], bounds[3]);
    }

    @Override
    protected void mouseMovedOrUp(int x, int y, int button) {
        super.mouseMovedOrUp(x, y, button);
        if (button == 0) dragging = false;
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == 1) mc.displayGuiScreen(parent);
        else super.keyTyped(character, key);
    }

    @Override
    public void onGuiClosed() { settings.save(); }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
