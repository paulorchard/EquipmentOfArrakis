package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import java.util.Locale;
import java.util.Set;

/** When a dead mob's body gets the long timer, engine-free. The corpse rule itself is {@link CorpseAim#isCorpse}, shared with the Blood Extractor. */
final class CorpseRules {

    private CorpseRules() {
    }

    /**
     * A body is extended when it is a corpse by the shared rule (not a player, dead, a non-player character) and its role is not on the
     * exclusion list (names compared without regard to case). {@code excluded} holds lower-case names, see {@link #lower}.
     */
    static boolean shouldExtend(boolean isPlayer, boolean isNpc, String roleName, Set<String> excluded) {
        return CorpseAim.isCorpse(isPlayer, true, isNpc, true) && (roleName == null || !excluded.contains(lower(roleName)));
    }

    static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** The remaining time shown to the operator: seconds rounded to the nearest ten, never negative. */
    static int nearestTen(double seconds) {
        return (int) (Math.round(Math.max(0, seconds) / 10.0) * 10);
    }
}
