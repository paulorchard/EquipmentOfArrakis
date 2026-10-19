package com.paulorchard.islandcraft.equipmentofarrakis.core.worms;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import java.util.List;
import java.util.Set;

/**
 * The link to the optional Worms of Arrakis mod. {@link #api()} is the {@link WormsApi} when that plugin is loaded and its classes can
 * be used, and null otherwise. It never throws: a missing plugin, a version of it without the classes this one was built against
 * ({@link LinkageError}) or anything else wrong is "not there".
 *
 * <p>Load order is not assumed. The plugin is looked up on every call until it is found, and the API is tied to the plugin instance
 * found: if Worms is reloaded (a new instance with a new, empty totem list) the link is rebuilt, and {@link #ensureTotem} puts the id
 * back. Callers ask again now and then (the Thumper module does, every few seconds).
 */
public final class WormsLink {

    static final String GROUP = "IslandCraft";
    /** The plugin name in the Worms manifest; its javadoc writes the id as "WormsOfArrakis", so both are looked for. */
    static final List<String> NAMES = List.of("IslandCraft - Worms of Arrakis", "WormsOfArrakis");

    private static volatile PluginBase seen;
    private static volatile WormsApi cached;
    private static volatile boolean failed;

    private WormsLink() {
    }

    private static PluginBase plugin() {
        try {
            for (String name : NAMES) {
                PluginBase found = PluginManager.get().getPlugin(new PluginIdentifier(GROUP, name));
                if (found != null) {
                    return found;
                }
            }
        } catch (RuntimeException e) {
            // not there
        }
        return null;
    }

    public static boolean pluginLoaded() {
        return plugin() != null;
    }

    /** The Worms API, or null when the mod is missing or cannot be used. */
    public static WormsApi api() {
        PluginBase found = plugin();
        if (found == null) {
            seen = null;
            cached = null;
            failed = false;
            return null;
        }
        if (found != seen) {
            // first sight, or a reloaded plugin: start again
            seen = found;
            cached = null;
            failed = false;
        }
        WormsApi api = cached;
        if (api != null) {
            return api;
        }
        if (failed) {
            return null;
        }
        try {
            api = new WormsBridge();
            // Touch the Worms classes once here, inside the handler, so that a mismatch shows up now and not in the middle of a click.
            api.totemIds();
            cached = api;
            return api;
        } catch (LinkageError | RuntimeException e) {
            failed = true;
            HytaleLogger.getLogger().atWarning().log("Worms of Arrakis is loaded but cannot be used by the Thumper: %s", e);
            return null;
        }
    }

    /**
     * Makes sure {@code deployableId} is a totem in Worms. Safe to call as often as wanted. Returns a short phrase for the log when
     * something changed or is wrong, and null when nothing needed doing (already registered, or Worms is not there).
     */
    public static String ensureTotem(String deployableId) {
        WormsApi api = api();
        if (api == null) {
            return null;
        }
        try {
            if (api.totemIds().contains(deployableId)) {
                return null;
            }
            boolean added = api.registerTotem(deployableId);
            return (added ? "registered " : "could not register ") + deployableId + " as a totem; Worms reports " + api.totemIds();
        } catch (LinkageError | RuntimeException e) {
            return "PROBLEM could not register " + deployableId + " as a totem: " + e;
        }
    }

    /** Takes the id out of the Worms totems (the plugin stops). Quiet when Worms is not there. */
    public static void releaseTotem(String deployableId) {
        WormsApi api = api();
        if (api == null) {
            return;
        }
        try {
            api.unregisterTotem(deployableId);
        } catch (LinkageError | RuntimeException e) {
            HytaleLogger.getLogger().atWarning().log("Could not take %s out of the Worms totems: %s", deployableId, e);
        }
    }

    /** The sand block names Worms reports, or null when Worms is not there or does not tell. */
    public static Set<String> sandBlockNames() {
        WormsApi api = api();
        if (api == null) {
            return null;
        }
        try {
            return api.sandBlockNames();
        } catch (LinkageError | RuntimeException e) {
            return null;
        }
    }

    /** Worms' SandTolerance, or the default 1.0 when Worms is not there or does not tell. */
    public static double sandTolerance() {
        WormsApi api = api();
        if (api == null) {
            return 1.0;
        }
        try {
            Double tolerance = api.sandTolerance();
            return tolerance == null ? 1.0 : tolerance;
        } catch (LinkageError | RuntimeException e) {
            return 1.0;
        }
    }

    /** One line for the start-up report and the state command. */
    public static String status(String deployableId) {
        if (!pluginLoaded()) {
            return "Worms of Arrakis not loaded: the Thumper places, thumps and stands, and no worm comes";
        }
        WormsApi api = api();
        if (api == null) {
            return "PROBLEM Worms of Arrakis is loaded but its classes cannot be used";
        }
        try {
            List<String> ids = api.totemIds();
            return "Worms of Arrakis linked; totem ids " + ids + (ids.contains(deployableId) ? "" : " (" + deployableId + " is not among them yet)");
        } catch (LinkageError | RuntimeException e) {
            return "PROBLEM Worms of Arrakis totem list could not be read: " + e;
        }
    }
}
