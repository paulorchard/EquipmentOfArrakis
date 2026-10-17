package com.paulorchard.islandcraft.equipmentofarrakis.cutter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CutterGateTest {

    @Test
    void aShotNeedsTheWholeCost() {
        assertFalse(CutterGate.canShoot(9f, 10f, false), "9 Mana: no shot");
        assertTrue(CutterGate.canShoot(10f, 10f, false), "10 Mana: a shot");
        assertTrue(CutterGate.canShoot(100f, 10f, false));
        assertFalse(CutterGate.canShoot(0f, 10f, false));
    }

    @Test
    void creativeIsFree() {
        assertTrue(CutterGate.canShoot(0f, 10f, true));
    }

    @Test
    void fullManaPaysForTenShots() {
        assertEquals(10, CutterGate.shotsFor(100f, 10f));
        assertEquals(9, CutterGate.shotsFor(99f, 10f));
        assertEquals(0, CutterGate.shotsFor(9.9f, 10f));
    }
}
