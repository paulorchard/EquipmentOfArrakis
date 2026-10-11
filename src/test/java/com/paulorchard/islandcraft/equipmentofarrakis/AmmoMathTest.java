package com.paulorchard.islandcraft.equipmentofarrakis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmmoMathTest {

    @Test
    void ammoIsClampedToZeroAndOneHundred() {
        assertEquals(0f, AmmoMath.clamp(-5f), 0f);
        assertEquals(0f, AmmoMath.clamp(Float.NaN), 0f);
        assertEquals(100f, AmmoMath.clamp(150f), 0f);
        assertEquals(42.5f, AmmoMath.clamp(42.5f), 0f);
        assertEquals(100f, AmmoMath.clamp(Float.POSITIVE_INFINITY), 0f);
    }

    @Test
    void aShotTakesTheWholeCostAndLeavesTheRest() {
        AmmoMath.Payment first = AmmoMath.spend(100f, 1f);
        assertTrue(first.paid());
        assertEquals(99f, first.after(), 0f);
        AmmoMath.Payment last = AmmoMath.spend(1f, 1f);
        assertTrue(last.paid(), "exactly the cost is enough");
        assertEquals(0f, last.after(), 0f);
    }

    @Test
    void lessThanTheWholeCostIsRefusedAndTakesNothing() {
        AmmoMath.Payment partial = AmmoMath.spend(0.5f, 1f);
        assertFalse(partial.paid(), "half a dart is no shot");
        assertEquals(0.5f, partial.after(), 0f);
        AmmoMath.Payment empty = AmmoMath.spend(0f, 1f);
        assertFalse(empty.paid());
        assertEquals(0f, empty.after(), 0f);
        assertFalse(AmmoMath.spend(Float.NaN, 1f).paid());
    }

    @Test
    void aFreeShotIsAlwaysPaidAndChangesNothing() {
        for (float cost : new float[] {0f, -1f, Float.NaN}) {
            AmmoMath.Payment p = AmmoMath.spend(7f, cost);
            assertTrue(p.paid());
            assertEquals(7f, p.after(), 0f);
        }
        assertTrue(AmmoMath.spend(0f, 0f).paid());
    }

    @Test
    void aPaymentNeverGoesBelowZeroOrAboveTheMaximum() {
        assertEquals(0f, AmmoMath.spend(3f, 3f).after(), 0f);
        assertEquals(99f, AmmoMath.spend(500f, 1f).after(), 0f, "a stat above the maximum is held at the maximum first");
        assertEquals(7.5f, AmmoMath.spend(10f, 2.5f).after(), 0f);
    }

    @Test
    void rechargeAddsTheRateTimesTheTimeUpToTheMaximum() {
        assertEquals(55f, AmmoMath.recharged(50f, 10f, 0.5f), 1e-5);
        assertEquals(100f, AmmoMath.recharged(95f, 10f, 1f), 0f);
        assertEquals(10f, AmmoMath.recharged(0f, 10f, 1f), 1e-5);
        assertEquals(0f, AmmoMath.recharged(0f, 10f, 0f), 0f);
    }

    @Test
    void theDisplayedNumberIsTheWholeNumberOfShotsLeft() {
        assertEquals(100, AmmoMath.display(100f));
        assertEquals(99, AmmoMath.display(99.9f));
        assertEquals(1, AmmoMath.display(1f));
        assertEquals(0, AmmoMath.display(0.99f), "less than one shot shows 0");
        assertEquals(0, AmmoMath.display(-3f));
        assertEquals(0, AmmoMath.display(Float.NaN));
        assertEquals(100, AmmoMath.display(150f));
    }

    @Test
    void ammoRechargesOnlyAfterTheIdleWait() {
        long wait = 350_000_000L;
        assertTrue(AmmoMath.idle(null, 10L, wait), "never shot");
        assertFalse(AmmoMath.idle(1_000_000_000L, 1_200_000_000L, wait), "shot 0.2 s ago");
        assertFalse(AmmoMath.idle(1_000_000_000L, 1_350_000_000L, wait), "exactly the wait is not past it");
        assertTrue(AmmoMath.idle(1_000_000_000L, 1_400_000_000L, wait), "shot 0.4 s ago");
    }

    @Test
    void shotsAtTheCooldownRateKeepTheRechargePaused() {
        // One shot every 0.33 s is faster than the 0.35 s wait: the ammo does not recharge while the player keeps clicking.
        long wait = 350_000_000L;
        long shot = 1_000_000_000L;
        long next = shot + 330_000_000L;
        assertFalse(AmmoMath.idle(shot, next, wait));
    }
}
