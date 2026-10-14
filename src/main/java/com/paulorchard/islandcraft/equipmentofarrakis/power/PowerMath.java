package com.paulorchard.islandcraft.equipmentofarrakis.power;

/**
 * The arithmetic of the power service, without the engine: clamping, the all-or-nothing spend, the take-what-is-there spend and
 * the creative rule. Creative mode spends nothing and always succeeds.
 */
public final class PowerMath {

    /** A spend asks for the amount to be there up to this much less, so that a rounding error (29.9999 for 30) does not refuse it. */
    static final float TOLERANCE = 1e-3f;

    private PowerMath() {
    }

    /** A spend: whether it went through, and the value afterwards. */
    public record Spend(boolean ok, float after) {
    }

    /** A take: what was taken, and the value afterwards. */
    public record Take(float taken, float after) {
    }

    /** The value held between 0 and the maximum (a maximum below 0 counts as 0). */
    public static float clamp(float value, float max) {
        if (Float.isNaN(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(value, Math.max(0f, max)));
    }

    /** current / max, 0 when the maximum is not above 0. */
    public static float fraction(float current, float max) {
        return max > 0f ? clamp(current, max) / max : 0f;
    }

    /** True when {@code amount} is there. A zero or negative amount is always there. */
    public static boolean has(float current, float amount) {
        return amount <= 0f || current + TOLERANCE >= amount;
    }

    /**
     * All or nothing: when less than {@code amount} is there nothing changes and the result is false. A zero or negative amount
     * is a no-op that succeeds. In creative mode it succeeds and nothing is spent.
     */
    public static Spend spend(float current, float amount, boolean creative, float max) {
        if (amount <= 0f || creative) {
            return new Spend(true, current);
        }
        if (!has(current, amount)) {
            return new Spend(false, current);
        }
        return new Spend(true, clamp(current - amount, max));
    }

    /** Takes what is there, up to {@code amount}; in creative mode takes nothing and reports the full amount. */
    public static Take spendUpTo(float current, float amount, boolean creative, float max) {
        if (amount <= 0f) {
            return new Take(0f, current);
        }
        if (creative) {
            return new Take(amount, current);
        }
        float taken = Math.min(amount, Math.max(0f, current));
        return new Take(taken, clamp(current - taken, max));
    }

    public static float add(float current, float amount, float max) {
        return clamp(current + amount, max);
    }

    public static float set(float amount, float max) {
        return clamp(amount, max);
    }
}
