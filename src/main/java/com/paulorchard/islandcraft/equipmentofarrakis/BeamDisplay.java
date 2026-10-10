package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the Cutter Ray beam looks like is the {@code Arrakis_Beam} entity stat: 0 is off, 1 to 9 is a length step. The
 * Cutter Ray item lists a model for each value (ItemAppearanceConditions), so the client swaps the gun model for the
 * same gun with a red box on the muzzle. The box is part of the model, so it moves with the gun on the client with
 * no delay; the server only chooses its length.
 *
 * <p>{@link #show} is called by each beam tick. The ticks stop when the button is released, the power runs out, the
 * player switches item or dies, and then nothing calls {@link #hide}, so {@link BeamExpirySystem} switches the stat
 * off once no tick has refreshed it for {@link #EXPIRY_NANOS}.
 */
final class BeamDisplay {

    static final String STAT_ID = "Arrakis_Beam";
    /** A little longer than the 0.1 s between beam ticks, so a late tick does not make the beam flicker. */
    static final long EXPIRY_NANOS = 150_000_000L;

    private static final Map<UUID, Long> LAST_SHOWN = new ConcurrentHashMap<>();

    private BeamDisplay() {
    }

    static int statIndex() {
        return EntityStatType.getAssetMap().getIndex(STAT_ID);
    }

    static void show(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, int step) {
        set(accessor, ref, step);
        PlayerRef player = accessor.getComponent(ref, PlayerRef.getComponentType());
        if (player != null) {
            LAST_SHOWN.put(player.getUuid(), System.nanoTime());
        }
    }

    static void hide(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        set(accessor, ref, 0);
    }

    /** True when the beam of this player has not been refreshed for too long (or never has since the server started). */
    static boolean expired(UUID player, long now) {
        Long last = LAST_SHOWN.get(player);
        return last == null || now - last > EXPIRY_NANOS;
    }

    static void forget(UUID player) {
        LAST_SHOWN.remove(player);
    }

    private static void set(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, int step) {
        int index = statIndex();
        EntityStatMap stats = index == Integer.MIN_VALUE ? null : accessor.getComponent(ref, EntityStatMap.getComponentType());
        if (stats != null && stats.get(index) != null && (int) stats.get(index).get() != step) {
            stats.setStatValue(index, step);
        }
    }
}
