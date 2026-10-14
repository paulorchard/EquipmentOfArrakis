package com.paulorchard.islandcraft.equipmentofarrakis.power;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CommandRoot;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldPositional;

import java.util.List;
import java.util.Locale;

/**
 * The operator commands of the power module. Each works on the sender, or on an online player named last.
 *
 * <pre>
 * /arrakis power get [player]                 Mana, max, fraction, game mode, modifiers
 * /arrakis power set &lt;n&gt; [player]
 * /arrakis power add &lt;n&gt; [player]
 * /arrakis power fill [player]
 * /arrakis power spend &lt;n&gt; [player]          says whether it was refused
 * /arrakis power max [player]                 the max and the modifiers
 * /arrakis power modifier add &lt;id&gt; &lt;add|mul&gt; &lt;amount&gt; [player]
 * /arrakis power modifier remove &lt;id&gt; [player]
 * /arrakis power modifier list [player]
 * /arrakis power selftest                     development: the checks, on a test dummy
 * </pre>
 */
final class PowerCommands {

    private static final String LANG = CommandRoot.LANG + "power.";

    private PowerCommands() {
    }

    static AbstractCommandCollection build(Power power) {
        AbstractCommandCollection root = new AbstractCommandCollection("power", LANG + "desc") {
        };
        root.addSubCommand(WorldPositional.build("get", LANG + "get.desc", (ctx, world, store, args) -> {
            PlayerRef target = resolve(ctx, store, args, 0);
            if (target != null) {
                report(ctx, power, store, target);
            }
        }, "player"));
        root.addSubCommand(WorldPositional.build("set", LANG + "set.desc", (ctx, world, store, args) -> {
            Float amount = number(ctx, args[0]);
            PlayerRef target = amount == null ? null : resolve(ctx, store, args, 1);
            if (target != null) {
                float after = power.set(store, target.getReference(), amount);
                say(ctx, "set to " + after + " (asked " + amount + ", clamped to 0 and the max)");
                report(ctx, power, store, target);
            }
        }, "amount", "player"));
        root.addSubCommand(WorldPositional.build("add", LANG + "add.desc", (ctx, world, store, args) -> {
            Float amount = number(ctx, args[0]);
            PlayerRef target = amount == null ? null : resolve(ctx, store, args, 1);
            if (target != null) {
                power.add(store, target.getReference(), amount);
                report(ctx, power, store, target);
            }
        }, "amount", "player"));
        root.addSubCommand(WorldPositional.build("fill", LANG + "fill.desc", (ctx, world, store, args) -> {
            PlayerRef target = resolve(ctx, store, args, 0);
            if (target != null) {
                power.fill(store, target.getReference());
                report(ctx, power, store, target);
            }
        }, "player"));
        root.addSubCommand(WorldPositional.build("spend", LANG + "spend.desc", (ctx, world, store, args) -> {
            Float amount = number(ctx, args[0]);
            PlayerRef target = amount == null ? null : resolve(ctx, store, args, 1);
            if (target != null) {
                boolean ok = power.spend(store, target.getReference(), amount);
                say(ctx, ok ? "spent " + amount + (power.isCreative(store, target.getReference()) ? " (creative: nothing taken)" : "")
                        : "REFUSED: less than " + amount + " power");
                report(ctx, power, store, target);
            }
        }, "amount", "player"));
        root.addSubCommand(WorldPositional.build("max", LANG + "max.desc", (ctx, world, store, args) -> {
            PlayerRef target = resolve(ctx, store, args, 0);
            if (target != null) {
                say(ctx, "max " + power.max(store, target.getReference()));
                listModifiers(ctx, power, store, target);
            }
        }, "player"));

        AbstractCommandCollection modifier = new AbstractCommandCollection("modifier", LANG + "modifier.desc") {
        };
        modifier.addSubCommand(WorldPositional.build("add", LANG + "modifier.add.desc", (ctx, world, store, args) -> {
            PowerModifiers.Kind kind = switch (args[1].toLowerCase(Locale.ROOT)) {
                case "add", "additive" -> PowerModifiers.Kind.ADDITIVE;
                case "mul", "multiplicative" -> PowerModifiers.Kind.MULTIPLICATIVE;
                default -> null;
            };
            if (kind == null) {
                say(ctx, "The kind is add or mul, not " + args[1]);
                return;
            }
            Float amount = number(ctx, args[2]);
            PlayerRef target = amount == null ? null : resolve(ctx, store, args, 3);
            if (target != null) {
                power.modifiers().putMax(store, target.getReference(), args[0], kind, amount);
                say(ctx, "modifier " + args[0] + " " + kind + " " + amount + " on the max");
                report(ctx, power, store, target);
            }
        }, "id", "kind", "amount", "player"));
        modifier.addSubCommand(WorldPositional.build("remove", LANG + "modifier.remove.desc", (ctx, world, store, args) -> {
            PlayerRef target = resolve(ctx, store, args, 1);
            if (target != null) {
                boolean removed = power.modifiers().removeMax(store, target.getReference(), args[0]);
                say(ctx, removed ? "removed " + args[0] : "there was no modifier " + args[0]);
                report(ctx, power, store, target);
            }
        }, "id", "player"));
        modifier.addSubCommand(WorldPositional.build("list", LANG + "modifier.list.desc", (ctx, world, store, args) -> {
            PlayerRef target = resolve(ctx, store, args, 0);
            if (target != null) {
                listModifiers(ctx, power, store, target);
            }
        }, "player"));
        root.addSubCommand(modifier);

        root.addSubCommand(WorldPositional.build("selftest", LANG + "selftest.desc", (ctx, world, store, args) ->
                PowerSelfTest.run(world, power, line -> say(ctx, line))));
        return root;
    }

    private static void report(CommandContext ctx, Power power, Store<EntityStore> store, PlayerRef target) {
        Ref<EntityStore> ref = target.getReference();
        say(ctx, "Mana " + power.get(store, ref) + " / " + power.max(store, ref) + " (" + Math.round(power.fraction(store, ref) * 100)
                + "%), " + (power.isCreative(store, ref) ? "creative" : "not creative"));
        listModifiers(ctx, power, store, target);
    }

    private static void listModifiers(CommandContext ctx, Power power, Store<EntityStore> store, PlayerRef target) {
        List<PowerModifiers.Listed> listed = power.modifiers().list(store, target.getReference());
        if (listed.isEmpty()) {
            say(ctx, "modifiers: none");
        }
        for (PowerModifiers.Listed m : listed) {
            say(ctx, "modifier " + m.id() + ": " + m.kind() + " " + m.amount() + " on " + m.target());
        }
    }

    private static Float number(CommandContext ctx, String text) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            say(ctx, "Not a number: " + text);
            return null;
        }
    }

    private static void say(CommandContext ctx, String text) {
        CoreModule.say(ctx, text);
    }

    private static PlayerRef resolve(CommandContext ctx, Store<EntityStore> store, String[] args, int index) {
        if (index < args.length) {
            PlayerRef found = Universe.get().getPlayer(args[index], NameMatching.DEFAULT);
            if (found == null) {
                say(ctx, "No such player: " + args[index]);
                return null;
            }
            Ref<EntityStore> ref = found.getReference();
            if (ref == null || ref.getStore() != store) {
                say(ctx, found.getUsername() + " is in another world; run the command from there.");
                return null;
            }
            return found;
        }
        if (!ctx.isPlayer()) {
            say(ctx, "Name a player; the console has no power.");
            return null;
        }
        Ref<EntityStore> ref = ctx.senderAsPlayerRef();
        return ref == null ? null : store.getComponent(ref, PlayerRef.getComponentType());
    }
}
