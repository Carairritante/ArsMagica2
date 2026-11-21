package am2.client.vfx;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Immutable, bounded geometry baked from the approved Blockbench models. No GL or world state. */
final class MahouCastingMesh {
    static final int ELEMENTS = 896;
    static final int FLOATS_PER_ELEMENT = 24 * 5;
    final byte[] phases = new byte[ELEMENTS];
    final float[] starts = new float[ELEMENTS], ends = new float[ELEMENTS];
    final float[][] vertices = new float[ELEMENTS][];
    final float[] phaseEnds = new float[3];

    static MahouCastingMesh read(InputStream stream) throws IOException {
        MahouCastingMesh mesh = new MahouCastingMesh();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(stream))) {
            if (in.readInt() != 0x4d485631 || in.readInt() != 1 || in.readInt() != ELEMENTS)
                throw new IOException("Unsupported Mahou casting mesh");
            int[] counts = new int[3];
            for (int i = 0; i < ELEMENTS; i++) {
                int phase = in.readUnsignedByte();
                float start = in.readFloat(), end = in.readFloat();
                if (phase < 1 || phase > 3 || !finite(start) || !finite(end) || start < 0 || end <= start || end > 8)
                    throw new IOException("Invalid casting phase timing");
                mesh.phases[i] = (byte)phase;
                mesh.starts[i] = start; mesh.ends[i] = end;
                mesh.phaseEnds[phase - 1] = Math.max(mesh.phaseEnds[phase - 1], end);
                counts[phase - 1]++;
                float[] vertices = mesh.vertices[i] = new float[FLOATS_PER_ELEMENT];
                for (int j = 0; j < vertices.length; j++) {
                    float v = in.readFloat();
                    boolean uv = j % 5 >= 3;
                    if (!finite(v) || (uv ? v < 0 || v > 1 : Math.abs(v) > 20))
                        throw new IOException("Invalid casting vertex");
                    vertices[j] = v;
                }
            }
            if (counts[0] != 64 || counts[1] != 308 || counts[2] != 524 || in.read() != -1)
                throw new IOException("Incomplete casting mesh");
        }
        return mesh;
    }

    static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }

    static int maxPhase(boolean superTier) { return superTier ? 3 : 2; }

    static float reveal(float time, float start, float end) {
        float a = Math.max(0, Math.min(1, (time - start) / (end - start)));
        return a * a * (3 - 2 * a);
    }
}
