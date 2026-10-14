package com.paulorchard.islandcraft.equipmentofarrakis.catalog;

import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemCategory;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * The creative-menu tab. It is all asset data (Server/Item/Category/CreativeLibrary/IslandCraft.json, two icons, translation
 * keys and a proof item); this class only checks at start that the game loaded it.
 *
 * <p>Convention for every later module: an item joins the tab by string only, with
 * {@code "Categories": ["IslandCraft.Tools"]} (or Weapons, Armor, Resources) in its item JSON, so no module imports catalog.
 */
public final class CatalogModule implements EquipmentModule {

    public static final String ID = "catalog";
    public static final String TAB = "IslandCraft";
    public static final List<String> CHILDREN = List.of("Tools", "Weapons", "Armor", "Resources");
    public static final String DEV_MARKER = "Arrakis_Dev_Marker";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void setup(ModuleContext ctx) {
        // Nothing to register: the tab, its children and the proof item are assets.
    }

    @Override
    public void start(ModuleContext ctx) {
        for (String line : selfCheck()) {
            ctx.logger().at(line.contains("PROBLEM") ? Level.WARNING : Level.INFO).log("Catalog: %s", line);
        }
    }

    @Override
    public List<String> selfCheck() {
        List<String> lines = new ArrayList<>();
        ItemCategory tab = ItemCategory.getAssetMap().getAsset(TAB);
        if (tab == null) {
            lines.add("PROBLEM the " + TAB + " category is not loaded");
        } else {
            lines.add("category " + tab.getId() + " loaded: name " + tab.getName() + ", order " + tab.getOrder() + ", icon "
                    + icon(tab.getIcon()) + ", active icon " + icon(activeIcon(tab.getIcon())));
            ItemCategory[] children = tab.getChildren();
            List<String> ids = new ArrayList<>();
            for (ItemCategory child : children == null ? new ItemCategory[0] : children) {
                ids.add(child.getId());
                lines.add("  child " + child.getId() + ": name " + child.getName() + ", icon " + icon(child.getIcon()));
            }
            if (!ids.equals(CHILDREN)) {
                lines.add("PROBLEM the children are " + ids + ", expected " + CHILDREN);
            }
        }
        Item marker = Item.getAssetMap().getAsset(DEV_MARKER);
        if (marker == null) {
            lines.add("PROBLEM the item " + DEV_MARKER + " is not loaded");
        } else {
            String[] categories = marker.getCategories();
            boolean inTab = categories != null && Arrays.asList(categories).contains(TAB + ".Tools");
            lines.add((inTab ? "" : "PROBLEM ") + "item " + DEV_MARKER + " loaded, categories " + Arrays.toString(categories));
        }
        return lines;
    }

    /** "Icons/ItemCategories/Arrakis.png" gives "Icons/ItemCategories/ArrakisActive.png", the way the vanilla tabs are named. */
    static String activeIcon(String icon) {
        return icon.endsWith(".png") ? icon.substring(0, icon.length() - 4) + "Active.png" : icon;
    }

    /** The icon path with whether the common-asset registry has it. */
    private static String icon(String path) {
        if (path == null) {
            return "none PROBLEM missing icon";
        }
        boolean found = CommonAssetRegistry.hasCommonAsset(path) || CommonAssetRegistry.hasCommonAsset("Common/" + path);
        return path + (found ? " (found)" : " PROBLEM not found among the loaded common assets");
    }
}
