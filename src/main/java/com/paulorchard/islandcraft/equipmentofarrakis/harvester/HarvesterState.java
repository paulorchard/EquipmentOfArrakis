package com.paulorchard.islandcraft.equipmentofarrakis.harvester;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the Dew Harvester remembers between the start of a swing (the gate) and its end (the finish), and the hum effect. A swing that
 * is cut short never reaches its finish: its memory is replaced by the next accepted swing and its hum lapses by itself after the
 * effect Duration.
 */
final class HarvesterState {

    /** An accepted click: the Primrose it was for, the water it will move and the hum to take off at the end. */
    record Pending(Vector3i primrose, String primroseBlockId, int waterPerSwing, String humEffectId) {
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private HarvesterState() {
    }

    static void put(UUID player, Pending pending) {
        PENDING.put(player, pending);
    }

    /** Takes the memory of this player swing, or null when there is none. */
    static Pending take(UUID player) {
        return PENDING.remove(player);
    }

    static void forget(UUID player) {
        PENDING.remove(player);
    }

    /** Applies the hum effect (for the length of its Duration). */
    static void startHum(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, String effectId) {
        EffectControllerComponent controller = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
        if (controller != null && effect != null) {
            controller.addEffect(ref, effect, accessor);
        }
    }

    /** Takes the hum effect off at once, which stops the loop. */
    static void stopHum(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, String effectId) {
        EffectControllerComponent controller = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        int index = EntityEffect.getAssetMap().getIndex(effectId);
        if (controller != null && index >= 0) {
            controller.removeEffect(ref, index, accessor);
        }
    }
}
