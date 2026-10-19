// EXPERIMENT: delete with the lab module
package com.paulorchard.islandcraft.equipmentofarrakis.lab;

import com.hypixel.hytale.protocol.MovementSettings;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** What the lab remembers about each player who uses it. Nothing is saved. */
final class LabState {

    /** One player's lab state. Touched on the world thread only. */
    static final class PlayerLab {
        double watchSeconds;          // 0 = off
        long nextWatch;
        Boolean lastJumping, lastFlying, lastGliding;
        Vector3d lastPos;
        long lastPosNanos;
        int beltMode;                 // 0 off, 2 or 3
        boolean beltOn;
        MovementSettings saved;       // the settings before the belt took over
        double clampMax;              // 0 = off, else blocks a second
        boolean flagSent;             // a SetMovementStates with flying was sent
        boolean pendingReset;
        String world;
        // the drop and landing report
        boolean dropTracking, airborne;
        double minVy;
        float health0;
        double fromY;
    }

    private static final Map<UUID, PlayerLab> PLAYERS = new ConcurrentHashMap<>();

    private LabState() {
    }

    static PlayerLab of(UUID player) {
        return PLAYERS.computeIfAbsent(player, k -> new PlayerLab());
    }

    static PlayerLab peek(UUID player) {
        return PLAYERS.get(player);
    }

    static void forget(UUID player) {
        PLAYERS.remove(player);
    }
}
