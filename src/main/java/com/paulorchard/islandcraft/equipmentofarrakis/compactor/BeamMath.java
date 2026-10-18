package com.paulorchard.islandcraft.equipmentofarrakis.compactor;

import org.joml.Vector3d;
import org.joml.Vector3i;

/** The geometry of the Cutter Ray and the Compactor (where a look ray meets a block), kept free of engine classes so it can be tested on its own. */
final class BeamMath {

    private BeamMath() {
    }

    /**
     * Where a ray enters a unit block: the distance along the ray, the face it crosses (as the outward normal of
     * that face, one of the six axis directions) and the middle of that face.
     */
    record Entry(double distance, Vector3i normal, Vector3d faceCenter) {
    }

    /**
     * The face of the unit block at {@code block} that the ray enters through. A ray that starts inside the block (or
     * grazes it without a clean entry) is given the face opposite its largest direction component, at distance 0.
     */
    static Entry entry(Vector3d origin, Vector3d dir, Vector3i block) {
        double near = 0;
        double far = Double.MAX_VALUE;
        int entryAxis = -1;
        double[] o = {origin.x, origin.y, origin.z};
        double[] d = {dir.x, dir.y, dir.z};
        int[] b = {block.x, block.y, block.z};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(d[axis]) < 1e-9) {
                continue;
            }
            double t1 = (b[axis] - o[axis]) / d[axis];
            double t2 = (b[axis] + 1 - o[axis]) / d[axis];
            double enter = Math.min(t1, t2);
            if (enter > near) {
                near = enter;
                entryAxis = axis;
            }
            far = Math.min(far, Math.max(t1, t2));
        }
        double distance = near;
        if (near > far || entryAxis < 0) {
            distance = 0;
            entryAxis = 0;
            for (int axis = 1; axis < 3; axis++) {
                if (Math.abs(d[axis]) > Math.abs(d[entryAxis])) {
                    entryAxis = axis;
                }
            }
        }
        int[] normal = new int[3];
        normal[entryAxis] = d[entryAxis] > 0 ? -1 : 1;
        Vector3d center = new Vector3d(b[0] + 0.5, b[1] + 0.5, b[2] + 0.5);
        center.add(normal[0] * 0.5, normal[1] * 0.5, normal[2] * 0.5);
        return new Entry(distance, new Vector3i(normal[0], normal[1], normal[2]), center);
    }

    /** The point {@code clearance} out from the middle of a face, along its normal. */
    static Vector3d offFace(Entry entry, double clearance) {
        return new Vector3d(entry.faceCenter()).fma(clearance, new Vector3d(entry.normal().x, entry.normal().y,
                entry.normal().z));
    }
}
