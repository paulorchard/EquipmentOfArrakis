package com.paulorchard.islandcraft.equipmentofarrakis;

import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;

/** The geometry of the Cutter Ray, kept free of engine classes so it can be tested on its own. */
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

    /** Distance along the ray to the face of the unit block at {@code block} that it enters through. */
    static double entryDistance(Vector3d origin, Vector3d dir, Vector3i block) {
        return entry(origin, dir, block).distance();
    }

    /** The point {@code clearance} out from the middle of a face, along its normal. */
    static Vector3d offFace(Entry entry, double clearance) {
        return new Vector3d(entry.faceCenter()).fma(clearance, new Vector3d(entry.normal().x, entry.normal().y,
                entry.normal().z));
    }

    /**
     * Where the barrel of a rifle held at the shoulder is, relative to the eye: {@code forward} along the look
     * direction, {@code right} to the player's right and {@code down} below the eye, all measured in the player's own
     * view frame (so it follows the pitch).
     */
    static Vector3d hand(Vector3d eye, Vector3d dir, double forward, double right, double down) {
        Vector3d look = new Vector3d(dir).normalize();
        Vector3d side = new Vector3d(look).cross(0, 1, 0);
        if (side.lengthSquared() < 1e-9) {
            // Looking straight up or down: any horizontal direction will do, so the hand does not flip about.
            side.set(1, 0, 0);
        }
        side.normalize();
        Vector3d up = new Vector3d(side).cross(look).normalize();
        return new Vector3d(eye).fma(forward, look).fma(right, side).fma(-down, up);
    }

    /** One piece of the tapering line: where its middle is, how wide it is and how long. */
    record Piece(Vector3d center, double width, double length) {
    }

    /**
     * Lays out a line from {@code start} to {@code end} that is {@code widthStart} wide at the start and
     * {@code widthEnd} at the end, as pieces whose length is always {@code lengthPerWidth} times their width (an
     * entity can only be scaled evenly, so a piece that is thinner is also shorter). Each piece starts
     * {@code advance} (below 1, so that they overlap) of a length after the one before, and the last is cut short to end
     * exactly at {@code end}. Empty when the line is shorter than {@code minimum}; never more than {@code max} pieces.
     */
    static List<Piece> pieces(Vector3d start, Vector3d end, double widthStart, double widthEnd, double lengthPerWidth,
                              double advance, double minimum, int max) {
        List<Piece> pieces = new ArrayList<>();
        double total = start.distance(end);
        if (total < minimum) {
            return pieces;
        }
        Vector3d dir = new Vector3d(end).sub(start).div(total);
        double at = 0;
        while (pieces.size() < max) {
            double width = widthStart + (widthEnd - widthStart) * Math.min(1, at / total);
            double length = lengthPerWidth * width;
            double next = at + advance * length;
            if (at + length >= total || pieces.size() == max - 1) {
                length = total - at;
                width = length / lengthPerWidth;
                next = total;
            }
            if (length > 1e-6) {
                pieces.add(new Piece(new Vector3d(start).fma(at + length / 2, dir), width, length));
            }
            if (next >= total) {
                break;
            }
            at = next;
        }
        return pieces;
    }
}
