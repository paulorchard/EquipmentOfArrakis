package com.paulorchard.islandcraft.equipmentofarrakis.power;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.logging.Level;

/**
 * The power service. Power is the vanilla Mana stat: every method takes the accessor and the ref of the entity and must be
 * called on the world thread. A missing stat map or stat is logged once and gives safe values (0, false). Changes use the
 * plain EntityStatMap overloads (Predictable.NONE), which queue the stat update for the client, so the Mana bar follows.
 */
public final class Power {

    public static final String STAT_ID = "Mana";

    private static Power instance;

    private final PowerModifiers modifiers = new PowerModifiers(this);
    private boolean warned;

    Power() {
    }

    /** The service; null before the power module is set up. */
    public static Power get() {
        return instance;
    }

    static void install(Power power) {
        instance = power;
    }

    public PowerModifiers modifiers() {
        return modifiers;
    }

    /** The stat index of Mana, or Integer.MIN_VALUE when the stat does not exist in the loaded assets. */
    public static int statIndex() {
        return EntityStatType.getAssetMap().getIndex(STAT_ID);
    }

    // ---- reads

    public float get(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        EntityStatValue value = value(accessor, ref);
        return value == null ? 0f : value.get();
    }

    public float max(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        EntityStatValue value = value(accessor, ref);
        return value == null ? 0f : value.getMax();
    }

    public float fraction(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        return PowerMath.fraction(get(accessor, ref), max(accessor, ref));
    }

    public boolean has(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, float amount) {
        return PowerMath.has(get(accessor, ref), amount);
    }

    public boolean isCreative(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        Player player = accessor.getComponent(ref, Player.getComponentType());
        return player != null && player.getGameMode() == GameMode.Creative;
    }

    // ---- changes

    /** All or nothing: false (and no change) when there is less than {@code amount}. Creative: true, nothing spent. */
    public boolean spend(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, float amount) {
        EntityStatMap map = stats(accessor, ref);
        EntityStatValue value = value(map);
        if (value == null) {
            return amount <= 0f;
        }
        PowerMath.Spend result = PowerMath.spend(value.get(), amount, isCreative(accessor, ref), value.getMax());
        if (result.ok() && result.after() != value.get()) {
            map.setStatValue(value.getIndex(), result.after());
        }
        return result.ok();
    }

    /** Takes what is there, up to {@code amount}, and returns what was taken. Creative takes nothing and returns the full amount. */
    public float spendUpTo(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, float amount) {
        EntityStatMap map = stats(accessor, ref);
        EntityStatValue value = value(map);
        if (value == null) {
            return 0f;
        }
        PowerMath.Take result = PowerMath.spendUpTo(value.get(), amount, isCreative(accessor, ref), value.getMax());
        if (result.after() != value.get()) {
            map.setStatValue(value.getIndex(), result.after());
        }
        return result.taken();
    }

    /** Adds (a negative amount takes), clamped to 0 and the current maximum. Returns the new value. */
    public float add(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, float amount) {
        EntityStatMap map = stats(accessor, ref);
        EntityStatValue value = value(map);
        if (value == null) {
            return 0f;
        }
        float after = PowerMath.add(value.get(), amount, value.getMax());
        map.setStatValue(value.getIndex(), after);
        return after;
    }

    /** Sets the value, clamped to 0 and the current maximum. Returns the new value. */
    public float set(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, float amount) {
        EntityStatMap map = stats(accessor, ref);
        EntityStatValue value = value(map);
        if (value == null) {
            return 0f;
        }
        float after = PowerMath.set(amount, value.getMax());
        map.setStatValue(value.getIndex(), after);
        return after;
    }

    public float fill(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        EntityStatMap map = stats(accessor, ref);
        EntityStatValue value = value(map);
        if (value == null) {
            return 0f;
        }
        map.maximizeStatValue(value.getIndex());
        return value.get();
    }

    // ---- the engine side

    /** The entity's stat map, or null (logged once) when it has none. */
    EntityStatMap stats(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        EntityStatMap map = ref == null || !ref.isValid() ? null : accessor.getComponent(ref, EntityStatMap.getComponentType());
        if (map == null) {
            warnOnce("an entity without an EntityStatMap was asked about power");
        }
        return map;
    }

    EntityStatValue value(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        return value(stats(accessor, ref));
    }

    private EntityStatValue value(EntityStatMap map) {
        if (map == null) {
            return null;
        }
        int index = statIndex();
        EntityStatValue value = index == Integer.MIN_VALUE ? null : map.get(index);
        if (value == null) {
            warnOnce("the " + STAT_ID + " stat is missing (index " + index + ")");
        }
        return value;
    }

    private void warnOnce(String text) {
        if (!warned) {
            warned = true;
            HytaleLogger.getLogger().at(Level.WARNING).log("Power: %s; logged once, values are 0", text);
        }
    }
}
