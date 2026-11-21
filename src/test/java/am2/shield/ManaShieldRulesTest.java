package am2.shield;

import org.junit.Test;
import static org.junit.Assert.*;

public class ManaShieldRulesTest {
    @Test public void approvedFiveLevelsAndFractionalPayments() {
        assertEquals(50, ManaShieldRules.manaPerDamage(0), 0);
        assertEquals(37.5, ManaShieldRules.manaPerDamage(1), 0);
        assertEquals(25, ManaShieldRules.manaPerDamage(2), 0);
        assertEquals(17.5, ManaShieldRules.manaPerDamage(3), 0);
        assertEquals(12.5, ManaShieldRules.manaPerDamage(4), 0);
        assertEquals(12.5, ManaShieldRules.manaPerDamage(Integer.MAX_VALUE), 0);
        ManaShieldRules.Absorption result = ManaShieldRules.absorb(3, 1000, 1);
        assertEquals(112.5, result.manaSpent, .0001);
        assertEquals(0, result.remaining, 0);
    }

    @Test public void insufficientManaOnlyAbsorbsWhatWasPaid() {
        ManaShieldRules.Absorption result = ManaShieldRules.absorb(1, 5, 4);
        assertEquals(.4, result.absorbed, .0001);
        assertEquals(.6, result.remaining, .0001);
        assertEquals(5, result.manaSpent, 0);
    }

    @Test public void tinyHitsCannotBecomeFreeThroughFloatRounding() {
        ManaShieldRules.Absorption result = ManaShieldRules.absorb(.000001f, 50000, 4);
        assertTrue(result.manaSpent > 0);
        assertTrue(50000f - result.manaSpent < 50000f);
        assertEquals(0, result.remaining, 0);
    }

    @Test public void nonDamageAndInvalidManaNeverCreateCredits() {
        assertEquals(0, ManaShieldRules.absorb(-10, 100, 0).manaSpent, 0);
        assertEquals(0, ManaShieldRules.absorb(10, Float.NaN, 0).manaSpent, 0);
        assertEquals(10, ManaShieldRules.absorb(10, 0, 0).remaining, 0);
        ManaShieldRules.Absorption infiniteHit = ManaShieldRules.absorb(Float.POSITIVE_INFINITY, 100, 0);
        assertFalse(Float.isNaN(infiniteHit.remaining));
        assertEquals(100, infiniteHit.manaSpent, 0);
    }

    @Test public void specialActionsScaleWithCapacityAndKeepFixedMinimums() {
        assertEquals(25, ManaShieldRules.actionCost(100, ManaShieldRules.Action.STATUS), 0);
        assertEquals(500, ManaShieldRules.actionCost(50000, ManaShieldRules.Action.STATUS), .001);
        assertEquals(1000, ManaShieldRules.actionCost(50000, ManaShieldRules.Action.THEFT), .001);
        assertEquals(2500, ManaShieldRules.actionCost(50000, ManaShieldRules.Action.DISPEL), .001);
        assertEquals(12500, ManaShieldRules.actionCost(50000, ManaShieldRules.Action.DIRECT_DEATH), .001);
    }
    @Test public void shieldedLifeTradesRefundOnlyHealthActuallyPaid() {
        assertEquals(0, ManaShieldRules.healthPaidFraction(20, 20, 2), 0);
        assertEquals(1, ManaShieldRules.healthPaidFraction(20, 18, 2), 0);
        assertEquals(.8F, ManaShieldRules.healthPaidFraction(20, 18.4F, 2), .00001F);
        assertEquals(0, ManaShieldRules.healthPaidFraction(20, 21, 2), 0);
        assertEquals(0, ManaShieldRules.healthPaidFraction(20, 18, Float.NaN), 0);
    }
}
