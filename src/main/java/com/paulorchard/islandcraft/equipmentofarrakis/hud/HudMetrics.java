package com.paulorchard.islandcraft.equipmentofarrakis.hud;

/**
 * The numbers of the vanilla Health and Mana bars and of the hotbar they sit on, as tools/assets/hud/make_hud.js reads them from the
 * client's own .ui files (Hotbar.ui, InGame/Common.ui, Health.ui, Mana.ui, StaminaPanel.ui). The generator writes what it read to
 * tools/assets/hud/vanilla-metrics.json and HudMetricsTest fails if this class and that file ever disagree, so a game update that
 * moves the vanilla bars shows up as a failing test after the generator is run again.
 */
final class HudMetrics {

    // Common.ui and Hotbar.ui
    static final int SLOT_SIZE = 74;
    static final int SLOTS_PER_ROW = 9;
    static final int GRID_PADDING = 2;
    static final int BOTTOM_MARGIN = 30;
    static final int CONTAINER_MARGIN = 6;
    static final int SLOT_SPACING = 4;
    /** @HotbarWidthHud = slot size * slots + spacing * slots. */
    static final int HOTBAR_WIDTH = SLOT_SIZE * SLOTS_PER_ROW + SLOT_SPACING * SLOTS_PER_ROW;
    /** @HotbarHeight = slot size + 2 * grid padding. */
    static final int HOTBAR_HEIGHT = SLOT_SIZE + 2 * GRID_PADDING;
    /** @InventoryClosedContainerMargin = @BottomMargin + @ContainerMargin. */
    static final int CLOSED_CONTAINER_MARGIN = BOTTOM_MARGIN + CONTAINER_MARGIN;

    // Health.ui and Mana.ui
    static final int BAR_WIDTH = 318;
    static final int BAR_HEIGHT = 12;
    static final int ICON_SIZE = 24;
    static final int ICON_GAP = 4;
    static final int ICON_TOP = -8;
    static final int BAR_ABOVE_HOTBAR = 6;
    /** Bottom of the vanilla Health and Mana bars: closed margin + hotbar height + 6. */
    static final int ROW_BOTTOM = CLOSED_CONTAINER_MARGIN + HOTBAR_HEIGHT + BAR_ABOVE_HOTBAR;
    /** Icon, gap and bar. */
    static final int BLOCK_WIDTH = ICON_SIZE + ICON_GAP + BAR_WIDTH;
    /** From the middle of the screen to the inner end of a bar: half the hotbar width less the block. */
    static final int INNER_GAP = HOTBAR_WIDTH / 2 - BLOCK_WIDTH;

    // Hotbar.ui, the last element: Label #ActiveItemNameLabel { Anchor: (Bottom: @DefaultItemSlotSpacing + @DefaultItemSlotSize + 50, Height: 40); Style: (Alignment: Center); Visible: false; }
    static final int ITEM_NAME_SLOT_SPACING = 2;
    static final int ITEM_NAME_BOTTOM = ITEM_NAME_SLOT_SPACING + SLOT_SIZE + 50;
    static final int ITEM_NAME_HEIGHT = 40;

    // StaminaPanel.ui: the low alert the bars copy (the Mana pulse in Mana.ui is a one-shot error event, not a state)
    static final double ALERT_FLIP_SECONDS = 0.25;
    static final double ALERT_OUTLINE_OPACITY_START = 0.5;

    private HudMetrics() {
    }
}
