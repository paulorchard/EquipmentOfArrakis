package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import org.bson.BsonDocument;

/**
 * The "Suspensor" section of Equipment_of_Arrakis.json. Speeds are the fly speeds of the movement settings; they start at 2 and are meant to be
 * tuned live ({@code /arrakis suspensor set}). The defaults apply to a fresh file only: a saved file keeps its numbers.
 *
 * <p>Keys of the removed marks (Mk1, Mk2, Mk3) in an old file are ignored. A file that still has the {@code Mk3} section was saved by
 * the mark build, whose {@code ManaPerSecond} was 1: that old value is not carried over, the default applies until a new one is set
 * or the file is saved without the old section.
 */
public class SuspensorConfig {

    static final double DEFAULT_SPEED = 2.0;
    static final double DEFAULT_MANA_PER_SECOND = 10.0;
    /** TEST DEVICE: the largest |y| a {@code /arrakis suspensor push} may send. */
    static final double DEFAULT_PUSH_CAP = 6.0;

    private double horizontalSpeed = DEFAULT_SPEED;
    private double verticalSpeed = DEFAULT_SPEED;
    private double manaPerSecond = DEFAULT_MANA_PER_SECOND;
    private double resumeMana = 5.0;
    private boolean hum = true;
    private double pushCap = DEFAULT_PUSH_CAP;
    /** True when the old Mk3 section was read: the saved ManaPerSecond belongs to the mark build and is not used. */
    private boolean legacyFile;

    /** Horizontal fly speed of the movement settings. */
    public double horizontalSpeed() {
        return Math.max(0, horizontalSpeed);
    }

    /** Vertical fly speed of the movement settings (Space up, crouch down). */
    public double verticalSpeed() {
        return Math.max(0, verticalSpeed);
    }

    /** Mana the belt costs a second while the player flies (creative pays nothing, and the belt does nothing there). */
    public double manaPerSecond() {
        return Math.max(0, legacyFile ? DEFAULT_MANA_PER_SECOND : manaPerSecond);
    }

    /** After a cut-off at 0 Mana the belt arms again when Mana is back at this value or more. */
    public double resumeMana() {
        return Math.max(0.1, resumeMana);
    }

    /** TEST DEVICE: the hard cap of the push test command, blocks a second. */
    public double pushCap() {
        return Math.max(0, pushCap);
    }

    /** True: the wind loop plays while the belt flies. */
    public boolean hum() {
        return hum;
    }

    /** Live tuning: both speeds and, when given, the cost; saved from now on as the new numbers. */
    void set(double horizontal, double vertical, Double mana) {
        double keep = manaPerSecond();
        horizontalSpeed = horizontal;
        verticalSpeed = vertical;
        manaPerSecond = mana == null ? keep : mana;
        legacyFile = false;
    }

    public static final BuilderCodec<SuspensorConfig> CODEC = BuilderCodec.builder(SuspensorConfig.class, SuspensorConfig::new)
            .documentation("Settings of the suspensor module: the Suspensor Belt toggle (flight on the client's double tap of Space).")
            .append(new KeyedCodec<>("HorizontalSpeed", Codec.DOUBLE, false), (c, v) -> c.horizontalSpeed = v, c -> c.horizontalSpeed)
            .documentation("Horizontal fly speed of the movement settings (2 for a fresh file).").add()
            .append(new KeyedCodec<>("VerticalSpeed", Codec.DOUBLE, false), (c, v) -> c.verticalSpeed = v, c -> c.verticalSpeed)
            .documentation("Vertical fly speed (Space up, crouch down).").add()
            .append(new KeyedCodec<>("ManaPerSecond", Codec.DOUBLE, false), (c, v) -> c.manaPerSecond = v, c -> c.manaPerSecond())
            .documentation("Mana per second while flying.").add()
            .append(new KeyedCodec<>("ResumeMana", Codec.DOUBLE, false), (c, v) -> c.resumeMana = v, c -> c.resumeMana)
            .documentation("After the belt cut a flight off at 0 Mana, it works again at this much Mana.").add()
            .append(new KeyedCodec<>("Hum", Codec.BOOLEAN, false), (c, v) -> c.hum = v, c -> c.hum)
            .documentation("Play the wind loop while flying.").add()
            // TEST DEVICE: goes with /arrakis suspensor push
            .append(new KeyedCodec<>("PushCap", Codec.DOUBLE, false), (c, v) -> c.pushCap = v, c -> c.pushCap)
            .documentation("Test device: the largest downward or upward speed the push test command accepts.").add()
            // read only, never written: marks the file of the mark build (see the class comment)
            .append(new KeyedCodec<>("Mk3", Codec.BSON_DOCUMENT, false), (c, v) -> c.legacyFile = v != null, c -> (BsonDocument) null)
            .documentation("Old section of the mark build; ignored.").add()
            .build();
}
