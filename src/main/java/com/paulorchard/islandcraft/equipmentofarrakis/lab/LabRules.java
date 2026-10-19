// EXPERIMENT: delete with the lab module
package com.paulorchard.islandcraft.equipmentofarrakis.lab;

import java.util.Locale;

/** The pure parts of the lab: when the belt prototype is active, what it costs, the fly values of Mk II and Mk III, the fall clamp. No engine class. */
final class LabRules {

    private LabRules() {
    }

    /**
     * The active condition of the Mk II and Mk III prototype: a mode is chosen (2 or 3), a lab belt is the selected Z item, the player is in
     * the air (not on the ground), the jump flag is held and there is Mana (or the player is in creative, which pays nothing).
     */
    static boolean beltActive(int mode, boolean beltIsSelectedZItem, boolean onGround, boolean jumping, boolean hasMana) {
        return (mode == 2 || mode == 3) && beltIsSelectedZItem && !onGround && jumping && hasMana;
    }

    /** Mana for one tick at {@code perSecond}: in proportion to the tick time, never negative. */
    static float manaForTick(float perSecond, float dt) {
        return Math.max(0f, perSecond) * Math.max(0f, dt);
    }

    /** The fly speeds {horizontal, vertical} of a belt mode: Mk II travels flat (vertical 0), Mk III rises. Other modes give null. */
    static float[] flySpeeds(int mode, float horizontal, float vertical) {
        return switch (mode) {
            case 2 -> new float[] {horizontal, 0f};
            case 3 -> new float[] {horizontal, vertical};
            default -> null;
        };
    }

    /**
     * The vertical velocity after the clamp: a fall faster than {@code maxFallSpeed} (blocks a second, a positive number) is held to
     * {@code -maxFallSpeed}; a rise, a slower fall, or a maximum of 0 or less (clamp off) is left alone.
     */
    static double clampFall(double vy, double maxFallSpeed) {
        if (maxFallSpeed <= 0 || vy >= -maxFallSpeed) {
            return vy;
        }
        return -maxFallSpeed;
    }

    /** True when the clamp would change {@code vy}. */
    static boolean clampBites(double vy, double maxFallSpeed) {
        return clampFall(vy, maxFallSpeed) != vy;
    }

    /**
     * The fall damage the engine does a player on landing (read from {@code DamageSystems.FallDamagePlayers}): nothing at or below
     * {@code minSpeed} (MinFallSpeedToEngageRoll, 21 blocks a second); above it {@code floor(maxHealth / 100 * ((0.58 * (speed - minSpeed))^2 + 10))}.
     * {@code speed} is the absolute vertical speed the client reports at the landing. No roll is assumed.
     */
    static int fallDamage(double speed, double minSpeed, double maxHealth) {
        if (speed <= minSpeed) {
            return 0;
        }
        double units = Math.pow(0.58 * (speed - minSpeed), 2.0) + 10.0;
        return (int) Math.floor(maxHealth / 100.0 * units);
    }

    /** Parses the fly mode word of the fly command: off, allowed or forced; null for anything else. */
    static String flyWord(String word) {
        if (word == null) {
            return null;
        }
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "off", "disabled" -> "Disabled";
            case "allowed" -> "Allowed";
            case "forced" -> "Forced";
            default -> null;
        };
    }
}
