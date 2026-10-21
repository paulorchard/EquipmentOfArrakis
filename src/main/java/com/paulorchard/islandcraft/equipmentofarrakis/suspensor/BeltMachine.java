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
    record Ground(boolean jumping, boolean onGround, long nowMs, long tick, double vy) {
        static final Ground NONE = new Ground(false, false, 0L, 0L, 0.0);

        Ground(boolean jumping, boolean onGround, long nowMs, long tick) {
            this(jumping, onGround, nowMs, tick, 0.0);
        }
    }

    /**
     * The descent (the belt's gravity), all live: {@code speed} blocks a second of sink while flying and not asking to go up (0 = off: the
     * hover of the earlier builds); {@code ascendFraction} of the vertical fly speed is what the player's own vertical speed must exceed to
     * count as asking to go up; {@code debounceTicks} of ignored input after the machine changes state; {@code groundTicks} consecutive
     * ticks on the ground (not ascending) before flight ends; {@code takeoffGrace} seconds after the flight start or the last ascending
     * tick in which the ground is ignored; {@code refreshSeconds} between safety re-sends of the push; {@code costFactor} multiplies the
     * Mana cost while DESCENDING.
     */
    record Descent(float speed, float ascendFraction, int debounceTicks, int groundTicks, float takeoffGrace, float refreshSeconds, float costFactor) {
        static final Descent OFF = new Descent(0f, 0.4f, 4, 2, 0.5f, 1.0f, 1.0f);

        boolean off() {
            return speed <= 0f;
        }
    }

    enum DescentState { NONE, UNDECIDED, DESCENDING, ASCENDING }

    /** The descent as the probe and `state` show it. */
    record DescentInfo(DescentState state, double own, double vy, boolean pushInForce, int sends, int refreshes, int drifts, int groundExits, int transitions) {
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
        /** Velocity Set (0, amount, 0) with the no-decay config: the descent push (amount is negative). */
        PUSH_DESCENT,
        /** Velocity Set (0, 0, 0) with the default config: the push ends. */
        CLEAR_PUSH,
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
    // the descent
    private DescentState dstate = DescentState.NONE;
    private boolean pushInForce;
    private float pushSpeed;
    private int debounce;
    private float sinceSend;
    private int driftTicks;
    private double ownBaseline;
    private double own;
    private double lastVy;
    private int groundContact;
    private int suppress;
    private float flightStartClock = -1f;
    private float lastAscendClock = -1f;
    private int descentSends;
    private int refreshSends;
    private int driftSends;
    private int groundExits;
    private int transitions;
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

    /** The descent state: DESCENDING, ASCENDING, the player's own vertical speed, the sends and the ground exits so far. */
    synchronized DescentInfo descent() {
        return new DescentInfo(dstate, own, lastVy, pushInForce, descentSends, refreshSends, driftSends, groundExits, transitions);
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

    synchronized List<Act> tick(float dt, boolean flyingNow, boolean modeOk, float mana, float manaPerSecond, float resumeMana,
                                FlySettings current, Speeds speeds, Gate gate, Ground ground) {
        return tick(dt, flyingNow, modeOk, mana, manaPerSecond, resumeMana, current, speeds, gate, ground, Descent.OFF);
    }

    /**
     * The same with the tap gate. While the belt is on and armed and the player is not flying, fly is Disabled except while the gate is
     * open; flying keeps it Allowed (see {@link #gateStep}). {@code gate} off gives the behaviour of the belt without a gate.
     */
    synchronized List<Act> tick(float dt, boolean flyingNow, boolean modeOk, float mana, float manaPerSecond, float resumeMana,
                                FlySettings current, Speeds speeds, Gate gate, Ground ground, Descent descent) {
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
        if (armed && mana > EMPTY) {
            descentStep(dt, flyingNow, wasFlying, ground, speeds, descent, out);
        } else if (pushInForce || dstate != DescentState.NONE) {
            endDescent(out);
        }
        if (armed) {
            if (flyingNow) {
                if (mana <= EMPTY) {
                    armed = false;
                    sinceStop = 0f;
                    flying = false;
                    endDescent(out);
                    out.add(Act.of(Kind.STOP_FLYING));
                    out.add(Act.of(Kind.HUM_OFF));
                    out.add(Act.of(Kind.NEEDS_MANA));
                    out.add(new Act(Kind.LOG, null, 0f, "cut off: out of Mana while flying"));
                } else {
                    float factor = dstate == DescentState.DESCENDING ? descent.costFactor() : 1f;
                    out.add(new Act(Kind.SPEND, null, manaPerSecond * dt * factor, null));
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

    private void clearPush(List<Act> out) {
        if (pushInForce) {
            out.add(Act.of(Kind.CLEAR_PUSH));
        }
        pushInForce = false;
        pushSpeed = 0f;
    }

    private void sendPush(float speed, List<Act> out) {
        out.add(new Act(Kind.PUSH_DESCENT, null, -speed, null));
        pushInForce = true;
        pushSpeed = -speed;
        sinceSend = 0f;
        driftTicks = 0;
        ownBaseline = own;
        descentSends++;
    }

    /** Every end of a flight, the cut-off and every reset: the push is cleared if one is in force and the descent state forgotten. */
    private void endDescent(List<Act> out) {
        clearPush(out);
        dstate = DescentState.NONE;
        groundContact = 0;
        debounce = 0;
        driftTicks = 0;
        suppress = 0;
        own = 0;
    }

    private void log(List<Act> out, String text) {
        out.add(new Act(Kind.LOG, null, 0f, text));
    }

    /**
     * One tick of the descent while the belt is armed (see {@link Descent}). The player's own vertical speed is read from the client's total,
     * which includes the push in force: own = vy + N while a push of -N is in force, own = vy otherwise. Ascending = own above
     * {@code ascendFraction} of the vertical fly speed. After every change of state the input is ignored for {@code debounceTicks} (the
     * client's report lags the change by a tick or two), so a held, released or tapped Space cannot flip the state every tick. A flight
     * starts UNDECIDED (no push) and decides when the debounce is over: ascending reads as ASCENDING and never pushes, otherwise DESCENDING.
     * The ground ends the flight after {@code groundTicks} ticks of onGround without ascending, never within {@code takeoffGrace} of the
     * flight start or the last ascending tick.
     */
    private void descentStep(float dt, boolean flyingNow, boolean wasFlying, Ground g, Speeds speeds, Descent d, List<Act> out) {
        if (!flyingNow) {
            if (pushInForce || dstate != DescentState.NONE) {
                endDescent(out);
            }
            return;
        }
        if (d.off()) {
            if (pushInForce || dstate != DescentState.NONE) {
                endDescent(out);
            }
            own = g.vy();
            lastVy = g.vy();
            return;
        }
        lastVy = g.vy();
        own = g.vy() - (pushInForce ? pushSpeed : 0f);
        boolean ascending = own > d.ascendFraction() * speeds.vertical();
        if (ascending) {
            lastAscendClock = clock;
        }
        if (suppress > 0) {
            suppress--;
            return;
        }
        if (!wasFlying || dstate == DescentState.NONE) {
            dstate = DescentState.UNDECIDED;
            debounce = d.debounceTicks();
            flightStartClock = clock;
            lastAscendClock = ascending ? clock : -1f;
            groundContact = 0;
            driftTicks = 0;
        }
        // the ground
        groundContact = g.onGround() && !ascending ? groundContact + 1 : 0;
        boolean grace = clock - flightStartClock < d.takeoffGrace() - EPS || (lastAscendClock >= 0f && clock - lastAscendClock < d.takeoffGrace() - EPS);
        if (groundContact >= d.groundTicks() && !grace) {
            groundExits++;
            log(out, String.format(java.util.Locale.ROOT, "descent: ground exit after %d ticks on the ground (own %.2f, vy %.2f, %s): flight ended, push cleared",
                    groundContact, own, g.vy(), dstate));
            endDescent(out);
            suppress = 10;
            out.add(Act.of(Kind.STOP_FLYING));
            out.add(Act.of(Kind.HUM_OFF));
            return;
        }
        // the state
        sinceSend += dt;
        if (debounce > 0) {
            debounce--;
        } else if (dstate == DescentState.DESCENDING) {
            if (ascending) {
                clearPush(out);
                dstate = DescentState.ASCENDING;
                debounce = d.debounceTicks();
                transitions++;
                log(out, String.format(java.util.Locale.ROOT, "descent: ASCENDING (own %.2f, vy %.2f): push cleared, sends so far %d", own, g.vy(), descentSends));
            } else {
                // a changed speed goes out at once; the drift of the reading from what the push should give, six ticks running, re-sends
                double expected = pushSpeed + ownBaseline;
                driftTicks = Math.abs(g.vy() - expected) > 0.5 ? driftTicks + 1 : 0;
                if (Math.abs(pushSpeed + d.speed()) > 1e-6) {
                    sendPush(d.speed(), out);
                    refreshSends++;
                } else if (driftTicks >= 6) {
                    sendPush(d.speed(), out);
                    driftSends++;
                    log(out, String.format(java.util.Locale.ROOT, "descent: drift refresh (vy %.2f, expected %.2f)", g.vy(), expected));
                }
            }
        } else if (!ascending) {
            sendPush(d.speed(), out);
            dstate = DescentState.DESCENDING;
            debounce = d.debounceTicks();
            transitions++;
            log(out, String.format(java.util.Locale.ROOT, "descent: DESCENDING (own %.2f, vy %.2f): push %.2f, sends so far %d", own, g.vy(), -d.speed(), descentSends));
        } else if (dstate == DescentState.UNDECIDED) {
            dstate = DescentState.ASCENDING;
            transitions++;
            log(out, String.format(java.util.Locale.ROOT, "descent: ASCENDING from the start (own %.2f, vy %.2f): no push", own, g.vy()));
        }
        // the safety refresh of a push that is in force
        if (dstate == DescentState.DESCENDING && pushInForce && sinceSend >= d.refreshSeconds() - EPS) {
            sendPush(d.speed(), out);
            refreshSends++;
        }
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
        endDescent(out);
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
