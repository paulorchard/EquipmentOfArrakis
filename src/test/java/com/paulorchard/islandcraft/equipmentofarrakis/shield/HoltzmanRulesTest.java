package com.paulorchard.islandcraft.equipmentofarrakis.shield;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoltzmanRulesTest {

    // ---- the rule

    @Test
    void theFieldIsRaisedOnlyWhileOnAliveAndPoweredAndNotCoolingOrBroken() {
        assertTrue(HoltzmanRules.raised(true, true, 0.1f, false, false), "any Mana above 0 raises it");
        assertTrue(HoltzmanRules.raised(true, true, 100f, false, false));
        assertFalse(HoltzmanRules.raised(false, true, 100f, false, false), "toggle OFF");
        assertFalse(HoltzmanRules.raised(true, false, 100f, false, false), "dead");
        assertFalse(HoltzmanRules.raised(true, true, 0f, false, false), "no Mana");
        assertFalse(HoltzmanRules.raised(true, true, -3f, false, false));
        assertFalse(HoltzmanRules.raised(true, true, 50f, true, false), "the cooldown after a shot");
        assertFalse(HoltzmanRules.raised(true, true, 50f, false, true), "broken, waiting to re-arm");
    }

    @Test
    void turningItOnWithLittleManaRaisesTheFieldUnlessItJustBroke() {
        assertTrue(HoltzmanRules.raised(true, true, 5f, false, false), "ON with 5 Mana: up, as in v1");
        assertFalse(HoltzmanRules.raised(true, true, 5f, false, true), "5 Mana right after a break: still waiting for 100");
    }

    // ---- absorbing

    @Test
    void aSmallHitTakesManaAndLeavesTheFieldUp() {
        HoltzmanRules.Absorption a = HoltzmanRules.absorb(20f, 100f, 1.0f);
        assertEquals(80f, a.manaAfter(), 1e-6);
        assertFalse(a.broken());
        assertEquals(20f, a.cost(), 1e-6);
    }

    @Test
    void theCostIsManaPerDamageTimesDamage() {
        HoltzmanRules.Absorption a = HoltzmanRules.absorb(10f, 100f, 2.5f);
        assertEquals(75f, a.manaAfter(), 1e-6);
        assertEquals(25f, a.cost(), 1e-6);
        assertEquals(95f, HoltzmanRules.absorb(10f, 100f, 0.5f).manaAfter(), 1e-6);
    }

    @Test
    void aHitEqualToTheManaLeftBreaksTheFieldWithNoOverflow() {
        HoltzmanRules.Absorption exact = HoltzmanRules.absorb(30f, 30f, 1.0f);
        assertTrue(exact.broken(), "exactly equal breaks it");
        assertEquals(0f, exact.manaAfter(), 0f);
        HoltzmanRules.Absorption over = HoltzmanRules.absorb(100f, 30f, 1.0f);
        assertTrue(over.broken());
        assertEquals(0f, over.manaAfter(), 0f, "Mana never goes below 0, and the whole hit is absorbed");
        HoltzmanRules.Absorption justUnder = HoltzmanRules.absorb(29.99f, 30f, 1.0f);
        assertFalse(justUnder.broken());
        assertEquals(0.01f, justUnder.manaAfter(), 1e-4);
    }

    @Test
    void aHitOfNoDamageOrAFreePriceChangesNothing() {
        for (float damage : new float[] {0f, -5f}) {
            HoltzmanRules.Absorption a = HoltzmanRules.absorb(damage, 50f, 1.0f);
            assertEquals(50f, a.manaAfter(), 0f);
            assertFalse(a.broken());
            assertEquals(0f, a.cost(), 0f);
        }
        HoltzmanRules.Absorption free = HoltzmanRules.absorb(500f, 50f, 0f);
        assertEquals(50f, free.manaAfter(), 0f);
        assertFalse(free.broken());
    }

    // ---- the break and the re-arm

    @Test
    void aBrokenShieldRearmsWhenManaIsBackToTheLevelAndNotBefore() {
        assertTrue(HoltzmanRules.stillBroken(true, 0f, 100f));
        assertTrue(HoltzmanRules.stillBroken(true, 99.9f, 100f), "99.9 is not full");
        assertFalse(HoltzmanRules.stillBroken(true, 100f, 100f), "at 100 it re-arms");
        assertFalse(HoltzmanRules.stillBroken(false, 0f, 100f), "a shield that is not broken is not waiting");
    }

    @Test
    void aLoweredMaximumCannotLockTheShield() {
        assertEquals(100f, HoltzmanRules.rearmLevel(100f, 150f), 0f);
        assertEquals(80f, HoltzmanRules.rearmLevel(100f, 80f), 0f, "a max of 80 re-arms at 80");
        assertEquals(100f, HoltzmanRules.rearmLevel(100f, 0f), 0f, "no max known: the setting");
    }

    /** The per-tick logic on its own: toggle, Mana, hits, shots and time, driven by a script. */
    private static final class Sim {
        static final long SECOND = 1_000_000_000L;
        boolean fieldUp;
        boolean on;
        boolean broken;
        boolean alive = true;
        float mana = 100f;
        long lastShot = ShieldCooldown.NONE;
        final List<String> sounds = new ArrayList<>();

        void shot(long now) {
            lastShot = now;
        }

        void hit(float damage) {
            if (!HoltzmanRules.raised(on, alive, mana, ShieldCooldown.cooling(lastShot, 0, 1.0), broken)) {
                return;
            }
            HoltzmanRules.Absorption a = HoltzmanRules.absorb(damage, mana, 1.0f);
            mana = a.manaAfter();
            if (a.broken()) {
                broken = true;
                fieldUp = false; // the break lowers it itself: no off sound
                sounds.add("BREAK");
            }
        }

        void tick(long now, boolean silent) {
            if (broken && !HoltzmanRules.stillBroken(true, mana, 100f)) {
                broken = false;
            }
            boolean cooling = ShieldCooldown.cooling(lastShot, now, 1.0);
            HoltzmanRules.Plan plan = HoltzmanRules.plan(on, alive, mana, cooling, broken, fieldUp, silent);
            if (plan.change() == HoltzmanRules.Change.RAISE) {
                fieldUp = true;
            } else if (plan.change() == HoltzmanRules.Change.LOWER) {
                fieldUp = false;
            }
            if (plan.sound() != HoltzmanRules.Sound.NONE) {
                sounds.add(plan.sound().name());
            }
        }
    }

    @Test
    void switchingOnRaisesWithThePowerUpSoundAndOffLowersWithThePowerDownSound() {
        Sim sim = new Sim();
        sim.tick(0, true);
        assertFalse(sim.fieldUp);
        sim.on = true;
        sim.tick(1, false);
        assertTrue(sim.fieldUp);
        sim.on = false;
        sim.tick(2, false);
        assertFalse(sim.fieldUp);
        assertEquals(List.of("ON", "OFF"), sim.sounds);
    }

    @Test
    void aShotDropsTheFieldForTheCooldownAndItReturnsByItselfAndEachShotRestartsTheTimer() {
        Sim sim = new Sim();
        sim.on = true;
        sim.tick(0, true);
        assertTrue(sim.fieldUp);
        sim.shot(1 * Sim.SECOND);
        sim.tick(1 * Sim.SECOND, false);
        assertFalse(sim.fieldUp, "down at the shot");
        sim.shot((long) (1.6 * Sim.SECOND));
        sim.tick((long) (2.2 * Sim.SECOND), false);
        assertFalse(sim.fieldUp, "the second shot restarted the second: 0.6 s after it is still down");
        sim.tick((long) (2.7 * Sim.SECOND), false);
        assertTrue(sim.fieldUp, "1.0 s after the last shot it is back");
        assertEquals(List.of("OFF", "ON"), sim.sounds, "one off sound and one on sound, not one per shot");
    }

    @Test
    void aBreakKeepsTheToggleOnAndTheFieldReturnsAtFullManaWithThePowerUpSound() {
        Sim sim = new Sim();
        sim.on = true;
        sim.mana = 30f;
        sim.tick(0, true);
        assertTrue(sim.fieldUp);
        sim.hit(30f);
        assertEquals(0f, sim.mana, 0f);
        assertTrue(sim.broken);
        assertTrue(sim.on, "the toggle stays ON");
        sim.tick(1, false);
        assertFalse(sim.fieldUp);
        sim.mana = 40f; // recharging, but not full: nothing happens
        sim.tick(2, false);
        assertFalse(sim.fieldUp);
        sim.mana = 100f;
        sim.tick(3, false);
        assertTrue(sim.fieldUp, "full Mana: re-armed");
        assertEquals(List.of("BREAK", "ON"), sim.sounds, "the break plays its own sounds; the re-arm plays the normal power-up sound");
    }

    @Test
    void deathIsSilentAndTheFieldGoesDown() {
        Sim sim = new Sim();
        sim.on = true;
        sim.tick(0, true);
        sim.alive = false;
        sim.tick(1, false);
        assertFalse(sim.fieldUp);
        assertTrue(sim.sounds.isEmpty());
    }

    // ---- the hit point

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

    // ---- the assets

    @Test
    void theAssetsHaveTheNamesTheCodeUses() throws IOException {
        String effect = resource("/Server/Entity/Effects/Equipment_of_Arrakis/Shield/Arrakis_Holtzman_Field.json");
        for (String text : new String[] {"\"Infinite\": true", "EntityBottomTint", "EntityTopTint", "\"ModelVFXId\": \"Arrakis_Holtzman_Field\"",
                "Arrakis_Holtzman_Bubble\"", "Arrakis_Holtzman_Bubble_FP\"", "SFX_Arrakis_Holtzman_Field\"", "SFX_Arrakis_Holtzman_Field_Local\""}) {
            assertTrue(effect.contains(text), text);
        }
        String sound = resource("/Server/Audio/SoundEvents/Equipment_of_Arrakis/Shield/SFX_Arrakis_Holtzman_Field_Local.json");
        assertTrue(sound.contains("Void_Static_Loop_Stereo_01.ogg") && sound.contains("\"Looping\": true") && sound.contains("\"Volume\": -8.5")
                && sound.contains("DuckingRules") && sound.contains("\"BypassDucking\": true"));
        String breakSound = resource("/Server/Audio/SoundEvents/Equipment_of_Arrakis/Shield/SFX_Arrakis_Holtzman_Break.json");
        assertTrue(breakSound.contains("Crystal_Break_Layer_01.ogg"));
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = HoltzmanRulesTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
