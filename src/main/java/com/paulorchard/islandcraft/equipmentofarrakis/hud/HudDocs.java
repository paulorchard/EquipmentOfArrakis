package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import java.util.function.Predicate;

/**
 * Which HUD document to append. A document the pack does not have must never be appended: the client refuses to join ("Could not find
 * document ... for Custom UI Append command"). The Reference documents are not in the pack (they broke the join of the first build), so the
 * Reference route is only used when the pack really has them; otherwise the Copy route is. Pure: {@code has} says whether the pack has a document.
 */
final class HudDocs {

    static final String PREFIX = "Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud_";
    /** The document in the form the pack registry knows (under UI/Custom, with the folders of the pack). */
    static final String PACK_PREFIX = "UI/Custom/Hud/Equipment_of_Arrakis/Hud/Arrakis_Hud_";

    private HudDocs() {
    }

    static String name(boolean copy, boolean creative) {
        return (copy ? "Copy" : "Reference") + (creative ? "_Creative" : "") + ".ui";
    }

    /** The document to append for a route and a game mode, or null when the pack has neither that route nor the Copy one. */
    static String pick(boolean copyRequested, boolean creative, Predicate<String> has) {
        if (!copyRequested && has.test(PACK_PREFIX + name(false, creative))) {
            return PREFIX + name(false, creative);
        }
        if (has.test(PACK_PREFIX + name(true, creative))) {
            return PREFIX + name(true, creative);
        }
        return null;
    }

    /** True when the route is the one that will really be used for a game mode (the Reference route needs its documents in the pack). */
    static boolean routeAvailable(boolean copy, boolean creative, Predicate<String> has) {
        return has.test(PACK_PREFIX + name(copy, creative));
    }
}
