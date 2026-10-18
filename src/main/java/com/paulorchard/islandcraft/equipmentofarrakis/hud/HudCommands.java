package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CommandRoot;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldPositional;
import com.paulorchard.islandcraft.equipmentofarrakis.core.water.WaterApi;
import com.paulorchard.islandcraft.equipmentofarrakis.core.water.WaterLink;

import java.util.Locale;
import java.util.TreeSet;

/**
 * <pre>
 * /arrakis hud on | off                           our bars (and the Water bars switched off) or the vanilla bars back, for everyone
 * /arrakis hud state                              hidden components, the four values, alert state, nudges, the Water link
 * /arrakis hud lb|rb|lt|rt [x[,] y]               nudge a bar (lb Health, rb Water, lt Power, rt Heat Exposure): saved, for everyone
 * /arrakis hud lb|rb|lt|rt reset                  clear one nudge;  /arrakis hud nudge reset  clears all four
 * /arrakis hud demo &lt;bar&gt; &lt;percent&gt;           pins power | health | exposure | water to a percent for a few seconds
 * /arrakis hud component hide|show &lt;Name&gt;       hides or shows one vanilla HUD part for you (to find what draws something)
 * /arrakis hud component reset                    the engine default set of vanilla parts for you
 * </pre>
 */
final class HudCommands {

    private static final String LANG = CommandRoot.LANG + "hud.";

    private HudCommands() {
    }

    static AbstractCommandCollection build(ModuleContext ctx) {
        AbstractCommandCollection hud = new AbstractCommandCollection("hud", LANG + "desc") {
        };
        hud.addSubCommand(WorldPositional.build("on", LANG + "on.desc", (c, w, s, a) -> {
            HudRuntime.setEnabled(true);
            say(c, "Hud ON: the four bars show, the vanilla Health and Mana bars are hidden. " + HudModule.hideWaterBars(true));
        }));
        hud.addSubCommand(WorldPositional.build("off", LANG + "off.desc", (c, w, s, a) -> {
            HudRuntime.setEnabled(false);
            say(c, "Hud OFF: the four bars are removed and the vanilla Health and Mana bars come back within a tick. " + HudModule.hideWaterBars(false));
        }));
        hud.addSubCommand(WorldPositional.build("state", LANG + "state.desc", (c, w, s, a) -> player(c, ref -> state(c, s, ref))));
        hud.addSubCommand(WorldPositional.build("demo", LANG + "demo.desc", (c, w, s, a) -> player(c, ref -> demo(c, s, ref, a)), "bar", "percent"));
        hud.addSubCommand(WorldPositional.build("component", LANG + "component.desc", (c, w, s, a) -> player(c, ref -> component(c, s, ref, a)), "action", "name"));
        for (HudBar bar : HudBar.values()) {
            hud.addSubCommand(WorldPositional.build(bar.code, LANG + "nudge.desc", (c, w, s, a) -> nudge(c, ctx, bar, a), "x", "y", "z"));
        }
        hud.addSubCommand(WorldPositional.build("nudge", LANG + "nudgereset.desc", (c, w, s, a) -> {
            if (a.length == 0 || !a[0].equalsIgnoreCase("reset")) {
                say(c, "Usage: arrakis hud nudge reset (clears the nudge of all four bars)");
                return;
            }
            HudRuntime.config().nudges().resetAll();
            changed(ctx);
            say(c, "All four nudges cleared and saved.");
        }, "word"));
        return hud;
    }

    // ---- nudge

    private static void nudge(CommandContext c, ModuleContext ctx, HudBar bar, String[] args) {
        HudConfig.Nudges nudges = HudRuntime.config().nudges();
        HudNudge.Parsed parsed = HudNudge.parse(args);
        switch (parsed.kind()) {
            case SHOW -> say(c, bar.code + " (" + bar.id + "): " + HudNudge.describe(nudges.of(bar).getX(), nudges.of(bar).getY()));
            case RESET -> {
                nudges.reset(bar);
                changed(ctx);
                say(c, bar.code + " (" + bar.id + ") cleared and saved.");
            }
            case MOVE -> {
                nudges.add(bar, parsed.dx(), parsed.dy());
                changed(ctx);
                say(c, bar.code + " (" + bar.id + ") moved " + HudNudge.describe(parsed.dx(), parsed.dy()) + ", now "
                        + HudNudge.describe(nudges.of(bar).getX(), nudges.of(bar).getY()) + " (saved)."
                        + (parsed.ignoredExtra() ? " The third number was ignored: the HUD is 2D." : "")
                        + (HudLayout.pad(bar, nudges.of(bar).getX()) == 0 ? " The bar is at the middle of the screen: it cannot go further that way." : ""));
            }
            default -> say(c, parsed.message());
        }
    }

    /** A nudge changed: write the config file and build every player view again. */
    private static void changed(ModuleContext ctx) {
        ctx.saveConfig();
        HudRuntime.rebuildAll();
    }

    // ---- state, demo, component

    private static void state(CommandContext c, Store<EntityStore> store, Ref<EntityStore> entity) {
        Player player = store.getComponent(entity, Player.getComponentType());
        PlayerRef ref = store.getComponent(entity, PlayerRef.getComponentType());
        HudRuntime.PlayerHud state = HudRuntime.of(ref.getUuid());
        HudConfig config = HudRuntime.config();
        var visible = player.getHudManager().getVisibleHudComponents();
        TreeSet<String> names = new TreeSet<>();
        visible.forEach(component -> names.add(component.name()));
        say(c, "module " + (HudRuntime.enabled() ? "ON" : "OFF") + ", our HUD " + (state.view != null ? "added (" + state.view.document() + ")" : "not added")
                + ", vanilla Health " + (visible.contains(HudComponent.Health) ? "SHOWN" : "hidden")
                + ", Mana " + (visible.contains(HudComponent.Mana) ? "SHOWN" : "hidden") + ", hidden by us " + state.hidden
                + ", game mode " + (player.getGameMode() == GameMode.Creative ? "Creative (creative fills)" : player.getGameMode() + " (adventure fills)"));
        say(c, "visible vanilla parts: " + names);
        WaterApi water = WaterLink.api();
        double[] now = new double[4];
        HudRuntime.readValues(ref, entity, store, water, now);
        for (HudBar bar : HudBar.values()) {
            int i = bar.ordinal();
            HudConfig.Nudge nudge = config.nudges().of(bar);
            say(c, String.format(Locale.ROOT, "  %s %-8s %-12s alert switch %-3s %s   nudge %s", bar.code, bar.id,
                    Double.isNaN(now[i]) ? "(not drawn)" : String.format(Locale.ROOT, "%.1f%%", now[i]), config.flashes(bar) ? "on" : "off",
                    state.alert[i] == HudMath.Alert.NONE ? "" : "ALERT " + state.alert[i], HudNudge.describe(nudge.getX(), nudge.getY())));
        }
        HudRuntime.Pin pin = state.pin;
        say(c, "pinned by demo: " + (pin == null ? "no" : pin.bar().id + " " + pin.percent() + "%"));
        say(c, "Water: " + WaterLink.status() + "; hide switch " + (water != null && HudModule.waterHidden() ? "set (its bars are off)" : "not set")
                + "; exposure colour rules " + (HudRuntime.exposureRulesFromWater() ? "from the Water mod" : "fallback (Hud config)"));
    }

    private static void demo(CommandContext c, Store<EntityStore> store, Ref<EntityStore> entity, String[] args) {
        HudBar bar = HudBar.parse(args[0]);
        double percent;
        try {
            percent = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            say(c, "Not a number: " + args[1]);
            return;
        }
        if (bar == null) {
            say(c, "The bar is power, health, exposure or water.");
            return;
        }
        PlayerRef ref = store.getComponent(entity, PlayerRef.getComponentType());
        HudConfig config = HudRuntime.config();
        double clamped = Math.max(0, Math.min(100, percent));
        long until = System.nanoTime() + (long) (config.getDemoSeconds() * 1e9);
        HudRuntime.of(ref.getUuid()).pin = new HudRuntime.Pin(bar, clamped, until);
        say(c, bar.id + " pinned to " + clamped + "% for " + config.getDemoSeconds() + " s"
                + (HudMath.alerting(config.flashes(bar), clamped, config.getFlashBelowPercent()) ? " (it turns red and flashes)" : config.flashes(bar) ? "" : " (its alert switch is off)"));
    }

    private static void component(CommandContext c, Store<EntityStore> store, Ref<EntityStore> entity, String[] args) {
        Player player = store.getComponent(entity, Player.getComponentType());
        PlayerRef ref = store.getComponent(entity, PlayerRef.getComponentType());
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("reset")) {
            player.getHudManager().resetVisibleHudComponents(ref);
            say(c, "Visible parts reset to the engine default (our tick hides Health and Mana again while the module is ON).");
            return;
        }
        if (args.length < 2 || (!action.equals("hide") && !action.equals("show"))) {
            say(c, "Usage: arrakis hud component hide|show <Name> (Hotbar, StatusIcons, Reticle, InputBindings, Abilities, Stamina, ...) or reset");
            return;
        }
        HudComponent component;
        try {
            component = HudComponent.valueOf(args[1]);
        } catch (IllegalArgumentException e) {
            say(c, "Not a HUD part: " + args[1]);
            return;
        }
        if (action.equals("hide")) {
            player.getHudManager().hideHudComponents(ref, component);
        } else {
            player.getHudManager().showHudComponents(ref, component);
        }
        say(c, action + " " + component + ": done (look at the screen)");
    }

    private static void player(CommandContext c, Action action) {
        if (!c.isPlayer()) {
            say(c, "Run this as a player in the game.");
            return;
        }
        Ref<EntityStore> ref = c.senderAsPlayerRef();
        if (ref != null) {
            action.run(ref);
        }
    }

    private interface Action {
        void run(Ref<EntityStore> ref);
    }

    private static void say(CommandContext c, String text) {
        CoreModule.say(c, text);
    }
}
