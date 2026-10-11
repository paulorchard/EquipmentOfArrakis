package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * The one place a projectile that was just created is turned into "this player shot": it looks the creator up and, if the creator is a
 * player, stamps the time on that player's {@link HoltzmanState}, which keeps their field down for the cooldown
 * ({@link HoltzmanSystem} does the rest on its next tick, and {@link HoltzmanDamageSystem} stops absorbing at once). Nothing here knows
 * a weapon: it is called by {@link ShieldShotLegacySystem} and {@link ShieldShotSystem} for every projectile entity the engine adds, and
 * by {@code /arrakis shield shot}. A creator that is not a player (a mob, a dispenser, a deployable, nobody) is ignored.
 *
 * <p>One shot that makes several projectiles needs no debounce: every one of them stamps the same time, and the field, already down after
 * the first, has nothing more to drop, so the power-down sound (played only when the field really goes down) is played once.
 */
final class ShieldShots {

    /** Development: log every projectile creation with who made it and whether that counts. */
    static volatile boolean trace;
    /** Development: this entity counts as a player too, so the hook can be run in a server with no player in it. */
    static volatile UUID testPlayer;

    private ShieldShots() {
    }

    /** True when {@code creator} is a player entity in this store (or the development stand-in). */
    static boolean isPlayer(Store<EntityStore> store, UUID creator) {
        if (creator == null) {
            return false;
        }
        if (creator.equals(testPlayer)) {
            return true;
        }
        Ref<EntityStore> who = store.getExternalData().getRefFromUUID(creator);
        return who != null && who.isValid() && store.getComponent(who, PlayerRef.getComponentType()) != null;
    }

    /**
     * A projectile of kind {@code kind} (the engine's older {@code legacy} projectile or the newer {@code standard} one) from asset
     * {@code asset} was created by {@code creator}. Returns true if it counted as a shot of a player.
     */
    static boolean record(Store<EntityStore> store, UUID creator, String kind, String asset) {
        boolean player = isPlayer(store, creator);
        if (trace) {
            EquipmentOfArrakisPlugin.get().getLogger().atInfo().log("projectile created: %s %s, creator %s, %s", kind, asset, creator,
                    player ? "a player: the field drops" : "not a player: ignored");
        }
        if (player) {
            HoltzmanState.of(creator).shot(System.nanoTime());
        }
        return player;
    }

    /** A shot with no projectile behind it: {@code /arrakis shield shot}. */
    static void shotBy(UUID player) {
        HoltzmanState.of(player).shot(System.nanoTime());
    }
}
