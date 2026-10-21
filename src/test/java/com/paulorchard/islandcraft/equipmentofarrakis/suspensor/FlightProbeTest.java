package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.paulorchard.islandcraft.equipmentofarrakis.suspensor.FlightProbe.Sample;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TEST DEVICE: the flight probe's lines (flips, height above ground, the quarter second sample). Delete with the probe. */
class FlightProbeTest {

    private static Sample s(long tick, long ms, boolean flying, boolean jumping, boolean crouching, boolean onGround, double y, double vy) {
        return new Sample(tick, ms, 0.05f, flying, jumping, crouching, onGround, false, false, y, vy, "(0.00, 1.00, 0.00)", 80f, true, "OPEN (afterJump, window 0.35 s)");
    }

    @Test
    void theFirstSampleOnlySaysTheProbeStartedAndFlipsAreNamedWithHeightAndTimeSinceGround() {
        FlightProbe probe = new FlightProbe();
        List<String> first = probe.observe(s(1, 1000, false, false, false, true, 64, 0));
        assertEquals(1, first.size());
        assertTrue(first.get(0).contains("probe started"), first.get(0));
        assertTrue(probe.observe(s(2, 1050, false, false, false, true, 64, 0)).isEmpty(), "no change, no line");
        List<String> up = probe.observe(s(3, 1100, true, true, false, false, 64.3, 5));
        String all = String.join("\n", up);
        assertTrue(all.contains("flip flying false->true") && all.contains("flip jumping false->true") && all.contains("flip onGround true->false"), all);
        assertTrue(all.contains("0.30 above ground") && all.contains("0.05 s"), all);
    }

    @Test
    void whileFlyingALineComesEveryQuarterSecondWithTheNumbers() {
        FlightProbe probe = new FlightProbe();
        probe.observe(s(1, 1000, false, false, false, true, 64, 0));
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 40; i++) { // 2 s of flight at 50 ms
            lines.addAll(probe.observe(s(1 + i, 1000 + i * 50L, true, false, false, false, 64 + 10 + i * 0.1, 2.0)));
        }
        long periodic = lines.stream().filter(l -> l.contains(" flying: flags ")).count();
        assertTrue(periodic >= 7 && periodic <= 9, "one every 0.25 s for 2 s, got " + periodic);
        String line = lines.stream().filter(l -> l.contains(" flying: flags ")).skip(2).findFirst().orElseThrow();
        assertTrue(line.contains("client vy 2.00") && line.contains("actual vy 2.00") && line.contains("wish (0.00, 1.00, 0.00)") && line.contains("Mana 80.0")
                && line.contains("armed true"), line);
    }

    @Test
    void aFlightStartLineGivesTheGapFromTheLastJumpStartAndTheGate() {
        FlightProbe probe = new FlightProbe();
        probe.observe(s(1, 1000, false, false, false, true, 64, 0));
        probe.observe(s(2, 1050, false, true, false, false, 64.2, 5)); // jump start at ms 1050
        probe.observe(s(3, 1100, false, true, false, false, 64.8, 4));
        List<String> start = probe.observe(s(4, 1250, true, false, false, false, 65, 3));
        String line = start.stream().filter(l -> l.contains("FLIGHT START")).findFirst().orElseThrow();
        assertTrue(line.contains("gap from the last jump start 200 ms (2 ticks)") && line.contains("gate OPEN (afterJump, window 0.35 s)") && line.contains("onGround false"), line);
        // no jump seen before a flight: said, not invented
        FlightProbe fresh = new FlightProbe();
        fresh.observe(s(1, 1000, false, false, false, true, 64, 0));
        String none = String.join("\n", fresh.observe(s(2, 1050, true, false, false, true, 64, 0)));
        assertTrue(none.contains("n/a (no jump seen)"), none);
    }

    @Test
    void noGroundSeenIsSaidAndNotInvented() {
        FlightProbe probe = new FlightProbe();
        probe.observe(s(1, 1000, true, false, false, false, 80, 0));
        String all = String.join("\n", probe.observe(s(2, 1050, true, false, true, false, 80, 0)));
        assertTrue(all.contains("flip crouching false->true") && all.contains("n/a (no ground seen)") && all.contains("since onGround n/a"), all);
    }

    @Test
    void notFlyingMeansNoPeriodicLinesButFlipsStillShow() {
        FlightProbe probe = new FlightProbe();
        probe.observe(s(1, 1000, false, false, false, true, 64, 0));
        for (int i = 1; i <= 20; i++) {
            assertTrue(probe.observe(s(1 + i, 1000 + i * 50L, false, false, false, true, 64, 0)).isEmpty());
        }
    }
}
