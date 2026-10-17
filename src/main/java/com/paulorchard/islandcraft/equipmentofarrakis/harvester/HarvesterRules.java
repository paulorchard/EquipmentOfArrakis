package com.paulorchard.islandcraft.equipmentofarrakis.harvester;

/** The Dew Harvester rules that need no engine classes. */
final class HarvesterRules {

    private HarvesterRules() {
    }

    /** Why a swing is refused, in the order the rules are checked. */
    enum Refusal {
        NONE, NOT_PRIMROSE, NO_FLASK, FLASKS_FULL, NO_MANA
    }

    /**
     * The first rule a click breaks. Order: the target must be a Primrose; a LitreJon must be carried; one must have room; the player
     * must have the Mana (creative needs none). A refused click costs nothing and starts no swing.
     */
    static Refusal refusal(boolean targetIsPrimrose, int flasksCarried, boolean anyRoom, float mana, float cost, boolean creative) {
        if (!targetIsPrimrose) {
            return Refusal.NOT_PRIMROSE;
        }
        if (flasksCarried <= 0) {
            return Refusal.NO_FLASK;
        }
        if (!anyRoom) {
            return Refusal.FLASKS_FULL;
        }
        return creative || cost <= 0f || mana >= cost ? Refusal.NONE : Refusal.NO_MANA;
    }

    /** True when the block id is the Primrose. */
    static boolean isPrimrose(String blockId, String primroseBlockId) {
        return blockId != null && blockId.equals(primroseBlockId);
    }
}
