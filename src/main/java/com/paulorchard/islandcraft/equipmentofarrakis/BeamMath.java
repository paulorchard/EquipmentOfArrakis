package com.paulorchard.islandcraft.equipmentofarrakis;

import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;

/** The geometry of the Cutter Ray, kept free of engine classes so it can be tested on its own. */
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

    /**
     * The points to spawn the effect at for a move of the aim point from {@code previous} to {@code current}: the
     * current point, and the points between it and the previous one no more than {@code spacing} apart, ending at the
     * current point. With no previous point, or one farther than {@code maxLink} away (the aim jumped to another
     * surface), it is just the current point. Never empty.
     */
    static List<Vector3d> trailPoints(Vector3d previous, Vector3d current, double spacing, double maxLink) {
        List<Vector3d> points = new ArrayList<>();
        double gap = previous == null ? 0 : previous.distance(current);
        if (previous == null || gap > maxLink || gap <= spacing) {
            points.add(new Vector3d(current));
            return points;
        }
        int steps = (int) Math.ceil(gap / spacing);
        for (int i = 1; i <= steps; i++) {
            points.add(new Vector3d(previous).lerp(current, (double) i / steps));
        }
        return points;
    }
}
