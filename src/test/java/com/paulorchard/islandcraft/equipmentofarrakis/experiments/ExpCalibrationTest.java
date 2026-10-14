// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
package com.paulorchard.islandcraft.equipmentofarrakis.experiments;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure parts of the calibration tools: the +, - and set handling, the JSON block, and the aiming maths. */
class ExpCalibrationTest {

    @Test
    void aNumberSetsAndPlusAndMinusStepOneStep() {
        ExpCalibration c = ExpCalibration.defaults();
        c.step = 0.01;
        assertEquals("0.31", c.change("fp", "x", "0.31"));
        assertEquals(0.31, c.fp.x, 1e-12);
        assertEquals("0.32", c.change("fp", "x", "+"));
        assertEquals("0.31", c.change("fp", "x", "-"));
        for (int i = 0; i < 30; i++) {
            c.change("tp", "y", "+");
        }
        assertEquals(0.3, c.tp.y, 1e-12, "thirty steps of 0.01 make 0.3, not 0.30000000000000004");
        assertEquals("0.3", ExpCalibration.format(c.tp.y));
    }

    @Test
    void anglesStepByTheAngleStepAndTheViewsAreIndependent() {
        ExpCalibration c = ExpCalibration.defaults();
        c.angleStep = 0.5;
        double before = c.tp.pitch;
        assertEquals("180.5", c.change("fp", "pitch", "+"));
        assertEquals(before, c.tp.pitch, 1e-12, "the other view is not touched");
        assertEquals("180", c.change("fp", "pitch", "-"));
        assertEquals("-1.5", c.change("tp", "yaw", "-1.5"));
    }

    @Test
    void nodeAndDetachedTakeWordsAndBadInputIsRefused() {
        ExpCalibration c = ExpCalibration.defaults();
        assertEquals("R-Hand", c.change("tp", "node", "R-Hand"));
        assertEquals("R-Hand", c.tp.node);
        assertEquals("false", c.change("tp", "detached", "false"));
        assertFalse(c.tp.detached);
        assertThrows(IllegalArgumentException.class, () -> c.change("fp", "x", "banana"));
        assertThrows(IllegalArgumentException.class, () -> c.change("fp", "wobble", "1"));
        assertThrows(IllegalArgumentException.class, () -> c.change("sp", "x", "1"));
        assertThrows(IllegalArgumentException.class, () -> c.change("fp", "x", null));
        assertThrows(IllegalArgumentException.class, () -> c.change("fp", "node", " "));
    }

    @Test
    void theJsonBlockRoundTrips() {
        ExpCalibration c = ExpCalibration.defaults();
        c.apex = 6.25;
        c.step = 0.005;
        c.change("fp", "node", "Chest");
        c.change("fp", "x", "-0.31");
        c.change("fp", "yaw", "1.5");
        c.change("tp", "scale", "0.8");
        c.change("tp", "detached", "false");
        String json = c.toJson();
        ExpCalibration back = ExpCalibration.fromJson(json);
        assertEquals(json, back.toJson(), "writing, reading and writing again changes nothing");
        assertEquals("Chest", back.fp.node);
        assertEquals(-0.31, back.fp.x, 1e-12);
        assertEquals(6.25, back.apex, 1e-12);
        assertFalse(back.tp.detached);
        assertTrue(json.contains("\"fp\"") && json.contains("\"tp\"") && json.contains("\"apex\""));
    }

    @Test
    void whatIsMissingKeepsItsDefaultAndNonsenseIsRefused() {
        ExpCalibration c = ExpCalibration.fromJson("{ \"fp\": { \"x\": 1.5 } }");
        assertEquals(1.5, c.fp.x, 1e-12);
        assertEquals(ExpCalibration.defaultFp().node, c.fp.node);
        assertEquals(5, c.apex, 1e-12);
        assertThrows(IllegalArgumentException.class, () -> ExpCalibration.fromJson("not json"));
    }

    @Test
    void aCopyIsIndependent() {
        ExpCalibration a = ExpCalibration.defaults();
        ExpCalibration b = a.copy();
        b.change("fp", "x", "9");
        assertEquals(ExpCalibration.defaultFp().x, a.fp.x, 1e-12);
    }

    @Test
    void aRayEntersABlockAtItsNearFace() {
        // looking along +X from x=0 at a block whose minimum corner is (3, 0, 0): enters at x=3
        assertEquals(3, ExpAim.entryDistance(0, 0.5, 0.5, 1, 0, 0, 3, 0, 0), 1e-9);
        // looking along -Z from z=10 at the block at z=2 (its far face is at z=3): enters at z=3, seven away
        assertEquals(7, ExpAim.entryDistance(0.5, 0.5, 10, 0, 0, -1, 0, 0, 2), 1e-9);
        // a diagonal ray
        double d = ExpAim.entryDistance(0, 0, 0, Math.sqrt(0.5), Math.sqrt(0.5), 0, 2, 2, -1);
        assertEquals(2 * Math.sqrt(2), d, 1e-9);
        // inside the block: 0; a miss and a block behind: NaN
        assertEquals(0, ExpAim.entryDistance(0.5, 0.5, 0.5, 1, 0, 0, 0, 0, 0), 1e-9);
        assertTrue(Double.isNaN(ExpAim.entryDistance(0, 5, 0, 1, 0, 0, 3, 0, 0)));
        assertTrue(Double.isNaN(ExpAim.entryDistance(10, 0.5, 0.5, 1, 0, 0, 3, 0, 0)));
    }

    @Test
    void aRingStreamMeetsTheApexAtTheAngleAndLifeThatTheMathsGives() {
        double radius = 0.45;
        double length = 3.8;
        double speed = 15.2;
        double angle = ExpAim.inwardAngleDegrees(radius, length);
        assertEquals(6.75, angle, 0.01);
        double life = ExpAim.lifetime(radius, length, speed);
        // flying along the slant for that life covers exactly the length forward and the radius inward
        double forward = speed * life * Math.cos(Math.toRadians(angle));
        double inward = speed * life * Math.sin(Math.toRadians(angle));
        assertEquals(length, forward, 1e-9);
        assertEquals(radius, inward, 1e-9);
        assertEquals(10, ExpAim.along(2, 4, 2), 1e-12);
    }

    @Test
    void theCameraStartRoundTripsAndStepsLikeTheOthers() {
        ExpCalibration c = ExpCalibration.defaults();
        assertEquals("0.16", c.change("cam", "x", "+"));
        assertEquals("0.45", c.change("cam", "z", "0.45"));
        c.endAtBlock = true;
        ExpCalibration back = ExpCalibration.fromJson(c.toJson());
        assertEquals(c.toJson(), back.toJson());
        assertEquals(0.16, back.cam.x, 1e-12);
        assertTrue(back.endAtBlock);
    }

    @Test
    void theYawAndPitchOfADirectionAreTheInverseOfTheEnginesOwn() {
        double[][] directions = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0.3, 0.5, -0.8}, {-0.6, -0.4, 0.2}, {0, 1, 0.001}};
        for (double[] d : directions) {
            double length = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            double[] u = {d[0] / length, d[1] / length, d[2] / length};
            float yaw = (float) ExpAim.yaw(u[0], u[2]);
            float pitch = (float) ExpAim.pitch(u[0], u[1], u[2]);
            // the engine uses a sine table, accurate to about a third of a percent
            var back = com.hypixel.hytale.math.vector.Transform.getDirection(pitch, yaw);
            assertEquals(u[0], back.x, 0.01, "x of " + java.util.Arrays.toString(d));
            assertEquals(u[1], back.y, 0.01, "y of " + java.util.Arrays.toString(d));
            assertEquals(u[2], back.z, 0.01, "z of " + java.util.Arrays.toString(d));
        }
    }

    @Test
    void theCameraFrameIsRightUpForwardAndAPointForwardIsOnTheRay() {
        double[] eye = {10, 64, -5};
        // looking north (-Z): right is +X, up is +Y
        double[] p = ExpAim.cameraPoint(eye, new double[] {0, 0, -1}, 0.2, -0.1, 0.6);
        assertEquals(10.2, p[0], 1e-9);
        assertEquals(63.9, p[1], 1e-9);
        assertEquals(-5.6, p[2], 1e-9);
        // looking east (+X): right is +Z (south)
        double[] q = ExpAim.cameraPoint(eye, new double[] {1, 0, 0}, 0.2, 0, 0);
        assertEquals(10, q[0], 1e-9);
        assertEquals(-4.8, q[2], 1e-9);
        // looking up: the frame is still orthonormal, and a point straight ahead stays on the ray
        double[] up = {0, 1, 0};
        double[] ahead = ExpAim.cameraPoint(eye, up, 0, 0, 3);
        assertEquals(67, ahead[1], 1e-9);
        // right and up are perpendicular to the look direction and to each other
        double[] dir = {0.3, 0.5, -0.8};
        double l = Math.sqrt(0.09 + 0.25 + 0.64);
        double[] d = {dir[0] / l, dir[1] / l, dir[2] / l};
        double[] r = ExpAim.right(d[0], d[1], d[2]);
        double[] u = ExpAim.up(d[0], d[1], d[2]);
        assertEquals(0, r[0] * d[0] + r[1] * d[1] + r[2] * d[2], 1e-9);
        assertEquals(0, u[0] * d[0] + u[1] * d[1] + u[2] * d[2], 1e-9);
        assertEquals(0, r[0] * u[0] + r[1] * u[1] + r[2] * u[2], 1e-9);
        assertEquals(1, Math.sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2]), 1e-9);
    }

    @Test
    void theGripIsWhereARightHandedPlayerHoldsAGunAndABodyPointFollowsTheHeadingNotThePitch() {
        double[] g = ExpAim.gripPoint(1.8);
        assertEquals(0.275, g[0], 1e-9, "shoulder 0.225 plus 0.05");
        assertEquals(-0.324, g[1], 1e-9, "chest height 1.296 against the eyes at 1.62");
        assertEquals(0.45, g[2], 1e-9);
        assertEquals(g[0], ExpCalibration.defaultBody().x, 1e-6);
        double[] eye = {10, 64, -5};
        // looking north: right is +X, forward is -Z
        double[] n = ExpAim.bodyPoint(eye, new double[] {0, 0, -1}, 0.275, -0.324, 0.45);
        assertEquals(10.275, n[0], 1e-9);
        assertEquals(63.676, n[1], 1e-9);
        assertEquals(-5.45, n[2], 1e-9);
        // looking north and 60 degrees up: the body does not pitch, so the point is the same
        double s = Math.sin(Math.toRadians(60));
        double c = Math.cos(Math.toRadians(60));
        double[] up = ExpAim.bodyPoint(eye, new double[] {0, s, -c}, 0.275, -0.324, 0.45);
        assertEquals(n[0], up[0], 1e-9);
        assertEquals(n[1], up[1], 1e-9);
        assertEquals(n[2], up[2], 1e-9);
        // looking east: right is +Z
        double[] e = ExpAim.bodyPoint(eye, new double[] {1, 0, 0}, 0.275, 0, 0.45);
        assertEquals(10.45, e[0], 1e-9);
        assertEquals(-4.725, e[2], 1e-9);
        // straight up does not blow up, and a left-hander is the mirror image
        assertEquals(63.676, ExpAim.bodyPoint(eye, new double[] {0, 1, 0}, 0.275, -0.324, 0.45)[1], 1e-9);
        assertEquals(9.725, ExpAim.bodyPoint(eye, new double[] {0, 0, -1}, -0.275, 0, 0)[0], 1e-9);
    }

    @Test
    void thePulseStepsThroughTheLevelsOnASineAndStaysInRange() {
        int[] seen = new int[4];
        for (int i = 0; i < 1000; i++) {
            int level = ExpAim.pulseLevel(i / 1000.0, 2, 4);
            assertTrue(level >= 0 && level < 4);
            seen[level]++;
        }
        for (int count : seen) {
            assertTrue(count > 0, "every level is used");
        }
        assertEquals(2, ExpAim.pulseLevel(0, 2, 4), "the sine starts in the middle (rounded up)");
        assertEquals(3, ExpAim.pulseLevel(0.125, 2, 4), "a quarter of a cycle at 2 Hz: the brightest");
        assertEquals(0, ExpAim.pulseLevel(0.375, 2, 4), "three quarters: the dimmest");
    }

    @Test
    void theBodyStartFrameAndPulseRoundTrip() {
        ExpCalibration c = ExpCalibration.defaults();
        c.useBody = true;
        c.pulseHz = 1.5;
        assertEquals("0.285", c.change("body", "x", "0.285"));
        ExpCalibration back = ExpCalibration.fromJson(c.toJson());
        assertEquals(c.toJson(), back.toJson());
        assertTrue(back.useBody);
        assertEquals(1.5, back.pulseHz, 1e-12);
        assertEquals(0.285, back.start().x, 1e-12);
        assertEquals(0.15, back.cam.x, 1e-12);
    }
}
