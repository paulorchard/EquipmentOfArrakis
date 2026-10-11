package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import java.util.logging.Level;

/**
 * IslandCraft - Equipment of Arrakis. Stage one: the four tools (Blood Extractor, Dew Reaper, Compactor, Cutter Ray),
 * the Arrakis_Power stat and the debug commands. The tools' models, animations and interactions are asset data;
 * the code here is the stat, the commands and the beam.
 */
public class EquipmentOfArrakisPlugin extends JavaPlugin {

    private static EquipmentOfArrakisPlugin instance;

    // Must be created before setup(); the server loads it from disk in between (the on-screen display settings).
    private final Config<HudSettings> hudConfig = withConfig(HudSettings.FILE_NAME, HudSettings.CODEC);

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
        getCodecRegistry(Interaction.CODEC).register(CompactorInteraction.TYPE, CompactorInteraction.class,
                CompactorInteraction.CODEC);
        getCommandRegistry().registerCommand(PowerCommands.build(() -> hudConfig.get()));
        getEntityStoreRegistry().registerSystem(new PowerRechargeSystem());
        getEntityStoreRegistry().registerSystem(new CompactorSystem());
        getEntityStoreRegistry().registerSystem(new HudSystem(() -> hudConfig.get()));
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            ArrakisPower.forget(event.getPlayerRef().getUuid());
            CompactorTrail.forget(event.getPlayerRef().getUuid());
            HudSystem.forget(event.getPlayerRef().getUuid());
        });
    }

    /** Assets are loaded by now: say which of this mod's assets this game has. */
    @Override
    protected void start() {
        // Writes the file with every setting (the defaults for any that were missing), so there is something to edit.
        hudConfig.save();
        StringBuilder missing = new StringBuilder();
        for (String id : new String[] {"Arrakis_Blood_Extractor", "Arrakis_Dew_Reaper", "Arrakis_Compactor",
                "Arrakis_Cutter_Ray"}) {
            if (Item.getAssetMap().getAsset(id) == null) {
                missing.append(' ').append(id);
            }
        }
        if (ArrakisPower.statIndex() == Integer.MIN_VALUE) {
            missing.append(' ').append(ArrakisPower.STAT_ID);
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
