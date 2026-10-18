package com.paulorchard.islandcraft.equipmentofarrakis.litrejon;

import com.paulorchard.islandcraft.equipmentofarrakis.core.water.WaterApi;
import com.paulorchard.islandcraft.equipmentofarrakis.core.water.WaterLink;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ContainerInventory;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ContainerItem;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.GiveRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleAssets;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToolRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The LitreJon of Equipment: a second flask, {@code Arrakis_Tool_Litrejon}, the picker water tool. Its water is in the stack
 * metadata {@code Arrakis_Water_Units} (shown on the durability bar, see ContainerItem); left click pours up to 50 into the player
 * water. It never touches the Water mod own {@code Arrakis_Litrejon}. The Dew Harvester fills it through its registered container.
 */
public final class LitrejonModule implements EquipmentModule {

    public static final String ID = "litrejon";
    public static final String TOOL = "Arrakis_Tool_Litrejon";
    public static final String KEY = "Arrakis_Water_Units";
    private static final int ORDER = 30;

    private static LitrejonConfig config = new LitrejonConfig();
    private static final ContainerItem CONTAINER =
            new ContainerItem(TOOL, KEY, () -> config.capacity(), () -> config.startsFull());

    private final ModuleAssets.Guard guard;

    public LitrejonModule(JavaPlugin plugin) {
        plugin.getCodecRegistry(Interaction.CODEC).register(LitrejonPourInteraction.TYPE, LitrejonPourInteraction.class,
                LitrejonPourInteraction.CODEC);
        guard = ModuleAssets.guard(plugin, ID, id -> id.equals(TOOL) || id.startsWith("Arrakis_Litrejon_"));
    }

    static ContainerItem container() {
        return CONTAINER;
    }

    static LitrejonConfig config() {
        return config;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void setup(ModuleContext ctx) {
        guard.activate();
        config = ctx.settings(ID, LitrejonConfig.class);
        ContainerInventory.register(CONTAINER);
        ToolRegistry.get().register(TOOL, ORDER, null);
        GiveRegistry.get().add(TOOL, ORDER, CONTAINER::fresh);
    }

    @Override
    public void start(ModuleContext ctx) {
        ctx.logger().atInfo().log("LitreJon: %s", WaterLink.status());
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
            boolean problem = !inTab || item.hasRecipesToGenerate() || item.getMaxStack() != 1 || item.getMaxDurability() <= 0;
            lines.add((problem ? "PROBLEM " : "") + "item " + TOOL + " loaded, categories " + Arrays.toString(categories)
                    + ", max durability " + item.getMaxDurability() + " (the fullness bar), max stack " + item.getMaxStack()
                    + ", interactions " + item.getInteractions());
        }
        lines.add(Interaction.getAssetMap().getAsset("Arrakis_Litrejon_Pour") == null
                ? "PROBLEM interaction Arrakis_Litrejon_Pour is not loaded" : "interaction Arrakis_Litrejon_Pour loaded");
        lines.add(RootInteraction.getAssetMap().getAsset("Arrakis_Litrejon_Primary") == null
                ? "PROBLEM root interaction Arrakis_Litrejon_Primary is not loaded" : "root interaction Arrakis_Litrejon_Primary loaded");
        lines.add(WaterLink.status());
        lines.add("the Water mod own Arrakis_Litrejon is "
                + (Item.getAssetMap().getAsset("Arrakis_Litrejon") == null ? "not loaded (Water mod absent)" : "loaded and untouched"));
        return lines;
    }
}
