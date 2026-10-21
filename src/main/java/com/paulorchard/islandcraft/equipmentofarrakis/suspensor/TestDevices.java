package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * TEST DEVICE: the adapter of {@link FlightProbe} and {@link PushRig}. Called by {@link SuspensorSystem} for a player who has either,
 * before the belt's own tick; it reads the engine and carries the rig's steps out. It never touches the belt machine. Delete with the
 * proofs (see the cleanup list in MODULES.md).
 */
final class TestDevices {

    private static final Map<UUID, FlightProbe> PROBES = new ConcurrentHashMap<>();
    private static final Map<UUID, PushRig> RIGS = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> PREVIOUS_Y = new ConcurrentHashMap<>();

    private TestDevices() {
    }

    static void probeOn(UUID player) {
        PROBES.put(player, new FlightProbe());
    }

    static void probeOff(UUID player) {
        PROBES.remove(player);
    }

    static boolean probing(UUID player) {
        return PROBES.containsKey(player);
    }

    static PushRig rig(UUID player) {
        return RIGS.computeIfAbsent(player, id -> new PushRig());
    }

    static PushRig peekRig(UUID player) {
        return RIGS.get(player);
    }

    static boolean any(UUID player) {
        return PROBES.containsKey(player) || RIGS.containsKey(player);
    }

    /** The push ends at the next tick with a CLEAR (belt off, death, respawn, world change). */
    static void cancelPush(UUID player) {
        PushRig rig = RIGS.get(player);
        if (rig != null) {
            rig.cancel();
        }
    }

    /** Disconnect: nothing is left to clear, the player is gone. */
    static void forget(UUID player) {
        PROBES.remove(player);
        RIGS.remove(player);
        PREVIOUS_Y.remove(player);
    }

    /** No downward decay: both resistances are multipliers per step, so 1.0 keeps the speed (read from the config class, see ARCHIVE.md). */
    static VelocityConfig resistance(float resistance) {
        if (Float.isNaN(resistance)) {
            return new VelocityConfig();
        }
        VelocityConfig config = new VelocityConfig();
        config.setGroundResistance(resistance);
        config.setGroundResistanceMax(resistance);
        config.setAirResistance(resistance);
        config.setAirResistanceMax(resistance);
        return config;
    }

    static void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> buffer, PlayerRef player, Ref<EntityStore> ref,
                     MovementStates states, BeltMachine machine) {
        UUID id = player.getUuid();
        FlightProbe probe = PROBES.get(id);
        PushRig rig = RIGS.get(id);
        if (probe == null && rig == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Velocity velocity = chunk.getComponent(index, Velocity.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        double y = transform == null ? 0 : transform.getPosition().y;
        Double before = PREVIOUS_Y.put(id, y);
        double actualVy = before == null || dt <= 0 ? 0 : (y - before) / dt;
        double clientVy = velocity == null ? 0 : velocity.getClientVelocity().y;
        Power power = Power.get();
        float mana = power == null ? -1f : power.get(buffer, ref);
        if (probe != null) {
            long tick = buffer.getExternalData().getWorld().getTick();
            List<String> lines = probe.observe(new FlightProbe.Sample(tick, now, dt, states.flying, states.jumping, states.crouching, states.onGround,
                    states.falling, states.fallingFar, y, clientVy, wish(chunk.getComponent(index, PlayerInput.getComponentType())), mana,
                    machine != null && machine.armed(), gateText(machine)));
            for (String line : lines) {
                Suspensors.logTest(player.getUsername(), "probe", line);
            }
        }
        if (rig != null) {
            for (PushRig.Step step : rig.tick(states.flying, machine != null && machine.isOn(), now, clientVy, actualVy, states.onGround)) {
                switch (step.kind()) {
                    case SEND -> {
                        if (velocity != null) {
                            velocity.addInstruction(new Vector3d(0, step.y(), 0), resistance(step.resistance()), ChangeVelocityType.Set);
                        }
                    }
                    case CLEAR -> {
                        if (velocity != null) {
                            velocity.addInstruction(new Vector3d(0, 0, 0), new VelocityConfig(), ChangeVelocityType.Set);
                        }
                    }
                    case LOG -> Suspensors.logTest(player.getUsername(), "push", step.text());
                }
            }
            if (!rig.active()) {
                RIGS.remove(id, rig);
            }
        }
        if (probe == null && RIGS.get(id) == null) {
            PREVIOUS_Y.remove(id);
        }
    }

    /** The gate as the probe prints it at a flight start: the state the client had (the machine has not ticked yet), the mode and the last window. */
    static String gateText(BeltMachine machine) {
        BeltMachine.Gate gate = Suspensors.gate();
        if (machine == null) {
            return "n/a (no belt)";
        }
        if (gate.off()) {
            return "off (TapWindow 0, fly Allowed whenever armed)";
        }
        BeltMachine.GateInfo info = machine.gate();
        return (info.open() ? "OPEN" : "CLOSED") + " (" + (gate.mode() == BeltMachine.GateMode.AFTER_JUMP ? "afterJump" : "groundOpen") + ", window " + gate.window()
                + " s); last window: jump tick " + info.jumpTick() + " ms " + info.jumpMs() + " -> push tick " + info.openTick() + " ms " + info.openMs() + " (+"
                + (info.openTick() - info.jumpTick()) + " ticks, +" + (info.openMs() - info.jumpMs()) + " ms)";
    }

    /**
     * What the server holds of the client's wish movement: the newest {@code WishMovement} still in the player's input queue. The queue is
     * the server's copy of what the client reported and may already be consumed when this system runs; the text says which.
     */
    static String wish(PlayerInput input) {
        if (input == null) {
            return "n/a (no PlayerInput component)";
        }
        List<PlayerInput.InputUpdate> queue = input.getMovementUpdateQueue();
        if (queue == null || queue.isEmpty()) {
            return "n/a (input queue empty at this tick)";
        }
        PlayerInput.WishMovement found = null;
        for (PlayerInput.InputUpdate update : queue) {
            if (update instanceof PlayerInput.WishMovement w) {
                found = w;
            }
        }
        if (found == null) {
            return "n/a (queue of " + queue.size() + " has no WishMovement)";
        }
        return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", found.getX(), found.getY(), found.getZ());
    }
}
