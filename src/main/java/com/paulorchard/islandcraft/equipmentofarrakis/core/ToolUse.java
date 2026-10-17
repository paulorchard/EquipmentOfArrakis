package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * "A tool fired": a weapon-like tool (the Cutter Ray, one call per shot) calls {@link #fired}; whoever listens reacts (the
 * Holtzman Shield drops for a second). Tools that are not shots (Compactor, Extractor, Harvester, LitreJon) never call it.
 * World thread.
 */
public final class ToolUse {

    private static final List<Consumer<PlayerRef>> LISTENERS = new CopyOnWriteArrayList<>();

    private ToolUse() {
    }

    public static void listen(Consumer<PlayerRef> listener) {
        LISTENERS.add(listener);
    }

    public static void fired(PlayerRef player) {
        for (Consumer<PlayerRef> listener : LISTENERS) {
            try {
                listener.accept(player);
            } catch (RuntimeException e) {
                // a listener's failure must not break the shot
            }
        }
    }

    /** Removes every listener (the tests). */
    public static void clear() {
        LISTENERS.clear();
    }
}
