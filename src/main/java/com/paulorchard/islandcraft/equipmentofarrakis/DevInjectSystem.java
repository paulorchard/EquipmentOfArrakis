package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Development: runs a queued piece of work with the command buffer of a world tick, which a command cannot get on its own. The
 * projectile module spawns a projectile through a command buffer, so the shield hook self-test uses this to fire a vanilla arrow.
 * It does nothing while the queue is empty.
 */
final class DevInjectSystem extends EntityTickingSystem<EntityStore> {

    private static final Queue<Consumer<CommandBuffer<EntityStore>>> QUEUE = new ConcurrentLinkedQueue<>();
    private Query<EntityStore> query;

    /** Runs {@code work} on the world thread in the next tick that has an entity to tick. */
    static void run(Consumer<CommandBuffer<EntityStore>> work) {
        QUEUE.add(work);
    }

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(UUIDComponent.getComponentType());
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
        Consumer<CommandBuffer<EntityStore>> work = QUEUE.poll();
        while (work != null) {
            work.accept(commandBuffer);
            work = QUEUE.poll();
        }
    }
}
