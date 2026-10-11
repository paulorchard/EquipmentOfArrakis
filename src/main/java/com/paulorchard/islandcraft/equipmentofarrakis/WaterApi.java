package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.server.core.inventory.ItemStack;

/**
 * The part of Water of Arrakis that the Dew Reaper uses, as an interface without any class of that mod in it. The only
 * implementation is {@link WaterBridge}; {@link WaterLink} hands it out only when the Water mod is there, so that a missing mod cannot
 * stop this one from loading.
 */
interface WaterApi {

    boolean isLitrejon(ItemStack stack);

    /** Units in a Litrejon stack. */
    double amount(ItemStack stack);

    /** The most a Litrejon holds. */
    double capacity();

    /** A copy of the Litrejon stack holding this many units, saved amount and durability bar included. */
    ItemStack withAmount(ItemStack stack, double units);
}
