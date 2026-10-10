package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Locale;

/**
 * The development commands. No permission group is set, so only operators can run them.
 *
 * <pre>
 * /arrakis power fill [player]
 * /arrakis power set &lt;n&gt; [player]
 * </pre>
 */
final class PowerCommands {

    private static final String LANG = "server.commands.arrakis.";

    private PowerCommands() {
    }

    static AbstractCommandCollection build() {
        AbstractCommandCollection arrakis = new AbstractCommandCollection("arrakis", LANG + "desc") {
        };
        AbstractCommandCollection power = new AbstractCommandCollection("power", LANG + "power.desc") {
        };
        power.addSubCommand(WorldPositional.build("fill", LANG + "power.fill.desc", (ctx, world, store, args) -> {
            PlayerRef target = resolve(ctx, store, args, 0);
            if (target != null) {
                report(ctx, target, ArrakisPower.fill(store, target.getReference()));
            }
        }, "player"));
        power.addSubCommand(WorldPositional.build("set", LANG + "power.set.desc", (ctx, world, store, args) -> {
            double amount;
            try {
                amount = Double.parseDouble(args[0]);
            } catch (NumberFormatException e) {
                say(ctx, "Not a number: " + args[0]);
                return;
            }
            PlayerRef target = resolve(ctx, store, args, 1);
            if (target != null) {
                report(ctx, target, ArrakisPower.set(store, target.getReference(), (float) amount));
            }
        }, "amount", "player"));
        arrakis.addSubCommand(power);
        return arrakis;
    }

    private static void report(CommandContext ctx, PlayerRef target, float now) {
        if (now < 0) {
            say(ctx, target.getUsername() + " has no Arrakis_Power stat (is the asset pack loaded?)");
        } else {
            say(ctx, String.format(Locale.ROOT, "%s power is now %.2f", target.getUsername(), now));
        }
    }

    private static void say(CommandContext ctx, String text) {
        ctx.sendMessage(Message.raw(text));
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
