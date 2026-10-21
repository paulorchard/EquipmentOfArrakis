package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.PushRig.Kind;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.PushRig.Step;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TEST DEVICE: the push test rig (cap, the hold schedule, every way it ends in one CLEAR). Delete with the rig. */
class PushRigTest {

    private static long count(List<Step> steps, Kind kind) {
        return steps.stream().filter(s -> s.kind() == kind).count();
    }

    /** Runs {@code ticks} ticks of 50 ms with the player flying and the belt on; returns the tick of each SEND. */
    private static List<Integer> sendTicks(PushRig rig, int ticks) {
        List<Integer> at = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            if (count(rig.tick(true, true, 1000L + i * 50L, -2, -2, false), Kind.SEND) > 0) {
                at.add(i);
            }
        }
        return at;
    }

    @Test
    void theCapRefusesAboveItAndNaN() {
        assertTrue(PushRig.allowed(6, 6));
        assertTrue(PushRig.allowed(-6, 6));
        assertTrue(PushRig.allowed(0, 6));
        assertFalse(PushRig.allowed(6.01, 6));
        assertFalse(PushRig.allowed(-7, 6));
        assertFalse(PushRig.allowed(Double.NaN, 6));
        assertFalse(PushRig.allowed(1, 0), "a cap of 0 refuses everything but 0");
    }

    @Test
    void aSinglePushSendsOnceAndStaysUntilCleared() {
        PushRig rig = new PushRig();
        rig.startOnce(-2f, 1f, 1000);
        assertEquals(List.of(0), sendTicks(rig, 100), "one send, on the first tick");
        assertTrue(rig.active(), "the velocity is still in force, so the rig is still on");
        assertEquals(1, rig.sends());
    }

    @Test
    void theHoldSendsEveryNTicks() {
        for (int every : new int[] {2, 5, 10}) {
            PushRig rig = new PushRig();
            rig.startHold(-2f, 1f, every, 1000);
            List<Integer> at = sendTicks(rig, 41);
            assertEquals(0, at.get(0));
            for (int i = 1; i < at.size(); i++) {
                assertEquals(every, at.get(i) - at.get(i - 1), "every " + every);
            }
            assertEquals(1 + 40 / every, at.size());
        }
        PushRig zero = new PushRig();
        zero.startHold(-2f, 1f, 0, 1000);
        assertEquals(41, sendTicks(zero, 41).size(), "0 ticks is taken as 1");
    }

    @Test
    void offClearsOnceAndStopsTheHold() {
        PushRig rig = new PushRig();
        rig.startHold(-2f, 1f, 5, 1000);
        sendTicks(rig, 12);
        rig.cancel();
        List<Step> end = rig.tick(true, true, 2000, 0, 0, false);
        assertEquals(1, count(end, Kind.CLEAR));
        assertEquals(0, count(end, Kind.SEND));
        assertFalse(rig.active());
        assertTrue(rig.tick(true, true, 2050, 0, 0, false).isEmpty(), "nothing more after the clear");
    }

    @Test
    void everyWayThePushCanEndEndsInOneClear() {
        // flight ended (landing, the client's own stop, the cut-off at 0 Mana)
        PushRig landed = new PushRig();
        landed.startHold(-2f, 1f, 5, 1000);
        sendTicks(landed, 3);
        assertEquals(1, count(landed.tick(false, true, 3000, 0, 0, true), Kind.CLEAR));
        assertFalse(landed.active());
        // the belt went off
        PushRig beltOff = new PushRig();
        beltOff.startOnce(-2f, 1f, 1000);
        sendTicks(beltOff, 3);
        assertEquals(1, count(beltOff.tick(true, false, 3000, 0, 0, false), Kind.CLEAR));
        // death, respawn, a world change and the off hook all cancel(): the next tick clears, even when the player is not flying any more
        PushRig reset = new PushRig();
        reset.startHold(-2f, 1f, 2, 1000);
        sendTicks(reset, 3);
        reset.cancel();
        List<Step> end = reset.tick(false, false, 3000, 0, 0, false);
        assertEquals(1, count(end, Kind.CLEAR));
        assertEquals(0, count(end, Kind.SEND));
        // cancelling a rig that is not running does nothing, and an old cancel does not eat a later push
        PushRig idle = new PushRig();
        idle.cancel();
        assertTrue(idle.tick(true, true, 1000, 0, 0, false).isEmpty());
        idle.startOnce(-1f, 1f, 1000);
        assertEquals(1, count(idle.tick(true, true, 1000, 0, 0, false), Kind.SEND));
    }

    @Test
    void theWatchLogsEveryTenthOfASecondForThreeSecondsAndTheHoldForAsLongAsItRuns() {
        PushRig once = new PushRig();
        once.startOnce(-2f, 1f, 0);
        long lines = 0;
        for (int i = 0; i <= 100; i++) { // 5 s of 50 ms ticks
            lines += count(once.tick(true, true, i * 50L, -2, -2, false), Kind.LOG);
        }
        assertTrue(lines >= 28 && lines <= 32, "about 30 samples in 3 s, got " + lines);
        PushRig hold = new PushRig();
        hold.startHold(-2f, 1f, 5, 0);
        long holdLines = 0;
        for (int i = 0; i <= 100; i++) {
            holdLines += count(hold.tick(true, true, i * 50L, -2, -2, false), Kind.LOG);
        }
        assertTrue(holdLines >= 48, "the hold keeps sampling, got " + holdLines);
    }

    @Test
    void theSampleLineSaysTheSendRate() {
        PushRig rig = new PushRig();
        rig.startHold(-2f, 1f, 2, 0);
        String last = null;
        for (int i = 0; i <= 40; i++) {
            for (Step s : rig.tick(true, true, i * 50L, -2.1, -2.0, false)) {
                if (s.kind() == Kind.LOG) {
                    last = s.text();
                }
            }
        }
        assertTrue(last.contains("hold every 2") && last.contains("client vy -2.10") && last.contains("actual vy -2.00") && last.contains("a second"), last);
    }
}
