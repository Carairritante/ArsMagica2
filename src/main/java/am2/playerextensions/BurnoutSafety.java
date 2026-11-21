package am2.playerextensions;

/** Per-entity overload episode; no world/player references or global collections. */
public final class BurnoutSafety {
    public static final int SILENCE_TICKS = 600;
    private boolean armed = true;
    private long lastPenalty = Long.MIN_VALUE;

    public static float clamp(float requested, float maximum) {
        if (!(maximum > 0f) || !Float.isFinite(maximum)) return 0f;
        if (!(requested > 0f)) return 0f;
        return requested >= maximum ? Math.max(0f, maximum - 1f) : requested;
    }

    public void observe(float requested, float maximum) {
        if (Float.isFinite(requested) && maximum > 0f && requested <= maximum * .8f) armed = true;
    }

    public boolean overflow(float requested, float maximum, long now) {
        if (!(maximum > 0f) || !Float.isFinite(maximum) || !(requested >= maximum)) return false;
        boolean recovered = armed;
        armed = false;
        if (!recovered) return false;
        if (lastPenalty != Long.MIN_VALUE && now >= lastPenalty && now - lastPenalty < SILENCE_TICKS) return false;
        lastPenalty = now;
        return true;
    }
}
