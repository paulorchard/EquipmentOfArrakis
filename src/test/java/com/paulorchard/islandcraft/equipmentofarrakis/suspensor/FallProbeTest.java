package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The fall probe and the drop checks. */
class FallProbeTest {

    @Test
    void itReportsEveryFlipWithHeightTimeAndSpeedAndNothingElse() {
        FallProbe probe = new FallProbe(false);
        assertEquals(1, probe.observe(false, false, true, false, 100, 0, 0, false, 0).size(), "the first call says where it started");
        assertTrue(probe.observe(false, false, true, false, 100, 0, 50, false, 0).isEmpty());
        List<String> start = probe.observe(true, false, false, false, 99, -2, 1000, false, 0);
        assertEquals(2, start.size());
        assertTrue(start.get(0).startsWith("falling false -> true"), start.get(0));
        assertTrue(start.get(1).startsWith("onGround true -> false"), start.get(1));
        assertTrue(probe.observe(true, false, false, false, 90, -18, 1500, false, 0).isEmpty(), "no flip, no line");
        List<String> far = probe.observe(true, true, false, false, 80, -21, 2000, false, 0);
        assertEquals(1, far.size());
        assertTrue(far.get(0).contains("fallingFar false -> true") && far.get(0).contains("height above the last ground 20.00") && far.get(0).contains("1.95 s since onGround")
                && far.get(0).contains("vy -21.00"), far.get(0));
        List<String> land = probe.observe(false, false, true, false, 60, 0, 3000, false, 0);
        assertTrue(land.stream().anyMatch(l -> l.contains("onGround false -> true") && l.contains("fastest fall -21.00")), land.toString());
    }

    @Test
    void whileEngagedItReportsTheSpeedEveryQuarterSecond() {
        FallProbe probe = new FallProbe(false);
        probe.observe(true, true, false, false, 100, -10, 0, false, 0);
        int lines = 0;
        for (int t = 100; t <= 2000; t += 50) {
            lines += probe.observe(true, true, false, false, 100 - t / 100.0, -4, t, true, 0).size();
        }
        assertTrue(lines >= 7 && lines <= 9, "about four lines a second for two seconds: " + lines);
    }

    @Test
    void dropRefusesTooFarAboveTheBuildLimitAndBlocksInTheWay() {
        assertNull(DropRules.refuse(64, 20, y -> false));
        assertTrue(DropRules.refuse(64, 0, y -> false).contains("from 1 to"));
        assertTrue(DropRules.refuse(64, 300, y -> false).contains("from 1 to"));
        assertTrue(DropRules.refuse(300, 20, y -> false).contains("build limit"));
        assertTrue(DropRules.refuse(64, 20, y -> y == 70).contains("row 70"));
        assertTrue(DropRules.refuse(64, 20, y -> y == 86).contains("row 86"), "the headroom above the landing spot counts");
        assertNull(DropRules.refuse(64, 20, y -> y == 87));
    }

    @Test
    void theProbeAlsoReportsTheFlyingFlagAndTheInstructionCountWhileEngaged() {
        FallProbe probe = new FallProbe(false);
        probe.observe(true, false, false, false, 100, -10, 0, false, 0);
        List<String> flip = probe.observe(true, false, false, true, 99, -4, 50, true, 1);
        assertTrue(flip.stream().anyMatch(l -> l.startsWith("flying false -> true")), flip.toString());
        assertTrue(flip.stream().anyMatch(l -> l.contains("instructions 1") && l.contains("flying true")), flip.toString());
    }

    @Test
    void theImpulseWatchSamplesEveryTenthOfASecondForTwoSeconds() {
        ImpulseWatch watch = new ImpulseWatch(1000, "y 10 r 1");
        int lines = 0;
        for (long t = 1000; t <= 3300; t += 20) {
            String line = watch.sample(t, 5.0, 4.0, 1, false, false);
            if (line != null) {
                lines++;
                assertTrue(line.contains("client vy 5.00") && line.contains("server vy 4.00") && line.contains("instructions 1"), line);
            }
        }
        assertTrue(lines >= 19 && lines <= 22, "about 20 lines: " + lines);
        assertTrue(watch.finished(3200));
        assertTrue(!watch.finished(2900));
    }
}
