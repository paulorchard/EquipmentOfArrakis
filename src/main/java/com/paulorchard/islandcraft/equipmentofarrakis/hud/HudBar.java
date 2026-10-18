package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import java.util.Locale;

/** The four bars. {@code id} is the name used in the .ui (#PowerFill, #PowerRow ...). */
enum HudBar {
    POWER("Power", true, true, true),
    HEALTH("Health", true, false, false),
    EXPOSURE("Exposure", false, true, false),
    WATER("Water", false, false, true);

    final String id;
    /** Left half of the screen (the outer end is the left end). */
    final boolean left;
    /** The upper of the two rows. */
    final boolean topRow;
    /** Flashes below FlashBelowPercent (Power and Water only). */
    final boolean flashes;

    HudBar(String id, boolean left, boolean topRow, boolean flashes) {
        this.id = id;
        this.left = left;
        this.topRow = topRow;
        this.flashes = flashes;
    }

    /** A bar from a command word: power, health, exposure (or heat), water; null if unknown. */
    static HudBar parse(String word) {
        if (word == null) {
            return null;
        }
        String w = word.toLowerCase(Locale.ROOT);
        if (w.equals("heat")) {
            return EXPOSURE;
        }
        for (HudBar bar : values()) {
            if (bar.id.toLowerCase(Locale.ROOT).equals(w)) {
                return bar;
            }
        }
        return null;
    }
}
