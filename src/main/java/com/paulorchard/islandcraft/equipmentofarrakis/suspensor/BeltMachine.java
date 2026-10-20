package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The Suspensor Belt of one player, as a state machine with no engine types: the toggle hooks only say what the player wants
 * ({@link #switchOn}, {@link #switchOff}, {@link #reset}) and {@link #tick} decides what to do about it, as a list of {@link Act}s.
 * The adapter ({@link SuspensorSystem}) carries the acts out. The rule that keeps the client quiet: a settings push is an act only
 * when the settings that should be in force differ from the ones last pushed (the flying marks) or when a descent starts or ends
 * (the Mk I marks), so a quiet belt sends nothing, tick after tick.
 *
 * <p>Mk II and Mk III arm the client's own double tap of Space and charge Mana while it flies. Mk I (far fall, speed) watch the
 * fall: ARMED until their trigger fires, then ENGAGED (a slow descent) until the player is on the ground.
 */
final class BeltMachine {

    enum Mark {
        I_FAR, I_SPEED, II, III;

        boolean flies() {
            return this == II || this == III;
        }

        boolean falls() {
            return !flies();
        }
    }

    enum Fly { DISABLED, ALLOWED, FORCED }

    /** The three movement settings the flying marks write: the fly mode and the two fly speeds. */
    record FlySettings(Fly mode, float horizontal, float vertical) {
    }

    record Speeds(float horizontal, float vertical) {
    }

    /** The two physics numbers the settings route of Mk I changes. */
    record Physics(float mass, float drag) {
    }

    /** What Mk I needs from the config. */
    record FallRules(boolean velocityRoute, double descentSpeed, double threshold, int ticks, float factor, boolean massField, int pushTicks,
                     float manaPerSecond) {
        static final FallRules DEFAULT = new FallRules(false, 4.0, 12.0, 2, 30.25f, false, 2, 0f);
    }

    /** Everything the machine reads from the world in one tick. */
    record Sense(float dt, boolean flying, boolean modeOk, boolean falling, boolean fallingFar, boolean onGround, boolean inFluid, double vy,
                 double y, float health, float mana, FlySettings fly, Physics physics) {
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
        LOG,
        /** Write these mass and drag numbers and push once (the slow descent starts). */
        DESCENT_ON,
        /** Write these mass and drag numbers back and push once (the slow descent ends). */
        DESCENT_OFF,
        /** One Set velocity instruction: the vertical speed in {@code amount}. */
        VELOCITY
    }

    record Act(Kind kind, FlySettings settings, float amount, String text, Physics physics) {
        Act(Kind kind, FlySettings settings, float amount, String text) {
            this(kind, settings, amount, text, null);
        }

        static Act of(Kind kind) {
            return new Act(kind, null, 0f, null, null);
        }
    }

    /** Seconds before a flight that is still going after the belt cut it off is stopped again. */
    static final float STOP_AGAIN_SECONDS = 1f;

    /** Under this much Mana the belt cannot pay. */
    static final float EMPTY = 0.0001f;

    /** Ticks on the ground before a descent ends. */
    static final int GROUND_TICKS = 2;

    /** Seconds of not falling before a descent ends. */
    static final float RISING_SECONDS = 0.5f;

    private Mark mark;
    private Mark lastOff;
    private boolean armed;
    private boolean flying;
    private FlySettings snapshot;
    private FlySettings pushed;
    private boolean restorePending;
    private boolean snapshotTrusted = true;
    private float sinceStop;
    private int pushes;

    // Mk I
    private boolean engaged;
    private boolean engagedVelocity;
    private Physics descentSnap;
    private int streak;
    private int groundTicks;
    private int sinceVelocity;
    private float risingSeconds;
    private double engageY;
    private double engageVy;
    private float engageHealth;
    private double lastAirVy;
    private double fastestVy;
    private int engages;
    private int ends;
    private int velocityPushes;
    private double lastLanding = Double.NaN;

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

    synchronized boolean engaged() {
        return engaged;
    }

    synchronized int engages() {
        return engages;
    }

    synchronized int ends() {
        return ends;
    }

    synchronized int velocityPushes() {
        return velocityPushes;
    }

    /** The vertical speed at the last landing of a descent (NaN: none yet). */
    synchronized double lastLanding() {
        return lastLanding;
    }

    /** Whether the machine has nothing to do or remember (an idle one can be dropped). */
    synchronized boolean idle() {
        return mark == null && !restorePending;
    }

    /**
     * The player switched a mark on. {@code current} is the movement settings of the player as they are now (the snapshot of a
     * flying mark, unless one is already held: switching from one flying mark to the other keeps the first snapshot). Returns true
     * when the belt starts armed (a Mk I mark always does; a flying mark needs {@code resumeMana}).
     */
    synchronized boolean switchOn(Mark which, FlySettings current, float mana, float resumeMana) {
        boolean wasOn = mark != null;
        mark = which;
        if (restorePending && snapshotTrusted && snapshot != null && lastOff != null && lastOff.flies() && which.flies()) {
            restorePending = false;
        }
        if (snapshot == null && !restorePending && which.flies()) {
            snapshot = current;
            pushed = current;
            snapshotTrusted = true;
        }
        if (!wasOn) {
            armed = which.falls() || mana >= resumeMana;
            sinceStop = 0f;
            streak = 0;
        }
        return armed;
    }

    /** The player switched the belt off (or another mark took over, which calls {@link #switchOn} right after). */
    synchronized void switchOff() {
        if (mark != null) {
            lastOff = mark;
            mark = null;
            restorePending = snapshot != null || descentSnap != null || engaged;
            armed = false;
        }
    }

    /**
     * Death, respawn, world change, disconnect: the belt is off and the movement settings must go back, but the snapshot is not
     * trusted (the world or the game mode may have changed them), so the settings are reset to the defaults instead.
     */
    synchronized void reset() {
        if (snapshot != null || descentSnap != null || engaged) {
            restorePending = true;
            snapshotTrusted = false;
        }
        mark = null;
        armed = false;
    }

    /** The tick of a flying mark with nothing else to sense (the old shape of the call; the tests of Mk II and Mk III use it). */
    synchronized List<Act> tick(float dt, boolean flyingNow, boolean modeOk, float mana, float manaPerSecond, float resumeMana,
                                FlySettings current, Function<Mark, Speeds> speeds) {
        return tick(new Sense(dt, flyingNow, modeOk, false, false, true, false, 0.0, 0.0, 0f, mana, current, null), manaPerSecond, resumeMana,
                FallRules.DEFAULT, speeds);
    }

    /**
     * One world tick for this player. {@code modeOk} (in the sense) is false outside Adventure: the belt does nothing there and lets
     * go. {@code manaPerSecond} is the cost of flight; the cost of a descent is in {@code rules}.
     */
    synchronized List<Act> tick(Sense s, float manaPerSecond, float resumeMana, FallRules rules, Function<Mark, Speeds> speeds) {
        List<Act> out = new ArrayList<>();
        if (mark != null && !s.modeOk()) {
            mark = null;
            armed = false;
            restorePending = true;
            snapshotTrusted = false;
        }
        if (restorePending) {
            restore(s, out);
            return out;
        }
        if (mark == null) {
            return out;
        }
        if (mark.falls()) {
            fall(s, resumeMana, rules, out);
            return out;
        }
        if (snapshot == null) {
            snapshot = s.fly();
            pushed = s.fly();
        }
        boolean wasFlying = flying;
        flying = s.flying();
        if (armed) {
            if (s.flying()) {
                if (s.mana() <= EMPTY) {
                    armed = false;
                    sinceStop = 0f;
                    flying = false;
                    out.add(Act.of(Kind.STOP_FLYING));
                    out.add(Act.of(Kind.HUM_OFF));
                    out.add(Act.of(Kind.NEEDS_MANA));
                    out.add(new Act(Kind.LOG, null, 0f, "cut off: out of Mana while flying"));
                } else {
                    out.add(new Act(Kind.SPEND, null, manaPerSecond * s.dt(), null));
                    out.add(Act.of(Kind.HUM));
                }
            } else if (wasFlying) {
                out.add(Act.of(Kind.HUM_OFF));
            }
        } else if (s.flying()) {
            // cut off (or never armed) and the client still reports flight: say stop again, but not every tick
            sinceStop += s.dt();
            if (sinceStop >= STOP_AGAIN_SECONDS) {
                sinceStop = 0f;
                out.add(Act.of(Kind.STOP_FLYING));
            }
        } else if (s.mana() >= resumeMana) {
            armed = true;
            out.add(new Act(Kind.LOG, null, 0f, "armed: Mana is back"));
        }
        Speeds sp = speeds.apply(mark);
        FlySettings want = armed ? new FlySettings(Fly.ALLOWED, sp.horizontal(), sp.vertical())
                : new FlySettings(Fly.DISABLED, snapshot.horizontal(), snapshot.vertical());
        if (!want.equals(pushed)) {
            pushed = want;
            pushes++;
            out.add(new Act(Kind.PUSH, want, 0f, null));
        }
        return out;
    }

    /** The belt went off (or a Mk I mark replaced a flying one): stop flight, end a descent, put the settings back, once. */
    private void restore(Sense s, List<Act> out) {
        if (s.flying() || flying) {
            out.add(Act.of(Kind.STOP_FLYING));
            out.add(Act.of(Kind.HUM_OFF));
        }
        if (engaged) {
            engaged = false;
            ends++;
            out.add(new Act(Kind.LOG, null, 0f, "descent ended: belt off or reset (landing speed so far " + fmt(lastAirVy) + ")"));
        }
        if (!snapshotTrusted) {
            if (snapshot != null || descentSnap != null) {
                out.add(new Act(Kind.RESET, null, 0f, "defaults"));
                pushes++;
            }
        } else {
            if (snapshot != null) {
                out.add(new Act(Kind.RESTORE, snapshot, 0f, "snapshot"));
                pushes++;
            }
            if (descentSnap != null) {
                out.add(new Act(Kind.DESCENT_OFF, null, 0f, "snapshot", descentSnap));
                pushes++;
            }
        }
        snapshot = null;
        pushed = null;
        descentSnap = null;
        restorePending = false;
        snapshotTrusted = true;
        flying = false;
        armed = mark != null && mark.falls();
        streak = 0;
        groundTicks = 0;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** The Mk I marks: ARMED until the trigger fires, ENGAGED until the player is on the ground. */
    private void fall(Sense s, float resumeMana, FallRules rules, List<Act> out) {
        boolean paid = rules.manaPerSecond() > 0f;
        boolean eligible = !s.onGround() && !s.flying() && !s.inFluid();
        boolean fastDown = s.falling() && -s.vy() > rules.threshold();
        streak = eligible && fastDown ? streak + 1 : 0;
        if (!s.onGround()) {
            lastAirVy = s.vy();
        }
        if (!engaged) {
            if (!armed) {
                if (s.mana() < resumeMana) {
                    return;
                }
                armed = true;
                out.add(new Act(Kind.LOG, null, 0f, "armed: Mana is back"));
            }
            boolean trigger = mark == Mark.I_FAR ? eligible && s.fallingFar() : eligible && streak >= Math.max(1, rules.ticks());
            if (trigger && (!paid || s.mana() > EMPTY)) {
                engage(s, rules, out);
            }
            return;
        }
        fastestVy = Math.min(fastestVy, s.vy());
        groundTicks = s.onGround() ? groundTicks + 1 : 0;
        risingSeconds = s.vy() > -0.1 ? risingSeconds + s.dt() : 0f;
        String reason = null;
        if (groundTicks >= GROUND_TICKS) {
            reason = "landed";
        } else if (s.flying()) {
            reason = "flying";
        } else if (s.inFluid()) {
            reason = "fluid";
        } else if (risingSeconds > RISING_SECONDS) {
            reason = "not falling any more";
        } else if (paid && s.mana() <= EMPTY) {
            reason = "out of Mana";
        }
        if (reason != null) {
            end(s, reason, out);
            return;
        }
        if (paid) {
            out.add(new Act(Kind.SPEND, null, rules.manaPerSecond() * s.dt(), null));
        }
        if (engagedVelocity) {
            sinceVelocity++;
            if (sinceVelocity >= Math.max(1, rules.pushTicks()) && -s.vy() > rules.descentSpeed() + 0.25) {
                sinceVelocity = 0;
                velocityPushes++;
                out.add(new Act(Kind.VELOCITY, null, (float) -rules.descentSpeed(), null));
            }
        }
    }

    private void engage(Sense s, FallRules rules, List<Act> out) {
        engaged = true;
        engagedVelocity = rules.velocityRoute();
        engages++;
        engageY = s.y();
        engageVy = s.vy();
        engageHealth = s.health();
        fastestVy = s.vy();
        groundTicks = 0;
        risingSeconds = 0f;
        sinceVelocity = 0;
        String how;
        if (engagedVelocity) {
            how = "velocity route, target " + fmt(-rules.descentSpeed());
            if (-s.vy() > rules.descentSpeed() + 0.25) {
                velocityPushes++;
                out.add(new Act(Kind.VELOCITY, null, (float) -rules.descentSpeed(), null));
            }
        } else {
            Physics now = s.physics();
            descentSnap = now;
            Physics want = rules.massField() ? new Physics(now.mass() / rules.factor(), now.drag()) : new Physics(now.mass(), now.drag() * rules.factor());
            pushes++;
            out.add(new Act(Kind.DESCENT_ON, null, 0f, null, want));
            how = "settings route, " + (rules.massField() ? "mass " + now.mass() + " -> " + want.mass() : "drag " + now.drag() + " -> " + want.drag())
                    + " (factor " + rules.factor() + ")";
        }
        out.add(new Act(Kind.LOG, null, 0f, "descent ENGAGED by " + (mark == Mark.I_FAR ? "far fall" : "speed") + " at vertical speed " + fmt(s.vy())
                + ", height y " + fmt(s.y()) + ", " + how));
    }

    private void end(Sense s, String reason, List<Act> out) {
        engaged = false;
        ends++;
        if ("landed".equals(reason)) {
            lastLanding = lastAirVy;
        }
        if (descentSnap != null) {
            pushes++;
            out.add(new Act(Kind.DESCENT_OFF, null, 0f, "snapshot", descentSnap));
            descentSnap = null;
        }
        out.add(new Act(Kind.LOG, null, 0f, "descent END (" + reason + "): fell " + fmt(engageY - s.y()) + " blocks since the trigger; speed at the trigger "
                + fmt(engageVy) + ", at the landing " + fmt(lastAirVy) + ", fastest " + fmt(fastestVy) + "; health " + fmt(engageHealth) + " -> "
                + fmt(s.health())));
        streak = 0;
        groundTicks = 0;
        if ("out of Mana".equals(reason)) {
            armed = false;
            out.add(Act.of(Kind.NEEDS_MANA));
        }
    }
}
