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
 * /arrakis suspensor probe on|off                            TEST DEVICE: log the flags, speeds and wish movement of your flight (INFO)
 * /arrakis suspensor push <y> [resistance|default]           TEST DEVICE: one external velocity Set (0, y, 0) while you fly; resistance 1 (the default here) keeps it
 * /arrakis suspensor push hold <y> <everyTicks> [resistance] TEST DEVICE: the same push every N ticks until push off
 * /arrakis suspensor push off                                TEST DEVICE: Set (0, 0, 0) with the default config and stop
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
        // TEST DEVICE: proofs A and B of the descent
        group.addSubCommand(WorldPositional.build("probe", LANG + "probe.desc", (c, w, s, a) -> probe(c, s, a), "mode"));
        group.addSubCommand(WorldPositional.build("push", LANG + "push.desc", (c, w, s, a) -> push(c, s, a), "first", "second", "third", "fourth"));
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

    private static void push(CommandContext c, Store<EntityStore> store, String[] args) {
        PlayerRef player = caller(c, store);
        if (player == null) {
            return;
        }
        String first = args.length > 0 ? args[0] : null;
        PushRig rig = TestDevices.rig(player.getUuid());
        if ("off".equalsIgnoreCase(first)) {
            if (!rig.active()) {
                CoreModule.say(c, "No push is running.");
                return;
            }
            rig.cancel();
            CoreModule.say(c, "push off: Set (0, 0, 0) with the default config goes out on the next tick");
            return;
        }
        boolean hold = "hold".equalsIgnoreCase(first);
        int at = hold ? 1 : 0;
        Double y = at < args.length ? number(args[at]) : null;
        Double every = hold && args.length > 2 ? number(args[2]) : null;
        String resistanceText = args.length > at + (hold ? 2 : 1) ? args[at + (hold ? 2 : 1)] : null;
        boolean defaultConfig = "default".equalsIgnoreCase(resistanceText);
        Double resistance = resistanceText == null ? Double.valueOf(1.0) : defaultConfig ? Double.valueOf(Double.NaN) : number(resistanceText);
        if (y == null || resistance == null || (hold && (every == null || every < 1 || every > 100 || every != Math.rint(every)))) {
            CoreModule.say(c, "usage: suspensor push <y> [resistance|default] | push hold <y> <everyTicks 1-100> [resistance|default] | push off (resistance: 1 = no decay, default = the config class's own)");
            return;
        }
        double cap = Suspensors.config().pushCap();
        if (!PushRig.allowed(y, cap)) {
            CoreModule.say(c, "refused: |y| is at most " + cap + " (PushCap in the Suspensor section)");
            return;
        }
        BeltMachine machine = Suspensors.peek(player.getUuid());
        MovementStatesComponent states = store.getComponent(c.senderAsPlayerRef(), MovementStatesComponent.getComponentType());
        if (machine == null || !machine.isOn() || states == null || !states.getMovementStates().flying) {
            CoreModule.say(c, "refused: the push works only while your belt is on and you are flying");
            return;
        }
        long now = System.currentTimeMillis();
        if (hold) {
            rig.startHold(y.floatValue(), resistance.floatValue(), every.intValue(), now);
        } else {
            rig.startOnce(y.floatValue(), resistance.floatValue(), now);
        }
        CoreModule.say(c, "push " + (hold ? "hold every " + every.intValue() + " ticks" : "once") + ": Set (0, " + y + ", 0), resistance "
                + (defaultConfig ? "default" : String.valueOf(resistance)) + "; samples every 0.1 s in the log; 'suspensor push off' ends it, and so do landing, the belt off, death and leaving");
    }

    private static void state(CommandContext c, Store<EntityStore> store) {
        SuspensorConfig config = Suspensors.config();
        String numbers = "speeds " + config.horizontalSpeed() + "/" + config.verticalSpeed() + " (horizontal/vertical), " + config.manaPerSecond()
                + " Mana a second, resume at " + config.resumeMana();
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
        if (manager != null) {
            BeltMachine.FlySettings now = Suspensors.read(manager.getSettings());
            CoreModule.say(c, "movement settings now: fly " + now.mode() + ", h " + now.horizontal() + ", v " + now.vertical()
                    + "; last pushed " + (machine == null ? "n/a" : String.valueOf(machine.pushed()))
                    + "; snapshot " + (machine == null || machine.snapshot() == null ? "none" : String.valueOf(machine.snapshot()))
                    + "; settings writes " + (machine == null ? 0 : machine.pushes()));
        }
    }
}
