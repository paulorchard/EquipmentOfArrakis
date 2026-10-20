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

    // ---- the probe

    @Test
    void theProbeLogsGlidingFlipsStatFlipsAndTheLandingHealth() {
        FallProbe probe = new FallProbe(false);
        assertTrue(probe.observeGlide(false, false, true, 100f, 0).isEmpty());
        assertTrue(probe.observeGlide(false, false, false, 100f, -3).isEmpty());
        List<String> flip = probe.observeGlide(true, true, false, 100f, -2.1);
        assertEquals(2, flip.size());
        assertTrue(flip.get(0).startsWith("gliding false -> true"));
        assertTrue(flip.get(1).startsWith("stat GlidingActive false -> true"));
        List<String> landing = probe.observeGlide(false, false, true, 91f, -4);
        assertEquals(3, landing.size());
        assertTrue(landing.get(2).contains("health 100.00 -> 91.00"));
    }
}
