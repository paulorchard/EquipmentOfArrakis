package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

/**
 * The hypothesis behind the AIRDRAG way: the client multiplies the vertical speed by the air drag multiplier m every tick and adds
 * a gravity step g, so the speed settles where v = (v + g) * m, i.e. v = g * m / (1 - m). With the measured terminal speed of
 * 23.58 blocks a second at the default m = 0.995 the step is 0.1185. This is a guess to be checked on a client, not a known fact.
 */
final class AirDragMath {

    /** The gravity step fitted from the measured terminal speed 23.58 at m = 0.995. */
    static final double FITTED_STEP = 23.58 * (1 - 0.995) / 0.995;

    private AirDragMath() {
    }

    /** The multiplier that gives {@code target} blocks a second as the terminal speed. */
    static double airDragFor(double target, double step) {
        double x = target / step;
        return x / (1 + x);
    }

    /** The terminal speed the multiplier {@code m} gives. */
    static double terminalFor(double m, double step) {
        return step * m / (1 - m);
    }

    /** The step that fits a measured terminal speed at a multiplier. */
    static double stepFrom(double terminal, double m) {
        return terminal * (1 - m) / m;
    }
}
