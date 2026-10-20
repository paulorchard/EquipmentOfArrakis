package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.util.RawJsonReader;
import org.bson.BsonValue;

import java.io.IOException;
import java.util.Locale;

/**
 * The "Mk1" part of the Suspensor section: the slow descent (a fall that does no lethal damage), as two test marks, "far fall" and
 * "speed". A 0.3.0 config file has a text placeholder under this key; {@link #CODEC} reads that as the defaults, so the old file loads.
 */
public class Mk1Config {

    private boolean listed = true;
    private String route = "glider";
    private String gliderTrigger = "falling";
    private int fallingTicks = 1;
    private double descentSpeed = 4.0;
    private double speedThreshold = 12.0;
    private int speedTicks = 2;
    private double glideHorizontal = 1.0;
    private double holdTolerance = 0.75;
    private int pushTicks = 5;
    private double airDrag = 0.0;
    private double gravityStep = AirDragMath.FITTED_STEP;
    private boolean reportsTotal = true;
    private double manaPerSecond = 0.0;

    /** False: the two Mk I toggles are not registered at all. */
    public boolean listed() {
        return listed;
    }

    /** The way the descent is applied; an unknown name (the old settings and velocity routes) is flyglide. */
    public BeltMachine.Way way() {
        return switch (route == null ? "" : route.toLowerCase(Locale.ROOT)) {
            case "airdrag" -> BeltMachine.Way.AIRDRAG;
            case "counter" -> BeltMachine.Way.COUNTER;
            case "flyglide" -> BeltMachine.Way.FLYGLIDE;
            default -> BeltMachine.Way.GLIDER;
        };
    }

    void way(BeltMachine.Way value) {
        route = value.name().toLowerCase(Locale.ROOT);
    }

    /** What the glider route takes as falling; an unknown name is the raw falling flag. */
    public BeltMachine.Trigger trigger() {
        return switch (gliderTrigger == null ? "" : gliderTrigger.toLowerCase(Locale.ROOT)) {
            case "speed" -> BeltMachine.Trigger.SPEED;
            case "farfall" -> BeltMachine.Trigger.FARFALL;
            default -> BeltMachine.Trigger.FALLING;
        };
    }

    void trigger(BeltMachine.Trigger value) {
        gliderTrigger = value.name().toLowerCase(Locale.ROOT);
    }

    /** Consecutive ticks the falling flag must hold for the falling trigger (at least 1). */
    public int fallingTicks() {
        return Math.max(1, fallingTicks);
    }

    void fallingTicks(int value) {
        fallingTicks = value;
    }

    /** Target descent speed in blocks a second. */
    public double descentSpeed() {
        return Math.max(0.1, descentSpeed);
    }

    void descentSpeed(double value) {
        descentSpeed = value;
    }

    /** Downward client speed (blocks a second) the "speed" mark engages above. */
    public double speedThreshold() {
        return Math.max(0, speedThreshold);
    }

    void speedThreshold(double value) {
        speedThreshold = value;
    }

    /** Consecutive ticks the speed must stay above the threshold. */
    public int speedTicks() {
        return Math.max(1, speedTicks);
    }

    void speedTicks(int value) {
        speedTicks = value;
    }

    /** Horizontal fly speed while gliding (flyglide). */
    public double glideHorizontal() {
        return Math.max(0, glideHorizontal);
    }

    void glideHorizontal(double value) {
        glideHorizontal = value;
    }

    /** How far the vertical speed may drift from the target before the instruction is refreshed (blocks a second). */
    public double holdTolerance() {
        return Math.max(0.05, holdTolerance);
    }

    void holdTolerance(double value) {
        holdTolerance = value;
    }

    /** Ticks between two instruction refreshes at most (flyglide, counter). */
    public int pushTicks() {
        return Math.max(1, pushTicks);
    }

    void pushTicks(int value) {
        pushTicks = value;
    }

    /** The air drag multiplier of the airdrag way: the setting if above 0, otherwise derived from the target and the fitted step. */
    public double airDrag() {
        return airDrag > 0 ? Math.min(airDrag, 0.9999) : AirDragMath.airDragFor(descentSpeed(), gravityStep());
    }

    public double airDragSetting() {
        return airDrag;
    }

    void airDrag(double value) {
        airDrag = value;
    }

    /** The gravity step of the airdrag formula (see {@link AirDragMath}). */
    public double gravityStep() {
        return Math.max(0.0001, gravityStep);
    }

    void gravityStep(double value) {
        gravityStep = value;
    }

    /** True: the client velocity the probe and the counter way read includes the external velocity (to be confirmed by the impulse probe). */
    public boolean reportsTotal() {
        return reportsTotal;
    }

    void reportsTotal(boolean value) {
        reportsTotal = value;
    }

    /** Mana a second while the descent is engaged (0: free). */
    public double manaPerSecond() {
        return Math.max(0, manaPerSecond);
    }

    void manaPerSecond(double value) {
        manaPerSecond = value;
    }

    BeltMachine.FallRules rules() {
        return new BeltMachine.FallRules(way(), descentSpeed(), speedThreshold(), speedTicks(), pushTicks(), holdTolerance(), (float) glideHorizontal(),
                (float) airDrag(), reportsTotal(), (float) manaPerSecond(), trigger(), fallingTicks());
    }

    static final BuilderCodec<Mk1Config> BUILDER = BuilderCodec.builder(Mk1Config.class, Mk1Config::new)
            .append(new KeyedCodec<>("Listed", Codec.BOOLEAN, false), (c, v) -> c.listed = v, c -> c.listed)
            .documentation("True: the two Mk I toggles are listed on the picker page (this test build). False: not registered.").add()
            .append(new KeyedCodec<>("Route", Codec.STRING, false), (c, v) -> c.route = v, c -> c.route)
            .documentation("glider (default: the falling signal effect opens the glider item), flyglide (fly, then drift down with an external velocity), airdrag (the air drag multipliers) or counter (an upward external velocity).").add()
            .append(new KeyedCodec<>("GliderTrigger", Codec.STRING, false), (c, v) -> c.gliderTrigger = v, c -> c.gliderTrigger)
            .documentation("glider route: what counts as falling. falling (default: the raw flag), speed (the speed rule) or farfall (the fallingFar flag).").add()
            .append(new KeyedCodec<>("FallingTicks", Codec.INTEGER, false), (c, v) -> c.fallingTicks = v, c -> c.fallingTicks)
            .documentation("glider route, falling trigger: consecutive ticks of the falling flag before the signal is sent (default 1).").add()
            .append(new KeyedCodec<>("DescentSpeed", Codec.DOUBLE, false), (c, v) -> c.descentSpeed = v, c -> c.descentSpeed)
            .documentation("Target descent speed in blocks a second.").add()
            .append(new KeyedCodec<>("SpeedThreshold", Codec.DOUBLE, false), (c, v) -> c.speedThreshold = v, c -> c.speedThreshold)
            .documentation("The speed mark engages when the client falls faster than this (blocks a second; fall damage starts at 21).").add()
            .append(new KeyedCodec<>("SpeedTicks", Codec.INTEGER, false), (c, v) -> c.speedTicks = v, c -> c.speedTicks)
            .documentation("Consecutive ticks above the threshold before the speed mark engages.").add()
            .append(new KeyedCodec<>("GlideHorizontal", Codec.DOUBLE, false), (c, v) -> c.glideHorizontal = v, c -> c.glideHorizontal)
            .documentation("flyglide: the horizontal fly speed while gliding (the vertical one is 0).").add()
            .append(new KeyedCodec<>("HoldTolerance", Codec.DOUBLE, false), (c, v) -> c.holdTolerance = v, c -> c.holdTolerance)
            .documentation("flyglide and counter: the instruction is refreshed when the vertical speed is further than this from the target.").add()
            .append(new KeyedCodec<>("PushTicks", Codec.INTEGER, false), (c, v) -> c.pushTicks = v, c -> c.pushTicks)
            .documentation("flyglide and counter: ticks between two instruction refreshes at most (default 5).").add()
            .append(new KeyedCodec<>("AirDrag", Codec.DOUBLE, false), (c, v) -> c.airDrag = v, c -> c.airDrag)
            .documentation("airdrag: the multiplier pushed for airDragMin and airDragMax. 0: derived from DescentSpeed and GravityStep.").add()
            .append(new KeyedCodec<>("GravityStep", Codec.DOUBLE, false), (c, v) -> c.gravityStep = v, c -> c.gravityStep)
            .documentation("airdrag: the fitted step of v = step * m / (1 - m); 0.1185 from the terminal speed 23.58 at m = 0.995.").add()
            .append(new KeyedCodec<>("ReportsTotal", Codec.BOOLEAN, false), (c, v) -> c.reportsTotal = v, c -> c.reportsTotal)
            .documentation("counter: true when the client velocity includes the external velocity (to be confirmed with suspensor impulse).").add()
            .append(new KeyedCodec<>("ManaPerSecond", Codec.DOUBLE, false), (c, v) -> c.manaPerSecond = v, c -> c.manaPerSecond)
            .documentation("Mana a second while the descent is engaged (0: free).").add()
            .build();

    /** The builder codec, except that a text value (the 0.3.0 placeholder) reads as the defaults. */
    public static final Codec<Mk1Config> CODEC = new Codec<>() {
        @Override
        public Mk1Config decode(BsonValue value, ExtraInfo extra) {
            return value != null && value.isString() ? new Mk1Config() : BUILDER.decode(value, extra);
        }

        @Override
        public BsonValue encode(Mk1Config value, ExtraInfo extra) {
            return BUILDER.encode(value, extra);
        }

        @Override
        public Mk1Config decodeJson(RawJsonReader reader, ExtraInfo extra) throws IOException {
            reader.consumeWhiteSpace();
            if (reader.peek() == '"') {
                reader.skipString();
                return new Mk1Config();
            }
            return BUILDER.decodeJson(reader, extra);
        }

        @Override
        public Schema toSchema(SchemaContext context) {
            return BUILDER.toSchema(context);
        }
    };
}
