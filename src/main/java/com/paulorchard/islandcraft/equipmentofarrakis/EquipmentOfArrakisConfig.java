package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.paulorchard.islandcraft.equipmentofarrakis.catalog.CatalogConfig;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreConfig;
import com.paulorchard.islandcraft.equipmentofarrakis.power.PowerConfig;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every tunable of the mod, read from Equipment_of_Arrakis.json in the plugin's data folder; the defaults are in code and a
 * missing file is written with them. One object per module, plus the list of enabled modules.
 *
 * <pre>
 * {
 *   "Modules": ["core", "power", "catalog"],
 *   "Core": { "LogSelfCheckAtBoot": true },
 *   "Power": { "LogStatDefinition": true },
 *   "Catalog": { }
 * }
 * </pre>
 */
public class EquipmentOfArrakisConfig {

    public static final String FILE_NAME = "Equipment_of_Arrakis";

    private String[] modules = {"core", "power", "catalog"};
    private CoreConfig core = new CoreConfig();
    private PowerConfig power = new PowerConfig();
    private CatalogConfig catalog = new CatalogConfig();
    { modules = java.util.stream.Stream.concat(java.util.Arrays.stream(modules), java.util.stream.Stream.of("experiments")).toArray(String[]::new); } // EXPERIMENT: remove with the experiments module
    private com.paulorchard.islandcraft.equipmentofarrakis.experiments.ExperimentsConfig experiments = new com.paulorchard.islandcraft.equipmentofarrakis.experiments.ExperimentsConfig(); // EXPERIMENT: remove with the experiments module

    /** The ids of the modules to load, lower case. The core module is always loaded whatever this says. */
    public Set<String> enabledModules() {
        return Arrays.stream(modules).map(m -> m.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    public CoreConfig core() {
        return core;
    }

    public PowerConfig power() {
        return power;
    }

    public CatalogConfig catalog() {
        return catalog;
    }

    public com.paulorchard.islandcraft.equipmentofarrakis.experiments.ExperimentsConfig experiments() { // EXPERIMENT: remove with the experiments module
        return experiments; // EXPERIMENT: remove with the experiments module
    } // EXPERIMENT: remove with the experiments module

    /** The settings object of a module, by module id. */
    public Object section(String moduleId) {
        return switch (moduleId) {
            case "core" -> core;
            case "power" -> power;
            case "catalog" -> catalog;
            case "experiments" -> experiments; // EXPERIMENT: remove with the experiments module
            default -> null;
        };
    }

    public static final BuilderCodec<EquipmentOfArrakisConfig> CODEC = BuilderCodec
            .builder(EquipmentOfArrakisConfig.class, EquipmentOfArrakisConfig::new)
            .documentation("Settings of IslandCraft - Equipment of Arrakis.")
            .append(new KeyedCodec<>("Modules", Codec.STRING_ARRAY, false), (c, v) -> c.modules = v, c -> c.modules)
            .documentation("The modules to load. Remove one to switch it off without a rebuild. The core module always loads.")
            .add()
            .append(new KeyedCodec<>("Core", CoreConfig.CODEC, false), (c, v) -> c.core = v, c -> c.core)
            .documentation("Settings of the core module.")
            .add()
            .append(new KeyedCodec<>("Power", PowerConfig.CODEC, false), (c, v) -> c.power = v, c -> c.power)
            .documentation("Settings of the power module.")
            .add()
            .append(new KeyedCodec<>("Catalog", CatalogConfig.CODEC, false), (c, v) -> c.catalog = v, c -> c.catalog)
            .documentation("Settings of the catalog module.")
            .add()
            .append(new KeyedCodec<>("Experiments", com.paulorchard.islandcraft.equipmentofarrakis.experiments.ExperimentsConfig.CODEC, false), (c, v) -> c.experiments = v, c -> c.experiments) // EXPERIMENT: remove with the experiments module
            .documentation("Settings of the experiments module (throw-away experiments).") // EXPERIMENT: remove with the experiments module
            .add() // EXPERIMENT: remove with the experiments module
            .build();
}
