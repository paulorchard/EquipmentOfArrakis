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
}
