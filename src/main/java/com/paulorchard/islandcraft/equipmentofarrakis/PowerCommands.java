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
 * /arrakis ammo fill [player]
 * /arrakis ammo set &lt;n&gt; [player]
 * /arrakis dart selftest
 * </pre>
 */
final class PowerCommands {

    private static final String LANG = "server.commands.arrakis.";

    private PowerCommands() {
    }

    static AbstractCommandCollection build(java.util.function.Supplier<HudSettings> hudSettings,
            java.util.function.Supplier<HoltzmanSettings> shieldSettings, java.util.function.Supplier<DartSettings> dartSettings) {
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
        AbstractCommandCollection ammo = new AbstractCommandCollection("ammo", LANG + "ammo.desc") {
        };
        ammo.addSubCommand(WorldPositional.build("fill", LANG + "ammo.fill.desc", (ctx, world, store, args) -> {
            PlayerRef target = resolve(ctx, store, args, 0);
            if (target != null) {
                reportAmmo(ctx, target, ArrakisAmmo.fill(store, target.getReference()));
            }
        }, "player"));
        ammo.addSubCommand(WorldPositional.build("set", LANG + "ammo.set.desc", (ctx, world, store, args) -> {
            double amount;
            try {
                amount = Double.parseDouble(args[0]);
            } catch (NumberFormatException e) {
                say(ctx, "Not a number: " + args[0]);
                return;
            }
            PlayerRef target = resolve(ctx, store, args, 1);
            if (target != null) {
                reportAmmo(ctx, target, ArrakisAmmo.set(store, target.getReference(), (float) amount));
            }
        }, "amount", "player"));
        arrakis.addSubCommand(ammo);
        AbstractCommandCollection dart = new AbstractCommandCollection("dart", LANG + "dart.desc") {
        };
        dart.addSubCommand(WorldPositional.build("selftest", LANG + "dart.selftest.desc", (ctx, world, store, args) -> {
            // Development: flies real darts around 0, 200, 0 and reports damage by distance, drop, range and the entity count.
            DartSelfTest.run(world, line -> say(ctx, line));
        }));
        arrakis.addSubCommand(dart);
        AbstractCommandCollection compactor = new AbstractCommandCollection("compactor", LANG + "compactor.desc") {
        };
        compactor.addSubCommand(WorldPositional.build("selftest", LANG + "compactor.selftest.desc", (ctx, world, store, args) -> {
            // Development: puts a sand block high in the air at 0, 200, 0 and runs the Compactor's engine calls on it.
            // The column, and the sky section at y 200 (it may not exist yet), held loaded while the test runs.
            WorldAccess.loadTestArea(world, 0, 0, 0, 0, 200).whenCompleteAsync((held, error) -> {
                if (error != null) {
                    say(ctx, "chunks did not load: " + error);
                    return;
                }
                try {
                    say(ctx, "sky section at y 200 loaded and held: " + held.loaded() + " of " + held.wanted);
                    for (String line : CompactorSelfTest.run(world)) {
                        say(ctx, line);
                    }
                } finally {
                    held.release();
                }
            }, world);
        }));
        arrakis.addSubCommand(compactor);
        AbstractCommandCollection dewReaper = new AbstractCommandCollection("dewreaper", LANG + "dewreaper.desc") {
        };
        dewReaper.addSubCommand(WorldPositional.build("selftest", LANG + "dewreaper.selftest.desc", (ctx, world, store, args) -> {
            // Development: says whether Water of Arrakis is linked and runs its flask functions on a made-up stack.
            say(ctx, WaterLink.status());
            WaterApi water = WaterLink.api();
            if (water == null) {
                return;
            }
            com.hypixel.hytale.server.core.inventory.ItemStack flask = new com.hypixel.hytale.server.core.inventory.ItemStack(
                    "Arrakis_Litrejon", 1);
            say(ctx, "capacity " + water.capacity() + ", is a flask: " + water.isLitrejon(flask)
                    + ", amount of a new flask " + water.amount(flask));
            com.hypixel.hytale.server.core.inventory.ItemStack half = water.withAmount(flask, 250);
            say(ctx, "withAmount 250 -> amount " + water.amount(half) + ", durability " + half.getDurability() + " of "
                    + half.getMaxDurability());
            com.hypixel.hytale.server.core.inventory.ItemStack more = water.withAmount(half, DewReaperRules.filled(water.amount(half), 1,
                    water.capacity()));
            say(ctx, "+1 -> amount " + water.amount(more) + ", durability " + more.getDurability());
        }));
        arrakis.addSubCommand(dewReaper);
        AbstractCommandCollection shield = new AbstractCommandCollection("shield", LANG + "shield.desc") {
        };
        shield.addSubCommand(WorldPositional.build("hit", LANG + "shield.hit.desc", (ctx, world, store, args) -> {
            // Hits you with a projectile-sourced damage event through the real damage pipeline (the same path an arrow takes).
            if (!ctx.isPlayer() || ctx.senderAsPlayerRef() == null) {
                say(ctx, "Run this as a player.");
                return;
            }
            float amount;
            try {
                amount = Float.parseFloat(args[0]);
            } catch (NumberFormatException e) {
                say(ctx, "Not a number: " + args[0]);
                return;
            }
            Ref<EntityStore> self = ctx.senderAsPlayerRef();
            com.hypixel.hytale.server.core.modules.entity.damage.Damage damage = new com.hypixel.hytale.server.core.modules.entity.damage.Damage(
                    new com.hypixel.hytale.server.core.modules.entity.damage.Damage.ProjectileSource(self, self),
                    com.hypixel.hytale.server.core.modules.entity.damage.DamageCause.PROJECTILE, amount);
            // A hit location one block in front of the eyes, so the flash has somewhere to go.
            com.hypixel.hytale.math.vector.Transform look = com.hypixel.hytale.server.core.util.TargetUtil.getLook(self, store);
            Vector3d at = new Vector3d(look.getPosition()).fma(1.0, new Vector3d(look.getDirection()).normalize());
            damage.putMetaObject(com.hypixel.hytale.server.core.modules.entity.damage.Damage.HIT_LOCATION,
                    new org.joml.Vector4d(at.x, at.y, at.z, 1.0));
            float before = ArrakisPower.get(store, self);
            com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems.executeDamage(self, store, damage);
            say(ctx, "Sent " + amount + " projectile damage (power was " + before + "). See arrakis shield state.");
        }, "damage"));
        shield.addSubCommand(WorldPositional.build("state", LANG + "shield.state.desc", (ctx, world, store, args) -> {
            if (!ctx.isPlayer() || ctx.senderAsPlayerRef() == null) {
                say(ctx, "Run this as a player.");
                return;
            }
            Ref<EntityStore> self = ctx.senderAsPlayerRef();
            HoltzmanSettings cfg = shieldSettings.get();
            boolean selected = HoltzmanField.selected(store, self, cfg);
            float have = ArrakisPower.get(store, self);
            boolean alive = store.getComponent(self, com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent.getComponentType()) == null;
            java.util.UUID id = store.getComponent(self, com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType()).getUuid();
            HoltzmanState state = HoltzmanState.of(id);
            long now = System.nanoTime();
            boolean cooling = state.cooling(id, now);
            say(ctx, "field " + (HoltzmanField.hasField(store, self, cfg) ? "UP" : "down") + ", should be up " + HoltzmanRules.raised(selected, alive, have, cooling)
                    + "; Holtzman selected off-hand item " + selected + ", power " + have + ", PowerPerDamage " + cfg.getPowerPerDamage());
            say(ctx, String.format(Locale.ROOT, "cooldown %.2f s (base %.2f, %d modifiers), remaining %.2f s; last shot %s; projectiles of yours seen %d, refused shots %d",
                    ShieldCooldown.secondsFor(id), ShieldCooldown.baseSeconds(), ShieldCooldown.modifierCount(), state.remaining(id, now),
                    state.lastShot == ShieldCooldown.NONE ? "never (or cleared)" : String.format(Locale.ROOT, "%.2f s ago", (now - state.lastShot) / 1e9),
                    state.projectiles, state.refusals));
        }));
        shield.addSubCommand(WorldPositional.build("shot", LANG + "shield.shot.desc", (ctx, world, store, args) -> {
            // Counts as a projectile launched by you, by the same call the projectile hooks make, without a gun.
            if (!ctx.isPlayer() || ctx.senderAsPlayerRef() == null) {
                say(ctx, "Run this as a player.");
                return;
            }
            java.util.UUID id = store.getComponent(ctx.senderAsPlayerRef(), com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType()).getUuid();
            ShieldShots.shotBy(id);
            say(ctx, "Counted as a shot: the field drops and the cooldown (" + ShieldCooldown.secondsFor(id) + " s) starts.");
        }));
        shield.addSubCommand(WorldPositional.build("cooldown", LANG + "shield.cooldown.desc", (ctx, world, store, args) -> {
            if (args.length == 0) {
                say(ctx, "Cooldown base " + ShieldCooldown.baseSeconds() + " s (data file " + shieldSettings.get().getCooldownSeconds() + "), "
                        + ShieldCooldown.modifierCount() + " modifiers registered.");
                return;
            }
            try {
                double seconds = Double.parseDouble(args[0]);
                if (seconds < 0) {
                    throw new NumberFormatException();
                }
                ShieldCooldown.setOverride(seconds);
                say(ctx, "Cooldown is now " + seconds + " s (not saved; a restart goes back to " + shieldSettings.get().getCooldownSeconds() + ").");
            } catch (NumberFormatException e) {
                say(ctx, "Not a number of seconds: " + args[0]);
            }
        }, "seconds"));
        shield.addSubCommand(WorldPositional.build("diag", LANG + "shield.diag.desc", (ctx, world, store, args) -> {
            java.util.Map<String, Integer> warnings = RangedWatch.warnings();
            say(ctx, "Ranged check " + (shieldSettings.get().isRangedDiagnostic() ? "on" : "OFF") + "; " + warnings.size() + " root(s) seen ending with no player projectile:");
            for (java.util.Map.Entry<String, Integer> w : warnings.entrySet()) {
                say(ctx, "  " + w.getKey() + " x" + w.getValue());
            }
            say(ctx, "Ignored roots: " + String.join(", ", shieldSettings.get().getIgnoredRoots()));
        }));
        shield.addSubCommand(WorldPositional.build("hooktest", LANG + "shield.hooktest.desc", (ctx, world, store, args) -> {
            // Development: fires darts and a vanilla arrow with dummies as the shooters and reports whether the hooks saw a player shot.
            ShieldHookSelfTest.run(world, line -> say(ctx, line));
        }));
        arrakis.addSubCommand(shield);
        AbstractCommandCollection hud = new AbstractCommandCollection("hud", LANG + "hud.desc") {
        };
        hud.addSubCommand(WorldPositional.build("tune", LANG + "hud.tune.desc", (ctx, world, store, args) -> {
            // Live, not saved: with no argument it lists the values; with a name and a value it sets one and the displays are rebuilt.
            if (args.length == 0) {
                for (String line : hudSettings.get().listing()) {
                    say(ctx, line);
                }
            } else if (args.length == 1) {
                say(ctx, "Give a value too: arrakis hud tune <name> <value>");
            } else if (hudSettings.get().tune(args[0], args[1])) {
                say(ctx, args[0] + " is now " + args[1] + " (not saved)");
            } else {
                say(ctx, "No such setting, or the value does not fit: " + args[0] + " " + args[1] + " (arrakis hud tune lists them)");
            }
        }, "name", "value"));
        hud.addSubCommand(WorldPositional.build("rig", LANG + "hud.rig.desc", (ctx, world, store, args) -> {
            // "rig [tool]" shows the tool panel for a few seconds; "rig ammo <n>" shows a test number in AMMO for a few seconds.
            if (!ctx.isPlayer() || ctx.senderAsPlayerRef() == null) {
                say(ctx, "Run this as a player.");
                return;
            }
            PlayerRef self = store.getComponent(ctx.senderAsPlayerRef(), PlayerRef.getComponentType());
            if (args.length >= 1 && args[0].equalsIgnoreCase("ammo")) {
                try {
                    AmmoReadout.rig(self.getUuid(), Integer.parseInt(args.length > 1 ? args[1] : "117"), 5);
                    say(ctx, "AMMO shows the test number for 5 seconds.");
                } catch (NumberFormatException e) {
                    say(ctx, "Not a whole number: " + args[1]);
                }
                return;
            }
            String wanted = args.length >= 1 ? args[0] : "Arrakis_Compactor";
            String tool = null;
            for (String id : hudSettings.get().getToolItems()) {
                if (id.equalsIgnoreCase(wanted) || id.toLowerCase().endsWith("_" + wanted.toLowerCase())) {
                    tool = id;
                }
            }
            if (tool == null) {
                say(ctx, "Not one of the tool panel items: " + String.join(", ", hudSettings.get().getToolItems()));
                return;
            }
            HudSystem.rigTool(self.getUuid(), tool, 5);
            say(ctx, "The tool panel shows " + tool + " for 5 seconds.");
        }, "what", "value"));
        arrakis.addSubCommand(hud);
        return arrakis;
    }

    private static void report(CommandContext ctx, PlayerRef target, float now) {
        if (now < 0) {
            say(ctx, target.getUsername() + " has no Arrakis_Power stat (is the asset pack loaded?)");
        } else {
            say(ctx, String.format(Locale.ROOT, "%s power is now %.2f", target.getUsername(), now));
        }
    }

    private static void reportAmmo(CommandContext ctx, PlayerRef target, float now) {
        if (now < 0) {
            say(ctx, target.getUsername() + " has no Arrakis_Ammo stat (is the asset pack loaded?)");
        } else {
            say(ctx, String.format(Locale.ROOT, "%s ammo is now %.2f", target.getUsername(), now));
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
