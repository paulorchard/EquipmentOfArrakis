package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Act;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.FallRules;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Fly;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.FlySettings;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Kind;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Mark;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Physics;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Sense;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Speeds;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mk I, the slow descent, with no engine: the triggers, the state machine, the push counts, the config. */
class Mk1Test {

    private static final float DT = 0.05f;
    private static final FlySettings NORMAL = new FlySettings(Fly.DISABLED, 10.32f, 10.32f);
    private static final Physics PHYS = new Physics(1f, 0.5f);
    private static final FallRules SETTINGS = FallRules.DEFAULT;
    private static final FallRules VELOCITY = new FallRules(true, 4.0, 12.0, 2, 30.25f, false, 2, 0f);

    private final BeltMachine belt = new BeltMachine();
    private double y = 100;

    private static Speeds speeds(Mark mark) {
        return new Speeds(1f, 1f);
    }

    /** One tick; vy in blocks a second (negative: falling). */
    private List<Act> tick(FallRules rules, boolean falling, boolean far, boolean ground, double vy) {
        return tick(rules, falling, far, ground, vy, false, false, true, 50f);
    }

    private List<Act> tick(FallRules rules, boolean falling, boolean far, boolean ground, double vy, boolean flying, boolean fluid, boolean modeOk, float mana) {
        y += vy * DT;
        return belt.tick(new Sense(DT, flying, modeOk, falling, far, ground, fluid, vy, y, 100f, mana, NORMAL, PHYS), 1f, 5f, rules, Mk1Test::speeds);
    }

    private static long count(List<Act> acts, Kind kind) {
        return acts.stream().filter(a -> a.kind() == kind).count();
    }

    private static Act first(List<Act> acts, Kind kind) {
        return acts.stream().filter(a -> a.kind() == kind).findFirst().orElse(null);
    }

    private List<Act> fallFor(FallRules rules, int ticks, boolean far, double vy) {
        List<Act> all = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            all.addAll(tick(rules, true, far, false, vy));
        }
        return all;
    }

    // ---- the triggers

    @Test
    void farFallEngagesOnFallingFarOnly() {
        belt.switchOn(Mark.I_FAR, NORMAL, 50f, 5f);
        assertTrue(fallFor(SETTINGS, 40, false, -20).isEmpty(), "fast but fallingFar is false: not this mark");
        List<Act> acts = tick(SETTINGS, true, true, false, -10);
        assertEquals(1, count(acts, Kind.DESCENT_ON));
        assertTrue(belt.engaged());
    }

    @Test
    void speedEngagesAfterTheThresholdOnTwoConsecutiveTicks() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        assertTrue(tick(SETTINGS, true, false, false, -13).isEmpty(), "one tick is noise");
        assertTrue(tick(SETTINGS, true, false, false, -11).isEmpty(), "dropped under the threshold: the count restarts");
        assertTrue(tick(SETTINGS, true, false, false, -13).isEmpty());
        assertEquals(1, count(tick(SETTINGS, true, false, false, -14), Kind.DESCENT_ON), "two in a row");
    }

    @Test
    void speedExactlyAtTheThresholdDoesNotEngage() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        assertTrue(fallFor(SETTINGS, 20, false, -12).isEmpty());
    }

    @Test
    void anOrdinaryJumpEngagesNeither() {
        for (Mark mark : new Mark[] {Mark.I_FAR, Mark.I_SPEED}) {
            BeltMachine b = new BeltMachine();
            b.switchOn(mark, NORMAL, 50f, 5f);
            // up, the peak, a short fall back down to the ground at about 8 blocks a second
            double[] vys = {8, 6, 4, 2, 0, -2, -4, -6, -8, -8, -8};
            for (double vy : vys) {
                y += vy * DT;
                List<Act> acts = b.tick(new Sense(DT, false, true, vy < 0, false, false, false, vy, y, 100f, 50f, NORMAL, PHYS), 1f, 5f, SETTINGS, Mk1Test::speeds);
                assertTrue(acts.isEmpty(), mark + " must not engage on a jump");
            }
            assertFalse(b.engaged());
        }
    }

    @Test
    void neitherEngagesOnTheGroundWhileFlyingInFluidOrOutsideAdventure() {
        for (Mark mark : new Mark[] {Mark.I_FAR, Mark.I_SPEED}) {
            boolean far = mark == Mark.I_FAR;
            BeltMachine b = new BeltMachine();
            b.switchOn(mark, NORMAL, 50f, 5f);
            for (int i = 0; i < 5; i++) {
                assertTrue(b.tick(new Sense(DT, false, true, true, far, true, false, -20, 50, 100f, 50f, NORMAL, PHYS), 1f, 5f, SETTINGS, Mk1Test::speeds).isEmpty(), "on the ground");
                assertTrue(b.tick(new Sense(DT, true, true, true, far, false, false, -20, 50, 100f, 50f, NORMAL, PHYS), 1f, 5f, SETTINGS, Mk1Test::speeds).isEmpty(), "flying");
                assertTrue(b.tick(new Sense(DT, false, true, true, far, false, true, -20, 50, 100f, 50f, NORMAL, PHYS), 1f, 5f, SETTINGS, Mk1Test::speeds).isEmpty(), "in fluid");
            }
            List<Act> creative = b.tick(new Sense(DT, false, false, true, far, false, false, -20, 50, 100f, 50f, NORMAL, PHYS), 1f, 5f, SETTINGS, Mk1Test::speeds);
            assertEquals(0, count(creative, Kind.DESCENT_ON), "outside Adventure the belt lets go and does not engage");
            assertFalse(b.isOn());
        }
    }

    // ---- the state machine and the pushes

    private void engageSpeed(FallRules rules) {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        fallFor(rules, 2, false, -15);
        assertTrue(belt.engaged());
    }

    @Test
    void theSettingsRouteSendsExactlyTwoPushesPerFall() {
        engageSpeed(SETTINGS);
        for (int i = 0; i < 100; i++) {
            List<Act> acts = tick(SETTINGS, true, false, false, -4);
            assertEquals(0, count(acts, Kind.DESCENT_ON) + count(acts, Kind.VELOCITY));
        }
        tick(SETTINGS, false, false, true, 0);
        List<Act> end = tick(SETTINGS, false, false, true, 0);
        assertEquals(1, count(end, Kind.DESCENT_OFF));
        assertEquals(2, belt.pushes(), "on and off");
        assertEquals(0, belt.velocityPushes());
        assertFalse(belt.engaged());
        // a second fall: two more
        fallFor(SETTINGS, 2, false, -15);
        tick(SETTINGS, false, false, true, 0);
        tick(SETTINGS, false, false, true, 0);
        assertEquals(4, belt.pushes());
        assertEquals(2, belt.engages());
        assertEquals(2, belt.ends());
    }

    @Test
    void theDragIsMultipliedByTheFactorAndRestoredExactly() {
        BeltMachine b = new BeltMachine();
        b.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        b.tick(new Sense(DT, false, true, true, false, false, false, -15, 90, 100f, 50f, NORMAL, PHYS), 1f, 5f, SETTINGS, Mk1Test::speeds);
        List<Act> on = b.tick(new Sense(DT, false, true, true, false, false, false, -15, 89, 100f, 50f, NORMAL, PHYS), 1f, 5f, SETTINGS, Mk1Test::speeds);
        assertEquals(new Physics(1f, 0.5f * 30.25f), first(on, Kind.DESCENT_ON).physics());
        b.tick(new Sense(DT, false, true, false, false, true, false, 0, 80, 100f, 50f, NORMAL, PHYS), 1f, 5f, SETTINGS, Mk1Test::speeds);
        List<Act> off = b.tick(new Sense(DT, false, true, false, false, true, false, 0, 80, 100f, 50f, NORMAL, PHYS), 1f, 5f, SETTINGS, Mk1Test::speeds);
        assertEquals(PHYS, first(off, Kind.DESCENT_OFF).physics());
    }

    @Test
    void theMassFieldDividesMassAndLeavesDragAlone() {
        FallRules mass = new FallRules(false, 4.0, 12.0, 2, 30.25f, true, 2, 0f);
        BeltMachine b = new BeltMachine();
        b.switchOn(Mark.I_FAR, NORMAL, 50f, 5f);
        List<Act> on = b.tick(new Sense(DT, false, true, true, true, false, false, -15, 90, 100f, 50f, NORMAL, PHYS), 1f, 5f, mass, Mk1Test::speeds);
        assertEquals(new Physics(1f / 30.25f, 0.5f), first(on, Kind.DESCENT_ON).physics());
    }

    @Test
    void theDescentEndsOnlyAfterTwoTicksOnTheGround() {
        engageSpeed(SETTINGS);
        assertTrue(tick(SETTINGS, false, false, true, 0).stream().noneMatch(a -> a.kind() == Kind.DESCENT_OFF), "one tick on the ground is not enough");
        tick(SETTINGS, true, false, false, -4);
        assertTrue(belt.engaged(), "a bounce in the air resets the ground count");
        tick(SETTINGS, false, false, true, 0);
        assertEquals(1, count(tick(SETTINGS, false, false, true, 0), Kind.DESCENT_OFF));
    }

    @Test
    void theLandingSpeedIsTheLastSpeedInTheAir() {
        engageSpeed(SETTINGS);
        tick(SETTINGS, true, false, false, -4.2);
        tick(SETTINGS, false, false, true, -0.1);
        List<Act> end = tick(SETTINGS, false, false, true, 0);
        assertEquals(-4.2, belt.lastLanding(), 1e-9);
        String log = first(end, Kind.LOG).text();
        assertTrue(log.contains("landed") && log.contains("-4.20"), log);
    }

    @Test
    void theDescentEndsWhenNotFallingForHalfASecond() {
        engageSpeed(SETTINGS);
        int ticks = 0;
        boolean ended = false;
        while (!ended && ticks < 100) {
            ended = count(tick(SETTINGS, false, false, false, 5), Kind.DESCENT_OFF) > 0;
            ticks++;
        }
        assertTrue(ended);
        assertTrue(ticks >= 10 && ticks <= 12, "about 0.5 s of rising at 0.05 s a tick: " + ticks);
    }

    @Test
    void aShortUpwardPushUnderHalfASecondKeepsTheDescent() {
        engageSpeed(SETTINGS);
        for (int i = 0; i < 8; i++) {
            tick(SETTINGS, false, false, false, 3);
        }
        assertTrue(belt.engaged());
        tick(SETTINGS, true, false, false, -4);
        for (int i = 0; i < 8; i++) {
            tick(SETTINGS, false, false, false, 3);
        }
        assertTrue(belt.engaged(), "falling again restarted the timer");
    }

    @Test
    void fluidAndFlyingEndTheDescent() {
        engageSpeed(SETTINGS);
        assertEquals(1, count(tick(SETTINGS, true, false, false, -4, false, true, true, 50f), Kind.DESCENT_OFF));
        assertFalse(belt.engaged());
        // re-armed: the next fall engages again
        fallFor(SETTINGS, 2, false, -15);
        assertTrue(belt.engaged());
        assertEquals(1, count(tick(SETTINGS, true, false, false, -4, true, false, true, 50f), Kind.DESCENT_OFF));
    }

    @Test
    void turningTheToggleOffWhileEngagedRestoresOnce() {
        engageSpeed(SETTINGS);
        belt.switchOff();
        List<Act> acts = tick(SETTINGS, true, false, false, -4);
        assertEquals(PHYS, first(acts, Kind.DESCENT_OFF).physics());
        assertFalse(belt.engaged());
        assertTrue(belt.idle());
        assertTrue(tick(SETTINGS, true, false, false, -4).isEmpty());
        assertEquals(2, belt.pushes());
    }

    @Test
    void deathWhileEngagedResetsToTheDefaultsAndDoesNotRestoreTheSnapshot() {
        engageSpeed(SETTINGS);
        belt.reset();
        List<Act> acts = tick(SETTINGS, true, false, false, -4);
        assertEquals(1, count(acts, Kind.RESET));
        assertEquals(0, count(acts, Kind.DESCENT_OFF));
        assertTrue(belt.idle());
    }

    @Test
    void aMarkThatNeverEngagedHasNothingToRestore() {
        belt.switchOn(Mark.I_FAR, NORMAL, 50f, 5f);
        tick(SETTINGS, false, false, true, 0);
        belt.switchOff();
        assertTrue(tick(SETTINGS, false, false, true, 0).isEmpty());
        belt.switchOn(Mark.I_FAR, NORMAL, 50f, 5f);
        belt.reset();
        assertTrue(tick(SETTINGS, false, false, true, 0).isEmpty(), "no reset of settings that were never changed");
        assertEquals(0, belt.pushes());
    }

    @Test
    void switchingFromMkIToAFlyingMarkEndsTheDescentFirstThenArmsTheFlight() {
        engageSpeed(SETTINGS);
        belt.switchOff();
        belt.switchOn(Mark.III, NORMAL, 50f, 5f);
        List<Act> first = tick(SETTINGS, true, false, false, -4);
        assertEquals(1, count(first, Kind.DESCENT_OFF));
        assertEquals(0, count(first, Kind.PUSH));
        List<Act> next = tick(SETTINGS, true, false, false, -4);
        assertEquals(new FlySettings(Fly.ALLOWED, 1f, 1f), first(next, Kind.PUSH).settings());
        assertEquals(NORMAL, belt.snapshot());
    }

    @Test
    void switchingFromAFlyingMarkToMkIEndsTheFlightAndRestoresTheSnapshot() {
        belt.switchOn(Mark.III, NORMAL, 50f, 5f);
        tick(SETTINGS, false, false, true, 0);
        tick(SETTINGS, false, false, false, 0, true, false, true, 50f);
        belt.switchOff();
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        List<Act> acts = tick(SETTINGS, false, false, false, 0, true, false, true, 50f);
        assertEquals(1, count(acts, Kind.STOP_FLYING));
        assertEquals(NORMAL, first(acts, Kind.RESTORE).settings());
        assertTrue(belt.isOn(Mark.I_SPEED));
        assertNull(belt.snapshot());
        // and the new mark works at once
        assertTrue(tick(SETTINGS, true, false, false, -15).isEmpty());
        assertEquals(1, count(tick(SETTINGS, true, false, false, -15), Kind.DESCENT_ON));
    }

    // ---- the velocity route

    @Test
    void theVelocityRoutePushesOnlyEveryPushTicksAndOnlyWhenTooFast() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        List<Act> engage = fallFor(VELOCITY, 2, false, -15);
        assertEquals(0, count(engage, Kind.DESCENT_ON), "no settings are changed on this route");
        assertEquals(1, belt.velocityPushes(), "one push at the engage");
        assertEquals(-4f, first(engage, Kind.VELOCITY).amount());
        int pushes = 0;
        for (int i = 0; i < 100; i++) {
            pushes += (int) count(tick(VELOCITY, true, false, false, -15), Kind.VELOCITY);
        }
        assertEquals(50, pushes, "every second tick of 100");
        int quiet = 0;
        for (int i = 0; i < 100; i++) {
            quiet += (int) count(tick(VELOCITY, true, false, false, -4.0), Kind.VELOCITY);
        }
        assertEquals(0, quiet, "already at the target speed: nothing sent");
        assertEquals(0, belt.pushes(), "no settings writes at all");
        tick(VELOCITY, false, false, true, 0);
        List<Act> end = tick(VELOCITY, false, false, true, 0);
        assertEquals(0, count(end, Kind.DESCENT_OFF));
        assertFalse(belt.engaged());
    }

    // ---- Mana

    @Test
    void theDescentIsFreeAtZeroManaPerSecondEvenWithNoMana() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 0f, 5f);
        List<Act> acts = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            acts.addAll(tick(SETTINGS, true, false, false, -15, false, false, true, 0f));
        }
        assertEquals(1, count(acts, Kind.DESCENT_ON));
        assertEquals(0, count(tick(SETTINGS, true, false, false, -4, false, false, true, 0f), Kind.SPEND));
    }

    @Test
    void aPaidDescentChargesPerTickAndEndsWhenManaRunsOutThenWaitsForTheResumeValue() {
        FallRules paid = new FallRules(false, 4.0, 12.0, 2, 30.25f, false, 2, 2f);
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        fallFor(paid, 2, false, -15);
        assertEquals(2f * DT, first(tick(paid, true, false, false, -4), Kind.SPEND).amount(), 1e-6);
        List<Act> cut = tick(paid, true, false, false, -4, false, false, true, 0f);
        assertEquals(1, count(cut, Kind.DESCENT_OFF));
        assertEquals(1, count(cut, Kind.NEEDS_MANA));
        assertFalse(belt.engaged());
        // unarmed: a new fall does nothing until Mana is back at the resume value
        assertTrue(tick(paid, true, false, false, -15, false, false, true, 4f).isEmpty());
        assertTrue(tick(paid, true, false, false, -15, false, false, true, 4f).isEmpty());
        assertEquals(1, count(tick(paid, true, false, false, -15, false, false, true, 6f), Kind.DESCENT_ON), "armed again and the fall in progress engages");
    }

    @Test
    void aPaidDescentDoesNotEngageWithNoMana() {
        FallRules paid = new FallRules(false, 4.0, 12.0, 2, 30.25f, false, 2, 2f);
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        for (int i = 0; i < 10; i++) {
            assertTrue(tick(paid, true, false, false, -15, false, false, true, 0f).isEmpty());
        }
    }

    // ---- the config

    @Test
    void theFactorIsDerivedFromTheObservedTerminalSpeedAndTheTarget() {
        Mk1Config m = new Mk1Config();
        assertEquals(30.25, m.dragFactor(), 1e-9);
        m.descentSpeed(8);
        assertEquals(7.5625, m.dragFactor(), 1e-9);
        m.dragFactor(10);
        assertEquals(10.0, m.dragFactor(), 1e-9, "an explicit factor wins");
        m.dragFactor(0);
        m.descentSpeed(30);
        assertEquals(1.0, m.dragFactor(), 1e-9, "a target above the terminal speed changes nothing");
    }

    @Test
    void anOldFileWithTheTextPlaceholderLoadsAsTheDefaults() throws IOException {
        SuspensorConfig old = SuspensorConfig.CODEC.decodeJson(RawJsonReader.fromJsonString(
                "{\"Mk3\":{\"HorizontalSpeed\":2.0,\"VerticalSpeed\":1.5},\"Mk1\":\"not read yet: the slow-descent mark is not built\",\"ManaPerSecond\":1.0,\"Hum\":true}"),
                new ExtraInfo());
        assertNotNull(old.mk1());
        assertEquals(4.0, old.mk1().descentSpeed());
        assertTrue(old.mk1().listed());
        assertEquals(2.0, old.mk3().horizontalSpeed(), "the rest of the old file was read");
    }

    @Test
    void theNewObjectIsReadAndAMissingOneIsTheDefaults() throws IOException {
        SuspensorConfig c = SuspensorConfig.CODEC.decodeJson(RawJsonReader.fromJsonString(
                "{\"Mk1\":{\"Listed\":false,\"Route\":\"velocity\",\"DescentSpeed\":6.0,\"SpeedThreshold\":15.0,\"SpeedTicks\":3,\"Field\":\"mass\",\"PushTicks\":4}}"), new ExtraInfo());
        Mk1Config m = c.mk1();
        assertFalse(m.listed());
        assertEquals(Mk1Config.Route.VELOCITY, m.route());
        assertEquals(6.0, m.descentSpeed());
        assertEquals(15.0, m.speedThreshold());
        assertEquals(3, m.speedTicks());
        assertTrue(m.massField());
        assertEquals(4, m.pushTicks());
        SuspensorConfig none = SuspensorConfig.CODEC.decodeJson(RawJsonReader.fromJsonString("{}"), new ExtraInfo());
        assertTrue(none.mk1().listed());
    }
}
