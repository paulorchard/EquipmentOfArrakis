package com.paulorchard.islandcraft.equipmentofarrakis.thumper;

/**
 * The timing of one Thumper's beat: a hit at the start of every beat and, when the gap is positive, a second hit {@code gap} seconds
 * later (a soft double thump, then a rest). The first beat starts at the first {@link #advance} (the placement). No engine class.
 */
final class BeatClock {

    private final double beat;
    private final double gap;
    private double time;
    private boolean firstFired;
    private boolean secondFired;

    BeatClock(double beat, double gap) {
        this.beat = beat;
        this.gap = gap > 0 && gap < beat ? gap : 0;
    }

    /** Seconds into the current beat (the thump state command shows it). */
    double phase() {
        return time;
    }

    /**
     * Moves the clock on by {@code dt} seconds and says how many hits are due now: 0, 1 or 2. A long pause (a lag spike, a chunk that
     * was not ticking) gives at most one beat's worth, never a burst.
     */
    int advance(double dt) {
        time += Math.max(0, dt);
        int hits = 0;
        if (time >= beat) {
            // finish the beat that was running, then start the next one
            if (!firstFired) {
                hits++;
            }
            if (gap > 0 && !secondFired) {
                hits++;
            }
            time = time % beat;
            firstFired = false;
            secondFired = false;
        }
        if (!firstFired && time >= 0) {
            firstFired = true;
            hits++;
        }
        if (gap > 0 && !secondFired && time >= gap) {
            secondFired = true;
            hits++;
        }
        return Math.min(hits, gap > 0 ? 2 : 1);
    }
}
