package am2.cinematics.client;

import am2.cinematics.scene.Scene;
import am2.cinematics.scene.VeinMap;
import cpw.mods.fml.common.Loader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/**
 * Atores do ritual: giz e velas recriados (o ritual já os consumiu), itens
 * voando, eclipse, raízes e anéis. A matriz já está na posição do ator.
 */
final class RitualRenderer {
    private static final double TWO_PI = Math.PI * 2;

    private final GroundMap ground = new GroundMap();
    private final Map<Scene.Actor, List<double[]>> veinPieces = new HashMap<Scene.Actor, List<double[]>>();
    /** Posição no mundo do ator que está sendo desenhado (a matriz está nela). */
    private double wx, wy, wz;
    private int flare = -1;

    /** Vela em destaque num minigame: 0 frente, 1 direita, 2 trás, 3 esquerda, -1 nenhuma. */
    void flare(int direction) {
        flare = direction;
    }

    private boolean flared(double[] point) {
        if (flare < 0) return false;
        double side = Math.abs(point[0]), forward = Math.abs(point[1]);
        int direction = forward >= side ? (point[1] >= 0 ? 0 : 2) : (point[0] >= 0 ? 1 : 3);
        return direction == flare;
    }

    void prepare(World world, double anchorY) {
        ground.reset(world, anchorY);
        veinPieces.clear();
    }

    /** Altura do chão em um ponto relativo ao ator, também relativa a ele. */
    private double floor(double x, double z) {
        return ground.at(wx + x, wz + z) - wy;
    }

    void draw(Minecraft mc, Scene.Actor actor, Scene.ActorKey key, double time, float yaw, double x, double y, double z) {
        wx = x;
        wy = y;
        wz = z;
        switch (actor.type) {
            case CHALK:
                chalk(mc, actor, key, time, yaw);
                break;
            case CANDLES:
                candles(actor, key, time, yaw);
                break;
            case WORLDVEINS:
                worldVeins(actor, key, time);
                break;
            case PILLAR:
                pillar(actor, key, time);
                break;
            case WAVE:
                wave(actor, key, time);
                break;
            case ITEM:
                item(mc, actor, key, time);
                break;
            case RAIN:
                rain(actor, key, time);
                break;
            case ROOTS:
                roots(actor, key, time);
                break;
            case RING:
                ring(actor, key, time);
                break;
            default:
                break;
        }
    }

    private static void solid() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.02F);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    private static double[] spot(double[] point, float yaw) {
        double[] offset = new double[3];
        Scene.rotate(new double[] { point[0], 0, point[1] }, yaw, offset);
        return offset;
    }

    /** Ordem de acendimento: de fora para o centro. */
    private static int rank(Scene.Actor actor, int index) {
        double[] mine = actor.points.get(index);
        double own = mine[0] * mine[0] + mine[1] * mine[1];
        int rank = 0;
        for (int i = 0; i < actor.points.size(); i++) {
            double[] other = actor.points.get(i);
            double distance = other[0] * other[0] + other[1] * other[1];
            if (distance > own || distance == own && i < index) rank++;
        }
        return rank;
    }

    private void chalk(Minecraft mc, Scene.Actor actor, Scene.ActorKey key, double time, float yaw) {
        boolean textured = Loader.isModLoaded("arsmagica2");
        int count = actor.points.size();
        for (int i = 0; i < count; i++) {
            double[] at = spot(actor.points.get(i), yaw);
            at[1] = floor(at[0], at[2]);
            double lit = Math.max(0, Math.min(1, key.open * (count + 3) - rank(actor, i)));
            if (textured) {
                mc.getTextureManager().bindTexture(ActorRenderer.CHALK_RUNES[(i * 7) % ActorRenderer.CHALK_RUNES.length]);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                solid();
                GL11.glDepthMask(false);
                flat(at[0], at[1] + 0.02, at[2], 0.5, 0xFFFFFF, (int) (220 * key.alpha));
                if (lit > 0) {
                    ActorRenderer.additive();
                    double pulse = 0.82 + 0.18 * Math.sin(time * 0.25 - i);
                    flat(at[0], at[1] + 0.03, at[2], 0.5, actor.color, (int) (255 * key.alpha * lit * pulse));
                    flat(at[0], at[1] + 0.035, at[2], 0.62, actor.color, (int) (110 * key.alpha * lit * pulse));
                }
            }
            if (lit > 0) {
                // Mancha de luz sob a runa.
                ActorRenderer.additive();
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glShadeModel(GL11.GL_SMOOTH);
                GL11.glPushMatrix();
                GL11.glTranslated(at[0], at[1] + 0.015, at[2]);
                GL11.glRotatef(90, 1, 0, 0);
                ActorRenderer.fan(0.75, actor.color, (int) (90 * key.alpha * lit));
                GL11.glPopMatrix();
            }
        }
        GL11.glDepthMask(true);
    }

    private static void flat(double x, double y, double z, double half, int color, int alpha) {
        if (alpha <= 0) return;
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorRGBA_I(color, Math.min(255, alpha));
        tess.addVertexWithUV(x - half, y, z - half, 0, 0);
        tess.addVertexWithUV(x - half, y, z + half, 0, 1);
        tess.addVertexWithUV(x + half, y, z + half, 1, 1);
        tess.addVertexWithUV(x + half, y, z - half, 1, 0);
        tess.draw();
    }

    /** Velas de cera; a chama de cada uma acende quando {@code open} passa por ela. */
    private void candles(Scene.Actor actor, Scene.ActorKey key, double time, float yaw) {
        int count = actor.points.size();
        for (int i = 0; i < count; i++) {
            double[] at = spot(actor.points.get(i), yaw);
            at[1] = floor(at[0], at[2]);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            solid();
            GL11.glDepthMask(true);
            int alpha = (int) (255 * key.alpha);
            box(at[0], at[1], at[2], 0.09, 0.34, 0xE8DFC4, alpha);
            box(at[0], at[1] + 0.34, at[2], 0.012, 0.05, 0x2A2018, alpha);
            double lit = Math.max(0, Math.min(1, (key.open * count - i) * 2));
            if (lit <= 0) continue;
            ActorRenderer.additive();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            double flicker = 1 + 0.12 * Math.sin(time * 1.7 + i * 2) + 0.07 * Math.sin(time * 3.1 + i);
            int glow = (int) (255 * key.alpha * lit);
            GL11.glPushMatrix();
            GL11.glTranslated(at[0], at[1] + 0.47, at[2]);
            ActorRenderer.faceCamera();
            ActorRenderer.fan(0.55 * flicker, actor.color, glow / 3);
            GL11.glScaled(0.75, 1.45, 1);
            ActorRenderer.fan(0.13 * flicker, actor.color, glow);
            ActorRenderer.fan(0.06 * flicker, 0xFFF6D8, glow);
            if (flared(actor.points.get(i))) {
                // A chama chamada pelo minigame cresce e clareia.
                ActorRenderer.fan(1.25 * flicker, actor.color, glow * 3 / 4);
                ActorRenderer.fan(0.45 * flicker, 0xFFF6D8, glow);
            }
            GL11.glPopMatrix();
        }
    }

    private static void box(double x, double y, double z, double half, double height, int color, int alpha) {
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        for (int side = 0; side < 4; side++) {
            double ax = side == 0 || side == 3 ? -half : half, az = side < 2 ? -half : half;
            double bx = side == 0 || side == 1 ? half : -half, bz = side == 1 || side == 2 ? half : -half;
            tess.setColorRGBA_I(shade(color, side % 2 == 0 ? 0.82 : 0.66), alpha);
            tess.addVertex(x + ax, y, z + az);
            tess.addVertex(x + bx, y, z + bz);
            tess.addVertex(x + bx, y + height, z + bz);
            tess.addVertex(x + ax, y + height, z + az);
        }
        tess.setColorRGBA_I(color, alpha);
        tess.addVertex(x - half, y + height, z - half);
        tess.addVertex(x - half, y + height, z + half);
        tess.addVertex(x + half, y + height, z + half);
        tess.addVertex(x + half, y + height, z - half);
        tess.draw();
    }

    private static int shade(int color, double factor) {
        return (int) ((color >> 16 & 255) * factor) << 16 | (int) ((color >> 8 & 255) * factor) << 8
                | (int) ((color & 255) * factor);
    }

    /** O ícone do próprio item, flutuando com um halo da cor do ator. */
    private void item(Minecraft mc, Scene.Actor actor, Scene.ActorKey key, double time) {
        Object registered = Item.itemRegistry.getObject(actor.item);
        if (!(registered instanceof Item)) return;
        Item item = (Item) registered;
        ItemStack stack = new ItemStack(item, 1, actor.meta);
        int alpha = (int) (255 * key.alpha);
        GL11.glTranslated(0, Math.sin(time * 0.14 + actor.meta + actor.item.length()) * 0.05, 0);
        ActorRenderer.faceCamera();

        ActorRenderer.additive();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glPushMatrix();
        GL11.glTranslated(0, 0, 0.12);
        ActorRenderer.fan(key.scale * 0.95, actor.color, alpha * 2 / 5);
        GL11.glPopMatrix();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        solid();
        GL11.glDepthMask(true);
        mc.getTextureManager().bindTexture(
                stack.getItemSpriteNumber() == 0 ? TextureMap.locationBlocksTexture : TextureMap.locationItemsTexture);
        int passes = item.requiresMultipleRenderPasses() ? item.getRenderPasses(actor.meta) : 1;
        for (int pass = 0; pass < passes; pass++) {
            IIcon icon = item.requiresMultipleRenderPasses() ? item.getIcon(stack, pass) : stack.getIconIndex();
            if (icon == null) continue;
            double half = key.scale / 2, z = -0.002 * pass;
            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            tess.setColorRGBA_I(item.getColorFromItemStack(stack, pass) & 0xFFFFFF, alpha);
            tess.addVertexWithUV(half, half, z, icon.getMinU(), icon.getMinV());
            tess.addVertexWithUV(half, -half, z, icon.getMinU(), icon.getMaxV());
            tess.addVertexWithUV(-half, -half, z, icon.getMaxU(), icon.getMaxV());
            tess.addVertexWithUV(-half, half, z, icon.getMaxU(), icon.getMinV());
            tess.draw();
        }
    }

    /**
     * Chuva desenhada pela própria cena, em volta da câmera: aparece em qualquer
     * bioma (a chuva do jogo não cai em deserto, por exemplo).
     */
    private void rain(Scene.Actor actor, Scene.ActorKey key, double time) {
        solid();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDepthMask(false);
        double cx = RenderManager.renderPosX - wx, cy = RenderManager.renderPosY - wy, cz = RenderManager.renderPosZ - wz;
        int drops = (int) (1100 * key.alpha);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        for (int i = 0; i < drops; i++) {
            // Espalhamento fixo por gota (sequência de baixa discrepância).
            double dx = ((i * 0.6180339887) % 1.0) * 44 - 22, dz = ((i * 0.7548776662) % 1.0) * 44 - 22;
            double flat = Math.sqrt(dx * dx + dz * dz);
            if (flat < 1.2) continue;
            double x = cx + dx, z = cz + dz;
            double y = cy + 14 - (time * 1.35 + i * 3.77) % 30;
            if (y < floor(x, z)) continue;
            double nx = -dz / flat * 0.016, nz = dx / flat * 0.016;
            tess.setColorRGBA_I(actor.color, (int) (150 * key.alpha * (1 - flat / 40)));
            tess.addVertex(x - nx, y, z - nz);
            tess.addVertex(x + nx, y, z + nz);
            tess.addVertex(x + nx + 0.12, y + 1.25, z + nz);
            tess.addVertex(x - nx + 0.12, y + 1.25, z - nz);
        }
        tess.draw();
        GL11.glDepthMask(true);
    }

    // ---------------------------------------------------------------- terreno

    /**
     * Veias no chão: cada trecho curto fica deitado na altura da sua coluna, com um
     * degrau vertical onde o terreno muda. {@code scale} é o raio, {@code open} o
     * quanto já se espalharam e {@code pulse} a onda que volta ao centro.
     */
    private void worldVeins(Scene.Actor actor, Scene.ActorKey key, double time) {
        List<double[]> pieces = veinPieces.get(actor);
        if (pieces == null) {
            // Raio final das veias, não o do instante: o desenho é feito uma vez.
            double radius = key.scale;
            for (Scene.ActorKey k : actor.keys) radius = Math.max(radius, k.scale);
            pieces = layVeins(radius);
            veinPieces.put(actor, pieces);
        }
        ActorRenderer.additive();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator tess = Tessellator.instance;
        double reach = key.open * 1.0001;
        double crest = key.pulse < 0 ? -9 : 1 - key.pulse;
        // Halo largo e depois o fio.
        for (int pass = 0; pass < 2; pass++) {
            tess.startDrawingQuads();
            for (double[] p : pieces) {
                // x1, y, z1, x2, z2, from, to, largura, y anterior
                if (p[5] > reach) continue;
                double part = Math.min(1, (reach - p[5]) / Math.max(1e-6, p[6] - p[5]));
                double x2 = p[0] + (p[3] - p[0]) * part, z2 = p[2] + (p[4] - p[2]) * part;
                double near = Math.max(0, 1 - Math.abs(p[5] - crest) / 0.11);
                // A ponta que avança brilha mais.
                double tip = key.open < 0.999 ? Math.max(0, 1 - (reach - p[5]) / 0.07) : 0;
                double beat = 0.72 + 0.28 * Math.sin(time * 0.35 - p[5] * 22);
                double light = Math.min(1, beat + tip + near);
                int color = near > 0.15 ? actor.pulseColor : actor.color;
                double width = p[7] * (pass == 0 ? 3.2 : 1) * (1 + 1.4 * near);
                tess.setColorRGBA_I(color, (int) (255 * key.alpha * light * (pass == 0 ? 0.22 : 0.95)));
                double dx = x2 - p[0], dz = z2 - p[2], length = Math.sqrt(dx * dx + dz * dz);
                if (length < 1e-4) continue;
                double nx = -dz / length * width / 2, nz = dx / length * width / 2;
                double y = p[1] + 0.035 + pass * 0.004;
                tess.addVertex(p[0] + nx, y, p[2] + nz);
                tess.addVertex(p[0] - nx, y, p[2] - nz);
                tess.addVertex(x2 - nx, y, z2 - nz);
                tess.addVertex(x2 + nx, y, z2 + nz);
                if (p[8] != p[1]) {
                    double low = Math.min(p[8], p[1]) + 0.035, high = Math.max(p[8], p[1]) + 0.035;
                    tess.addVertex(p[0] + nx, low, p[2] + nz);
                    tess.addVertex(p[0] - nx, low, p[2] - nz);
                    tess.addVertex(p[0] - nx, high, p[2] - nz);
                    tess.addVertex(p[0] + nx, high, p[2] + nz);
                }
            }
            tess.draw();
        }
    }

    private List<double[]> layVeins(double radius) {
        List<double[]> pieces = new ArrayList<double[]>();
        Map<Long, Double> ends = new HashMap<Long, Double>();
        for (VeinMap.Segment s : VeinMap.generate(23L)) {
            int count = Math.max(1, (int) Math.ceil(radius * Math.hypot(s.x2 - s.x1, s.z2 - s.z1) / 1.05));
            Double before = ends.get(point(s.x1, s.z1));
            double previous = before == null ? Double.NaN : before;
            for (int i = 0; i < count; i++) {
                double a = (double) i / count, b = (double) (i + 1) / count;
                double x1 = radius * (s.x1 + (s.x2 - s.x1) * a), z1 = radius * (s.z1 + (s.z2 - s.z1) * a);
                double x2 = radius * (s.x1 + (s.x2 - s.x1) * b), z2 = radius * (s.z1 + (s.z2 - s.z1) * b);
                // O bloco central é o próprio altar: as veias saem do chão em volta dele.
                double mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;
                double y = Math.abs(mx) < 0.5 && Math.abs(mz) < 0.5 ? floor(0.75, 0.75) : floor(mx, mz);
                double step = Double.isNaN(previous) || Math.abs(previous - y) > 3 ? y : previous;
                // Perto do altar são finas; longe engrossam para continuar visíveis lá de cima.
                double far = Math.min(1, Math.hypot(mx, mz) / 28);
                double thickness = (0.05 + 0.26 * s.width) * (1 + far * Math.max(0, radius / 30 - 1));
                pieces.add(new double[] { x1, y, z1, x2, z2, s.from + (s.to - s.from) * a, s.from + (s.to - s.from) * b,
                        thickness, step });
                previous = y;
            }
            ends.put(point(s.x2, s.z2), previous);
        }
        return pieces;
    }

    private static long point(double x, double z) {
        return (long) Math.round(x * 1e5) << 32 | Math.round(z * 1e5) & 0xFFFFFFFFL;
    }

    /** Coluna de luz: {@code scale} é o raio e {@code open} o quanto já subiu. */
    private void pillar(Scene.Actor actor, Scene.ActorKey key, double time) {
        ActorRenderer.additive();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        double height = 90 * key.open;
        int alpha = (int) (255 * key.alpha);
        shaft(key.scale * 1.9, height, actor.color, alpha / 6, time * 0.01);
        shaft(key.scale, height, actor.color, alpha / 3, -time * 0.025);
        shaft(key.scale * 0.45, height, 0xFFFFFF, alpha / 2, time * 0.04);
        // Anéis subindo pela coluna.
        for (int i = 0; i < 6; i++) {
            double y = (time * 0.55 + i * 5.5) % 33;
            if (y > height) continue;
            GL11.glPushMatrix();
            GL11.glTranslated(0, y, 0);
            double radius = key.scale * (1.25 + 0.25 * Math.sin(time * 0.2 + i));
            annulus3(radius, radius * 1.18, actor.color, (int) (alpha * 0.6 * (1 - y / 33)));
            GL11.glPopMatrix();
        }
    }

    private static void shaft(double radius, double height, int color, int alpha, double turn) {
        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= 32; i++) {
            double a = TWO_PI * i / 32 + turn;
            // Faixas verticais mais e menos claras.
            double band = 0.65 + 0.35 * Math.sin(i * TWO_PI * 5 / 32);
            tess.setColorRGBA_I(color, (int) (alpha * band));
            tess.addVertex(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            tess.setColorRGBA_I(color, 0);
            tess.addVertex(Math.cos(a) * radius, height, Math.sin(a) * radius);
        }
        tess.draw();
    }

    /** Onda de choque que acompanha o relevo: {@code scale} é o raio. */
    private void wave(Scene.Actor actor, Scene.ActorKey key, double time) {
        ActorRenderer.additive();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        int alpha = (int) (255 * key.alpha), segments = 120;
        Tessellator tess = Tessellator.instance;
        for (int layer = 0; layer < 2; layer++) {
            tess.startDrawing(GL11.GL_TRIANGLE_STRIP);
            for (int i = 0; i <= segments; i++) {
                double a = TWO_PI * i / segments;
                double x = Math.cos(a) * key.scale, z = Math.sin(a) * key.scale;
                double y = floor(x, z) + 0.05;
                tess.setColorRGBA_I(actor.color, alpha);
                tess.addVertex(x, y, z);
                tess.setColorRGBA_I(actor.color, 0);
                if (layer == 0) {
                    tess.addVertex(x, y + 1.6 + 0.4 * Math.sin(a * 9 + time * 0.4), z);
                } else {
                    double inner = Math.max(0, key.scale - 3.5);
                    tess.addVertex(Math.cos(a) * inner, y, Math.sin(a) * inner);
                }
            }
            tess.draw();
        }
    }

    // ---------------------------------------------------------------- reagentes

    /** Raízes que brotam de um anel e se enroscam para o centro. {@code open} é o crescimento. */
    private void roots(Scene.Actor actor, Scene.ActorKey key, double time) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        int alpha = (int) (255 * key.alpha);
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0) {
                solid();
                GL11.glDepthMask(true);
            } else {
                ActorRenderer.additive();
            }
            Tessellator tess = Tessellator.instance;
            for (int root = 0; root < 12; root++) {
                double start = TWO_PI * root / 12 + (root % 3) * 0.14, turn = root % 2 == 0 ? 1.15 : -0.9;
                double tall = 0.7 + 0.65 * ((root * 5) % 4) / 3.0;
                tess.startDrawing(GL11.GL_TRIANGLE_STRIP);
                for (int step = 0; step <= 26; step++) {
                    double s = key.open * step / 26;
                    double r = key.scale * (1 - 0.55 * s) + 0.12 * Math.sin(s * 17 + root);
                    double a = start + turn * s;
                    double y = tall * Math.sin(Math.PI * s * 0.78) + 0.05 + floor(Math.cos(a) * r, Math.sin(a) * r);
                    double width = (pass == 0 ? 0.13 : 0.05) * (1 - 0.85 * step / 26);
                    // Seiva verde correndo pela raiz.
                    double sap = 0.35 + 0.65 * Math.max(0, Math.sin(s * 14 - time * 0.28 + root));
                    tess.setColorRGBA_I(pass == 0 ? 0x3B2713 : actor.color, pass == 0 ? alpha : (int) (alpha * sap));
                    tess.addVertex(Math.cos(a) * r, y + width, Math.sin(a) * r);
                    tess.addVertex(Math.cos(a) * (r + width), y - width, Math.sin(a) * (r + width));
                }
                tess.draw();
            }
        }
    }

    /** Anel ondulante (água, areia): {@code scale} é o raio e {@code open} o arco. */
    private void ring(Scene.Actor actor, Scene.ActorKey key, double time) {
        ActorRenderer.additive();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glRotated(time * actor.spin, 0, 1, 0);
        int alpha = (int) (255 * key.alpha), segments = (int) Math.ceil(80 * key.open);
        Tessellator tess = Tessellator.instance;
        for (int band = 0; band < 3 && segments > 0; band++) {
            double radius = key.scale * (1 - 0.07 * band), height = 0.34 - 0.08 * band;
            tess.startDrawing(GL11.GL_TRIANGLE_STRIP);
            for (int i = 0; i <= segments; i++) {
                double a = TWO_PI * key.open * i / segments;
                double wave = 0.14 * Math.sin(a * (5 + band) - time * 0.3 * (band + 1));
                double fade = key.open < 0.999 ? Math.min(1, Math.min(i, segments - i) / 8.0) : 1;
                tess.setColorRGBA_I(actor.color, (int) (alpha * fade * (0.8 - 0.2 * band)));
                tess.addVertex(Math.cos(a) * radius, wave, Math.sin(a) * radius);
                tess.setColorRGBA_I(actor.color, 0);
                tess.addVertex(Math.cos(a) * radius, wave + height, Math.sin(a) * radius);
            }
            tess.draw();
            annulus3(radius * 0.93, radius * 1.03, actor.color, alpha / 3);
        }
    }

    private static void annulus3(double inner, double outer, int color, int alpha) {
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorRGBA_I(color, alpha);
        for (int i = 0; i < 48; i++) {
            double a = TWO_PI * i / 48, b = TWO_PI * (i + 1) / 48;
            tess.addVertex(Math.cos(a) * inner, 0, Math.sin(a) * inner);
            tess.addVertex(Math.cos(a) * outer, 0, Math.sin(a) * outer);
            tess.addVertex(Math.cos(b) * outer, 0, Math.sin(b) * outer);
            tess.addVertex(Math.cos(b) * inner, 0, Math.sin(b) * inner);
        }
        tess.draw();
    }
}
