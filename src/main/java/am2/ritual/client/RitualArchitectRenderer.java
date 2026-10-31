package am2.ritual.client;

import am2.blocks.BlocksCommonProxy;
import am2.guis.GuiRitualArchitect;
import am2.items.ItemRitualArchitect;
import am2.items.ItemsCommonProxy;
import am2.ritual.RitualArchitect;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure client projection. It never places blocks or invokes a ritual's consuming checker. */
@SideOnly(Side.CLIENT)
public final class RitualArchitectRenderer {
    private final Minecraft mc = Minecraft.getMinecraft();
    private Snapshot cached;
    private ItemStack cachedStack;
    private World cachedWorld;
    private NBTTagCompound cachedTag;
    private long checkedAt = Long.MIN_VALUE;

    private static final class Snapshot {
        RitualArchitect.Entry entry;
        RitualArchitect.Anchor anchor;
        List<RitualArchitect.Cell> cells = Collections.emptyList();
        int[] states = new int[0];
        RitualArchitect.Status status;
        String warning;
    }

    private static boolean isPlannedTool(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemRitualArchitect
                && RitualArchitect.anchor(stack) != null;
    }

    private ItemStack plannedTool(EntityPlayer player) {
        ItemStack held = player.getCurrentEquippedItem();
        if (isPlannedTool(held)) return held;
        long previousToken = RitualArchitect.token(cachedStack);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack candidate = player.inventory.getStackInSlot(slot);
            if (isPlannedTool(candidate) && (candidate == cachedStack
                    || previousToken != 0L && RitualArchitect.token(candidate) == previousToken)) return candidate;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack candidate = player.inventory.getStackInSlot(slot);
            if (isPlannedTool(candidate)) return candidate;
        }
        return null;
    }

    private Snapshot snapshot() {
        EntityPlayer player = mc.thePlayer;
        if (player == null || mc.theWorld == null || mc.gameSettings.hideGUI) return null;
        ItemStack held = plannedTool(player);
        if (held == null) {
            cached = null;
            cachedStack = null;
            return null;
        }
        RitualArchitect.Anchor activeAnchor = RitualArchitect.anchor(held);
        if (activeAnchor.dimension != player.dimension
                || player.getDistanceSq(activeAnchor.x + 0.5, activeAnchor.y + 0.5,
                activeAnchor.z + 0.5) > 32 * 32) return null;
        long now = mc.theWorld.getTotalWorldTime();
        NBTTagCompound tag = held.getTagCompound();
        boolean changed = mc.theWorld != cachedWorld || held != cachedStack
                || (tag == null ? cachedTag != null : !tag.equals(cachedTag));
        if (!changed && cached != null && now >= checkedAt && now - checkedAt < 5) return cached;
        checkedAt = now;
        cachedWorld = mc.theWorld;
        cachedStack = held;
        cachedTag = tag == null ? null : (NBTTagCompound)tag.copy();
        Snapshot result = new Snapshot();
        cached = result;
        result.entry = RitualArchitect.selected(held);
        if (result.entry == null || !RitualArchitect.canUse(player, result.entry)) {
            result.entry = null;
            result.warning = "choose";
            return result;
        }
        result.anchor = activeAnchor;
        List<RitualArchitect.Cell> cells = RitualArchitect.plan(held);
        for (RitualArchitect.Cell cell : cells) {
            if (!mc.theWorld.blockExists(cell.x, cell.y, cell.z)) {
                result.warning = "unloaded";
                return result;
            }
        }
        result.cells = cells;
        result.states = new int[cells.size()];
        for (int i = 0; i < cells.size(); i++) result.states[i] = cells.get(i).state(mc.theWorld);
        result.status = RitualArchitect.status(mc.theWorld, held);
        return result;
    }

    @SubscribeEvent
    public void onWorldRender(RenderWorldLastEvent event) {
        Snapshot view = snapshot();
        if (view == null || view.warning != null || view.cells.isEmpty()) return;
        Entity camera = mc.renderViewEntity;
        if (camera == null) return;
        double cameraX = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.partialTicks;
        double cameraY = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.partialTicks;
        double cameraZ = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.partialTicks;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-cameraX, -cameraY, -cameraZ);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2.0f);
            mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
            for (int i = 0; i < view.cells.size(); i++) {
                RitualArchitect.Cell cell = view.cells.get(i);
                int state = view.states[i];
                int color = state == RitualArchitect.CORRECT ? 0x65e897
                        : state == RitualArchitect.MISSING ? 0x69c7ff : 0xff6969;
                IIcon icon = cell.block.getIcon(1, cell.meta < 0 ? 0 : cell.meta);
                double faceY = cell.y + (cell.block == BlocksCommonProxy.wizardChalk ? 0.035 : 0.065);
                if (icon != null) drawIcon(icon, cell.x + 0.08, faceY, cell.z + 0.08,
                        cell.x + 0.92, cell.z + 0.92, color, state == RitualArchitect.CORRECT ? 0.3f : 0.7f);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                tint(color, 0.9f);
                double height = cell.block == BlocksCommonProxy.wizardChalk ? 0.045
                        : cell.block == BlocksCommonProxy.candle ? 0.5 : 1.0;
                box(cell.x + 0.015, cell.y + 0.015, cell.z + 0.015,
                        cell.x + 0.985, cell.y + height, cell.z + 0.985);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            }
            int radius = view.entry.interaction().getReagentSearchRadius();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            tint(0xc09aff, 0.65f);
            box(view.anchor.x - radius + 0.02, view.anchor.y + 0.025, view.anchor.z - radius + 0.02,
                    view.anchor.x + radius + 0.98, view.anchor.y + 0.04, view.anchor.z + radius + 0.98);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(view.anchor.x + 0.2, view.anchor.y + 0.08, view.anchor.z + 0.5);
            GL11.glVertex3d(view.anchor.x + 0.8, view.anchor.y + 0.08, view.anchor.z + 0.5);
            GL11.glVertex3d(view.anchor.x + 0.5, view.anchor.y + 0.08, view.anchor.z + 0.2);
            GL11.glVertex3d(view.anchor.x + 0.5, view.anchor.y + 0.08, view.anchor.z + 0.8);
            GL11.glEnd();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void tint(int color, float alpha) {
        GL11.glColor4f(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f,
                (color & 255) / 255f, alpha);
    }

    private static void drawIcon(IIcon icon, double x1, double y, double z1, double x2, double z2,
                                 int color, float alpha) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f,
                (color & 255) / 255f, alpha);
        tessellator.addVertexWithUV(x1, y, z1, icon.getMinU(), icon.getMinV());
        tessellator.addVertexWithUV(x1, y, z2, icon.getMinU(), icon.getMaxV());
        tessellator.addVertexWithUV(x2, y, z2, icon.getMaxU(), icon.getMaxV());
        tessellator.addVertexWithUV(x2, y, z1, icon.getMaxU(), icon.getMinV());
        tessellator.draw();
    }

    private static void box(double x1, double y1, double z1, double x2, double y2, double z2) {
        GL11.glBegin(GL11.GL_LINES);
        edge(x1,y1,z1,x2,y1,z1); edge(x2,y1,z1,x2,y1,z2);
        edge(x2,y1,z2,x1,y1,z2); edge(x1,y1,z2,x1,y1,z1);
        edge(x1,y2,z1,x2,y2,z1); edge(x2,y2,z1,x2,y2,z2);
        edge(x2,y2,z2,x1,y2,z2); edge(x1,y2,z2,x1,y2,z1);
        edge(x1,y1,z1,x1,y2,z1); edge(x2,y1,z1,x2,y2,z1);
        edge(x2,y1,z2,x2,y2,z2); edge(x1,y1,z2,x1,y2,z2);
        GL11.glEnd();
    }

    private static void edge(double x1,double y1,double z1,double x2,double y2,double z2) {
        GL11.glVertex3d(x1,y1,z1);
        GL11.glVertex3d(x2,y2,z2);
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || mc.currentScreen != null) return;
        Snapshot view = snapshot();
        if (view == null) return;
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int panelWidth = Math.min(270, resolution.getScaledWidth() - 16);
        int left = resolution.getScaledWidth() - panelWidth - 6;
        List<String> lines = new ArrayList<String>();
        lines.add("\u00a7e" + GuiRitualArchitect.text("title"));
        if (view.entry != null) lines.add(StatCollector.translateToLocal(view.entry.nameKey));
        if (view.warning != null) {
            lines.add("\u00a77" + GuiRitualArchitect.text(view.warning));
        } else if (view.status != null) {
            lines.add(GuiRitualArchitect.text("structure", view.status.correct,
                    view.status.correct + view.status.missing + view.status.blocked));
            if (view.status.blocked > 0) lines.add("\u00a7c" + GuiRitualArchitect.text("blocked", view.status.blocked));
            addMissingBlocks(lines, view);
            boolean missingReagent = false;
            int shown = 0;
            for (RitualArchitect.Reagent reagent : view.status.reagents) {
                if (view.entry.dynamicReagents && reagent.stack.getItem() == ItemsCommonProxy.rune) {
                    lines.add("\u00a7d" + GuiRitualArchitect.text("rune_combination", reagent.present));
                    continue;
                }
                if (reagent.present >= reagent.required) continue;
                missingReagent = true;
                if (shown++ < 6) lines.add("\u00a7d" + GuiRitualArchitect.text("ingredient",
                        reagent.stack.getDisplayName(), reagent.present, reagent.required));
            }
            if (shown > 6) lines.add("\u00a77" + GuiRitualArchitect.text("more", shown - 6));
            if (view.entry.dynamicReagents) lines.add("\u00a7d" + GuiRitualArchitect.text("dynamic_reagents"));
            else if (!missingReagent) lines.add("\u00a7a" + GuiRitualArchitect.text("reagents_ready"));
            if (view.status.extraItems > 0) lines.add("\u00a7c" + GuiRitualArchitect.text("extra_items", view.status.extraItems));
            String activation = "\u00a77" + StatCollector.translateToLocal(view.entry.activationKey);
            lines.addAll(mc.fontRenderer.listFormattedStringToWidth(activation, panelWidth - 12));
            lines.add("\u00a77" + GuiRitualArchitect.text("legend"));
        }
        lines.add("\u00a78" + GuiRitualArchitect.text("menu_hint"));
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            Gui.drawRect(left, 6, left + panelWidth, 16 + lines.size() * 10, 0xbd10131d);
            int y = 12;
            for (String line : lines) {
                mc.fontRenderer.drawStringWithShadow(mc.fontRenderer.trimStringToWidth(line, panelWidth - 12),
                        left + 6, y, 0xeeeeee);
                y += 10;
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void addMissingBlocks(List<String> lines, Snapshot view) {
        Map<String, Integer> missing = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < view.cells.size(); i++) {
            if (view.states[i] == RitualArchitect.CORRECT) continue;
            RitualArchitect.Cell cell = view.cells.get(i);
            String name = cell.block.getLocalizedName();
            Integer count = missing.get(name);
            missing.put(name, count == null ? 1 : count + 1);
        }
        for (Map.Entry<String, Integer> entry : missing.entrySet()) {
            lines.add("\u00a7b" + GuiRitualArchitect.text("material", entry.getValue(), entry.getKey()));
        }
    }
}
