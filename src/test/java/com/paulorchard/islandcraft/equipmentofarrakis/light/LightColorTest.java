package com.paulorchard.islandcraft.equipmentofarrakis.light;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightColorTest {

    @Test
    void theTorchWarmTintBecomesCoolWhiteAtTheSameBrightness() {
        // The torch light is #ba9 = 11, 10, 9.
        LightColor.Rgb cool = LightColor.resolve(0, 11, 10, 9, 0.85f, 0.93f, 1.0f, -1, -1, -1, -1);
        assertEquals(new LightColor.Rgb(0, 9, 10, 11), cool);
        assertTrue(LightColor.isCoolWhite(cool));
        assertEquals(11, Math.max(cool.red(), Math.max(cool.green(), cool.blue())), "the brightest channel is the torch brightness");
        assertFalse(LightColor.isCoolWhite(new LightColor.Rgb(0, 11, 10, 9)), "the warm torch is not cool white");
    }

    @Test
    void anySettingOfZeroOrMoreReplacesItsNumber() {
        LightColor.Rgb c = LightColor.resolve(0, 11, 10, 9, 0.85f, 0.93f, 1.0f, 4, 5, -1, 20);
        assertEquals(4, c.radius());
        assertEquals(5, c.red());
        assertEquals(10, c.green());
        assertEquals(15, c.blue(), "clamped to 15");
    }

    @Test
    void aDarkTorchGivesADarkLightNeverNegative() {
        assertEquals(new LightColor.Rgb(0, 0, 0, 0), LightColor.resolve(0, 0, 0, 0, 0.85f, 0.93f, 1f, -1, -1, -1, -1));
        assertEquals(0, LightColor.clamp(-3));
    }
}
