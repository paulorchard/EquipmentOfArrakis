package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.paulorchard.islandcraft.equipmentofarrakis.bloodbag.BloodBagConfig;
import com.paulorchard.islandcraft.equipmentofarrakis.catalog.CatalogConfig;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreConfig;
import com.paulorchard.islandcraft.equipmentofarrakis.light.LightConfig;
import com.paulorchard.islandcraft.equipmentofarrakis.litrejon.LitrejonConfig;
import com.paulorchard.islandcraft.equipmentofarrakis.power.PowerConfig;
import com.paulorchard.islandcraft.equipmentofarrakis.shield.ShieldConfig;

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
 *   "Modules": ["core", "power", "catalog", "picker", "cutter", "compactor", "litrejon", "harvester", "bloodbag", "extractor", "placeholders", "light", "shield", "weapons", "thumper", "suspensor", "hud"],
 *   "Core": { "LogSelfCheckAtBoot": true },
 *   "Power": { "LogStatDefinition": true },
 *   "Catalog": { }
 * }
 * </pre>
 */
public class EquipmentOfArrakisConfig {

    public static final String FILE_NAME = "Equipment_of_Arrakis";

    private String[] modules = {"core", "power", "catalog", "picker", "cutter", "compactor", "litrejon", "harvester", "bloodbag", "extractor", "placeholders", "light", "shield", "weapons", "thumper", "suspensor",
            "hud"};
    private CoreConfig core = new CoreConfig();
    private PowerConfig power = new PowerConfig();
    private CatalogConfig catalog = new CatalogConfig();
    private LitrejonConfig litrejon = new LitrejonConfig();
    private BloodBagConfig bloodbag = new BloodBagConfig();
    private com.paulorchard.islandcraft.equipmentofarrakis.extractor.ExtractorConfig extractor = new com.paulorchard.islandcraft.equipmentofarrakis.extractor.ExtractorConfig();
    private LightConfig light = new LightConfig();
    private ShieldConfig shield = new ShieldConfig();
    private com.paulorchard.islandcraft.equipmentofarrakis.weapons.WeaponsConfig weapons = new com.paulorchard.islandcraft.equipmentofarrakis.weapons.WeaponsConfig();
    private com.paulorchard.islandcraft.equipmentofarrakis.thumper.ThumperConfig thumper = new com.paulorchard.islandcraft.equipmentofarrakis.thumper.ThumperConfig();
    private com.paulorchard.islandcraft.equipmentofarrakis.suspensor.SuspensorConfig suspensor = new com.paulorchard.islandcraft.equipmentofarrakis.suspensor.SuspensorConfig();
    private com.paulorchard.islandcraft.equipmentofarrakis.hud.HudConfig hud = new com.paulorchard.islandcraft.equipmentofarrakis.hud.HudConfig();

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

    public LitrejonConfig litrejon() {
        return litrejon;
    }

    public BloodBagConfig bloodbag() {
        return bloodbag;
    }

    public com.paulorchard.islandcraft.equipmentofarrakis.extractor.ExtractorConfig extractor() {
        return extractor;
    }

    public LightConfig light() {
        return light;
    }

    public ShieldConfig shield() {
        return shield;
    }

    public com.paulorchard.islandcraft.equipmentofarrakis.weapons.WeaponsConfig weapons() {
        return weapons;
    }

    public com.paulorchard.islandcraft.equipmentofarrakis.thumper.ThumperConfig thumper() {
        return thumper;
    }


    public com.paulorchard.islandcraft.equipmentofarrakis.suspensor.SuspensorConfig suspensor() {
        return suspensor;
    }

    public com.paulorchard.islandcraft.equipmentofarrakis.hud.HudConfig hud() {
        return hud;
    }


    /** The settings object of a module, by module id. */
    public Object section(String moduleId) {
        return switch (moduleId) {
            case "core" -> core;
            case "power" -> power;
            case "catalog" -> catalog;
            case "litrejon" -> litrejon;
            case "bloodbag" -> bloodbag;
            case "extractor" -> extractor;
            case "light" -> light;
            case "shield" -> shield;
            case "weapons" -> weapons;
            case "thumper" -> thumper;
            case "suspensor" -> suspensor;
            case "hud" -> hud;
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
            .append(new KeyedCodec<>("Litrejon", LitrejonConfig.CODEC, false), (c, v) -> c.litrejon = v, c -> c.litrejon)
            .documentation("Settings of the litrejon module.")
            .add()
            .append(new KeyedCodec<>("Bloodbag", BloodBagConfig.CODEC, false), (c, v) -> c.bloodbag = v, c -> c.bloodbag)
            .documentation("Settings of the bloodbag module.")
            .add()
            .append(new KeyedCodec<>("Extractor", com.paulorchard.islandcraft.equipmentofarrakis.extractor.ExtractorConfig.CODEC, false), (c, v) -> c.extractor = v, c -> c.extractor)
            .documentation("Settings of the extractor module (how long the bodies of killed mobs stay).")
            .add()
            .append(new KeyedCodec<>("Light", LightConfig.CODEC, false), (c, v) -> c.light = v, c -> c.light)
            .documentation("Settings of the light module (Personal Light).")
            .add()
            .append(new KeyedCodec<>("Shield", ShieldConfig.CODEC, false), (c, v) -> c.shield = v, c -> c.shield)
            .documentation("Settings of the shield module (Holtzman Shield).")
            .add()
            .append(new KeyedCodec<>("Weapons", com.paulorchard.islandcraft.equipmentofarrakis.weapons.WeaponsConfig.CODEC, false), (c, v) -> c.weapons = v, c -> c.weapons)
            .documentation("Settings of the weapons module (dart range).")
            .add()
            .append(new KeyedCodec<>("Thumper", com.paulorchard.islandcraft.equipmentofarrakis.thumper.ThumperConfig.CODEC, false), (c, v) -> c.thumper = v, c -> c.thumper)
            .documentation("Settings of the thumper module (the sand-planted beat that draws the worm).")
            .add()
            .append(new KeyedCodec<>("Suspensor", com.paulorchard.islandcraft.equipmentofarrakis.suspensor.SuspensorConfig.CODEC, false), (c, v) -> c.suspensor = v, c -> c.suspensor)
            .documentation("Settings of the suspensor module (the Suspensor Belt Mk II and Mk III toggles).")
            .add()
            .append(new KeyedCodec<>("Hud", com.paulorchard.islandcraft.equipmentofarrakis.hud.HudConfig.CODEC, false), (c, v) -> c.hud = v, c -> c.hud)
            .documentation("Settings of the hud module (the four bars above the hotbar).")
            .add()
            .build();
}
