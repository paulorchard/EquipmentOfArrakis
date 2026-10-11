package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Every tunable of the on-screen display, read from {@code Equipment_of_Arrakis.json} in the plugin's data folder (written with these
 * defaults at start-up if it is missing). The defaults here are the design values. {@code /arrakis hud tune} changes the live
 * object, which is not saved, so a restart goes back to the file.
 *
 * <p>All lengths are in the HUD's own pixels (the same units as the vanilla hotbar, so they scale with it). Offsets are measured from
 * the horizontal centre of the screen, which is the centre of the hotbar.
 */
public final class HudSettings {

    public static final String FILE_NAME = "Equipment_of_Arrakis";

    /** Bumped by every live change: the displays are built again when it differs from the one they were built with. */
    private static volatile int generation;

    // ---- the two readouts
    private double labelSize = 16;
    private double numberSize = 48;
    /** The alpha of the black outline round the text: 0.6 is 60% black. */
    private double outlineStrength = 0.6;
    /** From the centre of the screen to the right end of the POWER readout (just left of the Z slot). */
    private double powerOffset = 465;
    /** From the centre of the screen to the left end of the AMMO readout (just right of hotbar slot 9). */
    private double ammoOffset = 360;
    /** From the bottom of the screen to the bottom of the readouts: the baseline of the hotbar slots. */
    private double bottomMargin = 24;
    /** The width of a readout; the text is aligned to the side that faces the hotbar. */
    private double readoutWidth = 220;
    /** POWER turns {@code lowPowerColor} below this whole number. */
    private double lowPowerThreshold = 20;
    private String textColor = "#ffffff";
    private String lowPowerColor = "#ff5a2a";
    /** What AMMO shows while no gun reports a number. */
    private String ammoPlaceholder = "--";

    // ---- the tool panel
    private double toolIconSize = 40;
    private double toolNameSize = 18;
    /** The gap between the top of the POWER readout and the bottom of the tool panel. */
    private double toolGap = 8;
    private double toolPanelWidth = 260;
    /** The off-hand tools that get the panel: add an item id here to add a tool. */
    private String[] toolItems = {"Arrakis_Blood_Extractor", "Arrakis_Dew_Reaper", "Arrakis_Compactor"};

    // ---- how often
    /** The most changes of the POWER number sent to a client in a second. */
    private double powerUpdatesPerSecond = 10;

    /** A tunable number: name, getter, setter. */
    record Num(String name, Function<HudSettings, Double> get, BiConsumer<HudSettings, Double> set) {
    }

    /** A tunable text. */
    record Text(String name, Function<HudSettings, String> get, BiConsumer<HudSettings, String> set) {
    }

    static final List<Num> NUMBERS = new ArrayList<>();
    static final List<Text> TEXTS = new ArrayList<>();

    private static void num(String name, Function<HudSettings, Double> get, BiConsumer<HudSettings, Double> set) {
        NUMBERS.add(new Num(name, get, set));
    }

    private static void text(String name, Function<HudSettings, String> get, BiConsumer<HudSettings, String> set) {
        TEXTS.add(new Text(name, get, set));
    }

    static {
        num("LabelSize", s -> s.labelSize, (s, v) -> s.labelSize = v);
        num("NumberSize", s -> s.numberSize, (s, v) -> s.numberSize = v);
        num("OutlineStrength", s -> s.outlineStrength, (s, v) -> s.outlineStrength = v);
        num("PowerOffset", s -> s.powerOffset, (s, v) -> s.powerOffset = v);
        num("AmmoOffset", s -> s.ammoOffset, (s, v) -> s.ammoOffset = v);
        num("BottomMargin", s -> s.bottomMargin, (s, v) -> s.bottomMargin = v);
        num("ReadoutWidth", s -> s.readoutWidth, (s, v) -> s.readoutWidth = v);
        num("LowPowerThreshold", s -> s.lowPowerThreshold, (s, v) -> s.lowPowerThreshold = v);
        num("ToolIconSize", s -> s.toolIconSize, (s, v) -> s.toolIconSize = v);
        num("ToolNameSize", s -> s.toolNameSize, (s, v) -> s.toolNameSize = v);
        num("ToolGap", s -> s.toolGap, (s, v) -> s.toolGap = v);
        num("ToolPanelWidth", s -> s.toolPanelWidth, (s, v) -> s.toolPanelWidth = v);
        num("PowerUpdatesPerSecond", s -> s.powerUpdatesPerSecond, (s, v) -> s.powerUpdatesPerSecond = v);
        text("TextColor", s -> s.textColor, (s, v) -> s.textColor = v);
        text("LowPowerColor", s -> s.lowPowerColor, (s, v) -> s.lowPowerColor = v);
        text("AmmoPlaceholder", s -> s.ammoPlaceholder, (s, v) -> s.ammoPlaceholder = v);
    }

    public static final BuilderCodec<HudSettings> CODEC = build();

    private static BuilderCodec<HudSettings> build() {
        BuilderCodec.Builder<HudSettings> b = BuilderCodec.builder(HudSettings.class, HudSettings::new)
                .documentation("The on-screen display of Equipment of Arrakis: POWER and AMMO beside the hotbar and the tool panel.");
        for (Num n : NUMBERS) {
            b = b.append(new KeyedCodec<>(n.name(), Codec.DOUBLE, false), (s, v) -> n.set().accept(s, v), s -> n.get().apply(s)).add();
        }
        for (Text t : TEXTS) {
            b = b.append(new KeyedCodec<>(t.name(), Codec.STRING, false), (s, v) -> t.set().accept(s, v), s -> t.get().apply(s)).add();
        }
        return b.append(new KeyedCodec<>("ToolItems", Codec.STRING_ARRAY, false), (s, v) -> s.toolItems = v, s -> s.toolItems)
                .documentation("Item ids of the off-hand tools that show the tool panel while they are in use.").add()
                .build();
    }

    static int generation() {
        return generation;
    }

    /** Sets a live value by its name (case does not matter); false if there is no such tunable or the value does not fit. */
    boolean tune(String name, String value) {
        for (Num n : NUMBERS) {
            if (n.name().equalsIgnoreCase(name)) {
                try {
                    n.set().accept(this, Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    return false;
                }
                generation++;
                return true;
            }
        }
        for (Text t : TEXTS) {
            if (t.name().equalsIgnoreCase(name)) {
                t.set().accept(this, value);
                generation++;
                return true;
            }
        }
        return false;
    }

    /** One line per tunable: name and current value. */
    List<String> listing() {
        List<String> lines = new ArrayList<>();
        for (Num n : NUMBERS) {
            double v = n.get().apply(this);
            lines.add(n.name() + " " + (v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v)));
        }
        for (Text t : TEXTS) {
            lines.add(t.name() + " " + t.get().apply(this));
        }
        lines.add("ToolItems " + String.join(", ", toolItems));
        return lines;
    }

    public double getLabelSize() { return labelSize; }
    public double getNumberSize() { return numberSize; }
    public double getOutlineStrength() { return outlineStrength; }
    public double getPowerOffset() { return powerOffset; }
    public double getAmmoOffset() { return ammoOffset; }
    public double getBottomMargin() { return bottomMargin; }
    public double getReadoutWidth() { return readoutWidth; }
    public double getLowPowerThreshold() { return lowPowerThreshold; }
    public String getTextColor() { return textColor; }
    public String getLowPowerColor() { return lowPowerColor; }
    public String getAmmoPlaceholder() { return ammoPlaceholder; }
    public double getToolIconSize() { return toolIconSize; }
    public double getToolNameSize() { return toolNameSize; }
    public double getToolGap() { return toolGap; }
    public double getToolPanelWidth() { return toolPanelWidth; }
    public String[] getToolItems() { return toolItems; }
    public double getPowerUpdatesPerSecond() { return powerUpdatesPerSecond; }
}
