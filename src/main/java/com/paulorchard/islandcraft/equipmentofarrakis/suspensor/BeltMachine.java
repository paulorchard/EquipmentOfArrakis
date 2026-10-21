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

    enum GateMode { AFTER_JUMP, GROUND_OPEN }

    /**
     * The tap gate: {@code window} seconds (0 = off: fly is Allowed whenever the belt is armed, as before the gate), the mode, and for
     * GROUND_OPEN the seconds of ground contact before the gate opens again.
     */
    record Gate(float window, GateMode mode, float reopen) {
        static final Gate OFF = new Gate(0f, GateMode.AFTER_JUMP, 1.2f);

        boolean off() {
            return window <= 0f;
        }
    }

    /** What the adapter reads of the player each tick for the gate: the jumping and onGround flags, the wall clock and the world tick. */
    record Ground(boolean jumping, boolean onGround, long nowMs, long tick) {
        static final Ground NONE = new Ground(false, false, 0L, 0L);
    }

    /** The gate as the probe and `state` show it. */
    record GateInfo(boolean open, long jumpMs, long jumpTick, long openMs, long openTick, int windows, int flightsInWindow, int flightsWithGap, int pushes) {
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
    // the tap gate
    private float clock;
    private boolean gateInit;
    private boolean prevJumping;
    private float sinceGround;
    private float groundTime;
    private float closeAt = -1f;
    private boolean latched;
    private boolean gateOpen;
    private float lastJumpClock = -1f;
    private long lastJumpMs;
    private long lastJumpTick;
    private long openMs;
    private long openTick;
    private int windows;
    private int flightsInWindow;
    private int flightsWithGap;

    /** Seconds after a jump start in which a jump still counts as a jump start (it can have left the ground a tick before it is seen). */
    static final float JUMP_GROUND_SLACK = 0.25f;
    /** A flight with no jump start within this many seconds counts as "with a gap" when the gate is off. */
    static final float DEFAULT_GAP = 0.35f;
    /** Float slack for the gate timers: 7 ticks of 0.05 s are a 0.35 s window. */
    private static final float EPS = 1e-4f;

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

    /** The gate state and the counters (windows opened, settings writes, flights started inside a window, flights started with a gap). */
    synchronized GateInfo gate() {
        return new GateInfo(gateOpen, lastJumpMs, lastJumpTick, openMs, openTick, windows, flightsInWindow, flightsWithGap, pushes);
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
            resetGate();
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
        return tick(dt, flyingNow, modeOk, mana, manaPerSecond, resumeMana, current, speeds, Gate.OFF, Ground.NONE);
    }

    /**
     * The same with the tap gate. While the belt is on and armed and the player is not flying, fly is Disabled except while the gate is
     * open; flying keeps it Allowed (see {@link #gateStep}). {@code gate} off gives the behaviour of the belt without a gate.
     */
    synchronized List<Act> tick(float dt, boolean flyingNow, boolean modeOk, float mana, float manaPerSecond, float resumeMana,
                                FlySettings current, Speeds speeds, Gate gate, Ground ground) {
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
        gateStep(dt, wasFlying, flyingNow, gate, ground);
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
        boolean allow = armed && (gate.off() || gateOpen);
        FlySettings want = allow ? new FlySettings(Fly.ALLOWED, speeds.horizontal(), speeds.vertical())
                : new FlySettings(Fly.DISABLED, snapshot.horizontal(), snapshot.vertical());
        if (!want.equals(pushed)) {
            boolean byGate = armed && !gate.off() && !flyingNow;
            if (byGate && allow) {
                openMs = ground.nowMs();
                openTick = ground.tick();
                out.add(new Act(Kind.LOG, null, 0f, String.format(java.util.Locale.ROOT,
                        "gate open (%s): jump start tick %d ms %d, push at tick %d ms %d (+%d ticks, +%d ms); window %.2f s",
                        gate.mode() == GateMode.AFTER_JUMP ? "afterJump" : "groundOpen", lastJumpTick, lastJumpMs, ground.tick(), ground.nowMs(),
                        ground.tick() - lastJumpTick, ground.nowMs() - lastJumpMs, gate.window())));
            } else if (byGate) {
                out.add(new Act(Kind.LOG, null, 0f, String.format(java.util.Locale.ROOT, "gate closed (%s): push at tick %d ms %d; window %.2f s",
                        gate.mode() == GateMode.AFTER_JUMP ? "afterJump" : "groundOpen", ground.tick(), ground.nowMs(), gate.window())));
            }
            pushed = want;
            pushes++;
            out.add(new Act(Kind.PUSH, want, 0f, null));
        }
        return out;
    }

    private void resetGate() {
        gateInit = false;
        prevJumping = false;
        sinceGround = 0f;
        groundTime = 0f;
        closeAt = -1f;
        latched = false;
        gateOpen = false;
    }

    /**
     * One tick of the tap gate (state only; the push comes from the settings that should be in force). A jump start is the jumping flag
     * rising while the player is not flying and was not flying a tick ago, on the ground or just off it. AFTER_JUMP: the gate opens at a jump
     * start and closes {@code window} seconds later (another jump start restarts the timer, no extra push). GROUND_OPEN: the gate is open
     * after {@code reopen} seconds of ground contact (at switch-on: if the player stands), closes {@code window} seconds after a jump start
     * and stays closed until the ground contact has lasted {@code reopen} again. Flying keeps the gate open; when flight ends it is closed.
     */
    private void gateStep(float dt, boolean wasFlying, boolean flyingNow, Gate gate, Ground g) {
        clock += dt;
        boolean jumpEdge = g.jumping() && !prevJumping;
        prevJumping = g.jumping();
        if (!gateInit) {
            gateInit = true;
            latched = !g.onGround();
            groundTime = g.onGround() ? gate.reopen() : 0f;
            sinceGround = g.onGround() ? 0f : JUMP_GROUND_SLACK + 1f;
        } else {
            groundTime = g.onGround() ? groundTime + dt : 0f;
            sinceGround = g.onGround() ? 0f : sinceGround + dt;
        }
        boolean jumpStart = jumpEdge && !flyingNow && !wasFlying && (g.onGround() || sinceGround <= JUMP_GROUND_SLACK);
        if (flyingNow && !wasFlying) {
            if (!gate.off() && gateOpen) {
                flightsInWindow++;
            }
            float gap = lastJumpClock < 0f ? Float.MAX_VALUE : clock - lastJumpClock;
            if (gap > (gate.off() ? DEFAULT_GAP : gate.window())) {
                flightsWithGap++;
            }
        }
        if (jumpStart) {
            lastJumpClock = clock;
            lastJumpMs = g.nowMs();
            lastJumpTick = g.tick();
        }
        boolean before = gateOpen;
        if (gate.off()) {
            gateOpen = true;
            closeAt = -1f;
            latched = false;
        } else if (flyingNow) {
            gateOpen = true;
            closeAt = -1f;
        } else {
            if (wasFlying) {
                closeAt = -1f;
                latched = true;
                groundTime = 0f;
            }
            if (gate.mode() == GateMode.AFTER_JUMP) {
                if (jumpStart) {
                    closeAt = clock + gate.window();
                }
                gateOpen = closeAt >= 0f && clock < closeAt - EPS;
            } else {
                if (jumpStart) {
                    closeAt = clock + gate.window();
                }
                if (closeAt >= 0f && clock >= closeAt - EPS) {
                    latched = true;
                    closeAt = -1f;
                }
                if (latched && groundTime >= gate.reopen() - EPS) {
                    latched = false;
                }
                gateOpen = !latched;
            }
        }
        if (gateOpen && !before && !flyingNow && !gate.off()) {
            windows++;
        }
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
        resetGate();
        restorePending = false;
        snapshotTrusted = true;
        flying = false;
        armed = false;
    }
}
