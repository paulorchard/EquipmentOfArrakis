package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import java.util.Locale;

/**
 * The watch after {@code /arrakis suspensor impulse}: for two seconds, a line every 0.1 s with the client vertical speed, the server
 * side velocity, the number of velocity instructions still queued and the flying and onGround flags. No engine types.
 */
final class ImpulseWatch {

    static final long DURATION_MS = 2000;
    static final long INTERVAL_MS = 100;

    private final long startMs;
    private final String label;
    private long nextMs;

    ImpulseWatch(long startMs, String label) {
        this.startMs = startMs;
        this.label = label;
        this.nextMs = startMs;
    }

    /** A line when one is due, otherwise null. */
    String sample(long nowMs, double clientVy, double serverVy, int instructions, boolean flying, boolean onGround) {
        if (nowMs < nextMs || nowMs > startMs + DURATION_MS + INTERVAL_MS) {
            return null;
        }
        nextMs = nowMs + INTERVAL_MS;
        return String.format(Locale.ROOT, "%s t=%.1fs client vy %.2f, server vy %.2f, instructions %d, flying %s, onGround %s", label, (nowMs - startMs) / 1000.0,
                clientVy, serverVy, instructions, flying, onGround);
    }

    boolean finished(long nowMs) {
        return nowMs > startMs + DURATION_MS + INTERVAL_MS;
    }
}
