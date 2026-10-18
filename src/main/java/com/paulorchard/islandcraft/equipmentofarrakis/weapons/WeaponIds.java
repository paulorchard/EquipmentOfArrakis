package com.paulorchard.islandcraft.equipmentofarrakis.weapons;

import java.util.ArrayList;
import java.util.List;

/** The ten weapon item ids. */
public final class WeaponIds {

    public static final String[] HOUSES = {"Crude", "Atreides", "Choam", "Harkonnen"};
    public static final String MAULA = "Arrakis_Weapon_Maula_Pistol";
    public static final String DARTGUN = "Arrakis_Weapon_Dartgun";

    private WeaponIds() {
    }

    /** The eight swords and daggers: swords first, in house order. */
    public static List<String> melee() {
        List<String> ids = new ArrayList<>();
        for (String kind : new String[] {"Sword", "Daggers"}) {
            for (String house : HOUSES) {
                ids.add("Arrakis_Weapon_" + kind + "_" + house);
            }
        }
        return ids;
    }

    public static List<String> guns() {
        return List.of(MAULA, DARTGUN);
    }

    /** All ten, melee then guns. */
    public static List<String> all() {
        List<String> ids = melee();
        ids.addAll(guns());
        return ids;
    }

    public static boolean isWeapon(String itemId) {
        return itemId != null && all().contains(itemId);
    }
}
