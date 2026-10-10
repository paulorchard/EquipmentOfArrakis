package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Everything that reads or changes the {@code Arrakis_Power} entity stat lives here: the Cutter Ray drains it
 * through {@link #spend}, the debug commands go through {@link #set} and {@link #fill}. A later Power Pack item and a
 * HUD bar should extend this class (recharge, capacity modifiers) rather than touch the stat directly.
 *
 * <p>The stat itself is the asset {@code Server/Entity/Stats/Equipment_of_Arrakis/Arrakis_Power.json}. The server gives
 * every living entity every stat asset, set to its InitialValue (100), when the entity's stat map is set up, so a new
 * character and a character from an old save both start full; no code is needed for that.
 */
public final class ArrakisPower {

    public static final String STAT_ID = "Arrakis_Power";
    public static final float MAX = 100f;

    private ArrakisPower() {
    }

    /** The stat's index, or {@link Integer#MIN_VALUE} when the asset pack did not load. */
    public static int statIndex() {
        return EntityStatType.getAssetMap().getIndex(STAT_ID);
    }

    private static EntityStatValue value(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        int index = statIndex();
        if (ref == null || index == Integer.MIN_VALUE) {
            return null;
        }
        EntityStatMap stats = accessor.getComponent(ref, EntityStatMap.getComponentType());
        return stats == null ? null : stats.get(index);
    }

    /** Current power, or -1 when the entity has no such stat. */
    public static float get(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        EntityStatValue value = value(accessor, ref);
        return value == null ? -1f : value.get();
    }

    /** Sets power, clamped to 0..MAX. Returns the new value, or -1 when the entity has no such stat. */
    public static float set(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, float amount) {
        EntityStatValue value = value(accessor, ref);
        if (value == null) {
            return -1f;
        }
        EntityStatMap stats = accessor.getComponent(ref, EntityStatMap.getComponentType());
        return stats.setStatValue(statIndex(), Math.max(0f, Math.min(MAX, amount)));
    }

    public static float fill(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        return set(accessor, ref, MAX);
    }

    /**
     * Takes {@code amount} power. Returns false, and takes nothing, when there is none left; a payment that is larger
     * than what is left takes the rest and still succeeds, so the last tick of a beam is not lost.
     */
    public static boolean spend(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, float amount) {
        float have = get(accessor, ref);
        if (have <= 0f) {
            return false;
        }
        set(accessor, ref, have - amount);
        return true;
    }
}
