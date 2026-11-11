package am2.client.vfx;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Client-only scenery. No weather entities, gameplay projectiles or block changes. */
@SideOnly(Side.CLIENT)
public final class CastingAtmosphereVFX {
    private static final float[] ANIME_WHITE = {.88f, .96f, 1};
    private static final float[] ANIME_BLUE = {.20f, .65f, 1};
    private static final float[] ANIME_VIOLET = {.58f, .24f, 1};
    private static double viewX, viewY, viewZ, viewRX, viewRZ, viewUX, viewUY, viewUZ;
    private static final List<StormArc> stormArcs = new ArrayList<StormArc>();
    private static boolean cleanWeather, cleanCasting, traditionalCasting;

    private static final class StormArc {
        final double[] x = new double[9], y = new double[9], z = new double[9];
        int owner, age;
    }

    private static void updateStormSky(Minecraft mc) {
        int quality = mc.gameSettings.particleSetting;
        int cap = quality >= 2 ? 2 : quality == 1 ? 8 : 18;
        for (Iterator<StormArc> it = stormArcs.iterator(); it.hasNext();) if (++it.next().age > 12) it.remove();
        while (stormArcs.size() > cap) stormArcs.remove(0);
        for (SkyCell cell : visibleSky) {
            if (cell.type != CastingCircleVFX.SuperTierType.STORM_LIGHTNING || cell.target < .69f
                    || clientTick < cell.nextStormTick) continue;
            cell.nextStormTick = clientTick + (quality >= 2 ? 24 : cell.target > .95f ? 16 : 24);
            int count = quality == 0 && cell.target > .95f ? 3 : 1;
            for (int n = 0; n < count && stormArcs.size() < cap; n++) {
                StormArc arc = new StormArc(); arc.owner = cell.owner;
                double a = random.nextDouble() * Math.PI * 2, b = a + 1.4 + random.nextDouble() * 1.6;
                double ax = cell.x + Math.cos(a) * 30, az = cell.z + Math.sin(a) * 25;
                double bx = cell.x + Math.cos(b) * 30, bz = cell.z + Math.sin(b) * 25;
                for (int k = 0; k < 9; k++) {
                    double f = k / 8.0, jitter = Math.sin(f * Math.PI);
                    arc.x[k] = ax + (bx - ax) * f + (random.nextDouble() - .5) * 9 * jitter;
                    arc.y[k] = cell.y + 46 + Math.sin(f * 5 + n) * 2;
                    arc.z[k] = az + (bz - az) * f + (random.nextDouble() - .5) * 9 * jitter;
                }
                stormArcs.add(arc);
            }
        }
    }

    /** Alpha-faded sky colour, without forcing Minecraft's rain over the scene. */
    private static void drawAnimeSkyVeil(Tessellator t, float partial) {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        t.startDrawing(GL11.GL_TRIANGLES);
        for (SkyCell cell : visibleSky) {
            if (cell.type != CastingCircleVFX.SuperTierType.STARFALL
                    && cell.type != CastingCircleVFX.SuperTierType.STORM_LIGHTNING) continue;
            float strength = cell.previousStrength + (cell.strength - cell.previousStrength) * partial;
            for (int i = 0; i < 80; i++) {
                double a = i * Math.PI / 40, b = a + Math.PI / 40;
                t.setColorRGBA_F(.014f, .018f, .045f, strength * .72f);
                t.addVertex(cell.x, cell.y + 73, cell.z);
                t.setColorRGBA_F(.014f, .018f, .045f, 0);
                t.addVertex(cell.x + Math.cos(a) * 190, cell.y + 73, cell.z + Math.sin(a) * 190);
                t.addVertex(cell.x + Math.cos(b) * 190, cell.y + 73, cell.z + Math.sin(b) * 190);
            }
	}
		t.draw(); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
	}

	// Precomputed static trigonometry lookup table for 24-segment radial fans (Zero CPU trig calls per frame!)
	private static final double[] GLOW_COS = new double[25];
	private static final double[] GLOW_SIN = new double[25];
	static {
		for (int i = 0; i <= 24; i++) {
			GLOW_COS[i] = Math.cos(i * Math.PI / 12.0);
			GLOW_SIN[i] = Math.sin(i * Math.PI / 12.0);
		}
	}

	private static void glowEllipse(Tessellator t, double x, double y, double z,
			double width, double height, float[] color, float alpha) {
		if (alpha <= .001f) return;
		for (int i = 0; i < 24; i++) {
			double cosA = GLOW_COS[i], sinA = GLOW_SIN[i];
			double cosB = GLOW_COS[i + 1], sinB = GLOW_SIN[i + 1];
			t.setColorRGBA_F(color[0], color[1], color[2], alpha); t.addVertex(x, y, z);
			t.setColorRGBA_F(color[0], color[1], color[2], 0);
			t.addVertex(x + viewRX * cosA * width + viewUX * sinA * height,
					y + viewUY * sinA * height,
					z + viewRZ * cosA * width + viewUZ * sinA * height);
			t.addVertex(x + viewRX * cosB * width + viewUX * sinB * height,
					y + viewUY * sinB * height,
					z + viewRZ * cosB * width + viewUZ * sinB * height);
		}
	}

    /** Camera-facing ribbon with a bright spine, transparent edges and a fading tail. */
    private static void lightTrail(Tessellator t, double ax, double ay, double az,
            double bx, double by, double bz, double width, float[] color, float alpha) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double mx = (ax + bx) * .5 - viewX, my = (ay + by) * .5 - viewY, mz = (az + bz) * .5 - viewZ;
        double nx = dz * my - dy * mz, ny = dx * mz - dz * mx, nz = dy * mx - dx * my;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < .00001) return;
        nx *= width / len; ny *= width / len; nz *= width / len;
        for (int side = -1; side <= 1; side += 2) {
            t.setColorRGBA_F(color[0], color[1], color[2], alpha); t.addVertex(ax, ay, az);
            t.setColorRGBA_F(color[0], color[1], color[2], alpha * .75f); t.addVertex(bx, by, bz);
            t.setColorRGBA_F(color[0], color[1], color[2], 0); t.addVertex(bx + nx * side, by + ny * side, bz + nz * side);
            t.setColorRGBA_F(color[0], color[1], color[2], alpha); t.addVertex(ax, ay, az);
            t.setColorRGBA_F(color[0], color[1], color[2], 0); t.addVertex(bx + nx * side, by + ny * side, bz + nz * side);
            t.addVertex(ax + nx * side, ay + ny * side, az + nz * side);
        }
    }

    private static double catmull(double a, double b, double c, double d, double f) {
        return .5 * ((2 * b) + (-a + c) * f + (2 * a - 5 * b + 4 * c - d) * f * f
                + (-a + 3 * b - 3 * c + d) * f * f * f);
    }

    private static void smoothLightning(Tessellator t, double[] x, double[] y, double[] z, float alpha) {
        for (int i = 0; i < x.length - 1; i++) {
            int p = Math.max(0, i - 1), n = Math.min(x.length - 1, i + 2);
            for (int step = 0; step < 3; step++) {
                double f = step / 3.0, g = (step + 1) / 3.0;
                double ax = catmull(x[p], x[i], x[i+1], x[n], f), bx = catmull(x[p], x[i], x[i+1], x[n], g);
                double ay = catmull(y[p], y[i], y[i+1], y[n], f), by = catmull(y[p], y[i], y[i+1], y[n], g);
                double az = catmull(z[p], z[i], z[i+1], z[n], f), bz = catmull(z[p], z[i], z[i+1], z[n], g);
                lightTrail(t, ax, ay, az, bx, by, bz, 1.05, ANIME_BLUE, alpha * .25f);
                lightTrail(t, ax, ay, az, bx, by, bz, .24, ANIME_BLUE, alpha * .7f);
                lightTrail(t, ax, ay, az, bx, by, bz, .085, ANIME_WHITE, alpha);
            }
        }
    }

    private static void draw3DStarCore(Tessellator t, double cx, double cy, double cz, double r, float[] color, float alpha) {
        if (alpha <= 0.001f) return;
        double topY = cy + r * 1.35;
        double botY = cy - r * 1.35;
        double x1 = cx + r, z1 = cz;
        double x2 = cx, z2 = cz + r;
        double x3 = cx - r, z3 = cz;
        double x4 = cx, z4 = cz - r;

        // Upper 3D pyramid (4 triangles)
        triangle(t, cx, topY, cz, x1, cy, z1, x2, cy, z2, color[0], color[1], color[2], alpha);
        triangle(t, cx, topY, cz, x2, cy, z2, x3, cy, z3, color[0], color[1], color[2], alpha);
        triangle(t, cx, topY, cz, x3, cy, z3, x4, cy, z4, color[0], color[1], color[2], alpha);
        triangle(t, cx, topY, cz, x4, cy, z4, x1, cy, z1, color[0], color[1], color[2], alpha);

        // Lower 3D pyramid (4 triangles)
        triangle(t, cx, botY, cz, x2, cy, z2, x1, cy, z1, color[0], color[1], color[2], alpha);
        triangle(t, cx, botY, cz, x3, cy, z3, x2, cy, z2, color[0], color[1], color[2], alpha);
        triangle(t, cx, botY, cz, x4, cy, z4, x3, cy, z3, color[0], color[1], color[2], alpha);
        triangle(t, cx, botY, cz, x1, cy, z1, x4, cy, z4, color[0], color[1], color[2], alpha);
    }

    private static void drawAnimeComet3D(Tessellator t, Falling f, double curX, double curY, double curZ, float partial) {
        float alpha = 0.95f;
        int count = f.trailCount;
        if (count > 1) {
            double prevSegX = curX, prevSegY = curY, prevSegZ = curZ;
            for (int i = 0; i < count; i++) {
                double nextSegX = f.trailX[i], nextSegY = f.trailY[i], nextSegZ = f.trailZ[i];
                double frac = i / (double)count;
                float segAlpha = alpha * (float)(1.0 - frac * 0.85);

                // 1. Bainha externa violeta fluida
                double outW = (1.30 - frac * 1.00) * 0.85;
                lightTrail(t, prevSegX, prevSegY, prevSegZ, nextSegX, nextSegY, nextSegZ, outW, ANIME_VIOLET, segAlpha * 0.38f);

                // 2. Fita central ciano brilhante
                double midW = (0.45 - frac * 0.35) * 0.85;
                lightTrail(t, prevSegX, prevSegY, prevSegZ, nextSegX, nextSegY, nextSegZ, midW, ANIME_BLUE, segAlpha * 0.85f);

                // 3. Fita cruzada volumétrica 3D (para volume de todos os ângulos)
                ribbon(t, prevSegX, prevSegY, prevSegZ, nextSegX, nextSegY, nextSegZ, midW * 0.70, ANIME_BLUE, segAlpha * 0.50f);

                // 4. Núcleo incandescente branco nos primeiros segmentos
                if (i < 4) {
                    lightTrail(t, prevSegX, prevSegY, prevSegZ, nextSegX, nextSegY, nextSegZ, 0.14 * (1.0 - frac * 0.5), ANIME_WHITE, segAlpha);
                }

                prevSegX = nextSegX; prevSegY = nextSegY; prevSegZ = nextSegZ;
            }
        }

        // Cabeça do Cometa: Diamante 3D Octaédrico (Zero impressão de PNG plano!)
        draw3DStarCore(t, curX, curY, curZ, 0.32, ANIME_WHITE, 1.0f);
        draw3DStarCore(t, curX, curY, curZ, 0.68, ANIME_BLUE, 0.65f);
        draw3DStarCore(t, curX, curY, curZ, 1.25, ANIME_VIOLET, 0.28f);

        // Lentes de Luz Estelar em Cruz de 4 Pontas
        glowEllipse(t, curX, curY, curZ, 2.4, 0.09, ANIME_WHITE, 0.90f);
        glowEllipse(t, curX, curY, curZ, 0.09, 2.4, ANIME_WHITE, 0.90f);
    }

    private static void animeComet(Tessellator t, double x, double y, double z,
            double direction, double size, float alpha) {
        double dx = Math.cos(direction), dz = Math.sin(direction);
        int segments = 6;
        for (int segment = 0; segment < segments; segment++) {
            double f = segment / (double)segments, g = (segment + 1) / (double)segments;
            double ax = x - dx * f * 5.2 * size, ay = y + f * 9.5 * size, az = z - dz * f * 5.2 * size;
            double bx = x - dx * g * 5.2 * size, by = y + g * 9.5 * size, bz = z - dz * g * 5.2 * size;
            float fade = alpha * (float)(1.0 - f * 0.85);

            lightTrail(t, ax, ay, az, bx, by, bz, (0.85 - f * 0.65) * size, ANIME_VIOLET, fade * 0.35f);
            lightTrail(t, ax, ay, az, bx, by, bz, (0.28 - f * 0.18) * size, ANIME_BLUE, fade * 0.80f);
            if (segment < 3) {
                lightTrail(t, ax, ay, az, bx, by, bz, 0.09 * size, ANIME_WHITE, fade);
            }
        }

        draw3DStarCore(t, x, y, z, size * 0.35, ANIME_WHITE, alpha);
        draw3DStarCore(t, x, y, z, size * 0.70, ANIME_BLUE, alpha * 0.60f);
        glowEllipse(t, x, y, z, size * 2.4, size * 0.08, ANIME_WHITE, alpha * 0.90f);
        glowEllipse(t, x, y, z, size * 0.08, size * 2.4, ANIME_WHITE, alpha * 0.90f);
    }


    private static final int MAX_LAVA_ARCS = 64;
    private static final List<LavaArc> lavaArcs = new ArrayList<LavaArc>();
    private static final float[][] LAVA_ARC_COLORS = {
        {1.0f, .025f, .003f, .16f}, {1.0f, .08f, .004f, .38f},
        {1.0f, .34f, .012f, .78f}, {1.0f, .86f, .3f, 1.0f}
    };
    private static final float[] LAVA_ARC_WIDTHS = {1.7f, .95f, .38f, .11f};
    private static final class LavaArc {
        final double[] x = new double[13], y = new double[13], z = new double[13];
        int owner, age, delay, life = 26;
        float strength, width;
    }

    private static void updateLavaSky(Minecraft mc) {
        int quality = mc.gameSettings.particleSetting;
        int globalBudget = quality >= 2 ? 6 : quality == 1 ? 24 : MAX_LAVA_ARCS;
        while (lavaArcs.size() > globalBudget) lavaArcs.remove(0);
        for (SkyCell cell : visibleSky) {
            if (cell.type != CastingCircleVFX.SuperTierType.FIRESTORM || cell.target < .69f
                    || clientTick < cell.nextArcTick) continue;
            boolean climax = cell.target > .95f;
            cell.nextArcTick = clientTick + (quality >= 2 ? 18 : quality == 1 ? 8 : climax ? 4 : 8);
            int perCaster = quality >= 2 ? 2 : quality == 1 ? 8 : 20;
            int owned = 0;
            for (LavaArc arc : lavaArcs) if (arc.owner == cell.owner) owned++;
            int count = quality == 0 && climax ? 2 : 1;
            for (int i = 0; i < count && owned < perCaster && lavaArcs.size() < globalBudget; i++) {
                double a = random.nextDouble() * Math.PI * 2, b = a + 1.5 + random.nextDouble() * 2.5;
                double r1 = 16 + random.nextDouble() * 40, r2 = 28 + random.nextDouble() * 38;
                LavaArc arc = makeLavaArc(cell, cell.x + Math.cos(a) * r1, cell.y + 44 + random.nextDouble() * 8,
                        cell.z + Math.sin(a) * r1, cell.x + Math.cos(b) * r2,
                        cell.y + 44 + random.nextDouble() * 9, cell.z + Math.sin(b) * r2, 0, .75f);
                lavaArcs.add(arc); owned++;
                if (quality == 0 && i == 0 && clientTick % 8 == 0 && owned < perCaster && lavaArcs.size() < globalBudget) {
                    double direction = b + (random.nextBoolean() ? .9 : -.9), branchLength = 20 + random.nextDouble() * 24;
                    lavaArcs.add(makeLavaArc(cell, arc.x[6], arc.y[6], arc.z[6],
                            arc.x[6] + Math.cos(direction) * branchLength, arc.y[6] + random.nextDouble() * 6 - 3,
                            arc.z[6] + Math.sin(direction) * branchLength, 4, .43f));
                    owned++;
                }
            }
        }
    }

    private static LavaArc makeLavaArc(SkyCell cell, double x1, double y1, double z1,
            double x2, double y2, double z2, int delay, float width) {
        LavaArc arc = new LavaArc(); arc.owner = cell.owner; arc.delay = delay;
        arc.strength = Math.min(1, cell.target); arc.width = width;
        double length = Math.sqrt((x2-x1)*(x2-x1)+(z2-z1)*(z2-z1));
        double jitter = Math.min(9, 3 + length * .055);
        for (int i = 0; i < 13; i++) {
            double f = i / 12.0, envelope = Math.sin(f * Math.PI);
            arc.x[i] = x1 + (x2-x1)*f + (random.nextDouble()-.5)*jitter*2*envelope;
            arc.y[i] = y1 + (y2-y1)*f + (random.nextDouble()-.5)*jitter*.55*envelope;
            arc.z[i] = z1 + (z2-z1)*f + (random.nextDouble()-.5)*jitter*2*envelope;
        }
        return arc;
    }

    private static void drawLavaArcs(Tessellator t, float partial, double cx, double cy, double cz) {
        // The same camera-facing polyline model as the existing lightning ribbons.
        // Geometry is cached at birth; rendering never emits or advances an arc.
        for (int layer = 0; layer < LAVA_ARC_WIDTHS.length; layer++) {
            t.startDrawingQuads();
            for (LavaArc arc : lavaArcs) {
                double age = arc.age + partial - arc.delay;
                if (age <= 0 || age >= arc.life || distanceSq(arc.x[6],arc.y[6],arc.z[6],cx,cy,cz) > 180*180) continue;
                double head = Math.min(12, age * 2), tail = Math.max(0, (age - 12) * 12 / (arc.life - 12));
                float envelope = (float)(Math.min(1,age/2)*Math.min(1,(arc.life-age)/6))*arc.strength;
                float[] c = LAVA_ARC_COLORS[layer];t.setColorRGBA_F(c[0],c[1],c[2],c[3]*envelope);
                for (int k = Math.max(0,(int)tail); k < 12 && k < head; k++) {
                    double f0 = Math.max(0,tail-k), f1 = Math.min(1,head-k);
                    double x0=arc.x[k]+(arc.x[k+1]-arc.x[k])*f0, y0=arc.y[k]+(arc.y[k+1]-arc.y[k])*f0, z0=arc.z[k]+(arc.z[k+1]-arc.z[k])*f0;
                    double x1=arc.x[k]+(arc.x[k+1]-arc.x[k])*f1, y1=arc.y[k]+(arc.y[k+1]-arc.y[k])*f1, z1=arc.z[k]+(arc.z[k+1]-arc.z[k])*f1;
                    double dx=x1-x0,dy=y1-y0,dz=z1-z0,mx=(x0+x1)*.5-cx,my=(y0+y1)*.5-cy,mz=(z0+z1)*.5-cz;
                    double nx=dy*(-mz)-dz*(-my),ny=dz*(-mx)-dx*(-mz),nz=dx*(-my)-dy*(-mx);
                    double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
                    if(length<.00001){nx=1;ny=nz=0;length=1;}
                    double scale=arc.width*LAVA_ARC_WIDTHS[layer]/length;nx*=scale;ny*=scale;nz*=scale;
                    t.addVertex(x0-nx,y0-ny,z0-nz);t.addVertex(x0+nx,y0+ny,z0+nz);
                    t.addVertex(x1+nx,y1+ny,z1+nz);t.addVertex(x1-nx,y1-ny,z1-nz);
                }
            }
            t.draw();
        }
    }

    private static final int MAX_FALLING = 384, MAX_IMPACTS = 96, MAX_BOLTS = 40;
    private static final ResourceLocation STORM_CLOUD = new ResourceLocation("arsmagica2", "textures/misc/vfx/anime_storm_cloud_cr34.png");
    private static final java.util.Map<Integer, SkyCell> skyCells = new java.util.LinkedHashMap<Integer, SkyCell>();
    private static final List<SkyCell> skyCandidates = new ArrayList<SkyCell>();
    private static final List<SkyCell> visibleSky = new ArrayList<SkyCell>();
    private static final java.util.Comparator<SkyCell> SKY_DISTANCE = new java.util.Comparator<SkyCell>() {
        public int compare(SkyCell a, SkyCell b) { return Double.compare(a.distance, b.distance); }
    };
    private static long nextFlashTick, flashUntilTick = -1;
    private static final Random random = new Random();
    private static final List<Falling> falling = new ArrayList<Falling>();
    private static final List<Impact> impacts = new ArrayList<Impact>();
    private static final List<Bolt> bolts = new ArrayList<Bolt>();
    private static final Set<Integer> reportedCasters = new HashSet<Integer>();
    private static float currentIntensity, previousIntensity, targetIntensity;
    private static long clientTick, nextSoundTick;
    private static World activeWorld, renderedWorld;
    private static GameSettings renderedSettings;
    private static boolean naturalClouds;
    private static final float[] naturalWeather = new float[4];
    private static int naturalFlash;
    private static final Field[] WEATHER_FIELDS = {
        ReflectionHelper.findField(World.class, "prevRainingStrength", "field_73003_n"),
        ReflectionHelper.findField(World.class, "rainingStrength", "field_73004_o"),
        ReflectionHelper.findField(World.class, "prevThunderingStrength", "field_73018_p"),
        ReflectionHelper.findField(World.class, "thunderingStrength", "field_73017_q")
    };

    private static final class Falling {
        CastingCircleVFX.SuperTierType type;
        int owner, age;
        double x, y, z, previousX, previousY, previousZ, groundY, speed;
        double vx, vy, vz;
        float rotation;
        final double[] trailX = new double[8];
        final double[] trailY = new double[8];
        final double[] trailZ = new double[8];
        int trailCount;
    }
    private static final class Impact {
        CastingCircleVFX.SuperTierType type;
        double x, y, z;
        int age;
    }
    private static final class Bolt {
        CastingCircleVFX.SuperTierType type;
        final double[] x = new double[9], y = new double[9], z = new double[9];
        int age;
    }

    private CastingAtmosphereVFX() {}

    public static void reset() {
        endRender();
        falling.clear(); impacts.clear(); bolts.clear(); lavaArcs.clear(); stormArcs.clear(); reportedCasters.clear();
        cleanWeather = cleanCasting = traditionalCasting = false;
        skyCells.clear(); skyCandidates.clear(); visibleSky.clear();
        previousIntensity = currentIntensity = targetIntensity = 0;
        clientTick = nextSoundTick = nextFlashTick = 0; flashUntilTick = -1;
        activeWorld = null;
    }

    public static void onClientTick(Minecraft mc) {
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) { reset(); return; }
        if (activeWorld != mc.theWorld) { reset(); activeWorld = mc.theWorld; }
        if (mc.isGamePaused()) return;
        clientTick++;
        updateElements(mc);
        targetIntensity = 0;
        reportedCasters.clear();
        cleanCasting = traditionalCasting = false;
        for (SkyCell cell : skyCells.values()) cell.target = 0;
        // Runs on the client tick, when camera overrides have been restored.
        CastingCircleVFX.collectAtmosphere(mc);
        updateSky(mc);
        updateLavaSky(mc);
        updateStormSky(mc);
        if (!reportedCasters.isEmpty()) cleanWeather = cleanCasting && !traditionalCasting;
        previousIntensity = currentIntensity;
        float delta = targetIntensity - currentIntensity;
        currentIntensity += Math.max(-0.035f, Math.min(0.05f, delta));
        if (currentIntensity <= 0) cleanWeather = false;
    }

    public static void reportCasting(EntityPlayer player, CastingCircleVFX.SuperTierType type, int useTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (player == null || type == null || activeWorld == null || player.worldObj != activeWorld
                || !activeWorld.isRemote || mc.thePlayer == null || reportedCasters.size() >= 8
                || player.getDistanceSqToEntity(mc.thePlayer) > 64 * 64
                || !reportedCasters.add(player.getEntityId())) return;
        if (type == CastingCircleVFX.SuperTierType.STARFALL || type == CastingCircleVFX.SuperTierType.STORM_LIGHTNING) cleanCasting = true;
        else traditionalCasting = true;
        float phase = useTicks >= 80 ? 1 : useTicks >= 40 ? 0.70f : 0.35f;
        targetIntensity = Math.max(targetIntensity, phase);
        SkyCell cell = skyCells.get(player.getEntityId());
        if (cell == null) {
            if (skyCells.size() >= 16) skyCells.remove(skyCells.keySet().iterator().next());
            cell = new SkyCell(); cell.owner = player.getEntityId(); skyCells.put(cell.owner, cell);
        }
        cell.chargeTicks = Math.max(0, Math.min(72000, useTicks));
        cell.type = type; cell.x = player.posX; cell.z = player.posZ;
        cell.y = player.boundingBox != null ? player.boundingBox.minY : player.posY - player.yOffset;
        cell.target = phase;
        // Phase 2 (40 ticks / 2s): initial atmospheric precipitation begins brewing.
        // Phase 3 (80 ticks / 4s): full cataclysmic downpour. Minimal particles suppress falling scenery.
        if (useTicks < 40 || mc.gameSettings.particleSetting >= 2) return;
        boolean lightning = type == CastingCircleVFX.SuperTierType.STORM_LIGHTNING || type == CastingCircleVFX.SuperTierType.LUNAR_LIGHTNING;
        int interval = useTicks >= 80 ? (lightning ? 4 : 3) : (lightning ? 8 : 6);
        if (type == CastingCircleVFX.SuperTierType.STORM_LIGHTNING) interval = useTicks >= 80 ? 10 : 18;
        boolean reduced = mc.gameSettings.particleSetting == 1;
        if (reduced) interval *= 2;
        if ((clientTick + Math.floorMod(player.getEntityId(), interval)) % interval != 0) return;
        int baseCount = type == CastingCircleVFX.SuperTierType.STORM_LIGHTNING ? 1
                : type == CastingCircleVFX.SuperTierType.BLIZZARD ? 6
                : type == CastingCircleVFX.SuperTierType.STARFALL ? 2 : lightning ? 3 : 5;
        int count = useTicks >= 80 ? baseCount : Math.max(1, baseCount / 2);
        if (reduced) count = Math.max(1, (count + 1) / 2);
        for (int i = 0; i < count; i++) spawnElement(player, type);
    }

    private static void spawnElement(EntityPlayer player, CastingCircleVFX.SuperTierType type) {
        boolean lightning = type == CastingCircleVFX.SuperTierType.STORM_LIGHTNING
                || type == CastingCircleVFX.SuperTierType.LUNAR_LIGHTNING;
        if (lightning && bolts.size() >= MAX_BOLTS || !lightning && falling.size() >= MAX_FALLING) return;
        int owned = 0;
        for (Falling f : falling) if (f.owner == player.getEntityId()) owned++;
        int maxOwned = type == CastingCircleVFX.SuperTierType.STARFALL ? 22 : (Minecraft.getMinecraft().gameSettings.particleSetting == 0 ? 60 : 30);
        if (!lightning && owned >= maxOwned) return;
        double angle = random.nextDouble() < 0.6
                ? Math.toRadians(player.rotationYaw) + Math.PI / 2 + (random.nextDouble() - 0.5) * 1.5
                : random.nextDouble() * Math.PI * 2;
        double opening = 1;
        if (type == CastingCircleVFX.SuperTierType.STARFALL) {
            SkyCell origin = skyCells.get(player.getEntityId());
            if (origin != null) opening = getStarfallPortalProgress(origin.chargeTicks);
        }
        double currentMaxRadius = 48.0 * opening;
        double radius = type == CastingCircleVFX.SuperTierType.STARFALL
                ? (2.0 + random.nextDouble() * Math.max(1.0, currentMaxRadius - 3.0))
                : 12 + random.nextDouble() * 52;
        double x = player.posX + Math.cos(angle) * radius, z = player.posZ + Math.sin(angle) * radius;
        int bx = (int)Math.floor(x), bz = (int)Math.floor(z);
        // Query only loaded terrain; scenery must never request a new chunk.
        if (!activeWorld.blockExists(bx, 0, bz)) return;
        double ground = activeWorld.getTopSolidOrLiquidBlock(bx, bz) + 0.04;
        double feet = player.boundingBox != null ? player.boundingBox.minY : player.posY - player.yOffset;
        double start = Math.min(activeWorld.getActualHeight() - 1, Math.max(ground + 28,
                feet + (type == CastingCircleVFX.SuperTierType.STARFALL ? 76 : 42)));
        if (start - ground < 3) return;
        if (lightning) {
            Bolt b = new Bolt(); b.type = type;
            for (int i = 0; i < 9; i++) {
                double progress = i / 8.0;
                double bend = i == 8 ? 0 : (1 - progress) * 5;
                b.x[i] = x + (random.nextDouble() - 0.5) * bend;
                b.y[i] = start + (ground - start) * progress;
                b.z[i] = z + (random.nextDouble() - 0.5) * bend;
            }
            bolts.add(b); addImpact(type, x, ground, z);
            if (clientTick >= nextFlashTick) { flashUntilTick = clientTick + 1; nextFlashTick = clientTick + 12; }
            return;
        }
        Falling f = new Falling(); f.type = type; f.owner = player.getEntityId();
        f.x = f.previousX = x; f.z = f.previousZ = z; f.y = f.previousY = start; f.groundY = ground;
        f.speed = type == CastingCircleVFX.SuperTierType.BLIZZARD ? 0.8 : 1.2;
        f.rotation = random.nextFloat() * 360;
        if (type == CastingCircleVFX.SuperTierType.STARFALL) {
            // Trajetória aerodinâmica 3D inclinada (cortando a atmosfera em ~70-75 graus)
            double driftAngle = angle + (random.nextDouble() - 0.5) * 1.4;
            double horizSpeed = 0.32 + random.nextDouble() * 0.38;
            f.vx = Math.cos(driftAngle) * horizSpeed;
            f.vz = Math.sin(driftAngle) * horizSpeed;
            f.vy = -(1.55 + random.nextDouble() * 0.45);
            f.speed = Math.sqrt(f.vx * f.vx + f.vy * f.vy + f.vz * f.vz);
            f.trailCount = 8;
            for (int tIdx = 0; tIdx < 8; tIdx++) {
                f.trailX[tIdx] = x - f.vx * (tIdx + 1);
                f.trailY[tIdx] = start - f.vy * (tIdx + 1);
                f.trailZ[tIdx] = z - f.vz * (tIdx + 1);
            }
        }
        falling.add(f);
    }

    private static void updateElements(Minecraft mc) {
        for (Iterator<LavaArc> it = lavaArcs.iterator(); it.hasNext();) {
            LavaArc arc = it.next(); if (++arc.age > arc.life + arc.delay) it.remove();
        }
        for (Iterator<Impact> it = impacts.iterator(); it.hasNext();) if (++it.next().age > 18) it.remove();
        for (Iterator<Bolt> it = bolts.iterator(); it.hasNext();) if (++it.next().age > 5) it.remove();
        for (Iterator<Falling> it = falling.iterator(); it.hasNext();) {
            Falling f = it.next();
            f.previousX = f.x;
            f.previousY = f.y;
            f.previousZ = f.z;
            f.age++;
            if (f.type == CastingCircleVFX.SuperTierType.STARFALL) {
                // Atualiza posições anteriores no histórico da fita de luz
                for (int tIdx = 7; tIdx > 0; tIdx--) {
                    f.trailX[tIdx] = f.trailX[tIdx - 1];
                    f.trailY[tIdx] = f.trailY[tIdx - 1];
                    f.trailZ[tIdx] = f.trailZ[tIdx - 1];
                }
                f.trailX[0] = f.previousX;
                f.trailY[0] = f.previousY;
                f.trailZ[0] = f.previousZ;

                // Gravidade e aceleração balística contínua
                f.vy = Math.max(-2.9, f.vy - 0.045);
                f.x += f.vx;
                f.y += f.vy;
                f.z += f.vz;

                if (f.y <= f.groundY) { addImpact(f.type, f.x, f.groundY, f.z); it.remove(); }
                else if (f.age > 96) it.remove();
            } else {
                f.speed = Math.min(2.5, f.speed + 0.055);
                f.y -= f.speed;
                if (f.y <= f.groundY) { addImpact(f.type, f.x, f.groundY, f.z); it.remove(); }
                else if (f.age > 96) it.remove();
            }
        }
        if (mc.gameSettings.particleSetting >= 2) { falling.clear(); impacts.clear(); bolts.clear(); }
    }

    private static void addImpact(CastingCircleVFX.SuperTierType type, double x, double y, double z) {
        if (impacts.size() >= MAX_IMPACTS) impacts.remove(0);
        Impact hit = new Impact(); hit.type = type; hit.x = x; hit.y = y; hit.z = z; impacts.add(hit);
        if (clientTick < nextSoundTick) return;
        nextSoundTick = clientTick + 12;
        String sound = type == CastingCircleVFX.SuperTierType.FIRESTORM ? "random.explode"
                : type == CastingCircleVFX.SuperTierType.BLIZZARD ? "dig.glass"
                : type == CastingCircleVFX.SuperTierType.STARFALL ? "random.orb" : "ambient.weather.thunder";
        // The existing vanilla sounds remain local and share a global rate limit.
        activeWorld.playSound(x, y, z, sound, 0.55f, 1.0f, false);
    }

    /** Overlay weather only while drawing; restore all four exact natural values afterwards. */
    public static void beginRender(float partialTicks) {
        endRender();
        Minecraft mc = Minecraft.getMinecraft();
        boolean liveStorm=OrbitalStormVFX.hasActiveStorms();
        boolean charging=mc.theWorld==activeWorld&&currentIntensity>0;
        if(mc.theWorld==null||(!charging&&!liveStorm))return;
        renderedWorld = mc.theWorld;
        naturalFlash = renderedWorld.lastLightningBolt;
        renderedSettings = mc.gameSettings; naturalClouds = renderedSettings.clouds;
        if (cleanWeather || liveStorm) renderedSettings.clouds = false;
        try {
            for (int i = 0; i < WEATHER_FIELDS.length; i++) naturalWeather[i] = WEATHER_FIELDS[i].getFloat(renderedWorld);
            for (int i = 0; i < WEATHER_FIELDS.length; i++) {
                float strength = (i % 2 == 0) ? previousIntensity : currentIntensity;
                if (!charging || cleanWeather && i < 2) strength = 0;
                WEATHER_FIELDS[i].setFloat(renderedWorld, Math.max(naturalWeather[i], strength));
            }
            if (clientTick <= flashUntilTick) renderedWorld.lastLightningBolt = Math.max(naturalFlash, 1);
        } catch (IllegalAccessException exception) {
            endRender();
            throw new IllegalStateException("Cannot apply temporary casting weather", exception);
        }
    }

    public static void endRender() {
        if (renderedWorld == null) return;
        try {
            for (int i = 0; i < WEATHER_FIELDS.length; i++) WEATHER_FIELDS[i].setFloat(renderedWorld, naturalWeather[i]);
            renderedWorld.lastLightningBolt = naturalFlash;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot restore casting weather", exception);
        } finally {
            if (renderedSettings != null) renderedSettings.clouds = naturalClouds;
            renderedSettings = null; renderedWorld = null;
        }
    }

    public static void renderAll(float partial) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != activeWorld || mc.renderViewEntity == null
                || falling.isEmpty() && impacts.isEmpty() && bolts.isEmpty() && lavaArcs.isEmpty() && stormArcs.isEmpty() && visibleSky.isEmpty()) return;
        Entity camera = mc.renderViewEntity;
        double cx = camera.prevPosX + (camera.posX - camera.prevPosX) * partial;
        double cy = camera.prevPosY + (camera.posY - camera.prevPosY) * partial;
        double cz = camera.prevPosZ + (camera.posZ - camera.prevPosZ) * partial;
        double yaw = Math.toRadians(camera.rotationYaw), pitch = Math.toRadians(camera.rotationPitch);
        viewX = cx; viewY = cy; viewZ = cz; viewRX = Math.cos(yaw); viewRZ = Math.sin(yaw);
        viewUX = -Math.sin(yaw) * Math.sin(pitch); viewUY = Math.cos(pitch); viewUZ = Math.cos(yaw) * Math.sin(pitch);
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_LIGHTING_BIT);
        try {
            GL11.glTranslated(-cx, -cy, -cz);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_FOG); GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); GL11.glDepthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            Tessellator t = Tessellator.instance;
            drawAnimeSkyVeil(t, partial);
            drawCloudCanopies(mc, t, partial, cx, cy, cz);
            drawSkySurfaces(t, partial, cx, cy, cz, mc.gameSettings.particleSetting);
            t.startDrawing(GL11.GL_TRIANGLES);
            for (SkyCell cell : visibleSky) if (distanceSq(cell.x, cell.y + 38, cell.z, cx, cy, cz) <= 160 * 160)
                drawApocalypse(t, cell, partial, mc.gameSettings.particleSetting);
            for (Falling f : falling) {
                double x = f.previousX + (f.x - f.previousX) * partial;
                double y = f.previousY + (f.y - f.previousY) * partial;
                double z = f.previousZ + (f.z - f.previousZ) * partial;
                if (distanceSq(x, y, z, cx, cy, cz) > 128 * 128) continue;
                drawFalling(t, f, x, y, z, partial);
            }
            for (Impact hit : impacts) if (distanceSq(hit.x, hit.y, hit.z, cx, cy, cz) <= 128 * 128) drawImpact(t, hit, partial);
            t.draw();
            drawLavaArcs(t, partial, cx, cy, cz);
            t.startDrawing(GL11.GL_TRIANGLES);
            for (StormArc arc : stormArcs) if (distanceSq(arc.x[4],arc.y[4],arc.z[4],cx,cy,cz)<180*180)
                smoothLightning(t,arc.x,arc.y,arc.z,(float)Math.min(1,Math.max(0,(12-arc.age-partial)/6.0)));
            for (Bolt bolt : bolts) if (bolt.type == CastingCircleVFX.SuperTierType.STORM_LIGHTNING)
                smoothLightning(t,bolt.x,bolt.y,bolt.z,Math.max(0,1-(bolt.age+partial)/6));
            t.draw();
            for (Bolt bolt : bolts) if (bolt.type != CastingCircleVFX.SuperTierType.STORM_LIGHTNING
                    && distanceSq(bolt.x[8], bolt.y[8], bolt.z[8], cx, cy, cz) <= 128 * 128) {
                float[] color = bolt.type.primaryColor;
                GL11.glLineWidth(4); GL11.glColor4f(color[0], color[1], color[2], 0.8f);
                drawBolt(t, bolt); GL11.glLineWidth(1.5f); GL11.glColor4f(1, 1, 1, 0.9f); drawBolt(t, bolt);
            }
        } finally {
            GL11.glPopAttrib(); GL11.glPopMatrix();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
        }
    }

    private static final class SkyCell {
        int owner, chargeTicks;
        CastingCircleVFX.SuperTierType type;
        double x, y, z, distance;
        float strength, previousStrength, target;
        long nextArcTick, nextStormTick;
    }

    private static void updateSky(Minecraft mc) {
        skyCandidates.clear(); visibleSky.clear();
        for (Iterator<SkyCell> it = skyCells.values().iterator(); it.hasNext();) {
            SkyCell cell = it.next();
            cell.previousStrength = cell.strength;
            cell.strength += Math.max(-0.04f, Math.min(0.08f, cell.target - cell.strength));
            if (cell.strength <= 0 && cell.target <= 0) { it.remove(); continue; }
            cell.distance = distanceSq(cell.x, cell.y, cell.z, mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
            skyCandidates.add(cell);
        }
        java.util.Collections.sort(skyCandidates, SKY_DISTANCE);
        for (SkyCell cell : skyCandidates) {
            boolean overlaps = false;
            for (SkyCell selected : visibleSky) {
                double dx = selected.x - cell.x, dz = selected.z - cell.z;
                if (selected.type == cell.type && dx * dx + dz * dz < 28 * 28) { overlaps = true; break; }
            }
            if (!overlaps) visibleSky.add(cell);
            if (visibleSky.size() >= 3) break;
        }
    }

    /** World-space cloud volume and funnel, shared by nearby equal storms. */
    private static void drawCloudCanopies(Minecraft mc,Tessellator t,float partial,double cx,double cy,double cz){
        GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        t.startDrawing(GL11.GL_TRIANGLES);
        for(SkyCell cell:visibleSky){
            if(!usesStormClouds(cell.type)||distanceSq(cell.x,cell.y+48,cell.z,cx,cy,cz)>160*160)continue;
            float s=cell.previousStrength+(cell.strength-cell.previousStrength)*partial;
            cloudMass(t,cell.x,cell.y+48,cell.z,s,(clientTick+partial)*.05,
                    mc.gameSettings.particleSetting==0?9:6,1,cell.y+20.035);
        }
        t.draw();GL11.glDisable(GL11.GL_CULL_FACE);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
    }


    private static final double[] activeArcX=new double[9],activeArcY=new double[9],activeArcZ=new double[9];

    private static final int[] stormLobeOrder=new int[12];
    private static final double[] stormLobeX=new double[12],stormLobeZ=new double[12],stormLobeDistance=new double[12];

    private static double fastPow22(double x) {
        if (x <= 0) return 0;
        double x2 = x * x;
        double x4 = x2 * x2;
        double x8 = x4 * x4;
        double x16 = x8 * x8;
        return x16 * x4 * x2;
    }

    /** Cloud geometry is fixed in world space; only draw order depends on the camera. */
    private static void cloudMass(Tessellator t,double cx,double deck,double cz,float alpha,double time,
            int puffs,double scale,double tipY){
        int quality=Minecraft.getMinecraft().gameSettings.particleSetting;
        int slices=quality==0?36:quality==1?24:16,rows=quality==0?14:quality==1?10:8;
        int lobeSlices=quality==0?16:12,lobeRows=quality==0?8:6;
        stormEllipsoid(t,cx,deck+2,cz,43*scale,9*scale,38*scale,time,0,alpha*.94f,lobeSlices*2,lobeRows*2);
        int count=Math.min(8,quality>=2?3:puffs);
        for(int i=0;i<count;i++){
            double a=i*Math.PI*2/count+time*.16,r=(33+Math.sin(i*2.7)*4)*scale;
            stormLobeX[i]=cx+Math.cos(a)*r;stormLobeZ[i]=cz+Math.sin(a)*r;
            stormLobeDistance[i]=(stormLobeX[i]-viewX)*(stormLobeX[i]-viewX)+(stormLobeZ[i]-viewZ)*(stormLobeZ[i]-viewZ);
            stormLobeOrder[i]=i;
        }
        for(int i=1;i<count;i++){
            int item=stormLobeOrder[i],j=i-1;
            while(j>=0&&stormLobeDistance[stormLobeOrder[j]]<stormLobeDistance[item]){stormLobeOrder[j+1]=stormLobeOrder[j];j--;}
            stormLobeOrder[j+1]=item;
        }
        for(int draw=0;draw<count;draw++){
            int i=stormLobeOrder[draw];double size=(10+Math.sin(i*1.7)*2)*scale;
            stormEllipsoid(t,stormLobeX[i],deck+Math.sin(i*2.4+time*.2)*2,stormLobeZ[i],
                    size,7*scale,size*.92,time,i+1,alpha*.90f,lobeSlices,lobeRows);
        }
        for(int row=0;row<rows;row++)for(int i=0;i<slices;i++){
            double f=row/(double)rows,g=(row+1)/(double)rows,a=i*Math.PI*2/slices,b=(i+1)*Math.PI*2/slices;
            funnelVertex(t,cx,tipY,deck,cz,f,a,time,scale,alpha,false);
            funnelVertex(t,cx,tipY,deck,cz,g,a,time,scale,alpha,false);
            funnelVertex(t,cx,tipY,deck,cz,g,b,time,scale,alpha,false);
            funnelVertex(t,cx,tipY,deck,cz,f,a,time,scale,alpha,false);
            funnelVertex(t,cx,tipY,deck,cz,g,b,time,scale,alpha,false);
            funnelVertex(t,cx,tipY,deck,cz,f,b,time,scale,alpha,false);
        }
        // A thin, less dense outer layer carries revolving trails of vapour.
        for(int row=0;row<rows;row++)for(int i=0;i<slices;i++){
            double f=row/(double)rows,g=(row+1)/(double)rows,a=i*Math.PI*2/slices,b=(i+1)*Math.PI*2/slices;
            funnelVertex(t,cx,tipY,deck,cz,f,a,time,scale,alpha,true);
            funnelVertex(t,cx,tipY,deck,cz,g,a,time,scale,alpha,true);
            funnelVertex(t,cx,tipY,deck,cz,g,b,time,scale,alpha,true);
            funnelVertex(t,cx,tipY,deck,cz,f,a,time,scale,alpha,true);
            funnelVertex(t,cx,tipY,deck,cz,g,b,time,scale,alpha,true);
            funnelVertex(t,cx,tipY,deck,cz,f,b,time,scale,alpha,true);
        }
    }

    private static double funnelRadius(double f,double a,double time,double scale){
        double r=1.35+(30*scale-1.35)*Math.pow(f,1.75);
        return r*(1+.07*Math.sin(a*6+f*13-time*1.2)+.025*Math.sin(a*11-f*8+time*.6));
    }

    private static void funnelVertex(Tessellator t,double cx,double tip,double deck,double cz,
            double f,double angle,double time,double scale,float alpha,boolean vapour){
        double r=funnelRadius(f,angle,time,scale)*(vapour?1.08:1);
        double slope=(30*scale-1.35)*1.75*Math.pow(f,.75)/Math.max(1,deck-tip);
        double normal=Math.sqrt(1+slope*slope);
        double nx=Math.cos(angle)/normal,ny=-slope/normal,nz=Math.sin(angle)/normal;
        float density=vapour?(float)(.23*Math.pow(Math.max(0,Math.sin(angle*5+f*14-time*1.15)),2)):.78f;
        stormShade(t,nx,ny,nz,angle+f*4,time,alpha*density);
        double bend=Math.sin(f*Math.PI)*Math.sin(time*.18)*1.1;
        t.addVertex(cx+Math.cos(angle)*r+bend,tip+(deck-tip)*f,cz+Math.sin(angle)*r);
    }

    private static void stormEllipsoid(Tessellator t,double x,double y,double z,double rx,double ry,double rz,
            double time,int seed,float alpha,int slices,int rows){
        for(int j=0;j<rows;j++)for(int i=0;i<slices;i++){
            double a=i*Math.PI*2/slices,b=(i+1)*Math.PI*2/slices;
            double f=-Math.PI/2+j*Math.PI/rows,g=-Math.PI/2+(j+1)*Math.PI/rows;
            stormEllipsoidVertex(t,x,y,z,rx,ry,rz,a,f,time,seed,alpha);
            stormEllipsoidVertex(t,x,y,z,rx,ry,rz,a,g,time,seed,alpha);
            stormEllipsoidVertex(t,x,y,z,rx,ry,rz,b,g,time,seed,alpha);
            stormEllipsoidVertex(t,x,y,z,rx,ry,rz,a,f,time,seed,alpha);
            stormEllipsoidVertex(t,x,y,z,rx,ry,rz,b,g,time,seed,alpha);
            stormEllipsoidVertex(t,x,y,z,rx,ry,rz,b,f,time,seed,alpha);
        }
    }

    private static void stormEllipsoidVertex(Tessellator t,double x,double y,double z,double rx,double ry,double rz,
            double a,double p,double time,int seed,float alpha){
        double nx=Math.cos(p)*Math.cos(a),ny=Math.sin(p),nz=Math.cos(p)*Math.sin(a);
        double billow=1+.045*Math.sin(a*5+p*4+seed)+.025*Math.sin(a*9-p*6-time*.35+seed);
        stormShade(t,nx,ny,nz,a+seed,time,alpha);
        t.addVertex(x+nx*rx*billow,y+ny*ry*billow,z+nz*rz*billow);
    }

    private static void stormShade(Tessellator t,double nx,double ny,double nz,double phase,double time,float alpha){
        double shade=.28+.72*Math.max(0,-nx*.4+ny*.65+nz*.55);
        double flash=fastPow22(Math.max(0,Math.sin(time*2.1+phase*1.3)))*.11;
        t.setColorRGBA_F((float)(.025+shade*.075+flash*.3),
                (float)(.04+shade*.105+flash*.6),(float)(.065+shade*.15+flash),alpha);
    }

    private static void stormFunnelLight(Tessellator t,double x,double tip,double deck,double z,
            double time,float alpha,double scale){
        // The neck touches the original 20-block casting pillar exactly.
        double bottom=tip-20.035;
        lightTrail(t,x,bottom+.1,z,x,deck-2,z,.95,ANIME_BLUE,alpha*.16f);
        lightTrail(t,x,bottom+.1,z,x,deck-2,z,.25,ANIME_BLUE,alpha*.35f);
        lightTrail(t,x,bottom+.1,z,x,deck-2,z,.065,ANIME_WHITE,alpha*.55f);
        int steps = 28;
        for(int arm=0;arm<3;arm++)for(int i=0;i<steps;i++){
            double f=i/(double)steps,g=(i+1)/(double)steps,a=arm*Math.PI*2/3+f*Math.PI*4-time*1.15,b=arm*Math.PI*2/3+g*Math.PI*4-time*1.15;
            double r0=funnelRadius(f,a,time,scale)*1.035,r1=funnelRadius(g,b,time,scale)*1.035;
            double ax=x+Math.cos(a)*r0,az=z+Math.sin(a)*r0,bx=x+Math.cos(b)*r1,bz=z+Math.sin(b)*r1;
            float glow=alpha*(float)(.12+.12*f);
            lightTrail(t,ax,tip+(deck-tip)*f,az,bx,tip+(deck-tip)*g,bz,.65,ANIME_BLUE,glow);
            lightTrail(t,ax,tip+(deck-tip)*f,az,bx,tip+(deck-tip)*g,bz,.07,ANIME_WHITE,glow*.65f);
        }
        glowEllipse(t,x,tip+.1,z,2.6,2.6,ANIME_BLUE,alpha*.25f);
    }


    /** Shared cloud artwork for the actual, packet-synchronized Storm after release. */
    public static void renderActiveStormCanopy(Minecraft mc,Tessellator t,double x,double deck,double z,
            double camX,double camY,double camZ,float alpha,double time,boolean overcharged){
        Entity camera=mc.renderViewEntity!=null?mc.renderViewEntity:mc.thePlayer;
        if(camera==null)return;
        double yaw=Math.toRadians(camera.rotationYaw),pitch=Math.toRadians(camera.rotationPitch);
        viewX=camX;viewY=camY;viewZ=camZ;viewRX=Math.cos(yaw);viewRZ=Math.sin(yaw);
        viewUX=-Math.sin(yaw)*Math.sin(pitch);viewUY=Math.cos(pitch);viewUZ=Math.cos(yaw)*Math.sin(pitch);
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT
                |GL11.GL_CURRENT_BIT|GL11.GL_TEXTURE_BIT|GL11.GL_LIGHTING_BIT);
        try{
            GL11.glTranslated(-camX,-camY,-camZ);GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            t.startDrawing(GL11.GL_TRIANGLES);
            cloudMass(t,x,deck,z,alpha,time,mc.gameSettings.particleSetting==0?9:6,overcharged?1.5:1.25,deck-43.965);
            t.draw();GL11.glDisable(GL11.GL_CULL_FACE);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
            t.startDrawing(GL11.GL_TRIANGLES);
            stormFunnelLight(t,x,deck-43.965,deck,z,time,alpha,overcharged?1.5:1.25);
            double cycle=time*2,phase=cycle-Math.floor(cycle),seed=Math.floor(cycle);
            float pulse=alpha*(float)Math.sin(phase*Math.PI);
            int arcs=mc.gameSettings.particleSetting>=2?1:overcharged?4:3;
            for(int arc=0;arc<arcs;arc++){
                double a=arc*2.1+seed*.71,b=a+1.8;
                double ax=x+Math.cos(a)*34,az=z+Math.sin(a)*29,bx=x+Math.cos(b)*34,bz=z+Math.sin(b)*29;
                for(int k=0;k<9;k++){
                    double f=k/8.0,jitter=Math.sin(f*Math.PI);
                    activeArcX[k]=ax+(bx-ax)*f+Math.sin(k*2.3+seed+arc*7)*3*jitter;
                    activeArcY[k]=deck-2+Math.sin(k*1.3+seed)*1.7;
                    activeArcZ[k]=az+(bz-az)*f+Math.cos(k*2.7+seed+arc*7)*3*jitter;
                }
                smoothLightning(t,activeArcX,activeArcY,activeArcZ,pulse);
            }
            t.draw();
        }finally{GL11.glPopAttrib();GL11.glPopMatrix();}
    }


    private static void cloudBillboard(Tessellator t, double x, double y, double z,
            double width, double height, float red, float green, float blue, float alpha) {
        // Vertex alpha feathers all boundaries, including the source art's margins.
        for (int row = 0; row < 6; row++) for (int col = 0; col < 8; col++) {
            cloudVertex(t,x,y,z,width,height,col/8.0,(row+1)/6.0,red,green,blue,alpha);
            cloudVertex(t,x,y,z,width,height,(col+1)/8.0,(row+1)/6.0,red,green,blue,alpha);
            cloudVertex(t,x,y,z,width,height,(col+1)/8.0,row/6.0,red,green,blue,alpha);
            cloudVertex(t,x,y,z,width,height,col/8.0,row/6.0,red,green,blue,alpha);
        }
    }

    private static void cloudVertex(Tessellator t, double x, double y, double z, double width, double height,
            double u, double v, float red, float green, float blue, float alpha) {
        float edge = (float)Math.min(1, Math.min(Math.min(u,1-u),Math.min(v,1-v))*8);
        double rx = (u*2-1)*width, ry = (1-v*2)*height;
        t.setColorRGBA_F(red, green, blue, alpha * edge);
        t.addVertexWithUV(x+viewRX*rx+viewUX*ry,y+viewUY*ry,z+viewRZ*rx+viewUZ*ry,u,v);
    }


    private static void cloudQuad(Tessellator t, double x, double y, double z, double size,
            double rx, double rz, double ux, double uy, double uz) {
        t.addVertexWithUV(x - (rx + ux) * size, y - uy * size, z - (rz + uz) * size, 0, 1);
        t.addVertexWithUV(x + (rx - ux) * size, y - uy * size, z + (rz - uz) * size, 1, 1);
        t.addVertexWithUV(x + (rx + ux) * size, y + uy * size, z + (rz + uz) * size, 1, 0);
        t.addVertexWithUV(x + (ux - rx) * size, y + uy * size, z + (uz - rz) * size, 0, 0);
    }

    private static void drawApocalypse(Tessellator t, SkyCell cell, float partial, int particles) {
        float s = cell.previousStrength + (cell.strength - cell.previousStrength) * partial;
        double time = (clientTick + partial) * 0.05, deck = cell.y + 38 + s * 10;
        float[] color = cell.type.primaryColor;
        if (usesStormClouds(cell.type)) drawStormDetails(t, cell, time, deck, s, color);
        else if (cell.type == CastingCircleVFX.SuperTierType.BLIZZARD) drawIceSky(t, cell, time, s, false);
        else if (cell.type == CastingCircleVFX.SuperTierType.STARFALL) drawAstralSky(t, cell, time, s, false, particles);
        else if (cell.type == CastingCircleVFX.SuperTierType.LUNAR_LIGHTNING) drawLunarSky(t, cell, time, s, false);
        if (cell.type == CastingCircleVFX.SuperTierType.BLIZZARD) {
            // Broad, faint wind fronts sweep below the cloud bank.
            for (int band = 0; band < 3; band++) for (int i = 0; i < 18; i++) {
                double a = band * 2.1 + i * 0.10 + time * 0.22, b = a + 0.10, r = 32 + band * 11;
                curtain(t, cell.x + Math.cos(a) * r, cell.y + 6, cell.z + Math.sin(a) * r,
                        cell.x + Math.cos(b) * r, cell.y + 6, cell.z + Math.sin(b) * r, 25 * s, color, s * 0.025f);
            }
        }
        if (particles >= 2 || s < 0.45f || cell.type == CastingCircleVFX.SuperTierType.STARFALL
                || cell.type == CastingCircleVFX.SuperTierType.STORM_LIGHTNING) return;
        int motes = particles == 0 ? 96 : 32;
        for (int i = 0; i < motes; i++) {
            double a = i * 2.399963 + time * 0.13, r = 7 + (i * 19 % 57);
            double progress = ((i * 0.618033 - time * 0.23) % 1 + 1) % 1;
            double x = cell.x + Math.cos(a) * r + Math.sin(time + i) * 1.5;
            double z = cell.z + Math.sin(a) * r, y = cell.y + 3 + progress * 42;
            double size = 0.07 + (i % 3) * 0.035;
            if (cell.type == CastingCircleVFX.SuperTierType.FIRESTORM) {
                diamond(t, x, y, z, size, size * 3, 1, .25f, .006f, s * .7f);
            } else diamond(t, x, y, z, size, size * (cell.type == CastingCircleVFX.SuperTierType.BLIZZARD ? 1 : 2),
                    color[0], color[1], color[2], s * 0.65f);
        }
    }

    private static void drawStormDetails(Tessellator t,SkyCell cell,double time,double deck,float s,float[] color){
        stormFunnelLight(t,cell.x,cell.y+20.035,cell.y+48,cell.z,time,s,1);
    }



    private static final float[] ICE_EDGE = {0.35f, 0.85f, 1.0f};
    private static final float[] ICE_CRACK = {0.8f, 0.96f, 1.0f};
    private static final float[] ASTRAL_EDGE = {0.65f, 0.24f, 1.0f};
    private static final float[] ASTRAL_CORE = {0.18f, 0.86f, 1.0f};

    private static boolean usesStormClouds(CastingCircleVFX.SuperTierType type) {
        return type == CastingCircleVFX.SuperTierType.STORM_LIGHTNING;
    }

    private static void drawSkySurfaces(Tessellator t, float partial, double cx, double cy, double cz, int particles) {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        t.startDrawing(GL11.GL_TRIANGLES);
        for (SkyCell cell : visibleSky) {
            if (distanceSq(cell.x, cell.y + 38, cell.z, cx, cy, cz) > 160 * 160) continue;
            float s = cell.previousStrength + (cell.strength - cell.previousStrength) * partial;
            double time = (clientTick + partial) * 0.05;
            if (cell.type == CastingCircleVFX.SuperTierType.BLIZZARD) drawIceSky(t, cell, time, s, true);
            else if (cell.type == CastingCircleVFX.SuperTierType.STARFALL) drawAstralSky(t, cell, time, s, true, particles);
            else if (cell.type == CastingCircleVFX.SuperTierType.LUNAR_LIGHTNING) drawLunarSky(t, cell, time, s, true);
        }
        t.draw();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    }

    private static void drawIceSky(Tessellator t, SkyCell cell, double time, float s, boolean surface) {
        double growth = Math.min(1, cell.chargeTicks / 40.0);
        double cracking = clamp01((cell.chargeTicks - 28) / 52.0);
        int id = 0;
        for (int row = -2; row <= 2; row++) for (int col = -2; col <= 2; col++, id++) {
            if ((row + col + 10) % 3 == 0) continue;
            double cycle = fraction(time * .16 + id * .137 + Math.floorMod(cell.owner, 23) * .017);
            double breaking = cell.chargeTicks >= 80 ? clamp01((cycle - .68) / .32) : 0;
            double scale = growth * (breaking > 0 ? 1 : Math.min(1, .25 + cycle * 6));
            double x = cell.x + col * 27 + Math.sin(id * 1.3) * 4 + Math.sin(time * .28 + id) * .8;
            double z = cell.z + row * 27 + Math.cos(id) * 5;
            double y = cell.y + 48 + Math.sin(id * 2) * 3;
            double turn = id * .71 + Math.sin(time * .15 + id) * .035;
            for (int edge = 0; edge < 6; edge++) {
                double a = turn + edge * Math.PI / 3, b = a + Math.PI / 3;
                double ra = (12 + Math.sin(id + edge * 2) * 2) * scale;
                double rb = (12 + Math.sin(id + (edge + 1) * 2) * 2) * scale;
                double ax = x + Math.cos(a) * ra, az = z + Math.sin(a) * ra;
                double bx = x + Math.cos(b) * rb, bz = z + Math.sin(b) * rb;
                if (breaking <= 0) {
                    if (surface) {
                        float shade = .75f + edge * .04f;
                        triangle(t, x, y + .7, z, ax, y, az, bx, y, bz,
                                .075f * shade, .27f * shade, .40f * shade, s * .38f);
                        triangle(t, x, y + .65, z, ax, y - .7, az, bx, y - .7, bz,
                                .15f, .40f, .55f, s * .12f);
                    } else {
                        ribbon(t, ax, y - .1, az, bx, y - .1, bz, .16, ICE_EDGE, s * .65f);
                        // Branches advance outwards as the second circle forms.
                        double bend = (a + b) * .5;
                        double mx = x + Math.cos(bend) * ra * .46;
                        double mz = z + Math.sin(bend) * ra * .46;
                        growingRibbon(t, x, y + .5, z, mx, y + .2, mz, .12,
                                ICE_CRACK, s * .80f, cracking * 2);
                        growingRibbon(t, mx, y + .2, mz, ax, y - .12, az, .095,
                                ICE_CRACK, s * .72f, cracking * 2 - 1);
                        growingRibbon(t, mx, y + .2, mz, bx, y - .12, bz, .065,
                                ICE_EDGE, s * .60f, cracking * 2 - 1.25);
                    }
                } else {
                    // A plate separates into six solid facets, then falls and fades.
                    double drift = breaking * 7, drop = breaking * breaking * 34;
                    double dx = Math.cos((a + b) * .5) * drift;
                    double dz = Math.sin((a + b) * .5) * drift;
                    double centerY = y - drop;
                    float alpha = s * (float)(1 - breaking);
                    if (surface) {
                        triangle(t, x + dx, centerY + breaking * 2, z + dz,
                                ax + dx, centerY - breaking * 3, az + dz,
                                bx + dx, centerY, bz + dz, .10f, .40f, .62f, alpha * .5f);
                    } else {
                        ribbon(t, ax + dx, centerY - breaking * 3, az + dz,
                                bx + dx, centerY, bz + dz, .15, ICE_CRACK, alpha * .8f);
                        if (edge % 2 == 0) iceLance(t, x + dx * 1.6, centerY - 2,
                                z + dz * 1.6, .65, 2.8, turn + breaking * 3, alpha);
                    }
                }
            }
        }
        if (surface) return;
        // Snow fronts sweep around the caster beneath the frozen canopy.
        for (int band = 0; band < 3; band++) for (int i = 0; i < 24; i++) {
            double a = time * .36 + band * 2.1 + i * .075, b = a + .065;
            double r = 22 + band * 13, y = cell.y + 4 + band * 5 + Math.sin(a * 3) * 2;
            ribbon(t, cell.x + Math.cos(a) * r, y, cell.z + Math.sin(a) * r,
                    cell.x + Math.cos(b) * r, y + .18, cell.z + Math.sin(b) * r,
                    .055 + (i % 3) * .025, ICE_CRACK, s * .32f);
            if (i % 4 == 0) diamond(t, cell.x + Math.cos(a) * r, y + .3,
                    cell.z + Math.sin(a) * r, .12, .12, .70f, .92f, 1, s * .7f);
        }
    }

    private static final float[] ASTRAL_VOID = {.004f, .006f, .025f};
    private static final double[] CONSTELLATION_U = {.08, .25, .43, .61, .79, .94};
    private static final double[] CONSTELLATION_V = {-.25, .52, -.42, .38, -.55, .12};

    public static double getStarfallPortalProgress(double chargeTicks) {
        if (chargeTicks <= 0) return 0.04;
        // Estágio 1: 0 a 16 ticks (0s a 0.8s) - Ponto focal estelar abrindo junto com o astrolábio do solo e pilar
        if (chargeTicks < 16.0) {
            double p = chargeTicks / 16.0;
            double smooth = p * p * (3.0 - 2.0 * p);
            return 0.04 + 0.12 * smooth; // 4% a 16% (de ~2m até ~7.5m de raio)
        }
        // Estágio 2: 16 a 40 ticks (0.8s a 2.0s) - Fenda cósmica abrindo junto com os anéis corporais e satélites
        if (chargeTicks < 40.0) {
            double p = (chargeTicks - 16.0) / 24.0;
            double smooth = p * p * (3.0 - 2.0 * p);
            return 0.16 + 0.22 * smooth; // 16% a 38% (de ~7.5m até ~18m de raio)
        }
        // Estágio 3: 40 a 80 ticks (2.0s a 4.0s) - Expansão titânica acelerada acompanhando a Fase 2 (50 blocos)
        if (chargeTicks < 80.0) {
            double p = (chargeTicks - 40.0) / 40.0;
            double smooth = p * p * (3.0 - 2.0 * p);
            return 0.38 + 0.38 * smooth; // 38% a 76% (de ~18m até ~36.5m de raio)
        }
        // Estágio 4: 80 a 100 ticks (4.0s a 5.0s) - Cataclismo total do horizonte cósmico de 96m (Fase 3)
        double p = Math.min(1.0, (chargeTicks - 80.0) / 20.0);
        double smooth = p * p * (3.0 - 2.0 * p);
        return 0.76 + 0.24 * smooth; // 76% a 100% (48m de raio / 96m de diâmetro)
    }

    private static void drawAstralSky(Tessellator t, SkyCell cell, double time, float s, boolean surface, int particles) {
        double opening = getStarfallPortalProgress(cell.chargeTicks);
        double radius = 48.0 * opening;
        double y = cell.y + 76;
        int segments = particles == 0 ? 160 : 96;
        if (surface) {
            for (int band = 0; band < 6; band++) for (int i = 0; i < segments; i++) {
                double a = i * Math.PI * 2 / segments, b = (i + 1) * Math.PI * 2 / segments;
                double r0 = radius * band / 6, r1 = radius * (band + 1) / 6;
                portalVertex(t,cell.x+Math.cos(a)*r0,y,cell.z+Math.sin(a)*r0,band/6.0,s);
                portalVertex(t,cell.x+Math.cos(a)*r1,y,cell.z+Math.sin(a)*r1,(band+1)/6.0,s);
                portalVertex(t,cell.x+Math.cos(b)*r1,y,cell.z+Math.sin(b)*r1,(band+1)/6.0,s);
                portalVertex(t,cell.x+Math.cos(a)*r0,y,cell.z+Math.sin(a)*r0,band/6.0,s);
                portalVertex(t,cell.x+Math.cos(b)*r1,y,cell.z+Math.sin(b)*r1,(band+1)/6.0,s);
                portalVertex(t,cell.x+Math.cos(b)*r0,y,cell.z+Math.sin(b)*r0,band/6.0,s);
            }
            return;
        }
        // 1. Triple Rim Trail (Hot White Core + Starlight Blue + Astral Violet Glow)
        for (int i = 0; i < segments; i++) {
            double a = i * Math.PI * 2 / segments, b = (i + 1) * Math.PI * 2 / segments;
            double ax = cell.x + Math.cos(a) * radius, az = cell.z + Math.sin(a) * radius;
            double bx = cell.x + Math.cos(b) * radius, bz = cell.z + Math.sin(b) * radius;
            float pulse = s * (.78f + .12f * (float)Math.sin(a * 3 - time * .7));
            lightTrail(t, ax, y - .10, az, bx, y - .10, bz, 3.4, ANIME_VIOLET, pulse * .14f);
            lightTrail(t, ax, y - .13, az, bx, y - .13, bz, .95, ANIME_BLUE, pulse * .55f);
            lightTrail(t, ax, y - .16, az, bx, y - .16, bz, .14, ANIME_WHITE, pulse * 1.0f);
        }

        // 2. Anel Externo Segmentado / Glifos Orbitais (Outer Orbital Gyro Ring)
        double outerR = radius * 1.06;
        int outerSegs = 36;
        for (int i = 0; i < outerSegs; i++) {
            if (i % 3 == 0) continue; // Stylized breaks create orbital dash runes
            double a = i * Math.PI * 2 / outerSegs + time * 0.04;
            double b = (i + 0.7) * Math.PI * 2 / outerSegs + time * 0.04;
            double ax = cell.x + Math.cos(a) * outerR, az = cell.z + Math.sin(a) * outerR;
            double bx = cell.x + Math.cos(b) * outerR, bz = cell.z + Math.sin(b) * outerR;
            lightTrail(t, ax, y - .12, az, bx, y - .12, bz, .32, ANIME_BLUE, s * 0.70f);
        }

        // 3. Anel Interno de Horizonte de Eventos com Contragiro (Counter-Rotating Inner Event Horizon)
        double innerR = radius * 0.94;
        int innerSegs = 24;
        for (int i = 0; i < innerSegs; i++) {
            if (i % 2 == 0) continue;
            double a = i * Math.PI * 2 / innerSegs - time * 0.06;
            double b = (i + 0.6) * Math.PI * 2 / innerSegs - time * 0.06;
            double ax = cell.x + Math.cos(a) * innerR, az = cell.z + Math.sin(a) * innerR;
            double bx = cell.x + Math.cos(b) * innerR, bz = cell.z + Math.sin(b) * innerR;
            lightTrail(t, ax, y - .14, az, bx, y - .14, bz, .22, ANIME_VIOLET, s * 0.80f);
        }

        // 4. Espículas Radiais Cósmicas (Corona Solar Flares)
        int flares = 20;
        for (int i = 0; i < flares; i++) {
            double angle = i * Math.PI * 2 / flares + time * 0.018;
            double flareLen = (3.5 + 2.5 * Math.sin(time * 2.2 + i * 1.6)) * clamp01(cell.chargeTicks / 80.0);
            double inR = radius * 0.98;
            double outR = radius + flareLen;
            double ax = cell.x + Math.cos(angle) * inR, az = cell.z + Math.sin(angle) * inR;
            double bx = cell.x + Math.cos(angle) * outR, bz = cell.z + Math.sin(angle) * outR;
            float flareAlpha = s * (float)(0.22 + 0.14 * Math.sin(time * 3.0 + i));
            lightTrail(t, ax, y - .11, az, bx, y - .11, bz, .42, ANIME_BLUE, flareAlpha * 0.5f);
            lightTrail(t, ax, y - .13, az, bx, y - .13, bz, .12, ANIME_WHITE, flareAlpha);
        }

        // 5. Nebulosa Espiral Intercalada (3 Braços Violeta + 3 Braços Ciano)
        for (int arm = 0; arm < 6; arm++) {
            float[] armColor = arm % 2 == 0 ? ANIME_VIOLET : ANIME_BLUE;
            for (int i = 0; i < 60; i++) {
                double f = i / 60.0, g = (i + 1) / 60.0;
                double a = arm * Math.PI / 3 + f * 4.5 + time * .035;
                double b = arm * Math.PI / 3 + g * 4.5 + time * .035;
                double r0 = (.06 + f * .82) * radius, r1 = (.06 + g * .82) * radius;
                lightTrail(t, cell.x + Math.cos(a) * r0, y - .20, cell.z + Math.sin(a) * r0,
                        cell.x + Math.cos(b) * r1, y - .20, cell.z + Math.sin(b) * r1, 2.6, armColor, s * .045f);
                if (i % 7 == 0) {
                    lightTrail(t, cell.x + Math.cos(a) * r0, y - .22, cell.z + Math.sin(a) * r0,
                            cell.x + Math.cos(b) * r1, y - .22, cell.z + Math.sin(b) * r1, .14, ANIME_WHITE, s * .25f);
                }
            }
        }

        // 6. Singularidade Central Estelar (Onde conecta o pilar celeste de 76 blocos do jogador)
        glowEllipse(t, cell.x, y - .25, cell.z, 3.8, 3.8, ANIME_BLUE, s * .35f);
        glowEllipse(t, cell.x, y - .28, cell.z, 1.3, 1.3, ANIME_WHITE, s * .85f);
        glowEllipse(t, cell.x, y - .30, cell.z, 7.5, .15, ANIME_WHITE, s * .75f);
        glowEllipse(t, cell.x, y - .30, cell.z, .15, 7.5, ANIME_WHITE, s * .75f);

        // 7. Campo de Estrelas Cintilantes e Constelação
        int stars = particles == 0 ? 70 : 35;
        for (int i = 0; i < stars; i++) {
            double a = i * 2.399963 + time * .006, r = Math.sqrt(fraction(i * .618033 + .2)) * radius * .94;
            float twinkle = s * (.40f + .35f * (float)Math.sin(time * .8 + i * 2.1));
            astralStar(t, cell.x + Math.cos(a) * r, y - .35, cell.z + Math.sin(a) * r, .10 + (i % 4) * .05, twinkle);
        }
        drawConstellation(t, cell, 0, time, s * .7f, cell.x, y, cell.z, 1, 0, radius * .82, radius * .48);
    }

    private static void portalVertex(Tessellator t,double x,double y,double z,double radial,float alpha){
        t.setColorRGBA_F(.004f+(float)radial*.010f,.005f+(float)radial*.005f,.018f+(float)radial*.028f,alpha*.98f);
        t.addVertex(x,y,z);
    }



    private static void drawConstellation(Tessellator t, SkyCell cell, int rift, double time,
            float s, double ox, double y, double oz, double c, double sn, double length, double width) {
        double cycle=fraction(time*.12),connect=clamp01((cell.chargeTicks-25)/45.0);
        double px=0,py=0,pz=0;
        for(int node=0;node<CONSTELLATION_U.length;node++){
            double f=CONSTELLATION_U[node],u=(f*2-1)*length;
            double v=Math.sin(f*Math.PI*2)*2.1+CONSTELLATION_V[node]*width*.75;
            double drop=cell.chargeTicks>=80?clamp01((cycle-.66)*4-node*.10):0;
            double x=ox+c*u-sn*v+c*drop*drop*5,z=oz+sn*u+c*v+sn*drop*drop*5;
            double sy=y-.45-drop*drop*44;
            float alpha=s*(float)(1-drop);
            // Connections dissolve before the stars detach, avoiding stretched polygon webs.
            if(node>0&&cycle<.66&&connect*6>node)
                lightTrail(t,px,py,pz,x,sy,z,.045,ANIME_BLUE,s*.28f);
            if(drop>0)animeComet(t,x,sy,z,Math.atan2(sn,c),.65,alpha);
            else astralStar(t,x,sy,z,.42,alpha);
            px=x;py=sy;pz=z;
        }
    }


    private static void astralStar(Tessellator t, double x, double y, double z, double size, float alpha) {
        if (alpha <= .005f) return;
        // Fast camera-facing billboard star (18 vertices instead of 216 vertices!)
        double r1 = size * 1.6;
        double rx1 = viewRX * r1, rz1 = viewRZ * r1, uy1 = viewUY * r1;
        triangle(t, x - rx1, y, z - rz1, x, y + uy1, z, x + rx1, y, z + rz1, ANIME_BLUE[0], ANIME_BLUE[1], ANIME_BLUE[2], alpha * 0.25f);
        triangle(t, x - rx1, y, z - rz1, x + rx1, y, z + rz1, x, y - uy1, z, ANIME_BLUE[0], ANIME_BLUE[1], ANIME_BLUE[2], alpha * 0.25f);

        double r2 = size * 0.45;
        double rx2 = viewRX * r2, rz2 = viewRZ * r2, uy2 = viewUY * r2;
        triangle(t, x - rx2, y, z - rz2, x, y + uy2, z, x + rx2, y, z + rz2, ANIME_WHITE[0], ANIME_WHITE[1], ANIME_WHITE[2], alpha * 0.95f);
        triangle(t, x - rx2, y, z - rz2, x + rx2, y, z + rz2, x, y - uy2, z, ANIME_WHITE[0], ANIME_WHITE[1], ANIME_WHITE[2], alpha * 0.95f);

        double r3w = size * 2.2, r3h = size * 0.12;
        double rx3 = viewRX * r3w, rz3 = viewRZ * r3w, uy3 = viewUY * r3h;
        triangle(t, x - rx3, y, z - rz3, x, y + uy3, z, x + rx3, y, z + rz3, ANIME_WHITE[0], ANIME_WHITE[1], ANIME_WHITE[2], alpha * 0.70f);
        triangle(t, x - rx3, y, z - rz3, x + rx3, y, z + rz3, x, y - uy3, z, ANIME_WHITE[0], ANIME_WHITE[1], ANIME_WHITE[2], alpha * 0.70f);
    }


    private static void iceLance(Tessellator t, double x, double y, double z, double radius,
            double length, double turn, float alpha) {
        for (int face = 0; face < 5; face++) {
            double a = turn + face * Math.PI * 2 / 5, b = a + Math.PI * 2 / 5;
            double ax = x + Math.cos(a) * radius, az = z + Math.sin(a) * radius;
            double bx = x + Math.cos(b) * radius, bz = z + Math.sin(b) * radius;
            float light = .55f + face * .08f;
            triangle(t, x, y - length, z, ax, y, az, bx, y, bz,
                    .22f * light, .73f * light, light, alpha);
            triangle(t, x, y + length * .3, z, bx, y, bz, ax, y, az,
                    .50f * light, .86f * light, light, alpha * .8f);
        }
        diamond(t, x, y - length * .25, z, radius * .18, length * .60,
                .72f, .96f, 1, alpha * .9f);
    }

    private static void growingRibbon(Tessellator t, double ax, double ay, double az,
            double bx, double by, double bz, double width, float[] color, float alpha, double growth) {
        growth = clamp01(growth);
        if (growth <= 0 || alpha <= 0) return;
        ribbon(t, ax, ay, az, ax + (bx - ax) * growth, ay + (by - ay) * growth,
                az + (bz - az) * growth, width, color, alpha);
    }

    private static double clamp01(double value) { return Math.max(0, Math.min(1, value)); }
    private static double fraction(double value) { return value - Math.floor(value); }


    private static final float[] LUNAR_VOID = {0.008f, 0.012f, 0.035f};
    private static void drawLunarSky(Tessellator t, SkyCell cell, double time, float s, boolean surface) {
        double y = cell.y + 50, radius = 12;
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI / 32, b = (i + 1) * Math.PI / 32;
            if (surface) triangle(t, cell.x, y, cell.z, cell.x + Math.cos(a) * radius, y, cell.z + Math.sin(a) * radius,
                    cell.x + Math.cos(b) * radius, y, cell.z + Math.sin(b) * radius, .008f, .012f, .035f, s * .96f);
            else {
                double innerA = radius - 3.8 * (.5 + .5 * Math.cos(a - time * .035));
                double innerB = radius - 3.8 * (.5 + .5 * Math.cos(b - time * .035));
                triangle(t, cell.x + Math.cos(a) * innerA, y - .1, cell.z + Math.sin(a) * innerA,
                        cell.x + Math.cos(a) * radius, y - .1, cell.z + Math.sin(a) * radius,
                        cell.x + Math.cos(b) * radius, y - .1, cell.z + Math.sin(b) * radius, .68f, .85f, 1, s * .85f);
                triangle(t, cell.x + Math.cos(a) * innerA, y - .1, cell.z + Math.sin(a) * innerA,
                        cell.x + Math.cos(b) * radius, y - .1, cell.z + Math.sin(b) * radius,
                        cell.x + Math.cos(b) * innerB, y - .1, cell.z + Math.sin(b) * innerB, .68f, .85f, 1, s * .85f);
            }
        }
        if (surface) return;
        for (int fragment = 0; fragment < 6; fragment++) for (int i = 0; i < 10; i++) {
            double a = fragment * Math.PI / 3 + i * .07 + time * .025, b = a + .07;
            double r = 27 + fragment % 3 * 13, height = y + Math.sin(a * 2) * 5;
            ribbon(t, cell.x + Math.cos(a) * r, height, cell.z + Math.sin(a) * r,
                    cell.x + Math.cos(b) * r, height, cell.z + Math.sin(b) * r, .35, ICE_CRACK, s * .6f);
        }
    }

    private static void ribbon(Tessellator t, double ax, double ay, double az, double bx, double by, double bz,
            double width, float[] color, float alpha) {
        double dx = bx - ax, dz = bz - az, length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.0001) return;
        double nx = -dz * width / length, nz = dx * width / length;
        triangle(t, ax - nx, ay, az - nz, ax + nx, ay, az + nz, bx + nx, by, bz + nz, color[0], color[1], color[2], alpha);
        triangle(t, ax - nx, ay, az - nz, bx + nx, by, bz + nz, bx - nx, by, bz - nz, color[0], color[1], color[2], alpha);
    }

    private static void curtain(Tessellator t, double ax, double ay, double az, double bx, double by, double bz,
            double height, float[] color, float alpha) {
        triangle(t, ax, ay, az, bx, by, bz, bx, by + height, bz, color[0], color[1], color[2], alpha);
        triangle(t, ax, ay, az, bx, by + height, bz, ax, ay + height, az, color[0], color[1], color[2], alpha);
    }

    private static double distanceSq(double x, double y, double z, double cx, double cy, double cz) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void drawFalling(Tessellator t, Falling f, double curX, double curY, double curZ, float partial) {
        if (f.type == CastingCircleVFX.SuperTierType.FIRESTORM) {
            diamond(t, curX, curY, curZ, 0.62, 0.85, 1, 0.22f, 0.015f, 0.95f);
            diamond(t, curX, curY + 1.8, curZ, 0.38, 2.8, 1, 0.65f, 0.06f, 0.6f);
            diamond(t, curX, curY, curZ, 0.3, 0.5, 1, 0.95f, 0.5f, 1);
        } else if (f.type == CastingCircleVFX.SuperTierType.BLIZZARD) {
            double sway = Math.sin((f.age + f.rotation) * .15) * .25;
            iceLance(t, curX + sway, curY, curZ, .48, 2.4, f.rotation + f.age * .035, .90f);
            diamond(t, curX - .8, curY + .8, curZ + .5, .13, .13, .7f, .9f, 1, .8f);
        } else {
            drawAnimeComet3D(t, f, curX, curY, curZ, partial);
        }
    }

    private static void drawImpact(Tessellator t, Impact hit, float partial) {
        float age = hit.age + partial, alpha = Math.max(0, 1 - age / 25);
        if (hit.type == CastingCircleVFX.SuperTierType.STARFALL) {
            float burstAlpha = Math.max(0, 1.0f - age / 18.0f);

            // 1. Coluna/Espinho de Luz Vertical de Impacto (Vertical Starlight Spike)
            if (age < 14) {
                float spikeH = (float)((14 - age) / 14.0 * 3.8);
                lightTrail(t, hit.x, hit.y + 0.05, hit.z, hit.x, hit.y + spikeH, hit.z, 0.45, ANIME_BLUE, burstAlpha * 0.70f);
                lightTrail(t, hit.x, hit.y + 0.05, hit.z, hit.x, hit.y + spikeH, hit.z, 0.12, ANIME_WHITE, burstAlpha * 0.95f);
            }

            // 2. Onda de Choque Dupla no Solo (Double Ground Ripple)
            double rOuter = 0.5 + age * 0.28;
            double rInner = 0.3 + age * 0.18;
            for (int i = 0; i < 24; i++) {
                double cosA = GLOW_COS[i], sinA = GLOW_SIN[i];
                double cosB = GLOW_COS[i + 1], sinB = GLOW_SIN[i + 1];
                ribbon(t, hit.x + cosA * rOuter, hit.y + 0.08, hit.z + sinA * rOuter,
                        hit.x + cosB * rOuter, hit.y + 0.08, hit.z + sinB * rOuter, 0.055, ANIME_VIOLET, alpha * 0.85f);
                ribbon(t, hit.x + cosA * rInner, hit.y + 0.09, hit.z + sinA * rInner,
                        hit.x + cosB * rInner, hit.y + 0.09, hit.z + sinB * rInner, 0.035, ANIME_WHITE, burstAlpha * 0.90f);
            }

            // 3. Cruz Estelar de Impacto no Solo (Ground Star Lens Flare)
            float crossR = (float)(1.6 + age * 0.12);
            glowEllipse(t, hit.x, hit.y + 0.12, hit.z, crossR, 0.10, ANIME_WHITE, burstAlpha * 0.80f);
            glowEllipse(t, hit.x, hit.y + 0.12, hit.z, 0.10, crossR, ANIME_WHITE, burstAlpha * 0.80f);
            glowEllipse(t, hit.x, hit.y + 0.10, hit.z, 1.8 + age * 0.15, 1.8 + age * 0.15, ANIME_VIOLET, alpha * 0.25f);

            // 4. Centelhas Estelares em Dispersão (Dispersing Astral Sparks)
            double sparkR = 0.4 + age * 0.08;
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + age * 0.05;
                double sx = hit.x + Math.cos(a) * sparkR, sy = hit.y + 0.15 + age * 0.04, sz = hit.z + Math.sin(a) * sparkR;
                double spW = 0.14 * (1.0 - age / 25.0);
                double rx = viewRX * spW, rz = viewRZ * spW, uy = viewUY * spW;
                triangle(t, sx - rx, sy, sz - rz, sx, sy + uy, sz, sx + rx, sy, sz + rz, ANIME_WHITE[0], ANIME_WHITE[1], ANIME_WHITE[2], burstAlpha * 0.8f);
                triangle(t, sx - rx, sy, sz - rz, sx + rx, sy, sz + rz, sx, sy - uy, sz, ANIME_WHITE[0], ANIME_WHITE[1], ANIME_WHITE[2], burstAlpha * 0.8f);
            }
            return;
        } else if (hit.type == CastingCircleVFX.SuperTierType.STORM_LIGHTNING) {
            glowEllipse(t,hit.x,hit.y+.20,hit.z,2+age*.14,.35+alpha*.5,ANIME_BLUE,alpha*.2f);
            double r = .5 + age * .24;
            for(int i=0;i<24;i++){
                double cosA = GLOW_COS[i], sinA = GLOW_SIN[i];
                double cosB = GLOW_COS[i + 1], sinB = GLOW_SIN[i + 1];
                ribbon(t,hit.x+cosA*r,hit.y+.09,hit.z+sinA*r,
                        hit.x+cosB*r,hit.y+.09,hit.z+sinB*r,.045,ANIME_BLUE,alpha*.8f);
            }
            double sparkR = .3 + age * .065;
            for(int i=0;i<6;i++){
                double a=i*Math.PI/3+age*.03;
                double sx = hit.x + Math.cos(a)*sparkR, sy = hit.y + .15 + age*.03, sz = hit.z + Math.sin(a)*sparkR;
                double spW = 0.15 * (1.0 - age / 25.0);
                double rx = viewRX * spW, rz = viewRZ * spW, uy = viewUY * spW;
                triangle(t, sx - rx, sy, sz - rz, sx, sy + uy, sz, sx + rx, sy, sz + rz, ANIME_WHITE[0], ANIME_WHITE[1], ANIME_WHITE[2], alpha * 0.75f);
                triangle(t, sx - rx, sy, sz - rz, sx + rx, sy, sz + rz, sx, sy - uy, sz, ANIME_WHITE[0], ANIME_WHITE[1], ANIME_WHITE[2], alpha * 0.75f);
            }
            return;
        }
        float[] c = hit.type.primaryColor;
        double radius = 0.6 + age * 0.26;
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI / 16, b = (i + 1) * Math.PI / 16;
            double inner = Math.max(0, radius - 0.14);
            triangle(t, hit.x + Math.cos(a) * inner, hit.y + 0.08, hit.z + Math.sin(a) * inner,
                    hit.x + Math.cos(a) * radius, hit.y + 0.08, hit.z + Math.sin(a) * radius,
                    hit.x + Math.cos(b) * radius, hit.y + 0.08, hit.z + Math.sin(b) * radius, c[0], c[1], c[2], alpha);
            triangle(t, hit.x + Math.cos(a) * inner, hit.y + 0.08, hit.z + Math.sin(a) * inner,
                    hit.x + Math.cos(b) * radius, hit.y + 0.08, hit.z + Math.sin(b) * radius,
                    hit.x + Math.cos(b) * inner, hit.y + 0.08, hit.z + Math.sin(b) * inner, c[0], c[1], c[2], alpha);
        }
        if (hit.type == CastingCircleVFX.SuperTierType.BLIZZARD) {
            // Three expanding, translucent mist skirts linger close to the ground.
            for (int layer = 0; layer < 3; layer++) for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8, b = a + Math.PI / 8;
                double r = radius * (.65 + layer * .3);
                curtain(t, hit.x + Math.cos(a) * r, hit.y + .04 + layer * .13,
                        hit.z + Math.sin(a) * r, hit.x + Math.cos(b) * r,
                        hit.y + .04 + layer * .13, hit.z + Math.sin(b) * r,
                        .4 + alpha * .7, ICE_EDGE, alpha * .055f);
            }
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                diamond(t, hit.x + Math.cos(a) * radius * 0.5, hit.y + 0.2, hit.z + Math.sin(a) * radius * 0.5,
                        0.15, alpha * 1.8, 0.6f, 0.9f, 1, alpha);
            }
        } else if (hit.type == CastingCircleVFX.SuperTierType.STARFALL) {
            for (int spark = 0; spark < 8; spark++) {
                double a = spark * Math.PI / 4 + age * .035;
                astralStar(t, hit.x + Math.cos(a) * radius * .75,
                        hit.y + .15 + age * .045, hit.z + Math.sin(a) * radius * .75,
                        .13 + alpha * .12, alpha);
            }
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3, b = a + 2 * Math.PI / 3;
                triangle(t, hit.x, hit.y + 0.11, hit.z, hit.x + Math.cos(a) * radius, hit.y + 0.11, hit.z + Math.sin(a) * radius,
                        hit.x + Math.cos(b) * radius, hit.y + 0.11, hit.z + Math.sin(b) * radius, 0.7f, 0.3f, 1, alpha * 0.6f);
            }
        } else diamond(t, hit.x, hit.y + 0.4, hit.z, alpha * 0.6, alpha * 3, c[0], c[1], c[2], alpha);
    }

    private static void drawBolt(Tessellator t, Bolt bolt) {
        t.startDrawing(GL11.GL_LINE_STRIP);
        for (int i = 0; i < 9; i++) t.addVertex(bolt.x[i], bolt.y[i], bolt.z[i]);
        t.draw();
    }

    private static void diamond(Tessellator t, double x, double y, double z, double radius, double height,
            float red, float green, float blue, float alpha) {
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2, b = (i + 1) * Math.PI / 2;
            double ax = x + Math.cos(a) * radius, az = z + Math.sin(a) * radius;
            double bx = x + Math.cos(b) * radius, bz = z + Math.sin(b) * radius;
            triangle(t, x, y + height, z, ax, y, az, bx, y, bz, red, green, blue, alpha);
            triangle(t, x, y - height * 0.5, z, bx, y, bz, ax, y, az, red * 0.6f, green * 0.7f, blue * 0.9f, alpha);
        }
    }

    private static void triangle(Tessellator t, double ax, double ay, double az, double bx, double by, double bz,
            double cx, double cy, double cz, float red, float green, float blue, float alpha) {
        t.setColorRGBA_F(red, green, blue, alpha);
        t.addVertex(ax, ay, az); t.addVertex(bx, by, bz); t.addVertex(cx, cy, cz);
    }
}
