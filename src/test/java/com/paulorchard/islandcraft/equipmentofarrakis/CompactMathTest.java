package com.paulorchard.islandcraft.equipmentofarrakis;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactMathTest {

    private static final List<String> GROUPS = List.of("Sand");
    private static final List<String> BLOCKS = List.of("Arrakis_Sand");
    private static final List<String> PREFIXES = List.of("Arrakis_Worm_Ripple_Layer_", "Arrakis_Worm_Sunk_");

    private static boolean allowed(String group, String id) {
        return CompactMath.allowed(group, id, GROUPS, BLOCKS, PREFIXES);
    }

    @Test
    void vanillaSandIsAllowedAndTheExclusionsAreNot() {
        assertTrue(allowed("Sand", "Soil_Sand"));
        assertTrue(allowed("Sand", "Soil_Sand_White"));
        assertFalse(allowed("Sand", "Arrakis_Sand"), "excluded by id");
        for (int n = 1; n <= 16; n++) {
            assertFalse(allowed("Sand", "Arrakis_Worm_Ripple_Layer_" + n));
            assertFalse(allowed("Sand", "Arrakis_Worm_Sunk_" + n));
        }
    }

    @Test
    void otherGroupsAndMissingValuesAreNotAllowed() {
        assertFalse(allowed("Soil", "Soil_Dirt"));
        assertFalse(allowed("sand", "Soil_Sand"), "the group is compared exactly, case included");
        assertFalse(allowed(null, "Soil_Sand"));
        assertFalse(allowed("Sand", null));
        assertFalse(allowed("Stone", "Rock_Stone"));
    }

    @Test
    void otherArrakisBlocksOfTheGroupAreNotExcluded() {
        // The exclusion is by exact id and by the two worm prefixes only: a future Arrakis_ sand block is compactable.
        assertTrue(allowed("Sand", "Arrakis_Sand_Packed"));
        assertTrue(allowed("Sand", "Arrakis_Worm_Dust"));
        // A prefix is not an exact id, and an exact id is not a prefix.
        assertFalse(allowed("Sand", "Arrakis_Worm_Sunk_"));
        assertTrue(allowed("Sand", "Arrakis_Sand_2"));
    }

    @Test
    void theListsAreData() {
        assertTrue(CompactMath.allowed("Sand", "Arrakis_Sand", List.of("Sand"), List.of(), List.of()));
        assertTrue(CompactMath.allowed("Gravel", "Soil_Gravel", List.of("Sand", "Gravel"), List.of(), List.of()));
        assertFalse(CompactMath.allowed("Sand", "X_1", List.of("Sand"), List.of(), List.of("X_")));
        // An empty prefix would exclude everything; it is ignored.
        assertTrue(CompactMath.allowed("Sand", "X_1", List.of("Sand"), List.of(), List.of("")));
    }

    @Test
    void aCycleCompletesAfterItsSecondsAndStartsOver() {
        CompactMath.Cycle cycle = new CompactMath.Cycle(1.0, 0.2);
        int drops = 0;
        double time = 0;
        for (int i = 0; i < 100; i++) {
            if (cycle.advance(0.1)) {
                drops++;
                assertEquals(0.0, cycle.fraction(), 1e-9, "progress is back at 0 after a drop");
            }
            time += 0.1;
        }
        // 100 ticks of 0.1 s = 10 s = 10 cycles (floating point may need one tick more or less for the last).
        assertTrue(drops >= 9 && drops <= 10, "drops " + drops + " in " + time + " s");
    }

    @Test
    void theFirstDropComesAtOneSecond() {
        CompactMath.Cycle cycle = new CompactMath.Cycle(1.0, 0.2);
        for (int i = 0; i < 9; i++) {
            assertFalse(cycle.advance(0.1));
        }
        assertEquals(0.9, cycle.fraction(), 1e-9);
        assertTrue(cycle.advance(0.1000001));
    }

    @Test
    void aLagSpikeCannotGiveMoreThanOneStepNorTwoDrops() {
        CompactMath.Cycle cycle = new CompactMath.Cycle(1.0, 0.2);
        assertFalse(cycle.advance(30.0), "a 30 s stall counts as 0.2 s");
        assertEquals(0.2, cycle.fraction(), 1e-9);
        CompactMath.Cycle short1 = new CompactMath.Cycle(0.05, 0.2);
        assertTrue(short1.advance(30.0));
        assertEquals(0.0, short1.fraction(), 1e-9, "never a second drop from one tick");
    }

    @Test
    void resetClearsProgressAndNegativeTimeIsIgnored() {
        CompactMath.Cycle cycle = new CompactMath.Cycle(1.0, 0.2);
        cycle.advance(0.15);
        cycle.reset();
        assertEquals(0.0, cycle.fraction(), 1e-9);
        cycle.advance(-5);
        assertEquals(0.0, cycle.fraction(), 1e-9);
    }

    @Test
    void theCrackNeverReachesTheBreakingPoint() {
        assertEquals(1.0f, CompactMath.healthAt(0.0, 0.9), 1e-6);
        assertEquals(0.55f, CompactMath.healthAt(0.5, 0.9), 1e-6);
        assertEquals(0.1f, CompactMath.healthAt(1.0, 0.9), 1e-6);
        // Whatever the progress or the setting, the shown health stays at or above 0.1.
        for (double fraction : new double[] {-3, 0, 0.5, 1, 7, Double.MAX_VALUE}) {
            for (double setting : new double[] {-1, 0, 0.5, 0.9, 1, 5, 100}) {
                float health = CompactMath.healthAt(fraction, CompactMath.clampMaxCrack(setting));
                assertTrue(health >= 0.1f - 1e-6f && health <= 1.0f, "health " + health);
            }
        }
        assertEquals(0.9, CompactMath.clampMaxCrack(1.0), 0.0);
        assertEquals(0.0, CompactMath.clampMaxCrack(-1.0), 0.0);
    }

    @Test
    void theCompactorAssetsCarryTheDefaults() throws IOException {
        String aim = resource("/Server/Item/Interactions/Equipment_of_Arrakis/Arrakis_Compactor_Aim.json");
        assertTrue(aim.contains("\"Type\": \"Arrakis_Compactor_Compact\""));
        assertTrue(aim.contains("\"Range\": 3,"));
        assertTrue(aim.contains("\"CompactSeconds\": 1,"));
        assertTrue(aim.contains("\"AllowedGroups\": [\"Sand\"]"));
        assertTrue(aim.contains("\"ExcludedBlocks\": [\"Arrakis_Sand\"]"));
        assertTrue(aim.contains("\"ExcludedPrefixes\": [\"Arrakis_Worm_Ripple_Layer_\", \"Arrakis_Worm_Sunk_\"]"));
        assertTrue(aim.contains("\"Arrakis_Aim\""), "the aim animation is unchanged");
        String effect = resource("/Server/Entity/Effects/Equipment_of_Arrakis/Arrakis_Compactor_Active.json");
        assertTrue(effect.contains("\"OverlapBehavior\": \"Overwrite\"") && effect.contains("\"Duration\": 0.25"));
        assertFalse(effect.contains("Removal"), "no end sound");
        String hum = resource("/Server/Audio/SoundEvents/Equipment_of_Arrakis/SFX_Arrakis_Compactor_Active.json");
        assertTrue(hum.contains("Sounds/Weapons/Staff/Staff_Charged_LP_01.ogg") && hum.contains("\"Looping\": true")
                && !hum.contains("-6") && hum.contains("\"Volume\": 6"));
        assertTrue(aim.contains("\"DustParticleId\": \"Arrakis_Compactor_Dust\""));
        String root = resource("/Server/Item/RootInteractions/Equipment_of_Arrakis/Arrakis_Root_Compactor.json");
        assertTrue(root.contains("\"RequireNewClick\": true") && root.contains("Arrakis_Compactor_Aim"));
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = CompactMathTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
