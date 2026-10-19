package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Finds the death clip the engine would play for a model and reads its {@code holdLastKeyframe} from the common asset registry (the bytes
 * of every loaded asset, vanilla included). The route: {@code Entity.DefaultAnimations.getDeathAnimationIds(states, cause)} gives the ids
 * the death system tries; {@code Model.getFirstBoundAnimationId} picks the first the model defines (the clip the death system plays); the model's
 * animation set under that id lists the clips, and the body holds only if every clip holds. When the bytes cannot be read the generated list
 * {@code corpses_nonholding_models.txt} decides by model id.
 */
final class DeathClips {

    /** The generated fallback list, see tools/assets/corpses/make_hold_list.js. */
    static final String LIST_RESOURCE = "corpses_nonholding_models.txt";

    private static final CorpseHold.Cache CACHE = new CorpseHold.Cache();
    private static final Map<String, Optional<Boolean>> CLIPS = new ConcurrentHashMap<>();
    private static volatile Set<String> nonHolding;

    private DeathClips() {
    }

    static CorpseHold.Cache cache() {
        return CACHE;
    }

    /** The id the death system plays for this model, movement state and cause; null when the model has none of them bound. */
    static String deathAnimationId(Model model, MovementStates states, DamageCause cause) {
        return model.getFirstBoundAnimationId(Entity.DefaultAnimations.getDeathAnimationIds(states, cause));
    }

    /** The verdict for a model (cached per model id and animation id). */
    static CorpseHold.Verdict verdict(Model model, MovementStates states, DamageCause cause) {
        String id = deathAnimationId(model, states, cause);
        String modelId = model.getModelAssetId();
        return CACHE.get(modelId + "/" + id, () -> compute(modelId, model, id));
    }

    /** The verdict of a model asset by its death set, for the self-check (no entity involved). */
    static CorpseHold.Verdict verdictOfSet(String modelId, ModelAsset.AnimationSet set) {
        return compute(modelId, set, "Death");
    }

    private static CorpseHold.Verdict compute(String modelId, Model model, String animationId) {
        if (animationId == null) {
            return new CorpseHold.Verdict("(no death animation bound)", false);
        }
        ModelAsset.AnimationSet set = model.getAnimationSetMap().get(animationId);
        return set == null ? new CorpseHold.Verdict("(" + animationId + " not in the model)", false) : compute(modelId, set, animationId);
    }

    private static CorpseHold.Verdict compute(String modelId, ModelAsset.AnimationSet set, String animationId) {
        List<Boolean> flags = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        ModelAsset.Animation[] animations = set.getAnimations();
        if (animations != null) {
            for (ModelAsset.Animation animation : animations) {
                paths.add(animation.getAnimation());
                flags.add(readHold(animation.getAnimation()));
            }
        }
        Boolean all = CorpseHold.allHold(flags);
        String clips = paths.isEmpty() ? "(" + animationId + " has no clip)" : String.join(",", paths);
        if (all == null) {
            return new CorpseHold.Verdict(clips + " [bytes unreadable: list]", CorpseHold.fromList(modelId, nonHoldingList()));
        }
        return new CorpseHold.Verdict(clips, all);
    }

    /** holdLastKeyframe of one clip by its asset path ("NPC/.../Death.blockyanim"), null when the bytes cannot be read. */
    static Boolean readHold(String path) {
        if (path == null) {
            return null;
        }
        return CLIPS.computeIfAbsent(path, p -> Optional.ofNullable(CorpseHold.parse(read(p)))).orElse(null);
    }

    private static String read(String path) {
        for (String name : new String[] {"Common/" + path, path}) {
            try {
                CommonAsset asset = CommonAssetRegistry.getByName(name);
                if (asset != null) {
                    byte[] bytes = asset.getBlob().get(5, TimeUnit.SECONDS);
                    if (bytes != null && bytes.length > 0) {
                        return new String(bytes, StandardCharsets.UTF_8);
                    }
                }
            } catch (Exception e) {
                // try the next spelling, then give up
            }
        }
        return null;
    }

    static Set<String> nonHoldingList() {
        Set<String> list = nonHolding;
        if (list == null) {
            list = new HashSet<>();
            try (InputStream in = DeathClips.class.getClassLoader().getResourceAsStream(LIST_RESOURCE)) {
                if (in != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    for (String line; (line = reader.readLine()) != null; ) {
                        if (!line.isBlank() && !line.startsWith("#")) {
                            list.add(line.trim());
                        }
                    }
                }
            } catch (Exception e) {
                // an empty list: every model then counts as holding
            }
            nonHolding = list;
        }
        return list;
    }
}
