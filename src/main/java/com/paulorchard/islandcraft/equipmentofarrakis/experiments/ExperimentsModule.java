// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
package com.paulorchard.islandcraft.equipmentofarrakis.experiments;

import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSpawner;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;

import com.hypixel.hytale.server.core.asset.type.model.config.ModelParticle;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import org.bson.BsonDocument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * The throw-away look-and-feel experiments: Experiment 1, the Flame Cutter (a rock-cutting beam made from the Scrap flamethrower).
 * Everything of it carries the word Experiments (package, asset folders) and the prefix Arrakis_Exp_ / Exp, so that one search
 * finds it and deleting it is the steps in EXPERIMENT_LOG.md. The module registers the one interaction type the experiment
 * needs; the item, chain, particles and texture are asset data.
 *
 * <p>Switching the module off in the config must leave nothing of the experiment behind, but the asset pack loads every asset
 * whatever the config says, and one asset with an interaction type nobody registered stops the whole server ("Mod failed to
 * load"). So the constructor, which runs in every case, registers the type and a boot listener; when {@link #setup} did not run by
 * the time the server has booted, the listener removes the Arrakis_Exp_ assets again, before any player can join.
 */
public final class ExperimentsModule implements EquipmentModule {

    public static final String ID = "experiments";
    public static final String FLAME_CUTTER = "Arrakis_Exp_Flame_Cutter";
    public static final String FLAME_PRIMARY = "Arrakis_Exp_Flame_Primary";
    public static final String FLAME_BURN = "Arrakis_Exp_Flame_Burn";
    public static final String FLAME_BEAM = "Arrakis_Exp_Flame_Beam";
    public static final String FLAME_FOCUS ="Arrakis_Exp_Flame_Focus";
    public static final String FLAME_TEXTURE = "Items/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Cutter_Texture.png";

    private static HytaleLogger logger;
    /** True once setup ran, which only happens when the config lists this module. */
    private static boolean active;
    private ExperimentsConfig config = new ExperimentsConfig();

    public ExperimentsModule(JavaPlugin plugin) {
        logger = plugin.getLogger();
        plugin.getCodecRegistry(Interaction.CODEC).register(ExpCutterTickInteraction.TYPE, ExpCutterTickInteraction.class,
                ExpCutterTickInteraction.CODEC);
        plugin.getCodecRegistry(Interaction.CODEC).register(ExpBeamSpawnInteraction.TYPE, ExpBeamSpawnInteraction.class,
                ExpBeamSpawnInteraction.CODEC);
        plugin.getEventRegistry().register(BootEvent.class, event -> {
            if (!active) {
                dropAssets();
            }
        });
    }

    private static volatile ExpCalibration starting = ExpCalibration.defaults();

    /** The numbers a player starts the calibration with: the saved file when there is one, else the vanilla Stoneskin placements. */
    static ExpCalibration startingCalibration() {
        return starting;
    }

    private static ExpCalibration loadStarting(Path data) {
        try {
            return ExpCalibration.fromJson(Files.readString(data.resolve(ExpCalCommands.FILE), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return ExpCalibration.defaults();
        }
    }

    /** How many packet units one degree of a JSON RotationOffset is: decode a ModelParticle with Yaw 180 through the engine's own codec. */
    private static double probeAngleUnit() {
        try {
            ModelParticle probe = ModelParticle.CODEC.decode(BsonDocument.parse("{\"SystemId\": \"Arrakis_Exp_Cal_Dot\", \"RotationOffset\": {\"Yaw\": 180}}"));
            return probe.getRotationOffset().yaw / 180d;
        } catch (RuntimeException e) {
            logger.at(Level.WARNING).log("Experiments: could not probe the model particle angle unit (%s); assuming degrees", e);
            return 1;
        }
    }

    static HytaleLogger logger() {
        return logger;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void setup(ModuleContext ctx) {
        active = true;
        config = ctx.settings(ID, ExperimentsConfig.class);
        Path data = ctx.plugin().getDataDirectory();
        starting = loadStarting(data);
        ctx.command(ExpCalCommands.build(data));
        ctx.events().registerGlobal(PlayerDisconnectEvent.class, event -> ExpCalibrationRig.forget(event.getPlayerRef().getUuid()));
    }

    @Override
    public void start(ModuleContext ctx) {
        ExpCalibrationRig.angleUnit = probeAngleUnit();
        if (config.logSelfCheck()) {
            for (String line : selfCheck()) {
                ctx.logger().at(line.contains("PROBLEM") ? Level.WARNING : Level.INFO).log("Experiments: %s", line);
            }
        }
    }

    @Override
    public List<String> selfCheck() {
        List<String> lines = new ArrayList<>();
        Item item = Item.getAssetMap().getAsset(FLAME_CUTTER);
        if (item == null) {
            lines.add("PROBLEM the item " + FLAME_CUTTER + " is not loaded");
        } else {
            String[] categories = item.getCategories();
            boolean inTab = categories != null && Arrays.asList(categories).contains("IslandCraft.Tools");
            lines.add((inTab ? "" : "PROBLEM ") + "item " + FLAME_CUTTER + " loaded, categories " + Arrays.toString(categories)
                    + ", animations " + item.getPlayerAnimationsId() + ", primary " + item.getInteractions());
            lines.add(item.getLight() == null && item.getSoundEventId() == null
                    ? "the item has no light and no sound of its own (the pilot flame is gone)"
                    : "PROBLEM the item still has a light or a sound: light " + item.getLight() + ", sound " + item.getSoundEventId());
            lines.add(common(item.getTexture()) + " (texture copy)");
            lines.add(common(item.getModel()) + " (vanilla model, by path)");
        }
        lines.add(exists(RootInteraction.getAssetMap().getAsset(FLAME_PRIMARY), "root interaction " + FLAME_PRIMARY));
        lines.add(exists(Interaction.getAssetMap().getAsset("Arrakis_Exp_Cutter_Tick"), "interaction Arrakis_Exp_Cutter_Tick"));
        lines.add(exists(Interaction.getAssetMap().getAsset(FLAME_BURN), "interaction " + FLAME_BURN));
        ParticleSystem system = ParticleSystem.getAssetMap().getAsset(FLAME_FOCUS);
        lines.add(exists(system, "particle system " + FLAME_FOCUS) + (system == null ? "" : ", " + system.getSpawners().length + " spawners"));
        lines.add(exists(ParticleSpawner.getAssetMap().getAsset(FLAME_FOCUS + "_Beam"), "particle spawner " + FLAME_FOCUS + "_Beam"));
        lines.add(Power.get() == null ? "PROBLEM the power service is missing: the beam cannot take Mana" : "power service present");
        Item tool = Item.getAssetMap().getAsset("Tool_Pickaxe_Mithril");
        lines.add(tool == null || tool.getTool() == null ? "PROBLEM the stand-in tool Tool_Pickaxe_Mithril is missing"
                : "stand-in tool Tool_Pickaxe_Mithril: " + tool.getTool().getSpecs().length + " gather specs");
        beamChecks(lines);
        return lines;
    }

    /** Experiment 2, the Flame Beam Cutter: the Flame Cutter with the Stoneskin laser. */
    private static void beamChecks(List<String> lines) {
        Item item = Item.getAssetMap().getAsset(FLAME_BEAM);
        if (item == null) {
            lines.add("PROBLEM the item " + FLAME_BEAM + " is not loaded");
        } else {
            String[] categories = item.getCategories();
            lines.add((categories != null && Arrays.asList(categories).contains("IslandCraft.Tools") ? "" : "PROBLEM ") + "item " + FLAME_BEAM
                    + " loaded, categories " + Arrays.toString(categories) + ", primary " + item.getInteractions());
            lines.add(item.getLight() == null && item.getSoundEventId() == null ? "the item has no light and no sound of its own"
                    : "PROBLEM the item has a light or a sound");
        }
        lines.add(exists(RootInteraction.getAssetMap().getAsset("Arrakis_Exp_Flame_Beam_Primary"), "root interaction Arrakis_Exp_Flame_Beam_Primary"));
        lines.add(exists(Interaction.getAssetMap().getAsset("Arrakis_Exp_Flame_Beam_Spawn"), "interaction Arrakis_Exp_Flame_Beam_Spawn"));
        lines.add(exists(Interaction.getAssetMap().getAsset("Arrakis_Exp_Flame_Beam_Pace"), "interaction Arrakis_Exp_Flame_Beam_Pace"));
        lines.add(exists(Interaction.getAssetMap().getAsset("Arrakis_Exp_Flame_Beam_Tick"), "interaction Arrakis_Exp_Flame_Beam_Tick"));
        lines.add(exists(Interaction.getAssetMap().getAsset("Arrakis_Exp_Flame_Tick"), "interaction Arrakis_Exp_Flame_Tick"));
        lines.add(exists(Interaction.getAssetMap().getAsset("Arrakis_Exp_Flame_Spawn"), "interaction Arrakis_Exp_Flame_Spawn"));
        for (int level = 0; level < ExpBeamSpawnInteraction.GLOW_LEVELS; level++) {
            String id = ExpBeamSpawnInteraction.GLOW_PREFIX + level;
            lines.add(exists(ParticleSystem.getAssetMap().getAsset(id), "glow particle system " + id));
        }
        for (String id : new String[] {ExpCalibrationRig.DOT, ExpCalibrationRig.MARK, ExpCalibrationRig.RULER, ExpCalibrationRig.STREAM}) {
            lines.add(exists(ParticleSystem.getAssetMap().getAsset(id), "calibration particle system " + id));
        }
        lines.add(ParticleSystem.getAssetMap().getAsset(ExpCalibrationRig.NATURE_BEAM) == null
                ? "PROBLEM the vanilla particle system NatureBeam is gone (it lives in a _Test folder and an update may remove it)"
                : "vanilla particle system NatureBeam present");
        lines.add("model particle angle unit: " + ExpCalibrationRig.angleUnit + " per degree (probed from the engine's own codec)");
    }

    /** Removes every Arrakis_Exp_ asset the pack loaded: the module is switched off. */
    private static void dropAssets() {
        int dropped = drop(Item.getAssetStore(), Item.getAssetMap().getAssetMap())
                + drop(RootInteraction.getAssetStore(), RootInteraction.getAssetMap().getAssetMap())
                + drop(Interaction.getAssetStore(), Interaction.getAssetMap().getAssetMap())
                + drop(ParticleSystem.getAssetStore(), ParticleSystem.getAssetMap().getAssetMap())
                + drop(ParticleSpawner.getAssetStore(), ParticleSpawner.getAssetMap().getAssetMap());
        logger.at(Level.INFO).log("Experiments: switched off in the config; removed %s experiment assets (item, interactions, particles). "
                + "Still there: %s", dropped, Item.getAssetMap().getAsset(FLAME_CUTTER) == null ? "nothing of the item" : "PROBLEM the item");
    }

    private static <T extends JsonAssetWithMap<String, M>, M extends AssetMap<String, T>> int drop(AssetStore<String, T, M> store,
            Map<String, T> loaded) {
        List<String> ids = loaded.keySet().stream().filter(id -> id.startsWith("Arrakis_Exp_")).toList();
        if (!ids.isEmpty()) {
            store.removeAssets(ids);
        }
        return ids.size();
    }

    private static String exists(Object asset, String what) {
        return (asset == null ? "PROBLEM " : "") + what + (asset == null ? " is not loaded" : " loaded");
    }

    private static String common(String path) {
        if (path == null) {
            return "PROBLEM no path";
        }
        boolean found = CommonAssetRegistry.hasCommonAsset(path) || CommonAssetRegistry.hasCommonAsset("Common/" + path);
        return (found ? "" : "PROBLEM not found: ") + path;
    }
}
