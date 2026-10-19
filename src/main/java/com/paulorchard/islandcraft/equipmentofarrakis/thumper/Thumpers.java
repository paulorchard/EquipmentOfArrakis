package com.paulorchard.islandcraft.equipmentofarrakis.thumper;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.paulorchard.islandcraft.equipmentofarrakis.core.worms.WormsLink;

import java.util.Set;
import java.util.logging.Level;

/** What the parts of the thumper module share: the settings, the logger, the book of standing Thumpers and the sand set. */
final class Thumpers {

    /** The config id of the deployable (the Id in the Config of the placement interaction); also the item id. */
    static final String ID = "Arrakis_Thumper";
    static final String THUMP_SOUND = "SFX_Arrakis_Thumper_Thump";
    /** Worms SandTolerance when Worms does not say (its default). */
    static final double DEFAULT_SAND_TOLERANCE = 1.0;

    private static volatile ThumperConfig config = new ThumperConfig();
    private static volatile HytaleLogger logger;
    private static volatile ThumperBook book = new ThumperBook(1.0, 0.25);

    private Thumpers() {
    }

    static void configure(ThumperConfig settings) {
        config = settings == null ? new ThumperConfig() : settings;
        book = new ThumperBook(config.beatSeconds(), config.doubleThumpGapSeconds());
    }

    static void logger(HytaleLogger log) {
        logger = log;
    }

    static ThumperConfig config() {
        return config;
    }

    static ThumperBook book() {
        return book;
    }

    static void log(String line) {
        HytaleLogger log = logger;
        if (log != null) {
            log.at(line.contains("PROBLEM") ? Level.WARNING : Level.INFO).log("%s", line);
        }
    }

    static void warn(String line) {
        HytaleLogger log = logger;
        if (log != null) {
            log.at(Level.WARNING).log("%s", line);
        }
    }

    /** The block names that count as sand now (read on use: assets are loaded and Worms may have come and gone). */
    static Set<String> sand() {
        boolean dunes = BlockType.getAssetMap().getIndex(ThumperRules.DUNES_SAND) != Integer.MIN_VALUE;
        return ThumperRules.resolveSand(config.sandBlocks(), WormsLink.sandBlockNames(), dunes);
    }
}
