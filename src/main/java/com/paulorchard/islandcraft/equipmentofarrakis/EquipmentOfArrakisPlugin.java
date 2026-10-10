package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import java.util.logging.Level;

/**
 * IslandCraft - Equipment of Arrakis. Stage one: the four tools (Blood Extractor, Dew Reaper, Compactor, Cutter Ray),
 * the Arrakis_Power stat and the debug commands. The tools' models, animations and interactions are asset data;
 * the code here is the stat, the commands and the beam.
 */
public class EquipmentOfArrakisPlugin extends JavaPlugin {

    private static EquipmentOfArrakisPlugin instance;

    public EquipmentOfArrakisPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static EquipmentOfArrakisPlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        getCodecRegistry(Interaction.CODEC).register(CutterBeamInteraction.TYPE, CutterBeamInteraction.class,
                CutterBeamInteraction.CODEC);
        getCommandRegistry().registerCommand(PowerCommands.build());
        getEntityStoreRegistry().registerSystem(new BeamExpirySystem());
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> BeamDisplay.forget(event.getPlayerRef().getUuid()));
    }

    /** Assets are loaded by now: say which of this mod's assets this game has. */
    @Override
    protected void start() {
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
        getLogger().at(Level.INFO).log("Pickaxes Tool_Pickaxe_Mithril does not match or beat in every gather type:%s",
                StrongestPickaxe.notDominated("Tool_Pickaxe_Mithril"));
        for (String line : AssetSelfCheck.run()) {
            if (line.contains("PROBLEM")) {
                getLogger().at(Level.WARNING).log("%s", line);
            } else {
                getLogger().at(Level.INFO).log("%s", line);
            }
        }
    }
}
