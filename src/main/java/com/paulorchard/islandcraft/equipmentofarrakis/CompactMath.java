package com.paulorchard.islandcraft.equipmentofarrakis;

import java.util.List;

/** The rules of the Compactor that need no engine classes: which blocks it works on, and the cycle of progress. */
final class CompactMath {

    private CompactMath() {
    }

    /**
     * True when a block of this group and id can be compacted: its group is one of {@code groups} (compared exactly, case
     * included), its id is not in {@code excludedBlocks} and does not start with any of {@code excludedPrefixes}.
     */
    static boolean allowed(String group, String id, List<String> groups, List<String> excludedBlocks,
            List<String> excludedPrefixes) {
        if (group == null || id == null || !groups.contains(group) || excludedBlocks.contains(id)) {
            return false;
        }
        for (String prefix : excludedPrefixes) {
            if (!prefix.isEmpty() && id.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    /** The health the block is shown at for a cycle {@code fraction} (0 to 1) done: full at 0, {@code 1 - maxCrack} at the end. */
    static float healthAt(double fraction, double maxCrack) {
        double f = Math.max(0, Math.min(1, fraction));
        return (float) (1.0 - maxCrack * f);
    }

    /** The largest crack share the settings may ask for: a block never gets below 0.1 of its health from the Compactor. */
    static double clampMaxCrack(double maxCrack) {
        return Math.max(0.0, Math.min(0.9, maxCrack));
    }

    /**
     * The progress of one compacting cycle. {@link #advance} adds time, at most {@code maxStep} seconds at a time (so a lag spike
     * cannot finish a cycle at once or more than one), and returns true when the cycle is complete; progress is then back at 0.
     */
    static final class Cycle {
        private final double seconds;
        private final double maxStep;
        private double progress;

        Cycle(double seconds, double maxStep) {
            this.seconds = Math.max(0.05, seconds);
            this.maxStep = maxStep;
        }

        boolean advance(double dt) {
            progress += Math.max(0, Math.min(dt, maxStep));
            if (progress >= seconds) {
                progress = 0;
                return true;
            }
            return false;
        }

        double fraction() {
            return progress / seconds;
        }

        void reset() {
            progress = 0;
        }
    }
}
