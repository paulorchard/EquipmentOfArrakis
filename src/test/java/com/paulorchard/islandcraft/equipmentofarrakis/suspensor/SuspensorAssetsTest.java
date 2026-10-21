package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.paulorchard.islandcraft.equipmentofarrakis.EquipmentOfArrakisConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The suspensor settings and files: defaults, the module in the default list, the hum effect, the language keys. */
class SuspensorAssetsTest {

    private static String text(String path) throws IOException {
        try (InputStream in = SuspensorAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static SuspensorConfig read(String json) throws IOException {
        return SuspensorConfig.CODEC.decodeJson(RawJsonReader.fromJsonString(json), new ExtraInfo());
    }

    @Test
    void theDefaultsAreTheBeltsNewNumbers() {
        SuspensorConfig c = new SuspensorConfig();
        assertEquals(2.0, c.horizontalSpeed());
        assertEquals(2.0, c.verticalSpeed());
        assertEquals(10.0, c.manaPerSecond());
        assertEquals(5.0, c.resumeMana());
        assertTrue(c.hum());
    }

    @Test
    void aSavedFileKeepsItsNumbersAndOnlyAFreshFileGetsTheNewDefaults() throws IOException {
        // the numbers the 0.5 build saved stay (the defaults change fresh files only)
        SuspensorConfig saved = read("{\"HorizontalSpeed\":0.5,\"VerticalSpeed\":0.5,\"ManaPerSecond\":10.0,\"ResumeMana\":5.0,\"Hum\":true}");
        assertEquals(0.5, saved.horizontalSpeed());
        assertEquals(0.5, saved.verticalSpeed());
        assertEquals(10.0, saved.manaPerSecond());
        // a file with only some keys: the missing ones take the defaults
        SuspensorConfig partial = read("{\"HorizontalSpeed\":0.75}");
        assertEquals(0.75, partial.horizontalSpeed());
        assertEquals(2.0, partial.verticalSpeed());
        assertEquals(6.0, new SuspensorConfig().pushCap(), "TEST DEVICE: the push cap default");
        assertEquals(3.0, read("{\"PushCap\":3}").pushCap());
    }

    @Test
    void theTapGateSettingsHaveTheirDefaultsAreReadAndTunedLive() throws IOException {
        SuspensorConfig c = new SuspensorConfig();
        assertEquals(0.35, c.tapWindow());
        assertEquals(BeltMachine.GateMode.AFTER_JUMP, c.tapMode());
        assertEquals(1.2, c.reopenSeconds());
        assertEquals(0.35, read("{\"HorizontalSpeed\":0.5}").tapWindow(), "an old file without the keys gets the gate defaults");
        SuspensorConfig r = read("{\"TapWindow\":0.5,\"TapMode\":\"groundOpen\",\"ReopenSeconds\":2}");
        assertEquals(0.5, r.tapWindow());
        assertEquals(BeltMachine.GateMode.GROUND_OPEN, r.tapMode());
        assertEquals(2.0, r.reopenSeconds());
        c.setWindow(0, null, null);
        assertEquals(0.0, c.tapWindow());
        assertEquals(BeltMachine.GateMode.AFTER_JUMP, c.tapMode(), "no mode given: unchanged");
        c.setWindow(0.25, "GROUNDOPEN", 1.0);
        assertEquals(BeltMachine.GateMode.GROUND_OPEN, c.tapMode());
        assertEquals(1.0, c.reopenSeconds());
        assertTrue(SuspensorConfig.CODEC.encode(c, new ExtraInfo()).toString().contains("groundOpen"));
    }

    @Test
    void theNewKeysAreReadAsPlainTopLevelKeys() throws IOException {
        SuspensorConfig c = read("{\"HorizontalSpeed\":1.5,\"VerticalSpeed\":0.75,\"ManaPerSecond\":2,\"ResumeMana\":8,\"Hum\":false}");
        assertEquals(1.5, c.horizontalSpeed());
        assertEquals(0.75, c.verticalSpeed());
        assertEquals(2.0, c.manaPerSecond());
        assertEquals(8.0, c.resumeMana());
        assertFalse(c.hum());
    }

    @Test
    void anOldFileFromTheMarkBuildLoadsWithoutErrorAndTheNewDefaultsApply() throws IOException {
        // what the 0.3.x build saved: Mk3/Mk2/Mk1 sections, ManaPerSecond 1
        SuspensorConfig c = read("{\"Mk3\":{\"HorizontalSpeed\":1.0,\"VerticalSpeed\":1.0},\"Mk2\":{\"HorizontalSpeed\":1.0,\"VerticalSpeed\":0.0},"
                + "\"Mk1\":{\"Route\":\"glider\",\"GliderTrigger\":\"falling\",\"Signal\":\"pulse\"},\"ManaPerSecond\":1.0,\"ResumeMana\":5.0,\"Hum\":true}");
        assertEquals(2.0, c.horizontalSpeed(), "the old 1.0 speeds lived under Mk3 and are not carried over");
        assertEquals(2.0, c.verticalSpeed());
        assertEquals(10.0, c.manaPerSecond(), "the old 1 Mana is not carried over");
        assertEquals(5.0, c.resumeMana());
        // the very first build had a text placeholder under Mk1
        assertEquals(2.0, read("{\"Mk1\":\"experimental\",\"Mk2\":\"x\"}").horizontalSpeed());
    }

    @Test
    void savingAnOldFileWritesTheNewKeysOnly() throws IOException {
        SuspensorConfig c = read("{\"Mk3\":{\"HorizontalSpeed\":1.0,\"VerticalSpeed\":1.0},\"ManaPerSecond\":1.0}");
        String saved = SuspensorConfig.CODEC.encode(c, new ExtraInfo()).toString();
        assertFalse(saved.contains("Mk"), saved);
        assertTrue(saved.contains("HorizontalSpeed") && saved.contains("VerticalSpeed") && saved.contains("ManaPerSecond"), saved);
        SuspensorConfig again = read(saved.replace("Document", ""));
        assertEquals(10.0, again.manaPerSecond(), "the rewritten file keeps the new default");
    }

    @Test
    void liveTuningSetsTheNumbersAndKeepsTheCostWhenNoneIsGiven() {
        SuspensorConfig c = new SuspensorConfig();
        c.set(2.0, 3.0, null);
        assertEquals(2.0, c.horizontalSpeed());
        assertEquals(3.0, c.verticalSpeed());
        assertEquals(10.0, c.manaPerSecond());
        c.set(1.0, 1.0, 2.0);
        assertEquals(2.0, c.manaPerSecond());
    }

    @Test
    void theModuleIsInTheDefaultListAndHasAConfigSection() {
        EquipmentOfArrakisConfig config = new EquipmentOfArrakisConfig();
        assertTrue(config.enabledModules().contains("suspensor"));
        assertTrue(config.section("suspensor") instanceof SuspensorConfig);
        assertFalse(config.enabledModules().contains("lab"), "the lab is gone");
    }

    @Test
    void theHumEffectUsesTheVanillaLoopAndLapsesQuickly() throws IOException {
        JsonObject effect = JsonParser.parseString(text("/Server/Entity/Effects/Equipment_of_Arrakis/Suspensor/Arrakis_Suspensor_Hum.json")).getAsJsonObject();
        assertEquals("SFX_Ability_Wind_Strike_Imbued_Loop", effect.getAsJsonObject("ApplicationEffects").get("WorldSoundEventId").getAsString());
        assertEquals("Overwrite", effect.get("OverlapBehavior").getAsString());
        assertFalse(effect.get("Infinite").getAsBoolean());
        assertTrue(effect.get("Duration").getAsDouble() <= 0.5);
    }

    @Test
    void theLanguageKeysExistAndNoMarkOrLabKeysRemain() throws IOException {
        String lang = text("/Server/Languages/en-US/server.lang");
        for (String key : new String[] {"customUI.arrakisPicker.suspensor = Suspensor Belt", "arrakis.suspensor.ready = Suspensor Belt ready: double-tap Space to fly.",
                "arrakis.suspensor.needsMana = The belt needs Mana.", "arrakis.suspensor.creative = ", "commands.arrakis.suspensor.state.desc",
                "commands.arrakis.suspensor.set.desc", "commands.arrakis.suspensor.off.desc", "commands.arrakis.suspensor.window.desc", "commands.arrakis.suspensor.probe.desc", "commands.arrakis.suspensor.push.desc", "commands.arrakis.suspensor.desc"}) {
            assertTrue(lang.contains(key), "missing " + key);
        }
        assertFalse(lang.contains("Arrakis_Lab_"));
        assertFalse(lang.contains("commands.arrakis.lab."));
        for (String gone : new String[] {"Mk I", "Mk II", "Mk III", "Mk1", "Mk2", "Mk3", "Glider", "glider", "Arrakis_Suspensor_Falling", "suspensor1", "suspensor2", "suspensor3"}) {
            assertFalse(lang.contains(gone), "leftover in the language file: " + gone);
        }
    }

    @Test
    void theRemovedAssetsAreGone() {
        for (String path : new String[] {"/Server/Entity/Effects/Equipment_of_Arrakis/Suspensor/Arrakis_Suspensor_Falling.json",
                "/Server/Item/Items/Equipment_of_Arrakis/Suspensor/Arrakis_Suspensor_Glider.json"}) {
            assertNull(SuspensorAssetsTest.class.getResource(path), path);
        }
        assertNotNull(SuspensorAssetsTest.class.getResource("/Server/Entity/Effects/Equipment_of_Arrakis/Suspensor/Arrakis_Suspensor_Hum.json"));
    }

    @Test
    void theLegacyCleanupMatchesOnlyTheRemovedItem() {
        assertTrue(LegacyGlider.isIt("Arrakis_Suspensor_Glider"));
        assertFalse(LegacyGlider.isIt("Template_Glider"));
        assertFalse(LegacyGlider.isIt(null));
        assertFalse(LegacyGlider.isIt("Arrakis_Suspensor_Glider2"));
    }
}
