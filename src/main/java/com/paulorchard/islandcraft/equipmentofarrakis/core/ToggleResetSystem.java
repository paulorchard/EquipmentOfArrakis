package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Death resets the toggles: a player who is dead (has a DeathComponent) has every registered toggle forgotten once, so a toggle is OFF
 * after death and after the respawn. Disconnect and world changes are events registered by the core module.
 */
final class ToggleResetSystem extends EntityTickingSystem<EntityStore> {

    private final Set<UUID> dead = ConcurrentHashMap.newKeySet();
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
        if (player == null) {
            return;
        }
        boolean isDead = chunk.getComponent(index, DeathComponent.getComponentType()) != null;
        if (isDead) {
            if (dead.add(player.getUuid())) {
                ToggleRegistry.get().forget(player.getUuid());
            }
        } else {
            dead.remove(player.getUuid());
        }
    }
}
