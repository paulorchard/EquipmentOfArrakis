package com.paulorchard.islandcraft.equipmentofarrakis.harvester;

import org.junit.jupiter.api.Test;

import static com.paulorchard.islandcraft.equipmentofarrakis.harvester.HarvesterRules.Refusal.FLASKS_FULL;
import static com.paulorchard.islandcraft.equipmentofarrakis.harvester.HarvesterRules.Refusal.NONE;
import static com.paulorchard.islandcraft.equipmentofarrakis.harvester.HarvesterRules.Refusal.NOT_PRIMROSE;
import static com.paulorchard.islandcraft.equipmentofarrakis.harvester.HarvesterRules.Refusal.NO_FLASK;
import static com.paulorchard.islandcraft.equipmentofarrakis.harvester.HarvesterRules.Refusal.NO_MANA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarvesterRulesTest {

    @Test
    void aGoodSwingIsAccepted() {
        assertEquals(NONE, HarvesterRules.refusal(true, 1, true, 10f, 10f, false), "exactly the cost is enough");
        assertEquals(NONE, HarvesterRules.refusal(true, 3, true, 100f, 10f, false));
    }

    @Test
    void theRulesAreCheckedInOrder() {
        assertEquals(NOT_PRIMROSE, HarvesterRules.refusal(false, 0, false, 0f, 10f, false), "the target comes first");
        assertEquals(NO_FLASK, HarvesterRules.refusal(true, 0, false, 0f, 10f, false), "no flask carried");
        assertEquals(FLASKS_FULL, HarvesterRules.refusal(true, 2, false, 0f, 10f, false), "all flasks full");
        assertEquals(NO_MANA, HarvesterRules.refusal(true, 1, true, 9f, 10f, false), "9 Mana is not enough");
    }

    @Test
    void creativeNeedsNoMana() {
        assertEquals(NONE, HarvesterRules.refusal(true, 1, true, 0f, 10f, true));
    }

    @Test
    void onlyThePrimroseCounts() {
        assertTrue(HarvesterRules.isPrimrose("Arrakis_Primrose", "Arrakis_Primrose"));
        assertFalse(HarvesterRules.isPrimrose("Arrakis_Primrose_Dead", "Arrakis_Primrose"));
        assertFalse(HarvesterRules.isPrimrose(null, "Arrakis_Primrose"));
    }
}
