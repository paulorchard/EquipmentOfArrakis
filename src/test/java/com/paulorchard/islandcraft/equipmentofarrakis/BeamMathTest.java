package com.paulorchard.islandcraft.equipmentofarrakis;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeamMathTest {

    @Test
    void entersThroughTheNearFace() {
        // Looking along +x from x=0.5, the block at x=3 starts 2.5 blocks away.
        assertEquals(2.5, BeamMath.entryDistance(new Vector3d(0.5, 1.5, 0.5), new Vector3d(1, 0, 0),
                new Vector3i(3, 1, 0)), 1e-9);
        // Looking along -y at a block below.
        assertEquals(2.0, BeamMath.entryDistance(new Vector3d(0.5, 5.0, 0.5), new Vector3d(0, -1, 0),
                new Vector3i(0, 2, 0)), 1e-9);
    }

    @Test
    void slantedRayUsesTheFaceItCrossesFirst() {
        Vector3d dir = new Vector3d(1, -1, 0).normalize();
        double d = BeamMath.entryDistance(new Vector3d(0, 4, 0.5), dir, new Vector3i(3, 0, 0));
        // At x=3 the ray is at y=1, the top face of the block at y 0: it enters at the corner, t = 3 * sqrt(2).
        assertEquals(3 * Math.sqrt(2), d, 1e-9);
    }

    @Test
    void startingInsideTheBlockIsDistanceZero() {
        assertEquals(0.0, BeamMath.entryDistance(new Vector3d(0.5, 0.5, 0.5), new Vector3d(1, 0, 0),
                new Vector3i(0, 0, 0)), 1e-9);
    }

    @Test
    void aStillOrFirstAimPointIsJustThatPoint() {
        Vector3d now = new Vector3d(1, 2, 3);
        assertEquals(List.of(now), BeamMath.trailPoints(null, now, 0.1, 1.5));
        assertEquals(List.of(now), BeamMath.trailPoints(new Vector3d(1.05, 2, 3), now, 0.1, 1.5));
    }

    @Test
    void aMovingAimIsFilledInEvenlyUpToTheCurrentPoint() {
        List<Vector3d> points = BeamMath.trailPoints(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0), 0.1, 1.5);
        assertEquals(10, points.size());
        assertEquals(1.0, points.get(points.size() - 1).x, 1e-9);
        double last = 0;
        for (Vector3d p : points) {
            assertTrue(p.x - last <= 0.1 + 1e-9);
            last = p.x;
        }
    }

    @Test
    void aJumpFartherThanTheLinkStartsANewStroke() {
        Vector3d now = new Vector3d(5, 0, 0);
        assertEquals(List.of(now), BeamMath.trailPoints(new Vector3d(0, 0, 0), now, 0.1, 1.5));
    }
}
