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
        assertEquals(1, probe.observe(false, false, true, 100, 0, 0, false).size(), "the first call says where it started");
        assertTrue(probe.observe(false, false, true, 100, 0, 50, false).isEmpty());
        List<String> start = probe.observe(true, false, false, 99, -2, 1000, false);
        assertEquals(2, start.size());
        assertTrue(start.get(0).startsWith("falling false -> true"), start.get(0));
        assertTrue(start.get(1).startsWith("onGround true -> false"), start.get(1));
        assertTrue(probe.observe(true, false, false, 90, -18, 1500, false).isEmpty(), "no flip, no line");
        List<String> far = probe.observe(true, true, false, 80, -21, 2000, false);
        assertEquals(1, far.size());
        assertTrue(far.get(0).contains("fallingFar false -> true") && far.get(0).contains("height above the last ground 20.00") && far.get(0).contains("1.95 s since onGround")
                && far.get(0).contains("vy -21.00"), far.get(0));
        List<String> land = probe.observe(false, false, true, 60, 0, 3000, false);
        assertTrue(land.stream().anyMatch(l -> l.contains("onGround false -> true") && l.contains("fastest fall -21.00")), land.toString());
    }

    @Test
    void whileEngagedItReportsTheSpeedEveryQuarterSecond() {
        FallProbe probe = new FallProbe(false);
        probe.observe(true, true, false, 100, -10, 0, false);
        int lines = 0;
        for (int t = 100; t <= 2000; t += 50) {
            lines += probe.observe(true, true, false, 100 - t / 100.0, -4, t, true).size();
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
}
