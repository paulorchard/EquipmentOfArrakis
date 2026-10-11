package com.paulorchard.islandcraft.equipmentofarrakis;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoltzmanRulesTest {

    @Test
    void theFieldIsRaisedOnlyWhileSelectedAliveAndPowered() {
        assertTrue(HoltzmanRules.raised(true, true, 0.1f));
        assertTrue(HoltzmanRules.raised(true, true, 100f));
        assertFalse(HoltzmanRules.raised(false, true, 100f), "not selected");
        assertFalse(HoltzmanRules.raised(true, false, 100f), "dead");
        assertFalse(HoltzmanRules.raised(true, true, 0f), "no power");
        assertFalse(HoltzmanRules.raised(true, true, -3f));
        assertFalse(HoltzmanRules.raised(false, false, 0f));
    }

    @Test
    void aSmallHitTakesPowerAndLeavesTheFieldUp() {
        HoltzmanRules.Absorption a = HoltzmanRules.absorb(20f, 100f, 1.0f);
        assertEquals(80f, a.powerAfter(), 1e-6);
        assertFalse(a.broken());
        assertEquals(20f, a.cost(), 1e-6);
    }

    @Test
    void theCostIsPowerPerDamageTimesDamage() {
        HoltzmanRules.Absorption a = HoltzmanRules.absorb(10f, 100f, 2.5f);
        assertEquals(75f, a.powerAfter(), 1e-6);
        assertEquals(25f, a.cost(), 1e-6);
        assertEquals(95f, HoltzmanRules.absorb(10f, 100f, 0.5f).powerAfter(), 1e-6);
    }

    @Test
    void aHitEqualToThePowerLeftBreaksTheFieldWithNoOverflow() {
        HoltzmanRules.Absorption exact = HoltzmanRules.absorb(30f, 30f, 1.0f);
        assertTrue(exact.broken(), "exactly equal breaks it");
        assertEquals(0f, exact.powerAfter(), 0f);
        HoltzmanRules.Absorption over = HoltzmanRules.absorb(100f, 30f, 1.0f);
        assertTrue(over.broken());
        assertEquals(0f, over.powerAfter(), 0f, "the power never goes below 0, and the whole hit is absorbed");
        HoltzmanRules.Absorption justUnder = HoltzmanRules.absorb(29.99f, 30f, 1.0f);
        assertFalse(justUnder.broken());
        assertEquals(0.01f, justUnder.powerAfter(), 1e-4);
    }

    @Test
    void aHitOfNoDamageOrAFreePriceChangesNothing() {
        for (float damage : new float[] {0f, -5f}) {
            HoltzmanRules.Absorption a = HoltzmanRules.absorb(damage, 50f, 1.0f);
            assertEquals(50f, a.powerAfter(), 0f);
            assertFalse(a.broken());
            assertEquals(0f, a.cost(), 0f);
        }
        HoltzmanRules.Absorption free = HoltzmanRules.absorb(500f, 50f, 0f);
        assertEquals(50f, free.powerAfter(), 0f);
        assertFalse(free.broken());
    }

    @Test
    void theHitPointIsOnTheLineFromTheCentreAtTheRadius() {
        double[] centre = {10, 5, -3};
        double[] p = HoltzmanRules.hitPoint(centre, new double[] {10, 5, 7}, 1.1);
        assertArrayEquals(new double[] {10, 5, -1.9}, p, 1e-9);
        double[] diagonal = HoltzmanRules.hitPoint(centre, new double[] {12, 7, -1}, 2.0);
        double dx = diagonal[0] - 10, dy = diagonal[1] - 5, dz = diagonal[2] + 3;
        assertEquals(2.0, Math.sqrt(dx * dx + dy * dy + dz * dz), 1e-9);
        assertEquals(dx, dy, 1e-9, "on the line towards the hit");
    }

    @Test
    void withNoHitLocationOrOneAtTheCentreTheCentreIsReturned() {
        double[] centre = {1, 2, 3};
        assertArrayEquals(centre, HoltzmanRules.hitPoint(centre, null, 1.1), 0.0);
        assertArrayEquals(centre, HoltzmanRules.hitPoint(centre, new double[] {1, 2, 3}, 1.1), 0.0);
    }

    @Test
    void theAssetsHaveTheNamesTheCodeUses() throws IOException {
        String item = resource("/Server/Item/Items/Equipment_of_Arrakis/Arrakis_Holtzman_Shield.json");
        assertTrue(item.contains("Weapon_Shield_Mithril.png"));
        assertTrue(item.contains("\"Usable\": true"));
        assertFalse(item.contains("\"Weapon\""), "not a weapon");
        assertFalse(item.contains("\"Secondary\": \"") || item.contains("\"Primary\": \""), "no interactions of its own");
        assertTrue(item.contains("\"OffHand\": -1"), "it never takes a click from the main hand");
        assertTrue(item.contains("\"PlayerAnimationsId\": \"Default\""));
        String effect = resource("/Server/Entity/Effects/Equipment_of_Arrakis/Arrakis_Holtzman_Field.json");
        for (String text : new String[] {"\"Infinite\": true", "EntityBottomTint", "EntityTopTint", "\"ModelVFXId\": \"Arrakis_Holtzman_Field\"",
                "Arrakis_Holtzman_Bubble\"", "Arrakis_Holtzman_Bubble_FP\"", "SFX_Arrakis_Holtzman_Field\"", "SFX_Arrakis_Holtzman_Field_Local\""}) {
            assertTrue(effect.contains(text), text);
        }
        String sound = resource("/Server/Audio/SoundEvents/Equipment_of_Arrakis/SFX_Arrakis_Holtzman_Field_Local.json");
        assertTrue(sound.contains("Void_Static_Loop_Stereo_01.ogg") && sound.contains("\"Looping\": true") && sound.contains("\"Volume\": -8.5") && sound.contains("DuckingRules") && sound.contains("\"BypassDucking\": true"));
        String breakSound = resource("/Server/Audio/SoundEvents/Equipment_of_Arrakis/SFX_Arrakis_Holtzman_Break.json");
        assertTrue(breakSound.contains("Crystal_Break_Layer_01.ogg"));
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = HoltzmanRulesTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
