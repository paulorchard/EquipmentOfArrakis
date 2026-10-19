package com.paulorchard.islandcraft.equipmentofarrakis.thumper;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** The placement rules of the Thumper, with no engine class in them. */
final class ThumperRules {

    /** The vanilla sand blocks, which Worms of Arrakis uses when Dunes of Arrakis is not installed. */
    static final String[] VANILLA_SAND = {"Soil_Sand", "Soil_Sand_Ashen", "Soil_Sand_Red", "Soil_Sand_White"};
    static final String DUNES_SAND = "Arrakis_Sand";

    enum Refusal { NONE, NOT_SAND, LIMIT, NO_ROOM }

    private ThumperRules() {
    }

    /**
     * The engine keeps a sound event Volume (decibels in the JSON) as a gain and a Pitch (semitones in the JSON) as a playback-rate ratio:
     * the self-check of the first boot showed 3 dB as 1.4125 and -6 semitones as 0.7071. These are the same conversions, for writing the
     * config values over the loaded event.
     */
    static float dbToGain(double db) {
        return (float) Math.pow(10.0, db / 20.0);
    }

    static float semitonesToRatio(double semitones) {
        return (float) Math.pow(2.0, semitones / 12.0);
    }

    static double gainToDb(double gain) {
        return 20.0 * Math.log10(gain);
    }

    static double ratioToSemitones(double ratio) {
        return 12.0 * Math.log(ratio) / Math.log(2.0);
    }

    /** Why a placement is refused, in this order, or NONE. */
    static Refusal refusal(boolean onSand, int live, int max, boolean room) {
        if (!onSand) {
            return Refusal.NOT_SAND;
        }
        if (live >= max) {
            return Refusal.LIMIT;
        }
        return room ? Refusal.NONE : Refusal.NO_ROOM;
    }

    /**
     * The block ids that count as sand, the way Worms of Arrakis works them out: a non-empty list in the config is used as written;
     * otherwise the ids Worms itself reports ({@code fromWorms}, null when Worms is not loaded or does not tell); otherwise Arrakis_Sand
     * when Dunes of Arrakis is installed, else the four vanilla sands.
     */
    static Set<String> resolveSand(String[] configured, Set<String> fromWorms, boolean dunesInstalled) {
        if (configured != null && configured.length > 0) {
            return new LinkedHashSet<>(Arrays.asList(configured));
        }
        if (fromWorms != null && !fromWorms.isEmpty()) {
            return new LinkedHashSet<>(fromWorms);
        }
        return new LinkedHashSet<>(Arrays.asList(dunesInstalled ? new String[] {DUNES_SAND} : VANILLA_SAND));
    }

    /**
     * Sand under the feet, the test Worms uses for a totem: the first block that is not air, looking down from the block under the feet
     * for {@code column.length} blocks, is a sand block. {@code column} is the block ids from the block under the feet downwards, null or
     * empty for air.
     */
    static boolean onSand(String[] column, Set<String> sand) {
        for (String id : column) {
            if (id != null && !id.isEmpty()) {
                return sand.contains(id);
            }
        }
        return false;
    }
}
