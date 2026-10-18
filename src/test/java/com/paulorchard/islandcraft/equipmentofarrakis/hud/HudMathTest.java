package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudMathTest {

    @Test
    void theProgressBarValueIsTheFractionAndStaysInRange() {
        assertEquals(0f, HudMath.value(0), 0f);
        assertEquals(1f, HudMath.value(100), 0f);
        assertEquals(0.5f, HudMath.value(50), 1e-6f);
        assertEquals(0f, HudMath.value(-3), 0f, "below 0 is empty");
        assertEquals(1f, HudMath.value(250), 0f, "above 100 is full");
        assertEquals(0f, HudMath.value(Double.NaN), 0f);
        assertEquals(0.001f, HudMath.value(0.1), 1e-6f);
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
    void aBarAlertsOnlyWithItsSwitchOnAndBelowTheLimitAndItStopsAtTheLimit() {
        assertTrue(HudMath.alerting(true, 0, 5));
        assertTrue(HudMath.alerting(true, 4.99, 5));
        assertFalse(HudMath.alerting(true, 5, 5), "at 5% it stops");
        assertFalse(HudMath.alerting(true, 80, 5));
        assertFalse(HudMath.alerting(false, 1, 5), "a bar whose switch is off never alerts, even at 0");
        assertFalse(HudMath.alerting(true, Double.NaN, 5), "a bar that is not drawn does not alert");
    }

    @Test
    void theDefaultSwitchesAlertHealthAndWaterOnly() {
        HudConfig config = new HudConfig();
        assertTrue(config.flashes(HudBar.HEALTH));
        assertTrue(config.flashes(HudBar.WATER));
        assertFalse(config.flashes(HudBar.POWER));
        assertFalse(config.flashes(HudBar.EXPOSURE));
        assertEquals(5.0, config.getFlashBelowPercent(), 0.0);
    }

    @Test
    void theAlertFlipsEveryQuarterSecondLikeTheVanillaLowAlertAndStartsBright() {
        double flip = HudMetrics.ALERT_FLIP_SECONDS;
        assertEquals(0.25, flip, 0.0);
        assertTrue(HudMath.bright(0.0, flip));
        assertTrue(HudMath.bright(0.24, flip));
        assertFalse(HudMath.bright(0.25, flip));
        assertFalse(HudMath.bright(0.49, flip));
        assertTrue(HudMath.bright(0.5, flip));
        int flips = 0;
        boolean last = HudMath.bright(0, flip);
        for (double t = 0.001; t < 10.0; t += 0.001) {
            boolean now = HudMath.bright(t, flip);
            if (now != last) {
                flips++;
                last = now;
            }
        }
        assertTrue(flips >= 39 && flips <= 40, "4 flips a second over 10 s (at most 4 updates a second per bar), was " + flips);
    }

    @Test
    void theAlertStatesShowAndHideTheOverlayGroupsInsteadOfSettingOpacity() {
        assertEquals(HudMath.Alert.NONE, HudMath.alert(false, true));
        assertEquals(HudMath.Alert.NONE, HudMath.alert(false, false));
        assertEquals(HudMath.Alert.BRIGHT, HudMath.alert(true, true));
        assertEquals(HudMath.Alert.DIM, HudMath.alert(true, false));
        assertEquals(new HudMath.Overlays(true, true, false), HudMath.overlays(HudMath.Alert.BRIGHT), "bright: the error overlay and its full outline");
        assertEquals(new HudMath.Overlays(false, false, true), HudMath.overlays(HudMath.Alert.DIM), "dim: only the outline at the vanilla start opacity (a group fixed in the .ui)");
        assertEquals(new HudMath.Overlays(false, false, false), HudMath.overlays(HudMath.Alert.NONE));
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
    void theExposureIconGoesFromYellowToOrange() {
        assertEquals("#ffd23f", HudMath.exposureIconColor(0, "#ffd23f", "#ff8a1f"));
        assertEquals("#ff8a1f", HudMath.exposureIconColor(100, "#ffd23f", "#ff8a1f"));
        assertEquals(HudMath.blend("#ffd23f", "#ff8a1f", 0.5), HudMath.exposureIconColor(50, "#ffd23f", "#ff8a1f"));
        assertEquals("#ffd23f", HudMath.exposureIconColor(Double.NaN, "#ffd23f", "#ff8a1f"));
    }

    @Test
    void barsAreFoundByTheirCommandWordAndPositionCode() {
        assertSame(HudBar.POWER, HudBar.parse("Power"));
        assertSame(HudBar.EXPOSURE, HudBar.parse("exposure"));
        assertSame(HudBar.EXPOSURE, HudBar.parse("heat"));
        assertSame(HudBar.WATER, HudBar.parse("WATER"));
        assertNull(HudBar.parse("mana"));
        assertNull(HudBar.parse(null));
        assertSame(HudBar.HEALTH, HudBar.ofCode("lb"));
        assertSame(HudBar.WATER, HudBar.ofCode("rb"));
        assertSame(HudBar.POWER, HudBar.ofCode("lt"));
        assertSame(HudBar.EXPOSURE, HudBar.ofCode("RT"));
        assertNull(HudBar.ofCode("xx"));
    }
}
