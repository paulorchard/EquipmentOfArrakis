package com.paulorchard.islandcraft.equipmentofarrakis.weapons;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.function.Supplier;

/**
 * Notes where each dart was launched from: the eye of the shooter (the engine starts the dart 0.7 blocks ahead of it). It runs when a
 * projectile entity is added to the world, before it has moved a tick, so the position it reads is the start of the flight. It puts a
 * {@link DartFlight} on the entity if the projectile is one of the darts listed in {@code the Weapons section of Equipment_of_Arrakis.json}; other projectiles are left
 * alone. The component goes with the entity, so nothing is kept anywhere that has to be cleaned up.
 */
final class DartFlightSystem extends RefSystem<EntityStore> {

    private final Supplier<WeaponsConfig> settings;
    private Query<EntityStore> query;

    DartFlightSystem(Supplier<WeaponsConfig> settings) {
        this.settings = settings;
    }

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(ProjectileComponent.getComponentType(), TransformComponent.getComponentType());
        }
        return query;
    }

    @Override
    public void onEntityAdded(Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        ProjectileComponent projectile = store.getComponent(ref, ProjectileComponent.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (projectile == null || transform == null || !settings.get().isDart(projectile.getProjectileAssetName())) {
            return;
        }
        // The engine puts the dart DepthShot (0.7) blocks ahead of the eye along its line of flight (its rotation is the aim, as the
        // LaunchProjectile interaction gives it); the range is counted from the eye, where the player measures from, so that is taken back off.
        Vector3d at = transform.getPosition();
        Rotation3f rotation = transform.getRotation();
        Vector3d direction = PhysicsMath.vectorFromAngles(rotation.yaw(), rotation.pitch(), new Vector3d());
        double ahead = projectile.getProjectile() == null ? 0 : projectile.getProjectile().getDepthShot();
        if (direction.lengthSquared() > 1e-9) {
            direction.normalize(ahead);
        } else {
            direction.set(0, 0, 0);
        }
        if (DartRangeDamageSystem.trace) {
            WeaponsModule.logger().atInfo().log("dart added at %s facing yaw %s pitch %s, %s ahead of the eye along %s", at,
                    rotation.yaw(), rotation.pitch(), ahead, direction);
        }
        commandBuffer.addComponent(ref, DartFlight.type, new DartFlight(new double[] {at.x - direction.x, at.y - direction.y, at.z - direction.z}));
    }

    @Override
    public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
    }
}
