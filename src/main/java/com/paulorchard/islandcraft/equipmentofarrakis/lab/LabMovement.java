// EXPERIMENT: delete with the lab module
package com.paulorchard.islandcraft.equipmentofarrakis.lab;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.FlyMode;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.protocol.packets.player.SetMovementStates;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Changing a player's movement settings and putting them back. Every change goes through here so that reset undoes all of it. */
final class LabMovement {

    private LabMovement() {
    }

    static MovementManager manager(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        return accessor.getComponent(ref, MovementManager.getComponentType());
    }

    /** Sends the manager's settings to the client (the UpdateMovementSettings packet). */
    static void push(PlayerRef player, MovementManager manager) {
        manager.update(player.getPacketHandler());
    }

    /** Sets the three fly fields and pushes them. */
    static void setFly(PlayerRef player, MovementManager manager, FlyMode mode, float horizontal, float vertical) {
        MovementSettings settings = manager.getSettings();
        settings.fly = mode;
        settings.horizontalFlySpeed = horizontal;
        settings.verticalFlySpeed = vertical;
        push(player, manager);
    }

    /** Sends the client a SetMovementStates with only the flying flag (the one field of SavedMovementStates). */
    static void sendFlying(PlayerRef player, boolean flying) {
        player.getPacketHandler().write(new SetMovementStates(new SavedMovementStates(flying)));
    }

    /** The engine's own way to start and stop flying: Player.setFlying (fly becomes Allowed or Forced on, the default fly mode again off; it pushes the settings and the flag). */
    static void engineFlying(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor, boolean flying) {
        com.hypixel.hytale.server.core.entity.entities.Player player = accessor.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
        if (player != null) {
            player.setFlying(ref, flying, accessor);
        }
    }

    static void say(PlayerRef player, String text) {
        player.sendMessage(Message.raw(text));
    }

    static void startHum(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        EffectControllerComponent controller = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(LabItems.HUM_EFFECT);
        if (controller != null && effect != null) {
            controller.addEffect(ref, effect, accessor);
        }
    }

    static void stopHum(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        EffectControllerComponent controller = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        int index = EntityEffect.getAssetMap().getIndex(LabItems.HUM_EFFECT);
        if (controller != null && index >= 0) {
            controller.removeEffect(ref, index, accessor);
        }
    }

    /**
     * Puts everything back: the engine's own reset of the defaults (which pushes them), no belt, no clamp, no flying flag, no hum. The
     * watch stays as it is.
     */
    static void reset(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor, PlayerRef player, LabState.PlayerLab lab) {
        MovementManager manager = manager(accessor, ref);
        if (manager != null) {
            manager.resetDefaultsAndUpdate(ref, accessor);
        }
        if (lab.flagSent || lab.beltOn) {
            sendFlying(player, false);
        }
        stopHum(accessor, ref);
        lab.beltOn = false;
        lab.beltMode = 0;
        lab.clampMax = 0;
        lab.flagSent = false;
        lab.saved = null;
        lab.pendingReset = false;
    }
}
