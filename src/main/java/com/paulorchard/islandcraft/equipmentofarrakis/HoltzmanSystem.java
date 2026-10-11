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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.function.Supplier;

/**
 * Runs every world tick for every player and makes the Holtzman field match the rule {@link HoltzmanRules#raised}: up while the
 * shield is the selected off-hand item, the player is alive and there is power; down otherwise. It keeps no state: each tick it looks at
 * what is, so a field left in a save, after a respawn or a change of world, or on a player whose shield was swapped away by anything
 * at all, is taken off at once, and nothing can be left behind. With the shield selected and no power it also switches the off hand
 * to empty (so selecting it with 0 power does nothing). The power is read only through {@link ArrakisPower#get}.
 */
final class HoltzmanSystem extends EntityTickingSystem<EntityStore> {

    private final Supplier<HoltzmanSettings> settings;
    private Query<EntityStore> query;

    HoltzmanSystem(Supplier<HoltzmanSettings> settings) {
        this.settings = settings;
    }

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
        HoltzmanSettings cfg = settings.get();
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        boolean alive = store.getComponent(ref, DeathComponent.getComponentType()) == null;
        boolean selected = HoltzmanField.selected(store, ref, cfg);
        float power = ArrakisPower.get(store, ref);
        boolean up = HoltzmanRules.raised(selected, alive, power);
        boolean has = HoltzmanField.hasField(store, ref, cfg);
        if (up && !has) {
            HoltzmanField.raise(commandBuffer, ref, cfg);
        } else if (!up && has) {
            HoltzmanField.lower(commandBuffer, ref, cfg);
        }
        if (selected && alive && power <= 0f) {
            // Selected with no power: the off hand goes back to empty at once.
            HoltzmanField.putAway(commandBuffer, ref);
        }
    }
}
