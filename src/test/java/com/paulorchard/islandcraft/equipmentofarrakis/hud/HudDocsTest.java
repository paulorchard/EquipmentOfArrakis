package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The module never appends a document the pack does not have, so a missing or removed document cannot stop a player joining. */
class HudDocsTest {

    private static final String P = HudDocs.PACK_PREFIX;
    private static final Set<String> COPY_ONLY = Set.of(P + "Copy.ui", P + "Copy_Creative.ui");
    private static final Set<String> BOTH = Set.of(P + "Copy.ui", P + "Copy_Creative.ui", P + "Reference.ui", P + "Reference_Creative.ui");

    @Test
    void theCopyDocumentIsUsedWhenThePackHasOnlyThem() {
        assertEquals("Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud_Copy.ui", HudDocs.pick(true, false, COPY_ONLY::contains));
        assertEquals("Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud_Copy_Creative.ui", HudDocs.pick(true, true, COPY_ONLY::contains));
    }

    @Test
    void askingForTheReferenceRouteFallsBackToCopyWhenThePackHasNoReferenceDocuments() {
        assertEquals("Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud_Copy.ui", HudDocs.pick(false, false, COPY_ONLY::contains));
        assertEquals("Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud_Copy_Creative.ui", HudDocs.pick(false, true, COPY_ONLY::contains));
        assertFalse(HudDocs.routeAvailable(false, false, COPY_ONLY::contains));
        assertTrue(HudDocs.routeAvailable(true, false, COPY_ONLY::contains));
    }

    @Test
    void theReferenceDocumentIsUsedOnlyWhenAskedForAndPresent() {
        assertEquals("Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud_Reference.ui", HudDocs.pick(false, false, BOTH::contains));
        assertEquals("Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud_Reference_Creative.ui", HudDocs.pick(false, true, BOTH::contains));
        assertEquals("Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud_Copy.ui", HudDocs.pick(true, false, BOTH::contains), "copy asked for: copy");
    }

    @Test
    void withNoDocumentAtAllNothingIsAppended() {
        assertNull(HudDocs.pick(true, false, path -> false));
        assertNull(HudDocs.pick(false, true, path -> false));
        assertNull(HudDocs.pick(true, true, Set.of(P + "Copy.ui")::contains), "the creative document is missing: nothing, not the adventure one");
    }
}
