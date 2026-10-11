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
import java.util.function.Supplier;

/**
 * Everything that reads or changes the {@code Arrakis_Ammo} entity stat lives here, the way {@link ArrakisPower} holds the power stat:
 * the spring guns take a shot through {@link #spend}, the debug commands go through {@link #set} and {@link #fill}, and
 * {@link AmmoRechargeSystem} refills it through {@link #recharge} while no shot is being tried. Real ammunition (clips, reloading) will
 * replace this class, so nothing else touches the stat.
 *
 * <p>The stat is the asset {@code Server/Entity/Stats/Equipment_of_Arrakis/Arrakis_Ammo.json}: every living entity gets it, set to 100,
 * when its stat map is set up, so a new character and one from an old save both start full. The rate and the wait are in
 * {@link DartSettings} ({@code Dart_Guns.json}), as 10 a second and 0.35 s, the same as the power.
 */
public final class ArrakisAmmo {

    public static final String STAT_ID = "Arrakis_Ammo";
    public static final float MAX = AmmoMath.MAX;

    private static final Map<UUID, Long> LAST_USED = new ConcurrentHashMap<>();
    private static volatile Supplier<DartSettings> settings = DartSettings::new;

    private ArrakisAmmo() {
    }

    /** Where the rate and the wait are read from; set once by the plugin. */
    static void bind(Supplier<DartSettings> source) {
        settings = source == null ? DartSettings::new : source;
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

    /** Current ammo, or -1 when the entity has no such stat. */
    public static float get(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        EntityStatValue value = value(accessor, ref);
        return value == null ? -1f : value.get();
    }

    /** Sets ammo, clamped to 0..MAX. Returns the new value, or -1 when the entity has no such stat. */
    public static float set(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, float amount) {
        EntityStatValue value = value(accessor, ref);
        if (value == null) {
            return -1f;
        }
        EntityStatMap stats = accessor.getComponent(ref, EntityStatMap.getComponentType());
        return stats.setStatValue(statIndex(), AmmoMath.clamp(amount));
    }

    public static float fill(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        return set(accessor, ref, MAX);
    }

    /**
     * Takes {@code amount} ammo for a shot. Returns false, and takes nothing, when there is less than that (or no stat). The attempt counts
     * as use either way, so clicking at 0 ammo does not recharge it, as for power.
     */
    public static boolean spend(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, float amount) {
        PlayerRef player = accessor.getComponent(ref, PlayerRef.getComponentType());
        if (player != null) {
            LAST_USED.put(player.getUuid(), System.nanoTime());
        }
        float have = get(accessor, ref);
        if (have < 0f) {
            return false;
        }
        AmmoMath.Payment payment = AmmoMath.spend(have, amount);
        if (payment.paid()) {
            set(accessor, ref, payment.after());
        }
        return payment.paid();
    }

    /** True when this player has not tried a shot for the idle wait (or never has since the server started). */
    static boolean idle(UUID player, long now) {
        return AmmoMath.idle(LAST_USED.get(player), now, (long) (settings.get().getAmmoIdleSeconds() * 1_000_000_000L));
    }

    /** Adds {@code seconds} of recharge at the configured rate, up to {@link #MAX}. */
    static void recharge(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, float seconds) {
        float have = get(accessor, ref);
        if (have >= 0f && have < MAX) {
            set(accessor, ref, AmmoMath.recharged(have, (float) settings.get().getAmmoRechargePerSecond(), seconds));
        }
    }

    static void forget(UUID player) {
        LAST_USED.remove(player);
    }
}
