package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Act;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Fly;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.FlySettings;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Kind;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Speeds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The belt's rules with no engine: arm/disarm, the Mana charge, pushes only on change, snapshot and restore. */
class BeltMachineTest {

    private static final float DT = 0.05f;
    private static final float PER_SECOND = 10f;
    private static final float RESUME = 5f;
    private static final FlySettings NORMAL = new FlySettings(Fly.DISABLED, 10.32f, 10.32f);

    private final BeltMachine belt = new BeltMachine();

    private static final Speeds SPEEDS = new Speeds(0.5f, 0.5f);

    private List<Act> tick(boolean flying, float mana) {
        return belt.tick(DT, flying, true, mana, PER_SECOND, RESUME, NORMAL, SPEEDS);
    }

    private static long count(List<Act> acts, Kind kind) {
        return acts.stream().filter(a -> a.kind() == kind).count();
    }

    private static Act first(List<Act> acts, Kind kind) {
        return acts.stream().filter(a -> a.kind() == kind).findFirst().orElse(null);
    }

    @Test
    void switchingOnTakesTheSnapshotAndTheFirstTickPushesAllowedWithTheBeltSpeedsOnce() {
        assertTrue(belt.switchOn(NORMAL, 50f, RESUME));
        assertEquals(NORMAL, belt.snapshot());
        List<Act> first = tick(false, 50f);
        assertEquals(new FlySettings(Fly.ALLOWED, 0.5f, 0.5f), first(first, Kind.PUSH).settings());
        assertEquals(1, belt.pushes());
        for (int i = 0; i < 200; i++) {
            assertTrue(tick(false, 50f).isEmpty(), "a quiet belt sends nothing");
        }
        assertEquals(1, belt.pushes());
    }

    @Test
    void flyingChargesManaPerSecondTimesTheTickAndHums() {
        belt.switchOn(NORMAL, 50f, RESUME);
        tick(false, 50f);
        List<Act> acts = tick(true, 50f);
        assertEquals(PER_SECOND * DT, first(acts, Kind.SPEND).amount(), 1e-6);
        assertEquals(0.5f, first(acts, Kind.SPEND).amount(), 1e-6, "10 a second on a 0.05 s tick");
        assertEquals(1, count(acts, Kind.HUM));
        float total = 0f;
        for (int i = 0; i < 20; i++) {
            total += first(tick(true, 50f), Kind.SPEND).amount();
        }
        assertEquals(10.0f, total, 1e-3, "twenty ticks of 0.05 s, one second, cost 10 Mana");
    }

    @Test
    void notFlyingCostsNothingAndTheHumStopsWhenFlightEnds() {
        belt.switchOn(NORMAL, 50f, RESUME);
        tick(false, 50f);
        tick(true, 50f);
        List<Act> end = tick(false, 50f);
        assertEquals(1, count(end, Kind.HUM_OFF));
        assertEquals(0, count(end, Kind.SPEND));
        assertTrue(tick(false, 50f).isEmpty());
    }

    @Test
    void runningOutOfManaWhileFlyingStopsTheFlightDisarmsAndPushesDisabledWithTheSnapshotSpeeds() {
        belt.switchOn(NORMAL, 50f, RESUME);
        tick(false, 50f);
        tick(true, 1f);
        List<Act> cut = tick(true, 0f);
        assertEquals(1, count(cut, Kind.STOP_FLYING));
        assertEquals(1, count(cut, Kind.NEEDS_MANA));
        assertEquals(0, count(cut, Kind.SPEND));
        assertEquals(new FlySettings(Fly.DISABLED, 10.32f, 10.32f), first(cut, Kind.PUSH).settings(), "a double tap does nothing now");
        assertTrue(belt.isOn(), "the toggle stays ON");
        assertFalse(belt.armed());
    }

    @Test
    void unarmedStaysQuietBelowTheThresholdAndRearmsAtItWithOnePush() {
        belt.switchOn(NORMAL, 50f, RESUME);
        tick(false, 50f);
        tick(true, 0f);
        for (int i = 0; i < 50; i++) {
            assertTrue(tick(false, 4.9f).isEmpty(), "under the resume value nothing happens and nothing is sent");
        }
        List<Act> back = tick(false, 5f);
        assertTrue(belt.armed());
        assertEquals(new FlySettings(Fly.ALLOWED, 0.5f, 0.5f), first(back, Kind.PUSH).settings());
        assertEquals(1, count(back, Kind.PUSH));
        assertTrue(tick(false, 5f).isEmpty());
    }

    @Test
    void aFlightThatContinuesAfterTheCutOffIsStoppedAgainOnlyOncePerSecond() {
        belt.switchOn(NORMAL, 50f, RESUME);
        tick(false, 50f);
        tick(true, 0f);
        int stops = 0;
        for (int i = 0; i < 60; i++) {
            stops += (int) count(tick(true, 0f), Kind.STOP_FLYING);
        }
        assertTrue(stops >= 2 && stops <= 3, "60 ticks of 0.05 s = 3 seconds, one stop a second, not 60: " + stops);
    }

    @Test
    void switchingOnWithTooLittleManaStartsUnarmedAndSendsNothing() {
        assertFalse(belt.switchOn(NORMAL, 2f, RESUME));
        assertTrue(tick(false, 2f).isEmpty(), "the settings in force are the snapshot already");
        assertEquals(0, belt.pushes());
        assertTrue(!tick(false, 6f).isEmpty());
        assertTrue(belt.armed());
    }

    @Test
    void switchingOffStopsFlightAndRestoresTheSnapshotOnce() {
        belt.switchOn(NORMAL, 50f, RESUME);
        tick(false, 50f);
        tick(true, 50f);
        belt.switchOff();
        List<Act> off = tick(true, 50f);
        assertEquals(1, count(off, Kind.STOP_FLYING));
        Act restore = first(off, Kind.RESTORE);
        assertEquals(NORMAL, restore.settings());
        assertEquals(0, count(off, Kind.RESET));
        assertEquals(0, count(off, Kind.SPEND), "no charge on the tick the belt goes off");
        assertTrue(tick(false, 50f).isEmpty());
        assertTrue(belt.idle());
        assertNull(belt.snapshot());
    }

    @Test
    void switchingOffOnTheGroundRestoresWithoutStoppingAnything() {
        belt.switchOn(NORMAL, 50f, RESUME);
        tick(false, 50f);
        belt.switchOff();
        List<Act> off = tick(false, 50f);
        assertEquals(0, count(off, Kind.STOP_FLYING));
        assertEquals(1, count(off, Kind.RESTORE));
    }

    @Test
    void deathOrWorldChangeStopsFlightAndResetsToTheDefaultsInsteadOfTheSnapshot() {
        belt.switchOn(NORMAL, 50f, RESUME);
        tick(false, 50f);
        tick(true, 50f);
        belt.reset();
        assertFalse(belt.isOn());
        List<Act> acts = tick(true, 50f);
        assertEquals(1, count(acts, Kind.STOP_FLYING));
        assertEquals(1, count(acts, Kind.RESET));
        assertEquals(0, count(acts, Kind.RESTORE));
        assertTrue(belt.idle());
        assertTrue(tick(false, 50f).isEmpty());
    }

    @Test
    void aResetOfABeltThatWasNeverOnDoesNothing() {
        belt.reset();
        assertTrue(belt.idle());
        assertTrue(tick(true, 50f).isEmpty());
    }

    @Test
    void leavingAdventureLetsGoWithAReset() {
        belt.switchOn(NORMAL, 50f, RESUME);
        tick(false, 50f);
        List<Act> acts = belt.tick(DT, false, false, 50f, PER_SECOND, RESUME, NORMAL, SPEEDS);
        assertEquals(1, count(acts, Kind.RESET));
        assertFalse(belt.isOn());
    }

    @Test
    void aScriptedSessionPushesOnlyWhenTheStateChanges() {
        belt.switchOn(NORMAL, 50f, RESUME);                       // on: push 1 (Allowed)
        for (int i = 0; i < 100; i++) {
            tick(false, 50f);
        }
        for (int i = 0; i < 100; i++) {
            tick(true, 50f);                                               // flying: charge, no push
        }
        tick(true, 0f);                                                    // cut off: push 2 (Disabled)
        for (int i = 0; i < 100; i++) {
            tick(false, 1f);
        }
        tick(false, 6f);                                                   // re-armed: push 3
        belt.switchOff();
        tick(false, 6f);                                                   // off: write 4 (restore)
        assertEquals(4, belt.pushes());
    }

    @Test
    void theSnapshotIsTakenFromTheTickWhenTheHookCouldNotTakeIt() {
        FlySettings creativeLike = new FlySettings(Fly.ALLOWED, 3f, 4f);
        belt.switchOn(null, 50f, RESUME);
        assertNull(belt.snapshot());
        belt.tick(DT, false, true, 50f, PER_SECOND, RESUME, creativeLike, SPEEDS);
        assertNotNull(belt.snapshot());
        assertEquals(creativeLike, belt.snapshot());
    }

    @Test
    void newSpeedsAreAPushedOnceToAFlyingPlayerAndNeverFlightEnding() {
        belt.switchOn(NORMAL, 50f, RESUME);
        tick(false, 50f);
        tick(true, 50f);
        List<Act> acts = belt.tick(DT, true, true, 50f, PER_SECOND, RESUME, NORMAL, new Speeds(1f, 1f));
        assertEquals(new FlySettings(Fly.ALLOWED, 1f, 1f), first(acts, Kind.PUSH).settings());
        assertEquals(0, count(acts, Kind.STOP_FLYING), "a player who flies keeps flying at the new speed");
        assertEquals(1, count(acts, Kind.SPEND));
        assertTrue(belt.tick(DT, true, true, 50f, PER_SECOND, RESUME, NORMAL, new Speeds(1f, 1f)).stream().noneMatch(a -> a.kind() == Kind.PUSH), "once");
    }

    @Test
    void aNewCostChangesTheChargeAtOnce() {
        belt.switchOn(NORMAL, 50f, RESUME);
        tick(false, 50f);
        Act spend = first(belt.tick(DT, true, true, 50f, 2f, RESUME, NORMAL, SPEEDS), Kind.SPEND);
        assertEquals(2f * DT, spend.amount(), 1e-6);
    }

    @Test
    void theWholeLifeOfABeltInOneScriptedSequence() {
        assertTrue(belt.switchOn(NORMAL, 50f, RESUME));                    // toggle on
        assertEquals(1, count(tick(false, 50f), Kind.PUSH));              // Allowed
        assertEquals(1, count(tick(true, 50f), Kind.SPEND));              // double tap: flight starts, charged
        assertEquals(1, count(tick(true, 0f), Kind.NEEDS_MANA));          // Mana runs out: cut off
        assertFalse(belt.armed());
        assertTrue(tick(false, 4f).isEmpty());                             // under the threshold: quiet
        assertEquals(1, count(tick(false, 5f), Kind.PUSH));                // resume at the threshold
        assertTrue(belt.armed());
        belt.switchOff();                                                  // toggle off
        assertEquals(1, count(tick(false, 5f), Kind.RESTORE));
        assertTrue(belt.idle());
        belt.switchOn(NORMAL, 50f, RESUME);                                // on again, then death
        tick(true, 50f);
        belt.reset();
        List<Act> dead = tick(true, 50f);
        assertEquals(1, count(dead, Kind.STOP_FLYING));
        assertEquals(1, count(dead, Kind.RESET));
        assertTrue(belt.idle());
    }

    @Test
    void switchingOnAgainBeforeTheRestoreRunsStillEndsWithTheOriginalSettings() {
        belt.switchOn(NORMAL, 50f, RESUME);
        tick(false, 50f);
        belt.switchOff();
        belt.switchOn(new FlySettings(Fly.ALLOWED, 0.5f, 0.5f), 50f, RESUME);
        assertEquals(NORMAL, first(tick(false, 50f), Kind.RESTORE).settings());
        tick(false, 50f);
        belt.switchOff();
        assertEquals(NORMAL, first(tick(false, 50f), Kind.RESTORE).settings(), "the second snapshot is the restored settings, not the belt's");
    }
}
