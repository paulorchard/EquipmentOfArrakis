package com.paulorchard.islandcraft.equipmentofarrakis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShieldCooldownTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void theFieldIsDownForTheCooldownAfterTheLastShot() {
        long shot = 5 * SECOND;
        assertTrue(ShieldCooldown.cooling(shot, shot, 1.0), "at the shot");
        assertTrue(ShieldCooldown.cooling(shot, shot + 999_000_000L, 1.0));
        assertFalse(ShieldCooldown.cooling(shot, shot + SECOND, 1.0), "exactly the cooldown later it is back");
        assertFalse(ShieldCooldown.cooling(shot, shot + 3 * SECOND, 1.0));
    }

    @Test
    void noShotMeansNoCooldown() {
        assertFalse(ShieldCooldown.cooling(ShieldCooldown.NONE, 0, 1.0));
        assertFalse(ShieldCooldown.cooling(ShieldCooldown.NONE, Long.MAX_VALUE, 1.0));
        assertEquals(0, ShieldCooldown.remaining(ShieldCooldown.NONE, 10 * SECOND, 1.0), 0);
    }

    @Test
    void everyFurtherShotRestartsTheTimer() {
        // shots at 0, 0.4 and 0.8 s: the field is still down at 1.5 s (0.7 s after the last) and back at 1.8 s
        long last = 0;
        for (long t : new long[] {0, 400_000_000L, 800_000_000L}) {
            last = t;
        }
        assertTrue(ShieldCooldown.cooling(last, 1_500_000_000L, 1.0));
        assertFalse(ShieldCooldown.cooling(last, 1_800_000_000L, 1.0));
        assertTrue(ShieldCooldown.cooling(last, 1_799_000_000L, 1.0));
    }

    @Test
    void theRemainingTimeCountsDown() {
        assertEquals(1.0, ShieldCooldown.remaining(0, 0, 1.0), 1e-9);
        assertEquals(0.6, ShieldCooldown.remaining(0, 400_000_000L, 1.0), 1e-9);
        assertEquals(0.0, ShieldCooldown.remaining(0, 1_000_000_000L, 1.0), 0);
    }

    @Test
    void aZeroCooldownNeverKeepsTheFieldDown() {
        assertFalse(ShieldCooldown.cooling(0, 0, 0.0));
    }

    @Test
    void modifiersRunInOrderOnTheBaseAndNeverGoBelowZero() {
        UUID player = UUID.randomUUID();
        assertEquals(1.0, ShieldCooldown.seconds(1.0, List.of(), player), 0);
        ShieldCooldown.Modifier halve = (p, s) -> s * 0.5;
        ShieldCooldown.Modifier plusTwo = (p, s) -> s + 2;
        assertEquals(2.5, ShieldCooldown.seconds(1.0, List.of(halve, plusTwo), player), 1e-9);
        assertEquals(1.5, ShieldCooldown.seconds(1.0, List.of(plusTwo, halve), player), 1e-9);
        assertEquals(0.0, ShieldCooldown.seconds(1.0, List.of((p, s) -> -5), player), 0, "never below 0");
        assertEquals(0.0, ShieldCooldown.seconds(-3.0, List.of(), player), 0);
    }

    @Test
    void aModifierSeesWhichPlayerItIsFor() {
        UUID fast = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        ShieldCooldown.Modifier perPlayer = (p, s) -> p.equals(fast) ? s * 0.25 : s;
        assertEquals(0.25, ShieldCooldown.seconds(1.0, List.of(perPlayer), fast), 1e-9);
        assertEquals(1.0, ShieldCooldown.seconds(1.0, List.of(perPlayer), other), 1e-9);
    }

    @Test
    void theDataDefaultIsOneSecondAndNothingIsRegistered() {
        assertEquals(1.0, new HoltzmanSettings().getCooldownSeconds(), 0);
        assertEquals(0, ShieldCooldown.modifierCount());
        assertEquals(1.0, ShieldCooldown.baseSeconds(), 0);
    }
}
