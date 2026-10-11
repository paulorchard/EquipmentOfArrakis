package com.paulorchard.islandcraft.equipmentofarrakis;

/** The rules of the Dew Reaper that need no engine classes, so they can be tested on their own. */
final class DewReaperRules {

    private DewReaperRules() {
    }

    /** What a swing will do, decided when the click starts. */
    enum Mode {
        /** The crosshair is on a Primrose: the swing gives the player water. */
        PRIMROSE,
        /** Anything else, including nothing: the swing fills a Litrejon. */
        AIR
    }

    /** Why a click is refused, in the order the rules are checked. */
    enum Refusal {
        NONE, WATER_MOD_MISSING, NO_POWER
    }

    /** How many units of water a swing collects: more at a Primrose, a little anywhere else. */
    static double units(Mode mode, double primroseUnits, double airUnits) {
        return mode == Mode.PRIMROSE ? primroseUnits : airUnits;
    }

    /** Primrose mode when the target block's id is the Primrose's, air mode for any other block and for no block. */
    static Mode mode(String targetBlockId, String primroseBlockId) {
        return targetBlockId != null && targetBlockId.equals(primroseBlockId) ? Mode.PRIMROSE : Mode.AIR;
    }

    /**
     * The first rule a click breaks, or {@link Refusal#NONE}. Order: the Water mod must be there (without it there is no flask to fill);
     * then power. There is no rule about flasks: a swing with no flask with room still happens, and its water is lost.
     */
    static Refusal refusal(boolean waterModLoaded, float power, float cost) {
        if (!waterModLoaded) {
            return Refusal.WATER_MOD_MISSING;
        }
        return power < cost ? Refusal.NO_POWER : Refusal.NONE;
    }

    /**
     * Finds the first flask with room. {@code amounts} holds one array per place to look, in the order they are searched (hotbar, then
     * storage, then backpack), and in each the amount in each slot, or a negative number where the slot is not a flask. Returns
     * {place, slot} of the first flask whose amount is below {@code capacity}, or null.
     */
    static int[] firstWithRoom(double[][] amounts, double capacity) {
        for (int place = 0; place < amounts.length; place++) {
            for (int slot = 0; slot < amounts[place].length; slot++) {
                double amount = amounts[place][slot];
                if (amount >= 0 && amount < capacity) {
                    return new int[] {place, slot};
                }
            }
        }
        return null;
    }

    /** The amount after adding {@code units}: never below 0 and never above {@code capacity}. */
    static double filled(double amount, double units, double capacity) {
        return Math.max(0, Math.min(capacity, amount + units));
    }
}
