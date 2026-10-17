package com.paulorchard.islandcraft.equipmentofarrakis.litrejon;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.paulorchard.islandcraft.waterofarrakis.WaterOfArrakisPlugin;
import com.paulorchard.islandcraft.waterofarrakis.WaterService;

/**
 * The one class of this mod that names classes of Water of Arrakis (compiled against its jar, which is not packed into this one). The
 * JVM loads it only when something first uses it, and {@link WaterLink} does that only after checking that the Water plugin is
 * present, and inside a handler for linkage errors; so when Water is not installed this class is never loaded and nothing here can
 * fail.
 */
final class WaterBridge implements WaterApi {

    @Override
    public double getWater(PlayerRef player) {
        return WaterOfArrakisPlugin.service().getWater(player);
    }

    @Override
    public double addWater(PlayerRef player, double amount) {
        return WaterOfArrakisPlugin.service().addWater(player, amount);
    }

    @Override
    public double max() {
        return WaterService.MAX;
    }
}
