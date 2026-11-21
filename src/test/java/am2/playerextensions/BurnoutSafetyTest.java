package am2.playerextensions;

import org.junit.Test;
import static org.junit.Assert.*;

public class BurnoutSafetyTest {
    @Test public void repeatedZoneCostsCannotRenewPenalty() {
        BurnoutSafety policy = new BurnoutSafety();
        assertTrue(policy.overflow(5000, 991, 10));
        for (int tick = 11; tick < 2000; tick++) {
            policy.observe(5000, 991);
            assertFalse(policy.overflow(5000, 991, tick));
        }
    }
    @Test public void needsRecoveryAndFullCooldownToStartNewEpisode() {
        BurnoutSafety policy = new BurnoutSafety();
        assertTrue(policy.overflow(991, 991, 100));
        policy.observe(790, 991);
        assertFalse(policy.overflow(991, 991, 699));
        assertFalse(policy.overflow(991, 991, 700));
        policy.observe(790, 991);
        assertTrue(policy.overflow(991, 991, 700));
    }
    @Test public void boundedPersistentDataNeverBecomesInvalid() {
        assertEquals(990, BurnoutSafety.clamp(Float.POSITIVE_INFINITY, 991), 0);
        assertEquals(0, BurnoutSafety.clamp(Float.NaN, 991), 0);
        assertEquals(0, BurnoutSafety.clamp(-20, 991), 0);
        assertEquals(0, BurnoutSafety.clamp(10, Float.NaN), 0);
        assertEquals(0, BurnoutSafety.clamp(1, 1), 0);
        assertEquals(5, BurnoutSafety.clamp(5, 991), 0);
    }
    @Test public void differentPlayersHaveIndependentEpisodes() {
        BurnoutSafety a = new BurnoutSafety(), b = new BurnoutSafety();
        assertTrue(a.overflow(5000, 991, 0));
        assertTrue(b.overflow(5000, 991, 0));
        assertFalse(a.overflow(5000, 991, 1000));
        assertFalse(b.overflow(5000, 991, 1000));
    }

    @Test public void fractionalDigCostsAccumulateUntilActualOverflow() {
        assertEquals(990.5f, BurnoutSafety.clamp(990.5f, 991), 0);
        BurnoutSafety safety = new BurnoutSafety();
        float fatigue = 990;
        boolean overflow = false;
        for (int i = 0; i < 20; i++) {
            float requested = fatigue + .15f;
            fatigue = BurnoutSafety.clamp(requested, 991);
            safety.observe(requested, 991);
            overflow |= safety.overflow(requested, 991, i);
        }
        assertTrue(overflow);
    }
}
