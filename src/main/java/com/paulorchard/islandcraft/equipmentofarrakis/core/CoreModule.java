package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import java.util.List;

/**
 * The core module: the module list and the commands that talk about it. The plugin, the config and the module loading are
 * in the root package and in this one; world access is {@link WorldAccess}.
 *
 * <pre>
 * /arrakis modules       every module, whether it is enabled, and how its setup went
 * /arrakis selfcheck     re-runs the self-checks and prints them
 * /arrakis worldtest     development: writes and reads a block at 0, 200, 0 through WorldAccess
 * </pre>
 */
public final class CoreModule implements EquipmentModule {

    private final Modules modules;

    public CoreModule(Modules modules) {
        this.modules = modules;
    }

    @Override
    public String id() {
        return "core";
    }

    @Override
    public void setup(ModuleContext ctx) {
        String lang = CommandRoot.LANG;
        // Toggles are OFF after a relog, a world change and death (the death part is ToggleResetSystem).
        ctx.events().registerGlobal(com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent.class,
                event -> ToggleRegistry.get().forget(event.getPlayerRef().getUuid()));
        ctx.events().registerGlobal(com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent.class,
                event -> forgetHolder(event.getHolder()));
        ctx.events().registerGlobal(com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent.class,
                event -> forgetHolder(event.getHolder()));
        ctx.system(new ToggleResetSystem());
        ctx.command(WorldPositional.build("modules", lang + "modules.desc", (c, world, store, args) -> {
            for (Modules.Entry entry : modules.entries()) {
                say(c, entry.module().id() + ": " + (entry.enabled() ? "enabled" : "disabled") + ", setup " + entry.setupResult());
            }
        }));
        ctx.command(WorldPositional.build("selfcheck", lang + "selfcheck.desc", (c, world, store, args) -> {
            List<String> lines = modules.selfChecks();
            lines.forEach(line -> say(c, line));
            long problems = lines.stream().filter(Modules::isProblem).count();
            say(c, problems == 0 ? "self-check: no problems" : "self-check: " + problems + " PROBLEM line(s)");
        }));
        ctx.command(WorldPositional.build("worldtest", lang + "worldtest.desc", (c, world, store, args) ->
                WorldAccessSelfTest.run(world, line -> say(c, line))));
    }

    @Override
    public List<String> selfCheck() {
        return List.of("modules loaded: " + modules.entries().stream().filter(Modules.Entry::running).count() + " of "
                + modules.entries().size());
    }

    private static void forgetHolder(com.hypixel.hytale.component.Holder<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> holder) {
        com.hypixel.hytale.server.core.universe.PlayerRef player = holder == null ? null
                : holder.getComponent(com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
        if (player != null) {
            ToggleRegistry.get().forget(player.getUuid());
        }
    }

    /** Sends a line to the sender of a command. */
    public static void say(CommandContext ctx, String text) {
        ctx.sendMessage(Message.raw(text));
    }
}
