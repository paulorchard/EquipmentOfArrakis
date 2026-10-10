package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The molten effect of the Cutter Ray, drawn as a trail. The beam tick ({@link CutterBeamInteraction}, every 0.1 to
 * 0.2 s) only keeps the effect switched on with {@link #fire}. {@link MoltenTrailSystem} then runs on every world
 * tick (about 30 a second): it finds the point of the block surface under the crosshair, and spawns the particle
 * burst at that point and at the points in between it and the previous tick's point, so a moving aim paints a
 * continuous line of globs on the face instead of separate bursts from separate spots.
 *
 * <p>The engine has no line emitter, so this is the closest to following the crosshair: the resolution is one world
 * tick, with the gaps filled in a straight line.
 */
final class MoltenTrail {

    static final String ITEM_ID = "Arrakis_Cutter_Ray";
    /** The beam ticks come every 0.1 to 0.2 s while the button is held; none for this long means it was released. */
    static final long EXPIRY_NANOS = 300_000_000L;
    /** How far in front of the surface the globs start, in blocks. */
    static final double SURFACE_CLEARANCE = 0.08;
    /** A burst throws one glob; this is the largest of the systems (the systems {@code <ParticleId>_1} to {@code _n}). */
    static final int MAX_GLOBS = 1;
    /** The tint when the block has no particle colour of its own. */
    private static final Color FALLBACK_COLOR = new Color((byte) 0xff, (byte) 0xff, (byte) 0xff);
    /** Players within this many blocks of a burst get to see it (the engine's own range for particles). */
    private static final double VIEW_RANGE = 75.0;
    /** Largest jump between two aim points that is still joined by a line, in blocks; farther is a new stroke. */
    static final double MAX_LINK = 1.5;

    /** One player's effect. */
    static final class State {
        final Ref<EntityStore> player;
        String particleId;
        double range;
        double spacing;
        int spawnsPerTick;
        long lastTick;
        boolean off;
        /** The surface point of the previous world tick, or null when nothing was hit then. */
        Vector3d lastPoint;

        State(Ref<EntityStore> player) {
            this.player = player;
        }
    }

    private static final Map<UUID, State> TRAILS = new ConcurrentHashMap<>();

    private MoltenTrail() {
    }

    static Iterable<Map.Entry<UUID, State>> all() {
        return TRAILS.entrySet();
    }

    /**
     * Keeps the effect on for another 0.3 s. {@code spacing} is the largest gap between globs bursts along the trail,
     * {@code spawnsPerTick} the least number of bursts per world tick (the intensity dial: a still aim spawns all of
     * them at one point).
     */
    static void fire(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> player, String particleId, double range,
                     double spacing, int spawnsPerTick) {
        PlayerRef ref = accessor.getComponent(player, PlayerRef.getComponentType());
        if (ref == null) {
            return;
        }
        State state = TRAILS.computeIfAbsent(ref.getUuid(), id -> new State(player));
        state.particleId = particleId;
        state.range = range;
        state.spacing = spacing;
        state.spawnsPerTick = spawnsPerTick;
        state.lastTick = System.nanoTime();
        state.off = false;
    }

    /** Switches the effect off at once (power ran out). */
    static void stop(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> player) {
        PlayerRef ref = accessor.getComponent(player, PlayerRef.getComponentType());
        State state = ref == null ? null : TRAILS.get(ref.getUuid());
        if (state != null) {
            state.off = true;
        }
    }

    static void forget(UUID player) {
        TRAILS.remove(player);
    }

    /** True while the effect should keep showing: fired lately, alive, and still holding the Cutter Ray. */
    static boolean alive(Store<EntityStore> store, State state, long now) {
        Ref<EntityStore> player = state.player;
        if (state.off || !player.isValid() || now - state.lastTick > EXPIRY_NANOS
                || store.getComponent(player, DeathComponent.getComponentType()) != null) {
            return false;
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(player, InventoryComponent.Hotbar.getComponentType());
        ItemStack held = hotbar == null ? null : hotbar.getActiveItem();
        return held != null && ITEM_ID.equals(held.getItemId());
    }

    /** One world tick of the trail. */
    static void draw(Store<EntityStore> store, State state) {
        Transform look = TargetUtil.getLook(state.player, store);
        Vector3d eye = look.getPosition();
        Vector3d dir = look.getDirection().normalize();
        Vector3i target = TargetUtil.getTargetBlock(state.player, state.range, store);
        if (target == null) {
            state.lastPoint = null;
            return;
        }
        double distance = Math.max(0, BeamMath.entryDistance(eye, dir, target) - SURFACE_CLEARANCE);
        Vector3d point = new Vector3d(eye).fma(distance, dir);
        List<Vector3d> points = BeamMath.trailPoints(state.lastPoint, point, state.spacing, MAX_LINK);
        state.lastPoint = point;
        // A still aim gives one point; the minimum number of bursts is then spawned there.
        int spawns = Math.max(state.spawnsPerTick, points.size());
        Color color = tint(store, target);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < spawns; i++) {
            Vector3d at = points.get(Math.min(i, points.size() - 1));
            String id = state.particleId + "_" + (1 + random.nextInt(MAX_GLOBS));
            spawn(store, id, at, color);
        }
    }

    /** The particle colour of the block under the crosshair, so the globs look like bits of it. */
    private static Color tint(Store<EntityStore> store, Vector3i target) {
        World world = store.getExternalData().getWorld();
        BlockType block = world.getBlockType(target);
        Color color = block == null ? null : block.getParticleColor();
        return color == null ? FALLBACK_COLOR : color;
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
