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
