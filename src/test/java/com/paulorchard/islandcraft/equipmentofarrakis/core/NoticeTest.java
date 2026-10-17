package com.paulorchard.islandcraft.equipmentofarrakis.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoticeTest {

    @Test
    void oneLinePerSecondPerKey() {
        assertTrue(Notice.due(null, 5000L));
        assertFalse(Notice.due(5000L, 5999L));
        assertTrue(Notice.due(5000L, 6000L));
    }
}
