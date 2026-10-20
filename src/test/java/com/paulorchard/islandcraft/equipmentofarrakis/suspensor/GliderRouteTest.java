package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Act;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Drag;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.FallRules;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Fly;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.FlySettings;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Kind;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Mark;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Sense;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Speeds;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Trigger;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Way;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The glider route with no engine: the falling trigger, the signal effect and its refresh schedule, the end conditions, the slot rules. */
class GliderRouteTest {

    private static final float DT = 0.05f;
    private static final FlySettings NORMAL = new FlySettings(Fly.DISABLED, 10.32f, 10.32f);
    private static final FallRules RULES = FallRules.DEFAULT.withWay(Way.GLIDER);

    private final BeltMachine belt = new BeltMachine();

    private static Speeds speeds(Mark mark) {
        return new Speeds(1f, 1f);
    }

    private List<Act> tick(FallRules rules, boolean falling, boolean far, boolean ground, boolean flying, boolean fluid, boolean gliding, boolean modeOk, double vy) {
        return belt.tick(new Sense(DT, flying, modeOk, falling, far, ground, fluid, vy, 100, 100f, 50f, NORMAL, new Drag(0.96f, 0.995f), gliding), 1f, 5f, rules,
                GliderRouteTest::speeds);
    }

    private List<Act> air(FallRules rules, boolean falling, double vy) {
        return tick(rules, falling, false, false, false, false, false, true, vy);
    }

    private static long count(List<Act> acts, Kind kind) {
        return acts.stream().filter(a -> a.kind() == kind).count();
    }

    private List<Act> airFor(FallRules rules, int ticks, boolean falling) {
        List<Act> all = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            all.addAll(air(rules, falling, -5));
        }
        return all;
    }

    // ---- the falling trigger

    @Test
    void theRawFallingFlagEngagesAtOnceAndOnlyTheSignalIsSent() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        List<Act> acts = air(RULES, true, 0.0);
        assertTrue(belt.engaged());
        assertEquals(Way.GLIDER, belt.engagedWay());
        assertEquals(1, count(acts, Kind.EFFECT_ON));
        assertEquals(1f, acts.stream().filter(a -> a.kind() == Kind.EFFECT_ON).findFirst().orElseThrow().amount(), "the first application");
        for (Kind none : new Kind[] {Kind.GLIDE_ON, Kind.PUSH, Kind.VELOCITY, Kind.DRAG_ON, Kind.STOP_FLYING, Kind.RESTORE}) {
            assertEquals(0, count(acts, none), "no " + none + ": nothing else is done to the player");
        }
        assertEquals(0, belt.pushes());
        assertTrue(belt.effectOn());
    }

    @Test
    void bothMarksUseTheGliderTrigger() {
        for (Mark mark : new Mark[] {Mark.I_FAR, Mark.I_SPEED}) {
            BeltMachine one = new BeltMachine();
            one.switchOn(mark, NORMAL, 50f, 5f);
            one.tick(new Sense(DT, false, true, true, false, false, false, -1, 100, 100f, 50f, NORMAL, null, false), 1f, 5f, RULES, GliderRouteTest::speeds);
            assertTrue(one.engaged(), mark.name());
        }
    }

    @Test
    void fallingTicksAreCounted() {
        FallRules three = RULES.withTrigger(Trigger.FALLING, 3);
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        assertTrue(airFor(three, 2, true).isEmpty());
        assertFalse(belt.engaged());
        assertEquals(1, count(air(three, true, -1), Kind.EFFECT_ON));
        assertTrue(belt.engaged());
    }

    @Test
    void aBrokenStreakStartsOver() {
        FallRules two = RULES.withTrigger(Trigger.FALLING, 2);
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        air(two, true, -1);
        air(two, false, -1);
        air(two, true, -1);
        assertFalse(belt.engaged());
        air(two, true, -1);
        assertTrue(belt.engaged());
    }

    @Test
    void notOnTheGroundNotFlyingNotInFluidNotCreativeNotGliding() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        assertTrue(tick(RULES, true, false, true, false, false, false, true, 0).isEmpty(), "onGround");
        assertTrue(tick(RULES, true, false, false, true, false, false, true, -1).isEmpty(), "flying");
        assertTrue(tick(RULES, true, false, false, false, true, false, true, -1).isEmpty(), "in fluid");
        assertTrue(tick(RULES, true, false, false, false, false, true, true, -1).isEmpty(), "already gliding");
        assertFalse(belt.engaged());
        tick(RULES, true, false, false, false, false, false, false, -1);
        assertFalse(belt.engaged(), "outside Adventure the belt lets go");
        assertFalse(belt.isOn());
    }

    @Test
    void theSpeedAndFarFallValuesReuseTheExistingRules() {
        FallRules speed = RULES.withTrigger(Trigger.SPEED, 1);
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        assertTrue(airFor(speed, 3, true).isEmpty(), "falling is true but slower than the threshold");
        List<Act> fast = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            fast.addAll(air(speed, true, -20));
        }
        assertEquals(1, count(fast, Kind.EFFECT_ON));

        BeltMachine other = new BeltMachine();
        FallRules far = RULES.withTrigger(Trigger.FARFALL, 1);
        other.switchOn(Mark.I_FAR, NORMAL, 50f, 5f);
        assertTrue(other.tick(new Sense(DT, false, true, true, false, false, false, -30, 100, 100f, 50f, NORMAL, null, false), 1f, 5f, far, GliderRouteTest::speeds).isEmpty());
        assertEquals(1, count(other.tick(new Sense(DT, false, true, true, true, false, false, -30, 100, 100f, 50f, NORMAL, null, false), 1f, 5f, far, GliderRouteTest::speeds),
                Kind.EFFECT_ON));
    }

    // ---- the refresh schedule

    @Test
    void theSignalIsRefreshedEveryQuarterSecondWhileFallingAndNotGliding() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        air(RULES, true, 0);
        long refreshes = 0;
        for (int i = 0; i < 40; i++) {
            refreshes += count(air(RULES, true, -8), Kind.EFFECT_ON);
        }
        // 40 ticks of 0.05 s = 2 s: one refresh each 0.25 s = 8
        assertEquals(8, refreshes);
    }

    @Test
    void noRefreshWhileGlidingOrWhenTheTriggerStopsHolding() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        air(RULES, true, 0);
        long refreshes = 0;
        for (int i = 0; i < 20; i++) {
            refreshes += count(tick(RULES, true, false, false, false, false, true, true, -2), Kind.EFFECT_ON);
        }
        assertEquals(0, refreshes, "gliding: the signal is left to lapse");
        for (int i = 0; i < 20; i++) {
            refreshes += count(air(RULES, false, -2), Kind.EFFECT_ON);
        }
        assertEquals(0, refreshes, "the trigger does not hold");
        assertTrue(belt.engaged());
    }

    // ---- the end

    @Test
    void twoTicksOnTheGroundEndItAndRemoveTheSignal() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        air(RULES, true, 0);
        assertTrue(tick(RULES, false, false, true, false, false, false, true, -3).stream().noneMatch(a -> a.kind() == Kind.EFFECT_OFF));
        List<Act> end = tick(RULES, false, false, true, false, false, false, true, -3);
        assertEquals(1, count(end, Kind.EFFECT_OFF));
        assertFalse(belt.engaged());
        assertFalse(belt.effectOn());
        assertEquals(0, count(end, Kind.VELOCITY_CLEAR), "no instruction was ever sent");
        assertEquals(0, count(end, Kind.GLIDE_OFF));
        assertEquals("landed", belt.lastEnd());
    }

    @Test
    void landingIsNotCountedOnTheRisingRuleAndTheNextHopEngagesAgain() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        air(RULES, true, 0);
        for (int i = 0; i < 20; i++) {
            air(RULES, true, 0.0);
        }
        assertTrue(belt.engaged(), "vertical speed near 0 for a second does not end it (the apex of a hop)");
        tick(RULES, false, false, true, false, false, false, true, -3);
        tick(RULES, false, false, true, false, false, false, true, -3);
        assertFalse(belt.engaged());
        assertEquals(1, count(air(RULES, true, 0), Kind.EFFECT_ON));
        assertEquals(2, belt.engages());
    }

    @Test
    void fluidAndFlightEndIt() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        air(RULES, true, 0);
        List<Act> fluid = tick(RULES, true, false, false, false, true, false, true, -2);
        assertEquals(1, count(fluid, Kind.EFFECT_OFF));
        assertEquals("fluid", belt.lastEnd());
        air(RULES, true, 0);
        List<Act> flying = tick(RULES, true, false, false, true, false, false, true, -2);
        assertEquals(1, count(flying, Kind.EFFECT_OFF));
        assertEquals("flying", belt.lastEnd());
    }

    @Test
    void toggleOffDeathAndTheRestRemoveTheSignalOnceAndTheItem() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        belt.itemPlaced(true);
        air(RULES, true, 0);
        belt.switchOff();
        List<Act> off = air(RULES, true, -5);
        assertEquals(1, count(off, Kind.EFFECT_OFF));
        assertEquals(1, count(off, Kind.ITEM_REMOVE));
        assertEquals(0, count(off, Kind.RESET), "no settings were touched, so nothing to reset");
        assertEquals(0, count(off, Kind.GLIDE_OFF), "a glide in progress is not touched");
        assertTrue(belt.idle());

        BeltMachine dead = new BeltMachine();
        dead.switchOn(Mark.I_FAR, NORMAL, 50f, 5f);
        dead.itemPlaced(true);
        dead.tick(new Sense(DT, false, true, true, true, false, false, -5, 100, 100f, 50f, NORMAL, null, false), 1f, 5f, RULES.withTrigger(Trigger.FARFALL, 1), GliderRouteTest::speeds);
        dead.reset();
        List<Act> acts = dead.tick(new Sense(DT, false, true, true, false, false, false, -5, 100, 100f, 50f, NORMAL, null, false), 1f, 5f, RULES, GliderRouteTest::speeds);
        assertEquals(1, count(acts, Kind.EFFECT_OFF));
        assertEquals(1, count(acts, Kind.ITEM_REMOVE));
        assertTrue(dead.idle());
    }

    @Test
    void switchingToTheOtherMkIKeepsTheItemButSwitchingToAFlyingMarkTakesItAway() {
        belt.switchOn(Mark.I_FAR, NORMAL, 50f, 5f);
        belt.itemPlaced(true);
        belt.switchOff();
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        assertEquals(0, count(air(RULES, false, -1), Kind.ITEM_REMOVE), "the other Mk I mark keeps the item");
        assertTrue(belt.itemPlaced());

        belt.switchOff();
        belt.switchOn(Mark.II, NORMAL, 50f, 5f);
        assertEquals(1, count(air(RULES, false, -1), Kind.ITEM_REMOVE), "Mk II has no use for it");
        assertFalse(belt.itemPlaced());
    }

    @Test
    void anOldRuleSetOfTheOtherWaysIsUnchanged() {
        FallRules flyglide = FallRules.DEFAULT;
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        List<Act> all = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            all.addAll(belt.tick(new Sense(DT, false, true, true, false, false, false, -20, 100, 100f, 50f, NORMAL, new Drag(0.96f, 0.995f), false), 1f, 5f, flyglide,
                    GliderRouteTest::speeds));
        }
        assertEquals(1, count(all, Kind.GLIDE_ON));
        assertEquals(0, count(all, Kind.EFFECT_ON));
    }

    // ---- the slot rules

    @Test
    void anEmptySlotIsFilledAnOccupiedOneIsNotTouched() {
        assertEquals(GliderSlot.Place.PLACE, GliderSlot.place(null));
        assertEquals(GliderSlot.Place.ALREADY, GliderSlot.place(GliderSlot.ITEM_ID));
        assertEquals(GliderSlot.Place.BLOCKED, GliderSlot.place("Weapon_Sword_Iron"));
    }

    @Test
    void onlyOurItemIsRemoved() {
        assertTrue(GliderSlot.removable(GliderSlot.ITEM_ID));
        assertFalse(GliderSlot.removable("Weapon_Sword_Iron"));
        assertFalse(GliderSlot.removable(null));
        assertEquals("Arrakis_Suspensor_Glider", GliderSlot.ITEM_ID);
    }

    // ---- the config

    @Test
    void theNewKeysAndDefaults() throws IOException {
        Mk1Config m = new Mk1Config();
        assertEquals(Way.GLIDER, m.way());
        assertEquals(Trigger.FALLING, m.trigger());
        assertEquals(1, m.fallingTicks());
        assertEquals(0.0, m.manaPerSecond());
        FallRules rules = m.rules();
        assertEquals(Trigger.FALLING, rules.trigger());
        assertEquals(1, rules.fallingTicks());

        Mk1Config read = SuspensorConfig.CODEC.decodeJson(RawJsonReader.fromJsonString("{\"Mk1\":{\"Route\":\"flyglide\",\"GliderTrigger\":\"farfall\",\"FallingTicks\":3}}"),
                new ExtraInfo()).mk1();
        assertEquals(Way.FLYGLIDE, read.way(), "an old route name still reads");
        assertEquals(Trigger.FARFALL, read.trigger());
        assertEquals(3, read.fallingTicks());
        assertEquals(Trigger.FALLING, SuspensorConfig.CODEC.decodeJson(RawJsonReader.fromJsonString("{\"Mk1\":{\"GliderTrigger\":\"nonsense\"}}"), new ExtraInfo()).mk1().trigger());
        assertNull(null);
    }

// ---- the pulse signal

    private static final FallRules PULSE = RULES.withPulse(0.15f, 1.0f, 1);

    private List<Act> glideTick(FallRules rules, boolean gliding) {
        return tick(rules, !gliding, false, false, false, false, gliding, true, gliding ? -2 : -8);
    }

    private static List<Act> pulses(List<Act> acts) {
        return acts.stream().filter(a -> a.kind() == Kind.EFFECT_PULSE).toList();
    }

    @Test
    void aPulseIsSentOnceWithTheShortDurationAndNeverRefreshed() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        List<Act> first = air(PULSE, true, 0);
        assertEquals(1, pulses(first).size());
        assertEquals(0.15f, pulses(first).get(0).amount());
        assertEquals(0, count(first, Kind.EFFECT_ON), "not the level signal");
        long more = 0;
        for (int i = 0; i < 19; i++) {
            List<Act> acts = glideTick(PULSE, true);
            more += pulses(acts).size() + count(acts, Kind.EFFECT_ON);
        }
        assertEquals(0, more, "a glide that opened: no refresh and no retry in a second of gliding");
        assertEquals(1, belt.pulses());
        assertEquals(0, belt.retries());
    }

    @Test
    void noRefreshAndOneRetryWhenNoGlideOpens() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        air(PULSE, true, 0);
        long pulses = 0;
        long refreshes = 0;
        List<Act> retry = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            List<Act> acts = glideTick(PULSE, false);
            pulses += pulses(acts).size();
            refreshes += count(acts, Kind.EFFECT_ON);
            retry.addAll(pulses(acts));
        }
        assertEquals(1, pulses, "one retry in three seconds of falling, at most PulseRetries");
        assertEquals(0, refreshes);
        assertTrue(retry.get(0).text().startsWith("retry 1"));
        assertEquals(2, belt.pulses());
        assertEquals(1, belt.retries());
    }

    @Test
    void theRetryComesAfterTheRetrySecondsNotBefore() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        air(PULSE, true, 0);
        for (int i = 0; i < 19; i++) {
            assertTrue(pulses(glideTick(PULSE, false)).isEmpty(), "tick " + i);
        }
        assertEquals(1, pulses(glideTick(PULSE, false)).size(), "after 1.0 s");
    }

    @Test
    void noRetryWhenAGlideWasSeenEvenIfItEndedAgain() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        air(PULSE, true, 0);
        glideTick(PULSE, true);
        long pulses = 0;
        for (int i = 0; i < 60; i++) {
            pulses += pulses(glideTick(PULSE, false)).size();
        }
        assertEquals(0, pulses, "one signal, one glide: a glide that closed is not asked for again in the same fall");
    }

    @Test
    void zeroRetriesMeansNone() {
        FallRules none = RULES.withPulse(0.15f, 1.0f, 0);
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        air(none, true, 0);
        long pulses = 0;
        for (int i = 0; i < 80; i++) {
            pulses += pulses(glideTick(none, false)).size();
        }
        assertEquals(0, pulses);
    }

    @Test
    void pulseRearmsOnlyAfterTheGroundTicksAndThenPulsesOnceAgain() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        air(PULSE, true, 0);
        glideTick(PULSE, true);
        assertTrue(pulses(tick(PULSE, true, false, true, false, false, false, true, -3)).isEmpty(), "one ground tick: still engaged");
        assertTrue(belt.engaged());
        // a falling flag in the air again before two ground ticks is not a new pulse while engaged
        assertTrue(pulses(air(PULSE, true, -2)).isEmpty());
        tick(PULSE, false, false, true, false, false, false, true, -3);
        List<Act> end = tick(PULSE, false, false, true, false, false, false, true, -3);
        assertFalse(belt.engaged());
        assertEquals(1, count(end, Kind.EFFECT_OFF));
        assertEquals(1, pulses(air(PULSE, true, 0)).size(), "armed again: the next fall pulses once");
        assertEquals(1, belt.pulses(), "the counters start over for the new descent");
    }

    @Test
    void theSummaryLineCountsTheFlipsTheLongestGlideAndTheEnd() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        air(PULSE, true, 0);
        for (int i = 0; i < 10; i++) {
            glideTick(PULSE, true);
        }
        glideTick(PULSE, false);
        glideTick(PULSE, true);
        glideTick(PULSE, true);
        glideTick(PULSE, false);
        tick(PULSE, false, false, true, false, false, false, true, -3);
        List<Act> end = tick(PULSE, false, false, true, false, false, false, true, -3);
        String summary = end.stream().filter(a -> a.kind() == Kind.LOG && a.text().startsWith("descent SUMMARY")).findFirst().orElseThrow().text();
        assertTrue(summary.contains("signal pulse"), summary);
        assertTrue(summary.contains("pulses sent 1 (retries 0)"), summary);
        assertTrue(summary.contains("gliding false->true flips 2"), summary);
        assertTrue(summary.contains("true->false flips 2"), summary);
        assertTrue(summary.contains("longest glide 0.50 s"), summary);
        assertTrue(summary.contains("glide ended by itself at the ground: yes"), summary);
    }

    @Test
    void theSummaryNamesAGlideThatNeverOpenedOrWasStillGoing() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        air(PULSE, true, 0);
        tick(PULSE, false, false, true, false, false, false, true, -3);
        String none = tick(PULSE, false, false, true, false, false, false, true, -3).stream().filter(a -> a.kind() == Kind.LOG && a.text().startsWith("descent SUMMARY")).findFirst()
                .orElseThrow().text();
        assertTrue(none.contains("no glide opened"), none);

        BeltMachine other = new BeltMachine();
        other.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        other.tick(new Sense(DT, false, true, true, false, false, false, 0, 100, 100f, 50f, NORMAL, null, false), 1f, 5f, PULSE, GliderRouteTest::speeds);
        other.tick(new Sense(DT, false, true, false, false, false, false, -2, 100, 100f, 50f, NORMAL, null, true), 1f, 5f, PULSE, GliderRouteTest::speeds);
        other.tick(new Sense(DT, false, true, false, false, true, false, -2, 100, 100f, 50f, NORMAL, null, true), 1f, 5f, PULSE, GliderRouteTest::speeds);
        String still = other.tick(new Sense(DT, false, true, false, false, true, false, -2, 100, 100f, 50f, NORMAL, null, true), 1f, 5f, PULSE, GliderRouteTest::speeds).stream()
                .filter(a -> a.kind() == Kind.LOG && a.text().startsWith("descent SUMMARY")).findFirst().orElseThrow().text();
        assertTrue(still.contains("still gliding when the descent ended"), still);
    }

    @Test
    void theSignalSettingAndThePulseKeysAreRead() throws IOException {
        Mk1Config d = new Mk1Config();
        assertEquals(BeltMachine.Signal.LEVEL, d.signal());
        assertEquals(0.15, d.pulseSeconds());
        assertEquals(1.0, d.pulseRetrySeconds());
        assertEquals(1, d.pulseRetries());
        Mk1Config m = SuspensorConfig.CODEC.decodeJson(RawJsonReader.fromJsonString("{\"Mk1\":{\"Signal\":\"pulse\",\"PulseSeconds\":0.5,\"PulseRetrySeconds\":2.0,\"PulseRetries\":0}}"),
                new ExtraInfo()).mk1();
        assertEquals(BeltMachine.Signal.PULSE, m.signal());
        assertEquals(0.5f, m.rules().pulseSeconds());
        assertEquals(2.0f, m.rules().pulseRetrySeconds());
        assertEquals(0, m.rules().pulseRetries());
        assertEquals(BeltMachine.Signal.LEVEL, SuspensorConfig.CODEC.decodeJson(RawJsonReader.fromJsonString("{\"Mk1\":{\"Signal\":\"nonsense\"}}"), new ExtraInfo()).mk1().signal());
    }

    // ---- the probe

    @Test
    void theProbeLogsGlidingFlipsStatFlipsAndTheLandingHealth() {
        FallProbe probe = new FallProbe(false);
        assertTrue(probe.observeGlide(false, false, true, 100f, 0, 1000L).isEmpty());
        assertTrue(probe.observeGlide(false, false, false, 100f, -3, 1100L).isEmpty());
        List<String> flip = probe.observeGlide(true, true, false, 100f, -2.1, 1570L);
        assertEquals(2, flip.size());
        assertTrue(flip.get(0).startsWith("gliding false -> true"));
        assertTrue(flip.get(1).startsWith("stat GlidingActive false -> true"));
        List<String> landing = probe.observeGlide(false, false, true, 91f, -4, 1600L);
        assertEquals(3, landing.size());
        assertTrue(landing.get(2).contains("health 100.00 -> 91.00"));
    }
}
