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
}
