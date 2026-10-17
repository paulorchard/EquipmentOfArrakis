package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The passive toggles of the picker page (the Holtzman Shield, the Personal Light): no item, always listed. A module registers
 * one with its callbacks and keeps the per-player state itself, in memory; {@link #forget} calls the reset hooks when a player
 * disconnects.
 */
public final class ToggleRegistry {

    private static final ToggleRegistry INSTANCE = new ToggleRegistry();

    /** Switches the toggle. Runs on the world thread. */
    @FunctionalInterface
    public interface Switch {
        void set(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, boolean on);
    }

    /** Reads the state of a player. */
    @FunctionalInterface
    public interface Reader {
        boolean isOn(UUID player);
    }

    /** Forgets a player (disconnect). */
    @FunctionalInterface
    public interface Reset {
        void forget(UUID player);
    }

    /** One toggle: id, display order, label key, the vanilla item whose icon is shown, and the callbacks. */
    public record Toggle(String id, int order, String labelKey, String iconItemId, Switch on, Reader reader, Reset reset) {

        public boolean isOn(UUID player) {
            return reader.isOn(player);
        }
    }

    private final Map<String, Toggle> toggles = new LinkedHashMap<>();

    public static ToggleRegistry get() {
        return INSTANCE;
    }

    /** A registry of its own (the tests). */
    public ToggleRegistry() {
    }

    public synchronized void register(Toggle toggle) {
        toggles.put(toggle.id(), toggle);
    }

    public synchronized Toggle find(String id) {
        return toggles.get(id);
    }

    /** Every toggle by display order, then by id. */
    public synchronized List<Toggle> all() {
        List<Toggle> list = new ArrayList<>(toggles.values());
        list.sort(Comparator.comparingInt(Toggle::order).thenComparing(Toggle::id));
        return list;
    }

    /** Flips a toggle for a player and returns the new state; false for an unknown id. */
    public boolean flip(String id, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player) {
        Toggle toggle = find(id);
        if (toggle == null) {
            return false;
        }
        boolean now = !toggle.isOn(player.getUuid());
        toggle.on().set(store, ref, player, now);
        return now;
    }

    /** The player is gone: every toggle forgets them. A failing hook does not stop the others. */
    public void forget(UUID player) {
        for (Toggle toggle : all()) {
            try {
                toggle.reset().forget(player);
            } catch (RuntimeException e) {
                // one module's reset must not keep another's from running
            }
        }
    }
}
