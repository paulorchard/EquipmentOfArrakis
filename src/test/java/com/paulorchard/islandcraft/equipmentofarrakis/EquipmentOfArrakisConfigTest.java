package com.paulorchard.islandcraft.equipmentofarrakis;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentOfArrakisConfigTest {

    @Test
    void defaultsEnableAllThreeModulesAndSurviveARoundTrip() {
        EquipmentOfArrakisConfig defaults = new EquipmentOfArrakisConfig();
        assertEquals(Set.of("core", "power", "catalog"), defaults.enabledModules());
        BsonValue written = EquipmentOfArrakisConfig.CODEC.encode(defaults);
        EquipmentOfArrakisConfig read = EquipmentOfArrakisConfig.CODEC.decode(written);
        assertEquals(written, EquipmentOfArrakisConfig.CODEC.encode(read), "writing, reading and writing again changes nothing");
        assertEquals(defaults.enabledModules(), read.enabledModules());
        BsonDocument document = written.asDocument();
        for (String key : new String[] {"Modules", "Core", "Power", "Catalog"}) {
            assertTrue(document.containsKey(key), "the written file has a " + key + " section");
        }
    }

    @Test
    void anEmptyFileGivesTheDefaults() {
        EquipmentOfArrakisConfig read = EquipmentOfArrakisConfig.CODEC.decode(new BsonDocument());
        assertEquals(Set.of("core", "power", "catalog"), read.enabledModules());
        assertNotNull(read.power());
        assertTrue(read.power().logStatDefinition());
    }

    @Test
    void aModuleCanBeLeftOutOfTheList() {
        BsonDocument file = BsonDocument.parse("{\"Modules\": [\"core\", \"Power\"]}");
        EquipmentOfArrakisConfig read = EquipmentOfArrakisConfig.CODEC.decode(file);
        assertEquals(Set.of("core", "power"), read.enabledModules(), "ids are compared without case");
    }

    @Test
    void everyModuleHasASection() {
        EquipmentOfArrakisConfig config = new EquipmentOfArrakisConfig();
        for (String id : new String[] {"core", "power", "catalog"}) {
            assertNotNull(config.section(id), id);
        }
    }
}
