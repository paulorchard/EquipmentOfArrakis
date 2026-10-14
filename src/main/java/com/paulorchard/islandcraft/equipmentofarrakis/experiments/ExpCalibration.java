// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
package com.paulorchard.islandcraft.equipmentofarrakis.experiments;

import org.bson.BsonDocument;
import org.bson.BsonValue;

import java.util.Locale;

/**
 * The numbers that place a beam on the player: for first person (fp) and third person (tp) the node it is attached to, its offset in
 * that node's frame (blocks), its rotation (degrees) and scale, plus the shared apex distance. Pure data and text, no engine: the
 * commands change it, {@link #toJson} prints the block that is pasted into tools/assets/experiments/calibration.json, and
 * {@link #fromJson} reads it back.
 *
 * <p>A parameter is changed with a value: a number sets it, "+" and "-" add and subtract one step (the step for blocks, or the
 * angle step for yaw, pitch and roll; the scale uses the block step).
 */
final class ExpCalibration {

    /** One set of numbers: where the beam sits and how it is turned. */
    static final class View {
        String node;
        double x;
        double y;
        double z;
        double yaw;
        double pitch;
        double roll;
        double scale = 1;
        /** Whether the particles ignore the animation of the node (vanilla Stoneskin: true in third person, not in first). */
        boolean detached;

        View(String node, double x, double y, double z, double yaw, double pitch, double roll, double scale, boolean detached) {
            this.node = node;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            this.scale = scale;
            this.detached = detached;
        }

        View copy() {
            return new View(node, x, y, z, yaw, pitch, roll, scale, detached);
        }
    }

    static final String[] PARAMETERS = {"node", "x", "y", "z", "yaw", "pitch", "roll", "scale", "detached"};

    View fp = defaultFp();
    View tp = defaultTp();
    /** Where a world-space beam STARTS (x right, y up, z forward of the eye); its end is the look target, not a number. */
    View cam = defaultCam();
    /**
     * Where a world-space beam starts when it is held in the hand (the Flame Cutter): x right (negative for a left-hander), y up from the eye,
     * z forward, along the heading of the body (the body does not pitch with the head). The default is ExpAim.gripPoint(1.8).
     */
    View body = defaultBody();
    /** True: the world-space beams start at {@link #body} rather than {@link #cam}. */
    boolean useBody;
    /** Cycles a second of the glow around the Flame Beam Cutter's beam. */
    double pulseHz = 2;
    /** True: the world-space beam ends where the look ray enters a block (else at the apex distance, also when nothing is hit). */
    boolean endAtBlock;
    /** The distance along the camera ray where the beam is meant to end, blocks. */
    double apex = 5;
    /** What "+" and "-" add to a position or a scale, blocks. */
    double step = 0.01;
    /** What "+" and "-" add to an angle, degrees. */
    double angleStep = 0.5;

    /** The vanilla Stoneskin placements of the NatureBeam: first person at the Body node, third person at the Head node. */
    static View defaultFp() {
        return new View("Body", -0.2, 1.5, 3.2, 0, 180, 0, 1, false);
    }

    static View defaultTp() {
        return new View("Head", -0.5, 0, 3.2, 0, 180, 0, 1, true);
    }

    /** Where a world-space beam starts, in the camera's frame: x right, y up, z forward of the eye, blocks. */
    static View defaultCam() {
        return new View("eye", 0.15, -0.15, 0.6, 0, 0, 0, 1, false);
    }

    static View defaultBody() {
        double[] g = ExpAim.gripPoint(1.8);
        return new View("body", round(g[0]), round(g[1]), round(g[2]), 0, 0, 0, 1, false);
    }

    /** The start in use: the body's grip, or the camera's. */
    View start() {
        return useBody ? body : cam;
    }

    static ExpCalibration defaults() {
        return new ExpCalibration();
    }

    ExpCalibration copy() {
        ExpCalibration c = new ExpCalibration();
        c.fp = fp.copy();
        c.tp = tp.copy();
        c.cam = cam.copy();
        c.body = body.copy();
        c.useBody = useBody;
        c.pulseHz = pulseHz;
        c.endAtBlock = endAtBlock;
        c.apex = apex;
        c.step = step;
        c.angleStep = angleStep;
        return c;
    }

    View view(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "fp" -> fp;
            case "tp" -> tp;
            case "cam" -> cam;
            case "body" -> body;
            default -> throw new IllegalArgumentException("the view is fp, tp, cam or body, not " + name);
        };
    }

    /**
     * Changes one parameter of a view. {@code value} is a number (set), "+" or "-" (one step up or down), or, for node, a node name and for
     * detached true or false. Returns the new value as text.
     */
    String change(String viewName, String parameter, String value) {
        View view = view(viewName);
        String p = parameter.toLowerCase(Locale.ROOT);
        switch (p) {
            case "node" -> {
                if (value == null || value.isBlank()) {
                    throw new IllegalArgumentException("give a node name, for example Head");
                }
                view.node = value;
                return view.node;
            }
            case "detached" -> {
                view.detached = Boolean.parseBoolean(value);
                return String.valueOf(view.detached);
            }
            case "x", "y", "z", "scale" -> {
                double next = next(current(view, p), value, step);
                set(view, p, next);
                return format(next);
            }
            case "yaw", "pitch", "roll" -> {
                double next = next(current(view, p), value, angleStep);
                set(view, p, next);
                return format(next);
            }
            default -> throw new IllegalArgumentException("unknown parameter " + parameter + "; they are " + String.join(", ", PARAMETERS));
        }
    }

    /** "+" and "-" step the current value, anything else is a number. */
    static double next(double current, String value, double step) {
        if (value == null) {
            throw new IllegalArgumentException("give a number, + or -");
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "+", "inc", "plus", "up" -> round(current + step);
            case "-", "dec", "minus", "down" -> round(current - step);
            default -> {
                try {
                    yield round(Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("not a number, + or -: " + value);
                }
            }
        };
    }

    private static double current(View v, String p) {
        return switch (p) {
            case "x" -> v.x;
            case "y" -> v.y;
            case "z" -> v.z;
            case "yaw" -> v.yaw;
            case "pitch" -> v.pitch;
            case "roll" -> v.roll;
            default -> v.scale;
        };
    }

    private static void set(View v, String p, double value) {
        switch (p) {
            case "x" -> v.x = value;
            case "y" -> v.y = value;
            case "z" -> v.z = value;
            case "yaw" -> v.yaw = value;
            case "pitch" -> v.pitch = value;
            case "roll" -> v.roll = value;
            default -> v.scale = value;
        }
    }

    /** Six decimals, so that adding 0.01 thirty times does not leave 0.30000000000000004. */
    static double round(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    static String format(double value) {
        String text = String.format(Locale.ROOT, "%.6f", value);
        text = text.contains(".") ? text.replaceAll("0+$", "").replaceAll("\\.$", "") : text;
        return text.equals("-0") ? "0" : text;
    }

    /** The paste-ready block. */
    String toJson() {
        return "{ \"$Comment\": \"EXPERIMENT: calibration of the Flame Beam Cutter, from /arrakis exp cal show (see EXPERIMENT_LOG.md, How to calibrate)\",\n"
                + "  \"apex\": " + format(apex) + ", \"step\": " + format(step) + ", \"angleStep\": " + format(angleStep) + ",\n"
                + "  \"endAtBlock\": " + endAtBlock + ", \"frame\": \"" + (useBody ? "body" : "cam") + "\", \"pulseHz\": " + format(pulseHz) + ",\n"
                + "  \"cam\": " + json(cam) + ",\n"
                + "  \"body\": " + json(body) + ",\n"
                + "  \"fp\": " + json(fp) + ",\n"
                + "  \"tp\": " + json(tp) + " }";
    }

    private static String json(View v) {
        return "{ \"node\": \"" + v.node + "\", \"x\": " + format(v.x) + ", \"y\": " + format(v.y) + ", \"z\": " + format(v.z)
                + ", \"yaw\": " + format(v.yaw) + ", \"pitch\": " + format(v.pitch) + ", \"roll\": " + format(v.roll)
                + ", \"scale\": " + format(v.scale) + ", \"detached\": " + v.detached + " }";
    }

    /** Reads the block back; anything missing keeps its default. */
    static ExpCalibration fromJson(String text) {
        BsonDocument document;
        try {
            document = BsonDocument.parse(text);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("not valid JSON: " + e.getMessage());
        }
        ExpCalibration c = new ExpCalibration();
        c.apex = number(document, "apex", c.apex);
        c.step = number(document, "step", c.step);
        c.angleStep = number(document, "angleStep", c.angleStep);
        if (document.containsKey("endAtBlock") && document.get("endAtBlock").isBoolean()) {
            c.endAtBlock = document.getBoolean("endAtBlock").getValue();
        }
        c.pulseHz = number(document, "pulseHz", c.pulseHz);
        if (document.containsKey("frame") && document.get("frame").isString()) {
            c.useBody = document.getString("frame").getValue().equalsIgnoreCase("body");
        }
        read(document, "cam", c.cam);
        read(document, "body", c.body);
        read(document, "fp", c.fp);
        read(document, "tp", c.tp);
        return c;
    }

    private static void read(BsonDocument root, String key, View view) {
        if (!root.containsKey(key) || !root.get(key).isDocument()) {
            return;
        }
        BsonDocument d = root.getDocument(key);
        if (d.containsKey("node") && d.get("node").isString()) {
            view.node = d.getString("node").getValue();
        }
        view.x = number(d, "x", view.x);
        view.y = number(d, "y", view.y);
        view.z = number(d, "z", view.z);
        view.yaw = number(d, "yaw", view.yaw);
        view.pitch = number(d, "pitch", view.pitch);
        view.roll = number(d, "roll", view.roll);
        view.scale = number(d, "scale", view.scale);
        if (d.containsKey("detached") && d.get("detached").isBoolean()) {
            view.detached = d.getBoolean("detached").getValue();
        }
    }

    private static double number(BsonDocument d, String key, double fallback) {
        BsonValue v = d.get(key);
        return v != null && v.isNumber() ? v.asNumber().doubleValue() : fallback;
    }
}
