package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthChunk;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
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
 * <p>The crack is the engine's own block health ({@link BlockHealthChunk}), which the client draws as the crack, but it is never set
 * through {@code BlockHarvestUtils.performBlockDamage}, the call that breaks a block when its health reaches 0. Here the health is
 * only ever lowered to {@code 1 - MaxCrack * progress} (MaxCrack at most 0.9), never further, and only by the part not already
 * taken, so no number of ticks, lag spikes or compacting players can bring it below 0.1 by this tool; and nothing in this class
 * breaks a block. What this player took off is remembered and given back (see {@link #release}) when the cycle ends or is cut off.
 */
final class CompactorTrail {

    static final String ITEM_ID = "Arrakis_Compactor";
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

    static BlockHealthChunk healthChunk(World world, Vector3i block) {
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(block.x, block.y, block.z);
        if (section == null || !section.isValid()) {
            return null;
        }
        ChunkSection chunkSection = chunks.getStore().getComponent(section, ChunkSection.getComponentType());
        if (chunkSection == null) {
            return null;
        }
        return chunks.getStore().getComponent(chunkSection.getChunkColumnReference(),
                BlockHealthModule.get().getBlockHealthChunkComponentType());
    }

    /**
     * Shows the block cracked to {@code targetHealth}: lowers its health to that if it is higher, never if it is lower already, and
     * never below {@code floor}. Remembers what was taken.
     */
    static void showCrack(State state, float targetHealth, float floor) {
        BlockHealthChunk health = healthChunk(state.world, state.target);
        if (health == null) {
            return;
        }
        float now = health.getBlockHealth(state.target);
        float wanted = Math.max(targetHealth, floor);
        float delta = now - wanted;
        if (delta > 0.0005f) {
            health.damageBlock(Instant.now(), state.world, state.target, delta);
            state.taken += delta;
        }
    }

    /** Gives back what this player took off the block, and forgets the target. */
    static void release(State state) {
        if (state.target != null && state.world != null && state.taken > 0f) {
            try {
                BlockHealthChunk health = healthChunk(state.world, state.target);
                if (health != null) {
                    // repairBlock drops the whole damage record whatever the amount (measured), so a partial give-back is a
                    // negative damage; when everything taken comes back the block is simply made whole.
                    float now = health.getBlockHealth(state.target);
                    if (now + state.taken >= 0.999f) {
                        health.removeBlock(state.world, state.target);
                    } else {
                        health.damageBlock(Instant.now(), state.world, state.target, -state.taken);
                    }
                }
            } catch (RuntimeException e) {
                EquipmentOfArrakisPlugin.get().getLogger().atWarning().log("Compactor: could not repair %s: %s", state.target, e);
            }
        }
        state.taken = 0f;
        state.target = null;
        state.cycle = null;
    }

    /** True while the player is alive, valid and holds the Compactor in either hand (utility slot or hotbar). */
    static boolean alive(Store<EntityStore> store, State state) {
        Ref<EntityStore> player = state.player;
        if (!player.isValid() || store.getComponent(player, DeathComponent.getComponentType()) != null) {
            return false;
        }
        // In either hand: the Compactor can be the off-hand (utility) item or the main-hand (hotbar) item.
        InventoryComponent.Utility utility = store.getComponent(player, InventoryComponent.Utility.getComponentType());
        InventoryComponent.Hotbar hotbar = store.getComponent(player, InventoryComponent.Hotbar.getComponentType());
        return isCompactor(utility == null ? null : utility.getActiveItem())
                || isCompactor(hotbar == null ? null : hotbar.getActiveItem());
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
