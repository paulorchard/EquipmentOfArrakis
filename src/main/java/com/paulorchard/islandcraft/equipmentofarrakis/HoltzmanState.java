package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the Holtzman remembers about one player, keyed by the player entity's UUID (the same UUID a projectile carries as its creator):
 * when the last projectile was launched, and the counters the Ranged check ({@link RangedWatch}) compares. Prompt 11's
 * {@link HoltzmanSystem} kept nothing and still keeps nothing about the field itself (it looks at what is, every tick); this is only the
 * cooldown and the counters. Everything is touched from the world thread.
 */
final class HoltzmanState {

    private static final Map<UUID, HoltzmanState> STATES = new ConcurrentHashMap<>();

    /** The player entity this state was last seen on; a different one (a new world, a respawn into a new entity) starts again. */
    Ref<EntityStore> ref;
    /** {@link System#nanoTime} of the last projectile the player launched, or {@link ShieldCooldown#NONE}. */
    long lastShot = ShieldCooldown.NONE;
    /** Projectiles launched by the player since the server started. */
    long projectiles;
    /** Shots a gun's gate refused (no ammo, no power, nothing in reach) since the server started. */
    long refusals;
    /** {@link #projectiles} at the end of the previous tick, for the Ranged check. */
    long projectilesAtPreviousTick;
    long refusalsAtPreviousTick;

    private HoltzmanState() {
    }

    static HoltzmanState of(UUID player) {
        return STATES.computeIfAbsent(player, id -> new HoltzmanState());
    }

    static HoltzmanState peek(UUID player) {
        return STATES.get(player);
    }

    static void forget(UUID player) {
        STATES.remove(player);
        RangedWatch.forget(player);
    }

    /** The player launched a projectile at {@code now}: the field is down for the cooldown from here. */
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
