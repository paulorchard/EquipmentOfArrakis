package com.paulorchard.islandcraft.equipmentofarrakis.thumper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The hand-over from the gate to the placement: the gate works out where the Thumper goes and checks it; the placement, next in the chain,
 * spawns it exactly there. One spot per player, valid for a couple of seconds. No engine class.
 */
final class ThumperPending {

    /** The spot a Thumper is to be spawned at, and a note of how the gate judged it (for the log). */
    record Spot(double x, double y, double z, String note, long atNanos) {
    }

    static final long VALID_NANOS = 2_000_000_000L;
    private static final Map<UUID, Spot> SPOTS = new ConcurrentHashMap<>();

    private ThumperPending() {
    }

    static void put(UUID player, Spot spot) {
        SPOTS.put(player, spot);
    }

    /** The player's spot if there is one that is still valid; it is removed either way. */
    static Spot take(UUID player, long now) {
        Spot spot = SPOTS.remove(player);
        return spot != null && now - spot.atNanos() <= VALID_NANOS ? spot : null;
    }

    static void forget(UUID player) {
        SPOTS.remove(player);
    }
}
