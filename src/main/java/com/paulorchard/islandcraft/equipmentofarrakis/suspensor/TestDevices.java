package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TEST DEVICE: the adapter of {@link FlightProbe}. Called by {@link SuspensorSystem} for a player who has the probe on, before the belt's
 * own tick; it reads the engine and logs. It never touches the belt machine. It is how the descent is tuned; delete it with the probe
 * (see the cleanup list in MODULES.md).
 */
final class TestDevices {

    private static final Map<UUID, FlightProbe> PROBES = new ConcurrentHashMap<>();

    private TestDevices() {
    }

    static void probeOn(UUID player) {
        PROBES.put(player, new FlightProbe());
    }

    static void probeOff(UUID player) {
        PROBES.remove(player);
    }

    static boolean any(UUID player) {
        return PROBES.containsKey(player);
    }

    /** Disconnect: the player is gone. */
    static void forget(UUID player) {
        PROBES.remove(player);
    }

    static void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> buffer, PlayerRef player, Ref<EntityStore> ref,
                     MovementStates states, BeltMachine machine) {
        FlightProbe probe = PROBES.get(player.getUuid());
        if (probe == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Velocity velocity = chunk.getComponent(index, Velocity.getComponentType());
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        double y = transform == null ? 0 : transform.getPosition().y;
        double clientVy = velocity == null ? 0 : velocity.getClientVelocity().y;
        Power power = Power.get();
        float mana = power == null ? -1f : power.get(buffer, ref);
        long tick = buffer.getExternalData().getWorld().getTick();
        List<String> lines = probe.observe(new FlightProbe.Sample(tick, now, dt, states.flying, states.jumping, states.crouching, states.onGround,
                states.falling, states.fallingFar, y, clientVy, wish(chunk.getComponent(index, PlayerInput.getComponentType())), mana,
                machine != null && machine.armed(), gateText(machine), descentText(machine)));
        for (String line : lines) {
            Suspensors.logTest(player.getUsername(), "probe", line);
        }
    }

    /** The descent as the probe prints it on its sample line: the state, the player's own vertical speed, the sends and the ground exits. */
    static String descentText(BeltMachine machine) {
        if (machine == null) {
            return "n/a (no belt)";
        }
        if (Suspensors.descent().off()) {
            return "off (DescentSpeed 0)";
        }
        BeltMachine.DescentInfo info = machine.descent();
        return String.format(Locale.ROOT, "%s own %.2f vy %.2f push %s sends %d (refresh %d, drift %d) transitions %d exits %d", info.state(), info.own(), info.vy(),
                info.pushInForce() ? "in force" : "none", info.sends(), info.refreshes(), info.drifts(), info.transitions(), info.groundExits());
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
     * What the server holds of the client's wish movement: the newest {@code WishMovement} still in the player's input queue. The log of
     * 2026-10-21 shows the queue is empty by the time this system runs, so the text says so; the descent does not use it.
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
