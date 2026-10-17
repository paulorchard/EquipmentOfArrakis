package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

/** The Blood Extractor refusals, engine-free. Every refusal happens before the animation and costs nothing. */
final class ExtractorRules {

    private ExtractorRules() {
    }

    enum Refusal {
        NONE, NO_CORPSE, ALREADY_USED, NOT_CROUCHING, NO_BAG, BAGS_FULL
    }

    /**
     * The first rule a click breaks. Order: a corpse in range and aim; not used already; (only when forcing the crouch is off and the
     * crouch is required) the player is crouched; a bag is carried; one has room.
     */
    static Refusal refusal(boolean corpseFound, boolean used, boolean requireCrouched, boolean crouched, int bagsCarried, boolean anyRoom) {
        if (!corpseFound) {
            return Refusal.NO_CORPSE;
        }
        if (used) {
            return Refusal.ALREADY_USED;
        }
        if (requireCrouched && !crouched) {
            return Refusal.NOT_CROUCHING;
        }
        if (bagsCarried <= 0) {
            return Refusal.NO_BAG;
        }
        return anyRoom ? Refusal.NONE : Refusal.BAGS_FULL;
    }
}
