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

    // ---- aiming a world-space particle system at a point

    /**
     * The yaw (radians) that turns the forward direction (0, 0, -1) to point along (dx, dz) on the ground. The engine's own
     * Transform.getDirection(pitch, yaw) is (-cos(pitch) sin(yaw), sin(pitch), -cos(pitch) cos(yaw)); this is its inverse.
     */
    static double yaw(double dx, double dz) {
        return Math.atan2(-dx, -dz);
    }

    /** The pitch (radians, up is positive) of a direction: the inverse of the same formula. */
    static double pitch(double dx, double dy, double dz) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return length < 1e-12 ? 0 : Math.asin(Math.max(-1, Math.min(1, dy / length)));
    }

    /** The player's right-hand direction for a look direction (world up is +Y; looking along -Z, right is +X). */
    static double[] right(double dx, double dy, double dz) {
        double rx = -dz;
        double rz = dx;
        double length = Math.hypot(rx, rz);
        return length < 1e-9 ? new double[] {1, 0, 0} : new double[] {rx / length, 0, rz / length};
    }

    /** The player's up direction for a look direction: right x forward. */
    static double[] up(double dx, double dy, double dz) {
        double[] r = right(dx, dy, dz);
        return new double[] {r[1] * dz - r[2] * dy, r[2] * dx - r[0] * dz, r[0] * dy - r[1] * dx};
    }

    /** A point given in the player's camera frame: eye + right * r + up * u + forward * f, for a unit look direction. */
    static double[] cameraPoint(double[] eye, double[] dir, double right, double up, double forward) {
        double[] r = right(dir[0], dir[1], dir[2]);
        double[] u = up(dir[0], dir[1], dir[2]);
        return new double[] {
                eye[0] + r[0] * right + u[0] * up + dir[0] * forward,
                eye[1] + r[1] * right + u[1] * up + dir[1] * forward,
                eye[2] + r[2] * right + u[2] * up + dir[2] * forward};
    }
}
