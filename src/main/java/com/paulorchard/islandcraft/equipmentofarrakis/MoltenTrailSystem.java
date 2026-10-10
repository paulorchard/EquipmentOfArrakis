package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Draws the Cutter Ray molten trail on every world tick for every player who is firing (see {@link MoltenTrail}). */
final class MoltenTrailSystem extends TickingSystem<EntityStore> {

    @Override
    public void tick(float dt, int index, Store<EntityStore> store) {
        long now = System.nanoTime();
        Iterator<Map.Entry<UUID, MoltenTrail.State>> it = MoltenTrail.all().iterator();
        while (it.hasNext()) {
            MoltenTrail.State state = it.next().getValue();
            if (!state.player.isValid()) {
                it.remove();
            } else if (state.player.getStore() == store) {
                if (MoltenTrail.alive(store, state, now)) {
                    MoltenTrail.draw(store, state);
                } else {
                    it.remove();
                }
            }
        }
    }
}
