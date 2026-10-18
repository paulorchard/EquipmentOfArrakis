package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import java.util.Locale;

/**
 * The four bars. {@code id} is the name used in the .ui (#PowerFill, #PowerRow ...), {@code code} the position word of the nudge
 * commands: lb left bottom, rb right bottom, lt left top, rt right top.
 */
enum HudBar {
    POWER("Power", "lt", true, true),
    HEALTH("Health", "lb", true, false),
    EXPOSURE("Exposure", "rt", false, true),
    WATER("Water", "rb", false, false);

    final String id;
    final String code;
    /** Left half of the screen: the Health-shaped bar, icon at the left end. */
    final boolean left;
    /** The upper of the two rows. */
    final boolean topRow;

    HudBar(String id, String code, boolean left, boolean topRow) {
        this.id = id;
        this.code = code;
        this.left = left;
        this.topRow = topRow;
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

    /** A bar from its position code (lb, rb, lt, rt); null if unknown. */
    static HudBar ofCode(String code) {
        for (HudBar bar : values()) {
            if (bar.code.equalsIgnoreCase(code)) {
                return bar;
            }
        }
        return null;
    }
}
