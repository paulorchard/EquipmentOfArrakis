package com.paulorchard.islandcraft.equipmentofarrakis.picker;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The tool picker: the right-click page of every Arrakis tool (promoted from Experiment 4). The page is registered with the engine
 * in the constructor, before the assets load, like every interaction type; the tools reference the root interaction
 * {@value #SECONDARY} as their Secondary. The tools and toggles it lists come from the core registries.
 */
public final class PickerModule implements EquipmentModule {

    public static final String ID = "picker";
    public static final String SECONDARY = "Arrakis_Picker_Secondary";

    private static HytaleLogger logger;

    public PickerModule(JavaPlugin plugin) {
        logger = plugin.getLogger();
        OpenCustomUIInteraction.registerSimple(plugin, ToolPicker.class, ToolPicker.ID, ToolPicker::new);
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
        ctx.command(PickerCommands.build());
    }

    @Override
    public List<String> selfCheck() {
        List<String> lines = new ArrayList<>();
        lines.add(exists(RootInteraction.getAssetMap().getAsset(SECONDARY), "root interaction " + SECONDARY));
        lines.add(exists(Interaction.getAssetMap().getAsset("Arrakis_Picker_Open"), "interaction Arrakis_Picker_Open"));
        for (String file : new String[] {"Page", "Entry", "Toggle"}) {
            lines.add(common("UI/Custom/Pages/Equipment_of_Arrakis/Picker/Arrakis_Picker_" + file + ".ui"));
        }
        return lines;
    }

    private static String exists(Object asset, String what) {
        return (asset == null ? "PROBLEM " : "") + what + (asset == null ? " is not loaded" : " loaded");
    }

    private static String common(String path) {
        boolean found = CommonAssetRegistry.hasCommonAsset(path) || CommonAssetRegistry.hasCommonAsset("Common/" + path);
        return (found ? "" : "PROBLEM not found: ") + path;
    }
}
