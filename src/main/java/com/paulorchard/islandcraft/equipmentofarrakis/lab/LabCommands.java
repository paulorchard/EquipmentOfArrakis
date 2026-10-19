// EXPERIMENT: delete with the lab module
package com.paulorchard.islandcraft.equipmentofarrakis.lab;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.protocol.FlyMode;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CommandRoot;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldAccess;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldPositional;
import org.joml.Vector3d;

import java.util.Locale;

/**
 * <pre>
 * /arrakis lab watch on|off [seconds]       a line a few times a second: states, speeds, Mana, Z item, mode, fly settings
 * /arrakis lab fly off|allowed|forced [h] [v]   set fly and the two fly speeds;  fly flag on|off  sends the flying flag
 * /arrakis lab belt 2|3|off                 the Mk II / Mk III prototype (needs a lab belt as the selected Z item)
 * /arrakis lab fall drag|mass|airdrag|fallforce|set|reset|list ...   the settings route
 * /arrakis lab vel y [value]                sets the vertical velocity once;  clamp on|off [max]  clamps the fall every tick
 * /arrakis lab give glider|belt|beltglider  the vanilla Glider_Scrap, the lab belt, the lab belt with glider physics
 * /arrakis lab drop [blocks]                teleports you up above the ground and reports the landing
 * /arrakis lab state, reset
 * </pre>
 */
final class LabCommands {

    private static final String LANG = CommandRoot.LANG + "lab.";

    private LabCommands() {
    }

    static void register(AbstractCommandCollection group) {
        group.addSubCommand(WorldPositional.build("watch", LANG + "watch.desc", (c, w, s, a) -> run(c, s, a, LabCommands::watch), "mode", "seconds"));
        group.addSubCommand(WorldPositional.build("fly", LANG + "fly.desc", (c, w, s, a) -> run(c, s, a, LabCommands::fly), "mode", "horizontal", "vertical"));
        group.addSubCommand(WorldPositional.build("belt", LANG + "belt.desc", (c, w, s, a) -> run(c, s, a, LabCommands::belt), "mode"));
        group.addSubCommand(WorldPositional.build("fall", LANG + "fall.desc", (c, w, s, a) -> run(c, s, a, LabCommands::fall), "route", "first", "second"));
        group.addSubCommand(WorldPositional.build("vel", LANG + "vel.desc", (c, w, s, a) -> run(c, s, a, LabCommands::vel), "axis", "value"));
        group.addSubCommand(WorldPositional.build("clamp", LANG + "clamp.desc", (c, w, s, a) -> run(c, s, a, LabCommands::clamp), "mode", "max"));
        group.addSubCommand(WorldPositional.build("give", LANG + "give.desc", (c, w, s, a) -> run(c, s, a, LabCommands::give), "what"));
        group.addSubCommand(WorldPositional.build("drop", LANG + "drop.desc", (c, w, s, a) -> drop(c, w, s, a), "blocks"));
        group.addSubCommand(WorldPositional.build("state", LANG + "state.desc", (c, w, s, a) -> run(c, s, a, LabCommands::state)));
        group.addSubCommand(WorldPositional.build("reset", LANG + "reset.desc", (c, w, s, a) -> run(c, s, a, LabCommands::reset)));
    }

    /** What every command needs: the caller as a player with a movement manager. */
    private record Caller(CommandContext c, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, MovementManager manager, LabState.PlayerLab lab,
            String[] args) {

        String arg(int i) {
            return args != null && i < args.length && args[i] != null && !args[i].isBlank() ? args[i].trim() : null;
        }

        void say(String text) {
            CoreModule.say(c, text);
            Labs.log("[" + player.getUsername() + "] " + text);
        }

        /** Pushes the manager's settings and notes that something is changed, so a reset has work to do. */
        void push() {
            LabMovement.push(player, manager);
        }
    }

    private interface Action {
        void run(Caller caller);
    }

    private static void run(CommandContext c, Store<EntityStore> store, String[] args, Action action) {
        if (!c.isPlayer() || c.senderAsPlayerRef() == null) {
            CoreModule.say(c, "Run this as a player in the game.");
            return;
        }
        Ref<EntityStore> ref = c.senderAsPlayerRef();
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        MovementManager manager = store.getComponent(ref, MovementManager.getComponentType());
        if (player == null || manager == null) {
            CoreModule.say(c, "No movement manager on you.");
            return;
        }
        action.run(new Caller(c, store, ref, player, manager, LabState.of(player.getUuid()), args));
    }

    private static Float number(String text) {
        try {
            return text == null ? null : Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- A. watch

    private static void watch(Caller k) {
        String mode = k.arg(0);
        if ("off".equalsIgnoreCase(mode)) {
            k.lab.watchSeconds = 0;
            k.lab.lastJumping = null;
            k.say("watch off");
        } else if ("on".equalsIgnoreCase(mode)) {
            Float seconds = number(k.arg(1));
            k.lab.watchSeconds = seconds != null && seconds > 0.05f ? seconds : Labs.config().watchSeconds();
            k.lab.nextWatch = 0;
            k.say("watch on, a line every " + k.lab.watchSeconds + " s (and one whenever jumping, flying or gliding flips)");
        } else {
            k.say("usage: lab watch on|off [seconds]");
        }
    }

    // ---- B. fly settings

    private static void fly(Caller k) {
        String mode = k.arg(0);
        if ("flag".equalsIgnoreCase(mode)) {
            boolean on = "on".equalsIgnoreCase(k.arg(1));
            LabMovement.sendFlying(k.player, on);
            k.lab.flagSent = on;
            k.say("sent SetMovementStates flying=" + on);
            return;
        }
        if ("engine".equalsIgnoreCase(mode)) {
            boolean on = "on".equalsIgnoreCase(k.arg(1));
            LabMovement.engineFlying(k.ref, k.store, on);
            k.say("called Player.setFlying(" + on + "): fly is now " + k.manager.getSettings().fly);
            return;
        }
        String word = LabRules.flyWord(mode);
        if (word == null) {
            k.say("usage: lab fly off|allowed|forced [horizontal] [vertical]   or   lab fly flag on|off   or   lab fly engine on|off");
            return;
        }
        MovementSettings settings = k.manager.getSettings();
        Float horizontal = number(k.arg(1));
        Float vertical = number(k.arg(2));
        LabMovement.setFly(k.player, k.manager, FlyMode.valueOf(word), horizontal != null ? horizontal : settings.horizontalFlySpeed,
                vertical != null ? vertical : settings.verticalFlySpeed);
        k.say("fly " + settings.fly + ", horizontalFlySpeed " + settings.horizontalFlySpeed + ", verticalFlySpeed " + settings.verticalFlySpeed + " (lab reset puts it back)");
    }

    // ---- C. the belt prototype

    private static void belt(Caller k) {
        String mode = k.arg(0);
        if ("2".equals(mode) || "3".equals(mode)) {
            k.lab.beltMode = Integer.parseInt(mode);
            k.say("belt Mk " + (k.lab.beltMode == 2 ? "II" : "III") + " on: active while a lab belt is the selected Z item, you are in the air, Space is held and you have Mana; selected Z item now: "
                    + zOf(k));
        } else if ("off".equalsIgnoreCase(mode)) {
            LabMovement.reset(k.ref, k.store, k.player, k.lab);
            k.say("belt off, everything put back");
        } else {
            k.say("usage: lab belt 2|3|off");
        }
    }

    private static String zOf(Caller k) {
        return LabSystem.zItem(k.store, k.ref);
    }

    // ---- D. slow fall candidates

    private static void fall(Caller k) {
        String route = k.arg(0) == null ? "" : k.arg(0).toLowerCase(Locale.ROOT);
        MovementSettings settings = k.manager.getSettings();
        switch (route) {
            case "drag" -> setField(k, "dragCoefficient", k.arg(1));
            case "mass" -> setField(k, "mass", k.arg(1));
            case "airdrag" -> {
                setField(k, "airDragMin", k.arg(1));
                setField(k, "airDragMax", k.arg(2));
            }
            case "fallforce" -> setField(k, "variableJumpFallForce", k.arg(1));
            case "set" -> setField(k, k.arg(1), k.arg(2));
            case "list" -> k.say("fields: " + String.join(", ", LabFields.names()));
            case "reset" -> {
                k.manager.resetDefaultsAndUpdate(k.ref, k.store);
                k.say("movement settings back to the defaults");
            }
            case "get" -> k.say(k.arg(1) + " = " + LabFields.get(settings, k.arg(1)));
            default -> k.say("usage: lab fall drag <v> | mass <v> | airdrag <min> <max> | fallforce <v> | set <field> <v> | get <field> | list | reset");
        }
    }

    private static void setField(Caller k, String field, String value) {
        if (field == null || value == null) {
            k.say("a field name and a value are needed");
            return;
        }
        String result = LabFields.set(k.manager.getSettings(), field, value);
        if (!result.startsWith("no ") && !result.startsWith("cannot")) {
            k.push();
        }
        k.say(result);
    }

    private static void vel(Caller k) {
        Velocity velocity = k.store.getComponent(k.ref, Velocity.getComponentType());
        Float value = number(k.arg(1));
        if (velocity == null || !"y".equalsIgnoreCase(k.arg(0)) || value == null) {
            k.say("usage: lab vel y <blocks per second>");
            return;
        }
        Vector3d client = velocity.getClientVelocity();
        velocity.addInstruction(new Vector3d(client.x, value, client.z), new VelocityConfig(), ChangeVelocityType.Set);
        k.say("sent a velocity instruction: vertical " + value + " blocks/s (Set), horizontal kept " + String.format("%.2f %.2f", client.x, client.z));
    }

    private static void clamp(Caller k) {
        if ("on".equalsIgnoreCase(k.arg(0))) {
            Float max = number(k.arg(1));
            k.lab.clampMax = max != null && max > 0 ? max : 2.0;
            k.say("clamp on: downward speed held to " + k.lab.clampMax + " blocks/s by a Set velocity instruction every tick it is faster");
        } else if ("off".equalsIgnoreCase(k.arg(0))) {
            k.lab.clampMax = 0;
            k.say("clamp off");
        } else {
            k.say("usage: lab clamp on|off [max fall speed]");
        }
    }

    // ---- E. items

    private static void give(Caller k) {
        String what = k.arg(0) == null ? "" : k.arg(0).toLowerCase(Locale.ROOT);
        String id = switch (what) {
            case "glider" -> LabItems.GLIDER;
            case "belt" -> LabItems.BELT;
            case "beltglider" -> LabItems.BELT_GLIDER;
            default -> null;
        };
        if (id == null) {
            k.say("usage: lab give glider|belt|beltglider");
            return;
        }
        InventoryComponent.Hotbar hotbar = k.store.getComponent(k.ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Storage storage = k.store.getComponent(k.ref, InventoryComponent.Storage.getComponentType());
        ItemStack rest = hotbar.getInventory().addItemStack(new ItemStack(id, 1)).getRemainder();
        if (rest != null && !rest.isEmpty()) {
            rest = storage.getInventory().addItemStack(rest).getRemainder();
        }
        k.say((rest == null || rest.isEmpty() ? "gave " : "NO ROOM for ") + id);
    }

    // ---- F. fall damage

    private static void drop(CommandContext c, World world, Store<EntityStore> store, String[] args) {
        run(c, store, args, k -> {
            Float blocks = number(k.arg(0));
            TransformComponent transform = k.store.getComponent(k.ref, TransformComponent.getComponentType());
            if (blocks == null || blocks <= 0 || transform == null) {
                k.say("usage: lab drop <blocks above the ground where you stand>");
                return;
            }
            Vector3d at = transform.getPosition();
            int top = new WorldAccess(world).height((int) Math.floor(at.x), (int) Math.floor(at.z));
            double ground = top == WorldAccess.NO_HEIGHT ? at.y : top + 1;
            double y = ground + blocks;
            k.lab.dropTracking = true;
            k.lab.airborne = false;
            k.lab.minVy = 0;
            k.lab.fromY = y;
            k.lab.health0 = LabSystem.health(k.store, k.ref);
            k.store.addComponent(k.ref, Teleport.getComponentType(), Teleport.createForPlayer(world, new Vector3d(at.x, y, at.z), new Rotation3f()));
            k.say("teleported " + blocks + " blocks above the ground (y " + String.format("%.1f", y) + "); health " + k.lab.health0 + "; the landing is reported");
        });
    }

    // ---- state and reset

    private static void state(Caller k) {
        MovementSettings s = k.manager.getSettings();
        k.say("watch " + (k.lab.watchSeconds > 0 ? k.lab.watchSeconds + " s" : "off") + ", belt mode " + k.lab.beltMode + (k.lab.beltOn ? " ACTIVE" : "") + ", clamp "
                + (k.lab.clampMax > 0 ? k.lab.clampMax : "off") + ", fly " + s.fly + " h " + s.horizontalFlySpeed + " v " + s.verticalFlySpeed + ", mass " + s.mass
                + ", drag " + s.dragCoefficient + ", airDrag " + s.airDragMin + ".." + s.airDragMax + ", variableJumpFallForce " + s.variableJumpFallForce
                + ", Z item " + zOf(k));
    }

    private static void reset(Caller k) {
        LabMovement.reset(k.ref, k.store, k.player, k.lab);
        k.lab.dropTracking = false;
        k.say("everything put back: movement settings reset to the defaults, belt off, clamp off, no flying flag");
    }
}
