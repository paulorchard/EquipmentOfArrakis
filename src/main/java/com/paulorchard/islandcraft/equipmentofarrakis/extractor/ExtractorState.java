package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the Blood Extractor remembers: the poke in progress per player, the corpses already used (a set of entity refs, so a body is
 * never used twice by anyone; a ref goes invalid when the entity is removed and is pruned then), and the crouch handling.
 *
 * <p>Forcing the crouch is a write to the server-side {@link MovementStatesComponent} of the player ({@code crouching} and
 * {@code forcedCrouching}), repeated every tick of the poke by {@link ExtractorSystem}. The engine's movement-states system sends
 * that state to the players who can see this one. The owner client sends its own states too, so whether it overrides the write
 * for the owner is what only a client can show; {@code RequireCrouched} in the gate data is the fallback.
 */
final class ExtractorState {

    /** An accepted click: the corpse, when the poke ends, and whether the player was crouching before. */
    record Pending(Ref<EntityStore> corpse, long endNanos, boolean wasCrouching, int blood, boolean forceCrouch) {
    }

    /** How long after its end a poke is still waited for before it counts as cut short. */
    static final long GRACE_NANOS = 400_000_000L;

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Set<Ref<EntityStore>> USED = ConcurrentHashMap.newKeySet();

    private ExtractorState() {
    }

    static void put(UUID player, Pending pending) {
        PENDING.put(player, pending);
    }

    static Pending get(UUID player) {
        return PENDING.get(player);
    }

    static Pending take(UUID player) {
        return PENDING.remove(player);
    }

    static Iterable<Map.Entry<UUID, Pending>> all() {
        return PENDING.entrySet();
    }

    /** Claims a corpse; false when it was already used or claimed. Prunes the refs of corpses that are gone. */
    static boolean claim(Ref<EntityStore> corpse) {
        USED.removeIf(ref -> !ref.isValid());
        return USED.add(corpse);
    }

    static boolean isUsed(Ref<EntityStore> corpse) {
        USED.removeIf(ref -> !ref.isValid());
        return USED.contains(corpse);
    }

    /** A poke that was cut short gives its corpse back. */
    static void unclaim(Ref<EntityStore> corpse) {
        USED.remove(corpse);
    }

    static void forget(UUID player) {
        PENDING.remove(player);
    }

    // ---- the crouch

    static boolean isCrouching(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> player) {
        MovementStatesComponent component = accessor.getComponent(player, MovementStatesComponent.getComponentType());
        return component != null && component.getMovementStates().crouching;
    }

    /** Puts the player in the crouched state on the server. */
    static void forceCrouch(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> player) {
        MovementStatesComponent component = accessor.getComponent(player, MovementStatesComponent.getComponentType());
        if (component != null) {
            MovementStates states = component.getMovementStates();
            states.crouching = true;
            states.forcedCrouching = true;
        }
    }

    /** Lets the crouch go: back to what it was before the poke. */
    static void releaseCrouch(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> player, boolean wasCrouching) {
        MovementStatesComponent component = accessor.getComponent(player, MovementStatesComponent.getComponentType());
        if (component != null) {
            MovementStates states = component.getMovementStates();
            states.forcedCrouching = false;
            states.crouching = wasCrouching;
        }
    }
}
