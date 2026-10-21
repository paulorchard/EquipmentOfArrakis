package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TEST DEVICE: the push test of one player (proof B of the descent), with no engine types. {@code /arrakis suspensor push} starts it,
 * {@link #tick} says what to send and what to log, the adapter ({@link TestDevices}) carries it out. One push ({@code ONCE}) sends a
 * single velocity and watches 3 s; {@code HOLD} sends it again every N ticks until it is stopped. Every way the push can end (off, the
 * flight ended, the belt went off, death, respawn, disconnect, a world change) ends in one CLEAR step: a velocity Set of zero with the
 * default config, so a player is never left with an external velocity. Delete with the proof (see the cleanup list in MODULES.md).
 */
final class PushRig {

    enum Kind {
        /** Velocity Set (0, y, 0) with the resistance given. */
        SEND,
        /** Velocity Set (0, 0, 0) with the default config. */
        CLEAR,
        /** An INFO line. */
        LOG
    }

    /** {@code resistance}: a number for both ground and air, or NaN for the config class's own defaults. */
    record Step(Kind kind, float y, float resistance, String text) {
    }

    static final long ONCE_WATCH_MS = 3000;
    static final long SAMPLE_MS = 100;

    /** Whether a push of this size is allowed under the cap. */
    static boolean allowed(double y, double cap) {
        return !Double.isNaN(y) && Math.abs(y) <= cap;
    }

    private boolean active;
    private boolean hold;
    private boolean cancel;
    private float y;
    private float resistance;
    private int every;
    private int ticks;
    private int sends;
    private long startMs;
    private long nextSampleMs;

    synchronized boolean active() {
        return active;
    }

    synchronized boolean holding() {
        return active && hold;
    }

    synchronized int sends() {
        return sends;
    }

    /** One push, then a 3 s watch; the velocity stays until cleared. */
    synchronized void startOnce(float y, float resistance, long nowMs) {
        begin(y, resistance, false, 1, nowMs);
    }

    /** The push again every {@code everyTicks} ticks (1 or more) until cleared. */
    synchronized void startHold(float y, float resistance, int everyTicks, long nowMs) {
        begin(y, resistance, true, Math.max(1, everyTicks), nowMs);
    }

    private void begin(float y, float resistance, boolean hold, int every, long nowMs) {
        this.active = true;
        this.hold = hold;
        this.cancel = false;
        this.y = y;
        this.resistance = resistance;
        this.every = every;
        this.ticks = 0;
        this.sends = 0;
        this.startMs = nowMs;
        this.nextSampleMs = nowMs;
    }

    /** Asks for the push to end; the next tick sends the CLEAR (death, respawn, world change, the command). */
    synchronized void cancel() {
        if (active) {
            cancel = true;
        }
    }

    /**
     * One world tick. The push ends (CLEAR) when asked to, when the player is no longer flying or the belt is off.
     * {@code clientVy} and {@code actualVy} are the two vertical speeds the watch logs.
     */
    synchronized List<Step> tick(boolean flying, boolean beltOn, long nowMs, double clientVy, double actualVy, boolean onGround) {
        List<Step> out = new ArrayList<>();
        if (!active) {
            return out;
        }
        if (cancel || !flying || !beltOn) {
            String why = cancel ? "push off" : !beltOn ? "the belt is off" : "flight ended";
            active = false;
            cancel = false;
            out.add(new Step(Kind.LOG, 0f, 0f, String.format(Locale.ROOT, "push ended (%s) after %d sends in %.1f s: velocity cleared", why, sends, (nowMs - startMs) / 1000.0)));
            out.add(new Step(Kind.CLEAR, 0f, 0f, null));
            return out;
        }
        if (ticks == 0 || (hold && ticks % every == 0)) {
            sends++;
            out.add(new Step(Kind.SEND, y, resistance, null));
        }
        ticks++;
        boolean watching = hold || nowMs <= startMs + ONCE_WATCH_MS;
        if (watching && nowMs >= nextSampleMs) {
            nextSampleMs = nowMs + SAMPLE_MS;
            double seconds = Math.max(0.001, (nowMs - startMs) / 1000.0);
            out.add(new Step(Kind.LOG, 0f, 0f, String.format(Locale.ROOT, "push y %.2f %s t=%.1fs client vy %.2f, actual vy %.2f, onGround %s, sends %d (%.1f a second)",
                    y, hold ? "hold every " + every : "once", seconds, clientVy, actualVy, onGround, sends, sends / seconds)));
        }
        return out;
    }
}
