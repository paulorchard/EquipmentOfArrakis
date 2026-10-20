package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.paulorchard.islandcraft.equipmentofarrakis.EquipmentOfArrakisConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The suspensor settings and files: defaults, the module in the default list, the hum effect, the language keys. */
class SuspensorAssetsTest {

    private static String text(String path) throws IOException {
        try (InputStream in = SuspensorAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theDefaultsAreTheOnesTheSpecAsks() {
        SuspensorConfig c = new SuspensorConfig();
        assertEquals(1.0, c.mk3().horizontalSpeed());
        assertEquals(1.0, c.mk3().verticalSpeed());
        assertEquals(1.0, c.mk2().horizontalSpeed());
        assertEquals(0.0, c.mk2().verticalSpeed());
        assertEquals(1.0, c.manaPerSecond());
        assertEquals(5.0, c.resumeMana());
        assertEquals(4.0, c.mk1().descentSpeed());
        assertEquals(12.0, c.mk1().speedThreshold());
        assertEquals(2, c.mk1().speedTicks());
        assertTrue(c.mk1().listed());
        assertEquals(BeltMachine.Way.FLYGLIDE, c.mk1().way());
        assertEquals(0.0, c.mk1().manaPerSecond());
    }

    @Test
    void liveTuningChangesOneMarkOnly() {
        SuspensorConfig c = new SuspensorConfig();
        c.of(BeltMachine.Mark.III).set(2.0, 3.0);
        assertEquals(2.0, c.mk3().horizontalSpeed());
        assertEquals(3.0, c.mk3().verticalSpeed());
        assertEquals(0.0, c.mk2().verticalSpeed());
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
    void theLanguageKeysExistAndNoLabKeysRemain() throws IOException {
        String lang = text("/Server/Languages/en-US/server.lang");
        for (String key : new String[] {"customUI.arrakisPicker.suspensor3 = Suspensor Belt Mk III", "customUI.arrakisPicker.suspensor2 = Suspensor Belt Mk II",
                "arrakis.suspensor.ready3 = ", "arrakis.suspensor.ready2 = ", "arrakis.suspensor.needsMana = The belt needs Mana.",
                "arrakis.suspensor.creative = ", "commands.arrakis.suspensor.state.desc", "commands.arrakis.suspensor.set.desc",
                "commands.arrakis.suspensor.off.desc", "commands.arrakis.suspensor.desc"}) {
            assertTrue(lang.contains(key), "missing " + key);
        }
        assertFalse(lang.contains("Arrakis_Lab_"));
        assertFalse(lang.contains("commands.arrakis.lab."));
    }
}
