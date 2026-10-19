package com.paulorchard.islandcraft.equipmentofarrakis.core.worms;

import java.util.List;
import java.util.Set;

/**
 * The part of Worms of Arrakis this mod uses, as an interface with no class of that mod in it. The only implementation is
 * {@link WormsBridge}; {@link WormsLink} hands it out only when the Worms plugin is there, so that a missing mod cannot stop this one
 * from loading.
 */
public interface WormsApi {

    /** Registers a deployable config id as a totem. False if it was already registered. */
    boolean registerTotem(String deployableId);

    /** Removes a totem id. False if it was not registered. */
    boolean unregisterTotem(String deployableId);

    /** The registered totem ids, sorted. */
    List<String> totemIds();

    /**
     * The names of the blocks Worms counts as sand, or null when this Worms jar does not tell (the method is not public in Worms, so it
     * is reached by reflection and a jar without it, or a different one, gives null).
     */
    Set<String> sandBlockNames();
}
