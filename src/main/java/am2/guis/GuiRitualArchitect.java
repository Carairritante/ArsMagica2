package am2.guis;

import am2.AMCore;
import am2.guis.controls.ArcaneChalkStyle;
import am2.guis.controls.GuiButtonArcaneChalk;
import am2.items.ItemRitualArchitect;
import am2.network.AMDataWriter;
import am2.network.AMNetHandler;
import am2.network.AMPacketIDs;
import am2.ritual.RitualArchitect;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/** The menu changes only the server-owned plan stored on the held tool. */
@SideOnly(Side.CLIENT)
public class GuiRitualArchitect extends GuiScreen {
    private final EntityPlayer player;
    private final int heldSlot;
    private long heldToken;
    private final List<RitualArchitect.Entry> entries = new ArrayList<RitualArchitect.Entry>();
    private int selected = -1;
    private int rotation;
    private int page;
    private int rows;
    private boolean selectionSynced;
    private int left, top, panelWidth, panelHeight, footer;

    public GuiRitualArchitect(EntityPlayer player) {
        this.player = player;
        this.heldSlot = player.inventory.currentItem;
        ItemStack stack = player.getCurrentEquippedItem();
        this.heldToken = RitualArchitect.token(stack);
        RitualArchitect.Entry entry = RitualArchitect.selected(stack);
        if (entry != null) selected = entry.index;
        rotation = RitualArchitect.rotation(stack);
        for (RitualArchitect.Entry candidate : RitualArchitect.entries()) {
            if (RitualArchitect.canUse(player, candidate)) entries.add(candidate);
        }
    }

    public static String text(String key, Object... args) {
        return StatCollector.translateToLocalFormatted("am2.ritual_architect." + key, args);
    }

    public static String ritualName(RitualArchitect.Entry entry) {
        if (entry.playground && entry.compat.key.startsWith("potionBuff_")
                && !StatCollector.canTranslate(entry.nameKey))
            return StatCollector.translateToLocalFormatted("am2.architect.pg.dynamic_name", entry.controller);
        return StatCollector.translateToLocal(entry.nameKey);
    }

    public static String ritualActivation(RitualArchitect.Entry entry) {
        if (entry.playground && entry.compat.key.startsWith("potionBuff_")
                && !StatCollector.canTranslate(entry.activationKey))
            return StatCollector.translateToLocalFormatted("am2.architect.pg.dynamic_activation", entry.controller);
        return StatCollector.translateToLocal(entry.activationKey);
    }

    @Override
    public void initGui() {
        int oldStart = page * Math.max(1, rows);
        boolean firstLayout = rows == 0;
        rows = Math.max(2, Math.min(7, (height - 180) / 25));
        panelWidth = Math.min(456, width - 24);
        panelHeight = rows * 25 + 174;
        left = (width - panelWidth) / 2;
        top = Math.max(4, (height - panelHeight) / 2);
        footer = top + 72 + rows * 25;
        page = oldStart / rows;
        if (firstLayout && selected >= 0) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).index == selected) page = i / rows;
            }
        }
        page = Math.max(0, Math.min(page, Math.max(0, (entries.size() - 1) / rows)));
        refreshButtons();
        syncSelection();
    }

    private void refreshButtons() {
        buttonList.clear();
        buttonList.add(new GuiButtonArcaneChalk(7, left + panelWidth - 36, top + 16, 20, 20,
                text("hud_settings"), GuiButtonArcaneChalk.GEAR, false));
        boolean ready = toolReady();
        for (int i = 0; i < rows; i++) {
            int at = page * rows + i;
            if (at >= entries.size()) break;
            RitualArchitect.Entry entry = entries.get(at);
            String name = ritualName(entry);
            GuiButton choice = new GuiButtonArcaneChalk(100 + at, left + 16, top + 65 + i * 25,
                    panelWidth - 32, 23, name, entry.index & 15, entry.index == selected);
            choice.enabled = ready;
            buttonList.add(choice);
        }
        GuiButton previous = new GuiButtonArcaneChalk(1, left + 16, footer, 27, 20, "<");
        previous.enabled = page > 0;
        buttonList.add(previous);
        GuiButton next = new GuiButtonArcaneChalk(2, left + 47, footer, 27, 20, ">");
        next.enabled = (page + 1) * rows < entries.size();
        buttonList.add(next);
        buttonList.add(new GuiButtonArcaneChalk(3, left + panelWidth - 94, footer, 78, 20, text("done")));
        int controlWidth = (panelWidth - 44) / 3;
        RitualArchitect.Entry entry = RitualArchitect.getEntry(selected);
        int orientations = orientations(entry);
        GuiButton rotate = new GuiButtonArcaneChalk(4, left + 16, footer + 25, controlWidth, 23,
                entry != null && entry.playground
                        ? text("orientation", rotation % orientations + 1, orientations)
                        : text("rotate", rotation * 90));
        rotate.enabled = ready && entry != null && orientations > 1;
        buttonList.add(rotate);
        GuiButton clear = new GuiButtonArcaneChalk(5, left + 22 + controlWidth, footer + 25, controlWidth, 23,
                text("clear"));
        clear.enabled = ready && RitualArchitect.anchor(player.getCurrentEquippedItem()) != null;
        buttonList.add(clear);
        GuiButton build = new GuiButtonArcaneChalk(6, left + 28 + controlWidth * 2, footer + 25,
                controlWidth, 23, text("build"));
        build.enabled = ready && player.capabilities.isCreativeMode && selected >= 0
                && RitualArchitect.anchor(player.getCurrentEquippedItem()) != null;
        buttonList.add(build);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if ((button.id >= 100 || button.id >= 4 && button.id <= 6) && !toolReady()) return;
        if (button.id == 7) {
            mc.displayGuiScreen(new GuiArcaneChalkHud(this));
            return;
        } else if (button.id >= 100) {
            selected = entries.get(button.id - 100).index;
            rotation %= orientations(RitualArchitect.getEntry(selected));
            send(0);
        } else if (button.id == 1) {
            page--;
        } else if (button.id == 2) {
            page++;
        } else if (button.id == 3) {
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
            return;
        } else if (button.id == 4) {
            rotation = (rotation + 1) % orientations(RitualArchitect.getEntry(selected));
            send(0);
        } else if (button.id == 5) {
            send(2);
            mc.displayGuiScreen(null);
            return;
        } else if (button.id == 6) {
            send(1);
            mc.displayGuiScreen(null);
            return;
        }
        refreshButtons();
    }

    private void send(int action) {
        if (!toolReady()) return;
        RitualArchitect.Entry entry = RitualArchitect.getEntry(selected);
        AMNetHandler.INSTANCE.sendPacketToServer(AMPacketIDs.RITUAL_ARCHITECT,
                new AMDataWriter().add(action).add(selected).add(rotation).add(heldSlot)
                        .add(heldToken).add(entry == null ? "" : entry.id)
                        .add(!AMCore.config.stagedCompendium()).generate());
    }

    private void syncSelection() {
        if (!selectionSynced && !AMCore.config.stagedCompendium() && selected >= 0 && toolReady()) {
            send(0);
            selectionSynced = true;
        }
    }

    private static int orientations(RitualArchitect.Entry entry) {
        return entry == null ? 1 : entry.playground
                ? Math.max(1, Math.min(4, entry.orientationCount())) : 4;
    }

    private boolean toolReady() {
        ItemStack stack = player.getCurrentEquippedItem();
        if (player.inventory.currentItem != heldSlot || stack == null
                || !(stack.getItem() instanceof ItemRitualArchitect)) return false;
        long token = RitualArchitect.token(stack);
        // A just-created tool may still be waiting for the first inventory synchronization.
        if (heldToken == 0L && token != 0L) heldToken = token;
        return heldToken != 0L && token == heldToken;
    }

    @Override
    public void updateScreen() {
        ItemStack stack = player.getCurrentEquippedItem();
        if (player.inventory.currentItem != heldSlot || stack == null
                || !(stack.getItem() instanceof ItemRitualArchitect)
                || heldToken != 0L && RitualArchitect.token(stack) != heldToken) {
            mc.displayGuiScreen(null);
            return;
        }
        if (heldToken == 0L && toolReady()) refreshButtons();
        syncSelection();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glLineWidth(1f);
            ArcaneChalkStyle.panel(left, top, panelWidth, panelHeight);
            ArcaneChalkStyle.seal(mc, selected < 0 ? 0 : selected & 15, left + 35, top + 30);
            GL11.glColor4f(1, 1, 1, 1);
            drawString(fontRendererObj, fontRendererObj.trimStringToWidth(text("title"), panelWidth - 110),
                    left + 66, top + 15, 0xffe9f5ff);
            String mode = text(player.capabilities.isCreativeMode ? "mode_creative" : "mode_survival");
            int modeWidth = Math.min(panelWidth - 88, fontRendererObj.getStringWidth(mode) + 12);
            drawRect(left + 65, top + 31, left + 65 + modeWidth, top + 49, 0xff102e4d);
            drawRect(left + 65, top + 31, left + 65 + modeWidth, top + 32, 0xff2b638b);
            drawString(fontRendererObj, fontRendererObj.trimStringToWidth(mode, modeWidth - 10),
                    left + 71, top + 36, 0xff86caff);
            drawRect(left + 16, top + 57, left + panelWidth - 16, top + 58, 0x55508fc0);
            for (int y = top + 73, n = 0; y < top + panelHeight - 16; y += 28, n++)
                ArcaneChalkStyle.glyph(mc, (selected + n * 3) & 15, left + panelWidth - 12, y,
                        7, 0x5eb9f2, 0.32f);
            if (entries.isEmpty()) fontRendererObj.drawSplitString(text("none_unlocked"), left + 20,
                    top + 73, panelWidth - 44, 0xffd1e0ef);
            int pages = Math.max(1, (entries.size() + rows - 1) / rows);
            drawString(fontRendererObj, text("page", page + 1, pages), left + 85, footer + 6, 0xff8bb9df);
            drawRect(left + 16, footer + 54, left + panelWidth - 16, footer + 55, 0x44446eab);
            fontRendererObj.drawSplitString(text("controls"), left + 19, footer + 61,
                    panelWidth - 48, 0xff91acc9);
            super.drawScreen(mouseX, mouseY, partialTicks);
            for (Object element : buttonList) {
                if (!(element instanceof GuiButtonArcaneChalk)) continue;
                GuiButtonArcaneChalk button = (GuiButtonArcaneChalk)element;
                if (!button.visible || mouseX < button.xPosition || mouseY < button.yPosition
                        || mouseX >= button.xPosition + button.width || mouseY >= button.yPosition + button.height) continue;
                if (button.id == 7) {
                    List<String> lines = fontRendererObj.listFormattedStringToWidth(text("hud_settings"), Math.min(260, width - 32));
                    drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
                } else if (button.labelClipped(mc)) {
                    List<String> lines = fontRendererObj.listFormattedStringToWidth(button.displayString, Math.min(260, width - 32));
                    drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
                } else if (button.id == 6 && !player.capabilities.isCreativeMode) {
                    List<String> lines = fontRendererObj.listFormattedStringToWidth(text("mode_survival"), Math.min(260, width - 32));
                    drawHoveringText(lines, mouseX, mouseY, fontRendererObj);
                }
                break;
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
