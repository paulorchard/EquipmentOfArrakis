package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Act;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Fly;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.FlySettings;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Gate;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.GateMode;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Ground;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Kind;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Speeds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tap gate of the belt, with no engine: scripted ticks of 50 ms, both modes, the push counts, the resets. */
class TapGateTest {

    private static final float DT = 0.05f;
    private static final FlySettings NORMAL = new FlySettings(Fly.DISABLED, 10.32f, 10.32f);
    private static final Speeds SPEEDS = new Speeds(2f, 2f);
    private static final Gate AFTER = new Gate(0.35f, GateMode.AFTER_JUMP, 1.2f);
    private static final Gate GROUND = new Gate(0.35f, GateMode.GROUND_OPEN, 1.2f);

    private final BeltMachine belt = new BeltMachine();
    private Gate gate = AFTER;
    private long tick = 100;
    private final List<Act> all = new ArrayList<>();

    private List<Act> step(boolean flying, boolean jumping, boolean onGround, float mana) {
        tick++;
        List<Act> acts = belt.tick(DT, flying, true, mana, 10f, 5f, NORMAL, SPEEDS, gate, new Ground(jumping, onGround, tick * 50, tick));
        all.addAll(acts);
        return acts;
    }

    private List<Act> ground() {
        return step(false, false, true, 100f);
    }

    private List<Act> air() {
        return step(false, false, false, 100f);
    }

    private List<Act> jump() {
        return step(false, true, false, 100f);
    }

    private static List<Act> pushes(List<Act> acts) {
        return acts.stream().filter(a -> a.kind() == Kind.PUSH).toList();
    }

    private long pushCount() {
        return pushes(all).size();
    }

    private static boolean allowed(Act push) {
        return push.settings().mode() == Fly.ALLOWED;
    }

    private void standOnGround(int ticks) {
        for (int i = 0; i < ticks; i++) {
            ground();
        }
    }

    @Test
    void closedOnArmNothingIsPushedWhileStanding() {
        belt.switchOn(NORMAL, 100f, 5f);
        standOnGround(40);
        assertEquals(0, pushCount(), "the settings in force are already Disabled: no write");
        assertFalse(belt.gate().open());
    }

    @Test
    void aJumpStartOpensTheGateAndItClosesAfterTheWindowWithTwoPushes() {
        belt.switchOn(NORMAL, 100f, 5f);
        standOnGround(10);
        List<Act> opened = jump();
        assertEquals(1, pushes(opened).size());
        assertTrue(allowed(pushes(opened).get(0)));
        assertEquals(new FlySettings(Fly.ALLOWED, 2f, 2f), pushes(opened).get(0).settings());
        assertTrue(opened.stream().anyMatch(a -> a.kind() == Kind.LOG && a.text().startsWith("gate open (afterJump): jump start tick 111 ms 5550, push at tick 111 ms 5550 (+0 ticks, +0 ms)")), opened.toString());
        assertTrue(belt.gate().open());
        // the window is 0.35 s = 7 ticks of 50 ms from the jump start: open for 6 more ticks, closed on the 7th
        for (int i = 0; i < 6; i++) {
            assertTrue(pushes(air()).isEmpty(), "still open, no push");
        }
        List<Act> closed = air();
        assertEquals(1, pushes(closed).size());
        assertFalse(allowed(pushes(closed).get(0)));
        assertEquals(NORMAL, pushes(closed).get(0).settings(), "closed means the snapshot's settings, as the disarmed state");
        assertTrue(closed.stream().anyMatch(a -> a.kind() == Kind.LOG && a.text().startsWith("gate closed (afterJump)")));
        assertEquals(2, pushCount());
        assertEquals(1, belt.gate().windows());
    }

    @Test
    void anotherJumpStartInsideTheWindowRestartsTheTimerWithoutAPush() {
        belt.switchOn(NORMAL, 100f, 5f);
        standOnGround(5);
        jump();
        for (int i = 0; i < 3; i++) {
            air();
        }
        step(false, false, true, 100f); // landed
        List<Act> second = jump(); // a second jump start, 5 ticks in
        assertTrue(pushes(second).isEmpty(), "no push for the restart");
        for (int i = 0; i < 6; i++) {
            assertTrue(pushes(air()).isEmpty(), "open for a full window from the second jump start (the first would have ended already)");
        }
        assertEquals(1, pushes(air()).size(), "closed at 0.35 s after the second jump start");
        assertEquals(2, pushCount());
    }

    @Test
    void bunnyHopsPushTwiceAPerHopAndNeverPerTick() {
        belt.switchOn(NORMAL, 100f, 5f);
        standOnGround(10);
        int hops = 8;
        for (int h = 0; h < hops; h++) {
            jump();
            for (int i = 0; i < 12; i++) { // 0.6 s in the air
                air();
            }
            standOnGround(6); // 0.3 s on the ground, then the next hop
        }
        assertEquals(2L * hops, pushCount(), "open and close, once per hop");
        assertEquals(hops, belt.gate().windows());
    }

    @Test
    void aJumpFlagInTheAirWithNoRecentGroundIsNotAJumpStart() {
        belt.switchOn(NORMAL, 100f, 5f);
        standOnGround(5);
        air();
        for (int i = 0; i < 10; i++) {
            air(); // 0.5 s off the ground
        }
        assertTrue(pushes(jump()).isEmpty(), "a jumping flag 0.5 s after leaving the ground does nothing");
        assertEquals(0, pushCount());
    }

    @Test
    void flyingKeepsTheGateOpenAndTheEndOfFlightClosesItAgain() {
        belt.switchOn(NORMAL, 100f, 5f);
        standOnGround(5);
        jump();
        List<Act> start = step(true, false, false, 100f); // the second press: flight, inside the window
        assertTrue(pushes(start).isEmpty());
        for (int i = 0; i < 100; i++) { // 5 s of flight, far past the window
            assertTrue(pushes(step(true, false, false, 100f)).isEmpty(), "flying keeps Allowed, no push");
        }
        assertEquals(1, belt.gate().flightsInWindow());
        assertEquals(0, belt.gate().flightsWithGap());
        List<Act> end = step(false, false, false, 100f);
        assertEquals(1, pushes(end).size(), "flight ended: back to the gate, closed");
        assertFalse(allowed(pushes(end).get(0)));
        assertEquals(2, pushCount());
        // the press that ends the flight is a jump flag edge but not a jump start
        assertTrue(pushes(step(false, true, false, 100f)).isEmpty());
        assertFalse(belt.gate().open());
    }

    @Test
    void aFlightThatStartsLongAfterTheLastJumpCountsAsWithAGap() {
        belt.switchOn(NORMAL, 100f, 5f);
        standOnGround(5);
        jump();
        for (int i = 0; i < 30; i++) {
            air();
        }
        step(true, false, false, 100f); // flight starts 1.5 s after the jump start with the gate closed (a client that ignores it)
        assertEquals(0, belt.gate().flightsInWindow());
        assertEquals(1, belt.gate().flightsWithGap());
    }

    @Test
    void notArmedStaysDisabledWhateverTheGateAndTheCutOffIsUnchanged() {
        belt.switchOn(NORMAL, 3f, 5f); // under the resume value: unarmed
        for (int i = 0; i < 3; i++) {
            step(false, false, true, 3f);
        }
        assertTrue(pushes(step(false, true, false, 3f)).isEmpty(), "unarmed: a jump start opens nothing");
        belt.reset();
        step(false, false, true, 3f);
        // the cut-off at 0 Mana, as without the gate
        BeltMachine other = new BeltMachine();
        other.switchOn(NORMAL, 100f, 5f);
        List<Act> acts = new ArrayList<>();
        acts.addAll(other.tick(DT, false, true, 100f, 10f, 5f, NORMAL, SPEEDS, AFTER, new Ground(false, true, 1, 1)));
        acts.addAll(other.tick(DT, false, true, 100f, 10f, 5f, NORMAL, SPEEDS, AFTER, new Ground(true, false, 2, 2)));
        acts.addAll(other.tick(DT, true, true, 100f, 10f, 5f, NORMAL, SPEEDS, AFTER, new Ground(false, false, 3, 3)));
        List<Act> cut = other.tick(DT, true, true, 0f, 10f, 5f, NORMAL, SPEEDS, AFTER, new Ground(false, false, 4, 4));
        assertTrue(cut.stream().anyMatch(a -> a.kind() == Kind.STOP_FLYING));
        assertTrue(cut.stream().anyMatch(a -> a.kind() == Kind.NEEDS_MANA));
        assertEquals(1, pushes(cut).size());
        assertFalse(allowed(pushes(cut).get(0)), "cut off: Disabled");
        assertFalse(other.armed());
    }

    @Test
    void aTapWindowOfZeroIsTheBeltWithoutAGate() {
        gate = Gate.OFF;
        belt.switchOn(NORMAL, 100f, 5f);
        List<Act> first = ground();
        assertEquals(1, pushes(first).size());
        assertEquals(new FlySettings(Fly.ALLOWED, 2f, 2f), pushes(first).get(0).settings(), "Allowed as soon as it is armed");
        for (int h = 0; h < 5; h++) {
            jump();
            for (int i = 0; i < 12; i++) {
                air();
            }
            standOnGround(6);
        }
        step(true, false, false, 100f);
        step(false, false, false, 100f);
        assertEquals(1, pushCount(), "hops and flights push nothing more");
        assertEquals(0, belt.gate().windows());
    }

    @Test
    void theGateCanBeSwitchedLiveAndFollowsAtOnce() {
        gate = Gate.OFF;
        belt.switchOn(NORMAL, 100f, 5f);
        standOnGround(3);
        assertEquals(1, pushCount());
        gate = AFTER;
        List<Act> closed = ground();
        assertEquals(1, pushes(closed).size());
        assertFalse(allowed(pushes(closed).get(0)));
        gate = Gate.OFF;
        List<Act> back = ground();
        assertTrue(allowed(pushes(back).get(0)));
    }

    @Test
    void switchingOffInTheMiddleOfAWindowRestoresTheSnapshotAndTheNextOnStartsClean() {
        belt.switchOn(NORMAL, 100f, 5f);
        standOnGround(5);
        jump();
        belt.switchOff();
        List<Act> off = air();
        assertEquals(Kind.RESTORE, off.stream().filter(a -> a.kind() == Kind.RESTORE).findFirst().orElseThrow().kind());
        assertEquals(NORMAL, off.stream().filter(a -> a.kind() == Kind.RESTORE).findFirst().orElseThrow().settings());
        assertTrue(belt.idle());
        assertFalse(belt.gate().open());
        belt.switchOn(NORMAL, 100f, 5f);
        List<Act> next = ground();
        assertTrue(pushes(next).isEmpty(), "closed again");
    }

    @Test
    void deathRespawnWorldChangeAndDisconnectResetToTheDefaultsAsBefore() {
        belt.switchOn(NORMAL, 100f, 5f);
        standOnGround(5);
        jump();
        assertTrue(belt.gate().open());
        belt.reset();
        List<Act> dead = air();
        assertNotNull(dead.stream().filter(a -> a.kind() == Kind.RESET).findFirst().orElse(null), "settings reset to the defaults, the snapshot is not trusted");
        assertTrue(belt.idle());
        assertFalse(belt.gate().open());
    }

    @Test
    void groundOpenIsOpenAtSwitchOnWhenStandingClosesAfterAJumpAndReopensOnlyAfterTheGroundTime() {
        gate = GROUND;
        belt.switchOn(NORMAL, 100f, 5f);
        List<Act> first = ground();
        assertEquals(1, pushes(first).size());
        assertTrue(allowed(pushes(first).get(0)), "standing: open from the start");
        standOnGround(10);
        assertEquals(1, pushCount());
        assertTrue(pushes(jump()).isEmpty(), "a jump start inside an open gate: no push");
        for (int i = 0; i < 6; i++) {
            assertTrue(pushes(air()).isEmpty());
        }
        List<Act> closed = air();
        assertEquals(1, pushes(closed).size());
        assertFalse(allowed(pushes(closed).get(0)));
        // lands and stays on the ground: closed for 1.2 s = 24 ticks of ground contact
        for (int i = 0; i < 23; i++) {
            assertTrue(pushes(ground()).isEmpty(), "closed after landing, tick " + i);
        }
        List<Act> reopened = ground();
        assertEquals(1, pushes(reopened).size());
        assertTrue(allowed(pushes(reopened).get(0)), "reopened after ReopenSeconds on the ground");
        assertEquals(3, pushCount());
    }

    @Test
    void groundOpenBunnyHopsDoNotReopenInBetween() {
        gate = GROUND;
        belt.switchOn(NORMAL, 100f, 5f);
        standOnGround(5);
        long before = pushCount();
        for (int h = 0; h < 6; h++) {
            jump();
            for (int i = 0; i < 12; i++) {
                air();
            }
            standOnGround(6); // 0.3 s on the ground is far less than 1.2 s
        }
        assertEquals(before + 1, pushCount(), "one close at the first hop, no reopen while hopping");
    }

    @Test
    void groundOpenStartedInTheAirStaysClosedUntilTheGround() {
        gate = GROUND;
        belt.switchOn(NORMAL, 100f, 5f);
        for (int i = 0; i < 10; i++) {
            air();
        }
        assertEquals(0, pushCount());
        standOnGround(23);
        assertEquals(0, pushCount());
        assertEquals(1, pushes(ground()).size());
    }
}
