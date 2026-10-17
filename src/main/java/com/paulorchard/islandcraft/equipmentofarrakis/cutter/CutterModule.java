package com.paulorchard.islandcraft.equipmentofarrakis.cutter;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
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
 * The Cutter Ray: a placeholder gun (the vanilla Pulse Rifle's look and shot feel) whose shot harvests the block in front of it with
 * the strength of a crude pickaxe and costs 10 Mana. See {@link CutterShotInteraction}. The interaction type is registered in the
 * constructor, before the assets load.
 */
public final class CutterModule implements EquipmentModule {

    public static final String ID = "cutter";
    public static final String TOOL = "Arrakis_Tool_Cutter_Ray";
    private static final int ORDER = 10;

    private static HytaleLogger logger;
    private final ModuleAssets.Guard guard;

    public CutterModule(JavaPlugin plugin) {
        logger = plugin.getLogger();
        plugin.getCodecRegistry(Interaction.CODEC).register(CutterShotInteraction.TYPE, CutterShotInteraction.class,
                CutterShotInteraction.CODEC);
        guard = ModuleAssets.guard(plugin, ID, id -> id.equals(TOOL) || id.startsWith("Arrakis_Cutter_"));
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
        guard.activate();
        ToolRegistry.get().register(TOOL, ORDER, null);
        GiveRegistry.get().add(TOOL, ORDER, null);
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
                    + ", max durability " + item.getMaxDurability() + ", max stack " + item.getMaxStack() + ", model " + item.getModel()
                    + ", interactions " + item.getInteractions());
        }
        for (String id : new String[] {"Arrakis_Cutter_Shot", "Arrakis_Cutter_Effects"}) {
            lines.add(Interaction.getAssetMap().getAsset(id) == null ? "PROBLEM interaction " + id + " is not loaded" : "interaction " + id + " loaded");
        }
        lines.add(RootInteraction.getAssetMap().getAsset("Arrakis_Cutter_Primary") == null
                ? "PROBLEM root interaction Arrakis_Cutter_Primary is not loaded" : "root interaction Arrakis_Cutter_Primary loaded");
        return lines;
    }
}
