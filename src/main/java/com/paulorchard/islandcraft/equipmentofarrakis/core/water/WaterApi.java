package com.paulorchard.islandcraft.equipmentofarrakis.core.water;

import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * The part of Water of Arrakis this mod uses, as an interface with no class of that mod in it. The only implementation is
 * {@link WaterBridge}; {@link WaterLink} hands it out only when the Water mod is there, so that a missing mod cannot stop this one
 * from loading. The methods that older Water jars do not have (the HUD switch, the exposure colour rules) are reached by reflection
 * inside the bridge and report "not there" instead of failing.
 */
public interface WaterApi {

    /** The player's water, 0 to {@link #max()}. */
    double getWater(PlayerRef player);

    /** Adds water (clamped to 0..max by the Water mod) and returns the new value. */
    double addWater(PlayerRef player, double amount);

    double max();

    /** The player's heat exposure, 0 to {@link #max()}. */
    double getExposure(PlayerRef player);

    /**
     * Asks the Water mod not to draw its own bars (true) or to draw them again (false). Returns false when this Water jar has no such
     * switch (an older jar), true when it was set.
     */
    boolean setHudHidden(boolean hidden);

    /**
     * The exposure bar colour rules of the Water mod, or null when this jar does not expose them (an older jar): the colour is then
     * worked out here from the defaults.
     */
    ExposureColors exposureColors();

    /** The rules behind the exposure colour: orange up to {@code redStartPercent}, then a straight blend to red at 100. */
    record ExposureColors(double redStartPercent, String orange, String red) {
    }
}
