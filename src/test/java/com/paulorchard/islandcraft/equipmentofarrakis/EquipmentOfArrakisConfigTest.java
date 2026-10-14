package com.paulorchard.islandcraft.equipmentofarrakis;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentOfArrakisConfigTest {

    /** The modules every build has; later modules may add to the defaults. */
    private static final Set<String> BASE = Set.of("core", "power", "catalog");

    @Test
    void defaultsEnableTheBaseModulesAndSurviveARoundTrip() {
        EquipmentOfArrakisConfig defaults = new EquipmentOfArrakisConfig();
        assertTrue(defaults.enabledModules().containsAll(BASE), "the base modules are on by default");
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
        assertTrue(read.enabledModules().containsAll(BASE), "the base modules are on by default");
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
