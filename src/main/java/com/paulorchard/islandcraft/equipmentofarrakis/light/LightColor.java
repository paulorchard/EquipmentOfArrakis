package com.paulorchard.islandcraft.equipmentofarrakis.light;

/** The Personal Light colour, engine-free: the torch light, tinted. */
final class LightColor {

    private LightColor() {
    }

    /** radius, red, green, blue, each a small number (0 to 15 for the colour). */
    record Rgb(int radius, int red, int green, int blue) {
    }

    /**
     * The light of the toggle. The brightness is the brightest channel of the torch; each channel is that brightness times its tint
     * (so the brightest channel of the result is the brightness itself when one tint is 1), clamped to 0..15. A setting of 0 or more
     * ({@code overrideRadius}, {@code overrideRed} ...) replaces the number it names.
     */
    static Rgb resolve(int torchRadius, int torchRed, int torchGreen, int torchBlue, float tintRed, float tintGreen, float tintBlue,
            int overrideRadius, int overrideRed, int overrideGreen, int overrideBlue) {
        int brightness = Math.max(torchRed, Math.max(torchGreen, torchBlue));
        int radius = overrideRadius >= 0 ? overrideRadius : torchRadius;
        int red = overrideRed >= 0 ? overrideRed : channel(brightness, tintRed);
        int green = overrideGreen >= 0 ? overrideGreen : channel(brightness, tintGreen);
        int blue = overrideBlue >= 0 ? overrideBlue : channel(brightness, tintBlue);
        return new Rgb(Math.max(0, radius), clamp(red), clamp(green), clamp(blue));
    }

    static int channel(int brightness, float tint) {
        return clamp(Math.round(brightness * tint));
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(15, v));
    }

    /** True when the colour leans cool: blue strictly the brightest, red not above green. A warm or neutral light is not cool white. */
    static boolean isCoolWhite(Rgb c) {
        return c.blue() >= c.green() && c.green() >= c.red() && c.blue() > c.red();
    }
}
