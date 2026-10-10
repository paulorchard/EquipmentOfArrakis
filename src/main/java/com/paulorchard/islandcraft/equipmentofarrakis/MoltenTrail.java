package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The state of every firing Cutter Ray: its lock-on, and what is drawn for it.
 *
 * <p>The beam tick ({@link CutterBeamInteraction}, every 0.1 to 0.2 s while Primary is held) decides the lock: the
 * first tick with power left and a block in range locks onto that block and the face the look ray entered; later ticks
 * damage that block. {@link MoltenTrailSystem} then runs on every world tick (about 30 a second) and, for a locked
 * player, checks the lock still holds (the block is still there, the crosshair has not been off it for longer than
 * the grace period), throws the molten globs from a fixed point just out from the middle of the locked face, and moves
 * the laser line ({@link CutterLaser}) to run from the barrel to that same middle.
 *
 * <p>A broken lock needs a release: the gun will not lock onto anything else until the beam ticks have stopped for
 * {@link #EXPIRY_NANOS}.
 */
final class MoltenTrail {

    static final String ITEM_ID = "Arrakis_Cutter_Ray";
    /** The beam ticks come every 0.1 to 0.2 s while the button is held; none for this long means it was released. */
    static final long EXPIRY_NANOS = 300_000_000L;
    /** How far in front of the face the globs start, in blocks. */
    static final double SURFACE_CLEARANCE = 0.08;
    /**
     * A broken lock is cleared by a gap this long between beam ticks. Longer than {@link #EXPIRY_NANOS}, so that a hiccup
     * of the server or the network while the button is still down cannot clear it and let the gun jump to a new block.
     */
    static final long RELEASE_NANOS = 500_000_000L;
    /** A burst throws one glob; this is the largest of the systems (the systems {@code <ParticleId>_1} to {@code _n}). */
    static final int MAX_GLOBS = 1;
    /** The tint when the block has no particle colour of its own. */
    private static final Color FALLBACK_COLOR = new Color((byte) 0xff, (byte) 0xff, (byte) 0xff);
    /** Players within this many blocks of a burst get to see it (the engine's own range for particles). */
    private static final double VIEW_RANGE = 75.0;

    /** What the beam is locked onto. Computed once when the lock starts and never changed. */
    static final class Lock {
        final Vector3i block;
        /** The block id at lock time: a different id later means it was broken or replaced. */
        final int blockId;
        /** The middle of the face the look ray entered. The line ends here. */
        final Vector3d faceCenter;
        /** {@link #SURFACE_CLEARANCE} out from {@link #faceCenter}: where the globs start. */
        final Vector3d globPoint;
        final Color tint;
        /** When the crosshair left the block, or 0 while it is on it. */
        long offSince;

        Lock(Vector3i block, int blockId, Vector3d faceCenter, Vector3d globPoint, Color tint) {
            this.block = block;
            this.blockId = blockId;
            this.faceCenter = faceCenter;
            this.globPoint = globPoint;
            this.tint = tint;
        }
    }

    /** One player's beam. */
    static final class State {
        final UUID id;
        Ref<EntityStore> player;
        String particleId;
        double range;
        int spawnsPerTick;
        long graceNanos;
        /** The last beam tick that fired (not one that was refused). */
        long lastTick;
        /** The last beam tick of any kind: how a release is told. */
        long lastArrival = System.nanoTime() - 10 * EXPIRY_NANOS;
        /** Set when a lock was broken; cleared by a release. */
        boolean needsRelease;
        Lock lock;
        /** The core pieces of this player's laser line, in order from the barrel. */
        final List<Ref<EntityStore>> segments = new ArrayList<>();
        /** The glow pieces, one for each core piece. */
        final List<Ref<EntityStore>> glows = new ArrayList<>();

        State(UUID id, Ref<EntityStore> player) {
            this.id = id;
            this.player = player;
        }
    }

    private static final Map<UUID, State> BEAMS = new ConcurrentHashMap<>();

    private MoltenTrail() {
    }

    static State get(UUID player) {
        return BEAMS.get(player);
    }

    /** The beam of this player, made on first use; null when the entity is not a player. */
    static State state(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> player) {
        PlayerRef ref = accessor.getComponent(player, PlayerRef.getComponentType());
        if (ref == null) {
            return null;
        }
        State state = BEAMS.computeIfAbsent(ref.getUuid(), id -> new State(id, player));
        state.player = player;
        return state;
    }

    /** Development: makes {@code owner} the owner of an entity, so that the sweeper keeps it. */
    static void debugOwn(UUID owner, Ref<EntityStore> segment) {
        BEAMS.computeIfAbsent(owner, id -> new State(id, segment)).segments.add(segment);
    }

    static void forget(UUID player) {
        BEAMS.remove(player);
    }

    /** True when this entity is one of the player's laser segments; the owner leaving or the lock ending makes it false. */
    static boolean owns(UUID player, Ref<EntityStore> segment) {
        State state = player == null ? null : BEAMS.get(player);
        return state != null && (state.segments.contains(segment) || state.glows.contains(segment));
    }

    /**
     * Notes a beam tick and says whether it must do nothing: true while a broken lock is waiting for a release. The tick
     * counts as an arrival either way (that is what tells a held button from a released one), but only a firing tick
     * counts as use of power (see {@link ArrakisPower#spend}).
     */
    static boolean refuse(State state, long now) {
        long gap = now - state.lastArrival;
        state.lastArrival = now;
        if (state.needsRelease) {
            if (gap <= RELEASE_NANOS) {
                return true;
            }
            state.needsRelease = false;
        }
        return false;
    }

    /**
     * Traces the look ray and locks onto the first block it hits within {@code range}. Null when there is none.
     */
    static Lock acquire(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> player, World world, double range) {
        Vector3i target = TargetUtil.getTargetBlock(player, range, accessor);
        if (target == null) {
            return null;
        }
        Transform look = TargetUtil.getLook(player, accessor);
        BeamMath.Entry entry = BeamMath.entry(look.getPosition(), new Vector3d(look.getDirection()).normalize(), target);
        BlockType block = world.getBlockType(target);
        Color color = block == null ? null : block.getParticleColor();
        return new Lock(new Vector3i(target), world.getBlock(target.x, target.y, target.z), entry.faceCenter(),
                BeamMath.offFace(entry, SURFACE_CLEARANCE), color == null ? FALLBACK_COLOR : color);
    }

    /** True while the locked block is still the block that was locked. */
    static boolean blockStillThere(World world, Lock lock) {
        try {
            return world.getBlock(lock.block.x, lock.block.y, lock.block.z) == lock.blockId;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Ends the lock and takes the line away. */
    static void end(CommandBuffer<EntityStore> buffer, State state) {
        state.lock = null;
        CutterLaser.remove(buffer, state);
    }

    /** Ends the lock because it broke: the gun then waits for a release before it locks onto anything. */
    static void breakLock(CommandBuffer<EntityStore> buffer, State state) {
        state.needsRelease = true;
        end(buffer, state);
    }

    /** True while the effect should keep showing: fired lately, alive, and still holding the Cutter Ray. */
    static boolean alive(Store<EntityStore> store, State state, long now) {
        Ref<EntityStore> player = state.player;
        if (!player.isValid() || now - state.lastTick > EXPIRY_NANOS
                || store.getComponent(player, DeathComponent.getComponentType()) != null) {
            return false;
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(player, InventoryComponent.Hotbar.getComponentType());
        ItemStack held = hotbar == null ? null : hotbar.getActiveItem();
        return held != null && ITEM_ID.equals(held.getItemId());
    }

    /** One world tick of a player's beam. */
    static void tick(Store<EntityStore> store, CommandBuffer<EntityStore> buffer, State state, long now) {
        Lock lock = state.lock;
        if (lock == null) {
            if (!state.segments.isEmpty() || !state.glows.isEmpty()) {
                CutterLaser.remove(buffer, state);
            }
            return;
        }
        if (!alive(store, state, now)) {
            end(buffer, state);
            return;
        }
        World world = store.getExternalData().getWorld();
        if (!blockStillThere(world, lock)) {
            breakLock(buffer, state);
            return;
        }
        Vector3i target = TargetUtil.getTargetBlock(state.player, state.range, store);
        if (lock.block.equals(target)) {
            lock.offSince = 0;
        } else if (lock.offSince == 0) {
            lock.offSince = now;
        } else if (now - lock.offSince > state.graceNanos) {
            breakLock(buffer, state);
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < state.spawnsPerTick; i++) {
            spawn(store, state.particleId + "_" + (1 + random.nextInt(MAX_GLOBS)), lock.globPoint, lock.tint);
        }
        CutterLaser.update(buffer, state);
    }

    /** Spawns a tinted particle system for the players near it (what ParticleUtil does for an untinted one). */
    private static void spawn(Store<EntityStore> store, String id, Vector3d at, Color color) {
        SpatialResource<Ref<EntityStore>, EntityStore> players = store.getResource(
                EntityModule.get().getPlayerSpatialResourceType());
        List<Ref<EntityStore>> nearby = SpatialResource.getThreadLocalReferenceList();
        players.getSpatialStructure().collect(at, VIEW_RANGE, nearby);
        ParticleUtil.spawnParticleEffect(id, at, 0f, 0f, 0f, 1f, color, nearby, store);
    }
}
