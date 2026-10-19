// EXPERIMENT: delete with the lab module
package com.paulorchard.islandcraft.equipmentofarrakis.lab;

import com.hypixel.hytale.protocol.FlyMode;
import com.hypixel.hytale.protocol.MovementSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabTest {

    @Test
    void theBeltIsActiveOnlyInTheAirWithSpaceHeldManaAndABeltInTheZSlot() {
        assertTrue(LabRules.beltActive(2, true, false, true, true));
        assertTrue(LabRules.beltActive(3, true, false, true, true));
        assertFalse(LabRules.beltActive(0, true, false, true, true), "no mode chosen");
        assertFalse(LabRules.beltActive(1, true, false, true, true), "Mk I is not a flight mode");
        assertFalse(LabRules.beltActive(2, false, false, true, true), "another Z item or an empty Z slot");
        assertFalse(LabRules.beltActive(2, true, true, true, true), "on the ground");
        assertFalse(LabRules.beltActive(2, true, false, false, true), "Space released");
        assertFalse(LabRules.beltActive(2, true, false, true, false), "no Mana");
    }

    @Test
    void manaIsSpentInProportionToTheTickTime() {
        assertEquals(0.05f, LabRules.manaForTick(1.0f, 0.05f), 1e-6);
        assertEquals(1.0f, LabRules.manaForTick(1.0f, 1.0f), 1e-6, "one Mana over one second of ticks");
        float total = 0;
        for (int i = 0; i < 20; i++) {
            total += LabRules.manaForTick(1.0f, 0.05f);
        }
        assertEquals(1.0f, total, 1e-5);
        assertEquals(0f, LabRules.manaForTick(-1f, 0.05f), "never negative");
        assertEquals(0f, LabRules.manaForTick(1f, -0.05f));
    }

    @Test
    void mkTwoFliesFlatAndMkThreeRises() {
        assertArrayEquals(new float[] {0.5f, 0f}, LabRules.flySpeeds(2, 0.5f, 0.5f));
        assertArrayEquals(new float[] {0.5f, 0.5f}, LabRules.flySpeeds(3, 0.5f, 0.5f));
        assertNull(LabRules.flySpeeds(0, 0.5f, 0.5f));
        assertNull(LabRules.flySpeeds(1, 0.5f, 0.5f));
    }

    @Test
    void theFallClampHoldsADownwardSpeedAndLeavesTheRestAlone() {
        assertEquals(-2.0, LabRules.clampFall(-15.0, 2.0));
        assertEquals(-2.0, LabRules.clampFall(-2.5, 2.0));
        assertEquals(-1.5, LabRules.clampFall(-1.5, 2.0), "slower than the limit");
        assertEquals(3.0, LabRules.clampFall(3.0, 2.0), "a rise is never clamped");
        assertEquals(-15.0, LabRules.clampFall(-15.0, 0), "0 is off");
        assertEquals(-15.0, LabRules.clampFall(-15.0, -1), "a negative maximum is off");
        assertTrue(LabRules.clampBites(-3.0, 2.0));
        assertFalse(LabRules.clampBites(-1.0, 2.0));
    }

    @Test
    void theEngineFallDamageFormulaGivesTheNumbersInTheNotes() {
        assertEquals(0, LabRules.fallDamage(21.0, 21.0, 100), "at the threshold nothing happens");
        assertEquals(0, LabRules.fallDamage(5.0, 21.0, 100));
        assertEquals(10, LabRules.fallDamage(22.0, 21.0, 100), "just over: the minimum of 10 percent of max health");
        assertEquals(43, LabRules.fallDamage(31.0, 21.0, 100));
        assertEquals(144, LabRules.fallDamage(41.0, 21.0, 100));
        assertEquals(87, LabRules.fallDamage(31.0, 21.0, 200), "it scales with the maximum health");
    }

    @Test
    void anyMovementSettingFieldCanBeSetByNameAndTheTypeIsChecked() {
        MovementSettings settings = new MovementSettings();
        assertEquals("verticalFlySpeed: 0.0 -> 0.5", LabFields.set(settings, "VERTICALFLYSPEED", "0.5"), "case does not matter");
        assertEquals(0.5f, settings.verticalFlySpeed);
        assertTrue(LabFields.set(settings, "invertedGravity", "true").endsWith("-> true"));
        assertTrue(settings.invertedGravity);
        assertTrue(LabFields.set(settings, "fly", "forced").endsWith("-> Forced"));
        assertEquals(FlyMode.Forced, settings.fly);
        assertTrue(LabFields.set(settings, "fly", "sideways").startsWith("cannot"));
        assertTrue(LabFields.set(settings, "mass", "heavy").startsWith("cannot"));
        assertTrue(LabFields.set(settings, "invertedGravity", "maybe").startsWith("cannot"));
        assertTrue(LabFields.set(settings, "noSuchField", "1").startsWith("no settable field"));
        assertEquals("0.5", LabFields.get(settings, "verticalFlySpeed"));
        assertNull(LabFields.get(settings, "noSuchField"));
        assertTrue(LabFields.names().contains("dragCoefficient") && LabFields.names().contains("variableJumpFallForce") && LabFields.names().contains("airDragMin"));
    }

    @Test
    void flyWordsAreOffAllowedAndForced() {
        assertEquals("Disabled", LabRules.flyWord("off"));
        assertEquals("Allowed", LabRules.flyWord("Allowed"));
        assertEquals("Forced", LabRules.flyWord("FORCED"));
        assertNull(LabRules.flyWord("up"));
        assertNull(LabRules.flyWord(null));
    }

    @Test
    void onlyTheLabBeltsCountAsTheZItem() {
        assertTrue(LabItems.isBelt("Arrakis_Lab_Belt"));
        assertTrue(LabItems.isBelt("Arrakis_Lab_Belt_Glider"));
        assertFalse(LabItems.isBelt("Glider_Scrap"));
        assertFalse(LabItems.isBelt(null));
    }
}
