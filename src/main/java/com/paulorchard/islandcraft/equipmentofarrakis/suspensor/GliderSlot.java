package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

/** The slot rules for the glider item (no engine types): what a Mk I mark does with the Utility slot. */
final class GliderSlot {

    static final String ITEM_ID = "Arrakis_Suspensor_Glider";

    enum Place {
        /** The slot is empty: put the glider in and say so. */
        PLACE,
        /** Our glider is already there: nothing to do. */
        ALREADY,
        /** Another item is there: do not touch it, say "Empty your utility slot for the glider." */
        BLOCKED
    }

    private GliderSlot() {
    }

    /** {@code slotItemId} is the item id in the Utility slot, or null when it is empty. */
    static Place place(String slotItemId) {
        if (slotItemId == null) {
            return Place.PLACE;
        }
        return ITEM_ID.equals(slotItemId) ? Place.ALREADY : Place.BLOCKED;
    }

    /** Switching off (or death, world change) removes only our item. */
    static boolean removable(String slotItemId) {
        return ITEM_ID.equals(slotItemId);
    }
}
