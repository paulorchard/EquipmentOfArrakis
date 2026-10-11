package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.paulorchard.islandcraft.waterofarrakis.Litrejon;

/**
 * The one class of this mod that names classes of Water of Arrakis (compiled against its jar, which is not packed into this one). The
 * JVM loads it only when something first uses it, and {@link WaterLink} does that only after checking that the Water plugin is
 * present, and inside a handler for linkage errors; so when Water is not installed this class is never loaded and nothing here can
 * fail.
 */
final class WaterBridge implements WaterApi {

    @Override
    public boolean isLitrejon(ItemStack stack) {
        return Litrejon.isLitrejon(stack);
    }

    @Override
    public double amount(ItemStack stack) {
        return Litrejon.getAmount(stack);
    }

    @Override
    public double capacity() {
        return Litrejon.capacity();
    }

    @Override
    public ItemStack withAmount(ItemStack stack, double units) {
        return Litrejon.withAmount(stack, units);
    }
}
