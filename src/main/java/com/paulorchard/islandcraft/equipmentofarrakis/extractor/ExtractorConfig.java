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
    private boolean extendNonHolding = false;
    private double testKillDelaySeconds = 1.0;
    private String pokeAnimation = "Interact";

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

    /** True: also extend mobs whose death clip does not hold its last keyframe (their bodies stand up again); default false. */
    public boolean extendNonHolding() {
        return extendNonHolding;
    }

    /** Seconds {@code /arrakis blood corpse} waits after spawning before it kills the mob. */
    public double testKillDelaySeconds() {
        return Math.max(0, testKillDelaySeconds);
    }

    /** The item animation of the poke (default Interact, the dry-fire click of the Handgun set; GuardBash is the pistol bash). */
    public String pokeAnimation() {
        return pokeAnimation == null || pokeAnimation.isBlank() ? "Interact" : pokeAnimation;
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
            .append(new KeyedCodec<>("ExtendNonHolding", Codec.BOOLEAN, false), (c, v) -> c.extendNonHolding = v, c -> c.extendNonHolding)
            .documentation("True: also extend mobs whose death clip does not hold its last keyframe (the body stands up again). Default false: they keep the vanilla timer.")
            .add()
            .append(new KeyedCodec<>("TestKillDelaySeconds", Codec.DOUBLE, false), (c, v) -> c.testKillDelaySeconds = v, c -> c.testKillDelaySeconds)
            .documentation("Seconds /arrakis blood corpse waits after spawning before it kills the mob (default 1), so the client has the entity when the death animation is sent.")
            .add()
            .append(new KeyedCodec<>("PokeAnimation", Codec.STRING, false), (c, v) -> c.pokeAnimation = v, c -> c.pokeAnimation)
            .documentation("Item animation of the Blood Extractor poke, from the Handgun set (default Interact; GuardBash is the old pistol bash). Read at server start.")
            .add()
            .build();
}
