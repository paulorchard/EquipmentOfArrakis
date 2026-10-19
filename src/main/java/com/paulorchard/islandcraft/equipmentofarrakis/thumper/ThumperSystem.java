package com.paulorchard.islandcraft.equipmentofarrakis.thumper;

import com.hypixel.hytale.builtin.deployables.component.DeployableComponent;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Runs every world tick for every deployable that has a position and an id: for a Thumper it moves that Thumper's beat clock on and
 * plays the thump for each hit that is due, as a world sound at the Thumper (every player nearby hears it, with the falloff of the
 * sound event). The first beat is at the first tick after the placement. A Thumper that is gone simply stops ticking and is dropped
 * from the book a couple of seconds later.
 */
final class ThumperSystem extends EntityTickingSystem<EntityStore> {

    private static final UUID NO_OWNER = new UUID(0L, 0L);
    private static final long PRUNE_EVERY_NANOS = 1_000_000_000L;

    private Query<EntityStore> query;
    private long lastPrune;
    private int soundIndex = Integer.MIN_VALUE;

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(DeployableComponent.getComponentType(), TransformComponent.getComponentType(), UUIDComponent.getComponentType());
        }
        return query;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        DeployableComponent deployable = chunk.getComponent(index, DeployableComponent.getComponentType());
        if (deployable == null || deployable.getConfig() == null || !Thumpers.ID.equals(deployable.getConfig().getId())) {
            return;
        }
        UUIDComponent uuid = chunk.getComponent(index, UUIDComponent.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (uuid == null || transform == null) {
            return;
        }
        UUID owner = deployable.getOwnerUUID() == null ? NO_OWNER : deployable.getOwnerUUID();
        Vector3d at = transform.getPosition();
        long now = System.nanoTime();
        int hits = Thumpers.book().tick(uuid.getUuid(), owner, at.x, at.y, at.z, dt, now);
        if (hits > 0) {
            int sound = sound();
            if (sound > 0) {
                Vector3d where = new Vector3d(at.x, at.y + 0.3, at.z);
                for (int i = 0; i < hits; i++) {
                    SoundUtil.playSoundEvent3d(sound, SoundCategory.SFX, where, commandBuffer);
                }
            }
        }
        if (now - lastPrune > PRUNE_EVERY_NANOS) {
            lastPrune = now;
            Thumpers.book().prune(now);
        }
    }

    private int sound() {
        if (soundIndex <= 0) {
            soundIndex = SoundEvent.getAssetMap().getIndex(Thumpers.THUMP_SOUND);
        }
        return soundIndex;
    }
}
