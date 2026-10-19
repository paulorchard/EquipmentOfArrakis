// EXPERIMENT: delete with the lab module
package com.paulorchard.islandcraft.equipmentofarrakis.lab;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.logging.Level;

/** The settings and the logger of the lab. */
final class Labs {

    private static volatile LabConfig config = new LabConfig();
    private static volatile HytaleLogger logger;

    private Labs() {
    }

    static void configure(LabConfig settings, HytaleLogger log) {
        config = settings == null ? new LabConfig() : settings;
        logger = log;
    }

    static LabConfig config() {
        return config;
    }

    static void log(String line) {
        HytaleLogger log = logger;
        if (log != null) {
            log.at(Level.INFO).log("lab %s", line);
        }
    }
}
