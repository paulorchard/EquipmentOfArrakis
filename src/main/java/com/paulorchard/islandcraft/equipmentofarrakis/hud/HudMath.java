package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import java.util.Locale;

/** The engine-free rules of the HUD: the ProgressBar value, the change threshold, the alert phase, colours. */
final class HudMath {

    /** What the error overlay and its outline are doing on a bar. */
    enum Alert {
        /** Not alerting: both overlays invisible, the alert fill hidden. */
        NONE,
        /** Alerting, bright phase: the error overlay and its outline fully visible. */
        BRIGHT,
        /** Alerting, dim phase: the error overlay invisible, the outline at its minimum opacity. */
        DIM
    }

    /** FlexWeight is an integer in the client, so a fill is split into SCALE parts: 1000 gives 0.1% steps. A part with weight 0 has no length. */
    static final int SCALE = 1000;

    private HudMath() {
    }

    /** The FlexWeights of (fill part, empty part) for a percent 0..100. They always add up to SCALE; 0 and 100 are exact. */
    static int[] split(double percent) {
        double v = Double.isNaN(percent) ? 0.0 : Math.max(0.0, Math.min(1.0, percent / 100.0));
        int fill = (int) Math.round(v * SCALE);
        return new int[] {fill, SCALE - fill};
    }

    /**
     * A colour string that is safe to send to the client at run time: exactly #rrggbb. The client disconnects the player on a string it cannot
     * convert (it refused #ffffff(0.5)), so a bad value from the config is replaced by the fallback before it is ever sent.
     */
    static String safeColor(String value, String fallback) {
        return value != null && value.matches("^#[0-9a-fA-F]{6}$") ? value.toLowerCase(Locale.ROOT) : fallback;
    }

    /** The value of a vanilla ProgressBar for a percent: 0..1 (NaN is 0). */
    static float value(double percent) {
        return Double.isNaN(percent) ? 0f : (float) Math.max(0.0, Math.min(1.0, percent / 100.0));
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

    /** A bar alerts while its switch is on and its value is below {@code below} percent. */
    static boolean alerting(boolean switchOn, double percent, double below) {
        return switchOn && !Double.isNaN(percent) && percent < below;
    }

    /**
     * The phase of the alert flash at {@code seconds}: false is the dim phase, true the bright one. It flips every {@code flipSeconds}
     * (the vanilla stamina low alert flips every 0.25 s), from a clock all bars share so that they flash together; it starts bright.
     */
    static boolean bright(double seconds, double flipSeconds) {
        if (flipSeconds <= 0) {
            return true;
        }
        return ((long) Math.floor(seconds / flipSeconds)) % 2 == 0;
    }

    static Alert alert(boolean alerting, boolean bright) {
        return !alerting ? Alert.NONE : bright ? Alert.BRIGHT : Alert.DIM;
    }

    /**
     * What the three overlay groups of a bar show in an alert state: the error overlay and its outline in the bright phase, the dim outline
     * (a second group at the vanilla outline start opacity, fixed in the .ui) in the dim phase. A colour with an alpha cannot be set from the
     * server (the client refuses the string #ffffff(0.50) and disconnects), so opacity is never set at run time: groups are shown or hidden.
     */
    record Overlays(boolean error, boolean outline, boolean outlineDim) {
    }

    static Overlays overlays(Alert alert) {
        return new Overlays(alert == Alert.BRIGHT, alert == Alert.BRIGHT, alert == Alert.DIM);
    }

    static String blend(String from, String to, double t) {
        int a = parse(safeColor(from, "#ffffff"));
        int b = parse(safeColor(to, "#ffffff"));
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

    /** The Heat Exposure icon colour: the low colour at 0 percent, the high one at 100. */
    static String exposureIconColor(double percent, String low, String high) {
        return blend(low, high, Double.isNaN(percent) ? 0.0 : percent / 100.0);
    }
}
