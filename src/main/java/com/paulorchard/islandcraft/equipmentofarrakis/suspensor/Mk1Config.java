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

    /** How the descent is applied. */
    public enum Route { SETTINGS, VELOCITY }

    private boolean listed = true;
    private String route = "settings";
    private double descentSpeed = 4.0;
    private double speedThreshold = 12.0;
    private int speedTicks = 2;
    private double dragFactor = 0.0;
    private String field = "drag";
    private double terminalSpeed = 22.0;
    private int pushTicks = 2;
    private double manaPerSecond = 0.0;

    /** False: the two Mk I toggles are not registered at all. */
    public boolean listed() {
        return listed;
    }

    public Route route() {
        return "velocity".equalsIgnoreCase(route) ? Route.VELOCITY : Route.SETTINGS;
    }

    void route(Route value) {
        route = value.name().toLowerCase(Locale.ROOT);
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

    /** The factor the settings route multiplies drag by (or divides mass by); 0 or less: derived from the observed terminal speed and the target. */
    public double dragFactorSetting() {
        return dragFactor;
    }

    void dragFactor(double value) {
        dragFactor = value;
    }

    /**
     * The factor in force. Under quadratic drag the terminal speed goes as the square root of mass over drag, so lowering it from the
     * observed {@link #terminalSpeed()} to the target takes a factor of (observed / target) squared.
     */
    public double dragFactor() {
        if (dragFactor > 0) {
            return dragFactor;
        }
        double ratio = terminalSpeed() / descentSpeed();
        return Math.max(1.0, ratio * ratio);
    }

    /** True: the settings route changes {@code mass} (divided) instead of {@code dragCoefficient} (multiplied). */
    public boolean massField() {
        return "mass".equalsIgnoreCase(field);
    }

    void field(String value) {
        field = value;
    }

    /** The terminal fall speed seen at the default settings (blocks a second). */
    public double terminalSpeed() {
        return Math.max(1.0, terminalSpeed);
    }

    void terminalSpeed(double value) {
        terminalSpeed = value;
    }

    /** Ticks between two velocity pushes (velocity route). */
    public int pushTicks() {
        return Math.max(1, pushTicks);
    }

    void pushTicks(int value) {
        pushTicks = value;
    }

    /** Mana a second while the descent is engaged (0: free). */
    public double manaPerSecond() {
        return Math.max(0, manaPerSecond);
    }

    void manaPerSecond(double value) {
        manaPerSecond = value;
    }

    BeltMachine.FallRules rules() {
        return new BeltMachine.FallRules(route() == Route.VELOCITY, descentSpeed(), speedThreshold(), speedTicks(), (float) dragFactor(), massField(),
                pushTicks(), (float) manaPerSecond());
    }

    static final BuilderCodec<Mk1Config> BUILDER = BuilderCodec.builder(Mk1Config.class, Mk1Config::new)
            .append(new KeyedCodec<>("Listed", Codec.BOOLEAN, false), (c, v) -> c.listed = v, c -> c.listed)
            .documentation("True: the two Mk I toggles are listed on the picker page (this test build). False: not registered.").add()
            .append(new KeyedCodec<>("Route", Codec.STRING, false), (c, v) -> c.route = v, c -> c.route)
            .documentation("settings: push heavier drag once at the start of the descent and back at the end. velocity: push the vertical speed every PushTicks ticks.").add()
            .append(new KeyedCodec<>("DescentSpeed", Codec.DOUBLE, false), (c, v) -> c.descentSpeed = v, c -> c.descentSpeed)
            .documentation("Target descent speed in blocks a second.").add()
            .append(new KeyedCodec<>("SpeedThreshold", Codec.DOUBLE, false), (c, v) -> c.speedThreshold = v, c -> c.speedThreshold)
            .documentation("The speed mark engages when the client falls faster than this (blocks a second; fall damage starts at 21).").add()
            .append(new KeyedCodec<>("SpeedTicks", Codec.INTEGER, false), (c, v) -> c.speedTicks = v, c -> c.speedTicks)
            .documentation("Consecutive ticks above the threshold before the speed mark engages.").add()
            .append(new KeyedCodec<>("DragFactor", Codec.DOUBLE, false), (c, v) -> c.dragFactor = v, c -> c.dragFactor)
            .documentation("Settings route: drag is multiplied by this (mass divided, with Field mass). 0: derived as (TerminalSpeed / DescentSpeed) squared.").add()
            .append(new KeyedCodec<>("Field", Codec.STRING, false), (c, v) -> c.field = v, c -> c.field)
            .documentation("Settings route: drag (dragCoefficient, the default) or mass.").add()
            .append(new KeyedCodec<>("TerminalSpeed", Codec.DOUBLE, false), (c, v) -> c.terminalSpeed = v, c -> c.terminalSpeed)
            .documentation("The terminal fall speed seen at the default physics (about 22 blocks a second), used to derive DragFactor.").add()
            .append(new KeyedCodec<>("PushTicks", Codec.INTEGER, false), (c, v) -> c.pushTicks = v, c -> c.pushTicks)
            .documentation("Velocity route: ticks between two pushes.").add()
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
