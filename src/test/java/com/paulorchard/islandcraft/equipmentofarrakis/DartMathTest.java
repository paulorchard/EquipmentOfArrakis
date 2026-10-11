package com.paulorchard.islandcraft.equipmentofarrakis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DartMathTest {

    @Test
    void distanceIsStraightLine() {
        assertEquals(5.0, DartMath.distance(new double[] {0, 0, 0}, new double[] {3, 4, 0}), 1e-12);
        assertEquals(40.0, DartMath.distance(new double[] {1, 2, 3}, new double[] {41, 2, 3}), 1e-12);
        assertEquals(0.0, DartMath.distance(new double[] {1, 2, 3}, new double[] {1, 2, 3}), 0.0);
    }

    @Test
    void exactlyTheRangeIsStillInRangeAndBeyondIsNot() {
        double[] launch = {0, 200.9, 0};
        assertFalse(DartMath.beyond(launch, new double[] {39.99, 200.9, 0}, 40));
        assertFalse(DartMath.beyond(launch, new double[] {40, 200.9, 0}, 40));
        assertTrue(DartMath.beyond(launch, new double[] {40.01, 200.9, 0}, 40));
        assertTrue(DartMath.beyond(launch, new double[] {0, 200.9, -41}, 40));
    }

    @Test
    void theNearestPointOfABoxIsMeasuredFromOutsideAndIsZeroInside() {
        double[] min = {40.7, 200, -0.3};
        double[] max = {41.3, 201.8, 0.3};
        assertEquals(40.7, DartMath.distanceToBox(new double[] {0, 200.9, 0}, min, max), 1e-12, "head on: the face");
        assertEquals(0.0, DartMath.distanceToBox(new double[] {41, 201, 0}, min, max), 0.0, "inside");
        // from above and beside: the nearest point is a corner or an edge
        assertEquals(Math.sqrt(1 + 4), DartMath.distanceToBox(new double[] {40.7 - 1, 203.8, 0}, min, max), 1e-12);
    }

    @Test
    void aTargetFortyOnePointThreeAwayIsOutOfRangeAndOneAtThirtyNinePointFiveIsIn() {
        double[] eye = {0, 200.9, 0};
        // a target whose centre is 41 blocks away has its near face at 40.7: beyond the 40 block range
        assertTrue(DartMath.distanceToBox(eye, new double[] {40.7, 200, -0.3}, new double[] {41.3, 201.8, 0.3}) > 40);
        // a target centred at 39.5 has its near face at 39.2
        assertTrue(DartMath.distanceToBox(eye, new double[] {39.2, 200, -0.3}, new double[] {39.8, 201.8, 0.3}) <= 40);
    }
}
