package com.paulorchard.islandcraft.equipmentofarrakis.weapons;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * The one place a cost per shot is asked for. The guns fire free for now: {@code Cost} in the gate asset is 0 and this lets every shot
 * through. An ammo system later raises {@code Cost} in {@code Arrakis_Weapon_*_Gate.json} (the generator constant COST_PER_SHOT) and
 * puts its check and its payment in {@link #charge}; the gate answers a false with the vanilla dry click. Nothing else changes.
 */
final class GunCost {

    private GunCost() {
    }

    /** Pays {@code cost} for one shot by {@code shooter}; false (and nothing paid) if it cannot be. */
    static boolean charge(CommandBuffer<EntityStore> buffer, Ref<EntityStore> shooter, float cost) {
        return !(cost > 0f) || payAmmo(buffer, shooter, cost);
    }

    /** No ammo system exists yet, so a gate given a cost refuses every shot rather than firing free. */
    private static boolean payAmmo(CommandBuffer<EntityStore> buffer, Ref<EntityStore> shooter, float cost) {
        return false;
    }
}
