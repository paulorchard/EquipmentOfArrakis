package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Whether a player is crouching because they chose to: the server keeps the movement states the client sends every tick, and
 * {@code crouching} is the player holding crouch. {@code forcedCrouching} is a crouch that something else set (a low ceiling, or a mod
 * writing the state, as the Blood Extractor does with {@code ForceCrouch}), which is not the player's choice and does not count.
 */
public final class Crouch {

    private Crouch() {
    }

    /** The pure part: a crouch counts when it is on and not forced. */
    public static boolean counts(boolean crouching, boolean forcedCrouching) {
        return crouching && !forcedCrouching;
    }

    /** True when the player is crouching by their own choice. World thread. */
    public static boolean isCrouching(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> player) {
        MovementStatesComponent component = accessor.getComponent(player, MovementStatesComponent.getComponentType());
        if (component == null) {
            return false;
        }
        MovementStates states = component.getMovementStates();
        return counts(states.crouching, states.forcedCrouching);
    }
}
