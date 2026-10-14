package com.paulorchard.islandcraft.equipmentofarrakis.power;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Modifiers on the maximum of Mana, keyed by a string id (callers use their module name, for example "IslandCraft:Stillsuit").
 * The same id again replaces. They are not saved: callers register them again when the item is equipped or the player joins.
 *
 * <p>ADDITIVE adds the amount to the maximum; MULTIPLICATIVE multiplies it (1.5 is one and a half times). The engine's own
 * recalculation (armor, held items, effects) only removes and puts modifiers under its own keys, so ours are left alone.
 */
public final class PowerModifiers {

    public enum Kind {
        ADDITIVE(StaticModifier.CalculationType.ADDITIVE),
        MULTIPLICATIVE(StaticModifier.CalculationType.MULTIPLICATIVE);

        final StaticModifier.CalculationType type;

        Kind(StaticModifier.CalculationType type) {
            this.type = type;
        }
    }

    /** A modifier as listed: its key, which end it changes, how, and the amount. */
    public record Listed(String id, String target, String kind, float amount) {
    }

    private final Power power;

    PowerModifiers(Power power) {
        this.power = power;
    }

    public void putMax(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, String id, Kind kind, float amount) {
        EntityStatMap map = power.stats(accessor, ref);
        EntityStatValue value = power.value(accessor, ref);
        if (map != null && value != null) {
            map.putModifier(value.getIndex(), id, new StaticModifier(Modifier.ModifierTarget.MAX, kind.type, amount));
        }
    }

    /** Removes the modifier; true when there was one. */
    public boolean removeMax(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, String id) {
        EntityStatMap map = power.stats(accessor, ref);
        EntityStatValue value = power.value(accessor, ref);
        return map != null && value != null && map.removeModifier(value.getIndex(), id) != null;
    }

    /** Every modifier on Mana, the engine's own (Armor..., Effect...) included, in no particular order. */
    public List<Listed> list(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        List<Listed> out = new ArrayList<>();
        EntityStatValue value = power.value(accessor, ref);
        if (value != null && value.getModifiers() != null) {
            for (Map.Entry<String, Modifier> entry : value.getModifiers().entrySet()) {
                Modifier modifier = entry.getValue();
                if (modifier instanceof StaticModifier fixed) {
                    out.add(new Listed(entry.getKey(), fixed.getTarget().name(), fixed.getCalculationType().name(), fixed.getAmount()));
                } else {
                    out.add(new Listed(entry.getKey(), modifier.getTarget().name(), modifier.getClass().getSimpleName(), Float.NaN));
                }
            }
        }
        return out;
    }
}
