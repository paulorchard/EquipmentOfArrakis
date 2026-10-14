package com.paulorchard.islandcraft.equipmentofarrakis.power;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerMathTest {

    @Test
    void clampHoldsBetweenZeroAndMax() {
        assertEquals(0f, PowerMath.clamp(-5, 100));
        assertEquals(100f, PowerMath.clamp(250, 100));
        assertEquals(42f, PowerMath.clamp(42, 100));
        assertEquals(0f, PowerMath.clamp(10, -3), "a max below 0 counts as 0");
        assertEquals(0f, PowerMath.clamp(Float.NaN, 100));
    }

    @Test
    void fractionIsZeroWithoutAMax() {
        assertEquals(0.25f, PowerMath.fraction(25, 100));
        assertEquals(0f, PowerMath.fraction(25, 0));
        assertEquals(1f, PowerMath.fraction(500, 100));
    }

    @Test
    void spendIsAllOrNothing() {
        PowerMath.Spend ok = PowerMath.spend(50, 30, false, 100);
        assertTrue(ok.ok());
        assertEquals(20f, ok.after());
        PowerMath.Spend refused = PowerMath.spend(20, 30, false, 100);
        assertFalse(refused.ok());
        assertEquals(20f, refused.after(), "a refused spend changes nothing");
    }

    @Test
    void spendingExactlyWhatIsThereWorksAndSmallRoundingErrorsDoNotRefuse() {
        assertTrue(PowerMath.spend(30, 30, false, 100).ok());
        assertEquals(0f, PowerMath.spend(30, 30, false, 100).after());
        PowerMath.Spend nearly = PowerMath.spend(29.9995f, 30, false, 100);
        assertTrue(nearly.ok(), "29.9995 pays for 30 (tolerance)");
        assertEquals(0f, nearly.after());
        assertFalse(PowerMath.spend(29.9f, 30, false, 100).ok());
    }

    @Test
    void zeroAndNegativeAmountsAreNoOpsThatSucceed() {
        for (float amount : new float[] {0f, -1f, -100f}) {
            PowerMath.Spend result = PowerMath.spend(10, amount, false, 100);
            assertTrue(result.ok());
            assertEquals(10f, result.after());
        }
        assertTrue(PowerMath.spend(0, 0, false, 100).ok());
    }

    @Test
    void creativeSpendsNothingAndAlwaysSucceeds() {
        PowerMath.Spend result = PowerMath.spend(5, 50, true, 100);
        assertTrue(result.ok());
        assertEquals(5f, result.after());
        PowerMath.Take take = PowerMath.spendUpTo(5, 50, true, 100);
        assertEquals(50f, take.taken(), "creative reports the full amount");
        assertEquals(5f, take.after(), "and takes nothing");
    }

    @Test
    void spendUpToTakesWhatIsThere() {
        PowerMath.Take take = PowerMath.spendUpTo(20, 50, false, 100);
        assertEquals(20f, take.taken());
        assertEquals(0f, take.after());
        PowerMath.Take part = PowerMath.spendUpTo(80, 50, false, 100);
        assertEquals(50f, part.taken());
        assertEquals(30f, part.after());
        assertEquals(0f, PowerMath.spendUpTo(80, 0, false, 100).taken());
        assertEquals(0f, PowerMath.spendUpTo(80, -4, false, 100).taken());
        assertEquals(0f, PowerMath.spendUpTo(0, 10, false, 100).taken());
    }

    @Test
    void addAndSetClamp() {
        assertEquals(100f, PowerMath.add(90, 50, 100));
        assertEquals(0f, PowerMath.add(10, -50, 100));
        assertEquals(60f, PowerMath.add(10, 50, 100));
        assertEquals(100f, PowerMath.set(500, 100));
        assertEquals(0f, PowerMath.set(-1, 100));
        assertEquals(116f, PowerMath.set(116, 116), "with a raised max a higher value is allowed");
    }

    @Test
    void hasMatchesSpend() {
        assertTrue(PowerMath.has(30, 30));
        assertTrue(PowerMath.has(0, 0));
        assertFalse(PowerMath.has(29, 30));
    }
}
