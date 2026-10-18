package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudLayoutTest {

    @Test
    void theBottomRowSitsWhereTheVanillaBarsSitAndTheTopRowIsAGapAbove() {
        assertEquals(120, HudLayout.rowBottom(HudBar.HEALTH, 26, 0, 0));
        assertEquals(120, HudLayout.rowBottom(HudBar.WATER, 26, 0, 0));
        assertEquals(146, HudLayout.rowBottom(HudBar.POWER, 26, 0, 0));
        assertEquals(146, HudLayout.rowBottom(HudBar.EXPOSURE, 26, 0, 0));
        assertEquals(150, HudLayout.rowBottom(HudBar.POWER, 30, 0, 0), "the gap is editable");
    }

    @Test
    void theLiftMovesBothRowsAndANudgeMovesOneBar() {
        assertEquals(133, HudLayout.rowBottom(HudBar.HEALTH, 26, 13, 0));
        assertEquals(159, HudLayout.rowBottom(HudBar.POWER, 26, 13, 0));
        assertEquals(125, HudLayout.rowBottom(HudBar.HEALTH, 26, 0, 5), "y + moves up");
        assertEquals(115, HudLayout.rowBottom(HudBar.HEALTH, 26, 0, -5), "y - moves down");
        assertEquals(120, HudLayout.rowBottom(HudBar.WATER, 26, 0, 0));
    }

    @Test
    void xMovesALeftBarRightByShrinkingItsPadAndARightBarRightByGrowingIt() {
        assertEquals(5, HudLayout.pad(HudBar.HEALTH, 0));
        assertEquals(5, HudLayout.pad(HudBar.WATER, 0));
        assertEquals(4, HudLayout.pad(HudBar.HEALTH, 1), "left bar, +1 right: closer to the middle");
        assertEquals(6, HudLayout.pad(HudBar.HEALTH, -1), "left bar, 1 left: further from the middle");
        assertEquals(6, HudLayout.pad(HudBar.WATER, 1), "right bar, +1 right: further from the middle");
        assertEquals(4, HudLayout.pad(HudBar.WATER, -1));
        assertEquals(0, HudLayout.pad(HudBar.POWER, 50), "a bar stops at the middle of the screen");
        assertEquals(0, HudLayout.pad(HudBar.EXPOSURE, -50));
    }
}
