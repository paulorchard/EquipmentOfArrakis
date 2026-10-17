package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import com.paulorchard.islandcraft.equipmentofarrakis.catalog.CatalogModule;
import com.paulorchard.islandcraft.equipmentofarrakis.bloodbag.BloodBagModule;
import com.paulorchard.islandcraft.equipmentofarrakis.compactor.CompactorModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CommandRoot;
import com.paulorchard.islandcraft.equipmentofarrakis.cutter.CutterModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;
import com.paulorchard.islandcraft.equipmentofarrakis.core.Modules;
import com.paulorchard.islandcraft.equipmentofarrakis.picker.PickerModule;
import com.paulorchard.islandcraft.equipmentofarrakis.extractor.ExtractorModule;
import com.paulorchard.islandcraft.equipmentofarrakis.harvester.HarvesterModule;
import com.paulorchard.islandcraft.equipmentofarrakis.light.LightModule;
import com.paulorchard.islandcraft.equipmentofarrakis.litrejon.LitrejonModule;
import com.paulorchard.islandcraft.equipmentofarrakis.placeholders.PlaceholdersModule;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;
import com.paulorchard.islandcraft.equipmentofarrakis.shield.ShieldModule;
import com.paulorchard.islandcraft.equipmentofarrakis.power.PowerModule;

import java.nio.file.Files;
import java.util.List;
import java.util.logging.Level;

/**
 * IslandCraft - Equipment of Arrakis (v2). The plugin only loads modules: this class is the one place that lists them, in build
 * order. A module that fails to set up is logged and the others go on; the config's Modules list switches modules off.
 */
public class EquipmentOfArrakisPlugin extends JavaPlugin {

    private static EquipmentOfArrakisPlugin instance;

    // Must be created before setup(); the server loads it from disk in between.
    private final Config<EquipmentOfArrakisConfig> config =
            withConfig(EquipmentOfArrakisConfig.FILE_NAME, EquipmentOfArrakisConfig.CODEC);

    private final Modules modules = new Modules();
    private final CommandRoot root = new CommandRoot();
    private ModuleContext context;

    public EquipmentOfArrakisPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static EquipmentOfArrakisPlugin get() {
        return instance;
    }

    /** The power service (the vanilla Mana stat); null when the power module is off or failed. World thread only. */
    public Power power() {
        return Power.get();
    }

    public EquipmentOfArrakisConfig settings() {
        return config.get();
    }

    @Override
    protected void setup() {
        writeConfig();
        EquipmentOfArrakisConfig settings = config.get();
        context = new ModuleContext(this, root, settings::section);
        List<EquipmentModule> all = List.of(new CoreModule(modules), new PowerModule(), new CatalogModule(),
                new PickerModule(this),
                new CutterModule(this),
                new CompactorModule(this),
                new LitrejonModule(this),
                new HarvesterModule(this),
                new BloodBagModule(this),
                new ExtractorModule(this),
                new PlaceholdersModule(this),
                new LightModule(),
                new ShieldModule(this));
        all = java.util.stream.Stream.concat(all.stream(), java.util.stream.Stream.of(new com.paulorchard.islandcraft.equipmentofarrakis.experiments.ExperimentsModule(this))).toList(); // EXPERIMENT: remove with the experiments module
        modules.setupAll(all, settings.enabledModules(), context);
        getCommandRegistry().registerCommand(root.command());
    }

    /** Assets are loaded by now: let the modules verify, then print every self-check. */
    @Override
    protected void start() {
        modules.startAll(context);
        if (config.get().core().logSelfCheckAtBoot()) {
            for (String line : modules.selfChecks()) {
                getLogger().at(Modules.isProblem(line) ? Level.WARNING : Level.INFO).log("Self-check: %s", line);
            }
        }
    }

    /** Writes the file with every setting (the defaults for any that were missing), so there is something to edit. */
    private void writeConfig() {
        try {
            Files.createDirectories(getDataDirectory());
            config.save().join();
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("Could not write the config to %s", getDataDirectory());
        }
    }
}
