package com.paulorchard.islandcraft.equipmentofarrakis.core;

/** Naming conventions of the mod, so that modules do not rebuild them. Asset ids are global in Hytale, hence the prefix. */
public final class ArrakisIds {

    public static final String PREFIX = "Arrakis_";
    public static final String ASSET_FOLDER = "Equipment_of_Arrakis";

    private ArrakisIds() {
    }

    /** "Compactor" gives "Arrakis_Compactor"; an id that already has the prefix is returned as it is. */
    public static String item(String name) {
        return name.startsWith(PREFIX) ? name : PREFIX + name;
    }

    public static boolean isOurs(String id) {
        return id != null && id.startsWith(PREFIX);
    }

    /** The translation key of an item's name: server.items.Arrakis_Compactor.name. */
    public static String itemNameKey(String itemId) {
        return "server.items." + itemId + ".name";
    }

    public static String itemDescriptionKey(String itemId) {
        return "server.items." + itemId + ".description";
    }

    /** The key of a creative-library category name: server.ui.itemcategory.islandcraft.tools. */
    public static String categoryKey(String... path) {
        return "server.ui.itemcategory." + String.join(".", path).toLowerCase(java.util.Locale.ROOT);
    }
}
