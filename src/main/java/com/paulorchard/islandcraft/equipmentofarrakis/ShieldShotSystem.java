package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Hook two: the engine's newer projectile, the {@code ProjectileConfig} launched by the {@code Projectile} interaction through
 * {@code ProjectileModule.spawnProjectile} (the vanilla bows, crossbows, thrown items, staffs and wands, and the mobs' ranged attacks).
 * Its creator is not on the {@code Projectile} marker component but on the entity's {@link StandardPhysicsProvider}, put there when the
 * entity is built, so it is already known when the entity is added, and this runs then, as {@link ShieldShotLegacySystem} does. The
 * {@code PredictedProjectile} component on these entities only carries the id the client predicted the projectile under: the server
 * adds one entity per shot, so a client-predicted shot is one trigger.
 */
final class ShieldShotSystem extends RefSystem<EntityStore> {

    @Override
    public Query<EntityStore> getQuery() {
        return StandardPhysicsProvider.getComponentType();
    }

    @Override
    public void onEntityAdded(Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        StandardPhysicsProvider physics = store.getComponent(ref, StandardPhysicsProvider.getComponentType());
        if (physics != null) {
            ShieldShots.record(store, physics.getCreatorUuid(), "standard", "ProjectileConfig");
        }
    }

    @Override
    public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
    }
}
