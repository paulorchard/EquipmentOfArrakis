package com.paulorchard.islandcraft.equipmentofarrakis.power;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManaRefusalTest {

    @Test
    void oneLinePerSecond() {
        assertTrue(ManaRefusal.due(null, 5000), "the first refusal is always said");
        assertFalse(ManaRefusal.due(5000L, 5999), "a second refusal inside a second is not said");
        assertTrue(ManaRefusal.due(5000L, 6000), "after a second it is said again");
    }

    @Test
    void theAmountIsShownWithoutNoise() {
        assertEquals("10", ManaRefusal.format(10f));
        assertEquals("2.5", ManaRefusal.format(2.5f));
        assertEquals("0", ManaRefusal.format(0f));
    }
}
