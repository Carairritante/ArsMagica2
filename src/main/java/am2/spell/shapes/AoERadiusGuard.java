package am2.spell.shapes;

/** Keeps malformed spell modifiers from creating unbounded AoE searches. */
public final class AoERadiusGuard {
	private static final double MAX_RADIUS = 64.0;

	private AoERadiusGuard(){
	}

	public static double sanitize(double radius){
		if (Double.isNaN(radius) || Double.isInfinite(radius))
			return 0.0;
		return Math.max(0.0, Math.min(MAX_RADIUS, radius));
	}
}
