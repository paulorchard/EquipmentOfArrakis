package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import java.util.function.IntPredicate;

/** The checks of {@code /arrakis suspensor drop}: how far up, and whether the column above is free. No engine types. */
final class DropRules {

    /** The highest block row the world has (a 320 block world: rows 0 to 319). */
    static final int WORLD_TOP = 319;

    /** The most blocks a drop may go up. */
    static final int MAX_BLOCKS = 250;

    /** Free rows needed above the landing spot (a player is two blocks tall). */
    static final int HEADROOM = 2;

    private DropRules() {
    }

    /**
     * Null when the player standing at {@code feetY} may be taken {@code blocks} up, otherwise why not. {@code solid} says whether the
     * block at a row of the column is in the way (a block, or a column that is not loaded).
     */
    static String refuse(int feetY, int blocks, IntPredicate solid) {
        if (blocks < 1 || blocks > MAX_BLOCKS) {
            return "give a number of blocks from 1 to " + MAX_BLOCKS;
        }
        int top = feetY + blocks + HEADROOM;
        if (top > WORLD_TOP) {
            return "that is above the build limit (row " + WORLD_TOP + "): at most " + Math.max(0, WORLD_TOP - HEADROOM - feetY) + " blocks from here";
        }
        for (int y = feetY + 1; y <= top; y++) {
            if (solid.test(y)) {
                return "there is a block (or an unloaded column) at row " + y + " above you";
            }
        }
        return null;
    }
}
