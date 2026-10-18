package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import java.util.Locale;

/** The engine-free rules of the HUD: fill weights, the change threshold, the flash phase, colours. */
final class HudMath {

    /**
     * FlexWeight is an integer in the client (a float is refused), so a fill is split into SCALE parts: 1000 gives 0.1% steps. A part
     * with weight 0 has no length.
     */
    static final int SCALE = 1000;

    private HudMath() {
    }

    /** The FlexWeights of (empty part, fill part) for a percent 0..100. They always add up to SCALE; 0 and 100 are exact. */
    static int[] split(double percent) {
        double v = Double.isNaN(percent) ? 0.0 : Math.max(0.0, Math.min(1.0, percent / 100.0));
        int fill = (int) Math.round(v * SCALE);
        return new int[] {SCALE - fill, fill};
    }

    /** True the first time, when the value moved by at least {@code min} percent, or when it reaches 0 or 100. */
    static boolean changed(double sent, double now, double min) {
        if (Double.isNaN(sent)) {
            return true;
        }
        if (now == sent) {
            return false;
        }
        return Math.abs(now - sent) >= min || now <= 0.0 || now >= 100.0;
    }

    /** A bar flashes while it is below {@code below} percent (and only if it is a flashing bar). */
    static boolean flashing(boolean flashingBar, double percent, double below) {
        return flashingBar && percent < below;
    }

    /**
     * The phase of the flash at {@code seconds}: false is the normal colour, true the bright one. It flips {@code hz} times a second
     * (bright for 1/hz, normal for 1/hz), from a clock all bars share so that they flash together.
     */
    static boolean bright(double seconds, double hz) {
        if (hz <= 0) {
            return false;
        }
        return ((long) Math.floor(seconds * hz)) % 2 != 0;
    }

    /** The colour of a bar: its own, or moved {@code tintPercent} toward white in the bright phase of a flash. */
    static String colorOf(String base, boolean flashing, boolean bright, double tintPercent) {
        return flashing && bright ? blend(base, "#ffffff", tintPercent / 100.0) : base;
    }

    static String blend(String from, String to, double t) {
        int a = parse(from);
        int b = parse(to);
        t = Math.max(0.0, Math.min(1.0, t));
        return String.format(Locale.ROOT, "#%02x%02x%02x", lerp((a >> 16) & 0xff, (b >> 16) & 0xff, t),
                lerp((a >> 8) & 0xff, (b >> 8) & 0xff, t), lerp(a & 0xff, b & 0xff, t));
    }

    private static int lerp(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    private static int parse(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        return Integer.parseInt(h.length() > 6 ? h.substring(0, 6) : h, 16);
    }

    /**
     * The heat exposure colour: orange up to {@code redStart} percent, then a straight blend to red at 100. The same rule as the Water
     * mod bar; the module takes the three numbers from the Water mod when it gives them and from its own config otherwise.
     */
    static String exposureColor(double percent, double redStart, String orange, String red) {
        double t = percent <= redStart ? 0.0 : Math.min(1.0, (percent - redStart) / Math.max(1e-6, 100.0 - redStart));
        return blend(orange, red, t);
    }
}
