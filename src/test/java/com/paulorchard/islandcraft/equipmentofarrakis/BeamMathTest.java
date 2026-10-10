package com.paulorchard.islandcraft.equipmentofarrakis;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        // At x=3 the ray is at y=1, which is the top face (y=1) of the block at y 0: enters at the corner, t = 3*sqrt(2).
        assertEquals(3 * Math.sqrt(2), d, 1e-9);
    }

    @Test
    void startingInsideTheBlockIsDistanceZero() {
        assertEquals(0.0, BeamMath.entryDistance(new Vector3d(0.5, 0.5, 0.5), new Vector3d(1, 0, 0),
                new Vector3i(0, 0, 0)), 1e-9);
    }
}
