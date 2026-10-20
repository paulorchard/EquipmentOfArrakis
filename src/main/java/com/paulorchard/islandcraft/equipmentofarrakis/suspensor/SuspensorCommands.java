package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
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

import java.util.Locale;

/**
 * <pre>
 * /arrakis suspensor state                          your belt: mark, armed, flying, Mana, the speeds in force, the snapshot
 * /arrakis suspensor set mk2|mk3 horizontal vertical   live tuning, saved to the config; players who have that mark on get it on the next tick
 * /arrakis suspensor off                            turn your belt off
 * /arrakis suspensor mk1 state                      Mk I: the route, the trigger of your mark, the numbers, ARMED or ENGAGED, the counters
 * /arrakis suspensor mk1 route settings|velocity    the way the slow descent is applied (saved)
 * /arrakis suspensor mk1 field drag|mass            which physics number the settings route changes (saved)
 * /arrakis suspensor mk1 set speed|threshold|ticks|factor|push|terminal|mana value   live tuning (saved)
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
            CoreModule.say(c, "movement settings now: fly " + now.mode() + ", h " + now.horizontal() + ", v " + now.vertical() + ", mass " + manager.getSettings().mass
                    + ", drag " + manager.getSettings().dragCoefficient
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
        } else if ("route".equalsIgnoreCase(what) && name != null && (name.equalsIgnoreCase("settings") || name.equalsIgnoreCase("velocity"))) {
            m.route(name.equalsIgnoreCase("velocity") ? Mk1Config.Route.VELOCITY : Mk1Config.Route.SETTINGS);
            saveConfig.run();
            CoreModule.say(c, "Mk I route: " + m.route().name().toLowerCase(Locale.ROOT) + " (saved; a descent already engaged ends the way it began)");
        } else if ("field".equalsIgnoreCase(what) && name != null && (name.equalsIgnoreCase("drag") || name.equalsIgnoreCase("mass"))) {
            m.field(name.toLowerCase(Locale.ROOT));
            saveConfig.run();
            CoreModule.say(c, "Mk I settings route changes: " + (m.massField() ? "mass (divided by the factor)" : "dragCoefficient (multiplied by the factor)") + " (saved)");
        } else if ("set".equalsIgnoreCase(what) && name != null) {
            Float v = number(value);
            if (v == null || !setNumber(m, name.toLowerCase(Locale.ROOT), v)) {
                CoreModule.say(c, "usage: suspensor mk1 set speed|threshold|ticks|factor|push|terminal|mana <number> (factor 0 = derived)");
                return;
            }
            saveConfig.run();
            CoreModule.say(c, "Mk I " + name + " = " + v + " (saved); factor in force " + m.dragFactor());
        } else {
            CoreModule.say(c, "usage: suspensor mk1 state | route settings|velocity | field drag|mass | set speed|threshold|ticks|factor|push|terminal|mana <number>");
        }
    }

    private static boolean setNumber(Mk1Config m, String name, float v) {
        switch (name) {
            case "speed" -> m.descentSpeed(v);
            case "threshold" -> m.speedThreshold(v);
            case "ticks" -> m.speedTicks(Math.round(v));
            case "factor" -> m.dragFactor(v);
            case "push" -> m.pushTicks(Math.round(v));
            case "terminal" -> m.terminalSpeed(v);
            case "mana" -> m.manaPerSecond(v);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void mk1State(CommandContext c, Store<EntityStore> store, Mk1Config m) {
        CoreModule.say(c, "Mk I: route " + m.route().name().toLowerCase(Locale.ROOT) + ", listed " + m.listed() + ", target " + m.descentSpeed() + " blocks a second, "
                + (m.massField() ? "mass divided" : "drag multiplied") + " by " + m.dragFactor() + (m.dragFactorSetting() > 0 ? " (set)" : " (derived from terminal " + m.terminalSpeed() + ")")
                + ", speed trigger " + m.speedThreshold() + " blocks a second for " + m.speedTicks() + " ticks, push every " + m.pushTicks() + " ticks (velocity route), "
                + m.manaPerSecond() + " Mana a second");
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
        double landing = machine.lastLanding();
        CoreModule.say(c, markName(machine.mark()) + ": " + (machine.engaged() ? "ENGAGED" : "ARMED") + ", trigger " + trigger + "; engages " + machine.engages() + ", ends "
                + machine.ends() + ", settings pushes " + machine.pushes() + ", velocity pushes " + machine.velocityPushes() + ", last landing speed "
                + (Double.isNaN(landing) ? "none yet" : String.format(Locale.ROOT, "%.2f", landing)));
    }

    // ---- probe and drop

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
