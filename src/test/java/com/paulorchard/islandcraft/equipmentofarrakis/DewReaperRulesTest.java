package com.paulorchard.islandcraft.equipmentofarrakis;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DewReaperRulesTest {

    @Test
    void thePrimroseIdMakesPrimroseModeAndEverythingElseIsAir() {
        assertEquals(DewReaperRules.Mode.PRIMROSE, DewReaperRules.mode("Arrakis_Primrose", "Arrakis_Primrose"));
        assertEquals(DewReaperRules.Mode.AIR, DewReaperRules.mode("Rock_Stone", "Arrakis_Primrose"));
        assertEquals(DewReaperRules.Mode.AIR, DewReaperRules.mode("Soil_Sand", "Arrakis_Primrose"));
        assertEquals(DewReaperRules.Mode.AIR, DewReaperRules.mode(null, "Arrakis_Primrose"), "no block in range");
        assertEquals(DewReaperRules.Mode.AIR, DewReaperRules.mode("Arrakis_Primrose_2", "Arrakis_Primrose"), "the id is exact");
        assertEquals(DewReaperRules.Mode.PRIMROSE, DewReaperRules.mode("Other", "Other"), "the id is data");
    }

    @Test
    void onlyTheWaterModAndPowerRefuseAClick() {
        assertEquals(DewReaperRules.Refusal.NONE, DewReaperRules.refusal(true, 100f, 100f));
        assertEquals(DewReaperRules.Refusal.NONE, DewReaperRules.refusal(true, 250f, 100f));
        assertEquals(DewReaperRules.Refusal.NO_POWER, DewReaperRules.refusal(true, 99.9f, 100f));
        assertEquals(DewReaperRules.Refusal.NO_POWER, DewReaperRules.refusal(true, 0f, 100f));
        // Without the Water mod there is no flask to fill: that comes first, whatever the power.
        assertEquals(DewReaperRules.Refusal.WATER_MOD_MISSING, DewReaperRules.refusal(false, 0f, 100f));
        assertEquals(DewReaperRules.Refusal.WATER_MOD_MISSING, DewReaperRules.refusal(false, 100f, 100f));
        // The power cost is data.
        assertEquals(DewReaperRules.Refusal.NONE, DewReaperRules.refusal(true, 30f, 25f));
        assertEquals(DewReaperRules.Refusal.NO_POWER, DewReaperRules.refusal(true, 30f, 31f));
    }

    @Test
    void aPrimroseGivesMoreWaterThanTheAir() {
        assertEquals(50.0, DewReaperRules.units(DewReaperRules.Mode.PRIMROSE, 50, 1), 0.0);
        assertEquals(1.0, DewReaperRules.units(DewReaperRules.Mode.AIR, 50, 1), 0.0);
    }

    @Test
    void theFlaskIsSearchedInHotbarThenStorageThenBackpack() {
        double cap = 500;
        // -1: not a flask. 500: a full flask.
        double[][] all = {{-1, 500, -1}, {-1, -1, 12, 3}, {7}};
        assertArrayEquals(new int[] {1, 2}, DewReaperRules.firstWithRoom(all, cap), "a full hotbar flask is skipped, storage is next");
        double[][] hotbar = {{-1, 20, 40}, {5}, {5}};
        assertArrayEquals(new int[] {0, 1}, DewReaperRules.firstWithRoom(hotbar, cap), "the first slot in a place wins");
        double[][] backpackOnly = {{-1, -1}, {-1, 500}, {0}};
        assertArrayEquals(new int[] {2, 0}, DewReaperRules.firstWithRoom(backpackOnly, cap), "an empty flask has room");
        assertNull(DewReaperRules.firstWithRoom(new double[][] {{500, -1}, {500}, {}}, cap));
        assertNull(DewReaperRules.firstWithRoom(new double[][] {{}, {}, {}}, cap));
        assertNull(DewReaperRules.firstWithRoom(new double[0][], cap));
    }

    @Test
    void theAmountIsClampedToTheCapacity() {
        assertEquals(1.0, DewReaperRules.filled(0, 1, 500), 1e-9);
        assertEquals(500.0, DewReaperRules.filled(499.5, 1, 500), 1e-9, "never above the capacity");
        assertEquals(500.0, DewReaperRules.filled(470, 50, 500), 1e-9, "50 into a flask with 30 room: 20 are lost");
        assertEquals(30.0, DewReaperRules.filled(470, 50, 500) - 470, 1e-9);
        assertEquals(500.0, DewReaperRules.filled(500, 1, 500), 1e-9);
        assertEquals(0.0, DewReaperRules.filled(0.5, -5, 500), 1e-9, "never below 0");
        assertTrue(DewReaperRules.filled(10, 0, 500) == 10);
    }

    @Test
    void theAssetsCarryTheDefaultsAndTheDependencyIsOptional() throws IOException {
        String gate = resource("/Server/Item/Interactions/Equipment_of_Arrakis/Arrakis_Dew_Reaper_Gate.json");
        for (String text : new String[] {"\"Range\": 3", "\"PowerCost\": 100", "\"PrimroseBlockId\": \"Arrakis_Primrose\"",
                "\"PrimroseWater\": 50", "\"AirWaterUnits\": 1", "\"ShowMessages\": true"}) {
            assertTrue(gate.contains(text), text);
        }
        String root = resource("/Server/Item/RootInteractions/Equipment_of_Arrakis/Arrakis_Root_Dew_Reaper.json");
        int g = root.indexOf("Arrakis_Dew_Reaper_Gate");
        int s = root.indexOf("Arrakis_Dew_Reaper_Swing");
        int f = root.indexOf("Arrakis_Dew_Reaper_Finish");
        assertTrue(g > 0 && g < s && s < f, "gate, swing, finish");
        assertTrue(root.contains("\"RequireNewClick\": true"));
        String swing = resource("/Server/Item/Interactions/Equipment_of_Arrakis/Arrakis_Dew_Reaper_Swing.json");
        assertTrue(swing.contains("\"RunTime\": 2.08333") && swing.contains("\"HorizontalSpeedMultiplier\": 0.6")
                && swing.contains("Arrakis_Slow_Swing"), "the swing is as it was");
        String hum = resource("/Server/Entity/Effects/Equipment_of_Arrakis/Arrakis_Dew_Reaper_Active.json");
        assertTrue(hum.contains("SFX_Arrakis_Compactor_Active\"") && hum.contains("SFX_Arrakis_Compactor_Active_Local")
                && hum.contains("\"Duration\": 2.1"));
        String manifest = resource("/manifest.json");
        int optional = manifest.indexOf("OptionalDependencies");
        int water = manifest.indexOf("Water of Arrakis");
        assertTrue(optional > 0 && water > optional, "Water is an optional dependency");
        assertFalse(manifest.substring(0, optional).contains("Water of Arrakis"), "and not a required one");
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = DewReaperRulesTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
