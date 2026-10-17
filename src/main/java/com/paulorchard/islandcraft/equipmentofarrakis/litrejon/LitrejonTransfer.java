package com.paulorchard.islandcraft.equipmentofarrakis.litrejon;

/** The LitreJon's pour, engine-free: how many units leave the flask for the player. */
final class LitrejonTransfer {

    private LitrejonTransfer() {
    }

    /**
     * Units to ask the Water mod to add: the most the flask has, the most one click gives ({@code cap}, 50), and only what the player
     * lacks (the shortfall below {@code max}, rounded up so a player 19.4 short gets 20 offered; the Water mod clamps at the maximum).
     */
    static int units(int inFlask, double water, double max, int cap) {
        double lack = Math.max(0, max - water);
        int lacking = (int) Math.ceil(lack - 1e-9);
        return Math.max(0, Math.min(Math.min(inFlask, cap), lacking));
    }

    /** Units the flask loses for the water the player really gained (whole units, rounded up so nothing is created). */
    static int cost(double gained) {
        return gained <= 1e-9 ? 0 : (int) Math.ceil(gained - 1e-9);
    }
}
