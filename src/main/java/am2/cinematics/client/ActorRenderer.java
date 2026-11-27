package am2.cinematics.client;

import am2.cinematics.scene.Scene;
import cpw.mods.fml.common.Loader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBook;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/** Desenha no mundo os atores da cena: livro, círculo de runas e brilho. */
final class ActorRenderer {
    private static final ResourceLocation BOOK_TEXTURE =
            new ResourceLocation("amcinematics", "textures/compendium_book.png");
    private static final String RUNES = "abcdefghijklmnopqrstuvwxyz";
    /** As runas do Giz de Mago do AM2; sem o AM2 usa o alfabeto das mesas de encantamento. */
    static final ResourceLocation[] CHALK_RUNES = new ResourceLocation[16];
    static {
        for (int i = 0; i < CHALK_RUNES.length; i++) {
            CHALK_RUNES[i] = new ResourceLocation("arsmagica2", "textures/blocks/wizardchalk/WizardChalk" + (i + 1) + ".png");
        }
    }
    private static final double TWO_PI = Math.PI * 2;

    private final ModelBook book = new ModelBook();
    private final RitualRenderer ritual = new RitualRenderer();
    private final Scene.ActorKey key = new Scene.ActorKey();
    private final double[] offset = new double[3];
    private double anim;

    /** Nova cinemática: o terreno é medido de novo a partir da altura da âncora. */
    void prepare(World world, double anchorY) {
        ritual.prepare(world, anchorY);
    }

    /**
     * Chamar em RenderWorldLastEvent, com a matriz da câmera ativa.
     *
     * @param time posição na linha do tempo (pontos dos atores)
     * @param anim relógio do que se mexe sozinho; segue andando nas pausas
     * @param flare direção da vela em destaque num minigame, ou -1
     */
    void draw(Minecraft mc, Scene scene, double time, double anim, int flare, double x, double y, double z,
            float yaw) {
        this.anim = anim;
        ritual.flare(flare);
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            // Primeiro o que é sólido; os efeitos aditivos entram por cima.
            for (Scene.Actor actor : scene.actors) {
                if (isSolid(actor.type)) draw(mc, actor, time, x, y, z, yaw);
            }
            for (Scene.Actor actor : scene.actors) {
                if (!isSolid(actor.type)) draw(mc, actor, time, x, y, z, yaw);
            }
            for (Scene.Event event : scene.events) {
                if (event.type == Scene.Type.RUNES) drawRunes(mc, event, time, x, y, z, yaw);
            }
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            GL11.glPopAttrib();
            GL11.glColor4f(1, 1, 1, 1);
        }
    }

    private static boolean isSolid(Scene.ActorType type) {
        return type == Scene.ActorType.BOOK || type == Scene.ActorType.ITEM || type == Scene.ActorType.CHALK
                || type == Scene.ActorType.CANDLES;
    }

    private void draw(Minecraft mc, Scene.Actor actor, double time, double x, double y, double z, float yaw) {
        // Veias e olhos vão junto com o corpo (BodyPerformer); a noite é do céu (SkyClock).
        if (!actor.type.placed()) return;
        if (!actor.sample(time, key) || key.scale <= 0.001 || key.alpha <= 0.01) return;
        Scene.rotate(key.pos, yaw, offset);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x + offset[0] - RenderManager.renderPosX, y + offset[1] - RenderManager.renderPosY,
                    z + offset[2] - RenderManager.renderPosZ);
            if (actor.type == Scene.ActorType.BOOK) drawBook(mc, anim, yaw);
            else if (actor.type == Scene.ActorType.CIRCLE) drawCircle(mc, actor, anim);
            else if (actor.type == Scene.ActorType.ORB) drawOrb(actor, anim);
            else if (actor.type == Scene.ActorType.GLOW) drawGlow(actor);
            else ritual.draw(mc, actor, key, anim, yaw, x + offset[0], y + offset[1], z + offset[2]);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private void drawBook(Minecraft mc, double time, float anchorYaw) {
        // Direção para onde as páginas olham: 0 grau é "de volta para a âncora".
        double angle = Math.toRadians(key.yaw);
        double[] facing = new double[3];
        Scene.rotate(new double[] { Math.sin(angle), 0, -Math.cos(angle) }, anchorYaw, facing);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glColor4f(1, 1, 1, 1);
        // Flutuação suave.
        GL11.glTranslated(0, Math.sin(time * 0.12) * 0.035, 0);
        GL11.glRotated(-Math.toDegrees(Math.atan2(facing[2], facing[0])), 0, 1, 0);
        GL11.glRotated(key.tilt, 0, 0, 1);
        GL11.glScaled(key.scale, key.scale, key.scale);
        float open = (float) key.open;
        // Fechado, o modelo fica deslocado da dobradiça: recentraliza.
        GL11.glTranslatef(-0.19F * (1 - open), 0, 0);
        float pageA = MathHelper.clamp_float((float) ((time * 0.065) % 1.0) * 1.6F - 0.3F, 0, 1);
        float pageB = MathHelper.clamp_float((float) ((time * 0.065 + 0.5) % 1.0) * 1.6F - 0.3F, 0, 1);
        mc.getTextureManager().bindTexture(BOOK_TEXTURE);
        book.render((Entity) null, (float) time, pageA, pageB, open, 0.0F, 0.0625F);
    }

    static void additive() {
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.004F);
    }

    /** Círculo no chão: anéis, hexagrama e runas, traçado conforme {@code open}. */
    private void drawCircle(Minecraft mc, Scene.Actor actor, double time) {
        additive();
        double radius = key.scale, sweep = key.open;
        int alpha = (int) (key.alpha * 255);
        GL11.glRotated(time * actor.spin, 0, 1, 0);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        ring(radius * 0.95, radius, sweep, actor.color, alpha);
        ring(radius * 0.905, radius * 0.92, sweep, actor.color, alpha * 2 / 3);
        ring(radius * 0.62, radius * 0.645, sweep, actor.color, alpha);
        ring(radius * 0.97, radius * 1.1, sweep, actor.color, alpha / 6);
        // Dois triângulos, lado a lado, aparecendo junto com o traçado.
        for (int side = 0; side < 6; side++) {
            double visible = Math.max(0, Math.min(1, sweep * 6 - side));
            if (visible <= 0) continue;
            double from = TWO_PI * (side % 3) / 3 + (side / 3) * Math.PI / 3;
            double to = from + TWO_PI / 3;
            double ax = Math.cos(from) * radius * 0.62, az = Math.sin(from) * radius * 0.62;
            double bx = Math.cos(to) * radius * 0.62, bz = Math.sin(to) * radius * 0.62;
            line(ax, az, ax + (bx - ax) * visible, az + (bz - az) * visible, radius * 0.012, actor.color, alpha * 3 / 4);
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        int runes = 28;
        float size = (float) (radius * 0.026);
        for (int i = 0; i < (int) (runes * sweep); i++) {
            // O FontRenderer trata alfa quase zero como opaco.
            if (alpha < 8) break;
            String rune = String.valueOf(RUNES.charAt((i * 7) % RUNES.length()));
            GL11.glPushMatrix();
            GL11.glRotated(-360.0 * i / runes, 0, 1, 0);
            GL11.glTranslated(0, 0.005, -radius * 0.775);
            GL11.glRotatef(90, 1, 0, 0);
            GL11.glScalef(size, size, size);
            mc.standardGalacticFontRenderer.drawString(rune,
                    -mc.standardGalacticFontRenderer.getStringWidth(rune) / 2, -4, alpha << 24 | actor.color);
            GL11.glPopMatrix();
            // O texto restaura estados próprios; garante o modo aditivo.
            additive();
        }
    }

    private static void ring(double inner, double outer, double sweep, int color, int alpha) {
        int segments = (int) Math.ceil(96 * sweep);
        if (segments <= 0) return;
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorRGBA_I(color, alpha);
        for (int i = 0; i < segments; i++) {
            double a = TWO_PI * sweep * i / segments, b = TWO_PI * sweep * (i + 1) / segments;
            tess.addVertex(Math.cos(a) * inner, 0, Math.sin(a) * inner);
            tess.addVertex(Math.cos(a) * outer, 0, Math.sin(a) * outer);
            tess.addVertex(Math.cos(b) * outer, 0, Math.sin(b) * outer);
            tess.addVertex(Math.cos(b) * inner, 0, Math.sin(b) * inner);
        }
        tess.draw();
    }

    private static void line(double ax, double az, double bx, double bz, double width, int color, int alpha) {
        double dx = bx - ax, dz = bz - az, length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1e-4) return;
        double nx = -dz / length * width, nz = dx / length * width;
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorRGBA_I(color, alpha);
        tess.addVertex(ax + nx, 0, az + nz);
        tess.addVertex(ax - nx, 0, az - nz);
        tess.addVertex(bx - nx, 0, bz - nz);
        tess.addVertex(bx + nx, 0, bz + nz);
        tess.draw();
    }

    private void drawRunes(Minecraft mc, Scene.Event e, double time, double x, double y, double z, float yaw) {
        if (time < e.t || time > e.t + e.duration) return;
        boolean chalk = Loader.isModLoaded("arsmagica2");
        double[] local = new double[3];
        for (int i = 0; i < e.count; i++) {
            double u = Scene.rune(e, i, time, local);
            if (u < 0) continue;
            // Surge, voa e encolhe ao entrar.
            double fade = Math.min(1, Math.min(u / 0.15, (1 - u) / 0.08));
            int alpha = (int) (255 * Math.max(0, fade));
            if (alpha < 8) continue;
            double size = e.scale * (1 - 0.65 * u);
            Scene.rotate(local, yaw, offset);
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x + offset[0] - RenderManager.renderPosX, y + offset[1] - RenderManager.renderPosY,
                        z + offset[2] - RenderManager.renderPosZ);
                faceCamera();
                GL11.glRotated(Math.sin(i * 2.7 + time * 0.09) * 25, 0, 0, 1);
                additive();
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                if (chalk) {
                    mc.getTextureManager().bindTexture(CHALK_RUNES[i % CHALK_RUNES.length]);
                    sprite(size * 1.35, e.color, alpha / 3);
                    sprite(size, e.color, alpha);
                    sprite(size * 0.9, 0xFFFFFF, alpha / 2);
                } else {
                    String letter = String.valueOf(RUNES.charAt(i % RUNES.length()));
                    float pixel = (float) (size / 8);
                    GL11.glScalef(-pixel, -pixel, pixel);
                    mc.standardGalacticFontRenderer.drawString(letter, -3, -4, alpha << 24 | e.color);
                }
            } finally {
                GL11.glPopMatrix();
            }
        }
    }

    static void sprite(double size, int color, int alpha) {
        double half = size / 2;
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorRGBA_I(color, alpha);
        tess.addVertexWithUV(half, half, 0, 0, 0);
        tess.addVertexWithUV(half, -half, 0, 0, 1);
        tess.addVertexWithUV(-half, -half, 0, 1, 1);
        tess.addVertexWithUV(-half, half, 0, 1, 0);
        tess.draw();
    }

    /** Depois disto, +Z aponta para longe da câmera. */
    static void faceCamera() {
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0, 1, 0);
        GL11.glRotatef(RenderManager.instance.playerViewX, 1, 0, 0);
    }

    /** Bolinha de mana: miolo branco e halo, pulsando, à frente do corpo. */
    private void drawOrb(Scene.Actor actor, double time) {
        additive();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        faceCamera();
        // Puxada para a câmera: aparece "dentro" do peito sem ser coberta por ele.
        GL11.glTranslated(0, 0, -0.35);
        double radius = key.scale * (1 + 0.22 * Scene.heartbeat(time - actor.keys.get(0).t));
        int alpha = (int) (key.alpha * 255);
        fan(radius, actor.color, alpha * 2 / 3);
        fan(radius * 0.55, actor.color, alpha);
        fan(radius * 0.3, 0xFFFFFF, alpha);
    }

    /** Disco de luz voltado para a câmera, sumindo nas bordas. */
    private void drawGlow(Scene.Actor actor) {
        additive();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        faceCamera();
        // Um pouco atrás do ponto: o livro fica na frente do próprio halo.
        GL11.glTranslated(0, 0, 0.45);
        int alpha = (int) (key.alpha * 255);
        fan(key.scale, actor.color, alpha * 3 / 5);
        fan(key.scale * 0.4, actor.color, alpha / 2);
    }

    static void fan(double radius, int color, int alpha) {
        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_TRIANGLE_FAN);
        tess.setColorRGBA_I(color, alpha);
        tess.addVertex(0, 0, 0);
        tess.setColorRGBA_I(color, 0);
        for (int i = 0; i <= 40; i++) {
            double a = TWO_PI * i / 40;
            tess.addVertex(Math.cos(a) * radius, Math.sin(a) * radius, 0);
        }
        tess.draw();
    }
}
