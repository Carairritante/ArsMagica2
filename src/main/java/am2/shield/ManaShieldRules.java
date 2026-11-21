package am2.shield;

/** Pure server-side pricing rules; no rendering, entities or world references. */
public final class ManaShieldRules {
    private static final double[] RATES = {50.0, 37.5, 25.0, 17.5, 12.5};

    public enum Action {
        STATUS(.01, 25), CONTROL(.01, 25), THEFT(.02, 50), DISPEL(.05, 100), DIRECT_DEATH(.25, 250);
        final double fraction, minimum;
        Action(double fraction, double minimum) { this.fraction = fraction; this.minimum = minimum; }
    }

    public static final class Absorption {
        public final float absorbed, remaining, manaSpent;
        Absorption(float absorbed, float remaining, float manaSpent) {
            this.absorbed = absorbed; this.remaining = remaining; this.manaSpent = manaSpent;
        }
    }

    private ManaShieldRules() {}

    /** Potion amplifier zero corresponds to the displayed level I. */
    public static double manaPerDamage(int amplifier) {
        return RATES[Math.max(0, Math.min(4, amplifier))];
    }

    public static double actionCost(float maximumMana, Action action) {
        double capacity = Float.isNaN(maximumMana) || Float.isInfinite(maximumMana) ? 0 : Math.max(0, maximumMana);
        return Math.max(action.minimum, capacity * action.fraction);
    }

    /** A shielded life-for-mana trade cannot refund mana for health never paid. */
    public static float healthPaidFraction(float before, float after, float requestedDamage) {
        if (Float.isNaN(before) || Float.isNaN(after) || Float.isNaN(requestedDamage)
                || Float.isInfinite(before) || Float.isInfinite(after) || Float.isInfinite(requestedDamage)
                || requestedDamage <= 0) return 0;
        return Math.max(0, Math.min(1, (before - after) / requestedDamage));
    }

    public static Absorption absorb(float damage, float availableMana, int amplifier) {
        if (Float.isNaN(damage) || damage <= 0) return new Absorption(0, 0, 0);
        if (Float.isNaN(availableMana) || Float.isInfinite(availableMana) || availableMana <= 0)
            return new Absorption(0, damage, 0);
        double rate = manaPerDamage(amplifier);
        double absorbed = Math.min((double)damage, availableMana / rate);
        double spent = absorbed * rate;
        // ExtendedProperties stores mana as float: even tiny absorbed hits must
        // produce a representable debit instead of rounding back to the same mana.
        if (spent > 0) spent = Math.min(availableMana, Math.max(spent, Math.ulp(availableMana)));
        return new Absorption((float)absorbed, (float)Math.max(0, damage - absorbed), (float)spent);
    }
}
