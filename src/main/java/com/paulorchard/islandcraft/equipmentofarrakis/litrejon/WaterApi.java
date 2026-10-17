package com.paulorchard.islandcraft.equipmentofarrakis.litrejon;

import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * The part of Water of Arrakis the LitreJon uses, as an interface with no class of that mod in it. The only implementation is
 * {@link WaterBridge}; {@link WaterLink} hands it out only when the Water mod is there, so that a missing mod cannot stop this one
 * from loading.
 */
interface WaterApi {

    /** The player's water, 0 to {@link #max()}. */
    double getWater(PlayerRef player);

    /** Adds water (clamped to 0..max by the Water mod) and returns the new value. */
    double addWater(PlayerRef player, double amount);

    double max();
}
