// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
package com.paulorchard.islandcraft.equipmentofarrakis.experiments;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CommandRoot;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldPositional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The operator commands of the calibration tools, under /arrakis exp cal (see EXPERIMENT_LOG.md, "How to calibrate"):
 *
 * <pre>
 * /arrakis exp cal fp|tp &lt;param&gt; [value]    param: node x y z yaw pitch roll scale detached; value: a number, + or -, a node name
 * /arrakis exp cal apex|step|astep &lt;value&gt;  the focus distance; the step of blocks; the step of degrees
 * /arrakis exp cal view fp|tp|both           which numbers the live beam uses
 * /arrakis exp cal collapse|target on|off    the vanishing-point test; the marker where the server thinks the crosshair lands
 * /arrakis exp cal nodes [node ...]|off      a coloured ruler along each node's local Z
 * /arrakis exp cal beam on|nature|off        the fine stream, or the NatureBeam itself, from the fp / tp numbers
 * /arrakis exp cal show|save|load|reset|off  print the paste-ready block; write or read Experiments_Calibration.json; defaults; all rigs off
 * </pre>
 */
final class ExpCalCommands {

    private static final String LANG = CommandRoot.LANG + "exp.";
    static final String FILE = "Experiments_Calibration.json";

    private ExpCalCommands() {
    }

    static AbstractCommandCollection build(Path dataDirectory) {
        AbstractCommandCollection exp = new AbstractCommandCollection("exp", LANG + "desc") {
        };
        AbstractCommandCollection cal = new AbstractCommandCollection("cal", LANG + "cal.desc") {
        };
        String[] nodeArgs = {"node1", "node2", "node3", "node4", "node5", "node6", "node7", "node8", "node9", "node10"};

        for (String view : new String[] {"fp", "tp", "cam"}) {
            cal.addSubCommand(WorldPositional.build(view, LANG + "cal.view.desc", (c, w, s, a) -> run(c, s, state -> {
                ExpCalibration.View v = state.calibration.view(view);
                if (a.length == 0) {
                    throw new IllegalArgumentException("fp|tp <param> [value]; params: " + String.join(", ", ExpCalibration.PARAMETERS));
                }
                if (a.length > 1) {
                    String value = state.calibration.change(view, a[0], a[1]);
                    say(c, view + " " + a[0].toLowerCase(Locale.ROOT) + " = " + value);
                } else {
                    say(c, view + " " + a[0] + " is " + (a[0].equalsIgnoreCase("node") ? v.node : a[0].equalsIgnoreCase("detached") ? String.valueOf(v.detached)
                            : ExpCalibration.format(valueOf(v, a[0]))));
                }
                say(c, summary(state.calibration));
            }), "param", "value"));
        }
        cal.addSubCommand(WorldPositional.build("apex", LANG + "cal.number.desc", (c, w, s, a) -> run(c, s, state -> {
            state.calibration.apex = ExpCalibration.next(state.calibration.apex, a[0], state.calibration.step);
            say(c, "apex = " + ExpCalibration.format(state.calibration.apex));
        }), "value"));
        cal.addSubCommand(WorldPositional.build("step", LANG + "cal.number.desc", (c, w, s, a) -> run(c, s, state -> {
            state.calibration.step = ExpCalibration.next(state.calibration.step, a[0], 0);
            say(c, "step = " + ExpCalibration.format(state.calibration.step));
        }), "value"));
        cal.addSubCommand(WorldPositional.build("astep", LANG + "cal.number.desc", (c, w, s, a) -> run(c, s, state -> {
            state.calibration.angleStep = ExpCalibration.next(state.calibration.angleStep, a[0], 0);
            say(c, "angle step = " + ExpCalibration.format(state.calibration.angleStep));
        }), "value"));
        cal.addSubCommand(WorldPositional.build("view", LANG + "cal.view.desc", (c, w, s, a) -> run(c, s, state -> {
            String v = a[0].toLowerCase(Locale.ROOT);
            if (!v.equals("fp") && !v.equals("tp") && !v.equals("both")) {
                throw new IllegalArgumentException("fp, tp or both");
            }
            state.view = v;
            say(c, "the live beam uses the " + v + " numbers");
        }), "which"));

        cal.addSubCommand(WorldPositional.build("collapse", LANG + "cal.rig.desc", (c, w, s, a) -> run(c, s, state -> {
            state.collapse = onOff(a[0]);
            afterSwitch(c, state, "collapse", state.collapse, "dots along the server's look ray, 1 to 12 blocks: do they hide behind the crosshair dot?");
        }), "on_off"));
        cal.addSubCommand(WorldPositional.build("target", LANG + "cal.rig.desc", (c, w, s, a) -> run(c, s, state -> {
            state.target = onOff(a[0]);
            afterSwitch(c, state, "target", state.target, "a magenta cross where the server thinks the crosshair lands");
        }), "on_off"));
        cal.addSubCommand(WorldPositional.build("beam", LANG + "cal.rig.desc", (c, w, s, a) -> run(c, s, state -> {
            String mode = a[0].toLowerCase(Locale.ROOT);
            state.beam = switch (mode) {
                case "on", "stream" -> ExpCalibrationRig.Beam.STREAM;
                case "nature" -> ExpCalibrationRig.Beam.NATURE;
                case "world" -> ExpCalibrationRig.Beam.WORLD_STREAM;
                case "worldnature" -> ExpCalibrationRig.Beam.WORLD_NATURE;
                case "off" -> ExpCalibrationRig.Beam.OFF;
                default -> throw new IllegalArgumentException("on, nature, world, worldnature or off");
            };
            afterSwitch(c, state, "beam", state.beam != ExpCalibrationRig.Beam.OFF, (switch (state.beam) {
                case NATURE -> "the NatureBeam attached to the fp / tp node numbers";
                case WORLD_STREAM -> "a fine stream in world space from the cam numbers (start, camera frame) to the look target (the end is not a number)";
                case WORLD_NATURE -> "the NatureBeam in world space from the cam numbers to the look target";
                default -> "a fine stream attached to the fp / tp node numbers";
            }));
        }), "mode"));
        cal.addSubCommand(WorldPositional.build("end", LANG + "cal.view.desc", (c, w, s, a) -> run(c, s, state -> {
            String v = a[0].toLowerCase(Locale.ROOT);
            if (!v.equals("apex") && !v.equals("block")) {
                throw new IllegalArgumentException("apex or block");
            }
            state.calibration.endAtBlock = v.equals("block");
            say(c, "the world beams end at " + (state.calibration.endAtBlock ? "the block the look ray enters (the apex distance when nothing is hit)" : "the apex distance along the look ray"));
        }), "where"));
        cal.addSubCommand(WorldPositional.build("nodes", LANG + "cal.rig.desc", (c, w, s, a) -> run(c, s, state -> {
            state.nodes.clear();
            if (a.length > 0 && a[0].equalsIgnoreCase("off")) {
                ExpCalibrationRig.switchAllOffNodes(state);
                say(c, "nodes: off");
                return;
            }
            if (a.length == 0) {
                state.nodes.addAll(java.util.List.of(ExpCalibrationRig.DEFAULT_NODES));
            } else {
                state.nodes.addAll(java.util.List.of(a));
            }
            say(c, "nodes: a ruler along the local Z of each node (small dots on the + side, big dots on the - side, 10 blocks):");
            for (int i = 0; i < state.nodes.size(); i++) {
                say(c, "  " + ExpCalibrationRig.COLOURS[i % ExpCalibrationRig.COLOURS.length][0] + " = " + state.nodes.get(i));
            }
            say(c, "A name the client does not accept shows nothing. The node that follows the camera is the one whose ruler stays on the dot.");
        }), nodeArgs));
        cal.addSubCommand(WorldPositional.build("off", LANG + "cal.rig.desc", (c, w, s, a) -> run(c, s, state -> {
            ExpCalibrationRig.switchAllOff(state);
            say(c, "every rig is off");
        })));

        cal.addSubCommand(WorldPositional.build("show", LANG + "cal.file.desc", (c, w, s, a) -> run(c, s, state -> {
            say(c, "calibration " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " (paste this block):");
            for (String line : state.calibration.toJson().split("\n")) {
                say(c, line);
            }
        })));
        cal.addSubCommand(WorldPositional.build("save", LANG + "cal.file.desc", (c, w, s, a) -> run(c, s, state -> {
            try {
                Files.createDirectories(dataDirectory);
                Files.writeString(dataDirectory.resolve(FILE), state.calibration.toJson() + "\n", StandardCharsets.UTF_8);
                say(c, "saved to " + dataDirectory.resolve(FILE));
            } catch (IOException e) {
                say(c, "could not save: " + e);
            }
        })));
        cal.addSubCommand(WorldPositional.build("load", LANG + "cal.file.desc", (c, w, s, a) -> run(c, s, state -> {
            try {
                state.calibration = ExpCalibration.fromJson(Files.readString(dataDirectory.resolve(FILE), StandardCharsets.UTF_8));
                say(c, "loaded " + dataDirectory.resolve(FILE));
                say(c, summary(state.calibration));
            } catch (IOException e) {
                say(c, "could not read " + dataDirectory.resolve(FILE) + ": " + e.getMessage());
            }
        })));
        cal.addSubCommand(WorldPositional.build("reset", LANG + "cal.file.desc", (c, w, s, a) -> run(c, s, state -> {
            state.calibration = ExpCalibration.defaults();
            say(c, "back to the vanilla Stoneskin placements");
            say(c, summary(state.calibration));
        })));
        exp.addSubCommand(cal);
        return exp;
    }

    /** Runs {@code action} on the sender's state; a bad argument is answered in chat. */
    private static void run(CommandContext c, Store<EntityStore> store, Action action) {
        if (!c.isPlayer()) {
            say(c, "The calibration is per player: run it as a player in the game.");
            return;
        }
        Ref<EntityStore> ref = c.senderAsPlayerRef();
        PlayerRef player = ref == null ? null : store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        ExpCalibrationRig.State state = ExpCalibrationRig.state(player, world, ExperimentsModule.startingCalibration());
        try {
            action.run(state);
        } catch (IllegalArgumentException e) {
            say(c, e.getMessage());
        }
    }

    private interface Action {
        void run(ExpCalibrationRig.State state);
    }

    private static boolean onOff(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "on", "true", "1" -> true;
            case "off", "false", "0" -> false;
            default -> throw new IllegalArgumentException("on or off, not " + text);
        };
    }

    private static void afterSwitch(CommandContext c, ExpCalibrationRig.State state, String rig, boolean on, String what) {
        if (on) {
            say(c, rig + ": on - " + what);
        } else {
            say(c, rig + ": off");
            ExpCalibrationRig.cancelAll(state);
        }
    }

    private static double valueOf(ExpCalibration.View v, String parameter) {
        return switch (parameter.toLowerCase(Locale.ROOT)) {
            case "x" -> v.x;
            case "y" -> v.y;
            case "z" -> v.z;
            case "yaw" -> v.yaw;
            case "pitch" -> v.pitch;
            case "roll" -> v.roll;
            case "scale" -> v.scale;
            case "detached" -> v.detached ? 1 : 0;
            default -> 0;
        };
    }

    private static String summary(ExpCalibration cal) {
        return "apex " + ExpCalibration.format(cal.apex) + ", step " + ExpCalibration.format(cal.step) + " blocks / "
                + ExpCalibration.format(cal.angleStep) + " degrees";
    }

    private static void say(CommandContext c, String text) {
        CoreModule.say(c, text);
    }
}
