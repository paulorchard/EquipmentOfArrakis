package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import java.util.Locale;

/**
 * The words of {@code /arrakis hud <lb|rb|lt|rt> [x[,] y [z]]}. A number is a multiplier-style value: the step is (value - 1) * 10
 * virtual pixels, so 1.1 moves +1, 0.9 moves -1, 1.5 moves +5, 0.5 moves -5 and 1 moves nothing. A third number is ignored. Pure.
 */
final class HudNudge {

    /** Pixels for a value of 2 (the step is (value - 1) times this). */
    static final double PIXELS_PER_UNIT = 10;

    enum Kind {
        /** No numbers: show the current offsets. */
        SHOW,
        /** The word reset: clear this bar. */
        RESET,
        /** Two numbers: add this step. */
        MOVE,
        /** Anything else; {@link Parsed#message} says what is wrong. */
        BAD
    }

    record Parsed(Kind kind, double dx, double dy, boolean ignoredExtra, String message) {
    }

    private HudNudge() {
    }

    /** The movement in pixels of one typed value, rounded to hundredths so that 1.1 is exactly 1. */
    static double step(double value) {
        return Math.round((value - 1.0) * PIXELS_PER_UNIT * 100.0) / 100.0;
    }

    /** Parses the arguments after the bar word: commas and spaces both separate the numbers. */
    static Parsed parse(String[] args) {
        StringBuilder joined = new StringBuilder();
        for (String arg : args) {
            if (arg != null) {
                joined.append(' ').append(arg);
            }
        }
        String[] words = joined.toString().replace(',', ' ').trim().split("\s+");
        if (words.length == 1 && words[0].isEmpty()) {
            return new Parsed(Kind.SHOW, 0, 0, false, null);
        }
        if (words.length == 1 && words[0].equalsIgnoreCase("reset")) {
            return new Parsed(Kind.RESET, 0, 0, false, null);
        }
        if (words.length < 2) {
            return new Parsed(Kind.BAD, 0, 0, false, "Two numbers are needed, x and y: 1.1, 1 moves one pixel right (1, 1 changes nothing).");
        }
        double x;
        double y;
        try {
            x = Double.parseDouble(words[0]);
            y = Double.parseDouble(words[1]);
        } catch (NumberFormatException e) {
            return new Parsed(Kind.BAD, 0, 0, false, "Not numbers: " + words[0] + ", " + words[1]);
        }
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return new Parsed(Kind.BAD, 0, 0, false, "Not finite numbers.");
        }
        return new Parsed(Kind.MOVE, step(x), step(y), words.length > 2, null);
    }

    /** An offset for display: "x 1.0, y -5.0 pixels". */
    static String describe(double x, double y) {
        return String.format(Locale.ROOT, "x %.2f, y %.2f pixels", x, y);
    }
}
