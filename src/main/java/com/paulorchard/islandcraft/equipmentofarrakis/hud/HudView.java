package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Arrays;

/**
 * The four bars of one player: Power over Health on the left, Heat Exposure over Water on the right, above the hotbar. Read only: it
 * shows what the game and the Water mod hold and changes nothing. The structure is one of the four generated documents
 * {@code Common/UI/Custom/Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud_<Reference|Copy>[_Creative].ui} (tools/assets/hud/make_hud.js): the
 * vanilla Health layout for the left bars and the vanilla Mana layout for the right ones, with the vanilla ProgressBar element, whose
 * {@code Value} (0 to 1) the server sets. The route (vanilla paths or copies) and the game mode (the creative fills) pick the document.
 *
 * <p>Every row is split in two halves of equal FlexWeight, so the middle of the screen (the middle of the hotbar, which the client
 * draws centred) is exact at every resolution; the pad between the middle and the bar, and the row bottom, come from the vanilla metrics
 * and the nudge of the bar. It has its own key, so it sits next to the Water mod HUD (key WaterOfArrakis) without touching it.
 */
final class HudView extends CustomUIHud {

    static final String KEY = "EquipmentOfArrakisBars";

    private final HudConfig config;
    private final boolean rightHalf;
    private final boolean creative;
    private final boolean copy;
    private final double[] sentPercent = new double[HudBar.values().length];
    private final String[] sentFill = new String[HudBar.values().length];
    private final String[] sentIcon = new String[HudBar.values().length];
    private final HudMath.Alert[] sentAlert = new HudMath.Alert[HudBar.values().length];

    /** {@code rightHalf} false: the Water mod is not there, so the Heat Exposure and Water bars are not drawn. */
    HudView(PlayerRef playerRef, HudConfig config, boolean rightHalf, boolean creative) {
        super(playerRef, KEY);
        this.config = config;
        this.rightHalf = rightHalf;
        this.creative = creative;
        this.copy = config.copyArt();
    }

    boolean creative() {
        return creative;
    }

    boolean copy() {
        return copy;
    }

    static String document(boolean copy, boolean creative) {
        return "Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud_" + (copy ? "Copy" : "Reference") + (creative ? "_Creative" : "") + ".ui";
    }

    @Override
    protected void build(UICommandBuilder ui) {
        ui.append(document(copy, creative));
        // A fresh document starts at its defaults on the client: forget what was sent before.
        Arrays.fill(sentPercent, Double.NaN);
        Arrays.fill(sentFill, null);
        Arrays.fill(sentIcon, null);
        Arrays.fill(sentAlert, HudMath.Alert.NONE);

        for (HudBar bar : HudBar.values()) {
            String n = bar.id;
            HudConfig.Nudge nudge = config.nudges().of(bar);
            ui.setObject("#" + n + "Row.Anchor", row(HudLayout.rowBottom(bar, config.getRowGap(), config.getBarsLift(), nudge.getY())));
            ui.setObject("#" + n + "Pad.Anchor", width(HudLayout.pad(bar, nudge.getX())));
            ui.set("#" + n + "Alert.Bar.Color", config.getAlertColor());
            boolean shown = bar.left || rightHalf;
            ui.set("#" + n + "Row.Visible", shown);
            switch (bar) {
                case POWER -> {
                    ui.set("#PowerFill.Bar.Color", config.getPowerColor());
                    ui.set("#PowerIcon.Background.Color", config.getPowerIconColor());
                }
                case WATER -> ui.set("#WaterIcon.Background.Color", config.getWaterIconColor());
                case EXPOSURE -> {
                    ui.set("#ExposureFill.Bar.Color", HudMath.exposureColor(0, config.getExposureRedStartPercent(), config.getExposureColorOrange(), config.getExposureColorRed()));
                    ui.set("#ExposureIcon.Background.Color", config.getExposureIconLow());
                }
                default -> {
                }
            }
        }
    }

    /**
     * Sends what changed by enough to be seen. {@code percent[i]} is the value of bar i (NaN: not drawn), {@code fill[i]} the fill tint
     * now (null: never changes), {@code icon[i]} the icon colour now (null: never changes), {@code alert[i]} the alert state.
     */
    void refresh(double[] percent, String[] fill, String[] icon, HudMath.Alert[] alert) {
        double min = config.getMinPercentChange();
        UICommandBuilder ui = null;
        for (HudBar bar : HudBar.values()) {
            int i = bar.ordinal();
            if (Double.isNaN(percent[i])) {
                continue;
            }
            String n = bar.id;
            boolean fillChanged = HudMath.changed(sentPercent[i], percent[i], min);
            boolean alertChanged = alert[i] != sentAlert[i];
            boolean tintChanged = fill[i] != null && !fill[i].equals(sentFill[i]);
            boolean iconChanged = icon[i] != null && !icon[i].equals(sentIcon[i]);
            if (!fillChanged && !alertChanged && !tintChanged && !iconChanged) {
                continue;
            }
            if (ui == null) {
                ui = new UICommandBuilder();
            }
            if (fillChanged) {
                float value = HudMath.value(percent[i]);
                ui.set("#" + n + "Fill.Value", value);
                ui.set("#" + n + "Effect.Value", value);
                sentPercent[i] = percent[i];
            }
            if (alert[i] != HudMath.Alert.NONE && (fillChanged || alertChanged)) {
                ui.set("#" + n + "Alert.Value", HudMath.value(percent[i]));
            }
            if (alertChanged) {
                ui.set("#" + n + "AlertGroup.Visible", alert[i] != HudMath.Alert.NONE);
                ui.set("#" + n + "Error.Background.Color", HudMath.white(HudMath.errorOpacity(alert[i])));
                ui.set("#" + n + "Outline.Background.Color", HudMath.white(HudMath.outlineOpacity(alert[i], config.getAlertOutlineOpacity())));
                sentAlert[i] = alert[i];
            }
            if (tintChanged) {
                ui.set("#" + n + "Fill.Bar.Color", fill[i]);
                sentFill[i] = fill[i];
            }
            if (iconChanged) {
                ui.set("#" + n + "Icon.Background.Color", icon[i]);
                sentIcon[i] = icon[i];
            }
        }
        if (ui != null) {
            update(false, ui);
        }
    }

    private static Anchor row(int bottom) {
        Anchor a = new Anchor();
        a.setLeft(Value.of(0));
        a.setRight(Value.of(0));
        a.setBottom(Value.of(bottom));
        a.setHeight(Value.of(HudMetrics.BAR_HEIGHT));
        return a;
    }

    private static Anchor width(int width) {
        Anchor a = new Anchor();
        a.setWidth(Value.of(width));
        return a;
    }
}
