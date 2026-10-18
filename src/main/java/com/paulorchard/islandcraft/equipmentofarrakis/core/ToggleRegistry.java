package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

    /** What a press did: nothing known by that id, ignored as too soon after the last press, or the new state. */
    public enum Outcome {
        UNKNOWN, DEBOUNCED, ON, OFF
    }

    private final Map<String, Long> lastPress = new HashMap<>();

    /**
     * The one way a player flips a toggle: the picker page clicks it (no debounce) and the Ability4 key reaches it through the
     * {@code Arrakis_Toggle} interaction (a short debounce, so a held key does not flicker it). A press of an id nobody registered
     * (its module is off) does nothing.
     */
    public Outcome press(String id, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, long nowNanos, long minGapNanos) {
        return pressWith(id, player.getUuid(), nowNanos, minGapNanos, on -> find(id).on().set(store, ref, player, on));
    }

    /** {@link #press} with the switching left to {@code apply}, which gets the new state (the tests). */
    Outcome pressWith(String id, UUID player, long nowNanos, long minGapNanos, java.util.function.Consumer<Boolean> apply) {
        Toggle toggle = find(id);
        if (toggle == null) {
            return Outcome.UNKNOWN;
        }
        synchronized (this) {
            String key = player + "/" + id;
            Long last = lastPress.get(key);
            if (last != null && minGapNanos > 0 && nowNanos - last < minGapNanos) {
                return Outcome.DEBOUNCED;
            }
            if (minGapNanos > 0) {
                lastPress.put(key, nowNanos);
            }
        }
        boolean now = !toggle.isOn(player);
        apply.accept(now);
        return now ? Outcome.ON : Outcome.OFF;
    }

    /** The player is gone: every toggle forgets them. A failing hook does not stop the others. */
    public void forget(UUID player) {
        synchronized (this) {
            lastPress.keySet().removeIf(key -> key.startsWith(player + "/"));
        }
        for (Toggle toggle : all()) {
            try {
                toggle.reset().forget(player);
            } catch (RuntimeException e) {
                // one module's reset must not keep another's from running
            }
        }
    }
}
