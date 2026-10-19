// EXPERIMENT: delete with the lab module
package com.paulorchard.islandcraft.equipmentofarrakis.lab;

/** The ids of the lab assets. */
final class LabItems {

    static final String BELT = "Arrakis_Lab_Belt";
    static final String BELT_GLIDER = "Arrakis_Lab_Belt_Glider";
    static final String GLIDER = "Glider_Scrap";
    static final String HUM_EFFECT = "Arrakis_Lab_Hum";

    private LabItems() {
    }

    /** True for the lab belts (the Z item the belt prototype looks for). */
    static boolean isBelt(String itemId) {
        return BELT.equals(itemId) || BELT_GLIDER.equals(itemId);
    }
}
