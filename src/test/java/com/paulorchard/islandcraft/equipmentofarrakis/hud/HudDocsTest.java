package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The module never appends a document the pack does not have, so a missing or removed document cannot stop a player joining. */
class HudDocsTest {

    private static final String P = HudDocs.PACK_PREFIX;
    private static final Set<String> COPY = Set.of(P + "Copy.ui", P + "Copy_Creative.ui");

    @Test
    void theCopyDocumentOfTheGameModeIsUsed() {
        assertEquals("Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud_Copy.ui", HudDocs.pick(false, COPY::contains));
        assertEquals("Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud_Copy_Creative.ui", HudDocs.pick(true, COPY::contains));
    }

    @Test
    void withNoDocumentAtAllNothingIsAppended() {
        assertNull(HudDocs.pick(false, path -> false));
        assertNull(HudDocs.pick(true, Set.of(P + "Copy.ui")::contains), "the creative document is missing: nothing, not the adventure one");
    }
}
