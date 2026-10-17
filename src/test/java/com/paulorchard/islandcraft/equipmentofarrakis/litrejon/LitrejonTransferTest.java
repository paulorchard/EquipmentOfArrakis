package com.paulorchard.islandcraft.equipmentofarrakis.litrejon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LitrejonTransferTest {

    @Test
    void upToFiftyAtATime() {
        assertEquals(50, LitrejonTransfer.units(500, 0, 100, 50), "a dry player takes the cap");
        assertEquals(50, LitrejonTransfer.units(500, 10, 100, 50));
    }

    @Test
    void onlyWhatThePlayerLacks() {
        assertEquals(19, LitrejonTransfer.units(500, 81, 100, 50), "19 below the cap takes 19");
        assertEquals(1, LitrejonTransfer.units(500, 99, 100, 50));
        assertEquals(0, LitrejonTransfer.units(500, 100, 100, 50), "a full player takes nothing");
        assertEquals(0, LitrejonTransfer.units(500, 120, 100, 50), "above the cap is full");
    }

    @Test
    void notMoreThanTheFlaskHas() {
        assertEquals(7, LitrejonTransfer.units(7, 0, 100, 50));
        assertEquals(0, LitrejonTransfer.units(0, 0, 100, 50), "an empty flask gives nothing");
    }

    @Test
    void aFractionalShortfallIsOfferedWholeAndTheFlaskPaysForWhatWasGained() {
        assertEquals(20, LitrejonTransfer.units(500, 80.6, 100, 50));
        assertEquals(20, LitrejonTransfer.cost(19.4));
        assertEquals(19, LitrejonTransfer.cost(19.0));
        assertEquals(0, LitrejonTransfer.cost(0));
    }
}
