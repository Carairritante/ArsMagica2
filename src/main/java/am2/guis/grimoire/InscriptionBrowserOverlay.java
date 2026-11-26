package am2.guis.grimoire;

import am2.api.spell.component.interfaces.ISkillTreeEntry;
import am2.api.spell.component.interfaces.ISpellComponent;
import am2.api.spell.component.interfaces.ISpellModifier;
import am2.api.spell.component.interfaces.ISpellPart;
import am2.api.spell.component.interfaces.ISpellShape;
import am2.api.spell.enums.Affinity;
import am2.spell.SkillTreeManager;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Side tabs, affinity filter grid, upper-grid scroll controls, subtle non-intrusive
 * icon markers, and recipe stage brackets for {@code GuiInscriptionTable}.
 * Preserves the classic Ars Magica table texture and original spell icon art.
 */
@SideOnly(Side.CLIENT)
public final class InscriptionBrowserOverlay {
    public enum Tab {
        ALL("am2.inscription.tab.all", "am2.inscription.tab.all.tip", 0xFFE08C),
        SHAPES("am2.inscription.tab.shapes", "am2.inscription.tab.shapes.tip", 0xB9A7FF),
        COMPONENTS("am2.inscription.tab.components", "am2.inscription.tab.components.tip", 0x7FE3FF),
        MODIFIERS("am2.inscription.tab.modifiers", "am2.inscription.tab.modifiers.tip", 0xFF9ECB),
        SILVER("am2.inscription.tab.silver", "am2.inscription.tab.silver.tip", 0xDCE8FF);

        public final String labelKey;
        public final String tipKey;
        public final int color;

        Tab(String labelKey, String tipKey, int color) {
            this.labelKey = labelKey;
            this.tipKey = tipKey;
            this.color = color;
        }
    }

    private static final RenderItem ITEMS = new RenderItem();
    private static final Map<Affinity, ItemStack> ESSENCE_CACHE = new EnumMap<Affinity, ItemStack>(Affinity.class);
    private static final int PANEL = 0x171D3A;
    private static final int BORDER = 0x3D4C8A;
    private static final int TEXT = 0xEEF1FF;
    private static final int MUTED = 0x909AC9;
    private static final int GOLD = 0xFFE08C;
    private static final int OK = 0x5CFFC0;
    private static final int[] STAGE_COLORS = { 0xFFE08C, 0x7FE3FF, 0xB9A7FF, 0x5CFFC0 };

    private static final int TAB_Y_START = 24;
    private static final int TAB_H = 14;
    private static final int TAB_GAP = 2;
    private static final int SCROLL_X = 183;
    private static final int SCROLL_UP_Y = 6;
    private static final int SCROLL_DN_Y = 39;
    private static final int SCROLL_W = 14;
    private static final int SCROLL_H = 11;

    private static ItemStack essenceIcon(Affinity aff) {
        if (aff == null || aff == Affinity.NONE) return null;
        ItemStack cached = ESSENCE_CACHE.get(aff);
        if (cached == null) {
            cached = GrimoireDecor.essenceForAffinity(aff);
            if (cached != null) ESSENCE_CACHE.put(aff, cached);
        }
        return cached;
    }

    /** Primary non-NONE affinity of a spell component, or null. */
    public static Affinity primaryAffinity(ISkillTreeEntry part) {
        if (!(part instanceof ISpellComponent)) return null;
        EnumSet<Affinity> affs = ((ISpellComponent) part).getAffinity();
        if (affs == null) return null;
        for (Affinity a : affs) {
            if (a != null && a != Affinity.NONE) return a;
        }
        return null;
    }

    public static boolean matchesAffinity(ISkillTreeEntry part, Affinity filter) {
        if (filter == null) return true;
        if (!(part instanceof ISpellComponent)) return false;
        EnumSet<Affinity> affs = ((ISpellComponent) part).getAffinity();
        return affs != null && affs.contains(filter);
    }

    private int tabWidth(int availLeft) {
        if (availLeft < 30) return 0;
        return availLeft >= 60 ? 54 : Math.max(28, availLeft - 4);
    }

    /**
     * Draws left-docked category tabs, optional affinity filter grid, and right-side scroll arrows.
     * Coordinates are relative to {@code (guiLeft, guiTop)}.
     */
    public List<String> draw(Minecraft mc, FontRenderer font, int availLeft,
                             Tab activeTab, Affinity activeAffinity, List<Affinity> presentAffinities,
                             int scrollRow, int maxScrollRow,
                             int localMouseX, int localMouseY) {
        List<String> tip = null;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            // 1. Left-docked category tabs
            int tw = tabWidth(availLeft);
            if (tw > 0) {
                int tx = -tw - 2;
                Tab[] tabs = Tab.values();
                for (int i = 0; i < tabs.length; i++) {
                    Tab tab = tabs[i];
                    int ty = TAB_Y_START + i * (TAB_H + TAB_GAP);
                    boolean active = (tab == activeTab);
                    boolean over = inside(localMouseX, localMouseY, tx, ty, tw, TAB_H);

                    GrimoireDraw.arcaneButton(tx, ty, tw, TAB_H, tab.color, active, over);
                    if (active) {
                        GrimoireDraw.rect(tx + tw - 2, ty + 1, 2, TAB_H - 2, tab.color, 0.98f);
                    } else {
                        GrimoireDraw.rect(tx + 1, ty + 2, 2, TAB_H - 4, tab.color, over ? 0.9f : 0.45f);
                    }

                    String label = GrimoireLang.tr(tab.labelKey);
                    int col = active ? tab.color : (over ? TEXT : MUTED);
                    drawScaled(font, font.trimStringToWidth(label, Math.round((tw - 7) / 0.80f)),
                            tx + 5, ty + 4, col, 0.80f);
                    if (over) {
                        tip = Collections.singletonList(GrimoireLang.tr(tab.tipKey));
                    }
                }

                // 2. Affinity filter box docked right below the tabs (only when relevant)
                boolean showAff = (activeTab == Tab.ALL || activeTab == Tab.COMPONENTS || activeTab == Tab.SILVER)
                        && presentAffinities != null && !presentAffinities.isEmpty();
                if (showAff && tw >= 46) {
                    int ay = TAB_Y_START + tabs.length * (TAB_H + TAB_GAP) + 4;
                    int cols = 3;
                    int rows = (presentAffinities.size() + 1 + cols - 1) / cols;
                    int boxH = 14 + rows * 15 + 3;
                    GrimoireDraw.panel(tx, ay, tw, boxH, PANEL, BORDER);
                    drawScaled(font, GrimoireLang.tr("am2.inscription.aff.title"), tx + 5, ay + 4, GOLD, 0.72f);
                    GrimoireDraw.hGrad(tx + 4, ay + 12, tw - 8, 1, GOLD, 0.45f, GOLD, 0.05f, true);

                    // Slot 0 = All affinities ("*")
                    int cell0X = tx + 5;
                    int cell0Y = ay + 15;
                    boolean overAll = inside(localMouseX, localMouseY, cell0X, cell0Y, 13, 13);
                    boolean allSel = (activeAffinity == null);
                    GrimoireDraw.arcaneButton(cell0X, cell0Y, 13, 13, GOLD, allSel, overAll);
                    drawScaled(font, "\u2726", cell0X + 4, cell0Y + 3, allSel ? GOLD : (overAll ? TEXT : MUTED), 0.85f);
                    if (overAll) {
                        tip = Collections.singletonList(GrimoireLang.tr("am2.inscription.aff.all"));
                    }

                    for (int i = 0; i < presentAffinities.size(); i++) {
                        Affinity aff = presentAffinities.get(i);
                        int idx = i + 1;
                        int cx = tx + 5 + (idx % cols) * 15;
                        int cy = ay + 15 + (idx / cols) * 15;
                        boolean sel = (aff == activeAffinity);
                        boolean over = inside(localMouseX, localMouseY, cx, cy, 13, 13);
                        int col = GrimoireDecor.brighten(aff.color);
                        GrimoireDraw.ring(cx + 6.5f, cy + 6.5f, 6.0f, 6.0f,
                                sel ? GrimoireDraw.mix(0x161B38, col, 0.35f) : 0x0E1226, 0.92f, 0, 1, 1, false);
                        GrimoireDraw.ring(cx + 6.5f, cy + 6.5f, 6.2f, sel ? 1.2f : 0.8f, col,
                                sel ? 1f : (over ? 0.9f : 0.45f), 0, 1, 1, sel || over);
                        ItemStack stack = essenceIcon(aff);
                        if (stack != null) {
                            drawTinyIcon(mc, font, stack, cx + 6.5f, cy + 6.5f);
                        }
                        if (over) {
                            tip = Collections.singletonList(GrimoireLang.affinityName(aff));
                        }
                    }
                }
            }

            // 3. Scroll controls (only shown when parts exceed 1 page / 24 icons)
            if (maxScrollRow > 0) {
                boolean canUp = scrollRow > 0;
                boolean canDn = scrollRow < maxScrollRow;
                boolean overUp = inside(localMouseX, localMouseY, SCROLL_X, SCROLL_UP_Y, SCROLL_W, SCROLL_H);
                boolean overDn = inside(localMouseX, localMouseY, SCROLL_X, SCROLL_DN_Y, SCROLL_W, SCROLL_H);

                GrimoireDraw.arcaneButton(SCROLL_X, SCROLL_UP_Y, SCROLL_W, SCROLL_H, GOLD, false, canUp && overUp);
                drawScaled(font, "\u25B2", SCROLL_X + 4, SCROLL_UP_Y + 2, canUp ? (overUp ? GOLD : TEXT) : 0x555E82, 0.78f);

                String pageStr = (scrollRow + 1) + "/" + (maxScrollRow + 1);
                int pw = Math.round(font.getStringWidth(pageStr) * 0.72f);
                drawScaled(font, pageStr, SCROLL_X + (SCROLL_W - pw) / 2, 24, GOLD, 0.72f);

                GrimoireDraw.arcaneButton(SCROLL_X, SCROLL_DN_Y, SCROLL_W, SCROLL_H, GOLD, false, canDn && overDn);
                drawScaled(font, "\u25BC", SCROLL_X + 4, SCROLL_DN_Y + 2, canDn ? (overDn ? GOLD : TEXT) : 0x555E82, 0.78f);
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }
        return tip;
    }

    /** Returns clicked Tab, or null. */
    public Tab tabAt(int availLeft, int localMouseX, int localMouseY) {
        int tw = tabWidth(availLeft);
        if (tw <= 0) return null;
        int tx = -tw - 2;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int ty = TAB_Y_START + i * (TAB_H + TAB_GAP);
            if (inside(localMouseX, localMouseY, tx, ty, tw, TAB_H)) return tabs[i];
        }
        return null;
    }

    /**
     * Hit-test the affinity grid.
     * Returns {@code -1} if nothing hit, {@code 0} for "All", or {@code 1..N} for {@code presentAffinities.get(idx - 1)}.
     */
    public int affinitySlotAt(int availLeft, Tab activeTab, List<Affinity> presentAffinities,
                              int localMouseX, int localMouseY) {
        int tw = tabWidth(availLeft);
        boolean showAff = tw >= 46
                && (activeTab == Tab.ALL || activeTab == Tab.COMPONENTS || activeTab == Tab.SILVER)
                && presentAffinities != null && !presentAffinities.isEmpty();
        if (!showAff) return -1;
        int tx = -tw - 2;
        int ay = TAB_Y_START + Tab.values().length * (TAB_H + TAB_GAP) + 4;
        if (inside(localMouseX, localMouseY, tx + 5, ay + 15, 13, 13)) return 0;
        for (int i = 0; i < presentAffinities.size(); i++) {
            int idx = i + 1;
            int cx = tx + 5 + (idx % 3) * 15;
            int cy = ay + 15 + (idx / 3) * 15;
            if (inside(localMouseX, localMouseY, cx, cy, 13, 13)) return i + 1;
        }
        return -1;
    }

    /** Returns -1 for scroll up arrow, +1 for scroll down arrow, 0 otherwise. */
    public int scrollClick(int maxScrollRow, int localMouseX, int localMouseY) {
        if (maxScrollRow <= 0) return 0;
        if (inside(localMouseX, localMouseY, SCROLL_X, SCROLL_UP_Y, SCROLL_W, SCROLL_H)) return -1;
        if (inside(localMouseX, localMouseY, SCROLL_X, SCROLL_DN_Y, SCROLL_W, SCROLL_H)) return 1;
        return 0;
    }

    /**
     * Ultra-subtle, respectful micro-markers over a 16x16 spell part icon:
     * NEVER replaces or obscures the original Ars Magica spell icon borders or artwork!
     * - Silver Skill: delicate 1px silver octagon outline (mirroring Golden Tornado's gold octagon).
     * - Component: tiny 2x2 affinity gem dot in the bottom-left corner.
     * - Shape (on ALL tab): tiny 2px violet corner ticks at the outer edge.
     * - Modifier (when recipe exists and modifier is useful): tiny 2x2 emerald dot at top-right.
     */
    public static void drawSubtlePartMarker(ISkillTreeEntry part, int x, int y, boolean onAllTab,
                                            boolean recipeHasParts, boolean modifierUseful, int fastTicker) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            if (SkillTreeManager.instance.isSilverSkill(part)) {
                int sCol = (fastTicker % 24 < 12) ? 0xE2EEFF : 0x9BB8E8;
                drawOctagon1px(x, y, sCol, 0.82f);
            }

            if (part instanceof ISpellComponent) {
                Affinity aff = primaryAffinity(part);
                if (aff != null) {
                    int col = GrimoireDecor.brighten(aff.color);
                    GrimoireDraw.rect(x, y + 13, 3, 3, 0x0A0D1A, 0.78f);
                    GrimoireDraw.rect(x + 1, y + 14, 2, 2, col, 0.96f);
                }
            } else if (part instanceof ISpellShape && onAllTab) {
                int col = 0xB9A7FF;
                GrimoireDraw.rect(x, y, 2, 1, col, 0.72f);
                GrimoireDraw.rect(x, y, 1, 2, col, 0.72f);
                GrimoireDraw.rect(x + 14, y, 2, 1, col, 0.72f);
                GrimoireDraw.rect(x + 15, y, 1, 2, col, 0.72f);
            } else if (part instanceof ISpellModifier && recipeHasParts && modifierUseful) {
                GrimoireDraw.rect(x + 13, y, 3, 3, 0x0A0D1A, 0.75f);
                GrimoireDraw.rect(x + 13, y + 1, 2, 2, OK, 0.95f);
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }
    }

    /** Clean stage-grouping underlines below the lower recipe strip (shows Stage 1, 2, 3...). */
    public static void drawStageBrackets(FontRenderer font, List<ISpellPart> recipe, int startX, int y, int step) {
        if (recipe == null || recipe.isEmpty()) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            int stageIdx = 0;
            int segStart = 0;
            int count = Math.min(8, recipe.size()); // lower bar spans 8 icons across 150px
            for (int i = 0; i <= count; i++) {
                boolean isBoundary = (i == count) || (i > segStart && recipe.get(i) instanceof ISpellShape);
                if (isBoundary && i > segStart) {
                    int x0 = startX + segStart * step;
                    int x1 = startX + (i - 1) * step + 16;
                    int col = STAGE_COLORS[stageIdx % STAGE_COLORS.length];
                    GrimoireDraw.rect(x0, y + 17, x1 - x0, 1, col, 0.85f);
                    GrimoireDraw.rect(x0, y + 15, 1, 3, col, 0.85f);
                    GrimoireDraw.rect(x1 - 1, y + 15, 1, 3, col, 0.85f);
                    if (stageIdx > 0 || i < count) {
                        drawScaled(font, String.valueOf(stageIdx + 1), x0 + 1, y + 18, col, 0.65f);
                    }
                    stageIdx++;
                    segStart = i;
                }
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }
    }

    private static void drawOctagon1px(int x, int y, int rgb, float alpha) {
        // Delicate 1px chamfered octagon around 16x16 icon without covering inner pixels
        GrimoireDraw.rect(x + 4, y, 8, 1, rgb, alpha);
        GrimoireDraw.rect(x + 4, y + 15, 8, 1, rgb, alpha);
        GrimoireDraw.rect(x, y + 4, 1, 8, rgb, alpha);
        GrimoireDraw.rect(x + 15, y + 4, 1, 8, rgb, alpha);
        GrimoireDraw.rect(x + 2, y + 1, 2, 1, rgb, alpha * 0.85f);
        GrimoireDraw.rect(x + 1, y + 2, 1, 2, rgb, alpha * 0.85f);
        GrimoireDraw.rect(x + 12, y + 1, 2, 1, rgb, alpha * 0.85f);
        GrimoireDraw.rect(x + 14, y + 2, 1, 2, rgb, alpha * 0.85f);
        GrimoireDraw.rect(x + 2, y + 14, 2, 1, rgb, alpha * 0.85f);
        GrimoireDraw.rect(x + 1, y + 12, 1, 2, rgb, alpha * 0.85f);
        GrimoireDraw.rect(x + 12, y + 14, 2, 1, rgb, alpha * 0.85f);
        GrimoireDraw.rect(x + 14, y + 12, 1, 2, rgb, alpha * 0.85f);
    }

    private static void drawTinyIcon(Minecraft mc, FontRenderer font, ItemStack stack, float cx, float cy) {
        GL11.glPushMatrix();
        GL11.glTranslatef(cx, cy, 0f);
        GL11.glScalef(0.56f, 0.56f, 1f);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        ITEMS.zLevel = 100f;
        try {
            ITEMS.renderItemAndEffectIntoGUI(font, mc.getTextureManager(), stack, -8, -8);
        } catch (Throwable ignored) {
        }
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glPopMatrix();
    }

    private static void drawScaled(FontRenderer font, String text, int x, int y, int col, float scale) {
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1f);
        font.drawStringWithShadow(text, Math.round(x / scale), Math.round(y / scale), col);
        GL11.glPopMatrix();
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
