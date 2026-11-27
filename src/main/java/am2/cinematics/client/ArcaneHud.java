package am2.cinematics.client;

import cpw.mods.fml.common.Loader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Primitivas 2D no estilo do Grimório do AM2.5 para as HUDs dos minigames:
 * molduras com filigrana dourada, anéis com marcações de astrolábio, polígonos arcanos,
 * estrelas, chamas, coração, selos de cera, botões de tecla e runas do Giz de Mago.
 *
 * Sem importar classes do AM2; agrupa vértices em lotes no Tessellator para rodar leve.
 */
final class ArcaneHud {
    static final int BG = 0x11152A;
    static final int PANEL = 0x171D3A;
    static final int PANEL_LIGHT = 0x242C5E;
    static final int BORDER = 0x3D4C8A;
    static final int TEXT = 0xEEF1FF;
    static final int MUTED = 0x909AC9;
    static final int GOLD = 0xFFE08C;
    static final int GOLD_HI = 0xFFE59E;
    static final int GOLD_MID = 0xC99842;
    static final int GOLD_LO = 0x6E5020;
    static final int CYAN = 0x7FE3FF;
    static final int EMERALD = 0x5CFFC0;
    static final int AMBER = 0xFFB347;
    static final int CRIMSON = 0xB82E46;
    static final int CRIMSON_HI = 0xFF5A72;

    private static final double TWO_PI = Math.PI * 2.0;
    private static final boolean HAS_AM2 = Loader.isModLoaded("arsmagica2");

    private ArcaneHud() {}

    static void rect(float x, float y, float w, float h, int rgb, float alpha) {
        if (alpha <= 0.005F || w <= 0F || h <= 0F) return;
        beginShape(false);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_I(rgb, a(alpha));
        quad(t, x, y, w, h);
        t.draw();
        endShape();
    }

    static void vGrad(float x, float y, float w, float h, int topRgb, float topAlpha,
                      int botRgb, float botAlpha, boolean additive) {
        if (w <= 0F || h <= 0F || (topAlpha <= 0.005F && botAlpha <= 0.005F)) return;
        beginShape(additive);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_I(topRgb, a(topAlpha));
        t.addVertex(x + w, y, 0);
        t.addVertex(x, y, 0);
        t.setColorRGBA_I(botRgb, a(botAlpha));
        t.addVertex(x, y + h, 0);
        t.addVertex(x + w, y + h, 0);
        t.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        endShape();
    }

    static void hGrad(float x, float y, float w, float h, int leftRgb, float leftAlpha,
                      int rightRgb, float rightAlpha, boolean additive) {
        if (w <= 0F || h <= 0F || (leftAlpha <= 0.005F && rightAlpha <= 0.005F)) return;
        beginShape(additive);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_I(leftRgb, a(leftAlpha));
        t.addVertex(x, y + h, 0);
        t.addVertex(x, y, 0);
        t.setColorRGBA_I(rightRgb, a(rightAlpha));
        t.addVertex(x + w, y, 0);
        t.addVertex(x + w, y + h, 0);
        t.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        endShape();
    }

    /** Halo radial aditivo suave (centro brilhante -> borda transparente). */
    static void glow(float cx, float cy, float radius, int rgb, float alpha) {
        if (alpha <= 0.005F || radius <= 0.5F) return;
        discInternal(cx, cy, radius, rgb, alpha, 0F, true, 24);
    }

    /** Disco com gradiente radial (alpha normal). */
    static void disc(float cx, float cy, float radius, int rgb, float centerAlpha, float edgeAlpha) {
        if ((centerAlpha <= 0.005F && edgeAlpha <= 0.005F) || radius <= 0.5F) return;
        discInternal(cx, cy, radius, rgb, centerAlpha, edgeAlpha, false, 32);
    }

    private static void discInternal(float cx, float cy, float radius, int rgb, float centerAlpha,
                                     float edgeAlpha, boolean additive, int segs) {
        beginShape(additive);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_TRIANGLE_FAN);
        t.setColorRGBA_I(rgb, a(centerAlpha));
        t.addVertex(cx, cy, 0);
        t.setColorRGBA_I(rgb, a(edgeAlpha));
        for (int i = 0; i <= segs; i++) {
            double ang = -TWO_PI * i / segs;
            t.addVertex(cx + Math.cos(ang) * radius, cy + Math.sin(ang) * radius, 0);
        }
        t.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        endShape();
    }

    /**
     * Anel centralizado em {@code radius} (vai de radius - thickness/2 até radius + thickness/2),
     * fiel ao cálculo de faixa de folga dos minigames.
     */
    static void ring(float cx, float cy, float radius, float thickness, int rgb, float alpha,
                     float rotation, int dashes, float fill, boolean additive) {
        if (alpha <= 0.008F || radius <= 0.5F || thickness <= 0.05F) return;
        float inner = Math.max(0F, radius - thickness * 0.5F);
        float outer = Math.max(0F, radius + thickness * 0.5F);
        int safeDashes = Math.max(1, dashes);
        int steps = Math.max(2, (safeDashes == 1 ? 48 : 36) / safeDashes);
        double slot = TWO_PI / safeDashes;
        beginShape(additive);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_I(rgb, a(alpha));
        for (int d = 0; d < safeDashes; d++) {
            double from = rotation + d * slot;
            double span = slot * fill / steps;
            for (int s = 0; s < steps; s++) {
                double a0 = from + s * span;
                double a1 = a0 + span;
                double c0 = Math.cos(a0), s0 = Math.sin(a0);
                double c1 = Math.cos(a1), s1 = Math.sin(a1);
                t.addVertex(cx + c0 * outer, cy + s0 * outer, 0);
                t.addVertex(cx + c0 * inner, cy + s0 * inner, 0);
                t.addVertex(cx + c1 * inner, cy + s1 * inner, 0);
                t.addVertex(cx + c1 * outer, cy + s1 * outer, 0);
            }
        }
        t.draw();
        endShape();
    }

    /** Graduação angular de astrolábio em lote único no Tessellator. */
    static void tickRing(float cx, float cy, float radius, float tickLen, int count,
                         int rgb, float alpha, float rotation) {
        if (alpha <= 0.008F || count <= 0 || radius <= 1F) return;
        int majorRgb = mix(rgb, GOLD_HI, 0.5F);
        int majorA = a(Math.min(1F, alpha * 1.15F));
        int minorA = a(alpha * 0.72F);
        beginShape(true);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (int i = 0; i < count; i++) {
            double ang = rotation + i * TWO_PI / count;
            boolean major = (i % 3 == 0);
            float len = major ? tickLen * 1.65F : tickLen;
            float hw = major ? 0.55F : 0.35F;
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            float nx = -sin * hw, ny = cos * hw;
            float r0 = Math.max(0F, radius - len), r1 = radius;
            t.setColorRGBA_I(major ? majorRgb : rgb, major ? majorA : minorA);
            t.addVertex(cx + cos * r0 + nx, cy + sin * r0 + ny, 0);
            t.addVertex(cx + cos * r1 + nx, cy + sin * r1 + ny, 0);
            t.addVertex(cx + cos * r1 - nx, cy + sin * r1 - ny, 0);
            t.addVertex(cx + cos * r0 - nx, cy + sin * r0 - ny, 0);
        }
        t.draw();
        endShape();
    }

    /** Polígono arcano regular em lote único no Tessellator. */
    static void polygon(float cx, float cy, float radius, int sides, float thickness,
                        int rgb, float alpha, float rotation, boolean additive) {
        if (alpha <= 0.008F || sides < 3 || radius <= 1F) return;
        beginShape(additive);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_I(rgb, a(alpha));
        for (int i = 0; i < sides; i++) {
            double a0 = rotation + i * TWO_PI / sides;
            double a1 = rotation + (i + 1) * TWO_PI / sides;
            float x0 = cx + (float) Math.cos(a0) * radius;
            float y0 = cy + (float) Math.sin(a0) * radius;
            float x1 = cx + (float) Math.cos(a1) * radius;
            float y1 = cy + (float) Math.sin(a1) * radius;
            addLineQuad(t, x0, y0, x1, y1, thickness);
        }
        t.draw();
        endShape();
    }

    /** Estrela arcana de 4 pontas (sem push/pop de matriz). */
    static void star(float cx, float cy, float radius, int rgb, float alpha, float rotRad) {
        if (alpha <= 0.01F || radius <= 0.4F) return;
        float inner = radius * 0.24F;
        float cos = (float) Math.cos(rotRad), sin = (float) Math.sin(rotRad);
        float rx = cos * radius, ry = sin * radius;
        float px = -sin * inner, py = cos * inner;
        float ux = -sin * radius, uy = cos * radius;
        float qx = cos * inner, qy = sin * inner;

        beginShape(true);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_I(rgb, a(alpha));
        t.addVertex(cx + ux, cy + uy, 0);
        t.addVertex(cx - qx, cy - qy, 0);
        t.addVertex(cx - ux, cy - uy, 0);
        t.addVertex(cx + qx, cy + qy, 0);

        t.addVertex(cx - rx, cy - ry, 0);
        t.addVertex(cx + px, cy + py, 0);
        t.addVertex(cx + rx, cy + ry, 0);
        t.addVertex(cx - px, cy - py, 0);
        t.draw();
        endShape();
    }

    /**
     * Faixa/placa no estilo Grimório: sombra, gradiente noturno, bordas chanfradas,
     * cantoneiras de filigrana dourada com gemas e brilho central aditivo (em poucos lotes).
     */
    static void grimoireBanner(float x, float y, float w, float h, int accentRgb, float alpha) {
        if (w <= 4F || h <= 4F) return;
        // Sombra suave
        rect(x - 1F, y + 2F, w + 2F, h, 0x04060E, 0.46F);
        // Corpo em gradiente escuro translúcido
        vGrad(x + 1F, y + 1F, w - 2F, h - 2F, PANEL, alpha, BG, Math.min(0.96F, alpha + 0.08F), false);

        // Todas as bordas, filigranas dos 4 cantos e gemas em um único lote de quads!
        float fl = Math.min(8F, h * 0.36F);
        float flHalf = fl * 0.55F;
        beginShape(false);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        // Bordas externas
        t.setColorRGBA_I(BORDER, a(0.92F));
        quad(t, x + 1F, y, w - 2F, 1F);
        t.setColorRGBA_I(shade(BORDER, 0.72F), a(0.92F));
        quad(t, x + 1F, y + h - 1F, w - 2F, 1F);
        t.setColorRGBA_I(shade(BORDER, 0.85F), a(0.92F));
        quad(t, x, y + 1F, 1F, h - 2F);
        quad(t, x + w - 1F, y + 1F, 1F, h - 2F);

        // Filigrana dourada nos 4 cantos + gema colorida
        addCornerFiligree(t, x + 1F, y + 1F, 1F, 1F, fl, flHalf, accentRgb);
        addCornerFiligree(t, x + w - 2F, y + 1F, -1F, 1F, fl, flHalf, accentRgb);
        addCornerFiligree(t, x + 1F, y + h - 2F, 1F, -1F, fl, flHalf, accentRgb);
        addCornerFiligree(t, x + w - 2F, y + h - 2F, -1F, -1F, fl, flHalf, accentRgb);
        t.draw();
        endShape();

        // Fios luminosos superior (cor do minigame) e inferior (ouro)
        float midX = x + w * 0.5F;
        float topHalf = Math.max(8F, w * 0.44F);
        float botHalf = Math.max(8F, w * 0.36F);
        hGrad(midX - topHalf, y + 0.5F, topHalf, 1F, accentRgb, 0F, accentRgb, 0.68F, true);
        hGrad(midX, y + 0.5F, topHalf, 1F, accentRgb, 0.68F, accentRgb, 0F, true);
        hGrad(midX - botHalf, y + h - 1.5F, botHalf, 1F, GOLD_MID, 0F, GOLD_HI, 0.48F, true);
        hGrad(midX, y + h - 1.5F, botHalf, 1F, GOLD_HI, 0.48F, GOLD_MID, 0F, true);
    }

    private static void addCornerFiligree(Tessellator t, float cx, float cy, float dx, float dy,
                                          float fl, float flHalf, int gemRgb) {
        // Braço dourado externo (GOLD_HI perto do vértice, GOLD_MID no prolongamento)
        t.setColorRGBA_I(GOLD_HI, 245);
        quadSpan(t, cx, cy, cx + dx * flHalf, cy + dy * 1F);
        quadSpan(t, cx, cy, cx + dx * 1F, cy + dy * flHalf);
        t.setColorRGBA_I(GOLD_MID, 230);
        quadSpan(t, cx + dx * flHalf, cy, cx + dx * fl, cy + dy * 1F);
        quadSpan(t, cx, cy + dy * flHalf, cx + dx * 1F, cy + dy * fl);
        // Gema arcana no canto interno
        t.setColorRGBA_I(gemRgb, 235);
        quadSpan(t, cx + dx * 2F, cy + dy * 2F, cx + dx * 3.5F, cy + dy * 3.5F);
    }

    /** Botão/tecla arcana estilizada; expande automaticamente para caber o rótulo. */
    static void arcaneKeycap(FontRenderer font, float cx, float cy, float minW, float h,
                             String label, int accentRgb, boolean lit, boolean fail, float textScale) {
        int textW = font.getStringWidth(label);
        float w = Math.max(minW, textW * textScale + 10F);
        float x = cx - w * 0.5F;
        float y = cy - h * 0.5F;

        int topFill = fail ? 0x4A1220 : lit ? 0x3D2E14 : PANEL;
        int botFill = fail ? 0x280A12 : lit ? 0x221A0D : BG;
        int borderCol = fail ? CRIMSON_HI : lit ? GOLD_HI : accentRgb;
        int rivetCol = fail ? CRIMSON_HI : GOLD_MID;

        vGrad(x + 1F, y + 1F, w - 2F, h - 2F, topFill, 0.94F, botFill, 0.96F, false);

        beginShape(false);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_I(borderCol, a(lit || fail ? 0.98F : 0.85F));
        quad(t, x + 1F, y, w - 2F, 1F);
        quad(t, x, y + 1F, 1F, h - 2F);
        quad(t, x + w - 1F, y + 1F, 1F, h - 2F);
        t.setColorRGBA_I(shade(borderCol, 0.72F), a(0.92F));
        quad(t, x + 1F, y + h - 1F, w - 2F, 1F);
        // Rebites dourados nos cantos
        t.setColorRGBA_I(rivetCol, 220);
        quad(t, x + 1.5F, y + 1.5F, 1.2F, 1.2F);
        quad(t, x + w - 2.7F, y + 1.5F, 1.2F, 1.2F);
        quad(t, x + 1.5F, y + h - 2.7F, 1.2F, 1.2F);
        quad(t, x + w - 2.7F, y + h - 2.7F, 1.2F, 1.2F);
        t.draw();
        endShape();

        if (lit || fail) {
            float half = (w - 4F) * 0.5F;
            hGrad(x + 2F, y + 1F, half, 1F, borderCol, 0.08F, 0xFFFFFF, 0.36F, true);
            hGrad(x + 2F + half, y + 1F, half, 1F, 0xFFFFFF, 0.36F, borderCol, 0.08F, true);
        }

        int txtCol = fail ? CRIMSON_HI : lit ? GOLD_HI : TEXT;
        textCentered(font, label, cx, cy - 3.6F * textScale, textScale, (int) (w - 4F), txtCol);
    }

    /** Chama estilizada em 2 camadas (aura externa + núcleo luminoso) com leve tremulação. */
    static void flame(float cx, float cy, float size, boolean hot, boolean fail, float timeSec) {
        if (size <= 0.5F) return;
        float sway = (hot || fail) ? (float) Math.sin(timeSec * 14F + cx * 0.2F) * 0.08F : 0F;
        int outerCol = fail ? CRIMSON_HI : hot ? 0xFF9A3C : 0x6A5640;
        int innerCol = fail ? 0xFFB8C6 : hot ? GOLD_HI : 0x9A8468;
        float outerA = (hot || fail) ? 0.94F : 0.44F;
        float innerA = (hot || fail) ? 0.98F : 0.34F;

        drawTeardrop(cx, cy, size * (1F + sway * 0.45F), size * (1F + sway), outerCol, outerA);
        drawTeardrop(cx, cy + size * 0.18F, size * 0.48F, size * 0.56F * (1F + sway * 0.6F), innerCol, innerA);
    }

    private static void drawTeardrop(float cx, float cy, float sx, float sy, int rgb, float alpha) {
        beginShape(false);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_TRIANGLE_FAN);
        t.setColorRGBA_I(rgb, a(alpha));
        t.addVertex(cx, cy + sy * 0.15F, 0);
        int segs = 18;
        for (int i = 0; i <= segs; i++) {
            double u = TWO_PI * i / segs;
            // Forma de gota/chama: ponta fina em cima (-sy*1.15) e base arredondada (+sy*0.78)
            double sin = Math.sin(u);
            double cos = Math.cos(u);
            double pinch = (1.0 - cos) * 0.5; // 0 no topo, 1 na base
            double px = sin * (0.28 + 0.58 * Math.pow(pinch, 0.55)) * sx;
            double py = (-cos * 0.95 - 0.18 * Math.pow(1.0 - pinch, 2.0)) * sy;
            t.addVertex(cx + px, cy + py, 0);
        }
        t.draw();
        endShape();
    }

    /** Coração paramétrico com gradiente centro->borda para profundidade arcana. */
    static void heart(float cx, float cy, float size, int centerRgb, int edgeRgb, float alpha) {
        if (size <= 0.5F || alpha <= 0.01F) return;
        beginShape(false);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_TRIANGLE_FAN);
        t.setColorRGBA_I(centerRgb, a(alpha));
        t.addVertex(cx, cy, 0);
        t.setColorRGBA_I(edgeRgb, a(alpha));
        for (int i = 0; i <= 40; i++) {
            double ang = TWO_PI * i / 40.0;
            double sn = Math.sin(ang);
            double px = 16.0 * sn * sn * sn;
            double py = 13.0 * Math.cos(ang) - 5.0 * Math.cos(2.0 * ang)
                    - 2.0 * Math.cos(3.0 * ang) - Math.cos(4.0 * ang);
            t.addVertex(cx + px * size / 16.0, cy - py * size / 16.0, 0);
        }
        t.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        endShape();
    }

    /** Selo de cera carmim-dourado com 'X' (erros e derrota). */
    static void waxSeal(float cx, float cy, float radius, float timeSec) {
        float pulse = 0.80F + 0.20F * (float) Math.sin(timeSec * 4.5F);
        glow(cx, cy, radius * 2.1F, CRIMSON_HI, 0.34F * pulse);
        disc(cx, cy, radius, CRIMSON, 0.96F, 0.90F);
        ring(cx, cy, radius + 0.3F, 0.9F, GOLD_HI, 0.92F * pulse, 0F, 1, 1F, true);
        float s = radius * 0.44F;
        beginShape(false);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_I(TEXT, 240);
        addLineQuad(t, cx - s, cy - s, cx + s, cy + s, 1.25F);
        addLineQuad(t, cx + s, cy - s, cx - s, cy + s, 1.25F);
        t.draw();
        endShape();
    }

    /** Marca de progresso/acerto: 0 = pendente, 1 = acertou (losango+estrela), 2 = errou (selo carmim). */
    static void progressMark(float cx, float cy, float radius, int status, int accentRgb, float timeSec) {
        if (status == 2) {
            waxSeal(cx, cy, radius * 0.95F, timeSec);
            return;
        }
        boolean hit = (status == 1);
        float r = hit ? radius : radius * 0.85F;
        if (hit) glow(cx, cy, radius * 2.2F, accentRgb, 0.40F);

        beginShape(false);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_I(hit ? PANEL_LIGHT : BG, a(hit ? 0.94F : 0.84F));
        t.addVertex(cx, cy - r, 0);
        t.addVertex(cx - r, cy, 0);
        t.addVertex(cx, cy + r, 0);
        t.addVertex(cx + r, cy, 0);
        t.draw();
        endShape();

        polygon(cx, cy, r, 4, hit ? 1.25F : 0.95F, hit ? GOLD_HI : BORDER, hit ? 0.96F : 0.85F, 0F, hit);
        if (hit) {
            star(cx, cy, radius * 0.72F, GOLD_HI, 0.96F, 0F);
        }
    }

    /** Uma runa do Giz de Mago (ou glifo geométrico de fallback caso o AM2 não esteja presente). */
    static void rune(Minecraft mc, int variant, float cx, float cy, float size, int rgb, float alpha, boolean glow) {
        if (alpha <= 0.01F || size <= 1F) return;
        if (glow) glow(cx, cy, size * 0.9F, rgb, alpha * 0.28F);
        if (!HAS_AM2) {
            polygon(cx, cy, size * 0.36F, 4, 0.9F, rgb, alpha, 0F, glow);
            star(cx, cy, size * 0.28F, rgb, alpha * 0.85F, 0F);
            return;
        }
        mc.getTextureManager().bindTexture(ActorRenderer.CHALK_RUNES[variant & 15]);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float half = size * 0.5F;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_I(rgb, a(alpha));
        t.addVertexWithUV(cx - half, cy + half, 0, 0, 1);
        t.addVertexWithUV(cx + half, cy + half, 0, 1, 1);
        t.addVertexWithUV(cx + half, cy - half, 0, 1, 0);
        t.addVertexWithUV(cx - half, cy - half, 0, 0, 0);
        t.draw();
        if (glow) {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            t.startDrawingQuads();
            t.setColorRGBA_I(rgb, a(alpha * 0.52F));
            t.addVertexWithUV(cx - half, cy + half, 0, 0, 1);
            t.addVertexWithUV(cx + half, cy + half, 0, 1, 1);
            t.addVertexWithUV(cx + half, cy - half, 0, 1, 0);
            t.addVertexWithUV(cx - half, cy - half, 0, 0, 0);
            t.draw();
        }
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** Faixa horizontal de runas com onda de luz viajante (usada em "Observe..." e "Prepare-se..."). */
    static void runeStrip(Minecraft mc, float x, float y, float width, int count, int rgb, float alpha, float timeSec) {
        if (count <= 1 || width < 16F) return;
        float half = width * 0.5F;
        hGrad(x, y, half, 0.8F, rgb, 0F, rgb, alpha * 0.45F, true);
        hGrad(x + half, y, half, 0.8F, rgb, alpha * 0.45F, rgb, 0F, true);
        float wave = ((timeSec * 3.4F) % (count + 3)) - 1.5F;
        for (int i = 0; i < count; i++) {
            float rx = x + width * (i / (float) (count - 1));
            float boost = Math.max(0F, 1F - Math.abs(i - wave) / 2.2F);
            int col = boost > 0.30F ? mix(rgb, GOLD_HI, boost * 0.65F) : rgb;
            rune(mc, i * 3 + 1, rx, y, 7F, col, Math.min(1F, alpha + boost * 0.62F), boost > 0.35F);
        }
    }

    /** Texto centralizado com ajuste automático de escala dentro de {@code maxWidth}. */
    static void textCentered(FontRenderer font, String text, float cx, float y, float scale, int maxWidth, int rgb) {
        if (text == null || text.isEmpty()) return;
        int full = font.getStringWidth(text);
        if (full <= 0) return;
        float fit = maxWidth > 0 ? Math.min(scale, maxWidth / (float) full) : scale;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glPushMatrix();
        GL11.glTranslatef(cx - full * fit * 0.5F, y, 0F);
        GL11.glScalef(fit, fit, 1F);
        font.drawStringWithShadow(text, 0, 0, rgb);
        GL11.glPopMatrix();
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    static int mix(int rgbA, int rgbB, float t) {
        float f = Math.max(0F, Math.min(1F, t));
        int ar = (rgbA >> 16) & 255, ag = (rgbA >> 8) & 255, ab = rgbA & 255;
        int br = (rgbB >> 16) & 255, bg = (rgbB >> 8) & 255, bb = rgbB & 255;
        int r = (int) (ar + (br - ar) * f);
        int g = (int) (ag + (bg - ag) * f);
        int b = (int) (ab + (bb - ab) * f);
        return (r << 16) | (g << 8) | b;
    }

    static int shade(int rgb, float factor) {
        int r = Math.min(255, Math.max(0, (int) (((rgb >> 16) & 255) * factor)));
        int g = Math.min(255, Math.max(0, (int) (((rgb >> 8) & 255) * factor)));
        int b = Math.min(255, Math.max(0, (int) ((rgb & 255) * factor)));
        return (r << 16) | (g << 8) | b;
    }

    private static int a(float alpha) {
        return Math.max(0, Math.min(255, (int) (alpha * 255F)));
    }

    private static void quad(Tessellator t, float x, float y, float w, float h) {
        t.addVertex(x, y + h, 0);
        t.addVertex(x + w, y + h, 0);
        t.addVertex(x + w, y, 0);
        t.addVertex(x, y, 0);
    }

    private static void quadSpan(Tessellator t, float x0, float y0, float x1, float y1) {
        float minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        float minY = Math.min(y0, y1), maxY = Math.max(y0, y1);
        quad(t, minX, minY, maxX - minX, maxY - minY);
    }

    private static void addLineQuad(Tessellator t, float x0, float y0, float x1, float y1, float width) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001F) return;
        float nx = -dy / len * width * 0.5F;
        float ny = dx / len * width * 0.5F;
        t.addVertex(x0 + nx, y0 + ny, 0);
        t.addVertex(x1 + nx, y1 + ny, 0);
        t.addVertex(x1 - nx, y1 - ny, 0);
        t.addVertex(x0 - nx, y0 - ny, 0);
    }

    private static void beginShape(boolean additive) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, additive ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void endShape() {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }
}
