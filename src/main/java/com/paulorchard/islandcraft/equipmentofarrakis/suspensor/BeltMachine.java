package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Suspensor Belt of one player, as a state machine with no engine types: the toggle hooks only say what the player wants
 * ({@link #switchOn}, {@link #switchOff}, {@link #reset}) and {@link #tick} decides what to do about it, as a list of {@link Act}s.
 * The adapter ({@link SuspensorSystem}) carries the acts out. The rule that keeps the client quiet: a settings push is an act only
 * when the settings that should be in force differ from the ones last pushed.
 *
 * <p>While the belt is on and armed, the movement setting {@code fly} is Allowed with the belt's fly speeds, so the client's own
 * double tap of Space starts creative-style flight. The machine only watches the flying flag, charges Mana while it lasts, and cuts
 * the flight off at 0 Mana (the belt disarms until Mana is back at the resume value).
 */
final class BeltMachine {

    enum Fly { DISABLED, ALLOWED, FORCED }

    /** The three movement settings the belt writes: the fly mode and the two fly speeds. */
    record FlySettings(Fly mode, float horizontal, float vertical) {
    }

    record Speeds(float horizontal, float vertical) {
    }

    enum Kind {
        /** Write these three settings and push once. */
        PUSH,
        /** Player.setFlying(false). */
        STOP_FLYING,
        /** Write the snapshot back and push once. */
        RESTORE,
        /** MovementManager.resetDefaultsAndUpdate. */
        RESET,
        /** Spend this much Mana. */
        SPEND,
        /** Refresh the hum effect. */
        HUM,
        /** Remove the hum effect. */
        HUM_OFF,
        /** "The belt needs Mana." */
        NEEDS_MANA,
        /** An INFO log line. */
        LOG
    }

    record Act(Kind kind, FlySettings settings, float amount, String text) {
        static Act of(Kind kind) {
            return new Act(kind, null, 0f, null);
        }
    }

    /** Seconds before a flight that is still going after the belt cut it off is stopped again. */
    static final float STOP_AGAIN_SECONDS = 1f;

    /** Under this much Mana the belt cannot pay. */
    static final float EMPTY = 0.0001f;

    private boolean on;
    private boolean armed;
    private boolean flying;
    private FlySettings snapshot;
    private FlySettings pushed;
    private boolean restorePending;
    private boolean snapshotTrusted = true;
    private float sinceStop;
    private int pushes;

    synchronized boolean isOn() {
        return on;
    }

    synchronized boolean armed() {
        return armed;
    }

    synchronized boolean flying() {
        return flying;
    }

    synchronized FlySettings snapshot() {
        return snapshot;
    }

    synchronized FlySettings pushed() {
        return pushed;
    }

    synchronized boolean restorePending() {
        return restorePending;
    }

    /** How many settings writes (pushes or restores) the machine has asked for: state changes only. */
    synchronized int pushes() {
        return pushes;
    }

    /** Whether the machine has nothing to do or remember (an idle one can be dropped). */
    synchronized boolean idle() {
        return !on && !restorePending;
    }

    /**
     * The player switched the belt on. {@code current} is the movement settings of the player as they are now (the snapshot, unless
     * one is already held). Returns true when the belt starts armed (it needs {@code resumeMana}).
     */
    synchronized boolean switchOn(FlySettings current, float mana, float resumeMana) {
        if (!on) {
            on = true;
            armed = mana >= resumeMana;
            sinceStop = 0f;
        }
        if (snapshot == null && !restorePending) {
            snapshot = current;
            pushed = current;
            snapshotTrusted = true;
        }
        return armed;
    }

    /** The player switched the belt off. */
    synchronized void switchOff() {
        if (on) {
            on = false;
            restorePending = snapshot != null;
            armed = false;
        }
    }

    /**
     * Death, respawn, world change, disconnect: the belt is off and the movement settings must go back, but the snapshot is not
     * trusted (the world or the game mode may have changed them), so the settings are reset to the defaults instead.
     */
    synchronized void reset() {
        if (snapshot != null) {
            restorePending = true;
            snapshotTrusted = false;
        }
        on = false;
        armed = false;
    }

    /**
     * One world tick for this player. {@code modeOk} is false outside Adventure: the belt does nothing there and lets go.
     * {@code manaPerSecond} is the cost of flight; {@code speeds} are the numbers in force now (they may change between ticks).
     */
    synchronized List<Act> tick(float dt, boolean flyingNow, boolean modeOk, float mana, float manaPerSecond, float resumeMana,
                                FlySettings current, Speeds speeds) {
        List<Act> out = new ArrayList<>();
        if (on && !modeOk) {
            on = false;
            armed = false;
            restorePending = true;
            snapshotTrusted = false;
        }
        if (restorePending) {
            restore(flyingNow, out);
            return out;
        }
        if (!on) {
            return out;
        }
        if (snapshot == null) {
            snapshot = current;
            pushed = current;
        }
        boolean wasFlying = flying;
        flying = flyingNow;
        if (armed) {
            if (flyingNow) {
                if (mana <= EMPTY) {
                    armed = false;
                    sinceStop = 0f;
                    flying = false;
                    out.add(Act.of(Kind.STOP_FLYING));
                    out.add(Act.of(Kind.HUM_OFF));
                    out.add(Act.of(Kind.NEEDS_MANA));
                    out.add(new Act(Kind.LOG, null, 0f, "cut off: out of Mana while flying"));
                } else {
                    out.add(new Act(Kind.SPEND, null, manaPerSecond * dt, null));
                    out.add(Act.of(Kind.HUM));
                }
            } else if (wasFlying) {
                out.add(Act.of(Kind.HUM_OFF));
            }
        } else if (flyingNow) {
            // cut off (or never armed) and the client still reports flight: say stop again, but not every tick
            sinceStop += dt;
            if (sinceStop >= STOP_AGAIN_SECONDS) {
                sinceStop = 0f;
                out.add(Act.of(Kind.STOP_FLYING));
            }
        } else if (mana >= resumeMana) {
            armed = true;
            out.add(new Act(Kind.LOG, null, 0f, "armed: Mana is back"));
        }
        FlySettings want = armed ? new FlySettings(Fly.ALLOWED, speeds.horizontal(), speeds.vertical())
                : new FlySettings(Fly.DISABLED, snapshot.horizontal(), snapshot.vertical());
        if (!want.equals(pushed)) {
            pushed = want;
            pushes++;
            out.add(new Act(Kind.PUSH, want, 0f, null));
        }
        return out;
    }

    /** The belt went off: stop flight, put the settings back, once. */
    private void restore(boolean flyingNow, List<Act> out) {
        if (flyingNow || flying) {
            out.add(Act.of(Kind.STOP_FLYING));
            out.add(Act.of(Kind.HUM_OFF));
        }
        if (snapshot != null) {
            pushes++;
            out.add(snapshotTrusted ? new Act(Kind.RESTORE, snapshot, 0f, "snapshot") : new Act(Kind.RESET, null, 0f, "defaults"));
        }
        snapshot = null;
        pushed = null;
        restorePending = false;
        snapshotTrusted = true;
        flying = false;
        armed = false;
    }
}
