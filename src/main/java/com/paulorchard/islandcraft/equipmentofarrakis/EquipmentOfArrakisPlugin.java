package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import java.util.logging.Level;

/**
 * IslandCraft - Equipment of Arrakis. Stage one: the four tools (Blood Extractor, Dew Reaper, Compactor, Cutter Ray), the Holtzman
 * Shield, the two spring guns (Maula Pistol, Dartgun), the Arrakis_Power and Arrakis_Ammo stats and the debug commands. The tools' models, animations and interactions are asset data;
 * the code here is the stat, the commands and the beam.
 */
public class EquipmentOfArrakisPlugin extends JavaPlugin {

    private static EquipmentOfArrakisPlugin instance;

    // Must be created before setup(); the server loads it from disk in between (the on-screen display settings).
    private final Config<HudSettings> hudConfig = withConfig(HudSettings.FILE_NAME, HudSettings.CODEC);
    private final Config<HoltzmanSettings> shieldConfig = withConfig(HoltzmanSettings.FILE_NAME, HoltzmanSettings.CODEC);
    private final Config<DartSettings> dartConfig = withConfig(DartSettings.FILE_NAME, DartSettings.CODEC);

    public EquipmentOfArrakisPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static EquipmentOfArrakisPlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        getCodecRegistry(Interaction.CODEC).register(CutterRayGateInteraction.TYPE, CutterRayGateInteraction.class,
                CutterRayGateInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register(DewReaperGateInteraction.TYPE, DewReaperGateInteraction.class,
                DewReaperGateInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register(DewReaperFinishInteraction.TYPE, DewReaperFinishInteraction.class,
                DewReaperFinishInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register(CompactorInteraction.TYPE, CompactorInteraction.class,
                CompactorInteraction.CODEC);
        getCodecRegistry(Interaction.CODEC).register(DartAmmoGateInteraction.TYPE, DartAmmoGateInteraction.class,
                DartAmmoGateInteraction.CODEC);
        DartFlight.type = getEntityStoreRegistry().registerComponent(DartFlight.class, DartFlight::new);
        ArrakisAmmo.bind(() -> dartConfig.get());
        ShieldCooldown.bind(() -> shieldConfig.get());
        getCommandRegistry().registerCommand(PowerCommands.build(() -> hudConfig.get(), () -> shieldConfig.get(), () -> dartConfig.get()));
        getEntityStoreRegistry().registerSystem(new PowerRechargeSystem());
        getEntityStoreRegistry().registerSystem(new AmmoRechargeSystem());
        getEntityStoreRegistry().registerSystem(new DartFlightSystem(() -> dartConfig.get()));
        getEntityStoreRegistry().registerSystem(new DartRangeSystem(() -> dartConfig.get()));
        getEntityStoreRegistry().registerSystem(new DartRangeDamageSystem(() -> dartConfig.get()));
        getEntityStoreRegistry().registerSystem(new CompactorSystem());
        getEntityStoreRegistry().registerSystem(new DewReaperSystem());
        getEntityStoreRegistry().registerSystem(new HoltzmanSystem(() -> shieldConfig.get()));
        getEntityStoreRegistry().registerSystem(new HoltzmanDamageSystem(() -> shieldConfig.get()));
        getEntityStoreRegistry().registerSystem(new ShieldShotLegacySystem());
        getEntityStoreRegistry().registerSystem(new ShieldShotSystem());
        getEntityStoreRegistry().registerSystem(new RangedWatch(() -> shieldConfig.get()));
        getEntityStoreRegistry().registerSystem(new DevInjectSystem());
        getEntityStoreRegistry().registerSystem(new HudSystem(() -> hudConfig.get()));
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            ArrakisPower.forget(event.getPlayerRef().getUuid());
            ArrakisAmmo.forget(event.getPlayerRef().getUuid());
            HoltzmanState.forget(event.getPlayerRef().getUuid());
            CompactorTrail.forget(event.getPlayerRef().getUuid());
            HudSystem.forget(event.getPlayerRef().getUuid());
            DewReaperState.forget(event.getPlayerRef().getUuid());
        });
    }

    /** Assets are loaded by now: say which of this mod's assets this game has. */
    @Override
    protected void start() {
        // Writes the file with every setting (the defaults for any that were missing), so there is something to edit.
        hudConfig.save();
        shieldConfig.save();
        dartConfig.save();
        StringBuilder missing = new StringBuilder();
        for (String id : new String[] {"Arrakis_Blood_Extractor", "Arrakis_Dew_Reaper", "Arrakis_Compactor",
                "Arrakis_Cutter_Ray", "Arrakis_Holtzman_Shield", "Arrakis_Maula_Pistol", "Arrakis_Dartgun"}) {
            if (Item.getAssetMap().getAsset(id) == null) {
                missing.append(' ').append(id);
            }
        }
        if (ArrakisPower.statIndex() == Integer.MIN_VALUE) {
            missing.append(' ').append(ArrakisPower.STAT_ID);
        }
        if (ArrakisAmmo.statIndex() == Integer.MIN_VALUE) {
            missing.append(' ').append(ArrakisAmmo.STAT_ID);
        }
        getLogger().at(Level.INFO).log("Missing:%s", missing.length() == 0 ? " nothing" : missing);
        for (String line : AssetSelfCheck.run()) {
            if (line.contains("PROBLEM")) {
                getLogger().at(Level.WARNING).log("%s", line);
            } else {
                getLogger().at(Level.INFO).log("%s", line);
            }
        }
    }
}
