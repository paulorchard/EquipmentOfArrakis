package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * The on-screen display of Equipment of Arrakis, one per player: POWER (big white number) to the left of the hotbar, AMMO to the
 * right of it, and the tool panel (icon and name of the off-hand tool in use) above POWER. Read only: it shows what the game holds
 * and changes nothing. The structure is {@code Common/UI/Custom/Hud/Equipment_of_Arrakis/Arrakis_Equipment.ui} (its comments
 * explain the layout); every size, offset and colour is set from {@link HudSettings} when the document is built, and the four text
 * elements are added here, inline, because their font size, outline and colour are settings.
 *
 * <p>It has its own key, so it sits next to the Water HUD (key {@code WaterOfArrakis}) without touching it: the server keeps custom
 * HUDs in a map by key and each one is its own packet.
 */
final class EquipmentHud extends CustomUIHud {

    static final String KEY = "EquipmentOfArrakis";
    private static final String DOCUMENT = "Hud/Equipment_of_Arrakis/Arrakis_Equipment.ui";

    private final HudSettings settings;
    /** The settings generation this document was built with: a live change makes the system build a new one. */
    final int generation;

    private boolean sentBefore;
    private int sentPower;
    private boolean sentLow;
    private String sentAmmo = "";
    private String sentTool;
    private boolean sentToolShown;

    EquipmentHud(PlayerRef playerRef, HudSettings settings) {
        super(playerRef, KEY);
        this.settings = settings;
        this.generation = HudSettings.generation();
    }

    @Override
    protected void build(UICommandBuilder ui) {
        ui.append(DOCUMENT);
        // A fresh document starts at its defaults on the client: forget what was sent before.
        sentBefore = false;
        sentAmmo = "";
        sentTool = null;
        sentToolShown = false;

        int label = px(settings.getLabelSize());
        int number = px(settings.getNumberSize());
        int rowHeight = (int) Math.ceil(label * 1.4 + number * 1.3);
        int bottom = px(settings.getBottomMargin());
        int readout = px(settings.getReadoutWidth());

        ui.setObject("#EquipRow.Anchor", anchor(0, 0, bottom, rowHeight));
        ui.setObject("#PowerReadout.Anchor", width(readout));
        ui.setObject("#AmmoReadout.Anchor", width(readout));
        ui.setObject("#PowerPad.Anchor", width(px(settings.getPowerOffset())));
        ui.setObject("#AmmoPad.Anchor", width(px(settings.getAmmoOffset())));

        String outline = HudMath.outline(settings.getOutlineStrength());
        String color = settings.getTextColor();
        ui.appendInline("#PowerReadout", text("PowerLabel", "POWER", label, color, outline, "End", false));
        ui.appendInline("#PowerReadout", text("PowerNumber", "0", number, color, outline, "End", true));
        ui.appendInline("#AmmoReadout", text("AmmoLabel", "AMMO", label, color, outline, "Start", false));
        ui.appendInline("#AmmoReadout", text("AmmoNumber", settings.getAmmoPlaceholder(), number, color, outline, "Start", true));

        // The tool panel: a row above the readouts, right-aligned to the same offset as POWER.
        int icon = px(settings.getToolIconSize());
        int name = px(settings.getToolNameSize());
        int toolHeight = Math.max(icon, (int) Math.ceil(name * 1.4));
        ui.setObject("#ToolRow.Anchor", anchor(0, 0, bottom + rowHeight + px(settings.getToolGap()), toolHeight));
        ui.setObject("#ToolPanel.Anchor", width(px(settings.getToolPanelWidth())));
        ui.setObject("#ToolPad.Anchor", width(px(settings.getPowerOffset())));
        Anchor iconAnchor = new Anchor();
        iconAnchor.setWidth(Value.of(icon));
        iconAnchor.setHeight(Value.of(icon));
        ui.setObject("#ToolIcon.Anchor", iconAnchor);
        ui.appendInline("#ToolNameHolder", "Label #ToolName { Anchor: (Left: 8, Height: " + toolHeight + "); Text: \" \"; Style: ("
                + "FontSize: " + name + ", TextColor: " + color + ", OutlineColor: " + outline
                + ", RenderBold: true, HorizontalAlignment: Start, VerticalAlignment: Center); }");
        ui.set("#ToolRow.Visible", false);
    }

    /** One text element, as inline .ui text. */
    private static String text(String id, String content, int size, String color, String outline, String align, boolean bold) {
        return "Label #" + id + " { Text: \"" + content + "\"; Style: (FontSize: " + size + ", TextColor: " + color
                + ", OutlineColor: " + outline + ", RenderBold: " + bold + ", HorizontalAlignment: " + align + "); }";
    }

    private static int px(double value) {
        return (int) Math.round(value);
    }

    private static Anchor width(int w) {
        Anchor a = new Anchor();
        a.setWidth(Value.of(w));
        return a;
    }

    private static Anchor anchor(int left, int right, int bottom, int height) {
        Anchor a = new Anchor();
        a.setLeft(Value.of(left));
        a.setRight(Value.of(right));
        a.setBottom(Value.of(bottom));
        a.setHeight(Value.of(height));
        return a;
    }

    /**
     * Sends what changed since the last call: the POWER number and colour (only when {@code powerDue}), the AMMO text, the tool panel.
     * Returns true when a POWER update was sent.
     */
    boolean refresh(int power, boolean low, boolean powerDue, String ammo, String toolId) {
        UICommandBuilder ui = new UICommandBuilder();
        boolean any = false;
        boolean powerSent = false;
        if (powerDue && HudMath.powerChanged(sentBefore, sentPower, sentLow, power, low)) {
            ui.set("#PowerNumber.Text", String.valueOf(power));
            if (!sentBefore || sentLow != low) {
                ui.set("#PowerNumber.Style.TextColor", low ? settings.getLowPowerColor() : settings.getTextColor());
            }
            sentBefore = true;
            sentPower = power;
            sentLow = low;
            any = true;
            powerSent = true;
        }
        if (!ammo.equals(sentAmmo)) {
            ui.set("#AmmoNumber.Text", ammo);
            sentAmmo = ammo;
            any = true;
        }
        if (toolId == null) {
            if (sentToolShown) {
                ui.set("#ToolRow.Visible", false);
                sentToolShown = false;
                sentTool = null;
                any = true;
            }
        } else if (!sentToolShown || !toolId.equals(sentTool)) {
            ui.set("#ToolIcon.ItemId", toolId);
            ui.set("#ToolName.Text", toolName(toolId));
            ui.set("#ToolRow.Visible", true);
            sentToolShown = true;
            sentTool = toolId;
            any = true;
        }
        if (any) {
            update(false, ui);
        }
        return powerSent;
    }

    /** The item's own display name, which follows the language files ({@code server.items.<id>.name}). */
    private static Message toolName(String itemId) {
        return Message.translation("server.items." + itemId + ".name");
    }
}
