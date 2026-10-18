package com.paulorchard.islandcraft.equipmentofarrakis.core;

import java.util.List;

/**
 * One module of the mod: a package, an asset folder and a section of the config. The plugin class lists the modules; a module
 * imports only its own package, core and power (see ModuleBoundaryTest).
 */
public interface EquipmentModule {

    /** The lower-case id used in the config's Modules list and by /arrakis modules, for example "power". */
    String id();

    /** Registers interactions, systems, commands and events. Runs in the plugin's setup(); a failure disables only this module. */
    void setup(ModuleContext ctx) throws Exception;

    /** Assets are loaded now: log and verify. */
    default void start(ModuleContext ctx) {
    }

    /** The plugin is shutting down: put back what this module changed in the game (a hidden vanilla HUD part, a switch in another mod). */
    default void stop(ModuleContext ctx) {
    }

    /** Lines of text describing what was found; a line containing PROBLEM is a failure. */
    default List<String> selfCheck() {
        return List.of();
    }
}
