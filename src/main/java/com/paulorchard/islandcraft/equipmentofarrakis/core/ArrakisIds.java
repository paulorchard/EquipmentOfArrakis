package com.paulorchard.islandcraft.equipmentofarrakis.core;

/** Naming conventions of the mod, so that modules do not rebuild them. Asset ids are global in Hytale, hence the prefix. */
public final class ArrakisIds {

    public static final String PREFIX = "Arrakis_";

    private ArrakisIds() {
    }

    /** "Compactor" gives "Arrakis_Compactor"; an id that already has the prefix is returned as it is. */
    public static String item(String name) {
        return name.startsWith(PREFIX) ? name : PREFIX + name;
    }

    /** The translation key of an item's name: server.items.Arrakis_Compactor.name. */
    public static String itemNameKey(String itemId) {
        return "server.items." + itemId + ".name";
    }
}
