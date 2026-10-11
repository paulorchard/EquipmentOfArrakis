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

    // ---- the cooldown: the field plan and which sound goes with each change

    private static final long SECOND = 1_000_000_000L;

    /** The system's per-tick logic on its own: the field, the shot time, and the sounds it would play, driven by a script. */
    private static final class Sim {
        boolean fieldUp;
        long lastShot = ShieldCooldown.NONE;
        boolean selected;
        boolean alive = true;
        float power = 100f;
        final java.util.List<String> sounds = new java.util.ArrayList<>();

        void shot(long now) {
            lastShot = now;
        }

        void tick(long now, boolean silent) {
            if (!alive) {
                lastShot = ShieldCooldown.NONE;
            }
            boolean cooling = ShieldCooldown.cooling(lastShot, now, 1.0);
            HoltzmanRules.Plan plan = HoltzmanRules.plan(selected, alive, power, cooling, fieldUp, silent);
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
    void theFieldIsDownWhileTheCooldownRunsWhateverElseIsTrue() {
        assertTrue(HoltzmanRules.raised(true, true, 50f, false));
        assertFalse(HoltzmanRules.raised(true, true, 50f, true), "cooling");
        assertFalse(HoltzmanRules.raised(false, true, 50f, false));
        assertFalse(HoltzmanRules.raised(true, true, 0f, false));
    }

    @Test
    void selectingRaisesTheFieldWithThePowerUpSoundAndDeselectingLowersItWithThePowerDownSound() {
        Sim s = new Sim();
        s.selected = true;
        s.tick(0, false);
        assertTrue(s.fieldUp);
        s.selected = false;
        s.tick(SECOND, false);
        assertFalse(s.fieldUp);
        assertEquals(java.util.List.of("ON", "OFF"), s.sounds);
    }

    @Test
    void aShotDropsTheFieldAndItComesBackOneSecondAfterTheLastShotWithOneSoundEach() {
        Sim s = new Sim();
        s.selected = true;
        s.tick(0, true);                    // up since the start, silently
        s.shot(10 * SECOND);
        s.tick(10 * SECOND, false);
        assertFalse(s.fieldUp, "down at once");
        s.tick(10 * SECOND + 999_000_000L, false);
        assertFalse(s.fieldUp);
        s.tick(11 * SECOND, false);
        assertTrue(s.fieldUp, "back one second after");
        assertEquals(java.util.List.of("OFF", "ON"), s.sounds);
    }

    @Test
    void shotsDuringTheCooldownRestartTheTimerAndMakeNoSound() {
        Sim s = new Sim();
        s.selected = true;
        s.tick(0, true);
        long t = 10 * SECOND;
        for (int i = 0; i < 6; i++) {           // shots 0.4 s apart
            s.shot(t + i * 400_000_000L);
            s.tick(t + i * 400_000_000L, false);
        }
        long last = t + 5 * 400_000_000L;
        s.tick(last + 900_000_000L, false);
        assertFalse(s.fieldUp, "still down 0.9 s after the last shot");
        s.tick(last + SECOND, false);
        assertTrue(s.fieldUp, "back 1.0 s after the last shot");
        assertEquals(java.util.List.of("OFF", "ON"), s.sounds, "one power-down for the first shot, one power-up at the end, nothing between");
    }

    @Test
    void aVolleyInOneTickPlaysOnePowerDownSound() {
        Sim s = new Sim();
        s.selected = true;
        s.tick(0, true);
        s.shot(5 * SECOND);
        s.shot(5 * SECOND);
        s.shot(5 * SECOND);                     // three projectiles of one shot
        s.tick(5 * SECOND, false);
        s.tick(5 * SECOND + 33_000_000L, false);
        assertEquals(java.util.List.of("OFF"), s.sounds);
    }

    @Test
    void aQuickReselectDuringTheCooldownDoesNotRaiseTheFieldUntilItEnds() {
        Sim s = new Sim();
        s.selected = true;
        s.tick(0, true);
        s.shot(10 * SECOND);
        s.tick(10 * SECOND, false);             // OFF
        s.selected = false;
        s.tick(10 * SECOND + 200_000_000L, false);
        s.selected = true;
        s.tick(10 * SECOND + 400_000_000L, false);
        assertFalse(s.fieldUp, "reselected inside the cooldown: still down");
        s.tick(11 * SECOND, false);
        assertTrue(s.fieldUp);
        assertEquals(java.util.List.of("OFF", "ON"), s.sounds, "no sound for the deselect (already down) nor the reselect; one at the real rise");
    }

    @Test
    void deselectingDuringTheCooldownMakesNoSoundWhenItEnds() {
        Sim s = new Sim();
        s.selected = true;
        s.tick(0, true);
        s.shot(10 * SECOND);
        s.tick(10 * SECOND, false);             // OFF
        s.selected = false;
        s.tick(10 * SECOND + 300_000_000L, false);
        s.tick(12 * SECOND, false);
        assertFalse(s.fieldUp);
        assertEquals(java.util.List.of("OFF"), s.sounds);
    }

    @Test
    void noFieldAndNoPowerUpSoundWhenTheCooldownEndsAtZeroPower() {
        Sim s = new Sim();
        s.selected = true;
        s.tick(0, true);
        s.shot(10 * SECOND);
        s.tick(10 * SECOND, false);
        s.power = 0f;
        s.tick(11 * SECOND, false);
        assertFalse(s.fieldUp);
        assertEquals(java.util.List.of("OFF"), s.sounds);
    }

    @Test
    void deathIsSilentAndForgetsTheCooldown() {
        Sim s = new Sim();
        s.selected = true;
        s.tick(0, true);
        s.alive = false;
        s.tick(SECOND, false);
        assertFalse(s.fieldUp);
        assertTrue(s.sounds.isEmpty(), "a dead player's field goes without a sound");
        s.shot(2 * SECOND);
        s.tick(2 * SECOND, false);
        assertEquals(ShieldCooldown.NONE, s.lastShot, "the cooldown is forgotten while dead");
        s.alive = true;
        s.tick(2 * SECOND + 50_000_000L, true);        // respawn: first tick for the new entity is silent
        assertTrue(s.fieldUp);
        assertTrue(s.sounds.isEmpty());
    }

    @Test
    void theFirstTickForAPlayerRaisesTheFieldSilently() {
        Sim s = new Sim();
        s.selected = true;
        s.tick(0, true);
        assertTrue(s.fieldUp);
        assertTrue(s.sounds.isEmpty());
    }

    @Test
    void thePowerDrainingToZeroLowersTheFieldWithThePowerDownSound() {
        Sim s = new Sim();
        s.selected = true;
        s.tick(0, true);
        s.power = 0f;
        s.tick(SECOND, false);
        assertFalse(s.fieldUp);
        assertEquals(java.util.List.of("OFF"), s.sounds);
    }

    @Test
    void theNewSoundEventsAndTheirClips() throws IOException {
        for (String suffix : new String[] {"", "_Local"}) {
            String on = resource("/Server/Audio/SoundEvents/Equipment_of_Arrakis/SFX_Arrakis_Holtzman_On" + suffix + ".json");
            assertTrue(on.contains("Sounds/Magic/Portals/Portal_Open_01.ogg") && on.contains("Sounds/Items/Portal/Textures/Portal_Zap_02.ogg"));
            assertTrue(on.contains("\"StartDelay\": 1"), "the zap starts a second after the charge-up");
            assertFalse(on.contains("\"Looping\": true"));
            assertTrue(on.contains("\"PreventSoundInterruption\": true"));
            String off = resource("/Server/Audio/SoundEvents/Equipment_of_Arrakis/SFX_Arrakis_Holtzman_Off" + suffix + ".json");
            assertTrue(off.contains(suffix.isEmpty() ? "Avatar_Powers_Disable_Alt_01.ogg" : "Avatar_Powers_Disable_Alt_Stereo_01.ogg"));
            assertFalse(off.contains("\"Looping\": true"));
            assertTrue(off.contains("\"PreventSoundInterruption\": true"));
            assertTrue(off.contains("\"AudioCategory\": \"AudioCat_Weapons\""));
            // the world version is attenuated like the other Holtzman sounds, the local one is not
            assertEquals(suffix.isEmpty(), on.contains("\"Parent\": \"SFX_Attn_Loud\"") && off.contains("\"Parent\": \"SFX_Attn_Loud\""));
        }
        assertFalse(resource("/Server/Audio/SoundEvents/Equipment_of_Arrakis/SFX_Arrakis_Holtzman_Off.json").contains("Stereo"));
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = HoltzmanRulesTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
