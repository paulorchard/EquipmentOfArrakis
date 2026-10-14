// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
package com.paulorchard.islandcraft.equipmentofarrakis.experiments;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.EntityPart;
import com.hypixel.hytale.protocol.ModelParticle;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.packets.entities.SpawnModelParticles;
import com.hypixel.hytale.protocol.packets.world.CancelParticleSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * The live calibration rigs of one player each, all off by default (see EXPERIMENT_LOG.md, "How to calibrate"):
 * <ul>
 *   <li><b>collapse</b>: dots in world space along the server's look ray (TargetUtil.getLook), 1 to 12 blocks. If that ray is the
 *       camera's, they all hide behind the crosshair dot.</li>
 *   <li><b>target</b>: a magenta cross where the server thinks the crosshair lands: the block entry point of the look ray, or the
 *       point at the apex distance when nothing is hit.</li>
 *   <li><b>nodes</b>: a ruler of dots along the local Z axis of each named model node, one colour each (SpawnModelParticles).</li>
 *   <li><b>beam</b>: a fine straight stream or the NatureBeam itself, from the fp or tp numbers (SpawnModelParticles).</li>
 * </ul>
 * Everything is cancelled when a rig is switched off, when the player disconnects or dies, and when the player's entity is not valid
 * any more (world change). A single thread ticks every 100 ms and hops to the world thread for the work.
 */
final class ExpCalibrationRig {

    static final String DOT = "Arrakis_Exp_Cal_Dot";
    static final String MARK = "Arrakis_Exp_Cal_Mark";
    static final String RULER = "Arrakis_Exp_Cal_Ruler";
    static final String STREAM = "Arrakis_Exp_Cal_Stream";
    static final String NATURE_BEAM = "NatureBeam";
    /** Blocks in front of the NatureBeam system's origin where its end burst sits (measure_nature_beam.js). */
    static final double NATURE_END_OFFSET = 1.8;

    /** The colours of the rulers, in the order the nodes are named. */
    static final String[][] COLOURS = {
            {"red", "ff3030"}, {"green", "30ff40"}, {"blue", "4070ff"}, {"yellow", "ffe030"}, {"cyan", "30f0ff"},
            {"magenta", "ff40ff"}, {"orange", "ff9020"}, {"white", "ffffff"}, {"pink", "ff90b0"}, {"lime", "b0ff30"}};

    static final String[] DEFAULT_NODES = {"Head", "Neck", "Chest", "Body", "Belly", "Pelvis", "R-Hand", "R-Attachment", "Origin"};

    /** How the packet's rotation relates to degrees: 1 when the engine takes the JSON numbers as they are, pi/180 when it takes radians. */
    static volatile double angleUnit = 1;

    enum Beam {OFF, STREAM, NATURE, WORLD_STREAM, WORLD_NATURE}

    /** What one player has switched on, and the numbers. */
    static final class State {
        final PlayerRef player;
        final World world;
        ExpCalibration calibration;
        boolean collapse;
        boolean target;
        Beam beam = Beam.OFF;
        /** Which numbers the live beam uses: "fp", "tp" or "both". */
        String view = "fp";
        List<String> nodes = new ArrayList<>();
        long lastSlow;
        boolean spawnedSlow;

        State(PlayerRef player, World world, ExpCalibration calibration) {
            this.player = player;
            this.world = world;
            this.calibration = calibration;
        }

        boolean anyOn() {
            return collapse || target || beam != Beam.OFF || !nodes.isEmpty();
        }
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static ScheduledExecutorService ticker;

    private ExpCalibrationRig() {
    }

    /** The state of this player, made on first use with the given starting numbers. */
    static State state(PlayerRef player, World world, ExpCalibration starting) {
        State state = STATES.computeIfAbsent(player.getUuid(), id -> new State(player, world, starting.copy()));
        ensureTicker();
        return state;
    }

    static State existing(UUID id) {
        return STATES.get(id);
    }

    private static synchronized void ensureTicker() {
        if (ticker == null) {
            ticker = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "arrakis-exp-calibration");
                t.setDaemon(true);
                return t;
            });
            ticker.scheduleAtFixedRate(ExpCalibrationRig::tickAll, 100, 100, TimeUnit.MILLISECONDS);
        }
    }

    private static void tickAll() {
        try {
            for (State state : STATES.values()) {
                if (state.anyOn()) {
                    state.world.execute(() -> tick(state));
                }
            }
        } catch (RuntimeException e) {
            ExperimentsModule.logger().at(Level.WARNING).withCause(e).log("Experiments calibration tick failed");
        }
    }

    /** Disconnect: stop everything and forget the player. Safe on any thread. */
    static void forget(UUID id) {
        State state = STATES.remove(id);
        if (state != null) {
            state.collapse = false;
            state.target = false;
            state.beam = Beam.OFF;
            state.nodes.clear();
            state.world.execute(() -> cancelAll(state));
        }
    }

    // ---- the tick, on the world thread

    private static void tick(State state) {
        Ref<EntityStore> ref = state.player.getReference();
        if (ref == null || !ref.isValid()) {
            // The entity is gone (world change or removal): the rigs cannot work any more and there is nothing to cancel it on.
            STATES.remove(state.player.getUuid());
            return;
        }
        Store<EntityStore> store = ref.getStore();
        if (store.getComponent(ref, DeathComponent.getComponentType()) != null) {
            switchAllOff(state);
            return;
        }
        Transform look = TargetUtil.getLook(ref, store);
        Vector3d eye = new Vector3d(look.getPosition());
        Vector3d dir = new Vector3d(look.getDirection()).normalize();
        if (state.collapse) {
            for (double d = 1; d <= 12; d += 0.5) {
                ParticleUtil.spawnParticleEffect(DOT, new Vector3d(eye).fma(d, dir), store);
            }
        }
        if (state.target) {
            ParticleUtil.spawnParticleEffect(MARK, targetPoint(state, ref, store, eye, dir), store);
        }
        if (state.beam == Beam.WORLD_STREAM || state.beam == Beam.WORLD_NATURE) {
            spawnWorldBeam(state, ref, store);
        }
        long now = System.nanoTime();
        if (now - state.lastSlow > 400_000_000L) {
            state.lastSlow = now;
            if (state.beam == Beam.STREAM || state.beam == Beam.NATURE) {
                spawnBeam(state, ref, store, eye);
            }
            if (!state.nodes.isEmpty()) {
                spawnRulers(state, ref, store);
            }
        }
    }

    /** The world-space beam: starts at the cam numbers in the camera frame and ends at the look target, every tick. */
    private static void spawnWorldBeam(State state, Ref<EntityStore> ref, Store<EntityStore> store) {
        ExpCalibration c = state.calibration;
        ExpBeamGeometry.Plan plan = ExpBeamGeometry.plan(ref, store, c.useBody, c.start().x, c.start().y, c.start().z, c.apex, c.endAtBlock);
        if (state.beam == Beam.WORLD_NATURE) {
            Vector3d origin = new Vector3d(plan.end).fma(-NATURE_END_OFFSET, plan.direction);
            ExpBeamGeometry.spawn(state.world, NATURE_BEAM, origin, plan, (float) c.start().scale, 0.14f);
            ExpBeamGeometry.spawnGlow(state.world, ExpBeamSpawnInteraction.GLOW_PREFIX, ExpBeamSpawnInteraction.GLOW_LEVELS, plan,
                    System.currentTimeMillis() / 1000.0, c.pulseHz, (float) c.start().scale, 0.14f);
        } else {
            ExpBeamGeometry.spawn(state.world, STREAM, plan.start, plan, (float) c.start().scale, 0.14f);
        }
    }

    /** Where the server believes the crosshair lands: the block entry point of the look ray, else the apex distance. */
    static Vector3d targetPoint(State state, Ref<EntityStore> ref, Store<EntityStore> store, Vector3d eye, Vector3d dir) {
        double apex = state.calibration.apex;
        Vector3i block = TargetUtil.getTargetBlock(ref, Math.max(apex, 1) + 2, store);
        if (block != null) {
            double d = ExpAim.entryDistance(eye.x, eye.y, eye.z, dir.x, dir.y, dir.z, block.x, block.y, block.z);
            if (!Double.isNaN(d)) {
                return new Vector3d(eye).fma(d, dir);
            }
        }
        return new Vector3d(eye).fma(apex, dir);
    }

    private static void spawnBeam(State state, Ref<EntityStore> ref, Store<EntityStore> store, Vector3d eye) {
        NetworkId id = store.getComponent(ref, NetworkId.getComponentType());
        if (id == null) {
            return;
        }
        String system = state.beam == Beam.NATURE ? NATURE_BEAM : STREAM;
        List<ModelParticle> particles = new ArrayList<>();
        if (!state.view.equals("tp")) {
            particles.add(modelParticle(system, state.calibration.fp, null));
        }
        if (!state.view.equals("fp")) {
            particles.add(modelParticle(system, state.calibration.tp, null));
        }
        if (state.beam == Beam.NATURE) {
            // The NatureBeam has no life span of its own: cancel the last one around the player before the next.
            cancelAround(state, eye, NATURE_BEAM, false);
        }
        send(state, new SpawnModelParticles(id.getId(), particles.toArray(new ModelParticle[0])));
    }

    private static void spawnRulers(State state, Ref<EntityStore> ref, Store<EntityStore> store) {
        NetworkId id = store.getComponent(ref, NetworkId.getComponentType());
        if (id == null) {
            return;
        }
        List<ModelParticle> particles = new ArrayList<>();
        for (int i = 0; i < state.nodes.size(); i++) {
            String[] colour = COLOURS[i % COLOURS.length];
            ModelParticle p = new ModelParticle();
            p.systemId = RULER;
            p.scale = 1;
            p.color = hex(colour[1]);
            p.targetEntityPart = EntityPart.Entity;
            p.targetNodeName = state.nodes.get(i);
            p.positionOffset = new Vector3f(0, 0, 0);
            p.rotationOffset = new Direction(0, 0, 0);
            p.detachedFromModel = false;
            p.clearParticlesOnRemove = true;
            particles.add(p);
        }
        send(state, new SpawnModelParticles(id.getId(), particles.toArray(new ModelParticle[0])));
    }

    /** The protocol particle for one set of numbers; {@code color} may be null. */
    static ModelParticle modelParticle(String system, ExpCalibration.View v, Color color) {
        ModelParticle p = new ModelParticle();
        p.systemId = system;
        p.scale = (float) v.scale;
        p.color = color;
        p.targetEntityPart = EntityPart.Entity;
        p.targetNodeName = v.node;
        p.positionOffset = new Vector3f((float) v.x, (float) v.y, (float) v.z);
        double unit = angleUnit;
        p.rotationOffset = new Direction((float) (v.yaw * unit), (float) (v.pitch * unit), (float) (v.roll * unit));
        p.detachedFromModel = v.detached;
        p.clearParticlesOnRemove = true;
        return p;
    }

    static Color hex(String rrggbb) {
        int rgb = Integer.parseInt(rrggbb, 16);
        return new Color((byte) (rgb >> 16), (byte) (rgb >> 8), (byte) rgb);
    }

    private static void send(State state, SpawnModelParticles packet) {
        state.player.getPacketHandler().writeNoCache(packet);
    }

    private static void cancelAround(State state, Vector3d at, String system, boolean instant) {
        double r = 24;
        state.player.getPacketHandler().writeNoCache(new CancelParticleSystems(new Position(at.x - r, at.y - r, at.z - r),
                new Position(at.x + r, at.y + r, at.z + r), new String[] {system}, instant));
    }

    /** Switches every rig off and cancels what is on the screen. World thread. */
    static void switchAllOff(State state) {
        state.collapse = false;
        state.target = false;
        state.beam = Beam.OFF;
        state.nodes.clear();
        cancelAll(state);
    }

    /** Nodes off: the rulers are cancelled. World thread. */
    static void switchAllOffNodes(State state) {
        state.nodes.clear();
        Ref<EntityStore> ref = state.player.getReference();
        if (ref != null && ref.isValid()) {
            Transform look = TargetUtil.getLook(ref, ref.getStore());
            cancelAround(state, new Vector3d(look.getPosition()), RULER, true);
        }
    }

    /** Cancels the calibration particle systems around the player. World thread. */
    static void cancelAll(State state) {
        Ref<EntityStore> ref = state.player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        Transform look = TargetUtil.getLook(ref, store);
        Vector3d at = new Vector3d(look.getPosition());
        for (String system : new String[] {DOT, MARK, RULER, STREAM, NATURE_BEAM}) {
            cancelAround(state, at, system, true);
        }
    }
}
