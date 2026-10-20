package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.FlyMode;
import com.hypixel.hytale.protocol.MovementSettings;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** The per-player belts in memory, the module settings and the log. Pruned on disconnect and when a belt has nothing left to do. */
final class Suspensors {

    static final String TOGGLE_III = "suspensor3";
    static final String TOGGLE_II = "suspensor2";
    static final String TOGGLE_I_FAR = "suspensor1f";
    static final String TOGGLE_I_SPEED = "suspensor1s";

    private static final Map<UUID, BeltMachine> MACHINES = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_LOG = new ConcurrentHashMap<>();
    private static final Map<UUID, FallProbe> PROBES = new ConcurrentHashMap<>();
    private static final Map<UUID, ImpulseWatch> IMPULSES = new ConcurrentHashMap<>();
    private static volatile SuspensorConfig config = new SuspensorConfig();
    private static volatile HytaleLogger logger;

    private Suspensors() {
    }

    static void configure(SuspensorConfig settings, HytaleLogger log) {
        config = settings;
        logger = log;
    }

    static SuspensorConfig config() {
        return config;
    }

    static BeltMachine of(UUID player) {
        return MACHINES.computeIfAbsent(player, id -> new BeltMachine());
    }

    static BeltMachine peek(UUID player) {
        return MACHINES.get(player);
    }

    static void dropIfIdle(UUID player) {
        MACHINES.computeIfPresent(player, (id, machine) -> machine.idle() ? null : machine);
    }

    static void forget(UUID player) {
        MACHINES.remove(player);
        PROBES.remove(player);
        IMPULSES.remove(player);
        LAST_LOG.keySet().removeIf(key -> key.startsWith(player + "|"));
    }

    static FallProbe probe(UUID player) {
        return PROBES.get(player);
    }

    static void probeOn(UUID player, boolean chat) {
        PROBES.put(player, new FallProbe(chat));
    }

    static void probeOff(UUID player) {
        PROBES.remove(player);
    }

    /** One probe line: INFO in the log and, if the probe was started with chat, in the player's chat. */
    static void probeLine(com.hypixel.hytale.server.core.universe.PlayerRef player, FallProbe probe, String line) {
        HytaleLogger log = logger;
        if (log != null) {
            log.at(Level.INFO).log("Suspensor probe [%s]: %s", player.getUsername(), line);
        }
        if (probe.chat()) {
            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("probe " + line));
        }
    }

    /** An INFO line without a player. */
    static void logPlain(String text) {
        HytaleLogger log = logger;
        if (log != null) {
            log.at(Level.INFO).log("Suspensor: %s", text);
        }
    }

    /** A line for the probe, if the player has one running (the effect, the item). */
    static void probeEvent(com.hypixel.hytale.server.core.universe.PlayerRef player, String line) {
        FallProbe probe = PROBES.get(player.getUuid());
        if (probe != null) {
            probeLine(player, probe, line);
        }
    }

    static ImpulseWatch impulse(UUID player) {
        return IMPULSES.get(player);
    }

    static void impulseOn(UUID player, ImpulseWatch watch) {
        IMPULSES.put(player, watch);
    }

    static void impulseOff(UUID player) {
        IMPULSES.remove(player);
    }

    /**
     * A velocity config that does not decay the instructed velocity. By the reading of the NPC motion controller (which applies the same
     * config on the server) the resistances are per-step multipliers (the default air 0.96, ground 0.82), so 1.0 keeps the speed; the
     * Max values are used with the threshold and are set to 1.0 too, so whatever blend is applied gives 1.0.
     */
    static com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig noDecay() {
        return resistance(1.0f);
    }

    /** A config with the given resistance on the ground and in the air (and the same for the Max values). */
    static com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig resistance(float r) {
        com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig c = new com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig();
        c.setGroundResistance(r);
        c.setGroundResistanceMax(r);
        c.setAirResistance(r);
        c.setAirResistanceMax(r);
        return c;
    }

    static int count() {
        return MACHINES.size();
    }

    static BeltMachine.Speeds speeds(BeltMachine.Mark mark) {
        SuspensorConfig.Mark m = config.of(mark);
        return new BeltMachine.Speeds((float) m.horizontalSpeed(), (float) m.verticalSpeed());
    }

    /** Reads the three fly fields of the settings. */
    static BeltMachine.FlySettings read(MovementSettings settings) {
        BeltMachine.Fly mode = settings.fly == null ? BeltMachine.Fly.DISABLED : BeltMachine.Fly.valueOf(settings.fly.name().toUpperCase(java.util.Locale.ROOT));
        return new BeltMachine.FlySettings(mode, settings.horizontalFlySpeed, settings.verticalFlySpeed);
    }

    /** Writes the three fly fields into the settings (the caller pushes). */
    static void write(MovementSettings settings, BeltMachine.FlySettings fly) {
        settings.fly = switch (fly.mode()) {
            case DISABLED -> FlyMode.Disabled;
            case ALLOWED -> FlyMode.Allowed;
            case FORCED -> FlyMode.Forced;
        };
        settings.horizontalFlySpeed = fly.horizontal();
        settings.verticalFlySpeed = fly.vertical();
    }

    /** An INFO line, at most one a second per player. */
    static void log(UUID player, String name, String kind, String text) {
        HytaleLogger log = logger;
        long now = System.currentTimeMillis();
        String key = player + "|" + kind;
        Long last = LAST_LOG.get(key);
        if (log == null || (last != null && now - last < 1000)) {
            return;
        }
        LAST_LOG.put(key, now);
        log.at(Level.INFO).log("Suspensor [%s]: %s", name, text);
    }
}
