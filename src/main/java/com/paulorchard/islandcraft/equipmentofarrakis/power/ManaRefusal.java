package com.paulorchard.islandcraft.equipmentofarrakis.power;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one place that tells a player "needs N Mana": a chat line from the translation key
 * {@code server.arrakis.needsMana}, at most one per second per player, switched by the Power setting ShowMessages.
 */
public final class ManaRefusal {

    static final long INTERVAL_MS = 1000;
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();
    static volatile boolean show = true;

    private ManaRefusal() {
    }

    /** Pure throttle: true when a line may be sent at {@code now} given the time of the last one (null: never). */
    static boolean due(Long last, long now) {
        return last == null || now - last >= INTERVAL_MS;
    }

    /** Says "needs N Mana" to the player unless throttled or switched off. Returns whether a line was sent. */
    public static boolean say(PlayerRef player, float needed) {
        return say(player, needed, System.currentTimeMillis());
    }

    static boolean say(PlayerRef player, float needed, long now) {
        if (!show || player == null || !due(LAST.get(player.getUuid()), now)) {
            return false;
        }
        LAST.put(player.getUuid(), now);
        player.sendMessage(Message.translation("server.arrakis.needsMana").param("amount", format(needed)));
        return true;
    }

    static String format(float amount) {
        return amount == Math.rint(amount) ? Long.toString((long) amount) : String.format(java.util.Locale.ROOT, "%.1f", amount);
    }

    public static void forget(UUID player) {
        LAST.remove(player);
    }
}
