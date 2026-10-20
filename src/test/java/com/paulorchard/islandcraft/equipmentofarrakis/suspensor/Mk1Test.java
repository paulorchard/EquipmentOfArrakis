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
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Way;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mk I, the slow descent, with no engine: the triggers, the three ways, the push and refresh counts, the reset path, the config. */
class Mk1Test {

    private static final float DT = 0.05f;
    private static final FlySettings NORMAL = new FlySettings(Fly.DISABLED, 10.32f, 10.32f);
    private static final Drag DRAG = new Drag(0.96f, 0.995f);
    private static final FallRules GLIDE = FallRules.DEFAULT;
    private static final FallRules AIRDRAG = GLIDE.withWay(Way.AIRDRAG);
    private static final FallRules COUNTER = GLIDE.withWay(Way.COUNTER);

    private final BeltMachine belt = new BeltMachine();
    private double y = 100;

    private static Speeds speeds(Mark mark) {
        return new Speeds(1f, 1f);
    }

    private List<Act> tick(FallRules rules, boolean falling, boolean far, boolean ground, double vy) {
        return tick(rules, falling, far, ground, false, vy, false, true, 50f);
    }

    private List<Act> tick(FallRules rules, boolean falling, boolean far, boolean ground, boolean flying, double vy, boolean fluid, boolean modeOk, float mana) {
        y += vy * DT;
        return belt.tick(new Sense(DT, flying, modeOk, falling, far, ground, fluid, vy, y, 100f, mana, NORMAL, DRAG), 1f, 5f, rules, Mk1Test::speeds);
    }

    /** A tick in the air with the glide going on: flying true, the given vertical speed. */
    private List<Act> glide(double vy) {
        return tick(GLIDE, false, false, false, true, vy, false, true, 50f);
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

    /** Switches Mk I (speed) on and falls fast until it engages; returns the acts of the engage. */
    private List<Act> engage(FallRules rules, double vy) {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        List<Act> acts = fallFor(rules, 2, false, vy);
        assertTrue(belt.engaged());
        return acts;
    }

    // ---- the triggers

    @Test
    void farFallEngagesOnFallingFarOnly() {
        belt.switchOn(Mark.I_FAR, NORMAL, 50f, 5f);
        assertTrue(fallFor(GLIDE, 40, false, -20).isEmpty(), "fast but fallingFar is false: not this mark");
        assertEquals(1, count(tick(GLIDE, true, true, false, -10), Kind.GLIDE_ON));
        assertTrue(belt.engaged());
    }

    @Test
    void speedEngagesAfterTheThresholdOnTwoConsecutiveTicks() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        assertTrue(tick(GLIDE, true, false, false, -13).isEmpty(), "one tick is noise");
        assertTrue(tick(GLIDE, true, false, false, -11).isEmpty(), "dropped under the threshold: the count restarts");
        assertTrue(tick(GLIDE, true, false, false, -13).isEmpty());
        assertEquals(1, count(tick(GLIDE, true, false, false, -14), Kind.GLIDE_ON), "two in a row");
    }

    @Test
    void speedExactlyAtTheThresholdDoesNotEngage() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        assertTrue(fallFor(GLIDE, 20, false, -12).isEmpty());
    }

    @Test
    void anOrdinaryJumpEngagesNeither() {
        for (Mark mark : new Mark[] {Mark.I_FAR, Mark.I_SPEED}) {
            BeltMachine b = new BeltMachine();
            b.switchOn(mark, NORMAL, 50f, 5f);
            double[] vys = {8, 6, 4, 2, 0, -2, -4, -6, -8, -8, -8};
            for (double vy : vys) {
                y += vy * DT;
                List<Act> acts = b.tick(new Sense(DT, false, true, vy < 0, false, false, false, vy, y, 100f, 50f, NORMAL, DRAG), 1f, 5f, GLIDE, Mk1Test::speeds);
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
                assertTrue(b.tick(new Sense(DT, false, true, true, far, true, false, -20, 50, 100f, 50f, NORMAL, DRAG), 1f, 5f, GLIDE, Mk1Test::speeds).isEmpty(), "on the ground");
                assertTrue(b.tick(new Sense(DT, true, true, true, far, false, false, -20, 50, 100f, 50f, NORMAL, DRAG), 1f, 5f, GLIDE, Mk1Test::speeds).isEmpty(), "flying");
                assertTrue(b.tick(new Sense(DT, false, true, true, far, false, true, -20, 50, 100f, 50f, NORMAL, DRAG), 1f, 5f, GLIDE, Mk1Test::speeds).isEmpty(), "in fluid");
            }
            List<Act> creative = b.tick(new Sense(DT, false, false, true, far, false, false, -20, 50, 100f, 50f, NORMAL, DRAG), 1f, 5f, GLIDE, Mk1Test::speeds);
            assertEquals(0, count(creative, Kind.GLIDE_ON), "outside Adventure the belt lets go and does not engage");
            assertFalse(b.isOn());
        }
    }

    // ---- flyglide

    @Test
    void flyglideEngageFliesForcedAtTheGlideSpeedWithNoVerticalSpeedAndSetsTheDescentVelocity() {
        List<Act> acts = engage(GLIDE, -15);
        Act on = first(acts, Kind.GLIDE_ON);
        assertEquals(new FlySettings(Fly.FORCED, 1.0f, 0f), on.settings(), "Forced so a double tap cannot end it; vertical 0 so Space and crouch do nothing");
        assertEquals(-4f, first(acts, Kind.VELOCITY).amount());
        assertEquals(1, belt.pushes(), "one settings push (setFlying pushes it)");
        assertEquals(1, belt.refreshes());
        assertTrue(belt.instructionActive());
        assertEquals(Way.FLYGLIDE, belt.engagedWay());
    }

    @Test
    void flyglideHoldRefreshesOnlyWhenTheSpeedDriftsAndNotMoreOftenThanPushTicks() {
        engage(GLIDE, -15);
        int refreshes = 0;
        for (int i = 0; i < 100; i++) {
            refreshes += (int) count(glide(-4.5), Kind.VELOCITY);
        }
        assertEquals(0, refreshes, "within the 0.75 tolerance: nothing is sent");
        for (int i = 0; i < 100; i++) {
            refreshes += (int) count(glide(-6), Kind.VELOCITY);
        }
        assertEquals(20, refreshes, "out of tolerance: every 5th tick of 100");
        assertEquals(21, belt.refreshes(), "the engage push plus the refreshes");
        assertEquals(1, belt.pushes(), "no settings at all while holding");
    }

    @Test
    void flyglideToleranceEdgeIsNotARefresh() {
        engage(GLIDE, -15);
        for (int i = 0; i < 20; i++) {
            assertEquals(0, count(glide(-4.75), Kind.VELOCITY));
        }
        assertEquals(1, count(glide(-4.76), Kind.VELOCITY), "just outside the tolerance");
    }

    @Test
    void flyglideEndsAfterTwoTicksOnTheGroundClearingTheInstructionAndRestoringTheSnapshot() {
        engage(GLIDE, -15);
        glide(-4);
        tick(GLIDE, false, false, true, true, -4, false, true, 50f);
        assertTrue(belt.engaged());
        List<Act> end = tick(GLIDE, false, false, true, true, -4, false, true, 50f);
        assertFalse(belt.engaged());
        assertEquals(1, count(end, Kind.VELOCITY_CLEAR));
        Act off = first(end, Kind.GLIDE_OFF);
        assertEquals(NORMAL, off.settings());
        assertTrue(end.indexOf(first(end, Kind.VELOCITY_CLEAR)) < end.indexOf(off), "the instruction is cleared before flight is ended");
        assertEquals(2, belt.pushes(), "on and off");
        assertFalse(belt.instructionActive());
        assertEquals(-4.0, belt.lastLanding(), 1e-9);
    }

    @Test
    void flyglideEndsWhenTheStallRuleSeesNoMovementBecauseTheGroundFlagDoesNotComeWhileFlying() {
        engage(GLIDE, -15);
        for (int i = 0; i < 12; i++) {
            glide(-4);
        }
        assertTrue(belt.engaged(), "moving down at the target: engaged");
        // now standing still on the ground with the flag still false, flying true
        boolean ended = false;
        int ticks = 0;
        List<Act> last = null;
        while (!ended && ticks < 40) {
            y -= 0;
            last = belt.tick(new Sense(DT, true, true, false, false, false, false, 0.0, y, 100f, 50f, NORMAL, DRAG), 1f, 5f, GLIDE, Mk1Test::speeds);
            ended = !belt.engaged();
            ticks++;
        }
        assertTrue(ended);
        assertTrue(ticks >= BeltMachine.STALL_TICKS && ticks <= BeltMachine.STALL_TICKS + 2, "about six still ticks: " + ticks);
        assertTrue(first(last, Kind.LOG).text().contains("stalled"));
        assertEquals(1, count(last, Kind.GLIDE_OFF));
    }

    @Test
    void flyglideEndsWhenTheFlightIsLostAfterTheGracePeriod() {
        engage(GLIDE, -15);
        List<Act> all = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            all.addAll(tick(GLIDE, true, false, false, false, -4, false, true, 50f));
        }
        assertFalse(belt.engaged());
        assertEquals("flight lost", belt.lastEnd());
        assertEquals(1, count(all, Kind.GLIDE_OFF));
    }

    @Test
    void flyglideIsNotEndedByTheFlyingFlagBeingTrue() {
        engage(GLIDE, -15);
        for (int i = 0; i < 40; i++) {
            glide(-4);
        }
        assertTrue(belt.engaged());
    }

    @Test
    void flyglideEndsInFluid() {
        engage(GLIDE, -15);
        List<Act> end = tick(GLIDE, false, false, false, true, -4, true, true, 50f);
        assertEquals(1, count(end, Kind.GLIDE_OFF));
        assertEquals(1, count(end, Kind.VELOCITY_CLEAR));
    }

    @Test
    void turningTheToggleOffWhileGlidingClearsTheInstructionAndEndsTheFlightWithOneSnapshotWrite() {
        engage(GLIDE, -15);
        belt.switchOff();
        List<Act> acts = glide(-4);
        assertEquals(1, count(acts, Kind.VELOCITY_CLEAR));
        assertEquals(NORMAL, first(acts, Kind.GLIDE_OFF).settings());
        assertEquals(0, count(acts, Kind.STOP_FLYING), "GLIDE_OFF ends the flight itself");
        assertTrue(belt.idle());
        assertEquals(2, belt.pushes());
        assertTrue(glide(-4).isEmpty());
    }

    @Test
    void deathOrDisconnectWhileGlidingClearsTheInstructionStopsTheFlightAndResetsToTheDefaults() {
        engage(GLIDE, -15);
        belt.reset();
        List<Act> acts = glide(-4);
        assertEquals(1, count(acts, Kind.VELOCITY_CLEAR));
        assertEquals(1, count(acts, Kind.STOP_FLYING));
        assertEquals(1, count(acts, Kind.RESET));
        assertEquals(0, count(acts, Kind.GLIDE_OFF), "an untrusted snapshot is not written back");
        assertTrue(belt.idle());
        assertFalse(belt.instructionActive());
        assertTrue(glide(-4).isEmpty());
    }

    @Test
    void switchingFromAGlidingMkIToMkIIIEndsTheGlideFirstThenArmsFlight() {
        engage(GLIDE, -15);
        belt.switchOff();
        belt.switchOn(Mark.III, NORMAL, 50f, 5f);
        List<Act> first = glide(-4);
        assertEquals(1, count(first, Kind.GLIDE_OFF));
        assertEquals(1, count(first, Kind.VELOCITY_CLEAR));
        assertEquals(0, count(first, Kind.PUSH));
        List<Act> next = tick(GLIDE, true, false, false, false, -4, false, true, 50f);
        assertEquals(new FlySettings(Fly.ALLOWED, 1f, 1f), first(next, Kind.PUSH).settings());
        assertEquals(NORMAL, belt.snapshot());
    }

    @Test
    void switchingFromAFlyingMarkToMkIEndsTheFlightAndRestoresTheSnapshot() {
        belt.switchOn(Mark.III, NORMAL, 50f, 5f);
        tick(GLIDE, false, false, true, 0);
        tick(GLIDE, false, false, false, true, 0, false, true, 50f);
        belt.switchOff();
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        List<Act> acts = tick(GLIDE, false, false, false, true, 0, false, true, 50f);
        assertEquals(1, count(acts, Kind.STOP_FLYING));
        assertEquals(NORMAL, first(acts, Kind.RESTORE).settings());
        assertTrue(belt.isOn(Mark.I_SPEED));
        assertNull(belt.snapshot());
        assertTrue(tick(GLIDE, true, false, false, -15).isEmpty());
        assertEquals(1, count(tick(GLIDE, true, false, false, -15), Kind.GLIDE_ON));
    }

    @Test
    void aMarkThatNeverEngagedHasNothingToRestoreOrClear() {
        belt.switchOn(Mark.I_FAR, NORMAL, 50f, 5f);
        tick(GLIDE, false, false, true, 0);
        belt.switchOff();
        assertTrue(tick(GLIDE, false, false, true, 0).isEmpty());
        belt.switchOn(Mark.I_FAR, NORMAL, 50f, 5f);
        belt.reset();
        assertTrue(tick(GLIDE, false, false, true, 0).isEmpty(), "no reset of settings that were never changed");
        assertEquals(0, belt.pushes());
    }

    @Test
    void settingsPushesAreTwoPerFallOnEveryWayThatChangesSettings() {
        engage(GLIDE, -15);
        for (int i = 0; i < 50; i++) {
            glide(-4);
        }
        tick(GLIDE, false, false, true, true, -4, false, true, 50f);
        tick(GLIDE, false, false, true, true, -4, false, true, 50f);
        assertEquals(2, belt.pushes());
        engage(GLIDE, -15);
        assertEquals(3, belt.pushes());
        assertEquals(2, belt.engages());
    }

    // ---- airdrag

    @Test
    void airdragPushesTheMultiplierForBothLimitsOnceAndRestoresTheSnapshotOnce() {
        List<Act> on = engage(AIRDRAG, -15);
        assertEquals(new Drag(0.9712f, 0.9712f), first(on, Kind.DRAG_ON).drag());
        assertEquals(0, count(on, Kind.VELOCITY));
        for (int i = 0; i < 50; i++) {
            assertTrue(tick(AIRDRAG, true, false, false, -5).isEmpty());
        }
        tick(AIRDRAG, false, false, true, 0);
        List<Act> off = tick(AIRDRAG, false, false, true, 0);
        assertEquals(DRAG, first(off, Kind.DRAG_OFF).drag());
        assertEquals(2, belt.pushes());
        assertEquals(0, belt.refreshes());
    }

    @Test
    void airdragEndsWhenNotFallingForHalfASecondOrWhenFlying() {
        engage(AIRDRAG, -15);
        int ticks = 0;
        boolean ended = false;
        while (!ended && ticks < 100) {
            ended = count(tick(AIRDRAG, false, false, false, 5), Kind.DRAG_OFF) > 0;
            ticks++;
        }
        assertTrue(ticks >= 10 && ticks <= 12, "about 0.5 s: " + ticks);
        engage(AIRDRAG, -15);
        assertEquals(1, count(tick(AIRDRAG, true, false, false, true, -4, false, true, 50f), Kind.DRAG_OFF));
    }

    @Test
    void deathWhileOnTheAirdragWayResetsToTheDefaults() {
        engage(AIRDRAG, -15);
        belt.reset();
        List<Act> acts = tick(AIRDRAG, true, false, false, -4);
        assertEquals(1, count(acts, Kind.RESET));
        assertEquals(0, count(acts, Kind.DRAG_OFF));
        assertTrue(belt.idle());
    }

    @Test
    void theAirdragFormulaGoesBothWays() {
        double step = AirDragMath.FITTED_STEP;
        assertEquals(0.1185, step, 0.0005);
        assertEquals(23.58, AirDragMath.terminalFor(0.995, step), 1e-6, "the fit reproduces the measured terminal speed");
        double m = AirDragMath.airDragFor(4.0, step);
        assertEquals(0.9712, m, 0.0002);
        assertEquals(4.0, AirDragMath.terminalFor(m, step), 1e-9, "and back");
        assertEquals(step, AirDragMath.stepFrom(23.58, 0.995), 1e-9);
        assertTrue(AirDragMath.airDragFor(8.0, step) > m, "a faster target needs a multiplier closer to 1");
    }

    // ---- counter

    @Test
    void theCounterImpulseIsTheOwnExcessSpeedWithTheOppositeSign() {
        assertEquals(19.0, BeltMachine.counterExternal(-23, 0, 4, true), 1e-9);
        assertEquals(23.0, BeltMachine.counterExternal(-8, 19, 4, true), 1e-9, "total: own = -8 - 19 = -27, so 23 cancels it");
        assertEquals(19.0, BeltMachine.counterExternal(-23, 19, 4, false), 1e-9, "own only: the reported speed is the own speed");
        assertEquals(0.0, BeltMachine.counterExternal(-4, 0, 4, true), 1e-9, "already at the target: nothing to cancel");
    }

    @Test
    void counterEngagePushesTheCancellingVelocityAndRefreshesAtMostEveryPushTicks() {
        List<Act> on = engage(COUNTER, -23);
        assertEquals(0, count(on, Kind.GLIDE_ON) + count(on, Kind.DRAG_ON), "no settings are changed on this way");
        assertEquals(19f, first(on, Kind.VELOCITY).amount(), 1e-4);
        int refreshes = 0;
        for (int i = 0; i < 100; i++) {
            refreshes += (int) count(tick(COUNTER, true, false, false, -10), Kind.VELOCITY);
        }
        assertEquals(20, refreshes, "too fast every time, but at most one per 5 ticks");
        int quiet = 0;
        for (int i = 0; i < 100; i++) {
            quiet += (int) count(tick(COUNTER, true, false, false, -4.5), Kind.VELOCITY);
        }
        assertEquals(0, quiet, "within the tolerance of the target: nothing sent");
        assertEquals(0, belt.pushes(), "no settings writes at all");
        tick(COUNTER, false, false, true, 0);
        List<Act> end = tick(COUNTER, false, false, true, 0);
        assertEquals(1, count(end, Kind.VELOCITY_CLEAR));
        assertFalse(belt.instructionActive());
    }

    // ---- Mana

    @Test
    void theDescentIsFreeAtZeroManaPerSecondEvenWithNoMana() {
        belt.switchOn(Mark.I_SPEED, NORMAL, 0f, 5f);
        List<Act> acts = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            acts.addAll(tick(GLIDE, true, false, false, false, -15, false, true, 0f));
        }
        assertEquals(1, count(acts, Kind.GLIDE_ON));
        assertEquals(0, count(glide(-4), Kind.SPEND));
    }

    @Test
    void aPaidDescentChargesPerTickAndEndsWhenManaRunsOutThenWaitsForTheResumeValue() {
        FallRules paid = new FallRules(Way.AIRDRAG, 4.0, 12.0, 2, 5, 0.75, 1f, 0.97f, true, 2f);
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        fallFor(paid, 2, false, -15);
        assertEquals(2f * DT, first(tick(paid, true, false, false, -4), Kind.SPEND).amount(), 1e-6);
        List<Act> cut = tick(paid, true, false, false, false, -4, false, true, 0f);
        assertEquals(1, count(cut, Kind.DRAG_OFF));
        assertEquals(1, count(cut, Kind.NEEDS_MANA));
        assertFalse(belt.engaged());
        assertTrue(tick(paid, true, false, false, false, -15, false, true, 4f).isEmpty());
        assertTrue(tick(paid, true, false, false, false, -15, false, true, 4f).isEmpty());
        assertEquals(1, count(tick(paid, true, false, false, false, -15, false, true, 6f), Kind.DRAG_ON), "armed again and the fall in progress engages");
    }

    @Test
    void aPaidDescentDoesNotEngageWithNoMana() {
        FallRules paid = new FallRules(Way.FLYGLIDE, 4.0, 12.0, 2, 5, 0.75, 1f, 0.97f, true, 2f);
        belt.switchOn(Mark.I_SPEED, NORMAL, 50f, 5f);
        for (int i = 0; i < 10; i++) {
            assertTrue(tick(paid, true, false, false, false, -15, false, true, 0f).isEmpty());
        }
    }

    // ---- the config

    @Test
    void theDefaultsAreTheSpec() {
        Mk1Config m = new Mk1Config();
        assertEquals(Way.FLYGLIDE, m.way());
        assertEquals(4.0, m.descentSpeed());
        assertEquals(12.0, m.speedThreshold());
        assertEquals(2, m.speedTicks());
        assertEquals(1.0, m.glideHorizontal());
        assertEquals(0.75, m.holdTolerance());
        assertEquals(5, m.pushTicks());
        assertEquals(0.0, m.manaPerSecond());
        assertEquals(0.9712, m.airDrag(), 0.0002);
        m.airDrag(0.98);
        assertEquals(0.98, m.airDrag(), 1e-9, "a set value wins over the derived one");
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
    void theFileOfTheFirstMkITestBuildStillLoadsTheOldRoutesAndKeysAreIgnored() throws IOException {
        SuspensorConfig c = SuspensorConfig.CODEC.decodeJson(RawJsonReader.fromJsonString(
                "{\"Mk1\":{\"Listed\":false,\"Route\":\"velocity\",\"DescentSpeed\":6.0,\"SpeedThreshold\":15.0,\"SpeedTicks\":3,\"DragFactor\":0.0,\"Field\":\"mass\","
                        + "\"TerminalSpeed\":22.0,\"PushTicks\":2,\"ManaPerSecond\":0.0}}"), new ExtraInfo());
        Mk1Config m = c.mk1();
        assertFalse(m.listed());
        assertEquals(Way.FLYGLIDE, m.way(), "the removed routes read as the new default");
        assertEquals(6.0, m.descentSpeed());
        assertEquals(15.0, m.speedThreshold());
        assertEquals(3, m.speedTicks());
        assertEquals(2, m.pushTicks());
    }

    @Test
    void theNewKeysAreRead() throws IOException {
        SuspensorConfig c = SuspensorConfig.CODEC.decodeJson(RawJsonReader.fromJsonString(
                "{\"Mk1\":{\"Route\":\"counter\",\"GlideHorizontal\":2.0,\"HoldTolerance\":0.5,\"AirDrag\":0.98,\"GravityStep\":0.2,\"ReportsTotal\":false}}"), new ExtraInfo());
        Mk1Config m = c.mk1();
        assertEquals(Way.COUNTER, m.way());
        assertEquals(2.0, m.glideHorizontal());
        assertEquals(0.5, m.holdTolerance());
        assertEquals(0.98, m.airDrag());
        assertEquals(0.2, m.gravityStep());
        assertFalse(m.reportsTotal());
        assertTrue(SuspensorConfig.CODEC.decodeJson(RawJsonReader.fromJsonString("{}"), new ExtraInfo()).mk1().listed());
    }
}
