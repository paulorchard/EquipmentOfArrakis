package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Switches the Cutter Ray beam off for a player whose beam ticks have stopped (see {@link BeamDisplay}). It also
 * clears a beam left on in a save: the stat is saved with the player, and nobody has fired since the load.
 */
final class BeamExpirySystem extends EntityTickingSystem<EntityStore> {

    private Query<EntityStore> query;

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(Player.getComponentType(), PlayerRef.getComponentType(), EntityStatMap.getComponentType());
        }
        return query;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        int stat = BeamDisplay.statIndex();
        EntityStatMap stats = chunk.getComponent(index, EntityStatMap.getComponentType());
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        if (stat == Integer.MIN_VALUE || stats == null || player == null || stats.get(stat) == null
                || stats.get(stat).get() <= 0f) {
            return;
        }
        if (BeamDisplay.expired(player.getUuid(), System.nanoTime())) {
            stats.setStatValue(stat, 0f);
        }
    }
}
