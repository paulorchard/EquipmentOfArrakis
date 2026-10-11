package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Runs every world tick for every player and ends a compacting that should have ended without the interaction saying so: no
 * compacting tick for {@link CompactorTrail#EXPIRY_NANOS} (the chain was cancelled, or the client went away), the Compactor no longer
 * in the utility slot, the player dead or gone. Ending gives the block its health back.
 */
final class CompactorSystem extends EntityTickingSystem<EntityStore> {

    private Query<EntityStore> query;

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(Player.getComponentType(), PlayerRef.getComponentType());
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
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        CompactorTrail.State state = player == null ? null : CompactorTrail.get(player.getUuid());
        if (state == null || state.target == null) {
            return;
        }
        if (System.nanoTime() - state.lastTick > CompactorTrail.EXPIRY_NANOS || !CompactorTrail.alive(store, state)) {
            CompactorTrail.release(state);
            state.lastTick = 0;
        }
    }
}
