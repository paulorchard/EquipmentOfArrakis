package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** The "Extractor" section of Equipment_of_Arrakis.json: how long the bodies of killed mobs stay. */
public class ExtractorConfig {

    private double corpseSeconds = 120.0;
    private double extractedSeconds = 1.0;
    private int maxCorpsesPerChunk = 5;
    private String[] excludedRoles = {};

    /** Seconds a killed non-player character's body stays; 0 or less leaves the vanilla timer alone. */
    public double corpseSeconds() {
        return corpseSeconds;
    }

    /** Seconds a body still has after a blood draw, or after the per-chunk cap cut it. */
    public double extractedSeconds() {
        return Math.max(0, extractedSeconds);
    }

    /** Most extended bodies one chunk column keeps (the newest); 0 or less is no limit. */
    public int maxCorpsesPerChunk() {
        return maxCorpsesPerChunk;
    }

    /** Role names (lower case) whose bodies keep the vanilla timer. */
    public Set<String> excludedRoles() {
        return excludedRoles == null ? Set.of() : Arrays.stream(excludedRoles).map(CorpseRules::lower).collect(Collectors.toSet());
    }

    public static final BuilderCodec<ExtractorConfig> CODEC = BuilderCodec.builder(ExtractorConfig.class, ExtractorConfig::new)
            .documentation("Settings of the Blood Extractor module: how long the bodies of killed mobs stay on the ground.")
            .append(new KeyedCodec<>("CorpseSeconds", Codec.DOUBLE, false), (c, v) -> c.corpseSeconds = v, c -> c.corpseSeconds)
            .documentation("Seconds the body of a killed non-player character stays (default 120). 0 or less: vanilla timer, nothing extended.")
            .add()
            .append(new KeyedCodec<>("ExtractedSeconds", Codec.DOUBLE, false), (c, v) -> c.extractedSeconds = v, c -> c.extractedSeconds)
            .documentation("Seconds a body stays after the Blood Extractor drew its blood, or after the per-chunk limit cut it (default 1).")
            .add()
            .append(new KeyedCodec<>("MaxCorpsesPerChunk", Codec.INTEGER, false), (c, v) -> c.maxCorpsesPerChunk = v, c -> c.maxCorpsesPerChunk)
            .documentation("Most extended bodies one chunk keeps; older ones get the quick removal (default 5, 0 = no limit). Bodies on a chunk border can add up in both chunks.")
            .add()
            .append(new KeyedCodec<>("ExcludedRoles", Codec.STRING_ARRAY, false), (c, v) -> c.excludedRoles = v, c -> c.excludedRoles)
            .documentation("Role names whose bodies keep the vanilla quick removal (default none).")
            .add()
            .build();
}
