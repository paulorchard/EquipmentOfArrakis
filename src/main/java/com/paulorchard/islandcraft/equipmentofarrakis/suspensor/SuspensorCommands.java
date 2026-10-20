package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CommandRoot;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToggleRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldAccess;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldPositional;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;

/**
 * <pre>
 * /arrakis suspensor state                          your belt: mark, armed, flying, Mana, the speeds in force, the snapshot
 * /arrakis suspensor set mk2|mk3 horizontal vertical   live tuning, saved to the config; players who have that mark on get it on the next tick
 * /arrakis suspensor off                            turn your belt off
 * /arrakis suspensor mk1 state                      Mk I: the route, the trigger of your mark, the numbers, ARMED or ENGAGED, the counters
 * /arrakis suspensor mk1 route flyglide|airdrag|counter   the way the slow descent is applied (saved)
 * /arrakis suspensor mk1 total true|false           whether the counter way reads the client velocity as the total
 * /arrakis suspensor mk1 set speed|threshold|ticks|glide|tolerance|push|airdrag|gravity|mana value   live tuning (saved)
 * /arrakis suspensor glider give hand|utility | take | effect on|off   the glider item and the falling signal by hand (test helpers)
 * /arrakis suspensor mk1 set trigger falling|speed|farfall | fallingticks n | signal level|pulse | pulse s | pulseretry s | pulseretries n   what the glider route takes as falling (saved)
 * /arrakis suspensor impulse y [resistance]|clear   one Set velocity instruction and a two second watch (dev)
 * /arrakis suspensor probe on [chat]|off            log every flip of falling, fallingFar, onGround with height, time and speed
 * /arrakis suspensor drop blocks                    teleport yourself that many blocks straight up (operator test helper)
 * </pre>
 */
final class SuspensorCommands {

    private static final String LANG = CommandRoot.LANG + "suspensor.";

    private SuspensorCommands() {
    }

    static void register(AbstractCommandCollection group, Runnable saveConfig) {
        group.addSubCommand(WorldPositional.build("state", LANG + "state.desc", (c, w, s, a) -> state(c, s)));
        group.addSubCommand(WorldPositional.build("set", LANG + "set.desc", (c, w, s, a) -> set(c, a, saveConfig), "mark", "horizontal", "vertical"));
        group.addSubCommand(WorldPositional.build("off", LANG + "off.desc", (c, w, s, a) -> off(c, s)));
        group.addSubCommand(WorldPositional.build("mk1", LANG + "mk1.desc", (c, w, s, a) -> mk1(c, s, a, saveConfig), "what", "name", "value"));
        group.addSubCommand(WorldPositional.build("impulse", LANG + "impulse.desc", (c, w, s, a) -> impulse(c, s, a), "y", "resistance"));
        group.addSubCommand(WorldPositional.build("glider", LANG + "glider.desc", (c, w, s, a) -> glider(c, s, a), "what", "value"));
        group.addSubCommand(WorldPositional.build("probe", LANG + "probe.desc", (c, w, s, a) -> probe(c, s, a), "mode", "chat"));
        group.addSubCommand(WorldPositional.build("drop", LANG + "drop.desc", (c, w, s, a) -> drop(c, w, s, a), "blocks"));
    }

    private static Float number(String text) {
        try {
            return text == null ? null : Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String arg(String[] args, int i) {
        return args != null && i < args.length && args[i] != null && !args[i].isBlank() ? args[i].trim() : null;
    }

    private static void set(CommandContext c, String[] args, Runnable saveConfig) {
        String which = args.length > 0 && args[0] != null ? args[0].toLowerCase(Locale.ROOT) : "";
        Float horizontal = args.length > 1 ? number(args[1]) : null;
        Float vertical = args.length > 2 ? number(args[2]) : null;
        BeltMachine.Mark mark = switch (which) {
            case "mk2" -> BeltMachine.Mark.II;
            case "mk3" -> BeltMachine.Mark.III;
            default -> null;
        };
        if (mark == null || horizontal == null || vertical == null || horizontal < 0 || vertical < 0) {
            CoreModule.say(c, "usage: suspensor set mk2|mk3 <horizontal> <vertical> (speeds, 0 or more)");
            return;
        }
        Suspensors.config().of(mark).set(horizontal, vertical);
        saveConfig.run();
        CoreModule.say(c, "Suspensor " + which + ": horizontal " + horizontal + ", vertical " + vertical + " (saved; players with it on get it on the next tick)");
    }

    private static void off(CommandContext c, Store<EntityStore> store) {
        if (!c.isPlayer() || c.senderAsPlayerRef() == null) {
            CoreModule.say(c, "Run this as a player in the game.");
            return;
        }
        Ref<EntityStore> ref = c.senderAsPlayerRef();
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        BeltMachine machine = Suspensors.peek(player.getUuid());
        if (machine == null || !machine.isOn()) {
            CoreModule.say(c, "Your belt is already off.");
            return;
        }
        ToggleRegistry.get().set(toggleId(machine.mark()), false, store, ref, player);
        CoreModule.say(c, "Suspensor Belt: OFF");
    }

    static String toggleId(BeltMachine.Mark mark) {
        return switch (mark) {
            case III -> Suspensors.TOGGLE_III;
            case II -> Suspensors.TOGGLE_II;
            case I_FAR -> Suspensors.TOGGLE_I_FAR;
            case I_SPEED -> Suspensors.TOGGLE_I_SPEED;
        };
    }

    private static String markName(BeltMachine.Mark mark) {
        return switch (mark) {
            case I_FAR -> "Mk I (far fall)";
            case I_SPEED -> "Mk I (speed)";
            case II -> "Mk II";
            case III -> "Mk III";
        };
    }

    private static void state(CommandContext c, Store<EntityStore> store) {
        SuspensorConfig config = Suspensors.config();
        String numbers = "Mk III " + config.mk3().horizontalSpeed() + "/" + config.mk3().verticalSpeed() + ", Mk II " + config.mk2().horizontalSpeed() + "/"
                + config.mk2().verticalSpeed() + " (horizontal/vertical), " + config.manaPerSecond() + " Mana a second, resume at " + config.resumeMana();
        if (!c.isPlayer() || c.senderAsPlayerRef() == null) {
            CoreModule.say(c, "Suspensor: " + Suspensors.count() + " belt(s) in memory; " + numbers);
            return;
        }
        Ref<EntityStore> ref = c.senderAsPlayerRef();
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        BeltMachine machine = Suspensors.peek(player.getUuid());
        MovementManager manager = store.getComponent(ref, MovementManager.getComponentType());
        MovementStatesComponent states = store.getComponent(ref, MovementStatesComponent.getComponentType());
        Power power = Power.get();
        String belt;
        if (machine == null || !machine.isOn()) {
            belt = "off";
        } else if (machine.mark().falls()) {
            belt = markName(machine.mark()) + ", " + (machine.engaged() ? "ENGAGED" : "ARMED");
        } else {
            belt = markName(machine.mark()) + ", " + (machine.armed() ? "armed" : "unarmed") + ", flying " + (states != null && states.getMovementStates().flying);
        }
        CoreModule.say(c, "Suspensor Belt: " + belt + "; Mana " + (power == null ? "n/a" : power.get(store, ref)) + "; " + numbers);
        if (manager != null) {
            BeltMachine.FlySettings now = Suspensors.read(manager.getSettings());
            CoreModule.say(c, "movement settings now: fly " + now.mode() + ", h " + now.horizontal() + ", v " + now.vertical() + ", airDrag " + manager.getSettings().airDragMin + "/" + manager.getSettings().airDragMax
                    + "; last pushed " + (machine == null ? "n/a" : String.valueOf(machine.pushed()))
                    + "; snapshot " + (machine == null || machine.snapshot() == null ? "none" : String.valueOf(machine.snapshot()))
                    + "; settings writes " + (machine == null ? 0 : machine.pushes()));
        }
    }

    // ---- Mk I

    private static void mk1(CommandContext c, Store<EntityStore> store, String[] args, Runnable saveConfig) {
        Mk1Config m = Suspensors.config().mk1();
        String what = arg(args, 0);
        String name = arg(args, 1);
        String value = arg(args, 2);
        if (what == null || "state".equalsIgnoreCase(what)) {
            mk1State(c, store, m);
        } else if ("route".equalsIgnoreCase(what) && name != null && List.of("flyglide", "airdrag", "counter").contains(name.toLowerCase(Locale.ROOT))) {
            m.way(BeltMachine.Way.valueOf(name.toUpperCase(Locale.ROOT)));
            saveConfig.run();
            CoreModule.say(c, "Mk I route: " + m.way().name().toLowerCase(Locale.ROOT) + " (saved; a descent already engaged ends the way it began)");
        } else if ("total".equalsIgnoreCase(what) && name != null) {
            m.reportsTotal(Boolean.parseBoolean(name));
            saveConfig.run();
            CoreModule.say(c, "Mk I counter: the client velocity is read as " + (m.reportsTotal() ? "the total (own + external)" : "the own part only") + " (saved)");
        } else if ("set".equalsIgnoreCase(what) && name != null && name.equalsIgnoreCase("trigger")) {
            BeltMachine.Trigger t = value == null ? null : switch (value.toLowerCase(Locale.ROOT)) {
                case "falling" -> BeltMachine.Trigger.FALLING;
                case "speed" -> BeltMachine.Trigger.SPEED;
                case "farfall" -> BeltMachine.Trigger.FARFALL;
                default -> null;
            };
            if (t == null) {
                CoreModule.say(c, "usage: suspensor mk1 set trigger falling|speed|farfall");
                return;
            }
            m.trigger(t);
            saveConfig.run();
            CoreModule.say(c, "Mk I glider trigger: " + t.name().toLowerCase(Locale.ROOT) + " (saved)");
        } else if ("set".equalsIgnoreCase(what) && name != null && name.equalsIgnoreCase("signal")) {
            BeltMachine.Signal sig = "level".equalsIgnoreCase(value) ? BeltMachine.Signal.LEVEL : "pulse".equalsIgnoreCase(value) ? BeltMachine.Signal.PULSE : null;
            if (sig == null) {
                CoreModule.say(c, "usage: suspensor mk1 set signal level|pulse");
                return;
            }
            m.signal(sig);
            saveConfig.run();
            CoreModule.say(c, "Mk I glider signal: " + sig.name().toLowerCase(Locale.ROOT) + " (saved; the next descent uses it)");
        } else if ("route".equalsIgnoreCase(what) && name != null && name.equalsIgnoreCase("glider")) {
            m.way(BeltMachine.Way.GLIDER);
            saveConfig.run();
            CoreModule.say(c, "Mk I route: glider (saved; switch the mark off and on to get the item)");
        } else if ("set".equalsIgnoreCase(what) && name != null) {
            Float v = number(value);
            if (v == null || !setNumber(m, name.toLowerCase(Locale.ROOT), v)) {
                CoreModule.say(c, "usage: suspensor mk1 set speed|threshold|ticks|glide|tolerance|push|airdrag|gravity|mana <number> (airdrag 0 = derived)");
                return;
            }
            saveConfig.run();
            CoreModule.say(c, "Mk I " + name + " = " + v + " (saved); air drag in force " + String.format(Locale.ROOT, "%.5f", m.airDrag()));
        } else {
            CoreModule.say(c, "usage: suspensor mk1 state | route flyglide|airdrag|counter | total true|false | set speed|threshold|ticks|glide|tolerance|push|airdrag|gravity|mana <number>");
        }
    }

    private static boolean setNumber(Mk1Config m, String name, float v) {
        switch (name) {
            case "speed" -> m.descentSpeed(v);
            case "threshold" -> m.speedThreshold(v);
            case "ticks" -> m.speedTicks(Math.round(v));
            case "glide" -> m.glideHorizontal(v);
            case "tolerance" -> m.holdTolerance(v);
            case "push" -> m.pushTicks(Math.round(v));
            case "airdrag" -> m.airDrag(v);
            case "gravity" -> m.gravityStep(v);
            case "mana" -> m.manaPerSecond(v);
            case "fallingticks" -> m.fallingTicks(Math.round(v));
            case "pulse" -> m.pulseSeconds(v);
            case "pulseretry" -> m.pulseRetrySeconds(v);
            case "pulseretries" -> m.pulseRetries(Math.round(v));
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void mk1State(CommandContext c, Store<EntityStore> store, Mk1Config m) {
        CoreModule.say(c, "Mk I: route " + m.way().name().toLowerCase(Locale.ROOT) + (m.way() == BeltMachine.Way.GLIDER ? " (trigger " + m.trigger().name().toLowerCase(Locale.ROOT)
                + ", falling ticks " + m.fallingTicks() + ", signal " + m.signal().name().toLowerCase(Locale.ROOT) + (m.signal() == BeltMachine.Signal.PULSE ? " " + m.pulseSeconds() + " s, retry after "
                + m.pulseRetrySeconds() + " s, up to " + m.pulseRetries() + " time(s)" : "") + ")" : "") + ", listed " + m.listed() + ", target " + m.descentSpeed() + " blocks a second, speed trigger "
                + m.speedThreshold() + " blocks a second for " + m.speedTicks() + " ticks; glide horizontal " + m.glideHorizontal() + ", hold tolerance " + m.holdTolerance()
                + ", refresh at most every " + m.pushTicks() + " ticks; air drag " + String.format(Locale.ROOT, "%.5f", m.airDrag()) + (m.airDragSetting() > 0 ? " (set)" : " (derived, step "
                + m.gravityStep() + ")") + "; counter reads the client velocity as " + (m.reportsTotal() ? "total" : "own") + "; " + m.manaPerSecond() + " Mana a second");
        if (!c.isPlayer() || c.senderAsPlayerRef() == null) {
            return;
        }
        PlayerRef player = store.getComponent(c.senderAsPlayerRef(), PlayerRef.getComponentType());
        BeltMachine machine = Suspensors.peek(player.getUuid());
        if (machine == null || machine.mark() == null || !machine.mark().falls()) {
            CoreModule.say(c, "You have no Mk I mark on.");
            return;
        }
        String trigger = machine.mark() == BeltMachine.Mark.I_FAR ? "fallingFar true while in the air" : "falling and faster than " + m.speedThreshold() + " blocks a second on "
                + m.speedTicks() + " ticks in a row";
        if (m.way() == BeltMachine.Way.GLIDER) {
            trigger = "glider route, trigger " + m.trigger().name().toLowerCase(Locale.ROOT) + ", signal " + m.signal().name().toLowerCase(Locale.ROOT) + ", pulses sent " + machine.pulses()
                    + " (retries " + machine.retries() + ")";
        }
        double landing = machine.lastLanding();
        com.hypixel.hytale.server.core.entity.entities.Player who = store.getComponent(c.senderAsPlayerRef(), com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
        com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent effects = store.getComponent(c.senderAsPlayerRef(),
                com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent.getComponentType());
        int signal = com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect.getAssetMap().getIndex(SuspensorSystem.FALLING_EFFECT);
        MovementStatesComponent movement = store.getComponent(c.senderAsPlayerRef(), MovementStatesComponent.getComponentType());
        CoreModule.say(c, "glider: item in the Utility slot " + GliderItems.inUtility(store, c.senderAsPlayerRef())
                + ", item placed by the belt " + machine.itemPlaced() + ", falling signal " + (effects != null && signal >= 0 && effects.hasEffect(signal)) + " (machine says " + machine.effectOn()
                + "), gliding flag " + (movement != null && movement.getMovementStates().gliding) + ", stat GlidingActive " + SuspensorSystem.glidingActive(store, c.senderAsPlayerRef()));
        CoreModule.say(c, markName(machine.mark()) + ": " + (machine.engaged() ? "ENGAGED (" + machine.engagedWay().name().toLowerCase(Locale.ROOT) + ")" : "ARMED") + ", trigger " + trigger
                + "; engages " + machine.engages() + ", ends " + machine.ends() + " (last: " + (machine.lastEnd().isEmpty() ? "none" : machine.lastEnd()) + "), settings pushes " + machine.pushes()
                + ", instruction refreshes " + machine.refreshes() + ", instruction active " + machine.instructionActive() + ", last landing speed "
                + (Double.isNaN(landing) ? "none yet" : String.format(Locale.ROOT, "%.2f", landing)));
    }

    // TEST DEVICE: the impulse probe belongs to the abandoned velocity routes; remove with them.
    /** One Set instruction of (0, y, 0) with the given resistance, and a two second watch of what the client reports. */
    private static void impulse(CommandContext c, Store<EntityStore> store, String[] args) {
        if (!c.isPlayer() || c.senderAsPlayerRef() == null) {
            CoreModule.say(c, "Run this as a player in the game.");
            return;
        }
        Ref<EntityStore> ref = c.senderAsPlayerRef();
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        Velocity velocity = store.getComponent(ref, Velocity.getComponentType());
        if (velocity == null) {
            CoreModule.say(c, "No velocity component on you.");
            return;
        }
        String first = arg(args, 0);
        if (first != null && first.equalsIgnoreCase("clear")) {
            velocity.addInstruction(new Vector3d(0, 0, 0), new VelocityConfig(), ChangeVelocityType.Set);
            Suspensors.impulseOn(player.getUuid(), new ImpulseWatch(System.currentTimeMillis(), "clear"));
            CoreModule.say(c, "impulse: sent Set (0, 0, 0) with the default config; watching 2 s");
            return;
        }
        Float y = number(first);
        Float resistance = arg(args, 1) == null ? Float.valueOf(1.0f) : number(arg(args, 1));
        if (y == null || resistance == null) {
            CoreModule.say(c, "usage: suspensor impulse <y> [resistance, default 1.0 = no decay] | clear   (careful: a downward push adds to a fall)");
            return;
        }
        velocity.addInstruction(new Vector3d(0, y, 0), Suspensors.resistance(resistance), ChangeVelocityType.Set);
        Suspensors.impulseOn(player.getUuid(), new ImpulseWatch(System.currentTimeMillis(), "y " + y + " r " + resistance));
        CoreModule.say(c, "impulse: sent Set (0, " + y + ", 0) with resistance " + resistance + " on ground and air; the client vertical speed is logged every 0.1 s for 2 s");
    }

    // ---- the glider by hand

    // TEST DEVICE: give, take and effect are manual controls for the proof runs, not part of the final belt.
    private static void glider(CommandContext c, Store<EntityStore> store, String[] args) {
        if (!c.isPlayer() || c.senderAsPlayerRef() == null) {
            CoreModule.say(c, "Run this as a player in the game.");
            return;
        }
        Ref<EntityStore> ref = c.senderAsPlayerRef();
        com.hypixel.hytale.server.core.entity.entities.Player who = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
        String what = arg(args, 0);
        String value = arg(args, 1);
        if (who == null || what == null) {
            CoreModule.say(c, "usage: suspensor glider give hand|utility | take | effect on|off");
        } else if (what.equalsIgnoreCase("give") && value != null && (value.equalsIgnoreCase("hand") || value.equalsIgnoreCase("utility"))) {
            CoreModule.say(c, "glider: " + GliderItems.give(store, ref, value.equalsIgnoreCase("utility")));
        } else if (what.equalsIgnoreCase("take")) {
            CoreModule.say(c, "glider: " + GliderItems.removeEverywhere(store, ref) + " removed from your inventory");
        } else if (what.equalsIgnoreCase("effect") && value != null && (value.equalsIgnoreCase("on") || value.equalsIgnoreCase("off"))) {
            com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent controller = store.getComponent(ref,
                    com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent.getComponentType());
            com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect effect =
                    com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect.getAssetMap().getAsset(SuspensorSystem.FALLING_EFFECT);
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (controller == null || effect == null) {
                CoreModule.say(c, "glider: no effect controller or the effect is not loaded");
            } else if (value.equalsIgnoreCase("on")) {
                controller.addEffect(ref, effect, 10f, com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior.OVERWRITE, store);
                Suspensors.probeEvent(player, "effect " + SuspensorSystem.FALLING_EFFECT + " APPLIED by hand for 10 s");
                CoreModule.say(c, "glider: the falling signal is on for 10 s");
            } else {
                SuspensorSystem.removeFalling(store, ref);
                Suspensors.probeEvent(player, "effect " + SuspensorSystem.FALLING_EFFECT + " REMOVED by hand");
                CoreModule.say(c, "glider: the falling signal is off");
            }
        } else {
            CoreModule.say(c, "usage: suspensor glider give hand|utility | take | effect on|off");
        }
    }

    // ---- probe and drop

    // TEST DEVICE: the fall probe is kept for tuning, but it is a developer tool.
    private static void probe(CommandContext c, Store<EntityStore> store, String[] args) {
        if (!c.isPlayer() || c.senderAsPlayerRef() == null) {
            CoreModule.say(c, "Run this as a player in the game.");
            return;
        }
        PlayerRef player = store.getComponent(c.senderAsPlayerRef(), PlayerRef.getComponentType());
        String mode = arg(args, 0);
        if ("on".equalsIgnoreCase(mode)) {
            Suspensors.probeOn(player.getUuid(), "chat".equalsIgnoreCase(arg(args, 1)));
            CoreModule.say(c, "probe on: every flip of falling, fallingFar and onGround is logged (INFO)" + ("chat".equalsIgnoreCase(arg(args, 1)) ? " and said here" : "")
                    + ", and the vertical speed every 0.25 s while a descent is engaged");
        } else if ("off".equalsIgnoreCase(mode)) {
            Suspensors.probeOff(player.getUuid());
            CoreModule.say(c, "probe off");
        } else {
            CoreModule.say(c, "usage: suspensor probe on [chat] | off");
        }
    }

    // TEST DEVICE: the drop helper (operator only) is kept for tests.
    private static void drop(CommandContext c, World world, Store<EntityStore> store, String[] args) {
        if (!c.isPlayer() || c.senderAsPlayerRef() == null) {
            CoreModule.say(c, "Run this as a player in the game.");
            return;
        }
        Ref<EntityStore> ref = c.senderAsPlayerRef();
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        Float blocks = number(arg(args, 0));
        if (blocks == null || transform == null) {
            CoreModule.say(c, "usage: suspensor drop <blocks to go straight up>");
            return;
        }
        Vector3d at = transform.getPosition();
        WorldAccess access = new WorldAccess(world);
        int x = (int) Math.floor(at.x);
        int z = (int) Math.floor(at.z);
        int feet = (int) Math.floor(at.y);
        int count = Math.round(blocks);
        String refusal = DropRules.refuse(feet, count, y -> !access.isLoaded(x, z) || access.block(x, y, z) > 0);
        if (refusal != null) {
            CoreModule.say(c, "No drop: " + refusal);
            return;
        }
        double y = at.y + count;
        store.addComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(world, new Vector3d(at.x, y, at.z), new Rotation3f()));
        CoreModule.say(c, "Taken " + count + " blocks up (y " + String.format(Locale.ROOT, "%.1f", y) + "); fall from here. Health " + SuspensorSystem.health(store, ref));
    }
}
