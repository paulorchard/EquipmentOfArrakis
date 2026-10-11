package com.paulorchard.islandcraft.equipmentofarrakis;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

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
    void theEntryFaceIsTheOneTheRayCrossesFirst() {
        // Along +x into the block at x=3: it enters through the west face (normal -x), whose middle is (3, 1.5, 0.5).
        BeamMath.Entry west = BeamMath.entry(new Vector3d(0.5, 1.5, 0.5), new Vector3d(1, 0, 0), new Vector3i(3, 1, 0));
        assertEquals(new Vector3i(-1, 0, 0), west.normal());
        assertEquals(new Vector3d(3, 1.5, 0.5), west.faceCenter());
        assertEquals(2.5, west.distance(), 1e-9);
        // Down onto a block: the top face (normal +y), middle at the block's top.
        BeamMath.Entry top = BeamMath.entry(new Vector3d(0.5, 5.0, 0.5), new Vector3d(0, -1, 0), new Vector3i(0, 2, 0));
        assertEquals(new Vector3i(0, 1, 0), top.normal());
        assertEquals(new Vector3d(0.5, 3, 0.5), top.faceCenter());
        // Looking along -z at a block ahead: it enters through the +z face of the block at z=-2.
        BeamMath.Entry south = BeamMath.entry(new Vector3d(0.5, 0.5, 5.0), new Vector3d(0, 0, -1), new Vector3i(0, 0, -2));
        assertEquals(new Vector3i(0, 0, 1), south.normal());
        assertEquals(new Vector3d(0.5, 0.5, -1), south.faceCenter());
    }

    @Test
    void aSlantedRayEntersThroughTheFaceItCrossesFirst() {
        // From (0, 4.5, 0.5) going right and down into the block at (3, 1, 0): at x=3 the ray is at y=1.5, inside the
        // block's side, so it enters through the west face.
        Vector3d dir = new Vector3d(1, -1, 0).normalize();
        BeamMath.Entry e = BeamMath.entry(new Vector3d(0, 4.5, 0.5), dir, new Vector3i(3, 1, 0));
        assertEquals(new Vector3i(-1, 0, 0), e.normal());
        // A shallow ray that reaches the top of the block before its side enters through the top.
        Vector3d shallow = new Vector3d(2, -1, 0).normalize();
        BeamMath.Entry top = BeamMath.entry(new Vector3d(0, 2.7, 0.5), shallow, new Vector3i(3, 0, 0));
        assertEquals(new Vector3i(0, 1, 0), top.normal());
    }

    @Test
    void aRayStartingInsideGetsTheFaceOppositeItsDirection() {
        BeamMath.Entry e = BeamMath.entry(new Vector3d(0.5, 0.5, 0.5), new Vector3d(1, 0.2, 0).normalize(),
                new Vector3i(0, 0, 0));
        assertEquals(0.0, e.distance(), 1e-9);
        assertEquals(new Vector3i(-1, 0, 0), e.normal());
    }

    @Test
    void theGlobPointIsClearOfTheFace() {
        BeamMath.Entry e = BeamMath.entry(new Vector3d(0.5, 5.0, 0.5), new Vector3d(0, -1, 0), new Vector3i(0, 2, 0));
        assertEquals(new Vector3d(0.5, 3.08, 0.5), BeamMath.offFace(e, 0.08));
    }
}
