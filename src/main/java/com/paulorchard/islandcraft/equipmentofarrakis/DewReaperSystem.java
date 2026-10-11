package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Runs every world tick for a player with a swing under way and ends it when it was cut short: the Dew Reaper's right-click chain is no
 * longer running (the tool was swapped, the chain was cancelled) before the Finish interaction took the memory, or the player died, or
 * the memory is older than any swing. Ending takes the hum off at once and clears the memory; the power is not given back and no
 * water is given. A swing that completes is cleared by the Finish interaction itself.
 */
final class DewReaperSystem extends EntityTickingSystem<EntityStore> {

    /** The chain is started in the same tick as the memory; give it this long before judging it gone. */
    private static final long GRACE_NANOS = 150_000_000L;

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
        DewReaperState.Pending pending = player == null ? null : DewReaperState.get(player.getUuid());
        if (pending == null) {
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        long age = System.nanoTime() - pending.startNanos();
        boolean dead = store.getComponent(ref, DeathComponent.getComponentType()) != null;
        boolean stale = age > DewReaperState.STALE_NANOS;
        boolean gone = age > GRACE_NANOS && !swinging(store, ref);
        if (dead || stale || gone) {
            DewReaperState.take(player.getUuid());
            DewReaperState.stopHum(commandBuffer, ref, pending.humEffectId());
        }
    }

    /** True while a Secondary chain of the Dew Reaper's root is running for this player. */
    private static boolean swinging(Store<EntityStore> store, Ref<EntityStore> ref) {
        InteractionManager manager = store.getComponent(ref, InteractionModule.get().getInteractionManagerComponent());
        if (manager == null) {
            return false;
        }
        for (InteractionChain chain : manager.getChains().values()) {
            if (chain.getType() == InteractionType.Secondary && chain.getServerState() == InteractionState.NotFinished
                    && chain.getRootInteraction() != null && DewReaperState.ROOT_ID.equals(chain.getRootInteraction().getId())) {
                return true;
            }
        }
        return false;
    }
}
