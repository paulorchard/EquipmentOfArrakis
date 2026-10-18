package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The "Hud" section of Equipment_of_Arrakis.json: every number of the four-bar HUD. Lengths are the HUD's virtual pixels (the unit the
 * vanilla hotbar is measured in), offsets are measured from the horizontal centre of the screen, which is the centre of the hotbar,
 * and vertical positions from the bottom of the screen. The defaults were measured from the reference screenshot (hotbar of nine
 * slots 360 virtual pixels either side of the centre, so one virtual pixel is 0.847 screenshot pixels). A change needs the document to
 * be built again: a restart, or {@code /arrakis hud off} and {@code on}.
 */
public final class HudConfig {

    // ---- layout
    /** Raises both rows by this much (for example to clear the held item name). */
    private double barsLift = 13;
    /** Bottom of the Health / Water row above the bottom of the screen: hotbar baseline 24 + slot 78 + gap 9. */
    private double bottomRowBottom = 111;
    /** From the bottom of one row to the bottom of the row above. */
    private double rowPitch = 26;
    /** Thickness of a bar; the art is drawn for 12 (tools/assets/hud/make_hud.js). */
    private double barThickness = 12;
    /** Outer end of the Power / Heat Exposure bars, from the centre. */
    private double topOuter = 314;
    /** Outer end of the Health / Water bars, from the centre. */
    private double bottomOuter = 335;
    /** Inner end of every bar, from the centre. */
    private double innerGap = 7;
    private double iconSize = 22;
    /** Centre of the Power / Heat Exposure icons, from the centre of the screen. */
    private double topIconCentre = 334;
    /** Centre of the Health / Water icons, from the centre of the screen. */
    private double bottomIconCentre = 350;

    // ---- colours
    private String powerColor = "#57bbaf";
    private String healthColor = "#d64d58";
    private String waterColor = "#599de3";
    /** Exposure colour rules, used only when the Water mod does not give its own: the Water defaults. */
    private double exposureRedStartPercent = 75;
    private String exposureColorOrange = "#ff8a1f";
    private String exposureColorRed = "#e0201a";
    /** How far a flashing bar moves toward white in its bright phase, percent. */
    private double flashTintPercent = 55;

    // ---- behaviour
    /** Power and Water flash below this percent. */
    private double flashBelowPercent = 5;
    /** Colour changes a second while flashing: the bar is bright for 1/FlashHz s, then normal for 1/FlashHz s. */
    private double flashHz = 2;
    /** A fill is sent only when it moved by this many percent (always at 0 and 100). */
    private double minPercentChange = 0.25;
    /** Seconds a /arrakis hud demo value is held. */
    private double demoSeconds = 6;

    public static final BuilderCodec<HudConfig> CODEC = build();

    private static BuilderCodec<HudConfig> build() {
        BuilderCodec.Builder<HudConfig> b = BuilderCodec.builder(HudConfig.class, HudConfig::new)
                .documentation("The four-bar HUD above the hotbar (Power, Health, Heat Exposure, Water).");
        b = num(b, "BarsLift", (c, v) -> c.barsLift = v, c -> c.barsLift, "Raises both rows by this many pixels (to clear the held item name, for example).");
        b = num(b, "BottomRowBottom", (c, v) -> c.bottomRowBottom = v, c -> c.bottomRowBottom, "Bottom of the Health / Water row above the bottom of the screen.");
        b = num(b, "RowPitch", (c, v) -> c.rowPitch = v, c -> c.rowPitch, "From the bottom of one row to the bottom of the row above.");
        b = num(b, "BarThickness", (c, v) -> c.barThickness = v, c -> c.barThickness, "Thickness of a bar (the art is drawn for 12).");
        b = num(b, "TopOuter", (c, v) -> c.topOuter = v, c -> c.topOuter, "Outer end of the Power and Heat Exposure bars, from the centre of the screen.");
        b = num(b, "BottomOuter", (c, v) -> c.bottomOuter = v, c -> c.bottomOuter, "Outer end of the Health and Water bars, from the centre of the screen.");
        b = num(b, "InnerGap", (c, v) -> c.innerGap = v, c -> c.innerGap, "Inner end of every bar, from the centre of the screen.");
        b = num(b, "IconSize", (c, v) -> c.iconSize = v, c -> c.iconSize, "Size of the round icons.");
        b = num(b, "TopIconCentre", (c, v) -> c.topIconCentre = v, c -> c.topIconCentre, "Centre of the Power and Heat Exposure icons, from the centre of the screen.");
        b = num(b, "BottomIconCentre", (c, v) -> c.bottomIconCentre = v, c -> c.bottomIconCentre, "Centre of the Health and Water icons, from the centre of the screen.");
        b = str(b, "PowerColor", (c, v) -> c.powerColor = v, c -> c.powerColor, "Power fill colour.");
        b = str(b, "HealthColor", (c, v) -> c.healthColor = v, c -> c.healthColor, "Health fill colour.");
        b = str(b, "WaterColor", (c, v) -> c.waterColor = v, c -> c.waterColor, "Water fill colour.");
        b = num(b, "ExposureRedStartPercent", (c, v) -> c.exposureRedStartPercent = v, c -> c.exposureRedStartPercent,
                "Fallback only (used when the Water mod gives no colour rules): exposure percent where orange starts to blend to red.");
        b = str(b, "ExposureColorOrange", (c, v) -> c.exposureColorOrange = v, c -> c.exposureColorOrange, "Fallback only: heat exposure colour up to ExposureRedStartPercent.");
        b = str(b, "ExposureColorRed", (c, v) -> c.exposureColorRed = v, c -> c.exposureColorRed, "Fallback only: heat exposure colour at 100.");
        b = num(b, "FlashTintPercent", (c, v) -> c.flashTintPercent = v, c -> c.flashTintPercent, "How far a flashing bar moves toward white in its bright phase, percent.");
        b = num(b, "FlashBelowPercent", (c, v) -> c.flashBelowPercent = v, c -> c.flashBelowPercent, "Power and Water flash below this percent.");
        b = num(b, "FlashHz", (c, v) -> c.flashHz = v, c -> c.flashHz, "Colour changes a second while flashing (2: bright half a second, normal half a second).");
        b = num(b, "MinPercentChange", (c, v) -> c.minPercentChange = v, c -> c.minPercentChange, "A fill is sent to the client only when it moved by this many percent (always at 0 and 100).");
        b = num(b, "DemoSeconds", (c, v) -> c.demoSeconds = v, c -> c.demoSeconds, "Seconds a /arrakis hud demo value is held.");
        return b.build();
    }

    private static BuilderCodec.Builder<HudConfig> num(BuilderCodec.Builder<HudConfig> b, String key, BiConsumer<HudConfig, Double> set,
            Function<HudConfig, Double> get, String doc) {
        return b.append(new KeyedCodec<>(key, Codec.DOUBLE, false), set::accept, get::apply).documentation(doc).add();
    }

    private static BuilderCodec.Builder<HudConfig> str(BuilderCodec.Builder<HudConfig> b, String key, BiConsumer<HudConfig, String> set,
            Function<HudConfig, String> get, String doc) {
        return b.append(new KeyedCodec<>(key, Codec.STRING, false), set::accept, get::apply).documentation(doc).add();
    }

    public double getBarsLift() { return barsLift; }
    public double getBottomRowBottom() { return bottomRowBottom; }
    public double getRowPitch() { return rowPitch; }
    public double getBarThickness() { return barThickness; }
    public double getTopOuter() { return topOuter; }
    public double getBottomOuter() { return bottomOuter; }
    public double getInnerGap() { return innerGap; }
    public double getIconSize() { return iconSize; }
    public double getTopIconCentre() { return topIconCentre; }
    public double getBottomIconCentre() { return bottomIconCentre; }
    public String getPowerColor() { return powerColor; }
    public String getHealthColor() { return healthColor; }
    public String getWaterColor() { return waterColor; }
    public double getExposureRedStartPercent() { return exposureRedStartPercent; }
    public String getExposureColorOrange() { return exposureColorOrange; }
    public String getExposureColorRed() { return exposureColorRed; }
    public double getFlashTintPercent() { return flashTintPercent; }
    public double getFlashBelowPercent() { return flashBelowPercent; }
    public double getFlashHz() { return flashHz; }
    public double getMinPercentChange() { return minPercentChange; }
    public double getDemoSeconds() { return demoSeconds; }
}
