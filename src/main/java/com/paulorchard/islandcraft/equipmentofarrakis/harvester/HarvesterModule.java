package com.paulorchard.islandcraft.equipmentofarrakis.harvester;

import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.GiveRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleAssets;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToolRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Dew Harvester (renamed from the Dew Reaper): left click on an Arrakis_Primrose within 3 blocks, one 1 second swing at a time
 * (held, it repeats); each swing costs 10 Mana and moves 10 water into the first LitreJon with room. The Primrose is not used up. The
 * Primrose block belongs to the Water mod: without it there is nothing to aim at and the tool says so.
 */
public final class HarvesterModule implements EquipmentModule {

    public static final String ID = "harvester";
    public static final String TOOL = "Arrakis_Tool_Dew_Harvester";
    private static final int ORDER = 40;

    private final ModuleAssets.Guard guard;

    public HarvesterModule(JavaPlugin plugin) {
        plugin.getCodecRegistry(Interaction.CODEC).register(HarvesterGateInteraction.TYPE, HarvesterGateInteraction.class,
                HarvesterGateInteraction.CODEC);
        plugin.getCodecRegistry(Interaction.CODEC).register(HarvesterFinishInteraction.TYPE, HarvesterFinishInteraction.class,
                HarvesterFinishInteraction.CODEC);
        guard = ModuleAssets.guard(plugin, ID, id -> id.equals(TOOL) || id.startsWith("Arrakis_Harvester_") || id.startsWith("SFX_Arrakis_Harvester_"));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void setup(ModuleContext ctx) {
        guard.activate();
        ToolRegistry.get().register(TOOL, ORDER, null);
        GiveRegistry.get().add(TOOL, ORDER, null);
        ctx.events().registerGlobal(PlayerDisconnectEvent.class, event -> HarvesterState.forget(event.getPlayerRef().getUuid()));
    }

    @Override
    public List<String> selfCheck() {
        List<String> lines = new ArrayList<>();
        Item item = Item.getAssetMap().getAsset(TOOL);
        if (item == null) {
            lines.add("PROBLEM the item " + TOOL + " is not loaded");
        } else {
            String[] categories = item.getCategories();
            boolean inTab = categories != null && Arrays.asList(categories).contains("IslandCraft.Tools");
            boolean problem = !inTab || item.hasRecipesToGenerate() || item.getMaxDurability() > 0 || item.getMaxStack() != 1;
            lines.add((problem ? "PROBLEM " : "") + "item " + TOOL + " loaded, categories " + Arrays.toString(categories)
                    + ", max durability " + item.getMaxDurability() + ", max stack " + item.getMaxStack() + ", animations "
                    + item.getPlayerAnimationsId() + ", interactions " + item.getInteractions());
        }
        for (String id : new String[] {"Arrakis_Harvester_Gate", "Arrakis_Harvester_Swing", "Arrakis_Harvester_Finish"}) {
            lines.add(Interaction.getAssetMap().getAsset(id) == null ? "PROBLEM interaction " + id + " is not loaded" : "interaction " + id + " loaded");
        }
        lines.add(RootInteraction.getAssetMap().getAsset("Arrakis_Harvester_Primary") == null
                ? "PROBLEM root interaction Arrakis_Harvester_Primary is not loaded" : "root interaction Arrakis_Harvester_Primary loaded");
        lines.add(EntityEffect.getAssetMap().getAsset("Arrakis_Harvester_Active") == null
                ? "PROBLEM entity effect Arrakis_Harvester_Active is not loaded" : "entity effect Arrakis_Harvester_Active loaded");
        for (String id : new String[] {"SFX_Arrakis_Harvester_Active", "SFX_Arrakis_Harvester_Active_Local"}) {
            lines.add(SoundEvent.getAssetMap().getAsset(id) == null ? "PROBLEM sound " + id + " is not loaded" : "sound " + id + " loaded");
        }
        return lines;
    }
}
