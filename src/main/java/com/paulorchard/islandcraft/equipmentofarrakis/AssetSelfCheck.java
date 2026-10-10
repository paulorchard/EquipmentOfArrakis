package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ItemAnimation;
import com.hypixel.hytale.protocol.PrioritySlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the loaded assets back and says what the game made of them, so a typo in a JSON file shows up in the log at
 * start instead of as a missing animation in the world. Pure reporting: nothing here changes any state.
 */
final class AssetSelfCheck {

    private AssetSelfCheck() {
    }

    /** One entry per tool: item id, the animation key its secondary uses, and the interaction that plays it. */
    private static final String[][] OFF_HAND = {
            {"Arrakis_Blood_Extractor", "Arrakis_Hold_Out", "Arrakis_Blood_Extractor_Hold"},
            {"Arrakis_Dew_Reaper", "Arrakis_Slow_Swing", "Arrakis_Dew_Reaper_Swing"},
            {"Arrakis_Compactor", "Arrakis_Aim", "Arrakis_Compactor_Aim"},
    };

    /** Returns the lines to log; lines starting with "PROBLEM" are errors. */
    static List<String> run() {
        List<String> lines = new ArrayList<>();
        for (String[] tool : OFF_HAND) {
            Item item = Item.getAssetMap().getAsset(tool[0]);
            if (item == null) {
                lines.add("PROBLEM no item " + tool[0]);
                continue;
            }
            lines.add(String.format("%s: usable=%s compatible=%s scale=%.4f model=%s animations=%s light=%s secondary=%s",
                    tool[0], item.getUtility() != null && item.getUtility().isUsable(),
                    item.getUtility() != null && item.getUtility().isCompatible(), item.getScale(), item.getModel(),
                    item.getPlayerAnimationsId(), item.getLight(), item.getInteractions().get(InteractionType.Secondary)));
            if (item.getLight() != null) {
                lines.add("PROBLEM " + tool[0] + " has a light");
            }
            ItemPlayerAnimations set = ItemPlayerAnimations.getAssetMap().getAsset(item.getPlayerAnimationsId());
            if (set == null) {
                lines.add("PROBLEM no animation set " + item.getPlayerAnimationsId());
            } else {
                ItemAnimation animation = set.getAnimations().get(tool[1]);
                lines.add(String.format("  animation set %s: %d animations, %s=%s, Idle=%s, camera pitch=%s",
                        set.getId(), set.getAnimations().size(), tool[1],
                        animation == null ? "MISSING" : "speed " + animation.speed + " " + animation.thirdPerson,
                        set.getAnimations().containsKey("Idle"), set.getCamera() == null ? "none"
                                : String.valueOf(set.getCamera().getPitch())));
                if (animation == null) {
                    lines.add("PROBLEM no animation " + tool[1]);
                }
            }
            Interaction interaction = Interaction.getAssetMap().getAsset(tool[2]);
            lines.add("  interaction " + tool[2] + ": " + (interaction == null ? "MISSING"
                    : interaction.getClass().getSimpleName() + " runTime=" + interaction.getRunTime()));
            if (interaction == null) {
                lines.add("PROBLEM no interaction " + tool[2]);
            }
            String root = item.getInteractions().get(InteractionType.Secondary);
            if (root == null || RootInteraction.getAssetMap().getAsset(root) == null) {
                lines.add("PROBLEM no root interaction " + root);
            }
            lines.add("  secondary priority main hand/off hand: "
                    + item.getInteractionConfig().getPriorityFor(InteractionType.Secondary, PrioritySlot.MainHand) + "/"
                    + item.getInteractionConfig().getPriorityFor(InteractionType.Secondary, PrioritySlot.OffHand));
        }

        Item ray = Item.getAssetMap().getAsset("Arrakis_Cutter_Ray");
        if (ray != null) {
            lines.add(String.format("Arrakis_Cutter_Ray: usable=%s compatible=%s primary=%s secondary=%s animations=%s "
                            + "priorities primary %s secondary %s",
                    ray.getUtility() != null && ray.getUtility().isUsable(),
                    ray.getUtility() != null && ray.getUtility().isCompatible(),
                    ray.getInteractions().get(InteractionType.Primary), ray.getInteractions().get(InteractionType.Secondary),
                    ray.getPlayerAnimationsId(),
                    ray.getInteractionConfig().getPriorityFor(InteractionType.Primary, PrioritySlot.MainHand),
                    ray.getInteractionConfig().getPriorityFor(InteractionType.Secondary, PrioritySlot.MainHand)));
            Interaction beam = Interaction.getAssetMap().getAsset("Arrakis_Cutter_Beam");
            lines.add("  beam interaction: " + (beam == null ? "MISSING" : beam.getClass().getSimpleName()
                    + " runTime=" + beam.getRunTime()));
            if (!(beam instanceof CutterBeamInteraction)) {
                lines.add("PROBLEM Arrakis_Cutter_Beam is not a CutterBeamInteraction");
            }
            if (ItemPlayerAnimations.getAssetMap().getAsset(ray.getPlayerAnimationsId()) == null) {
                lines.add("PROBLEM no animation set " + ray.getPlayerAnimationsId());
            }
        }
        for (String particle : new String[] {"Arrakis_Cutter_Beam", "Laser_Impact"}) {
            lines.add("particle system " + particle + ": "
                    + (ParticleSystem.getAssetMap().getAsset(particle) == null ? "PROBLEM MISSING" : "ok"));
        }
        return lines;
    }
}
