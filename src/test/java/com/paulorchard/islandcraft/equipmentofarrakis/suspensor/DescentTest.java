package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Act;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.Descent;
import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.BeltMachine.DescentState;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The descent with no engine. A tiny client model reports the vertical speed: the player's own input (0 hover, +V Space, -V crouch) plus the
 * push the client has received, one tick after the machine sent it (the lag the debounce exists for).
 */
class DescentTest {

    private static final float DT = 0.05f;
    private static final float V = 2f;
    private static final float N = 2f;
    private static final FlySettings NORMAL = new FlySettings(Fly.DISABLED, 10.32f, 10.32f);
    private static final Speeds SPEEDS = new Speeds(2f, V);
    private static final Descent DESCENT = new Descent(N, 0.4f, 4, 2, 0.5f, 1.0f, 1.0f);

    private final BeltMachine belt = new BeltMachine();
    private Descent descent = DESCENT;
    private Gate gate = Gate.OFF;
    private boolean flying = true;
    private long tick = 0;
    private float appliedPush = 0f;
    private float pendingPush = 0f;
    private final List<Act> all = new ArrayList<>();
    private final List<Long> pushTicks = new ArrayList<>();
    private final List<Long> clearTicks = new ArrayList<>();
    private final List<Long> transitionTicks = new ArrayList<>();
    private int lastTransitions;

    DescentTest() {
        belt.switchOn(NORMAL, 100f, 5f);
    }

    /** One tick: the client reports own + the push it has received; returns the acts. */
    private List<Act> step(double own, boolean onGround, float mana) {
        tick++;
        appliedPush = pendingPush;
        double vy = own + appliedPush;
        List<Act> acts = belt.tick(DT, flying, true, mana, 10f, 5f, NORMAL, SPEEDS, gate, new Ground(false, onGround, tick * 50, tick, vy), descent);
        all.addAll(acts);
        for (Act a : acts) {
            if (a.kind() == Kind.PUSH_DESCENT) {
                pendingPush = a.amount();
                pushTicks.add(tick);
            } else if (a.kind() == Kind.CLEAR_PUSH) {
                pendingPush = 0f;
                clearTicks.add(tick);
            } else if (a.kind() == Kind.STOP_FLYING) {
                flying = false;
            }
        }
        int t = belt.descent().transitions();
        if (t != lastTransitions) {
            transitionTicks.add(tick);
            lastTransitions = t;
        }
        return acts;
    }

    private void run(int ticks, double own, boolean onGround) {
        for (int i = 0; i < ticks; i++) {
            step(own, onGround, 100f);
        }
    }

    private static List<Act> of(List<Act> acts, Kind kind) {
        return acts.stream().filter(a -> a.kind() == kind).toList();
    }

    private long count(Kind kind) {
        return all.stream().filter(a -> a.kind() == kind).count();
    }

    private static DescentTest sim() {
        return new DescentTest();
    }

    @Test
    void theAscendReadingTakesTheOwnSpeedOutOfTheTotal() {
        DescentTest s = sim();
        s.run(10, 0, false); // hover: undecided for the debounce, then the push goes out
        assertEquals(DescentState.DESCENDING, s.belt.descent().state());
        assertEquals(-N, s.appliedPush, 1e-6);
        s.run(6, 0, false);
        assertEquals(0.0, s.belt.descent().own(), 1e-6, "hover with the push in force: vy -2, own 0");
        assertEquals(-2.0, s.belt.descent().vy(), 1e-6);
        s.run(1, V, false);
        assertEquals(V, s.belt.descent().own(), 1e-6, "Space with the push in force: vy 0, own = vy + N = 2");
        assertEquals(0.0, s.belt.descent().vy(), 1e-6);
        // crouch needs no special case: own -V is not ascending
        DescentTest c = sim();
        c.run(10, 0, false);
        c.run(8, -V, false);
        assertEquals(-V, c.belt.descent().own(), 1e-6);
        assertEquals(-4.0, c.belt.descent().vy(), 1e-6);
        assertEquals(DescentState.DESCENDING, c.belt.descent().state());
    }

    @Test
    void theFirstTickOfAFlightWaitsForTheDebounceThenSinksAndAHeldSpaceNeverPushes() {
        DescentTest s = sim();
        for (int i = 1; i <= 4; i++) {
            assertTrue(of(s.step(0, false, 100f), Kind.PUSH_DESCENT).isEmpty(), "ignored during the debounce, tick " + i);
        }
        List<Act> fifth = s.step(0, false, 100f);
        assertEquals(1, of(fifth, Kind.PUSH_DESCENT).size(), "the fifth tick decides: no input, so sink");
        assertEquals(-N, of(fifth, Kind.PUSH_DESCENT).get(0).amount(), 1e-6);
        // a double tap with Space held reads as ascending and never pushes
        DescentTest held = sim();
        held.run(60, V, false);
        assertEquals(0, held.count(Kind.PUSH_DESCENT));
        assertEquals(DescentState.ASCENDING, held.belt.descent().state());
    }

    @Test
    void holdReleaseAndTapChangeTheStateOncePerTransition() {
        DescentTest s = sim();
        s.run(30, 0, false);          // sinking
        s.run(30, V, false);          // Space held
        s.run(30, 0, false);          // released
        assertEquals(1, s.count(Kind.CLEAR_PUSH), "one clear when Space is held");
        assertEquals(3, s.belt.descent().transitions(), "start of the sink, up, down again: three changes");
        assertEquals(DescentState.DESCENDING, s.belt.descent().state());
    }

    @Test
    void rapidTapsNeverToggleEveryTickAndTheChangesAreAtLeastTheDebouncePlusOneTicksApart() {
        for (int width : new int[] {1, 2, 3}) {
            DescentTest s = sim();
            s.run(12, 0, false);
            for (int i = 0; i < 60; i++) {
                s.run(width, V, false);
                s.run(width, 0, false);
            }
            for (int i = 1; i < s.transitionTicks.size(); i++) {
                assertTrue(s.transitionTicks.get(i) - s.transitionTicks.get(i - 1) >= 5, "tap width " + width + ": changes at " + s.transitionTicks);
            }
            long ticks = s.tick;
            assertTrue(s.belt.descent().transitions() <= ticks / 5 + 1, "bounded changes: " + s.belt.descent().transitions() + " in " + ticks + " ticks");
        }
    }

    @Test
    void thePushIsRefreshedAboutOnceASecondWhileDescending() {
        DescentTest s = sim();
        s.run(100, 0, false); // 5 s
        assertTrue(s.pushTicks.size() >= 5 && s.pushTicks.size() <= 6, "the first push and one refresh a second: " + s.pushTicks);
        for (int i = 1; i < s.pushTicks.size(); i++) {
            assertEquals(20, s.pushTicks.get(i) - s.pushTicks.get(i - 1), "refresh every 1.0 s = 20 ticks");
        }
        assertEquals(1, s.belt.descent().sends() - s.belt.descent().refreshes(), "one real push, the rest refreshes");
        assertEquals(0, s.belt.descent().transitions() - 1);
    }

    @Test
    void aDriftOfTheReadingForSixTicksSendsOnceAndTheBaselineFollows() {
        DescentTest s = sim();
        s.run(12, 0, false);
        int before = s.belt.descent().sends();
        s.run(10, -V, false); // crouch: vy -4 against an expected -2
        assertEquals(1, s.belt.descent().drifts(), "one drift refresh after six ticks of deviation");
        s.run(10, -V, false);
        assertEquals(1, s.belt.descent().drifts(), "the baseline followed the new input: no repeat");
        assertEquals(before + 1, s.belt.descent().sends() - s.belt.descent().refreshes() + 0 * 1, "one send for the drift (refreshes counted apart)");
        // a lost push (the client reports no push): read as ascending, cleared, then pushed again: it heals itself
        DescentTest lost = sim();
        lost.run(12, 0, false);
        lost.pendingPush = 0f;
        lost.appliedPush = 0f;
        lost.run(30, 0, false);
        assertEquals(DescentState.DESCENDING, lost.belt.descent().state());
        assertTrue(lost.belt.descent().sends() >= 2);
    }

    @Test
    void groundExitAfterTwoTicksOnTheGroundOutsideTheGrace() {
        DescentTest s = sim();
        s.run(30, 0, false); // sinking since 1.5 s
        assertTrue(of(s.step(0, true, 100f), Kind.STOP_FLYING).isEmpty(), "one tick on the ground: not yet");
        List<Act> second = s.step(0, true, 100f);
        assertEquals(1, of(second, Kind.STOP_FLYING).size());
        assertEquals(1, of(second, Kind.CLEAR_PUSH).size());
        assertEquals(1, of(second, Kind.HUM_OFF).size());
        assertEquals(1, s.belt.descent().groundExits());
        assertFalse(s.belt.descent().pushInForce());
        assertFalse(s.flying);
    }

    @Test
    void aHoverJustAboveTheGroundDoesNotExit() {
        DescentTest s = sim();
        s.run(30, 0, false);
        s.run(1, 0, true);
        s.run(1, 0, false); // the ground contact is not two in a row
        s.run(1, 0, true);
        s.run(30, 0, false);
        assertEquals(0, s.belt.descent().groundExits());
        assertEquals(0, s.count(Kind.STOP_FLYING));
    }

    @Test
    void theGraceAfterTheFlightStartAndAfterTheLastAscendingTickKeepsTheGround() {
        // a double tap on the ground with no keys: 0.5 s of grace from the start (10 ticks), then the exit
        DescentTest s = sim();
        for (int i = 1; i <= 9; i++) {
            assertTrue(of(s.step(0, true, 100f), Kind.STOP_FLYING).isEmpty(), "inside the grace, tick " + i);
        }
        s.run(2, 0, true);
        assertEquals(1, s.belt.descent().groundExits(), "exits once the grace is over");
    }

    @Test
    void ascendingIgnoresTheGroundAndTheTakeoffWorks() {
        DescentTest s = sim();
        s.run(60, V, true); // Space held on the ground for 3 s
        assertEquals(0, s.belt.descent().groundExits());
        assertTrue(s.flying);
        s.run(3, V, true);
        s.run(5, V, false); // lifted off
        assertEquals(0, s.count(Kind.STOP_FLYING));
        // released while still touching the ground: the grace after the last ascending tick holds for 0.5 s, then the exit
        DescentTest t = sim();
        t.run(30, V, true);
        for (int i = 1; i <= 9; i++) {
            assertTrue(of(t.step(0, true, 100f), Kind.STOP_FLYING).isEmpty(), "grace after the last ascending tick, tick " + i);
        }
        t.run(3, 0, true);
        assertEquals(1, t.belt.descent().groundExits());
    }

    @Test
    void everyWayAFlightEndsClearsThePushThatIsInForce() {
        // toggle off
        DescentTest off = sim();
        off.run(20, 0, false);
        off.belt.switchOff();
        assertEquals(1, of(off.step(0, false, 100f), Kind.CLEAR_PUSH).size(), "belt off");
        // death, respawn, world change, disconnect: reset()
        DescentTest dead = sim();
        dead.run(20, 0, false);
        dead.belt.reset();
        assertEquals(1, of(dead.step(0, false, 100f), Kind.CLEAR_PUSH).size(), "reset");
        // leaving Adventure
        DescentTest mode = sim();
        mode.run(20, 0, false);
        mode.tick++;
        List<Act> left = mode.belt.tick(DT, true, false, 100f, 10f, 5f, NORMAL, SPEEDS, Gate.OFF, new Ground(false, false, 1, 1, -2.0), DESCENT);
        assertEquals(1, of(left, Kind.CLEAR_PUSH).size(), "left Adventure");
        // the client ends the flight itself
        DescentTest ended = sim();
        ended.run(20, 0, false);
        ended.flying = false;
        assertEquals(1, of(ended.step(0, false, 100f), Kind.CLEAR_PUSH).size(), "flight ended");
        assertEquals(DescentState.NONE, ended.belt.descent().state());
        // the speed set to 0 live
        DescentTest zero = sim();
        zero.run(20, 0, false);
        zero.descent = new Descent(0f, 0.4f, 4, 2, 0.5f, 1f, 1f);
        assertEquals(1, of(zero.step(0, false, 100f), Kind.CLEAR_PUSH).size(), "descent 0");
        // nothing to clear when no push is in force
        DescentTest none = sim();
        none.run(2, V, false);
        none.belt.switchOff();
        assertTrue(of(none.step(0, false, 100f), Kind.CLEAR_PUSH).isEmpty());
    }

    @Test
    void manaRunningOutWhileDescendingStopsFlightAndClearsThePush() {
        DescentTest s = sim();
        s.run(20, 0, false);
        List<Act> cut = s.step(0, false, 0f);
        assertEquals(1, of(cut, Kind.STOP_FLYING).size());
        assertEquals(1, of(cut, Kind.CLEAR_PUSH).size());
        assertEquals(1, of(cut, Kind.NEEDS_MANA).size());
        assertFalse(s.belt.armed());
        assertEquals(DescentState.NONE, s.belt.descent().state());
        assertFalse(s.belt.descent().pushInForce());
    }

    @Test
    void aChangedDescentSpeedGoesOutAtOnceAndOnlyTheYComponentIsSent() {
        DescentTest s = sim();
        s.run(20, 0, false);
        s.descent = new Descent(4f, 0.4f, 4, 2, 0.5f, 1f, 1f);
        List<Act> acts = s.step(0, false, 100f);
        assertEquals(-4f, of(acts, Kind.PUSH_DESCENT).get(0).amount(), 1e-6);
        s.run(6, 0, false);
        assertEquals(0.0, s.belt.descent().own(), 1e-6, "own is still read right with the new push in force");
        assertEquals(DescentState.DESCENDING, s.belt.descent().state());
    }

    @Test
    void theCostFactorAppliesOnlyWhileDescending() {
        DescentTest s = sim();
        s.descent = new Descent(N, 0.4f, 4, 2, 0.5f, 1f, 0f);
        float undecided = of(s.step(0, false, 100f), Kind.SPEND).get(0).amount();
        assertEquals(0.5f, undecided, 1e-6, "undecided: the full cost");
        s.run(8, 0, false);
        assertEquals(DescentState.DESCENDING, s.belt.descent().state());
        assertEquals(0f, of(s.step(0, false, 100f), Kind.SPEND).get(0).amount(), 1e-6, "descending with factor 0: free");
        s.run(2, V, false);
        s.run(8, V, false);
        assertEquals(DescentState.ASCENDING, s.belt.descent().state());
        assertEquals(0.5f, of(s.step(V, false, 100f), Kind.SPEND).get(0).amount(), 1e-6, "ascending: the full cost");
        DescentTest half = sim();
        half.descent = new Descent(N, 0.4f, 4, 2, 0.5f, 1f, 0.5f);
        half.run(10, 0, false);
        assertEquals(0.25f, of(half.step(0, false, 100f), Kind.SPEND).get(0).amount(), 1e-6);
    }

    @Test
    void descentSpeedZeroIsTheHoverOfTheEarlierBuilds() {
        DescentTest s = sim();
        s.descent = Descent.OFF;
        s.run(100, 0, false);
        s.run(30, V, true);
        s.run(30, -V, true);
        assertEquals(0, s.count(Kind.PUSH_DESCENT));
        assertEquals(0, s.count(Kind.CLEAR_PUSH));
        assertEquals(0, s.count(Kind.STOP_FLYING), "no ground exit either: today's hover on the ground");
        assertEquals(160, s.count(Kind.SPEND));
        assertTrue(s.all.stream().filter(a -> a.kind() == Kind.SPEND).allMatch(a -> Math.abs(a.amount() - 0.5f) < 1e-6));
        assertEquals(DescentState.NONE, s.belt.descent().state());
    }

    @Test
    void afterAGroundExitTheTapGateTakesOverAsForAnyFlightEnd() {
        DescentTest s = sim();
        s.gate = new Gate(0.35f, GateMode.AFTER_JUMP, 1.2f);
        // the belt is armed with the gate closed; a window is open for the flight to start in, then flight holds the gate open
        s.belt.switchOff();
        s.step(0, false, 100f);
        s.belt.switchOn(NORMAL, 100f, 5f);
        s.flying = false;
        s.step(0, true, 100f);
        s.tick++;
        s.belt.tick(DT, false, true, 100f, 10f, 5f, NORMAL, SPEEDS, s.gate, new Ground(true, false, s.tick * 50, s.tick, 0.0), DESCENT); // the jump start opens the gate
        s.flying = true;
        s.run(30, 0, false);
        s.run(2, 0, true); // the ground exit
        assertEquals(1, s.belt.descent().groundExits());
        List<Act> next = s.step(0, true, 100f); // flight is over: the gate closes
        assertTrue(next.stream().anyMatch(a -> a.kind() == Kind.PUSH && a.settings().mode() == Fly.DISABLED), "the gate closes the fly setting: " + next);
    }
}
