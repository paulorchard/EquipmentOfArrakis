package com.paulorchard.islandcraft.equipmentofarrakis.thumper;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/** The "Thumper" section of Equipment_of_Arrakis.json. */
public class ThumperConfig {

    private int maxLivePerPlayer = 3;
    private String[] sandBlocks = {};
    private double beatSeconds = 1.0;
    private double doubleThumpGapSeconds = 0.25;
    private double thumpPitchSemitones = -6.0;
    private double thumpVolumeDb = 3.0;
    private double maxLiveSeconds = 0.0;

    /** Most Thumpers one player may have standing at once; a placement over it is refused. */
    public int maxLivePerPlayer() {
        return Math.max(1, maxLivePerPlayer);
    }

    /** Block ids that count as sand; empty means the set the Worms of Arrakis mod uses (see {@link ThumperRules#resolveSand}). */
    public String[] sandBlocks() {
        return sandBlocks == null ? new String[0] : sandBlocks;
    }

    /** Seconds from the start of one beat to the start of the next. */
    public double beatSeconds() {
        return beatSeconds > 0.05 ? beatSeconds : 1.0;
    }

    /** Seconds between the two hits of a beat; 0 or less makes it a single hit. */
    public double doubleThumpGapSeconds() {
        return doubleThumpGapSeconds;
    }

    /** Pitch of the thump in semitones (the layer's RandomSettings MinPitch and MaxPitch in the sound event). */
    public double thumpPitchSemitones() {
        return thumpPitchSemitones;
    }

    /** Volume of the thump in decibels (the layer's Volume in the sound event). */
    public double thumpVolumeDb() {
        return thumpVolumeDb;
    }

    /** Safety net: a Thumper that stands longer than this many seconds is removed by the engine; 0 or less means never. */
    public double maxLiveSeconds() {
        return Math.max(0, maxLiveSeconds);
    }

    public static final BuilderCodec<ThumperConfig> CODEC = BuilderCodec.builder(ThumperConfig.class, ThumperConfig::new)
            .documentation("Settings of the Thumper module.")
            .append(new KeyedCodec<>("MaxLivePerPlayer", Codec.INTEGER, false), (c, v) -> c.maxLivePerPlayer = v, c -> c.maxLivePerPlayer)
            .documentation("Most Thumpers one player may have standing at once (default 3). A placement over it is refused and the item kept.")
            .add()
            .append(new KeyedCodec<>("SandBlocks", Codec.STRING_ARRAY, false), (c, v) -> c.sandBlocks = v, c -> c.sandBlocks)
            .documentation("Block ids that count as sand for a placement. Empty (default): the set Worms of Arrakis uses (its SandBlocksOverride when it is loaded, else Arrakis_Sand when Dunes of Arrakis is installed, else the four vanilla sands).")
            .add()
            .append(new KeyedCodec<>("BeatSeconds", Codec.DOUBLE, false), (c, v) -> c.beatSeconds = v, c -> c.beatSeconds)
            .documentation("Seconds between the start of one beat and the next (default 1.0).")
            .add()
            .append(new KeyedCodec<>("DoubleThumpGapSeconds", Codec.DOUBLE, false), (c, v) -> c.doubleThumpGapSeconds = v, c -> c.doubleThumpGapSeconds)
            .documentation("Seconds between the two hits of a beat (default 0.25). 0 or less: a single hit per beat.")
            .add()
            .append(new KeyedCodec<>("ThumpPitchSemitones", Codec.DOUBLE, false), (c, v) -> c.thumpPitchSemitones = v, c -> c.thumpPitchSemitones)
            .documentation("Pitch of the thump in semitones relative to the clip (default -6, half an octave down). Written over the sound event at server start.")
            .add()
            .append(new KeyedCodec<>("ThumpVolumeDb", Codec.DOUBLE, false), (c, v) -> c.thumpVolumeDb = v, c -> c.thumpVolumeDb)
            .documentation("Volume of the thump in decibels relative to the clip (default +3). Written over the sound event at server start.")
            .add()
            .append(new KeyedCodec<>("MaxLiveSeconds", Codec.DOUBLE, false), (c, v) -> c.maxLiveSeconds = v, c -> c.maxLiveSeconds)
            .documentation("Safety net: seconds after which a standing Thumper is removed (for example 1800). Default 0: it stands until the worm takes it or /arrakis thumper clear. Read at server start.")
            .add()
            .build();
}
