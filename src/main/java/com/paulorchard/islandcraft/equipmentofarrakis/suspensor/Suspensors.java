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

    private static final Map<UUID, BeltMachine> MACHINES = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_LOG = new ConcurrentHashMap<>();
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
        LAST_LOG.keySet().removeIf(key -> key.startsWith(player + "|"));
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
