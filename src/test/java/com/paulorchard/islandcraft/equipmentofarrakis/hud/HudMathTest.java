package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudMathTest {

    @Test
    void aFillIsSplitIntoWeightsThatAlwaysAddUpToTheScale() {
        for (double percent = -5; percent <= 105; percent += 0.1) {
            int[] s = HudMath.split(percent);
            assertEquals(HudMath.SCALE, s[0] + s[1], "percent " + percent);
            assertTrue(s[0] >= 0 && s[1] >= 0);
        }
    }

    @Test
    void zeroAndOneHundredAreExactAndTheMiddleIsHalf() {
        assertArrayEquals(new int[] {1000, 0}, HudMath.split(0));
        assertArrayEquals(new int[] {0, 1000}, HudMath.split(100));
        assertArrayEquals(new int[] {500, 500}, HudMath.split(50));
        assertArrayEquals(new int[] {1000, 0}, HudMath.split(-3), "below 0 is empty");
        assertArrayEquals(new int[] {0, 1000}, HudMath.split(250), "above 100 is full");
        assertArrayEquals(new int[] {1000, 0}, HudMath.split(Double.NaN));
        assertArrayEquals(new int[] {999, 1}, HudMath.split(0.1), "0.1% is one step");
    }

    @Test
    void aValueIsSentFirstWhenItMovedEnoughAndAlwaysAtTheEnds() {
        assertTrue(HudMath.changed(Double.NaN, 50, 0.25), "the first time");
        assertFalse(HudMath.changed(50, 50, 0.25));
        assertFalse(HudMath.changed(50, 50.2, 0.25), "less than the minimum");
        assertTrue(HudMath.changed(50, 50.25, 0.25), "exactly the minimum");
        assertTrue(HudMath.changed(50, 49.7, 0.25));
        assertTrue(HudMath.changed(0.1, 0, 0.25), "reaching 0 is always sent");
        assertTrue(HudMath.changed(99.9, 100, 0.25), "reaching 100 is always sent");
        assertFalse(HudMath.changed(100, 100, 0.25));
    }

    @Test
    void onlyAFlashingBarBelowTheLimitFlashesAndItStopsAtTheLimit() {
        assertTrue(HudMath.flashing(true, 0, 5));
        assertTrue(HudMath.flashing(true, 4.99, 5));
        assertFalse(HudMath.flashing(true, 5, 5), "at 5% it stops");
        assertFalse(HudMath.flashing(true, 80, 5));
        assertFalse(HudMath.flashing(false, 1, 5), "Health and Heat Exposure never flash");
        assertTrue(HudBar.POWER.flashes && HudBar.WATER.flashes);
        assertFalse(HudBar.HEALTH.flashes || HudBar.EXPOSURE.flashes);
    }

    @Test
    void theFlashPhaseFlipsHzTimesASecondAndStartsNormal() {
        assertFalse(HudMath.bright(0.0, 2));
        assertFalse(HudMath.bright(0.49, 2));
        assertTrue(HudMath.bright(0.5, 2));
        assertTrue(HudMath.bright(0.99, 2));
        assertFalse(HudMath.bright(1.0, 2));
        int flips = 0;
        boolean last = HudMath.bright(0, 2);
        for (double t = 0.01; t < 10.0; t += 0.01) {
            boolean now = HudMath.bright(t, 2);
            if (now != last) {
                flips++;
                last = now;
            }
        }
        assertTrue(flips >= 19 && flips <= 20, "2 flips a second over 10 s, was " + flips);
        assertFalse(HudMath.bright(3.3, 0), "no rate: never bright");
    }

    @Test
    void theBrightPhaseMovesTheColourTowardWhiteOnlyWhileFlashing() {
        assertEquals("#57bbaf", HudMath.colorOf("#57bbaf", true, false, 55));
        assertEquals("#57bbaf", HudMath.colorOf("#57bbaf", false, true, 55), "bright without a flash changes nothing");
        assertEquals("#b3e0db", HudMath.colorOf("#57bbaf", true, true, 55));
        assertEquals("#ffffff", HudMath.colorOf("#57bbaf", true, true, 100));
    }

    @Test
    void theExposureColourIsOrangeThenABlendToRed() {
        String orange = "#ff8a1f";
        String red = "#e0201a";
        assertEquals(orange, HudMath.exposureColor(0, 75, orange, red));
        assertEquals(orange, HudMath.exposureColor(75, 75, orange, red), "orange up to the start");
        assertEquals(red, HudMath.exposureColor(100, 75, orange, red), "red at 100");
        assertEquals(HudMath.blend(orange, red, 0.5), HudMath.exposureColor(87.5, 75, orange, red), "midpoint");
        assertEquals(orange, HudMath.exposureColor(10, 100, orange, red), "a start of 100 never blends");
    }

    @Test
    void barsAreFoundByTheirCommandWord() {
        assertSame(HudBar.POWER, HudBar.parse("Power"));
        assertSame(HudBar.EXPOSURE, HudBar.parse("exposure"));
        assertSame(HudBar.EXPOSURE, HudBar.parse("heat"));
        assertSame(HudBar.WATER, HudBar.parse("WATER"));
        assertNull(HudBar.parse("mana"));
        assertNull(HudBar.parse(null));
    }
}
