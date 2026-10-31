package am2.entities;

/** Limits a wave's travel range without changing its radius. */
public final class WaveTravelLimiter {
	private static final double MAX_TRAVEL_DISTANCE = 128.0D;

	private WaveTravelLimiter() {
	}

	public static float limitSpeed(float speedPerTick) {
		if (Float.isNaN(speedPerTick)) {
			return 0.0F;
		}

		if (Float.isInfinite(speedPerTick) || Math.abs(speedPerTick) > MAX_TRAVEL_DISTANCE) {
			return Math.copySign((float)MAX_TRAVEL_DISTANCE, speedPerTick);
		}

		return speedPerTick;
	}

	public static int limitDuration(int durationTicks, float speedPerTick) {
		if (durationTicks <= 0) {
			return durationTicks;
		}

		double speed = Math.abs((double)speedPerTick);
		if (speed == 0.0D || Double.isNaN(speed) || Double.isInfinite(speed)) {
			return durationTicks;
		}

		double maxTicks = Math.floor(MAX_TRAVEL_DISTANCE / speed);
		if (maxTicks >= Integer.MAX_VALUE) {
			return durationTicks;
		}

		return Math.min(durationTicks, Math.max(1, (int)maxTicks));
	}
}
