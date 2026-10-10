package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;

import java.util.HashMap;
import java.util.Map;

/**
 * Compares the tool specs of the pickaxe items, so the Cutter Ray's {@code ToolItemId} can be checked against the
 * data instead of trusted. A pickaxe beats another when, for every gather type, its power is at least as high and its
 * quality at least as high; one that is only better in some types is reported as not comparable.
 */
final class StrongestPickaxe {

    private StrongestPickaxe() {
    }

    /** gather type -> {power, quality}. */
    static Map<String, float[]> specs(Item pickaxe) {
        Map<String, float[]> map = new HashMap<>();
        ItemTool tool = pickaxe == null ? null : pickaxe.getTool();
        if (tool != null && tool.getSpecs() != null) {
            for (ItemToolSpec spec : tool.getSpecs()) {
                map.put(spec.getGatherType(), new float[] {spec.getPower(), spec.getQuality()});
            }
        }
        return map;
    }

    /** True when {@code a} is at least as strong as {@code b} in every gather type {@code b} has. */
    static boolean atLeastAsStrong(Map<String, float[]> a, Map<String, float[]> b) {
        for (Map.Entry<String, float[]> entry : b.entrySet()) {
            float[] mine = a.get(entry.getKey());
            if (mine == null || mine[0] < entry.getValue()[0] || mine[1] < entry.getValue()[1]) {
                return false;
            }
        }
        return true;
    }

    /** Names every pickaxe item (an id starting with Tool_Pickaxe_) that {@code candidate} does not dominate. */
    static String notDominated(String candidateId) {
        Item candidate = Item.getAssetMap().getAsset(candidateId);
        if (candidate == null) {
            return "(no such item)";
        }
        Map<String, float[]> best = specs(candidate);
        StringBuilder worse = new StringBuilder();
        for (Map.Entry<String, Item> entry : Item.getAssetMap().getAssetMap().entrySet()) {
            if (entry.getKey().startsWith("Tool_Pickaxe_") && !entry.getKey().equals(candidateId)
                    && !atLeastAsStrong(best, specs(entry.getValue()))) {
                worse.append(' ').append(entry.getKey());
            }
        }
        return worse.length() == 0 ? " none" : worse.toString();
    }
}
