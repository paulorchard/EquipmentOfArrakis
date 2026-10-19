package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeferredCorpseRemoval;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.joml.Vector3d;

import java.util.List;

/**
 * Gives the body of a killed mob the long timer. The engine's own {@code NPCSystems.OnDeathSystem} reacts to the {@code DeathComponent}
 * being added and adds a {@link DeferredCorpseRemoval} whose time is the death animation (a second or a few); this system reacts to that
 * component being added, so it always runs after it, and replaces it with one of {@code CorpseSeconds}, the same death particles kept.
 * {@code DeathSystems.TickCorpseRemoval} counts the new one down and {@code DeathSystems.CorpseRemoval} removes the entity, as for the
 * vanilla one. Players are not in the query. Only mobs whose own death clip holds its last keyframe (read from the clip, see {@link DeathClips})
 * are extended, because the body of the others would stand up again; they keep the vanilla timer unless {@code ExtendNonHolding} is set, and
 * are not in the ledger. The {@link Corpses#LEDGER} remembers every body handled, so the replacement (which may
 * raise the change events again) is never extended a second time. A chunk that would hold more than {@code MaxCorpsesPerChunk} bodies
 * has its oldest ones cut to the quick removal. Runs on the world thread, as every system does.
 */
final class CorpseTimerSystem extends RefChangeSystem<EntityStore, DeferredCorpseRemoval> {

    private Query<EntityStore> query;

    @Override
    public ComponentType<EntityStore, DeferredCorpseRemoval> componentType() {
        return DeferredCorpseRemoval.getComponentType();
    }

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Query.and(NPCEntity.getComponentType(), Query.not(Player.getComponentType()), TransformComponent.getComponentType());
        }
        return query;
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> ref, DeferredCorpseRemoval timer, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        ExtractorConfig config = Corpses.config();
        if (config.corpseSeconds() <= 0 || Corpses.LEDGER.knows(ref)) {
            return;
        }
        NPCEntity npc = buffer.getComponent(ref, NPCEntity.getComponentType());
        TransformComponent transform = buffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || npc == null) {
            return;
        }
        ModelComponent modelComponent = buffer.getComponent(ref, ModelComponent.getComponentType());
        DeathComponent death = buffer.getComponent(ref, DeathComponent.getComponentType());
        MovementStatesComponent movement = buffer.getComponent(ref, MovementStatesComponent.getComponentType());
        CorpseHold.Verdict verdict = modelComponent == null ? new CorpseHold.Verdict("(no model)", false)
                : DeathClips.verdict(modelComponent.getModel(), movement == null ? new MovementStates() : movement.getMovementStates(),
                        death == null || death.getDeathCause() == null ? DamageCause.PHYSICAL : death.getDeathCause());
        boolean extend = CorpseRules.shouldExtend(false, true, npc.getRoleName(), config.excludedRoles(), verdict.holds(), config.extendNonHolding());
        String modelId = modelComponent == null ? "?" : modelComponent.getModel().getModelAssetId();
        if (DeathClips.cache().firstTime(modelId)) {
            Corpses.log(CorpseHold.logLine(npc.getRoleName(), modelId, verdict, extend, config.corpseSeconds()));
        }
        if (!extend) {
            return;
        }
        Vector3d at = transform.getPosition();
        long chunk = ChunkUtil.indexChunkFromBlock(at.x, at.z);
        long end = System.nanoTime() + (long) (config.corpseSeconds() * 1_000_000_000L);
        List<Ref<EntityStore>> cuts = Corpses.LEDGER.add(ref, chunk, end, config.maxCorpsesPerChunk(), Corpses.GONE);
        Corpses.setTimer(buffer, ref, config.corpseSeconds());
        for (Ref<EntityStore> oldest : cuts) {
            Corpses.setTimer(buffer, oldest, config.extractedSeconds());
        }
    }

    @Override
    public void onComponentSet(Ref<EntityStore> ref, DeferredCorpseRemoval oldTimer, DeferredCorpseRemoval newTimer, Store<EntityStore> store,
                               CommandBuffer<EntityStore> buffer) {
    }

    /** Nothing to do: the ledger drops an entity that is gone the next time it is touched. Removing here would let a replaced timer be extended again. */
    @Override
    public void onComponentRemoved(Ref<EntityStore> ref, DeferredCorpseRemoval timer, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
    }
}
