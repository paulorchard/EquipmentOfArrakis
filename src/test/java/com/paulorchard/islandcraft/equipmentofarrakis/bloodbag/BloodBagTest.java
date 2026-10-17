package com.paulorchard.islandcraft.equipmentofarrakis.bloodbag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class BloodBagTest {

    @Test
    void theBagHasItsDefaultsAndIsNotAnythingElse() {
        assertEquals(500, BloodBag.capacity());
        assertEquals("Arrakis_Blood_Bag", BloodBag.container().itemId());
        assertFalse(BloodBag.isBag(null));
        assertEquals(0, BloodBag.getAmount(null));
        assertNull(null);
    }
}
