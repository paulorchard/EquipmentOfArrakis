package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/**
 * The operator-only root command /arrakis. Modules attach sub-commands during setup; the plugin registers the root once, after
 * every module has been set up. No permission group is set, so only operators can run it.
 */
public final class CommandRoot {

    private final java.util.Map<String, AbstractCommandCollection> groups = new java.util.HashMap<>();

    public static final String LANG = "server.commands.arrakis.";

    private final AbstractCommandCollection collection = new AbstractCommandCollection("arrakis", LANG + "desc") {
    };

    public void add(AbstractCommand sub) {
        collection.addSubCommand(sub);
    }

    /**
     * A command group shared by several modules (for example blood: the bag adds, the extractor spawns a corpse): the first module to
     * ask creates it, the others add their sub-commands to it; {@link #command()} attaches the groups to /arrakis once every module
     * has been set up (a command cannot get sub-commands after it is attached).
     */
    public synchronized AbstractCommandCollection group(String name) {
        return groups.computeIfAbsent(name, n -> {
            AbstractCommandCollection group = new AbstractCommandCollection(n, LANG + n + ".desc") {
            };
            return group;
        });
    }

    public synchronized AbstractCommandCollection command() {
        groups.values().forEach(collection::addSubCommand);
        groups.clear();
        return collection;
    }
}
