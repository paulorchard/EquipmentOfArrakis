package com.paulorchard.islandcraft.equipmentofarrakis.shield;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the Holtzman Shield remembers about one player, keyed by the player UUID: whether the toggle is ON, whether the shield has
 * broken and is waiting to re-arm, when the last shot was (for the cooldown) and a few counters. The field itself is not remembered:
 * each tick the system looks at what is. Nothing is saved across a relog: the state is dropped on disconnect, a world change and death
 * (the toggle registry calls {@link #forget}). Everything is touched from the world thread.
 */
final class HoltzmanState {

    private static final Map<UUID, HoltzmanState> STATES = new ConcurrentHashMap<>();

    /** The player entity this state was last seen on; a different one (a new world, a respawn into a new entity) starts again. */
    Ref<EntityStore> ref;
    /** The toggle on the picker page. */
    boolean on;
    /** The shield broke (a hit used up the Mana) and waits for the Mana to come back to the re-arm level; the toggle stays ON. */
    boolean broken;
    /** {@link System#nanoTime} of the last shot of the player, or {@link ShieldCooldown#NONE}. */
    long lastShot = ShieldCooldown.NONE;
    /** Shots seen (projectiles and Cutter Ray shots) since the server started. */
    long projectiles;

    private HoltzmanState() {
    }

    static HoltzmanState of(UUID player) {
        return STATES.computeIfAbsent(player, id -> new HoltzmanState());
    }

    static HoltzmanState peek(UUID player) {
        return STATES.get(player);
    }

    static boolean isOn(UUID player) {
        HoltzmanState state = STATES.get(player);
        return state != null && state.on;
    }

    /** The player is gone, dead or in another world: OFF, not broken, no cooldown. */
    static void forget(UUID player) {
        STATES.remove(player);
    }

    /** The player shot at {@code now}: the field is down for the cooldown from here. */
    void shot(long now) {
        lastShot = now;
        projectiles++;
    }

    /** Forgets the cooldown (a break, a death, a new world): the field may rise at once. */
    void clearCooldown() {
        lastShot = ShieldCooldown.NONE;
    }

    boolean cooling(UUID player, long now) {
        return ShieldCooldown.cooling(lastShot, now, ShieldCooldown.secondsFor(player));
    }

    double remaining(UUID player, long now) {
        return ShieldCooldown.remaining(lastShot, now, ShieldCooldown.secondsFor(player));
    }
}
