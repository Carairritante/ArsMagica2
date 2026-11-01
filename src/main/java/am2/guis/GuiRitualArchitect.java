package am2.guis;

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
        rows = Math.max(2, Math.min(7, (height - 148) / 21));
        panelWidth = Math.min(420, width - 12);
        panelHeight = rows * 21 + 142;
        left = (width - panelWidth) / 2;
        top = Math.max(4, (height - panelHeight) / 2);
        footer = top + 47 + rows * 21;
        if (page == 0 && selected >= 0) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).index == selected) page = i / rows;
            }
        }
        refreshButtons();
    }

    private void refreshButtons() {
        buttonList.clear();
        boolean ready = toolReady();
        for (int i = 0; i < rows; i++) {
            int at = page * rows + i;
            if (at >= entries.size()) break;
            RitualArchitect.Entry entry = entries.get(at);
            String name = ritualName(entry);
            name = fontRendererObj.trimStringToWidth(name, panelWidth - 48);
            GuiButton choice = new GuiButton(100 + at, left + 12, top + 43 + i * 21,
                    panelWidth - 24, 20, (entry.index == selected ? "\u00a76> " : "") + name);
            choice.enabled = ready;
            buttonList.add(choice);
        }
        GuiButton previous = new GuiButton(1, left + 12, footer, 26, 20, "<");
        previous.enabled = page > 0;
        buttonList.add(previous);
        GuiButton next = new GuiButton(2, left + 42, footer, 26, 20, ">");
        next.enabled = (page + 1) * rows < entries.size();
        buttonList.add(next);
        buttonList.add(new GuiButton(3, left + panelWidth - 82, footer, 70, 20, text("done")));
        int controlWidth = (panelWidth - 32) / 3;
        RitualArchitect.Entry entry = RitualArchitect.getEntry(selected);
        int orientations = orientations(entry);
        GuiButton rotate = new GuiButton(4, left + 12, footer + 24, controlWidth, 20,
                entry != null && entry.playground
                        ? text("orientation", rotation % orientations + 1, orientations)
                        : text("rotate", rotation * 90));
        rotate.enabled = ready && entry != null && orientations > 1;
        buttonList.add(rotate);
        GuiButton clear = new GuiButton(5, left + 16 + controlWidth, footer + 24, controlWidth, 20,
                text("clear"));
        clear.enabled = ready && RitualArchitect.anchor(player.getCurrentEquippedItem()) != null;
        buttonList.add(clear);
        GuiButton build = new GuiButton(6, left + 20 + controlWidth * 2, footer + 24,
                controlWidth, 20, text("build"));
        build.enabled = ready && player.capabilities.isCreativeMode && selected >= 0
                && RitualArchitect.anchor(player.getCurrentEquippedItem()) != null;
        buttonList.add(build);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if ((button.id >= 100 || button.id >= 4 && button.id <= 6) && !toolReady()) return;
        if (button.id >= 100) {
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
                        .add(heldToken).add(entry == null ? "" : entry.id).generate());
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
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(left, top, left + panelWidth, top + panelHeight, 0xe5191c2a);
        drawRect(left, top, left + panelWidth, top + 2, 0xffbda55e);
        drawCenteredString(fontRendererObj, text("title"), width / 2, top + 10, 0xffe7ca80);
        drawCenteredString(fontRendererObj, text(player.capabilities.isCreativeMode
                ? "mode_creative" : "mode_survival"), width / 2, top + 25, 0xffc8c8d1);
        if (entries.isEmpty()) {
            fontRendererObj.drawSplitString(text("none_unlocked"), left + 16, top + 48,
                    panelWidth - 32, 0xffdfbf79);
        }
        int pages = Math.max(1, (entries.size() + rows - 1) / rows);
        drawString(fontRendererObj, text("page", page + 1, pages), left + 78, footer + 6, 0xffccccdd);
        fontRendererObj.drawSplitString(text("controls"), left + 12, footer + 51,
                panelWidth - 24, 0xffbbbbcc);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
