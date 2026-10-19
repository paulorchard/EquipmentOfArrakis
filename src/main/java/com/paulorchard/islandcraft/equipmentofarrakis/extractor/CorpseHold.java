package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether a mob's death clip holds its last keyframe (the body stays lying) or not (it stands back up when the clip ends), engine-free: the
 * parser of the one boolean in a {@code .blockyanim} file, the rule over several clips, the list fallback and a cache per model.
 */
final class CorpseHold {

    private static final Pattern HOLD = Pattern.compile("\"holdLastKeyframe\"\\s*:\\s*(true|false)");

    /** What is known about one model: the clip(s) looked at, and whether the body holds its pose. */
    record Verdict(String clips, boolean holds) {
    }

    private CorpseHold() {
    }

    /** The value of {@code holdLastKeyframe} in the text of a clip; null when the key is absent or the text is not what we expect. */
    static Boolean parse(String clipText) {
        if (clipText == null) {
            return null;
        }
        Matcher matcher = HOLD.matcher(clipText);
        return matcher.find() ? Boolean.valueOf(matcher.group(1)) : null;
    }

    /**
     * The rule over the clips of one animation set: the body holds only if every clip is known to hold. A clip whose flag is absent counts
     * as not holding (the engine's default for the key is false); an unreadable clip (null) makes the answer unknown (null).
     */
    static Boolean allHold(List<Boolean> clips) {
        if (clips.isEmpty()) {
            return Boolean.FALSE;
        }
        boolean unknown = false;
        for (Boolean clip : clips) {
            if (clip == null) {
                unknown = true;
            } else if (!clip) {
                return Boolean.FALSE;
            }
        }
        return unknown ? null : Boolean.TRUE;
    }

    /** The fallback: a model on the generated list does not hold, any other model does. */
    static boolean fromList(String modelId, Set<String> nonHolding) {
        return !nonHolding.contains(modelId);
    }

    /** One verdict per key (a model id with the animation id the death system picks), computed once; {@link #firstTime} tells the caller to log. */
    static final class Cache {

        private final Map<String, Verdict> verdicts = new ConcurrentHashMap<>();
        private final Set<String> logged = ConcurrentHashMap.newKeySet();

        Verdict get(String key, Supplier<Verdict> compute) {
            return verdicts.computeIfAbsent(key, k -> compute.get());
        }

        /** True once per key. */
        boolean firstTime(String key) {
            return logged.add(key);
        }

        int size() {
            return verdicts.size();
        }
    }

    /** The one-line INFO message, once per model. */
    static String logLine(String role, String model, Verdict verdict, boolean extended, double seconds) {
        return "corpse: " + role + " model " + model + " death clip " + verdict.clips() + " holds=" + verdict.holds() + " -> "
                + (extended ? "extended " + Math.round(seconds) + " s" : "vanilla timer");
    }
}
