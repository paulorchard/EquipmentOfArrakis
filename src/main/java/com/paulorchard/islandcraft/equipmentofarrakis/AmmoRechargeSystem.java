package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Recharges {@code Arrakis_Ammo} for every living player who is not shooting (see {@link ArrakisAmmo#idle}), at the rate in
 * {@code Dart_Guns.json}. The same system as {@link PowerRechargeSystem}, for the ammo.
 */
final class AmmoRechargeSystem extends EntityTickingSystem<EntityStore> {

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
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (player == null || ref == null || !ref.isValid() || !ArrakisAmmo.idle(player.getUuid(), System.nanoTime())
                || store.getComponent(ref, DeathComponent.getComponentType()) != null) {
            return;
        }
        ArrakisAmmo.recharge(store, ref, dt);
    }
}
