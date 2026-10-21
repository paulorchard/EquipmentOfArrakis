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
    static final double DEFAULT_TAP_WINDOW = 0.35;
    static final double DEFAULT_REOPEN_SECONDS = 1.2;
    static final double DEFAULT_MANA_PER_SECOND = 10.0;
    static final double DEFAULT_DESCENT_SPEED = 2.0;
    /** The sink is an external velocity; nothing above this is accepted (the cap of the push test that proved it). */
    static final double MAX_DESCENT_SPEED = 6.0;

    private double horizontalSpeed = DEFAULT_SPEED;
    private double verticalSpeed = DEFAULT_SPEED;
    private double manaPerSecond = DEFAULT_MANA_PER_SECOND;
    private double resumeMana = 5.0;
    private boolean hum = true;
    private double descentSpeed = DEFAULT_DESCENT_SPEED;
    private double ascendFraction = 0.4;
    private int debounceTicks = 4;
    private int groundTicks = 2;
    private double takeoffGrace = 0.5;
    private double refreshSeconds = 1.0;
    private double descentCostFactor = 1.0;
    private double tapWindow = DEFAULT_TAP_WINDOW;
    private String tapMode = "afterJump";
    private double reopenSeconds = DEFAULT_REOPEN_SECONDS;
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

    /** Seconds from a jump start in which the double tap can start flight (0 = no gate: fly is Allowed whenever the belt is armed). */
    public double tapWindow() {
        return Math.max(0, tapWindow);
    }

    /** afterJump: the gate opens at a jump start. groundOpen: open while the player has stood on the ground for ReopenSeconds. */
    public BeltMachine.GateMode tapMode() {
        return "groundopen".equalsIgnoreCase(tapMode) ? BeltMachine.GateMode.GROUND_OPEN : BeltMachine.GateMode.AFTER_JUMP;
    }

    /** groundOpen: seconds of ground contact before the gate opens again. */
    public double reopenSeconds() {
        return Math.max(0, reopenSeconds);
    }

    /** Live tuning of the gate: the window, and when given the mode and the reopen seconds. */
    void setWindow(double window, String mode, Double reopen) {
        tapWindow = window;
        if (mode != null) {
            tapMode = "groundopen".equalsIgnoreCase(mode) ? "groundOpen" : "afterJump";
        }
        if (reopen != null) {
            reopenSeconds = reopen;
        }
    }

    /** Blocks a second of sink while flying and not asking to go up (the belt's gravity); 0 = off, the hover of the earlier builds. */
    public double descentSpeed() {
        return Math.min(MAX_DESCENT_SPEED, Math.max(0, descentSpeed));
    }

    /** The player's own vertical speed must exceed this fraction of VerticalSpeed to count as asking to go up. */
    public double ascendFraction() {
        return Math.max(0, ascendFraction);
    }

    /** Ticks the input is ignored after the descent changes state (the client's report lags our change). */
    public int debounceTicks() {
        return Math.max(0, debounceTicks);
    }

    /** Consecutive ticks on the ground, not ascending, before flight ends. */
    public int groundTicks() {
        return Math.max(1, groundTicks);
    }

    /** Seconds after a flight start or the last ascending tick in which the ground is ignored. */
    public double takeoffGrace() {
        return Math.max(0, takeoffGrace);
    }

    /** Seconds between safety re-sends of the descent push. */
    public double refreshSeconds() {
        return Math.max(0.05, refreshSeconds);
    }

    /** Multiplies the Mana cost while descending (0 = only climbing costs Power). */
    public double descentCostFactor() {
        return Math.max(0, descentCostFactor);
    }

    void setDescentSpeed(double speed) {
        descentSpeed = speed;
    }

    /** Live tuning of the other descent numbers by key (case does not matter); false when the key is unknown. */
    boolean tune(String key, double value) {
        switch (key.toLowerCase(java.util.Locale.ROOT)) {
            case "descentspeed" -> descentSpeed = value;
            case "ascendfraction" -> ascendFraction = value;
            case "debounceticks" -> debounceTicks = (int) Math.round(value);
            case "groundticks" -> groundTicks = (int) Math.round(value);
            case "takeoffgrace" -> takeoffGrace = value;
            case "refreshseconds" -> refreshSeconds = value;
            case "descentcostfactor" -> descentCostFactor = value;
            default -> {
                return false;
            }
        }
        return true;
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
            .append(new KeyedCodec<>("TapWindow", Codec.DOUBLE, false), (c, v) -> c.tapWindow = v, c -> c.tapWindow)
            .documentation("Seconds after a jump start in which a second press can start flight; 0 = no gate (fly is Allowed whenever the belt is armed).").add()
            .append(new KeyedCodec<>("TapMode", Codec.STRING, false), (c, v) -> c.tapMode = v, c -> c.tapMode)
            .documentation("afterJump (the gate opens at a jump start) or groundOpen (open after ReopenSeconds on the ground, closes TapWindow after a jump start).").add()
            .append(new KeyedCodec<>("ReopenSeconds", Codec.DOUBLE, false), (c, v) -> c.reopenSeconds = v, c -> c.reopenSeconds)
            .documentation("groundOpen: seconds of ground contact before the gate opens again.").add()
            .append(new KeyedCodec<>("DescentSpeed", Codec.DOUBLE, false), (c, v) -> c.descentSpeed = v, c -> c.descentSpeed)
            .documentation("Blocks a second of sink while flying and not asking to go up; 0 = off (hover).").add()
            .append(new KeyedCodec<>("AscendFraction", Codec.DOUBLE, false), (c, v) -> c.ascendFraction = v, c -> c.ascendFraction)
            .documentation("The player's own vertical speed must exceed this fraction of VerticalSpeed to count as asking to go up.").add()
            .append(new KeyedCodec<>("DebounceTicks", Codec.INTEGER, false), (c, v) -> c.debounceTicks = v, c -> c.debounceTicks)
            .documentation("Ticks the input is ignored after the descent changes state.").add()
            .append(new KeyedCodec<>("GroundTicks", Codec.INTEGER, false), (c, v) -> c.groundTicks = v, c -> c.groundTicks)
            .documentation("Consecutive ticks on the ground, not ascending, before flight ends.").add()
            .append(new KeyedCodec<>("TakeoffGrace", Codec.DOUBLE, false), (c, v) -> c.takeoffGrace = v, c -> c.takeoffGrace)
            .documentation("Seconds after a flight start or the last ascending tick in which the ground is ignored.").add()
            .append(new KeyedCodec<>("RefreshSeconds", Codec.DOUBLE, false), (c, v) -> c.refreshSeconds = v, c -> c.refreshSeconds)
            .documentation("Seconds between safety re-sends of the descent push.").add()
            .append(new KeyedCodec<>("DescentCostFactor", Codec.DOUBLE, false), (c, v) -> c.descentCostFactor = v, c -> c.descentCostFactor)
            .documentation("Multiplies the Mana cost while descending (0 = only climbing costs Power).").add()
            // read only, never written: marks the file of the mark build (see the class comment)
            .append(new KeyedCodec<>("Mk3", Codec.BSON_DOCUMENT, false), (c, v) -> c.legacyFile = v != null, c -> (BsonDocument) null)
            .documentation("Old section of the mark build; ignored.").add()
            .build();
}
