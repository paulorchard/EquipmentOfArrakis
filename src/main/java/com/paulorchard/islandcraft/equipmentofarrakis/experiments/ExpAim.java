// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
package com.paulorchard.islandcraft.equipmentofarrakis.experiments;

/**
 * The small pure maths of aiming, no engine: where a look ray enters a block (for the target marker), the point at a distance along a
 * ray, and the angle at which a stream that starts on a ring must be tilted inwards to meet at a point (the convergence of the
 * Flame Cutter's stream, see make_flame_focus.js).
 */
final class ExpAim {

    private ExpAim() {
    }

    /**
     * The distance along the ray (origin, direction) at which it enters the unit cube of the block whose minimum corner is (bx, by, bz),
     * the slab method; 0 when the origin is inside, NaN when the ray misses it.
     */
    static double entryDistance(double ox, double oy, double oz, double dx, double dy, double dz, int bx, int by, int bz) {
        double near = Double.NEGATIVE_INFINITY;
        double far = Double.POSITIVE_INFINITY;
        double[] origin = {ox, oy, oz};
        double[] direction = {dx, dy, dz};
        int[] min = {bx, by, bz};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(direction[i]) < 1e-12) {
                if (origin[i] < min[i] || origin[i] > min[i] + 1) {
                    return Double.NaN;
                }
                continue;
            }
            double t1 = (min[i] - origin[i]) / direction[i];
            double t2 = (min[i] + 1 - origin[i]) / direction[i];
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
        }
        if (near > far || far < 0) {
            return Double.NaN;
        }
        return Math.max(0, near);
    }

    /** origin + distance * direction, one coordinate. */
    static double along(double origin, double direction, double distance) {
        return origin + direction * distance;
    }

    /** The angle (degrees) towards the axis of a stream that starts {@code radius} off the axis and must meet it {@code length} on. */
    static double inwardAngleDegrees(double radius, double length) {
        return Math.toDegrees(Math.atan2(radius, length));
    }

    /** The distance a particle flies when it starts on the ring and meets the axis {@code length} on: the slant of the cone. */
    static double slant(double radius, double length) {
        return Math.hypot(radius, length);
    }

    /** The life that brings a particle of {@code speed} along that slant exactly to the apex. */
    static double lifetime(double radius, double length, double speed) {
        return slant(radius, length) / speed;
    }
}
