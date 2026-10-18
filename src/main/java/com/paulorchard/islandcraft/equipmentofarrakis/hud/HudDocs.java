package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import java.util.function.Predicate;

/**
 * Which HUD document to append. A document the pack does not have must never be appended: the client refuses to join ("Could not find
 * document ... for Custom UI Append command"). Pure: {@code has} says whether the pack has a document.
 */
final class HudDocs {

    static final String PREFIX = "Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud_";
    /** The document in the form the pack registry knows (under UI/Custom, with the folders of the pack). */
    static final String PACK_PREFIX = "UI/Custom/Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud_";

    private HudDocs() {
    }

    static String name(boolean creative) {
        return "Copy" + (creative ? "_Creative" : "") + ".ui";
    }

    /** The document to append for a game mode, or null when the pack does not have it. */
    static String pick(boolean creative, Predicate<String> has) {
        return has.test(PACK_PREFIX + name(creative)) ? PREFIX + name(creative) : null;
    }
}
