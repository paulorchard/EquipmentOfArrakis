package com.paulorchard.islandcraft.equipmentofarrakis;

import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The hook that feeds the AMMO readout. The spring guns do not use it: the HUD reads the player's {@code Arrakis_Ammo} stat itself (the hook is
 * given only a player id, and the stat needs the world's store), whenever this hook has nothing. Another mod can still call
 * {@link #setSource} once with a function from a player's id to the ammunition they have, or an empty value when the held item
 * reports none; the HUD shows that number, and the player's Arrakis_Ammo while the answer is empty (and "--" only if the player has no such
 * stat). The function is called on the world thread, once per world tick per player, so it must be cheap.
 */
public final class AmmoReadout {

    /** Gives the ammunition a player has, or empty when nothing is to be shown. */
    @FunctionalInterface
    public interface Source {
        OptionalInt ammo(UUID player);
    }

    private static volatile Source source = player -> OptionalInt.empty();

    /** A test number from {@code /arrakis hud rig ammo}, shown until {@code untilNanos}. */
    private record Rig(int value, long untilNanos) {
    }

    private static final Map<UUID, Rig> RIGS = new ConcurrentHashMap<>();

    private AmmoReadout() {
    }

    public static void setSource(Source newSource) {
        source = newSource == null ? player -> OptionalInt.empty() : newSource;
    }

    /** What to show for this player now: a test number while one is rigged, else what the source says. */
    static OptionalInt read(UUID player, long nowNanos) {
        Rig rig = RIGS.get(player);
        if (rig != null) {
            if (nowNanos < rig.untilNanos()) {
                return OptionalInt.of(rig.value());
            }
            RIGS.remove(player, rig);
        }
        return source.ammo(player);
    }

    /** Development: shows {@code value} for this player for {@code seconds}. */
    static void rig(UUID player, int value, double seconds) {
        RIGS.put(player, new Rig(value, System.nanoTime() + (long) (seconds * 1e9)));
    }

    static void forget(UUID player) {
        RIGS.remove(player);
    }
}
