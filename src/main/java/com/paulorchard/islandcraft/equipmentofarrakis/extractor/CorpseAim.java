package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import org.joml.Vector3d;

import java.util.List;

/**
 * The Blood Extractor aim and entity rules, engine-free. A corpse is aimed at when the look ray passes within {@code radius} of its
 * aim point (the middle of the body) and the point is at most {@code range} along the ray; the nearest such corpse along the ray wins.
 * This replaces the engine entity targeting because a dead entity may no longer have a hitbox the engine ray accepts.
 */
final class CorpseAim {

    private CorpseAim() {
    }

    /** A candidate: any id the caller likes (an index) and the aim point of the body. */
    record Candidate(int id, Vector3d aimPoint) {
    }

    /** The nearest candidate the ray hits, or -1. {@code dir} need not be normalised. */
    static int pick(Vector3d origin, Vector3d dir, List<Candidate> candidates, double range, double radius) {
        double length = dir.length();
        if (length < 1e-9) {
            return -1;
        }
        Vector3d d = new Vector3d(dir).div(length);
        int best = -1;
        double bestT = Double.MAX_VALUE;
        for (Candidate candidate : candidates) {
            Vector3d to = new Vector3d(candidate.aimPoint()).sub(origin);
            double t = to.dot(d);
            if (t < 0 || t > range) {
                continue;
            }
            double sideways = to.sub(new Vector3d(d).mul(t)).length();
            if (sideways <= radius && t < bestT) {
                bestT = t;
                best = candidate.id();
            }
        }
        return best;
    }

    /** What counts as a corpse: dead (has a death component), not a player, and (when required) a non-player character. */
    static boolean isCorpse(boolean isPlayer, boolean isDead, boolean isNpc, boolean requireNpc) {
        return !isPlayer && isDead && (!requireNpc || isNpc);
    }
}
