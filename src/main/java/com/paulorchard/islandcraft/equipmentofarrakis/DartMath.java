package com.paulorchard.islandcraft.equipmentofarrakis;

/** The range rule of the spring guns' darts, without engine classes so that it can be tested on its own. */
final class DartMath {

    private DartMath() {
    }

    /** The straight-line distance between two points. */
    static double distance(double[] from, double[] to) {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double dz = to[2] - from[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The distance from a point to the nearest point of an axis-aligned box (0 when the point is inside it). */
    static double distanceToBox(double[] point, double[] min, double[] max) {
        double sum = 0;
        for (int i = 0; i < 3; i++) {
            double d = point[i] < min[i] ? min[i] - point[i] : point[i] > max[i] ? point[i] - max[i] : 0;
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    /** True when {@code at} is further from the launch point than {@code range} blocks (exactly the range is still in range). */
    static boolean beyond(double[] launch, double[] at, double range) {
        return distance(launch, at) > range;
    }
}
