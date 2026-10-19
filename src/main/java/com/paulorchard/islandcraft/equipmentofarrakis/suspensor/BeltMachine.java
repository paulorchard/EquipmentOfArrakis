package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The Suspensor Belt of one player, as a state machine with no engine types: the toggle hooks only say what the player wants
 * ({@link #switchOn}, {@link #switchOff}, {@link #reset}) and {@link #tick} decides what to do about it, as a list of {@link Act}s.
 * The adapter ({@link SuspensorSystem}) carries the acts out. The rule that keeps the client quiet: a settings push is an act only
 * when the settings that should be in force differ from the ones last pushed, so a quiet belt sends nothing, tick after tick.
 */
final class BeltMachine {

    enum Mark { II, III }

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

    private Mark mark;
    private boolean armed;
    private boolean flying;
    private FlySettings snapshot;
    private FlySettings pushed;
    private boolean restorePending;
    private boolean snapshotTrusted = true;
    private float sinceStop;
    private int pushes;

    synchronized Mark mark() {
        return mark;
    }

    synchronized boolean isOn(Mark which) {
        return mark == which;
    }

    synchronized boolean isOn() {
        return mark != null;
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
        return mark == null && !restorePending;
    }

    /**
     * The player switched a mark on. {@code current} is the movement settings of the player as they are now (the snapshot, unless one
     * is already held: switching from one mark to the other keeps the first snapshot). Returns true when the belt starts armed
     * (enough Mana), false when it starts unarmed because Mana is under {@code resumeMana}.
     */
    synchronized boolean switchOn(Mark which, FlySettings current, float mana, float resumeMana) {
        boolean wasOn = mark != null;
        mark = which;
        if (restorePending && snapshotTrusted && snapshot != null) {
            restorePending = false;
        }
        if (snapshot == null && !restorePending) {
            snapshot = current;
            pushed = current;
            snapshotTrusted = true;
        }
        if (!wasOn) {
            armed = mana >= resumeMana;
            sinceStop = 0f;
        }
        return armed;
    }

    /** The player switched the belt off (or the other mark took over, which calls {@link #switchOn} right after). */
    synchronized void switchOff() {
        if (mark != null) {
            mark = null;
            restorePending = snapshot != null;
            armed = false;
        }
    }

    /**
     * Death, respawn, world change, disconnect: the belt is off and the movement settings must go back, but the snapshot is not
     * trusted (the world or the game mode may have changed them), so the settings are reset to the defaults instead.
     */
    synchronized void reset() {
        if (mark != null || snapshot != null) {
            mark = null;
            armed = false;
            restorePending = true;
            snapshotTrusted = false;
        }
    }

    /**
     * One world tick for this player. {@code modeOk} is false outside Adventure (the belt does nothing there and lets go).
     * {@code current} is the movement settings of the player now, only used when a snapshot is still missing.
     */
    synchronized List<Act> tick(float dt, boolean flyingNow, boolean modeOk, float mana, float manaPerSecond, float resumeMana,
                                FlySettings current, Function<Mark, Speeds> speeds) {
        List<Act> out = new ArrayList<>();
        if (mark != null && !modeOk) {
            mark = null;
            armed = false;
            restorePending = true;
            snapshotTrusted = false;
        }
        if (restorePending) {
            if (flyingNow || flying) {
                out.add(Act.of(Kind.STOP_FLYING));
                out.add(Act.of(Kind.HUM_OFF));
            }
            if (snapshotTrusted && snapshot != null) {
                out.add(new Act(Kind.RESTORE, snapshot, 0f, "snapshot"));
            } else {
                out.add(new Act(Kind.RESET, null, 0f, "defaults"));
            }
            pushes++;
            snapshot = null;
            pushed = null;
            restorePending = false;
            snapshotTrusted = true;
            flying = false;
            armed = false;
            return out;
        }
        if (mark == null) {
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
        Speeds s = speeds.apply(mark);
        FlySettings want = armed ? new FlySettings(Fly.ALLOWED, s.horizontal(), s.vertical())
                : new FlySettings(Fly.DISABLED, snapshot.horizontal(), snapshot.vertical());
        if (!want.equals(pushed)) {
            pushed = want;
            pushes++;
            out.add(new Act(Kind.PUSH, want, 0f, null));
        }
        return out;
    }
}
