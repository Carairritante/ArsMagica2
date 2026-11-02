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
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Pure client projection. It never places blocks or invokes a ritual's consuming checker. */
@SideOnly(Side.CLIENT)
public final class RitualArchitectRenderer {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final RitualArchitectHud hud = new RitualArchitectHud(mc);
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
            AxisAlignedBB bounds = view.entry.reagentBounds(view.anchor.x, view.anchor.y, view.anchor.z);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            tint(0xc09aff, 0.65f);
            box(bounds.minX + 0.02, bounds.minY + 0.025, bounds.minZ + 0.02,
                    bounds.maxX - 0.02, view.entry.playground ? bounds.maxY - 0.02 : bounds.minY + 0.04,
                    bounds.maxZ - 0.02);
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
        drawHud(new ScaledResolution(mc, mc.displayWidth, mc.displayHeight), false);
    }

    public int[] drawHudPreview(ScaledResolution resolution) { return drawHud(resolution, true); }

    private int[] drawHud(ScaledResolution resolution, boolean preview) {
        Snapshot view = snapshot();
        if (view == null) {
            if (!preview) return null;
            view = new Snapshot();
            view.warning = "mark_center";
        }
        List<RitualArchitectHud.Row> lines = new ArrayList<RitualArchitectHud.Row>();
        if (view.warning != null) {
            lines.add(RitualArchitectHud.Row.text(GuiRitualArchitect.text(view.warning), RitualArchitectHud.WARN));
        } else if (view.status != null) {
            if (view.status.blocked > 0) lines.add(RitualArchitectHud.Row.text(
                    GuiRitualArchitect.text("blocked", view.status.blocked), RitualArchitectHud.BAD));
            // Context requirements must remain visible even when a ritual has many ingredients.
            LinkedHashSet<String> warnings = new LinkedHashSet<String>(view.status.warnings);
            if (view.entry.noteKey != null && !view.entry.noteKey.isEmpty()) warnings.add(view.entry.noteKey);
            for (String key : warnings) lines.add(RitualArchitectHud.Row.text(
                    StatCollector.translateToLocal(key), RitualArchitectHud.WARN));
            addMissingBlocks(lines, view);
            boolean missingReagent = false;
            if (!view.status.reagents.isEmpty()) lines.add(RitualArchitectHud.Row.heading("hud_reagents"));
            int shown = 0;
            for (RitualArchitect.Reagent reagent : view.status.reagents) {
                if (view.entry.dynamicReagents && reagent.stack.getItem() == ItemsCommonProxy.rune) {
                    lines.add(RitualArchitectHud.Row.text(GuiRitualArchitect.text("rune_combination", reagent.present), RitualArchitectHud.BLUE));
                    continue;
                }
                if (reagent.present >= reagent.required) continue;
                missingReagent = true;
                if (shown++ < 6) lines.add(RitualArchitectHud.Row.text(GuiRitualArchitect.text("ingredient",
                        reagent.stack.getDisplayName(), reagent.present, reagent.required), RitualArchitectHud.TEXT));
            }
            if (shown > 6) lines.add(RitualArchitectHud.Row.text(GuiRitualArchitect.text("more", shown - 6), RitualArchitectHud.MUTED));
            if (view.entry.dynamicReagents) lines.add(RitualArchitectHud.Row.text(
                    GuiRitualArchitect.text("dynamic_reagents"), RitualArchitectHud.WARN));
            else if (!missingReagent && view.status.reagentsReady && view.status.warnings.isEmpty())
                lines.add(RitualArchitectHud.Row.text(GuiRitualArchitect.text("reagents_ready"), RitualArchitectHud.GOOD));
            if (view.status.extraItems > 0) lines.add(RitualArchitectHud.Row.text(
                    GuiRitualArchitect.text("extra_items", view.status.extraItems), RitualArchitectHud.BAD));
            lines.add(RitualArchitectHud.Row.heading("hud_activation"));
            lines.add(RitualArchitectHud.Row.text(GuiRitualArchitect.ritualActivation(view.entry), RitualArchitectHud.MUTED));
        }
        return hud.draw(resolution, view.entry == null ? GuiRitualArchitect.text("title") : GuiRitualArchitect.ritualName(view.entry),
                view.entry == null ? 0 : view.entry.index & 15, view.status == null ? 0 : view.status.correct,
                view.status == null ? -1 : view.status.correct + view.status.missing + view.status.blocked,
                view.status == null ? 0 : view.status.blocked, lines);
    }

    private static void addMissingBlocks(List<RitualArchitectHud.Row> lines, Snapshot view) {
        Map<String, Integer> missing = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < view.cells.size(); i++) {
            if (view.states[i] == RitualArchitect.CORRECT) continue;
            RitualArchitect.Cell cell = view.cells.get(i);
            String name = cell.block.getLocalizedName();
            Integer count = missing.get(name);
            missing.put(name, count == null ? 1 : count + 1);
        }
        if (!missing.isEmpty()) lines.add(RitualArchitectHud.Row.heading("hud_materials"));
        for (Map.Entry<String, Integer> entry : missing.entrySet())
            lines.add(RitualArchitectHud.Row.text(GuiRitualArchitect.text("material", entry.getValue(), entry.getKey()), RitualArchitectHud.TEXT));
    }
}
