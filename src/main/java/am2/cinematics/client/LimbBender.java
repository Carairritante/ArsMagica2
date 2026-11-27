package am2.cinematics.client;

import cpw.mods.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Desenha um braço do modelo com cotovelo. Reaproveita as faces e o mapa de
 * textura das próprias caixas, então vale para skin comum, espelhada, fina,
 * mangas e para a casca das veias. Cada face é cortada na altura do cotovelo e o
 * anel da dobra fica no plano bissetor, sem abrir fresta.
 */
final class LimbBender {
    /** Altura do cotovelo em pixels, medida a partir do ombro. */
    private static final double ELBOW_Y = 4;
    private static final float PIXEL = 0.0625F;

    private final Field quads = ReflectionHelper.findField(ModelBox.class, "quadList", "field_78254_i");

    private static final class Vertex {
        final double x, y, z, u, v;

        Vertex(double x, double y, double z, double u, double v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
        }
    }

    /**
     * @param arm    caixa(s) do braço; as filhas (mangas) vão junto
     * @param angles ombro em radianos {x, y, z}
     * @param elbow  dobra do cotovelo em radianos (negativa = antebraço para a frente)
     * @param widen  escala em X ao redor do centro do braço (1 = original)
     * @param center centro do braço em X, em pixels
     */
    void render(ModelRenderer arm, float[] angles, double elbow, double widen, double center) {
        GL11.glPushMatrix();
        GL11.glTranslatef(arm.rotationPointX * PIXEL, arm.rotationPointY * PIXEL, arm.rotationPointZ * PIXEL);
        GL11.glRotated(Math.toDegrees(angles[2]), 0, 0, 1);
        GL11.glRotated(Math.toDegrees(angles[1]), 0, 1, 0);
        GL11.glRotated(Math.toDegrees(angles[0]), 1, 0, 0);
        boxes(arm, elbow, widen, center);
        if (arm.childModels != null) {
            for (Object child : arm.childModels) boxes((ModelRenderer) child, elbow, widen, center);
        }
        GL11.glPopMatrix();
    }

    private void boxes(ModelRenderer renderer, double elbow, double widen, double center) {
        if (!renderer.showModel) return;
        for (Object o : renderer.cubeList) {
            ModelBox box = (ModelBox) o;
            double axis = (box.posX1 + box.posX2) / 2.0;
            try {
                for (TexturedQuad quad : (TexturedQuad[]) quads.get(box)) draw(quad, elbow, axis, widen, center);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private void draw(TexturedQuad quad, double elbow, double axis, double widen, double center) {
        Vertex[] corners = new Vertex[quad.vertexPositions.length];
        double lowest = Double.MAX_VALUE, highest = -Double.MAX_VALUE;
        for (int i = 0; i < corners.length; i++) {
            PositionTextureVertex p = quad.vertexPositions[i];
            double x = center + (p.vector3D.xCoord - center) * widen;
            corners[i] = new Vertex(x, p.vector3D.yCoord, p.vector3D.zCoord, p.texturePositionX, p.texturePositionY);
            lowest = Math.min(lowest, p.vector3D.yCoord);
            highest = Math.max(highest, p.vector3D.yCoord);
        }
        double scaledAxis = center + (axis - center) * widen;
        if (lowest < ELBOW_Y && highest > ELBOW_Y) {
            // Face lateral que cruza o cotovelo: vira duas.
            emit(clip(corners, true), elbow, scaledAxis, false);
            emit(clip(corners, false), elbow, scaledAxis, true);
        } else {
            emit(corners, elbow, scaledAxis, lowest >= ELBOW_Y);
        }
    }

    /** Parte do polígono acima (upper) ou abaixo do corte, com a textura interpolada. */
    private static Vertex[] clip(Vertex[] polygon, boolean upper) {
        List<Vertex> result = new ArrayList<Vertex>(6);
        for (int i = 0; i < polygon.length; i++) {
            Vertex a = polygon[i], b = polygon[(i + 1) % polygon.length];
            boolean keepA = upper ? a.y <= ELBOW_Y : a.y >= ELBOW_Y;
            boolean keepB = upper ? b.y <= ELBOW_Y : b.y >= ELBOW_Y;
            if (keepA) result.add(a);
            if (keepA != keepB) {
                double u = (ELBOW_Y - a.y) / (b.y - a.y);
                result.add(new Vertex(a.x + (b.x - a.x) * u, ELBOW_Y, a.z + (b.z - a.z) * u,
                        a.u + (b.u - a.u) * u, a.v + (b.v - a.v) * u));
            }
        }
        return result.toArray(new Vertex[result.size()]);
    }

    private static double[] bend(Vertex p, double elbow, boolean lower) {
        if (!lower || p.y < ELBOW_Y - 1e-4) return new double[] { p.x, p.y, p.z };
        boolean joint = p.y < ELBOW_Y + 1e-4;
        // No anel da dobra, meio ângulo e Z alongado: os dois segmentos se encontram.
        double angle = joint ? elbow / 2 : elbow;
        double dy = joint ? 0 : p.y - ELBOW_Y;
        double dz = joint ? p.z / Math.cos(elbow / 2) : p.z;
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new double[] { p.x, ELBOW_Y + dy * cos - dz * sin, dy * sin + dz * cos };
    }

    private void emit(Vertex[] polygon, double elbow, double axis, boolean lower) {
        if (polygon.length < 3) return;
        double[][] p = new double[polygon.length][];
        double[] middle = new double[3];
        double sourceY = 0;
        for (int i = 0; i < polygon.length; i++) {
            // O anel superior do segmento de cima também usa o plano bissetor.
            boolean useLower = lower || Math.abs(polygon[i].y - ELBOW_Y) < 1e-4;
            p[i] = bend(polygon[i], elbow, useLower);
            for (int axisIndex = 0; axisIndex < 3; axisIndex++) middle[axisIndex] += p[i][axisIndex] / polygon.length;
            sourceY += polygon[i].y / polygon.length;
        }
        // Normal para a iluminação, sempre apontando para fora do braço.
        double ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
        double bx = p[2][0] - p[0][0], by = p[2][1] - p[0][1], bz = p[2][2] - p[0][2];
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-6) return;
        double[] core = bend(new Vertex(axis, lower ? Math.max(sourceY, ELBOW_Y + 0.01) : sourceY, 0, 0, 0), elbow, lower);
        // Tampas (ombro e mão) apontam ao longo do eixo; para elas o centro fica deslocado.
        double ox = middle[0] - core[0], oy = middle[1] - core[1], oz = middle[2] - core[2];
        if (ox * ox + oy * oy + oz * oz < 0.04) {
            double[] inner = bend(new Vertex(axis, ELBOW_Y + (lower ? 1 : -1), 0, 0, 0), elbow, lower);
            ox = middle[0] - inner[0];
            oy = middle[1] - inner[1];
            oz = middle[2] - inner[2];
        }
        if (nx * ox + ny * oy + nz * oz < 0) length = -length;

        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_POLYGON);
        tess.setNormal((float) (nx / length), (float) (ny / length), (float) (nz / length));
        for (int i = 0; i < polygon.length; i++) {
            tess.addVertexWithUV(p[i][0] * PIXEL, p[i][1] * PIXEL, p[i][2] * PIXEL, polygon[i].u, polygon[i].v);
        }
        tess.draw();
    }
}
