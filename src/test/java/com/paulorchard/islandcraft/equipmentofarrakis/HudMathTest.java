package com.paulorchard.islandcraft.equipmentofarrakis;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudMathTest {

    @Test
    void powerIsAWholeNumberAndZeroOnlyWhenReallyZero() {
        assertEquals(100, HudMath.powerDisplay(100f));
        assertEquals(100, HudMath.powerDisplay(99.6f));
        assertEquals(100, HudMath.powerDisplay(250f), "never more than 100");
        assertEquals(57, HudMath.powerDisplay(56.6f));
        assertEquals(56, HudMath.powerDisplay(56.4f));
        assertEquals(0, HudMath.powerDisplay(0f));
        assertEquals(0, HudMath.powerDisplay(-3f));
        assertEquals(0, HudMath.powerDisplay(Float.NaN));
        assertEquals(1, HudMath.powerDisplay(0.01f), "between 0 and 1 rounds up to 1");
        assertEquals(1, HudMath.powerDisplay(0.4f));
        assertEquals(1, HudMath.powerDisplay(0.99f));
        assertEquals(1, HudMath.powerDisplay(1f));
    }

    @Test
    void powerIsRedOrangeBelowTwentyAndWhiteFromTwenty() {
        assertTrue(HudMath.isLow(19, 20));
        assertTrue(HudMath.isLow(0, 20));
        assertFalse(HudMath.isLow(20, 20));
        assertFalse(HudMath.isLow(100, 20));
        // 19.6 shows 20: white, consistent with the number on the screen.
        assertFalse(HudMath.isLow(HudMath.powerDisplay(19.6f), 20));
        assertTrue(HudMath.isLow(HudMath.powerDisplay(19.4f), 20));
    }

    @Test
    void powerIsSentOnlyWhenTheNumberOrTheColourChanges() {
        assertTrue(HudMath.powerChanged(false, 0, false, 100, false), "the first time");
        assertFalse(HudMath.powerChanged(true, 100, false, 100, false));
        assertTrue(HudMath.powerChanged(true, 100, false, 99, false));
        assertTrue(HudMath.powerChanged(true, 20, false, 19, true));
        assertTrue(HudMath.powerChanged(true, 20, true, 20, false), "the colour alone changed");
    }

    @Test
    void updatesAreRateLimited() {
        long second = 1_000_000_000L;
        assertTrue(HudMath.due(0, 5 * second, 10), "the first one is due");
        assertFalse(HudMath.due(second, second + 50_000_000L, 10), "50 ms after is too soon at 10 a second");
        assertTrue(HudMath.due(second, second + 100_000_000L, 10));
        assertTrue(HudMath.due(second, second + 1, 0), "a rate of 0 means no limit");
    }

    @Test
    void ammoShowsTheNumberOrTheDashes() {
        assertEquals("--", HudMath.ammoText(OptionalInt.empty(), "--"));
        assertEquals("117", HudMath.ammoText(OptionalInt.of(117), "--"));
        assertEquals("0", HudMath.ammoText(OptionalInt.of(0), "--"));
        assertEquals("0", HudMath.ammoText(OptionalInt.of(-4), "--"));
    }

    @Test
    void theOutlineIsABlackColourWithTheStrengthAsAlpha() {
        assertEquals("#000000(0.60)", HudMath.outline(0.6));
        assertEquals("#000000(1.00)", HudMath.outline(7));
        assertEquals("#000000(0.00)", HudMath.outline(-1));
    }

    @Test
    void theToolPanelShowsHidesAndSwitches() {
        HudMath.ToolPanel panel = new HudMath.ToolPanel();
        assertEquals(HudMath.Action.NONE, panel.update(null));
        assertEquals(HudMath.Action.SHOW, panel.update("Arrakis_Compactor"));
        assertEquals("Arrakis_Compactor", panel.shown());
        assertEquals(HudMath.Action.NONE, panel.update("Arrakis_Compactor"), "still in use: nothing to send");
        assertEquals(HudMath.Action.SHOW, panel.update("Arrakis_Dew_Reaper"), "another tool: show it");
        assertEquals(HudMath.Action.HIDE, panel.update(null));
        assertEquals(HudMath.Action.NONE, panel.update(null));
        assertEquals(HudMath.Action.SHOW, panel.update("Arrakis_Blood_Extractor"));
        panel.forget();
        assertEquals(HudMath.Action.SHOW, panel.update("Arrakis_Blood_Extractor"), "a new document has nothing shown");
    }

    @Test
    void theDocumentAndSettingsFileHaveTheNamesTheCodeUses() throws IOException {
        String ui;
        try (InputStream in = HudMathTest.class.getResourceAsStream("/Common/UI/Custom/Hud/Equipment_of_Arrakis/Arrakis_Equipment.ui")) {
            assertTrue(in != null, "the .ui document is in the jar");
            ui = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String id : new String[] {"#EquipRow", "#PowerReadout", "#AmmoReadout", "#PowerPad", "#AmmoPad", "#ToolRow", "#ToolPanel",
                "#ToolPad", "#ToolIcon", "#ToolNameHolder"}) {
            assertTrue(ui.contains(id), id + " is in the document");
        }
        assertTrue(ui.contains("ItemIcon #ToolIcon"));
        assertTrue(ui.contains("Visible: false"), "the tool panel starts hidden");
    }
}
