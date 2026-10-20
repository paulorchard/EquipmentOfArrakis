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
 * (the Mk I marks); the instruction routes of Mk I refresh their velocity instruction at a limited rate and count it.
 *
 * <p>Mk II and Mk III arm the client's own double tap of Space and charge Mana while it flies. Mk I (far fall, speed) watch the
 * fall: ARMED until their trigger fires, then ENGAGED until the player is on the ground. How the descent is slowed is the
 * {@link Way}: FLYGLIDE (fly, with an external downward velocity of the target speed), AIRDRAG (the air drag multipliers of the
 * movement settings) or COUNTER (an upward external velocity that cancels the excess fall speed).
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

    /** How the Mk I descent is applied. */
    enum Way { FLYGLIDE, AIRDRAG, COUNTER, GLIDER }

    /** What the glider route takes as "falling": the raw flag (default), the speed rule or the fallingFar flag. */
    enum Trigger { FALLING, SPEED, FARFALL }

    /** How the glider signal is sent: LEVEL refreshes the effect while the trigger holds, PULSE applies it once with a short duration. */
    enum Signal { LEVEL, PULSE }

    /** The three movement settings the flying marks write: the fly mode and the two fly speeds. */
    record FlySettings(Fly mode, float horizontal, float vertical) {
    }

    record Speeds(float horizontal, float vertical) {
    }

    /** The two air drag multipliers of the movement settings, which the AIRDRAG way changes. */
    record Drag(float min, float max) {
    }

    /** What Mk I needs from the config. */
    record FallRules(Way way, double descentSpeed, double threshold, int ticks, int pushTicks, double tolerance, float glideHorizontal, float airDrag,
                     boolean reportsTotal, float manaPerSecond, Trigger trigger, int fallingTicks, Signal signal, float pulseSeconds, float pulseRetrySeconds,
                     int pulseRetries) {
        static final FallRules DEFAULT = new FallRules(Way.FLYGLIDE, 4.0, 12.0, 2, 5, 0.75, 1.0f, 0.9712f, true, 0f);

        FallRules(Way way, double descentSpeed, double threshold, int ticks, int pushTicks, double tolerance, float glideHorizontal, float airDrag,
                  boolean reportsTotal, float manaPerSecond) {
            this(way, descentSpeed, threshold, ticks, pushTicks, tolerance, glideHorizontal, airDrag, reportsTotal, manaPerSecond, Trigger.FALLING, 1, Signal.LEVEL, 0.15f, 1.0f, 1);
        }

        FallRules(Way way, double descentSpeed, double threshold, int ticks, int pushTicks, double tolerance, float glideHorizontal, float airDrag,
                  boolean reportsTotal, float manaPerSecond, Trigger trigger, int fallingTicks) {
            this(way, descentSpeed, threshold, ticks, pushTicks, tolerance, glideHorizontal, airDrag, reportsTotal, manaPerSecond, trigger, fallingTicks, Signal.LEVEL, 0.15f, 1.0f, 1);
        }

        FallRules withWay(Way other) {
            return new FallRules(other, descentSpeed, threshold, ticks, pushTicks, tolerance, glideHorizontal, airDrag, reportsTotal, manaPerSecond, trigger, fallingTicks, signal, pulseSeconds, pulseRetrySeconds, pulseRetries);
        }

        FallRules withPulse(float seconds, float retrySeconds, int retries) {
            return new FallRules(way, descentSpeed, threshold, ticks, pushTicks, tolerance, glideHorizontal, airDrag, reportsTotal, manaPerSecond, trigger, fallingTicks, Signal.PULSE, seconds,
                    retrySeconds, retries);
        }

        FallRules withTrigger(Trigger other, int fallTicks) {
            return new FallRules(way, descentSpeed, threshold, ticks, pushTicks, tolerance, glideHorizontal, airDrag, reportsTotal, manaPerSecond, other, fallTicks, signal, pulseSeconds, pulseRetrySeconds, pulseRetries);
        }
    }

    /** Everything the machine reads from the world in one tick. */
    record Sense(float dt, boolean flying, boolean modeOk, boolean falling, boolean fallingFar, boolean onGround, boolean inFluid, double vy,
                 double y, float health, float mana, FlySettings fly, Drag drag, boolean gliding) {
        Sense(float dt, boolean flying, boolean modeOk, boolean falling, boolean fallingFar, boolean onGround, boolean inFluid, double vy, double y,
              float health, float mana, FlySettings fly, Drag drag) {
            this(dt, flying, modeOk, falling, fallingFar, onGround, inFluid, vy, y, health, mana, fly, drag, false);
        }
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
        /** Write these fly settings, then Player.setFlying(true) (one push). */
        GLIDE_ON,
        /** Write these fly settings (the snapshot), then Player.setFlying(false) (one push). */
        GLIDE_OFF,
        /** Write these air drag numbers and push once. */
        DRAG_ON,
        /** Write the air drag numbers back and push once. */
        DRAG_OFF,
        /** One Set instruction of (0, amount, 0) with a config that does not decay. */
        VELOCITY,
        /** One Set instruction of (0, 0, 0) with the default config: the external velocity is cleared. */
        VELOCITY_CLEAR,
        /** Apply the falling signal effect (amount 1: the first time of a descent, 0: a refresh). */
        EFFECT_ON,
        /** Apply the falling signal effect once for {@code amount} seconds (the pulse; text says "pulse n" or "retry n"). */
        EFFECT_PULSE,
        /** Remove the falling signal effect. */
        EFFECT_OFF,
        /** Take the glider item out of the Utility slot (only ours). */
        ITEM_REMOVE
    }

    record Act(Kind kind, FlySettings settings, float amount, String text, Drag drag) {
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

    /** Seconds of not falling before a descent ends (AIRDRAG, COUNTER). */
    static final float RISING_SECONDS = 0.5f;

    /** Seconds a glide gets to start (the flying flag, the first steady speed) before the stall and flight-lost rules apply. */
    static final float GRACE_SECONDS = 0.5f;

    /** Seconds between two refreshes of the falling signal while the trigger holds and the player is not gliding. */
    static final float EFFECT_REFRESH_SECONDS = 0.25f;

    /** Ticks without vertical movement that count as touching down in a glide. */
    static final int STALL_TICKS = 6;

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
    private Way engagedWay;
    private FlySettings glideSnap;
    private Drag dragSnap;
    private boolean instrActive;
    private double extCur;
    private int streak;
    private int fallStreak;
    private boolean effectOn;
    private float effectClock;
    private boolean itemPlaced;
    // glider route counters and the pulse state
    private int pulses;
    private int retriesUsed;
    private float pulseClock;
    private boolean glideSeenSincePulse;
    private boolean prevGliding;
    private int glideOnFlips;
    private int glideOffFlips;
    private float glideStartSeconds;
    private float longestGlide;
    private long tickNo;
    private long firstGroundTick;
    private long lastGlideOffTick;
    private Signal engagedSignal = Signal.LEVEL;
    private int groundTicks;
    private int sinceRefresh;
    private int stallTicks;
    private int lostTicks;
    private float risingSeconds;
    private float engagedSeconds;
    private double lastY;
    private double engageY;
    private double engageVy;
    private float engageHealth;
    private double lastAirVy;
    private double fastestVy;
    private int engages;
    private int ends;
    private int refreshes;
    private String lastEnd = "";
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

    synchronized Way engagedWay() {
        return engagedWay;
    }

    synchronized int engages() {
        return engages;
    }

    synchronized int ends() {
        return ends;
    }

    /** How many times the velocity instruction was sent or refreshed (not counting the clears). */
    synchronized int refreshes() {
        return refreshes;
    }

    /** The vertical speed at the last landing of a descent (NaN: none yet). */
    synchronized double lastLanding() {
        return lastLanding;
    }

    synchronized String lastEnd() {
        return lastEnd;
    }

    /** Whether an external velocity instruction was sent and not cleared yet. */
    synchronized boolean instructionActive() {
        return instrActive;
    }

    /** The adapter says the glider item is in the Utility slot because of this belt (or is not any more). */
    synchronized void itemPlaced(boolean placed) {
        itemPlaced = placed;
    }

    synchronized boolean itemPlaced() {
        return itemPlaced;
    }

    /** Pulses sent in the current or last glider descent (the first one and the retries). */
    synchronized int pulses() {
        return pulses;
    }

    synchronized int retries() {
        return retriesUsed;
    }

    synchronized boolean effectOn() {
        return effectOn;
    }

    /** Whether the machine has nothing to do or remember (an idle one can be dropped). */
    synchronized boolean idle() {
        return mark == null && !restorePending;
    }

    private boolean touched() {
        return snapshot != null || glideSnap != null || dragSnap != null || instrActive || engaged || effectOn || itemPlaced;
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
            restorePending = touched();
            armed = false;
        }
    }

    /**
     * Death, respawn, world change, disconnect: the belt is off and the movement settings must go back, but the snapshot is not
     * trusted (the world or the game mode may have changed them), so the settings are reset to the defaults instead; a velocity
     * instruction that was active is cleared.
     */
    synchronized void reset() {
        if (touched()) {
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

    /** The belt went off (or a Mk I mark replaced a flying one): stop flight, clear the instruction, end a descent, put the settings back, once. */
    private void restore(Sense s, List<Act> out) {
        boolean glideHandlesFlight = snapshotTrusted && glideSnap != null;
        if ((s.flying() || flying) && !glideHandlesFlight) {
            out.add(Act.of(Kind.STOP_FLYING));
            out.add(Act.of(Kind.HUM_OFF));
        }
        if (engaged) {
            engaged = false;
            ends++;
            lastEnd = "belt off or reset";
            out.add(new Act(Kind.LOG, null, 0f, "descent ended: belt off or reset (landing speed so far " + fmt(lastAirVy) + ")"));
        }
        if (instrActive) {
            out.add(Act.of(Kind.VELOCITY_CLEAR));
            instrActive = false;
        }
        if (effectOn) {
            effectOn = false;
            out.add(new Act(Kind.EFFECT_OFF, null, 0f, "belt off or reset"));
        }
        if (itemPlaced && (mark == null || !mark.falls())) {
            itemPlaced = false;
            out.add(Act.of(Kind.ITEM_REMOVE));
        }
        if (!snapshotTrusted) {
            if (snapshot != null || glideSnap != null || dragSnap != null) {
                out.add(new Act(Kind.RESET, null, 0f, "defaults"));
                pushes++;
            }
        } else {
            if (snapshot != null) {
                out.add(new Act(Kind.RESTORE, snapshot, 0f, "snapshot"));
                pushes++;
            }
            if (glideSnap != null) {
                out.add(new Act(Kind.GLIDE_OFF, glideSnap, 0f, "snapshot"));
                pushes++;
            }
            if (dragSnap != null) {
                out.add(new Act(Kind.DRAG_OFF, null, 0f, "snapshot", dragSnap));
                pushes++;
            }
        }
        snapshot = null;
        pushed = null;
        glideSnap = null;
        dragSnap = null;
        engagedWay = null;
        restorePending = false;
        snapshotTrusted = true;
        flying = false;
        armed = mark != null && mark.falls();
        streak = 0;
        fallStreak = 0;
        groundTicks = 0;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /**
     * The upward (or downward) external velocity that makes the client's total vertical speed the target. {@code reportsTotal}: the
     * reported client velocity already includes the external part sent before ({@code extCur}), so the client's own part is the
     * difference.
     */
    static double counterExternal(double vy, double extCur, double target, boolean reportsTotal) {
        double own = reportsTotal ? vy - extCur : vy;
        return -target - own;
    }

    /** The Mk I marks: ARMED until the trigger fires, ENGAGED until the player is on the ground. */
    private void fall(Sense s, float resumeMana, FallRules rules, List<Act> out) {
        boolean paid = rules.manaPerSecond() > 0f;
        boolean eligible = !s.onGround() && !s.flying() && !s.inFluid();
        boolean fastDown = s.falling() && -s.vy() > rules.threshold();
        streak = eligible && fastDown ? streak + 1 : 0;
        fallStreak = eligible && s.falling() ? fallStreak + 1 : 0;
        boolean gliderWay = rules.way() == Way.GLIDER;
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
            boolean trigger = gliderWay ? !s.gliding() && gliderTrigger(rules, eligible, s)
                    : mark == Mark.I_FAR ? eligible && s.fallingFar() : eligible && streak >= Math.max(1, rules.ticks());
            if (trigger && (!paid || s.mana() > EMPTY)) {
                engage(s, rules, out);
            }
            return;
        }
        boolean glide = engagedWay == Way.FLYGLIDE;
        fastestVy = Math.min(fastestVy, s.vy());
        groundTicks = s.onGround() ? groundTicks + 1 : 0;
        engagedSeconds += s.dt();
        tickNo++;
        if (groundTicks == 1) {
            firstGroundTick = tickNo;
        }
        if (gliderWay) {
            trackGlide(s);
        }
        sinceRefresh++;
        risingSeconds = s.vy() > -0.1 ? risingSeconds + s.dt() : 0f;
        boolean settled = engagedSeconds > GRACE_SECONDS;
        stallTicks = glide && settled && Math.abs(s.y() - lastY) < 0.02 && Math.abs(s.vy()) < 0.5 ? stallTicks + 1 : 0;
        lostTicks = glide && settled && !s.flying() ? lostTicks + 1 : 0;
        lastY = s.y();
        String reason = null;
        if (groundTicks >= GROUND_TICKS) {
            reason = "landed";
        } else if (stallTicks >= STALL_TICKS) {
            reason = "landed (stalled, no vertical movement)";
        } else if (s.inFluid()) {
            reason = "fluid";
        } else if (glide && lostTicks >= STALL_TICKS) {
            reason = "flight lost";
        } else if (!glide && s.flying()) {
            reason = "flying";
        } else if (!glide && !gliderWay && risingSeconds > RISING_SECONDS) {
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
        if (gliderWay && engagedSignal == Signal.PULSE) {
            pulseClock += s.dt();
            if (s.gliding()) {
                glideSeenSincePulse = true;
            } else if (!glideSeenSincePulse && pulseClock >= rules.pulseRetrySeconds() && retriesUsed < rules.pulseRetries()) {
                retriesUsed++;
                pulses++;
                pulseClock = 0f;
                out.add(new Act(Kind.EFFECT_PULSE, null, rules.pulseSeconds(), "retry " + retriesUsed + " (no glide " + fmt(rules.pulseRetrySeconds()) + " s after the last pulse)"));
            }
        } else if (gliderWay) {
            effectClock += s.dt();
            if (!s.gliding() && effectClock >= EFFECT_REFRESH_SECONDS && gliderTrigger(rules, eligible, s)) {
                effectClock = 0f;
                out.add(new Act(Kind.EFFECT_ON, null, 0f, null));
            }
        }
        if (sinceRefresh >= Math.max(1, rules.pushTicks())) {
            if (engagedWay == Way.FLYGLIDE && Math.abs(s.vy() + rules.descentSpeed()) > rules.tolerance()) {
                refresh(-rules.descentSpeed(), out);
            } else if (engagedWay == Way.COUNTER && s.vy() < -rules.descentSpeed() - rules.tolerance()) {
                refresh(counterExternal(s.vy(), extCur, rules.descentSpeed(), rules.reportsTotal()), out);
            }
        }
    }

    /** The glider route trigger: the raw falling flag (with the ticks), the speed rule or fallingFar; the caller has checked the player is not gliding. */
    private boolean gliderTrigger(FallRules rules, boolean eligible, Sense s) {
        if (!eligible) {
            return false;
        }
        return switch (rules.trigger()) {
            case FALLING -> fallStreak >= Math.max(1, rules.fallingTicks());
            case SPEED -> streak >= Math.max(1, rules.ticks());
            case FARFALL -> s.fallingFar();
        };
    }

    /** Counts the flips of the gliding flag and the glide lengths of the descent. */
    private void trackGlide(Sense s) {
        if (s.gliding() && !prevGliding) {
            glideOnFlips++;
            glideStartSeconds = engagedSeconds;
        } else if (!s.gliding() && prevGliding) {
            glideOffFlips++;
            lastGlideOffTick = tickNo;
            longestGlide = Math.max(longestGlide, engagedSeconds - glideStartSeconds);
        }
        prevGliding = s.gliding();
    }

    private void refresh(double ext, List<Act> out) {
        sinceRefresh = 0;
        extCur = ext;
        instrActive = true;
        refreshes++;
        out.add(new Act(Kind.VELOCITY, null, (float) ext, null));
    }

    private void engage(Sense s, FallRules rules, List<Act> out) {
        engaged = true;
        engagedWay = rules.way();
        engages++;
        engageY = s.y();
        lastY = s.y();
        engageVy = s.vy();
        engageHealth = s.health();
        fastestVy = s.vy();
        groundTicks = 0;
        stallTicks = 0;
        lostTicks = 0;
        effectClock = 0f;
        risingSeconds = 0f;
        engagedSeconds = 0f;
        sinceRefresh = 0;
        extCur = 0;
        String how;
        switch (rules.way()) {
            case FLYGLIDE -> {
                glideSnap = s.fly();
                pushes++;
                out.add(new Act(Kind.GLIDE_ON, new FlySettings(Fly.FORCED, rules.glideHorizontal(), 0f), 0f, null));
                refresh(-rules.descentSpeed(), out);
                how = "flyglide: fly Forced, h " + rules.glideHorizontal() + " v 0, external velocity " + fmt(-rules.descentSpeed());
            }
            case AIRDRAG -> {
                dragSnap = s.drag();
                pushes++;
                out.add(new Act(Kind.DRAG_ON, null, 0f, null, new Drag(rules.airDrag(), rules.airDrag())));
                how = "airdrag: airDragMin/Max " + fmt(s.drag().min()) + "/" + fmt(s.drag().max()) + " -> " + rules.airDrag();
            }
            case GLIDER -> {
                effectOn = true;
                effectClock = 0f;
                engagedSignal = rules.signal();
                pulses = 0;
                retriesUsed = 0;
                pulseClock = 0f;
                glideSeenSincePulse = false;
                prevGliding = s.gliding();
                glideOnFlips = 0;
                glideOffFlips = 0;
                glideStartSeconds = 0f;
                longestGlide = 0f;
                tickNo = 0;
                firstGroundTick = Long.MAX_VALUE;
                lastGlideOffTick = Long.MIN_VALUE / 2;
                if (engagedSignal == Signal.PULSE) {
                    pulses = 1;
                    out.add(new Act(Kind.EFFECT_PULSE, null, rules.pulseSeconds(), "pulse 1"));
                } else {
                    out.add(new Act(Kind.EFFECT_ON, null, 1f, null));
                }
                how = "glider: the falling signal effect applied (signal " + engagedSignal.name().toLowerCase(Locale.ROOT) + ", trigger " + rules.trigger().name().toLowerCase(Locale.ROOT)
                        + "), nothing else is done to the player";
            }
            default -> {
                how = "counter: target " + fmt(-rules.descentSpeed());
                if (s.vy() < -rules.descentSpeed() - rules.tolerance()) {
                    refresh(counterExternal(s.vy(), 0, rules.descentSpeed(), rules.reportsTotal()), out);
                    how += ", first external velocity " + fmt(extCur);
                }
            }
        }
        out.add(new Act(Kind.LOG, null, 0f, "descent ENGAGED by " + (rules.way() == Way.GLIDER ? "trigger " + rules.trigger().name().toLowerCase(Locale.ROOT) : mark == Mark.I_FAR ? "far fall" : "speed") + " at vertical speed " + fmt(s.vy())
                + ", height y " + fmt(s.y()) + ", " + how));
    }

    private void end(Sense s, String reason, List<Act> out) {
        engaged = false;
        ends++;
        lastEnd = reason;
        if (reason.startsWith("landed")) {
            lastLanding = lastAirVy;
        }
        if (instrActive) {
            instrActive = false;
            out.add(Act.of(Kind.VELOCITY_CLEAR));
        }
        if (effectOn) {
            effectOn = false;
            out.add(new Act(Kind.EFFECT_OFF, null, 0f, reason));
        }
        if (glideSnap != null) {
            pushes++;
            out.add(new Act(Kind.GLIDE_OFF, glideSnap, 0f, "snapshot"));
            glideSnap = null;
        }
        if (dragSnap != null) {
            pushes++;
            out.add(new Act(Kind.DRAG_OFF, null, 0f, "snapshot", dragSnap));
            dragSnap = null;
        }
        if (engagedWay == Way.GLIDER) {
            if (s.gliding() != prevGliding) {
                trackGlide(s);
            }
            if (prevGliding) {
                longestGlide = Math.max(longestGlide, engagedSeconds - glideStartSeconds);
            }
            String byItself = glideOnFlips == 0 ? "no glide opened" : !s.gliding() && glideOffFlips > 0 && lastGlideOffTick >= firstGroundTick - 3 ? "yes" : s.gliding()
                    ? "no, still gliding when the descent ended" : "no, the glide ended " + (firstGroundTick == Long.MAX_VALUE ? "in the air" : "away from the ground");
            out.add(new Act(Kind.LOG, null, 0f, "descent SUMMARY: signal " + engagedSignal.name().toLowerCase(Locale.ROOT) + ", pulses sent " + pulses + " (retries " + retriesUsed
                    + "), gliding false->true flips " + glideOnFlips + ", true->false flips " + glideOffFlips + ", longest glide " + fmt(longestGlide) + " s, glide ended by itself at the ground: "
                    + byItself + ", average descent " + fmt(engagedSeconds > 0 ? (engageY - s.y()) / engagedSeconds : 0) + " blocks a second over " + fmt(engagedSeconds)
                    + " s, landing speed " + fmt(lastAirVy) + ", health " + fmt(engageHealth) + " -> " + fmt(s.health()) + " (" + fmt(s.health() - engageHealth) + ")"));
        }
        out.add(new Act(Kind.LOG, null, 0f, "descent END (" + reason + "): fell " + fmt(engageY - s.y()) + " blocks since the trigger; speed at the trigger "
                + fmt(engageVy) + ", at the landing " + fmt(lastAirVy) + ", fastest " + fmt(fastestVy) + "; health " + fmt(engageHealth) + " -> "
                + fmt(s.health()) + "; instruction refreshes so far " + refreshes));
        engagedWay = null;
        streak = 0;
        fallStreak = 0;
        groundTicks = 0;
        if ("out of Mana".equals(reason)) {
            armed = false;
            out.add(Act.of(Kind.NEEDS_MANA));
        }
    }
}
