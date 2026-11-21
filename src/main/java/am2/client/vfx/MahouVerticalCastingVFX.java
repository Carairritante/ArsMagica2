package am2.client.vfx;

import am2.LogHelper;
import am2.api.spell.enums.Affinity;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Open-centred vertical architecture. All geometry is prepared once, never parsed per frame. */
@SideOnly(Side.CLIENT)
public final class MahouVerticalCastingVFX {
    private static final Map<String, CachedMesh> CACHE = new HashMap<String, CachedMesh>();
    private static final float ALPHA = .72f;

    private static final class CachedMesh {
        final ResourceLocation texture;
        final MahouCastingMesh mesh;
        final int[] lists = new int[3];
        CachedMesh(String variant) throws IOException {
            texture = new ResourceLocation("arsmagica2", "textures/mahou_vertical/" + variant + ".png");
            mesh = MahouCastingMesh.read(Minecraft.getMinecraft().getResourceManager().getResource(
                    new ResourceLocation("arsmagica2", "models/mahou_vertical/" + variant + ".mesh")).getInputStream());
        }
    }

    private MahouVerticalCastingVFX() {}

    static String variant(Affinity affinity, CastingCircleVFX.SuperTierType tier) {
        if (tier != null) return tier.textureSuffix;
        return affinity == null ? "none" : affinity.name().toLowerCase(Locale.ROOT);
    }

    private static CachedMesh cached(String variant) {
        if (!CACHE.containsKey(variant)) {
            try { CACHE.put(variant, new CachedMesh(variant)); }
            catch (IOException invalid) {
                // Fail once and omit this visual; do not fall back to the old opaque discs.
                LogHelper.error("Unable to load vertical casting model " + variant + ": " + invalid.getMessage());
                CACHE.put(variant, null);
            }
        }
        return CACHE.get(variant);
    }

    static void render(double x, double feetY, double z, Affinity affinity,
            CastingCircleVFX.SuperTierType tier, float useTicks, float distanceSq) {
        CachedMesh cached = cached(variant(affinity, tier));
        if (cached == null) return;
        float seconds = Math.max(0, useTicks) / 20f;
        float fade = Math.min(1, Math.max(0, (96f - (float)Math.sqrt(distanceSq)) / 16f));
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_POLYGON_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, feetY + .035, z);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_BACK);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            Minecraft.getMinecraft().getTextureManager().bindTexture(cached.texture);
            for (int phase = 1; phase <= MahouCastingMesh.maxPhase(tier != null); phase++) {
                if (seconds >= cached.mesh.phaseEnds[phase - 1]) {
                    if (cached.lists[phase - 1] == 0) cached.lists[phase - 1] = compile(cached.mesh, phase);
                    GL11.glColor4f(1, 1, 1, ALPHA * fade);
                    GL11.glCallList(cached.lists[phase - 1]);
                } else {
                    drawOpening(cached.mesh, phase, seconds, fade);
                }
            }
            // Keep a coloured body against bright skies. Additive light alone washes
            // red/gold runes to white when added to the blue daylight background.
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glColor4f(1, 1, 1, .10f * fade);
            for (int phase = 1; phase <= MahouCastingMesh.maxPhase(tier != null); phase++) {
                if (seconds >= cached.mesh.phaseEnds[phase - 1]) GL11.glCallList(cached.lists[phase - 1]);
            }
            drawTips(seconds, tier != null, fade);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
        }
    }

    private static int compile(MahouCastingMesh mesh, int phase) {
        int list = GLAllocation.generateDisplayLists(1);
        GL11.glNewList(list, GL11.GL_COMPILE);
        // Immediate vertices inside a list are uploaded once; no client array lifetime assumptions.
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < mesh.phases.length; i++) if (mesh.phases[i] == phase) {
            float[] vertices = mesh.vertices[i];
            for (int j = 0; j < vertices.length; j += 5) {
                GL11.glTexCoord2f(vertices[j + 3], vertices[j + 4]);
                GL11.glVertex3f(vertices[j], vertices[j + 1], vertices[j + 2]);
            }
        }
        GL11.glEnd();
        GL11.glEndList();
        return list;
    }

    private static void drawOpening(MahouCastingMesh mesh, int phase, float seconds, float fade) {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (int i = 0; i < mesh.phases.length; i++) if (mesh.phases[i] == phase) {
            float reveal = MahouCastingMesh.reveal(seconds, mesh.starts[i], mesh.ends[i]);
            if (reveal <= 0) continue;
            t.setColorRGBA_F(1, 1, 1, ALPHA * fade * reveal);
            float[] v = mesh.vertices[i];
            // Sweep each textured face from its leading edge, rather than scale cubes through the camera.
            for (int face = 0; face < v.length; face += 20) {
                vertex(t, v, face, face, 1);
                vertex(t, v, face + 5, face + 5, 1);
                vertex(t, v, face + 5, face + 10, reveal);
                vertex(t, v, face, face + 15, reveal);
            }
        }
        t.draw();
    }

    private static void vertex(Tessellator t, float[] v, int from, int to, float a) {
        t.addVertexWithUV(v[from] + (v[to] - v[from]) * a,
                v[from+1] + (v[to+1] - v[from+1]) * a, v[from+2] + (v[to+2] - v[from+2]) * a,
                v[from+3] + (v[to+3] - v[from+3]) * a, v[from+4] + (v[to+4] - v[from+4]) * a);
    }

    private static void drawTips(float seconds, boolean superTier, float fade) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (int phase = 1; phase <= MahouCastingMesh.maxPhase(superTier); phase++) {
            float start = phase == 1 ? 0 : phase == 2 ? 1.55f : 3.65f;
            float end = phase == 1 ? 1.4f : phase == 2 ? 3.78f : 6.83f;
            if (seconds <= start || seconds >= end) continue;
            double angle = (seconds - start) / (end - start) * Math.PI * 2;
            double radius = phase == 1 ? 4.5 : phase == 2 ? 9.25 : 16;
            double x = Math.sin(angle) * radius, z = Math.cos(angle) * radius;
            double y = phase == 1 ? 1.06 : phase == 2 ? 3.375 : 6.625;
            t.setColorRGBA_F(1, .97f, .88f, .8f * fade);
            // Small edge spark, never a beam in the caster's line of sight.
            double s = .07;
            t.addVertex(x-s,y,z); t.addVertex(x,y+s,z); t.addVertex(x+s,y,z); t.addVertex(x,y-s,z);
            t.addVertex(x,y,z-s); t.addVertex(x,y+s,z); t.addVertex(x,y,z+s); t.addVertex(x,y-s,z);
        }
        t.draw();
    }
}
