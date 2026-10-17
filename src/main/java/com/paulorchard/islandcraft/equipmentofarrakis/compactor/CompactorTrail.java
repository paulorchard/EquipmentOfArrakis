package com.paulorchard.islandcraft.equipmentofarrakis.compactor;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthSection;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.packets.world.UpdateBlockDamage;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldAccess;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The state of every compacting Compactor, and the parts of compacting that touch the engine: the crack on the block, the drop and
 * the hum. The tick itself is {@link CompactorInteraction}; {@link CompactorSystem} ends a compacting whose ticks have stopped.
 *
 * <p>The crack is the engine's own block health ({@link BlockHealthSection}), which the client draws as the crack, but it is never set
 * through {@code BlockHarvestUtils.performBlockDamage}, the call that breaks a block when its health reaches 0. Here the health is
 * only ever lowered to {@code 1 - MaxCrack * progress} (MaxCrack at most 0.9), never further, and only by the part not already
 * taken, so no number of ticks, lag spikes or compacting players can bring it below 0.1 by this tool; and nothing in this class
 * breaks a block. What this player took off is remembered and given back (see {@link #release}) when the cycle ends or is cut off.
 */
final class CompactorTrail {

    static final String ITEM_ID = CompactorModule.TOOL;
    /** The compacting ticks come every tick or two while the button is held; none for this long means it was released. */
    static final long EXPIRY_NANOS = 300_000_000L;
    /** A tick counts for at most this long, so that a stall of the server cannot finish a cycle at once. */
    static final double MAX_STEP_SECONDS = 0.2;

    /** One player's compacting. */
    static final class State {
        final UUID id;
        Ref<EntityStore> player;
        World world;
        Vector3i target;
        int blockId;
        CompactMath.Cycle cycle;
        /** The health this player has taken off the target block and has not given back. */
        float taken;
        long lastTick;
        long lastDust;

        State(UUID id, Ref<EntityStore> player) {
            this.id = id;
            this.player = player;
        }
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private CompactorTrail() {
    }

    static State get(UUID player) {
        return STATES.get(player);
    }

    /** The state of this player, made on first use; null when the entity is not a player. */
    static State state(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> player) {
        PlayerRef ref = accessor.getComponent(player, PlayerRef.getComponentType());
        if (ref == null) {
            return null;
        }
        State state = STATES.computeIfAbsent(ref.getUuid(), id -> new State(id, player));
        state.player = player;
        return state;
    }

    /** Forgets the player (on disconnect), giving the block its health back on the world's own thread. */
    static void forget(UUID player) {
        State state = STATES.remove(player);
        if (state != null && state.world != null && state.target != null) {
            state.world.execute(() -> release(state));
        }
    }

    // ---- the block health ----

    /** The game clock the engine's own regeneration compares damage times with (not wall time). */
    private static Instant gameNow(World world) {
        return world.getEntityStore().getStore().getResource(TimeResource.getResourceType()).getNow();
    }

    /**
     * Shows the block cracked to {@code targetHealth}: lowers its health to that if it is higher, never if it is lower already, and
     * never below {@code floor}. Remembers what was taken. The engine's ReplicateChanges system sends the crack to the players.
     */
    static void showCrack(State state, float targetHealth, float floor) {
        Vector3i at = state.target;
        WorldAccess access = new WorldAccess(state.world);
        BlockHealthSection health = access.healthSection(at.x, at.y, at.z);
        if (health == null) {
            return;
        }
        float now = health.getHealth(at.x, at.y, at.z);
        float wanted = Math.max(targetHealth, floor);
        float delta = now - wanted;
        if (delta > 0.0005f) {
            health.damage(at.x, at.y, at.z, delta, gameNow(state.world));
            markSaving(access, at);
            state.taken += delta;
        }
    }

    /** Gives back what this player took off the block, and forgets the target. */
    static void release(State state) {
        if (state.target != null && state.world != null && state.taken > 0f) {
            try {
                Vector3i at = state.target;
                WorldAccess access = new WorldAccess(state.world);
                BlockHealthSection health = access.healthSection(at.x, at.y, at.z);
                if (health != null) {
                    // Healing is a negative damage, which also deletes the record when the health is back at 1 or more. That
                    // deletion queues no change for the client, so the clearing update is sent here (and a record left a hair
                    // below 1 by float rounding is cleared the same way).
                    float healed = state.taken;
                    float now = health.damage(at.x, at.y, at.z, -healed, gameNow(state.world));
                    if (now >= 0.999f) {
                        if (now < 1f) {
                            health.clearHealth(ChunkUtil.indexBlock(at.x & 31, at.y & 31, at.z & 31));
                        }
                        sendCleared(state.world, at, healed);
                    }
                    markSaving(access, at);
                }
            } catch (RuntimeException e) {
                CompactorModule.logger().atWarning().log("Compactor: could not repair %s: %s", state.target, e);
            }
        }
        state.taken = 0f;
        state.target = null;
        state.cycle = null;
    }

    private static void markSaving(WorldAccess access, Vector3i at) {
        ChunkSection section = access.chunkSection(at.x, at.y, at.z);
        if (section != null) {
            section.markNeedsSaving();
        }
    }

    /** Tells the players who have the section loaded that the crack is gone, the way the engine's ReplicateChanges does. */
    private static void sendCleared(World world, Vector3i at, float healed) {
        int sx = ChunkUtil.chunkCoordinate(at.x);
        int sy = ChunkUtil.chunkCoordinate(at.y);
        int sz = ChunkUtil.chunkCoordinate(at.z);
        UpdateBlockDamage packet = new UpdateBlockDamage(new BlockPosition(at.x, at.y, at.z), 1.0f, healed);
        for (PlayerRef player : world.getPlayerRefs()) {
            if (player.getChunkTracker().isLoaded(sx, sy, sz)) {
                player.getPacketHandler().writeNoCache(packet);
            }
        }
    }

    /** True while the player is alive, valid and holds the Compactor in the main hand. */
    static boolean alive(Store<EntityStore> store, State state) {
        Ref<EntityStore> player = state.player;
        if (!player.isValid() || store.getComponent(player, DeathComponent.getComponentType()) != null) {
            return false;
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(player, InventoryComponent.Hotbar.getComponentType());
        return isCompactor(hotbar == null ? null : hotbar.getActiveItem());
    }

    private static boolean isCompactor(ItemStack stack) {
        return stack != null && ITEM_ID.equals(stack.getItemId());
    }

    // ---- the drop ----

    /** What breaking this block would give, from the engine's own drop generation (loot tables included). */
    static List<ItemStack> dropsOf(BlockType block) {
        BlockGathering gathering = block.getGathering();
        if (gathering != null) {
            BlockBreakingDropType breaking = gathering.getBreaking();
            if (breaking != null) {
                return BlockHarvestUtils.getDrops(block, breaking.getQuantity(), breaking.getItemId(), breaking.getDropListId());
            }
            if (gathering.isSoft()) {
                SoftBlockDropType soft = gathering.getSoft();
                return BlockHarvestUtils.getDrops(block, 1, soft.getItemId(), soft.getDropListId());
            }
        }
        return BlockHarvestUtils.getDrops(block, 1, null, null);
    }

    /** Pops the drops out of the face of the block the player is looking at, with a small push outwards. */
    @SuppressWarnings("unchecked")
    static void drop(CommandBuffer<EntityStore> buffer, Ref<EntityStore> player, Vector3i block, List<ItemStack> drops) {
        Transform look = TargetUtil.getLook(player, buffer);
        BeamMath.Entry entry = BeamMath.entry(look.getPosition(), new Vector3d(look.getDirection()).normalize(), block);
        Vector3d at = BeamMath.offFace(entry, 0.15);
        for (ItemStack stack : drops) {
            Holder<EntityStore> item = ItemComponent.generateItemDrop(buffer, stack, at, new Rotation3f(),
                    entry.normal().x * 1.5f, 1.5f, entry.normal().z * 1.5f);
            if (item != null) {
                buffer.addEntities(new Holder[] {item}, AddReason.SPAWN);
            }
        }
    }

    // ---- the hum ----

    /** Applies (refreshes) the hum effect. */
    static void hum(CommandBuffer<EntityStore> buffer, Ref<EntityStore> player, String effectId) {
        EffectControllerComponent controller = buffer.getComponent(player, EffectControllerComponent.getComponentType());
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
        if (controller != null && effect != null) {
            controller.addEffect(player, effect, buffer);
        }
    }
}
