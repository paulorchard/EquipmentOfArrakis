package com.paulorchard.islandcraft.equipmentofarrakis.litrejon;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.PluginManager;

/**
 * The link to the optional Water of Arrakis mod. {@link #api()} is the {@link WaterApi} when that plugin is loaded and its classes can
 * be used, and null otherwise. It never throws: a missing plugin, a version of it without the classes this one was built against
 * ({@link LinkageError}) or anything else wrong is "not there", and the LitreJon then says "Water of Arrakis is needed".
 */
final class WaterLink {

    static final String GROUP = "IslandCraft";
    static final String NAME = "IslandCraft - Water of Arrakis";

    private static volatile WaterApi cached;
    private static volatile boolean failed;

    private WaterLink() {
    }

    static boolean pluginLoaded() {
        try {
            return PluginManager.get().getPlugin(new PluginIdentifier(GROUP, NAME)) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The Water API, or null when the mod is missing or cannot be used. */
    static WaterApi api() {
        WaterApi api = cached;
        if (api != null) {
            return api;
        }
        if (failed || !pluginLoaded()) {
            return null;
        }
        try {
            api = new WaterBridge();
            // Touch the Water classes once here, inside the handler, so that a mismatch shows up now and not in the middle of a click.
            api.max();
            cached = api;
            return api;
        } catch (LinkageError | RuntimeException e) {
            failed = true;
            HytaleLogger.getLogger().atWarning().log("Water of Arrakis is loaded but cannot be used by the LitreJon: %s", e);
            return null;
        }
    }

    /** One line for the start-up report. */
    static String status() {
        if (!pluginLoaded()) {
            return "Water of Arrakis not loaded: the LitreJon will refuse to pour (everything else works)";
        }
        return api() == null ? "PROBLEM Water of Arrakis is loaded but its classes cannot be used" : "Water of Arrakis linked";
    }
}
