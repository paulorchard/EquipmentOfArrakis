package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CommandRoot;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToggleRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldPositional;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;

/**
 * <pre>
 * /arrakis suspensor state                                   your belt: on or off, armed, flying, Mana, the speeds and cost in force
 * /arrakis suspensor set horizontal vertical [manaPerSecond]  live tuning, saved to the config; players who have the belt on get it on the next tick
 * /arrakis suspensor off                                     turn your belt off
 * /arrakis suspensor window <seconds> [afterJump|groundOpen] [reopenSeconds]   the tap gate, live and saved (0 = off)
 * /arrakis suspensor descent <speed>                         the belt's gravity in blocks a second (0 = off: hover), live and saved
 * /arrakis suspensor tune <key> <value>                      ascendFraction, debounceTicks, groundTicks, takeoffGrace, refreshSeconds, descentCostFactor
 * /arrakis suspensor probe on|off                            TEST DEVICE: log the flags, speeds and descent of your flight (INFO)
 * </pre>
 */
final class SuspensorCommands {

    private static final String LANG = CommandRoot.LANG + "suspensor.";

    private SuspensorCommands() {
    }

    static void register(AbstractCommandCollection group, Runnable saveConfig) {
        group.addSubCommand(WorldPositional.build("state", LANG + "state.desc", (c, w, s, a) -> state(c, s)));
        group.addSubCommand(WorldPositional.build("set", LANG + "set.desc", (c, w, s, a) -> set(c, a, saveConfig), "horizontal", "vertical", "manaPerSecond"));
        group.addSubCommand(WorldPositional.build("off", LANG + "off.desc", (c, w, s, a) -> off(c, s)));
        group.addSubCommand(WorldPositional.build("window", LANG + "window.desc", (c, w, s, a) -> window(c, a, saveConfig), "seconds", "mode", "reopenSeconds"));
        group.addSubCommand(WorldPositional.build("descent", LANG + "descent.desc", (c, w, s, a) -> descent(c, a, saveConfig), "speed"));
        group.addSubCommand(WorldPositional.build("tune", LANG + "tune.desc", (c, w, s, a) -> tune(c, a, saveConfig), "key", "value"));
        // TEST DEVICE: the probe
        group.addSubCommand(WorldPositional.build("probe", LANG + "probe.desc", (c, w, s, a) -> probe(c, s, a), "mode"));
    }

    private static Double number(String text) {
        try {
            return text == null || text.isBlank() ? null : Double.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void set(CommandContext c, String[] args, Runnable saveConfig) {
        Double horizontal = args.length > 0 ? number(args[0]) : null;
        Double vertical = args.length > 1 ? number(args[1]) : null;
        Double mana = args.length > 2 ? number(args[2]) : null;
        boolean manaGiven = args.length > 2;
        if (horizontal == null || vertical == null || horizontal < 0 || vertical < 0 || (manaGiven && (mana == null || mana < 0))) {
            CoreModule.say(c, "usage: suspensor set <horizontal> <vertical> [manaPerSecond] (numbers, 0 or more)");
            return;
        }
        SuspensorConfig config = Suspensors.config();
        config.set(horizontal, vertical, mana);
        saveConfig.run();
        CoreModule.say(c, "Suspensor Belt: horizontal " + config.horizontalSpeed() + ", vertical " + config.verticalSpeed() + ", " + config.manaPerSecond()
                + " Mana a second (saved; players with the belt on get it on the next tick)");
    }

    private static void window(CommandContext c, String[] args, Runnable saveConfig) {
        Double seconds = args.length > 0 ? number(args[0]) : null;
        String mode = args.length > 1 ? args[1] : null;
        boolean modeOk = mode == null || "afterJump".equalsIgnoreCase(mode) || "groundOpen".equalsIgnoreCase(mode);
        Double reopen = args.length > 2 ? number(args[2]) : null;
        if (seconds == null || seconds < 0 || !modeOk || (args.length > 2 && (reopen == null || reopen < 0))) {
            CoreModule.say(c, "usage: suspensor window <seconds, 0 = off> [afterJump|groundOpen] [reopenSeconds]");
            return;
        }
        SuspensorConfig config = Suspensors.config();
        config.setWindow(seconds, mode, reopen);
        saveConfig.run();
        CoreModule.say(c, windowText(config) + " (saved; players with the belt on get it on the next tick)");
    }

    private static String windowText(SuspensorConfig config) {
        return config.tapWindow() <= 0 ? "tap gate OFF (fly is Allowed whenever the belt is armed)"
                : "tap gate: " + (config.tapMode() == BeltMachine.GateMode.AFTER_JUMP ? "afterJump" : "groundOpen") + ", window " + config.tapWindow() + " s"
                + (config.tapMode() == BeltMachine.GateMode.GROUND_OPEN ? ", reopen after " + config.reopenSeconds() + " s on the ground" : "");
    }

    private static void descent(CommandContext c, String[] args, Runnable saveConfig) {
        Double speed = args.length > 0 ? number(args[0]) : null;
        if (speed == null || speed < 0 || speed > SuspensorConfig.MAX_DESCENT_SPEED) {
            CoreModule.say(c, "usage: suspensor descent <blocks a second, 0 = off, at most " + SuspensorConfig.MAX_DESCENT_SPEED + ">");
            return;
        }
        Suspensors.config().setDescentSpeed(speed);
        saveConfig.run();
        CoreModule.say(c, speed <= 0 ? "descent OFF: the belt hovers (saved)" : "descent " + speed + " blocks a second (saved; players flying get it on the next tick)");
    }

    private static void tune(CommandContext c, String[] args, Runnable saveConfig) {
        String key = args.length > 0 ? args[0] : null;
        Double value = args.length > 1 ? number(args[1]) : null;
        if (key == null || value == null || value < 0) {
            CoreModule.say(c, "usage: suspensor tune <ascendFraction|debounceTicks|groundTicks|takeoffGrace|refreshSeconds|descentCostFactor> <value, 0 or more>");
            return;
        }
        SuspensorConfig config = Suspensors.config();
        if (key.equalsIgnoreCase("descentSpeed") && value > SuspensorConfig.MAX_DESCENT_SPEED) {
            CoreModule.say(c, "refused: DescentSpeed is at most " + SuspensorConfig.MAX_DESCENT_SPEED);
            return;
        }
        if (!config.tune(key, value)) {
            CoreModule.say(c, "unknown key '" + key + "': ascendFraction, debounceTicks, groundTicks, takeoffGrace, refreshSeconds, descentCostFactor (or descentSpeed)");
            return;
        }
        saveConfig.run();
        CoreModule.say(c, descentText(config) + " (saved)");
    }

    private static String descentText(SuspensorConfig config) {
        return "descent " + (config.descentSpeed() <= 0 ? "OFF" : config.descentSpeed() + " blocks a second") + ", ascendFraction " + config.ascendFraction()
                + ", debounceTicks " + config.debounceTicks() + ", groundTicks " + config.groundTicks() + ", takeoffGrace " + config.takeoffGrace()
                + ", refreshSeconds " + config.refreshSeconds() + ", descentCostFactor " + config.descentCostFactor();
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
        ToggleRegistry.get().set(Suspensors.TOGGLE, false, store, ref, player);
        CoreModule.say(c, "Suspensor Belt: OFF");
    }

    // ---- TEST DEVICE: the probe and the push test

    private static PlayerRef caller(CommandContext c, Store<EntityStore> store) {
        if (!c.isPlayer() || c.senderAsPlayerRef() == null) {
            CoreModule.say(c, "Run this as a player in the game.");
            return null;
        }
        return store.getComponent(c.senderAsPlayerRef(), PlayerRef.getComponentType());
    }

    private static void probe(CommandContext c, Store<EntityStore> store, String[] args) {
        PlayerRef player = caller(c, store);
        if (player == null) {
            return;
        }
        String mode = args.length > 0 ? args[0] : null;
        if ("on".equalsIgnoreCase(mode)) {
            TestDevices.probeOn(player.getUuid());
            CoreModule.say(c, "probe on: every flip of flying, jumping, crouching, onGround, falling and fallingFar is logged (INFO), and every 0.25 s while you fly "
                    + "the flags, client and actual vertical speed, wish movement, Mana and armed");
        } else if ("off".equalsIgnoreCase(mode)) {
            TestDevices.probeOff(player.getUuid());
            CoreModule.say(c, "probe off");
        } else {
            CoreModule.say(c, "usage: suspensor probe on | off");
        }
    }

    private static void state(CommandContext c, Store<EntityStore> store) {
        SuspensorConfig config = Suspensors.config();
        String numbers = "speeds " + config.horizontalSpeed() + "/" + config.verticalSpeed() + " (horizontal/vertical), " + config.manaPerSecond()
                + " Mana a second, resume at " + config.resumeMana() + "; " + windowText(config) + "; " + descentText(config);
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
        String belt = machine == null || !machine.isOn() ? "off"
                : "on, " + (machine.armed() ? "armed" : "unarmed") + ", flying " + (states != null && states.getMovementStates().flying);
        CoreModule.say(c, "Suspensor Belt: " + belt + "; Mana " + (power == null ? "n/a" : power.get(store, ref)) + "; " + numbers);
        if (machine != null && machine.isOn()) {
            BeltMachine.GateInfo gate = machine.gate();
            CoreModule.say(c, "gate " + (config.tapWindow() <= 0 ? "off" : gate.open() ? "OPEN" : "closed") + "; windows opened " + gate.windows() + ", settings writes " + gate.pushes()
                    + ", flights started inside a window " + gate.flightsInWindow() + ", flights started with a gap to the jump start " + gate.flightsWithGap());
        }
        if (machine != null && machine.isOn()) {
            BeltMachine.DescentInfo d = machine.descent();
            CoreModule.say(c, "descent " + d.state() + ": own " + String.format(java.util.Locale.ROOT, "%.2f", d.own()) + ", vy now " + String.format(java.util.Locale.ROOT, "%.2f", d.vy())
                    + ", push " + (d.pushInForce() ? "in force" : "none") + ", sends " + d.sends() + " (refresh " + d.refreshes() + ", drift " + d.drifts() + "), state changes "
                    + d.transitions() + ", ground exits " + d.groundExits());
        }
        if (manager != null) {
            BeltMachine.FlySettings now = Suspensors.read(manager.getSettings());
            CoreModule.say(c, "movement settings now: fly " + now.mode() + ", h " + now.horizontal() + ", v " + now.vertical()
                    + "; last pushed " + (machine == null ? "n/a" : String.valueOf(machine.pushed()))
                    + "; snapshot " + (machine == null || machine.snapshot() == null ? "none" : String.valueOf(machine.snapshot()))
                    + "; settings writes " + (machine == null ? 0 : machine.pushes()));
        }
    }
}
