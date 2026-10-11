package com.paulorchard.islandcraft.equipmentofarrakis;

/** The rules of the spring guns' ammunition that need no engine classes, so they can be tested on their own. */
final class AmmoMath {

    static final float MAX = 100f;

    private AmmoMath() {
    }

    /** Ammo clamped to 0..{@link #MAX}; anything that is not a number is 0. */
    static float clamp(float ammo) {
        if (Float.isNaN(ammo)) {
            return 0f;
        }
        return Math.max(0f, Math.min(MAX, ammo));
    }

    /** What is left after a payment, and whether it was made. */
    record Payment(boolean paid, float after) {
    }

    /**
     * Paying {@code cost} from {@code have}: made only when there is at least the whole cost, and then {@code have - cost} is left (never
     * below 0). With less than the cost nothing is taken and the payment is refused (unlike power, a partly paid shot is no shot). A
     * cost of nothing, or less, is always paid and changes nothing.
     */
    static Payment spend(float have, float cost) {
        float held = clamp(have);
        if (!(cost > 0f)) {
            return new Payment(true, held);
        }
        if (held < cost) {
            return new Payment(false, held);
        }
        return new Payment(true, clamp(held - cost));
    }

    /** {@code have} plus {@code perSecond} for {@code seconds}, clamped to 0..{@link #MAX}. */
    static float recharged(float have, float perSecond, float seconds) {
        return clamp(clamp(have) + perSecond * seconds);
    }

    /** The whole number shown for an amount: rounded down (a shot needs the whole cost), 0..100. */
    static int display(float ammo) {
        return (int) Math.floor(clamp(ammo));
    }

    /** True when nothing was spent for more than {@code idleNanos} before {@code now}; {@code lastUsed} is null if never. */
    static boolean idle(Long lastUsed, long now, long idleNanos) {
        return lastUsed == null || now - lastUsed > idleNanos;
    }
}
