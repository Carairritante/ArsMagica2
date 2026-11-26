package am2.guis.grimoire;

import am2.api.spell.component.interfaces.ISpellModifier;
import am2.api.spell.enums.Affinity;
import am2.playerextensions.ExtendedProperties;
import am2.spell.SkillManager;
import am2.spell.SpellInscriptionAnalyzer;
import am2.spell.SpellValidator;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StatCollector;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Grimoire-styled side panel rendered beside {@code GuiInscriptionTable}:
 * live estimated mana cost (with affinity discount), burnout, dominant affinity,
 * Crafting Altar tier requirement, unused-modifier warnings, and reagent summary.
 */
@SideOnly(Side.CLIENT)
public final class InscriptionPreviewPanel {
    private static final int PANEL = 0x171D3A;
    private static final int BORDER = 0x3D4C8A;
    private static final int TEXT = 0xEEF1FF;
    private static final int MUTED = 0x909AC9;
    private static final int GOLD = 0xFFE08C;
    private static final int CYAN = 0x7FE3FF;
    private static final int WARN = 0xFFB84D;
    private static final int DANGER = 0xFF5A72;
    private static final int OK = 0x5CFFC0;

    private int matBoxX, matBoxY, matBoxW, matBoxH;

    /**
     * Draws the live analysis panel in GuiContainer foreground coordinates
     * (origin at {@code (guiLeft, guiTop)}). Returns a tooltip list if the mouse
     * hovers the materials summary block, or {@code null}.
     */
    public List<String> draw(FontRenderer font, int x, int y, int availRight,
                             SpellInscriptionAnalyzer.Analysis analysis,
                             SpellValidator.ValidationResult validation,
                             boolean bookReadyToRemove,
                             EntityPlayer player,
                             int localMouseX, int localMouseY) {
        if (availRight < 74) return null;
        int w = Math.min(126, availRight - 4);
        int h = computeHeight(font, w, analysis, validation, bookReadyToRemove);

        int accent = CYAN;
        if (analysis != null && analysis.dominantAffinity != null && analysis.dominantAffinity != Affinity.NONE) {
            accent = GrimoireDecor.brighten(analysis.dominantAffinity.color);
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            // Outer dark frame & subtle accent header edge
            GrimoireDraw.rect(x - 1, y + 2, w + 2, h, 0x04060E, 0.48f);
            GrimoireDraw.panel(x, y, w, h, PANEL, BORDER);
            float mid = x + w * 0.5f;
            float half = Math.max(10f, w * 0.44f);
            GrimoireDraw.hGrad(mid - half, y + 1, half, 1, accent, 0f, accent, 0.7f, true);
            GrimoireDraw.hGrad(mid, y + 1, half, 1, accent, 0.7f, accent, 0f, true);
            GrimoireDecor.rune(5, x + 9, y + 9, 8, GOLD, 0.9f, true);

            String title = tr("am2.inscription.panel.title");
            font.drawStringWithShadow(font.trimStringToWidth(title, w - 20), x + 16, y + 5, GOLD);
            GrimoireDraw.hGrad(x + 5, y + 16, w - 10, 1, accent, 0.55f, accent, 0.05f, true);

            int cy = y + 20;
            matBoxW = 0;

            if (analysis == null || analysis.totalParts == 0) {
                cy = drawWrapped(font, tr("am2.inscription.panel.empty"), x + 6, cy, w - 12, MUTED, 0.85f);
                return null;
            }

            // 1. Validation status
            if (validation != null && validation.valid) {
                int stCol = bookReadyToRemove ? WARN : OK;
                String stText = bookReadyToRemove
                        ? StatCollector.translateToLocal("am2.gui.bookOut")
                        : "\u2714 " + tr("am2.inscription.panel.valid");
                cy = drawWrapped(font, stText, x + 6, cy, w - 12, stCol, 0.85f) + 3;
            } else if (validation != null && validation.message != null && !validation.message.isEmpty()) {
                cy = drawWrapped(font, "\u2716 " + validation.message, x + 6, cy, w - 12, WARN, 0.82f) + 3;
            }

            // 2. Mana & Burnout
            ExtendedProperties props = player != null ? ExtendedProperties.For(player) : null;
            float playerMaxMana = props != null ? props.getMaxMana() : Float.MAX_VALUE;
            boolean manaTooHigh = analysis.maxManaEffective > playerMaxMana && playerMaxMana > 0f;

            String manaVal = formatManaRange(analysis);
            int manaCol = manaTooHigh ? DANGER : CYAN;
            drawStatRow(font, x + 6, cy, w - 12, tr("am2.inscription.panel.mana"), manaVal, manaCol);
            cy += 10;

            if (analysis.maxManaBase > analysis.maxManaEffective + 0.5f && analysis.maxManaBase > 0f) {
                int pct = Math.round((1f - analysis.maxManaEffective / analysis.maxManaBase) * 100f);
                if (pct > 0) {
                    drawScaled(font, "-" + pct + "% " + tr("am2.inscription.panel.affinity"), x + 8, cy, OK, 0.72f);
                    cy += 8;
                }
            }
            if (manaTooHigh) {
                drawScaled(font, "\u26A0 " + tr("am2.inscription.panel.manaExceeds"), x + 8, cy, DANGER, 0.74f);
                cy += 8;
            }

            drawStatRow(font, x + 6, cy, w - 12, tr("am2.inscription.panel.burnout"),
                    String.format(Locale.ROOT, "%.0f", analysis.burnout), 0xFF9E7A);
            cy += 11;

            // 3. Dominant Affinity badge
            if (analysis.dominantAffinity != null && analysis.dominantAffinity != Affinity.NONE) {
                String affName = GrimoireLang.affinityName(analysis.dominantAffinity);
                int pct = Math.round(analysis.dominantAffinityShare * 100f);
                String badgeTxt = affName + (pct < 100 ? (" " + pct + "%") : "");
                int bw = Math.min(w - 12, font.getStringWidth(badgeTxt) + 8);
                GrimoireDraw.badge(x + 6, cy, bw, 11, accent);
                font.drawString(font.trimStringToWidth(badgeTxt, bw - 6), x + 10, cy + 2, accent);
                cy += 14;
            }

            // 4. Altar complexity & tier hint
            int tierCol = analysis.altarTier >= 5 ? DANGER : (analysis.altarTier >= 3 ? GOLD : MUTED);
            drawStatRow(font, x + 6, cy, w - 12, tr("am2.inscription.panel.altar"),
                    trFmt("am2.inscription.panel.parts", analysis.totalParts), tierCol);
            cy += 9;
            drawScaled(font, tr("am2.inscription.panel.tier" + Math.min(5, analysis.altarTier)), x + 8, cy, tierCol, 0.76f);
            cy += 10;

            // 5. Unused modifiers warning block
            if (!analysis.unusedModifiers.isEmpty()) {
                GrimoireDraw.hGrad(x + 6, cy, w - 12, 1, DANGER, 0.55f, DANGER, 0.05f, true);
                cy += 3;
                drawScaled(font, "\u26A0 " + tr("am2.inscription.panel.unused"), x + 6, cy, WARN, 0.78f);
                cy += 8;
                StringBuilder names = new StringBuilder();
                for (int i = 0; i < analysis.unusedModifiers.size(); i++) {
                    if (i > 0) names.append(", ");
                    ISpellModifier mod = analysis.unusedModifiers.get(i);
                    names.append(SkillManager.instance.getDisplayName(mod));
                }
                cy = drawWrapped(font, names.toString(), x + 8, cy, w - 14, DANGER, 0.76f) + 3;
            }

            // 6. Altar materials summary (hoverable for full list!)
            if (!analysis.altarMaterials.isEmpty() || analysis.totalEtherium > 0) {
                GrimoireDraw.hGrad(x + 6, cy, w - 12, 1, BORDER, 0.6f, BORDER, 0.05f, true);
                cy += 3;
                matBoxX = x + 4;
                matBoxY = cy - 1;
                matBoxW = w - 8;
                int totalItems = 0;
                for (Integer q : analysis.altarMaterials.values()) totalItems += q;
                drawScaled(font, trFmt("am2.inscription.panel.reagents", totalItems), x + 6, cy, GOLD, 0.80f);
                cy += 9;
                if (analysis.totalEtherium > 0) {
                    drawScaled(font, trFmt("am2.inscription.panel.etherium", analysis.totalEtherium), x + 8, cy, CYAN, 0.76f);
                    cy += 8;
                }
                drawScaled(font, tr("am2.inscription.panel.hoverMaterials"), x + 8, cy, MUTED, 0.72f);
                cy += 8;
                matBoxH = cy - matBoxY + 2;
            }

            // Hover tooltip for full reagent breakdown
            if (matBoxW > 0 && localMouseX >= matBoxX && localMouseX <= matBoxX + matBoxW
                    && localMouseY >= matBoxY && localMouseY <= matBoxY + matBoxH) {
                List<String> tip = new ArrayList<String>();
                tip.add("\u00a76" + tr("am2.inscription.panel.altar") + " (" + trFmt("am2.inscription.panel.parts", analysis.totalParts) + ")");
                for (Map.Entry<String, Integer> e : analysis.altarMaterials.entrySet()) {
                    tip.add("\u00a7f" + e.getValue() + "x \u00a77" + e.getKey());
                }
                if (analysis.totalEtherium > 0) {
                    tip.add("\u00a7b" + trFmt("am2.inscription.panel.etherium", analysis.totalEtherium));
                }
                return tip;
            }
            return null;
        } finally {
            GL11.glPopAttrib();
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }
    }

    /** Subtle warning corner pip drawn over an unused modifier icon inside the recipe slots. */
    public static void drawUnusedWarningMarker(int iconX, int iconY) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GrimoireDraw.rect(iconX, iconY, 16, 1, WARN, 0.85f);
            GrimoireDraw.rect(iconX, iconY + 15, 16, 1, WARN, 0.85f);
            GrimoireDraw.rect(iconX, iconY, 1, 16, WARN, 0.85f);
            GrimoireDraw.rect(iconX + 15, iconY, 1, 16, WARN, 0.85f);
            GrimoireDraw.rect(iconX + 11, iconY + 1, 4, 4, DANGER, 0.95f);
        } finally {
            GL11.glPopAttrib();
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }
    }

    private int computeHeight(FontRenderer font, int w, SpellInscriptionAnalyzer.Analysis a,
                              SpellValidator.ValidationResult val, boolean bookOut) {
        if (a == null || a.totalParts == 0) return 48;
        int h = 24;
        if (val != null && val.valid) {
            String s = bookOut ? StatCollector.translateToLocal("am2.gui.bookOut") : "V " + tr("am2.inscription.panel.valid");
            h += wrappedLines(font, s, w - 12, 0.85f) * 8 + 4;
        } else if (val != null && val.message != null && !val.message.isEmpty()) {
            h += wrappedLines(font, "X " + val.message, w - 12, 0.82f) * 8 + 4;
        }
        h += 22; // mana + burnout
        if (a.maxManaBase > a.maxManaEffective + 0.5f) h += 8;
        if (Minecraft.getMinecraft().thePlayer != null) {
            ExtendedProperties props = ExtendedProperties.For(Minecraft.getMinecraft().thePlayer);
            if (props != null && a.maxManaEffective > props.getMaxMana() && props.getMaxMana() > 0f) h += 8;
        }
        if (a.dominantAffinity != null && a.dominantAffinity != Affinity.NONE) h += 14;
        h += 20; // altar complexity
        if (!a.unusedModifiers.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < a.unusedModifiers.size(); i++) {
                if (i > 0) names.append(", ");
                names.append(SkillManager.instance.getDisplayName(a.unusedModifiers.get(i)));
            }
            h += 14 + wrappedLines(font, names.toString(), w - 14, 0.76f) * 7 + 4;
        }
        if (!a.altarMaterials.isEmpty() || a.totalEtherium > 0) {
            h += 22 + (a.totalEtherium > 0 ? 8 : 0);
        }
        return Math.min(244, Math.max(76, h + 4));
    }

    private static String formatManaRange(SpellInscriptionAnalyzer.Analysis a) {
        if (a.hasRange()) {
            return String.format(Locale.ROOT, "%.0f-%.0f MP", a.minManaEffective, a.maxManaEffective);
        }
        return String.format(Locale.ROOT, "%.0f MP", a.minManaEffective);
    }

    private static void drawStatRow(FontRenderer font, int x, int y, int w, String label, String value, int valCol) {
        drawScaled(font, label, x, y, MUTED, 0.82f);
        int vw = Math.round(font.getStringWidth(value) * 0.85f);
        drawScaled(font, value, x + w - vw, y, valCol, 0.85f);
    }

    private static void drawScaled(FontRenderer font, String text, int x, int y, int col, float scale) {
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1f);
        font.drawStringWithShadow(text, Math.round(x / scale), Math.round(y / scale), col);
        GL11.glPopMatrix();
    }

    private static int drawWrapped(FontRenderer font, String text, int x, int y, int maxW, int col, float scale) {
        List<?> lines = font.listFormattedStringToWidth(text, Math.max(24, Math.round(maxW / scale)));
        int step = Math.max(7, Math.round(9 * scale));
        int shown = Math.min(4, lines.size());
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1f);
        for (int i = 0; i < shown; i++) {
            font.drawStringWithShadow((String) lines.get(i), Math.round(x / scale), Math.round((y + i * step) / scale), col);
        }
        GL11.glPopMatrix();
        return y + shown * step;
    }

    private static int wrappedLines(FontRenderer font, String text, int maxW, float scale) {
        List<?> lines = font.listFormattedStringToWidth(text, Math.max(24, Math.round(maxW / scale)));
        return Math.min(4, Math.max(1, lines.size()));
    }

    private static String tr(String key) {
        return GrimoireLang.tr(key);
    }

    private static String trFmt(String key, Object... args) {
        return GrimoireLang.trFmt(key, args);
    }
}
