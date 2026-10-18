package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.function.Function;

/**
 * What a module may touch: the plugin, the logger, its section of the config, and ways to register commands and systems,
 * wrapped once so the engine registries are not spread through the modules.
 */
public final class ModuleContext {

    private final JavaPlugin plugin;
    private final CommandRoot root;
    private final Function<String, Object> sections;
    private final Runnable saveConfig;

    public ModuleContext(JavaPlugin plugin, CommandRoot root, Function<String, Object> sections, Runnable saveConfig) {
        this.plugin = plugin;
        this.root = root;
        this.sections = sections;
        this.saveConfig = saveConfig;
    }

    /** Writes the whole config file now (a module that changes its own section at run time, such as the HUD nudges). */
    public void saveConfig() {
        saveConfig.run();
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public HytaleLogger logger() {
        return plugin.getLogger();
    }

    /** The module's own settings object, from the section of the config named after it ("Power" for the id "power"). */
    public <T> T settings(String moduleId, Class<T> type) {
        return type.cast(sections.apply(moduleId));
    }

    /** Adds a sub-command under /arrakis. */
    public void command(AbstractCommand sub) {
        root.add(sub);
    }

    /** A shared command group under /arrakis; see CommandRoot.group. */
    public com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection group(String name) {
        return root.group(name);
    }

    public void system(ISystem<EntityStore> system) {
        plugin.getEntityStoreRegistry().registerSystem(system);
    }

    public EventRegistry events() {
        return plugin.getEventRegistry();
    }
}
