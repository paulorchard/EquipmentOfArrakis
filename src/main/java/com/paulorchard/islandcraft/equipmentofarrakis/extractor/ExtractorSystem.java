package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Runs every world tick for every player: while a poke is in progress it keeps the player crouched on the server (the client states
 * may overwrite it between ticks); when a poke did not reach its finish within the grace time (the click was cut short, the player
 * went away) it lets the crouch go and gives the corpse back, so a body is not spent without blood.
 */
final class ExtractorSystem extends EntityTickingSystem<EntityStore> {

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
        ExtractorState.Pending pending = player == null ? null : ExtractorState.get(player.getUuid());
        if (pending == null) {
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (System.nanoTime() > pending.endNanos() + ExtractorState.GRACE_NANOS) {
            ExtractorState.take(player.getUuid());
            if (pending.forceCrouch()) {
                ExtractorState.releaseCrouch(commandBuffer, ref, pending.wasCrouching());
            }
            ExtractorState.unclaim(pending.corpse());
        } else if (pending.forceCrouch()) {
            ExtractorState.forceCrouch(commandBuffer, ref);
        }
    }
}
