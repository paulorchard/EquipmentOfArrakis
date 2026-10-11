package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Hook one: the engine's older projectile, the {@code Projectile} asset launched by the {@code LaunchProjectile} interaction (the Maula
 * Pistol, the Dartgun, the Cutter Ray and the vanilla guns). It runs when the projectile entity is added to the world, the moment of
 * the shot, in the same way as the engine's own {@code LegacyProjectileSystems$OnAddRefSystem}. On add and not on the first tick,
 * because the creator is already set ({@code ProjectileComponent.shoot} sets it before the entity is added), so the field drops in the
 * tick of the shot and not one later, and each entity is seen exactly once.
 */
final class ShieldShotLegacySystem extends RefSystem<EntityStore> {

    @Override
    public Query<EntityStore> getQuery() {
        return ProjectileComponent.getComponentType();
    }

    @Override
    public void onEntityAdded(Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        ProjectileComponent projectile = store.getComponent(ref, ProjectileComponent.getComponentType());
        if (projectile != null) {
            ShieldShots.record(store, projectile.getCreatorUuid(), "legacy", projectile.getProjectileAssetName());
        }
    }

    @Override
    public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
    }
}
