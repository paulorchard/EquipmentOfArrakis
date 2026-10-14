package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/** Loads the modules the config enables, one at a time: a module that fails to set up is logged and the others go on. */
public final class Modules {

    /** One module and what became of it. */
    public static final class Entry {
        private final EquipmentModule module;
        private final boolean enabled;
        private String setupResult;

        Entry(EquipmentModule module, boolean enabled, String setupResult) {
            this.module = module;
            this.enabled = enabled;
            this.setupResult = setupResult;
        }

        public EquipmentModule module() {
            return module;
        }

        public boolean enabled() {
            return enabled;
        }

        /** "ok", "disabled" or "FAILED: reason". */
        public String setupResult() {
            return setupResult;
        }

        public boolean running() {
            return enabled && "ok".equals(setupResult);
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public List<Entry> entries() {
        return entries;
    }

    /**
     * Sets up every module whose id is in {@code enabledIds} (compared without case). The core module is always on: it carries
     * the module list and the commands.
     */
    public void setupAll(List<EquipmentModule> all, Set<String> enabledIds, ModuleContext ctx) {
        HytaleLogger.Api log = ctx.logger().at(Level.INFO);
        for (EquipmentModule module : all) {
            boolean enabled = module.id().equals("core") || enabledIds.contains(module.id().toLowerCase(Locale.ROOT));
            if (!enabled) {
                entries.add(new Entry(module, false, "disabled"));
                continue;
            }
            Entry entry = new Entry(module, true, "ok");
            try {
                module.setup(ctx);
            } catch (Throwable t) {
                entry.setupResult = "FAILED: " + t;
                ctx.logger().at(Level.SEVERE).withCause(t).log("Module %s setup FAILED", module.id());
            }
            entries.add(entry);
        }
        for (Entry entry : entries) {
            if (entry.running() || !entry.enabled()) {
                log.log("Module %s: %s", entry.module().id(), entry.enabled() ? "setup ok" : "disabled in the config");
            } else {
                ctx.logger().at(Level.WARNING).log("Module %s: setup %s", entry.module().id(), entry.setupResult());
            }
        }
    }

    public void startAll(ModuleContext ctx) {
        for (Entry entry : entries) {
            if (entry.running()) {
                try {
                    entry.module().start(ctx);
                } catch (Throwable t) {
                    ctx.logger().at(Level.WARNING).withCause(t).log("Module %s start failed", entry.module().id());
                }
            }
        }
    }

    /** The self-check lines of every running module, each prefixed with the module id. */
    public List<String> selfChecks() {
        List<String> lines = new ArrayList<>();
        for (Entry entry : entries) {
            if (!entry.running()) {
                continue;
            }
            try {
                for (String line : entry.module().selfCheck()) {
                    lines.add("[" + entry.module().id() + "] " + line);
                }
            } catch (Throwable t) {
                lines.add("[" + entry.module().id() + "] PROBLEM self-check threw " + t);
            }
        }
        return lines;
    }

    public static boolean isProblem(String line) {
        return line.contains("PROBLEM");
    }
}
