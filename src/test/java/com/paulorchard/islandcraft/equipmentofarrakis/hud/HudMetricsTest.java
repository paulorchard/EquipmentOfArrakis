package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The numbers in HudMetrics are the vanilla ones: tools/assets/hud/vanilla-metrics.json is what the generator read from the client's own
 * .ui files, and the two must agree.
 */
class HudMetricsTest {

    private static JsonObject vanilla() throws IOException {
        return JsonParser.parseString(Files.readString(Path.of("tools/assets/hud/vanilla-metrics.json"))).getAsJsonObject();
    }

    @Test
    void theConstantsAreTheOnesTheGeneratorReadFromTheVanillaFiles() throws IOException {
        JsonObject v = vanilla();
        assertEquals(v.get("slotSize").getAsInt(), HudMetrics.SLOT_SIZE);
        assertEquals(v.get("slotsPerRow").getAsInt(), HudMetrics.SLOTS_PER_ROW);
        assertEquals(v.get("gridPadding").getAsInt(), HudMetrics.GRID_PADDING);
        assertEquals(v.get("bottomMargin").getAsInt(), HudMetrics.BOTTOM_MARGIN);
        assertEquals(v.get("containerMargin").getAsInt(), HudMetrics.CONTAINER_MARGIN);
        assertEquals(v.get("slotSpacing").getAsInt(), HudMetrics.SLOT_SPACING);
        assertEquals(v.get("hotbarWidth").getAsInt(), HudMetrics.HOTBAR_WIDTH);
        assertEquals(v.get("hotbarHeight").getAsInt(), HudMetrics.HOTBAR_HEIGHT);
        assertEquals(v.get("closedContainerMargin").getAsInt(), HudMetrics.CLOSED_CONTAINER_MARGIN);
        assertEquals(v.get("barWidth").getAsInt(), HudMetrics.BAR_WIDTH);
        assertEquals(v.get("barHeight").getAsInt(), HudMetrics.BAR_HEIGHT);
        assertEquals(v.get("iconSize").getAsInt(), HudMetrics.ICON_SIZE);
        assertEquals(v.get("healthIconRight").getAsInt(), HudMetrics.ICON_GAP);
        assertEquals(v.get("manaIconLeft").getAsInt(), HudMetrics.ICON_GAP);
        assertEquals(v.get("iconTop").getAsInt(), HudMetrics.ICON_TOP);
        assertEquals(v.get("barAboveHotbar").getAsInt(), HudMetrics.BAR_ABOVE_HOTBAR);
        assertEquals(v.get("rowBottom").getAsInt(), HudMetrics.ROW_BOTTOM);
        assertEquals(v.get("blockWidth").getAsInt(), HudMetrics.BLOCK_WIDTH);
        assertEquals(v.get("innerGap").getAsInt(), HudMetrics.INNER_GAP);
        assertEquals(v.getAsJsonObject("alert").get("flipSeconds").getAsDouble(), HudMetrics.ALERT_FLIP_SECONDS, 0.0);
        assertEquals(v.getAsJsonObject("alert").get("outlineOpacityStart").getAsDouble(), HudMetrics.ALERT_OUTLINE_OPACITY_START, 0.0);
    }

    @Test
    void theVanillaArithmeticGivesTheKnownPositions() {
        assertEquals(702, HudMetrics.HOTBAR_WIDTH, "74 * 9 + 4 * 9");
        assertEquals(78, HudMetrics.HOTBAR_HEIGHT, "74 + 2 * 2");
        assertEquals(36, HudMetrics.CLOSED_CONTAINER_MARGIN, "30 + 6");
        assertEquals(120, HudMetrics.ROW_BOTTOM, "the vanilla Health and Mana bars: 36 + 78 + 6");
        assertEquals(346, HudMetrics.BLOCK_WIDTH, "24 + 4 + 318");
        assertEquals(5, HudMetrics.INNER_GAP, "351 - 346");
    }
}
