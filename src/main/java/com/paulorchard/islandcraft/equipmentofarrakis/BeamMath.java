package com.paulorchard.islandcraft.equipmentofarrakis;

import org.joml.Vector3d;
import org.joml.Vector3i;

/** The geometry of the Cutter Ray beam, kept free of engine classes so it can be tested on its own. */
final class BeamMath {

    private BeamMath() {
    }

    /** Distance along the ray to the face of the unit block at {@code block} that it enters through. */
    static double entryDistance(Vector3d origin, Vector3d dir, Vector3i block) {
        double near = 0;
        double far = Double.MAX_VALUE;
        double[] o = {origin.x, origin.y, origin.z};
        double[] d = {dir.x, dir.y, dir.z};
        int[] b = {block.x, block.y, block.z};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(d[axis]) < 1e-9) {
                continue;
            }
            double t1 = (b[axis] - o[axis]) / d[axis];
            double t2 = (b[axis] + 1 - o[axis]) / d[axis];
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
        }
        return near <= far ? near : 0;
    }

    /** Number of beam lengths: 1.0, 1.5, ... 5.0 blocks. */
    static final int STEPS = 9;
    static final double FIRST_LENGTH = 1.0;
    static final double STEP_LENGTH = 0.5;

    /**
     * The beam length step (1 to {@link #STEPS}) for a surface {@code distance} blocks away: the shortest beam that
     * reaches it, so the beam may poke up to half a block into the surface but never stops short of it. Anything
     * closer than the first length gets step 1, anything farther than the last gets the last.
     */
    static int stepForDistance(double distance) {
        int step = (int) Math.ceil((distance - FIRST_LENGTH) / STEP_LENGTH - 1e-9) + 1;
        return Math.max(1, Math.min(STEPS, step));
    }

    /** Length in blocks of a step: 1 + 0.5 * (step - 1). */
    static double lengthOfStep(int step) {
        return FIRST_LENGTH + STEP_LENGTH * (step - 1);
    }
}
