package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything that reads or changes the {@code Arrakis_Power} entity stat lives here: the Cutter Ray drains it
 * through {@link #spend}, the debug commands go through {@link #set} and {@link #fill}, and {@link PowerRechargeSystem}
 * refills it through {@link #recharge} while it is not being used. A later Power Pack item and a HUD bar should extend
 * this class (capacity modifiers, other costs) rather than touch the stat directly.
 *
 * <p>The stat itself is the asset {@code Server/Entity/Stats/Equipment_of_Arrakis/Arrakis_Power.json}. The server gives
 * every living entity every stat asset, set to its InitialValue (100), when the entity's stat map is set up, so a new
 * character and a character from an old save both start full; no code is needed for that.
 */
public final class ArrakisPower {

    public static final String STAT_ID = "Arrakis_Power";
    public static final float MAX = 100f;
    /** Power gained per second while not in use. */
    public static final float RECHARGE_PER_SECOND = 10f;
    /**
     * No spend attempt for this long counts as "not in use". Beam ticks come every 0.1 to 0.2 s while the button is
     * held, so a shorter time would recharge between ticks.
     */
    static final long IDLE_NANOS = 350_000_000L;

    private static final Map<UUID, Long> LAST_USED = new ConcurrentHashMap<>();

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
        // An attempt counts as use even when there is nothing left, so holding the button at 0 power does not recharge.
        PlayerRef player = accessor.getComponent(ref, PlayerRef.getComponentType());
        if (player != null) {
            LAST_USED.put(player.getUuid(), System.nanoTime());
        }
        float have = get(accessor, ref);
        if (have <= 0f) {
            return false;
        }
        set(accessor, ref, have - amount);
        return true;
    }

    /** True when this player has not tried to spend power for {@link #IDLE_NANOS} (or never has since the server started). */
    static boolean idle(UUID player, long now) {
        Long last = LAST_USED.get(player);
        return last == null || now - last > IDLE_NANOS;
    }

    /** Adds {@code seconds} of recharge, up to {@link #MAX}. */
    static void recharge(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, float seconds) {
        float have = get(accessor, ref);
        if (have >= 0f && have < MAX) {
            set(accessor, ref, have + RECHARGE_PER_SECOND * seconds);
        }
    }

    static void forget(UUID player) {
        LAST_USED.remove(player);
    }
}
