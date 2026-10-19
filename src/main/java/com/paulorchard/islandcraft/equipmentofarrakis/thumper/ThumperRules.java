package com.paulorchard.islandcraft.equipmentofarrakis.thumper;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** The placement rules of the Thumper, with no engine class in them. */
final class ThumperRules {

    /** The vanilla sand blocks, which Worms of Arrakis uses when Dunes of Arrakis is not installed. */
    static final String[] VANILLA_SAND = {"Soil_Sand", "Soil_Sand_Ashen", "Soil_Sand_Red", "Soil_Sand_White"};
    static final String DUNES_SAND = "Arrakis_Sand";

    enum Refusal { NONE, NOT_CROUCHING, LIMIT, NO_ROOM, NOT_SAND, OCCUPIED }

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

    /**
     * Why a placement is refused, in this order, or NONE: not crouching; the player already has the most they may; no ground or no room in
     * the chosen column; the ground there is not sand (Worms' rule); another Thumper already stands on the spot.
     */
    static Refusal refusal(boolean crouched, int live, int max, boolean room, boolean onSand, boolean occupied) {
        if (!crouched) {
            return Refusal.NOT_CROUCHING;
        }
        if (live >= max) {
            return Refusal.LIMIT;
        }
        if (!room) {
            return Refusal.NO_ROOM;
        }
        if (!onSand) {
            return Refusal.NOT_SAND;
        }
        return occupied ? Refusal.OCCUPIED : Refusal.NONE;
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

}
