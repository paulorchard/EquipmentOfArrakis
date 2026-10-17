package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A short chat line to a player, from a translation key, at most one per second for the same key and player: a held click that is
 * refused every tick says it once a second, not twenty times.
 */
public final class Notice {

    static final long INTERVAL_MS = 1000;
    private static final Map<String, Long> LAST = new ConcurrentHashMap<>();

    private Notice() {
    }

    /** Pure throttle: true when a line may be sent at {@code now} given the time of the last one (null: never). */
    static boolean due(Long last, long now) {
        return last == null || now - last >= INTERVAL_MS;
    }

    /** Says the key to the player, with {name, value, name, value...} parameters; false when throttled. */
    public static boolean say(PlayerRef player, String key, String... params) {
        return say(player, key, System.currentTimeMillis(), params);
    }

    static boolean say(PlayerRef player, String key, long now, String... params) {
        if (player == null) {
            return false;
        }
        String id = player.getUuid() + "|" + key;
        if (!due(LAST.get(id), now)) {
            return false;
        }
        LAST.put(id, now);
        Message message = Message.translation(key);
        for (int i = 0; i + 1 < params.length; i += 2) {
            message = message.param(params[i], params[i + 1]);
        }
        player.sendMessage(message);
        return true;
    }

    public static void forget(UUID player) {
        LAST.keySet().removeIf(id -> id.startsWith(player + "|"));
    }
}
