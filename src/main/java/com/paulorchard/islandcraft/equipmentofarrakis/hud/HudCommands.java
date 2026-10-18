package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CommandRoot;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldPositional;
import com.paulorchard.islandcraft.equipmentofarrakis.core.water.WaterApi;
import com.paulorchard.islandcraft.equipmentofarrakis.core.water.WaterLink;

import java.util.Locale;
import java.util.TreeSet;

/**
 * <pre>
 * /arrakis hud on | off                       our bars (and the Water bars switched off) or the vanilla bars back, for everyone
 * /arrakis hud state                          hidden components, the four values, flash state, the Water link
 * /arrakis hud demo &lt;bar&gt; &lt;percent&gt;       pins power | health | exposure | water to a percent for a few seconds
 * /arrakis hud component hide|show &lt;Name&gt;   hides or shows one vanilla HUD part for you (to find what draws something)
 * /arrakis hud component reset                the engine default set of vanilla parts for you
 * </pre>
 */
final class HudCommands {

    private static final String LANG = CommandRoot.LANG + "hud.";

    private HudCommands() {
    }

    static AbstractCommandCollection build() {
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
        return hud;
    }

    private static void state(CommandContext c, Store<EntityStore> store, Ref<EntityStore> entity) {
        Player player = store.getComponent(entity, Player.getComponentType());
        PlayerRef ref = store.getComponent(entity, PlayerRef.getComponentType());
        HudRuntime.PlayerHud state = HudRuntime.of(ref.getUuid());
        var visible = player.getHudManager().getVisibleHudComponents();
        TreeSet<String> names = new TreeSet<>();
        visible.forEach(component -> names.add(component.name()));
        say(c, "module " + (HudRuntime.enabled() ? "ON" : "OFF") + ", our HUD " + (state.view != null ? "added" : "not added")
                + ", vanilla Health " + (visible.contains(HudComponent.Health) ? "SHOWN" : "hidden")
                + ", Mana " + (visible.contains(HudComponent.Mana) ? "SHOWN" : "hidden") + ", hidden by us " + state.hidden);
        say(c, "visible vanilla parts: " + names);
        WaterApi water = WaterLink.api();
        double[] now = new double[4];
        HudRuntime.readValues(ref, entity, store, water, now);
        for (HudBar bar : HudBar.values()) {
            int i = bar.ordinal();
            say(c, String.format(Locale.ROOT, "  %-8s %s%s  colour %s", bar.id, Double.isNaN(now[i]) ? "(not drawn)" : String.format(Locale.ROOT, "%.1f%%", now[i]),
                    state.flashing[i] ? "  FLASHING (" + (state.bright ? "bright" : "normal") + ")" : "",
                    HudRuntime.colorOf(bar, now[i], state.flashing[i], state.bright, HudRuntime.config())));
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
        double clamped = Math.max(0, Math.min(100, percent));
        long until = System.nanoTime() + (long) (HudRuntime.config().getDemoSeconds() * 1e9);
        HudRuntime.of(ref.getUuid()).pin = new HudRuntime.Pin(bar, clamped, until);
        say(c, bar.id + " pinned to " + clamped + "% for " + HudRuntime.config().getDemoSeconds() + " s"
                + (bar.flashes && clamped < HudRuntime.config().getFlashBelowPercent() ? " (it flashes)" : ""));
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
