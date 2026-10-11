package com.paulorchard.islandcraft.equipmentofarrakis;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * How long the Holtzman field stays down after the last projectile its owner launched, and the rule that says whether it is still down.
 * {@link #secondsFor} is the single place the length is decided: it starts from the data value ({@code CooldownSeconds} in
 * {@code Holtzman_Shield.json}, 1.0) or a live value set by {@code /arrakis shield cooldown}, and then runs the player through the
 * {@link Modifier}s other equipment may register with {@link #addModifier}. Nothing is registered now.
 *
 * <p>The timer rule is the plain one: a shot at time T keeps the field down until T plus the length; a newer shot moves T. Times are
 * {@link System#nanoTime} values; {@link #NONE} means "no shot".
 */
final class ShieldCooldown {

    /** No shot to count from. */
    static final long NONE = Long.MIN_VALUE;

    /** A change to the cooldown length for one player, applied in the order the modifiers were added; the result is kept at 0 or more. */
    @FunctionalInterface
    interface Modifier {
        double apply(UUID player, double seconds);
    }

    private static final List<Modifier> MODIFIERS = new CopyOnWriteArrayList<>();
    private static volatile Supplier<HoltzmanSettings> settings = HoltzmanSettings::new;
    private static volatile double override = -1;

    private ShieldCooldown() {
    }

    /** Where the data value is read from; set once by the plugin. */
    static void bind(Supplier<HoltzmanSettings> source) {
        settings = source == null ? HoltzmanSettings::new : source;
    }

    /** The hook for other equipment: adds a modifier (a per-player multiplier, a bonus, ...). */
    static void addModifier(Modifier modifier) {
        MODIFIERS.add(modifier);
    }

    static int modifierCount() {
        return MODIFIERS.size();
    }

    /** Development: a length that replaces the data value until the server restarts; a negative number goes back to the data value. */
    static void setOverride(double seconds) {
        override = seconds;
    }

    /** The length before any modifier: the live value if one was set, else the data value. */
    static double baseSeconds() {
        return override >= 0 ? override : settings.get().getCooldownSeconds();
    }

    /** The cooldown for this player, in seconds. */
    static double secondsFor(UUID player) {
        return seconds(baseSeconds(), MODIFIERS, player);
    }

    /** The pure part of {@link #secondsFor}: the base, run through the modifiers in order, never below 0. */
    static double seconds(double base, List<Modifier> modifiers, UUID player) {
        double seconds = Math.max(0, base);
        for (Modifier modifier : modifiers) {
            seconds = Math.max(0, modifier.apply(player, seconds));
        }
        return seconds;
    }

    /** True while the last shot is less than {@code seconds} old. */
    static boolean cooling(long lastShot, long now, double seconds) {
        return lastShot != NONE && (now - lastShot) < (long) (seconds * 1_000_000_000L);
    }

    /** Seconds left of the cooldown, 0 when it is over or there was no shot. */
    static double remaining(long lastShot, long now, double seconds) {
        if (!cooling(lastShot, now, seconds)) {
            return 0;
        }
        return seconds - (now - lastShot) / 1e9;
    }
}
