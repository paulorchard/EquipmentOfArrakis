package com.paulorchard.islandcraft.equipmentofarrakis.placeholders;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
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
 * The two tools that are not built yet, so the picker shows the full set: the Vehicle Summoner and the Build/Repair Tool. Each is a
 * crude pickaxe to look at; left click does nothing and right click opens the picker.
 */
public final class PlaceholdersModule implements EquipmentModule {

    public static final String ID = "placeholders";
    public static final String VEHICLE_SUMMONER = "Arrakis_Tool_Vehicle_Summoner";
    public static final String BUILD_REPAIR = "Arrakis_Tool_Build_Repair";

    private final ModuleAssets.Guard guard;

    public PlaceholdersModule(JavaPlugin plugin) {
        guard = ModuleAssets.guard(plugin, ID, id -> id.equals(VEHICLE_SUMMONER) || id.equals(BUILD_REPAIR));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void setup(ModuleContext ctx) {
        guard.activate();
        ToolRegistry.get().register(VEHICLE_SUMMONER, 60, null);
        ToolRegistry.get().register(BUILD_REPAIR, 70, null);
        GiveRegistry.get().add(VEHICLE_SUMMONER, 60, null);
        GiveRegistry.get().add(BUILD_REPAIR, 70, null);
    }

    @Override
    public List<String> selfCheck() {
        List<String> lines = new ArrayList<>();
        for (String id : new String[] {VEHICLE_SUMMONER, BUILD_REPAIR}) {
            Item item = Item.getAssetMap().getAsset(id);
            if (item == null) {
                lines.add("PROBLEM the item " + id + " is not loaded");
                continue;
            }
            String[] categories = item.getCategories();
            boolean inTab = categories != null && Arrays.asList(categories).contains("IslandCraft.Tools");
            boolean problem = !inTab || item.hasRecipesToGenerate() || item.getMaxDurability() > 0 || item.getMaxStack() != 1
                    || item.getInteractions() == null || !isEmptyPrimary(item.getInteractions().get(com.hypixel.hytale.protocol.InteractionType.Primary));
            lines.add((problem ? "PROBLEM " : "") + "item " + id + " loaded, categories " + Arrays.toString(categories)
                    + ", max durability " + item.getMaxDurability() + ", interactions " + item.getInteractions() + " (no Primary)");
        }
        return lines;
    }

    /** The engine fills a missing Primary with its own empty interaction (*Empty_...): that is "no left click behaviour". */
    private static boolean isEmptyPrimary(String id) {
        return id == null || id.startsWith("*Empty");
    }
}
