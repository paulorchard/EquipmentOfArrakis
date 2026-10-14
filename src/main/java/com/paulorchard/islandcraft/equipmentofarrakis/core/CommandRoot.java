package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/**
 * The operator-only root command /arrakis. Modules attach sub-commands during setup; the plugin registers the root once, after
 * every module has been set up. No permission group is set, so only operators can run it.
 */
public final class CommandRoot {

    public static final String LANG = "server.commands.arrakis.";

    private final AbstractCommandCollection collection = new AbstractCommandCollection("arrakis", LANG + "desc") {
    };

    public void add(AbstractCommand sub) {
        collection.addSubCommand(sub);
    }

    public AbstractCommandCollection command() {
        return collection;
    }
}
