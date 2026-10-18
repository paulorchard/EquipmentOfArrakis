package com.paulorchard.islandcraft.equipmentofarrakis.bloodbag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BloodBagTest {

    @Test
    void theBagHasItsDefaultsAndIsNotAnythingElse() {
        assertEquals(500, BloodBagModule.container().capacity());
        assertEquals("Arrakis_Blood_Bag", BloodBagModule.container().itemId());
        assertEquals(0, BloodBagModule.container().getAmount(null));
    }
}
