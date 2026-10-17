package com.paulorchard.islandcraft.equipmentofarrakis.shield;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import java.util.logging.Level;

/**
 * The one place a shot is turned into "this player shot": it stamps the time on the player {@link HoltzmanState}, which keeps their
 * field down for the cooldown ({@link HoltzmanSystem} does the rest on its next tick, and {@link HoltzmanDamageSystem} stops absorbing at
 * once). Three callers: {@link ShieldShotLegacySystem} and {@link ShieldShotSystem} for every projectile entity the engine adds (guns,
 * bows, thrown items, staffs), the {@code ToolUse.fired} hook (each Cutter Ray shot, which launches no projectile) and
 * {@code /arrakis shield shot}. A creator that is not a player (a mob, a dispenser, nobody) is ignored.
 *
 * <p>One shot that makes several projectiles needs no debounce: every one of them stamps the same time, and the field, already down after
 * the first, has nothing more to drop, so the power-down sound (played only when the field really goes down) is played once.
 */
final class ShieldShots {

    /** Development: log every projectile creation with who made it and whether that counts. */
    static volatile boolean trace;

    private ShieldShots() {
    }

    /** The player ref of a creator entity UUID in this store, or null when the creator is not a player. */
    static PlayerRef playerOf(Store<EntityStore> store, UUID creator) {
        if (creator == null) {
            return null;
        }
        Ref<EntityStore> who = store.getExternalData().getRefFromUUID(creator);
        return who != null && who.isValid() ? store.getComponent(who, PlayerRef.getComponentType()) : null;
    }

    /**
     * A projectile of kind {@code kind} (the engine older {@code legacy} projectile or the newer {@code standard} one) from asset
     * {@code asset} was created by {@code creator}. Returns true if it counted as a shot of a player.
     */
    static boolean record(Store<EntityStore> store, UUID creator, String kind, String asset) {
        PlayerRef player = playerOf(store, creator);
        if (trace) {
            ShieldModule.logger().at(Level.INFO).log("projectile created: %s %s, creator %s, %s", kind, asset, creator,
                    player != null ? "a player: the field drops" : "not a player: ignored");
        }
        if (player != null) {
            HoltzmanState.of(player.getUuid()).shot(System.nanoTime());
        }
        return player != null;
    }

    /** A shot with no projectile behind it: the Cutter Ray through ToolUse, or {@code /arrakis shield shot}. */
    static void shotBy(UUID player) {
        HoltzmanState.of(player).shot(System.nanoTime());
    }
}
