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

import org.joml.Vector3d;

import java.util.Locale;
import java.util.UUID;

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
    private static final UUID DEBUG_OWNER = new UUID(0, 1);

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
        AbstractCommandCollection laser = new AbstractCommandCollection("laser", LANG + "laser.desc") {
        };
        laser.addSubCommand(WorldPositional.build("rig", LANG + "laser.rig.desc", (ctx, world, store, args) -> {
            // Three thin rods 1.5 blocks long, 3 blocks ahead of the player, to see which way a piece of the laser model lies:
            // one pointing away from the player, one straight up, one to the player's right. They stay until "laser clear".
            if (!ctx.isPlayer() || ctx.senderAsPlayerRef() == null) {
                say(ctx, "Run this as a player.");
                return;
            }
            Ref<EntityStore> player = ctx.senderAsPlayerRef();
            com.hypixel.hytale.math.vector.Transform look = com.hypixel.hytale.server.core.util.TargetUtil.getLook(player, store);
            Vector3d eye = look.getPosition();
            Vector3d ahead = new Vector3d(look.getDirection().x, 0, look.getDirection().z).normalize();
            Vector3d right = BeamMath.hand(new Vector3d(), ahead, 0, 1, 0);
            Vector3d base = new Vector3d(eye).fma(3, ahead);
            Vector3d[] centres = {new Vector3d(base), new Vector3d(base).fma(1.5, right), new Vector3d(base).fma(-1.5, right)};
            Vector3d[] directions = {ahead, new Vector3d(0, 1, 0), right};
            for (int i = 0; i < 3; i++) {
                Ref<EntityStore> ref = CutterLaser.debugSpawn(store, DEBUG_OWNER, centres[i], directions[i], 0.5f);
                if (ref == null) {
                    say(ctx, "The laser model assets are missing");
                    return;
                }
                MoltenTrail.debugOwn(DEBUG_OWNER, ref);
            }
            say(ctx, "Rods: the middle one should point away from you, the one on your right straight up, the one on your left to your right.");
        }));
        laser.addSubCommand(WorldPositional.build("clear", LANG + "laser.clear.desc", (ctx, world, store, args) -> {
            MoltenTrail.forget(DEBUG_OWNER);
            say(ctx, "Debug rods will be removed.");
        }));
        laser.addSubCommand(WorldPositional.build("spawn", LANG + "laser.spawn.desc", (ctx, world, store, args) -> {
            // "keep" makes a debug owner own it, so the sweeper leaves it alone; without it the sweeper removes it.
            boolean keep = args.length > 0 && args[0].equals("keep");
            Vector3d at = args.length >= 4
                    ? new Vector3d(Double.parseDouble(args[1]), Double.parseDouble(args[2]), Double.parseDouble(args[3]))
                    : new Vector3d(0, 100, 0);
            // The chunk has to be loaded, or the engine drops the entity (no player is near it on a headless server).
            world.getChunkAsync(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(at.x, at.z))
                    .thenAccept(chunk -> world.execute(() -> {
                        chunk.resetKeepAlive();
                        Ref<EntityStore> ref = CutterLaser.debugSpawn(store, DEBUG_OWNER, at, new Vector3d(1, 0, 0), 1f);
                        if (ref == null) {
                            say(ctx, "Could not add the piece (model assets missing)");
                            return;
                        }
                        if (keep) {
                            MoltenTrail.debugOwn(DEBUG_OWNER, ref);
                        }
                        say(ctx, "Spawned one laser piece at " + at + (keep ? " (kept)" : " (unowned)"));
                    }));
        }, "keep", "x", "y", "z"));
        laser.addSubCommand(WorldPositional.build("count", LANG + "laser.count.desc", (ctx, world, store, args) -> {
            say(ctx, "Laser segments: " + store.getEntityCountFor(
                    com.hypixel.hytale.component.Archetype.of(BeamSegment.getComponentType()))
                    + ", entities in all: " + store.getEntityCount());
        }));
        arrakis.addSubCommand(laser);
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
