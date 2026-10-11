package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.function.Supplier;

/**
 * Ends a dart that has flown {@code Range} blocks (40) from where it was launched: the entity is removed at once, with no impact
 * particle or sound, so it can never hit anything beyond. This is needed because the projectile asset's {@code TimeToLive} is never read
 * by this server version (verified in the jar: nothing calls it); without this a dart that finds nothing flies for 60 seconds, the
 * engine's default despawn time. The dart can move up to one tick of flight (3.3 blocks at speed 100) past the limit before this runs;
 * a hit in that last step is cancelled by {@link DartRangeDamageSystem}.
 */
final class DartRangeSystem extends EntityTickingSystem<EntityStore> {

    private final Supplier<DartSettings> settings;
    private Query<EntityStore> query;

    DartRangeSystem(Supplier<DartSettings> settings) {
        this.settings = settings;
    }

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(DartFlight.type, TransformComponent.getComponentType());
        }
        return query;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        DartFlight flight = chunk.getComponent(index, DartFlight.type);
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (flight == null || transform == null) {
            return;
        }
        Vector3d at = transform.getPosition();
        if (DartMath.beyond(flight.launch, new double[] {at.x, at.y, at.z}, settings.get().getRange())) {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
        }
    }
}
