package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Removes laser entities nobody owns: the owner left, the world was reloaded, or the lock ended without the entity
 * being removed. The normal path removes them itself ({@link CutterLaser}); this is the safety net.
 */
final class BeamSegmentSystem extends EntityTickingSystem<EntityStore> {

    private Query<EntityStore> query;

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(BeamSegment.getComponentType());
        }
        return query;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        BeamSegment segment = chunk.getComponent(index, BeamSegment.getComponentType());
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (segment != null && ref != null && !MoltenTrail.owns(segment.owner(), ref)) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
        }
    }
}
