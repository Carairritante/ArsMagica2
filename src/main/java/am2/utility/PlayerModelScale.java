package am2.utility;

/** Column-major placement matrix; right multiplication preserves the feet anchor. */
public final class PlayerModelScale {
    private PlayerModelScale() {}

    public static void apply(float[] matrix, float shrinkPct) {
        if (matrix == null || matrix.length < 16 || !(shrinkPct > 0f)) return;
        float scale = 1f - .75f * Math.min(1f, shrinkPct);
        for (int i = 0; i < 12; i++) matrix[i] *= scale;
    }
}
