package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The "Hud" section of Equipment_of_Arrakis.json. The bars are the vanilla Health and Mana bars laid out like the vanilla ones, so their
 * size and place come from the game's own numbers ({@link HudMetrics}); what is here is only what can be chosen: how far the top row is
 * above the bottom one, a lift, the art route, the colours, which bars alert, and the nudge of each bar. Lengths are virtual pixels.
 * A change to the layout or the art needs the document to be built again: a restart, or {@code /arrakis hud off} and {@code on}
 * (the nudge and art commands rebuild it for everyone at once).
 *
 * <p>Removed against the first version (its own art): BarThickness, TopOuter, BottomOuter, InnerGap, IconSize, TopIconCentre,
 * BottomIconCentre, BottomRowBottom, RowPitch (now RowGap), HealthColor and WaterColor (the vanilla fills are used untinted), FlashHz and
 */
public final class HudConfig {

    // ---- layout and art
    /** Raises both rows by this much (for example to clear the held item name). */
    private double barsLift = 0;
    /** From the bottom of the lower row (where the vanilla Health and Mana bars sit) to the bottom of the upper row. */
    private double rowGap = 26;
    /** "copy" (default): the copies shipped with the mod; "reference": the vanilla texture paths, only if the pack has the Reference documents (it does not by default: they broke the client join). */
    private String artSource = "copy";

    // ---- colours
    private String powerColor = "#57bbaf";
    private String powerIconColor = "#7fd0ff";
    private String waterIconColor = "#4da3ff";
    /** The Heat Exposure icon goes from the first to the second colour as exposure rises. */
    private String exposureIconLow = "#ffd23f";
    private String exposureIconHigh = "#ff8a1f";
    /** Exposure colour rules, used only when the Water mod does not give its own: the Water defaults. */
    private double exposureRedStartPercent = 75;
    private String exposureColorOrange = "#ff8a1f";
    private String exposureColorRed = "#e0201a";

    // ---- the low alert, copied from the vanilla stamina low alert (StaminaPanel.ui)
    /** A bar whose switch is on turns red and flashes below this percent. */
    private double flashBelowPercent = 5;
    /** The fill colour while alerting (the vanilla error overlay red, brightened a fifth). */
    private String alertColor = "#e6004f";
    /** The vanilla @LowAlertFlashTimer: the error overlay flips this often (4 updates a second at most). */
    private double alertFlipSeconds = 0.25;
    private boolean flashPower = false;
    private boolean flashHealth = true;
    private boolean flashExposure = false;
    private boolean flashWater = true;

    // ---- behaviour
    /** A fill is sent only when it moved by this many percent (always at 0 and 100). */
    private double minPercentChange = 0.25;
    /** Seconds a /arrakis hud demo value is held. */
    private double demoSeconds = 6;

    // ---- the held item name (a client label; see HudItemName)
    /** Ignored: a custom HUD cannot reach the vanilla item name label (tested on a client), so nothing is sent. Kept for a future game version. */
    private boolean itemNameMove = false;
    private double itemNameTop = 16;
    private double itemNameLeft = 16;
    private double itemNameWidth = 420;
    /** 40 is the vanilla height. */
    private double itemNameHeight = 40;

    private Nudges nudges = new Nudges();

    /** An offset of one bar in virtual pixels: x + moves right, y + moves up. */
    public static final class Nudge {
        double x;
        double y;

        public static final BuilderCodec<Nudge> CODEC = BuilderCodec.builder(Nudge.class, Nudge::new)
                .append(new KeyedCodec<>("X", Codec.DOUBLE, false), (n, v) -> n.x = v, n -> n.x)
                .documentation("Pixels to the right (negative: left).").add()
                .append(new KeyedCodec<>("Y", Codec.DOUBLE, false), (n, v) -> n.y = v, n -> n.y)
                .documentation("Pixels up (negative: down).").add()
                .build();

        public double getX() { return x; }
        public double getY() { return y; }
    }

    /** The four nudges, by position: LeftBottom (Health), RightBottom (Water), LeftTop (Power), RightTop (Heat Exposure). */
    public static final class Nudges {
        Nudge leftBottom = new Nudge();
        Nudge rightBottom = new Nudge();
        Nudge leftTop = new Nudge();
        Nudge rightTop = new Nudge();

        public static final BuilderCodec<Nudges> CODEC = BuilderCodec.builder(Nudges.class, Nudges::new)
                .append(new KeyedCodec<>("LeftBottom", Nudge.CODEC, false), (n, v) -> n.leftBottom = v, n -> n.leftBottom)
                .documentation("Health.").add()
                .append(new KeyedCodec<>("RightBottom", Nudge.CODEC, false), (n, v) -> n.rightBottom = v, n -> n.rightBottom)
                .documentation("Water.").add()
                .append(new KeyedCodec<>("LeftTop", Nudge.CODEC, false), (n, v) -> n.leftTop = v, n -> n.leftTop)
                .documentation("Power.").add()
                .append(new KeyedCodec<>("RightTop", Nudge.CODEC, false), (n, v) -> n.rightTop = v, n -> n.rightTop)
                .documentation("Heat Exposure.").add()
                .build();

        Nudge of(HudBar bar) {
            return switch (bar) {
                case HEALTH -> leftBottom;
                case WATER -> rightBottom;
                case POWER -> leftTop;
                case EXPOSURE -> rightTop;
            };
        }

        /** Adds a movement in pixels to a bar (nudges accumulate). */
        void add(HudBar bar, double dx, double dy) {
            Nudge nudge = of(bar);
            nudge.x = round(nudge.x + dx);
            nudge.y = round(nudge.y + dy);
        }

        void reset(HudBar bar) {
            Nudge nudge = of(bar);
            nudge.x = 0;
            nudge.y = 0;
        }

        void resetAll() {
            for (HudBar bar : HudBar.values()) {
                reset(bar);
            }
        }

        private static double round(double value) {
            return Math.round(value * 100.0) / 100.0;
        }
    }

    public static final BuilderCodec<HudConfig> CODEC = build();

    private static BuilderCodec<HudConfig> build() {
        BuilderCodec.Builder<HudConfig> b = BuilderCodec.builder(HudConfig.class, HudConfig::new)
                .documentation("The four-bar HUD above the hotbar (Power, Health, Heat Exposure, Water), drawn with the vanilla Health and Mana bar art.");
        b = num(b, "BarsLift", (c, v) -> c.barsLift = v, c -> c.barsLift, "Raises both rows by this many pixels (to clear the held item name, for example).");
        b = num(b, "RowGap", (c, v) -> c.rowGap = v, c -> c.rowGap, "From the bottom of the lower row (where the vanilla Health and Mana bars sit) to the bottom of the upper row.");
        b = str(b, "ArtSource", (c, v) -> c.artSource = v, c -> c.artSource, "copy: the copies shipped with the mod (default); reference: the vanilla texture paths, used only if the pack has the Reference documents (it does not by default).");
        b = str(b, "PowerColor", (c, v) -> c.powerColor = v, c -> c.powerColor, "Power fill colour (a tint of a neutral copy of the vanilla fill).");
        b = str(b, "PowerIconColor", (c, v) -> c.powerIconColor = v, c -> c.powerIconColor, "Power icon colour.");
        b = str(b, "WaterIconColor", (c, v) -> c.waterIconColor = v, c -> c.waterIconColor, "Water icon colour.");
        b = str(b, "ExposureIconLow", (c, v) -> c.exposureIconLow = v, c -> c.exposureIconLow, "Heat Exposure icon colour at 0 percent.");
        b = str(b, "ExposureIconHigh", (c, v) -> c.exposureIconHigh = v, c -> c.exposureIconHigh, "Heat Exposure icon colour at 100 percent.");
        b = num(b, "ExposureRedStartPercent", (c, v) -> c.exposureRedStartPercent = v, c -> c.exposureRedStartPercent,
                "Fallback only (used when the Water mod gives no colour rules): exposure percent where orange starts to blend to red.");
        b = str(b, "ExposureColorOrange", (c, v) -> c.exposureColorOrange = v, c -> c.exposureColorOrange, "Fallback only: heat exposure colour up to ExposureRedStartPercent.");
        b = str(b, "ExposureColorRed", (c, v) -> c.exposureColorRed = v, c -> c.exposureColorRed, "Fallback only: heat exposure colour at 100.");
        b = num(b, "FlashBelowPercent", (c, v) -> c.flashBelowPercent = v, c -> c.flashBelowPercent, "A bar whose switch is on turns red and flashes below this percent.");
        b = str(b, "AlertColor", (c, v) -> c.alertColor = v, c -> c.alertColor, "Fill colour while alerting (the vanilla error overlay red, brightened).");
        b = num(b, "AlertFlipSeconds", (c, v) -> c.alertFlipSeconds = v, c -> c.alertFlipSeconds, "How often the error overlay flips while alerting (the vanilla stamina low alert: 0.25).");
        b = bool(b, "FlashPower", (c, v) -> c.flashPower = v, c -> c.flashPower, "Power turns red and flashes when low.");
        b = bool(b, "FlashHealth", (c, v) -> c.flashHealth = v, c -> c.flashHealth, "Health turns red and flashes when low.");
        b = bool(b, "FlashExposure", (c, v) -> c.flashExposure = v, c -> c.flashExposure, "Heat Exposure turns red and flashes when low.");
        b = bool(b, "FlashWater", (c, v) -> c.flashWater = v, c -> c.flashWater, "Water turns red and flashes when low.");
        b = num(b, "MinPercentChange", (c, v) -> c.minPercentChange = v, c -> c.minPercentChange, "A fill is sent to the client only when it moved by this many percent (always at 0 and 100).");
        b = num(b, "DemoSeconds", (c, v) -> c.demoSeconds = v, c -> c.demoSeconds, "Seconds a /arrakis hud demo value is held.");
        b = bool(b, "ItemNameMove", (c, v) -> c.itemNameMove = v, c -> c.itemNameMove, "Move the held item name label to ItemNameTop / ItemNameLeft when the HUD is built. Off until the dev command showed that it works on a client.");
        b = num(b, "ItemNameTop", (c, v) -> c.itemNameTop = v, c -> c.itemNameTop, "Distance of the moved item name from the top of the screen.");
        b = num(b, "ItemNameLeft", (c, v) -> c.itemNameLeft = v, c -> c.itemNameLeft, "Distance of the moved item name from the left of the screen.");
        b = num(b, "ItemNameWidth", (c, v) -> c.itemNameWidth = v, c -> c.itemNameWidth, "Width of the moved item name box.");
        b = num(b, "ItemNameHeight", (c, v) -> c.itemNameHeight = v, c -> c.itemNameHeight, "Height of the item name box (vanilla: 40).");
        b = b.append(new KeyedCodec<>("Nudges", Nudges.CODEC, false), (c, v) -> c.nudges = v, c -> c.nudges)
                .documentation("The hand placement of each bar, written by /arrakis hud lb|rb|lt|rt (pixels; x + right, y + up).").add();
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

    private static BuilderCodec.Builder<HudConfig> bool(BuilderCodec.Builder<HudConfig> b, String key, BiConsumer<HudConfig, Boolean> set,
            Function<HudConfig, Boolean> get, String doc) {
        return b.append(new KeyedCodec<>(key, Codec.BOOLEAN, false), set::accept, get::apply).documentation(doc).add();
    }

    public double getBarsLift() { return barsLift; }
    public double getRowGap() { return rowGap; }
    public String getArtSource() { return artSource; }
    public void setArtSource(String artSource) { this.artSource = artSource; }
    public String getPowerColor() { return powerColor; }
    public String getPowerIconColor() { return powerIconColor; }
    public String getWaterIconColor() { return waterIconColor; }
    public String getExposureIconLow() { return exposureIconLow; }
    public String getExposureIconHigh() { return exposureIconHigh; }
    public double getExposureRedStartPercent() { return exposureRedStartPercent; }
    public String getExposureColorOrange() { return exposureColorOrange; }
    public String getExposureColorRed() { return exposureColorRed; }
    public double getFlashBelowPercent() { return flashBelowPercent; }
    public String getAlertColor() { return alertColor; }
    public double getAlertFlipSeconds() { return alertFlipSeconds; }
    public double getMinPercentChange() { return minPercentChange; }
    public double getDemoSeconds() { return demoSeconds; }
    public boolean isItemNameMove() { return itemNameMove; }
    public double getItemNameTop() { return itemNameTop; }
    public double getItemNameLeft() { return itemNameLeft; }
    public double getItemNameWidth() { return itemNameWidth; }
    public double getItemNameHeight() { return itemNameHeight; }
    public Nudges nudges() { return nudges; }

    /** The alert switch of a bar. */
    public boolean flashes(HudBar bar) {
        return switch (bar) {
            case POWER -> flashPower;
            case HEALTH -> flashHealth;
            case EXPOSURE -> flashExposure;
            case WATER -> flashWater;
        };
    }

    /** True for the copy route (the default), false only when the word reference is set. */
    boolean copyArt() {
        return !"reference".equalsIgnoreCase(artSource);
    }
}
