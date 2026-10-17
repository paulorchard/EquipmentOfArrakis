package com.paulorchard.islandcraft.equipmentofarrakis.bloodbag;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ContainerInventory;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ContainerItem;

import java.util.List;

/**
 * The Blood Bag API for whoever needs bags later (the blood purifier workbench): is this stack a bag, how much blood it holds, add
 * blood to it, and the first bag with room in a player inventory (hotbar, then storage). The Blood Extractor fills bags through the
 * same core helper, by the item id.
 */
public final class BloodBag {

    private BloodBag() {
    }

    public static ContainerItem container() {
        return BloodBagModule.container();
    }

    public static boolean isBag(ItemStack stack) {
        return container().isContainer(stack);
    }

    public static int getAmount(ItemStack stack) {
        return container().getAmount(stack);
    }

    public static int capacity() {
        return container().capacity();
    }

    /** A copy of the bag with {@code blood} added (negative takes), clamped to 0..capacity, bar included. */
    public static ItemStack add(ItemStack bag, int blood) {
        return container().add(bag, blood);
    }

    /** The first bag carried with room, or null. World thread. */
    public static ContainerInventory.Slot firstWithRoom(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> player) {
        return ContainerInventory.firstWithRoom(accessor, player, container());
    }

    /** Every bag carried, hotbar first. */
    public static List<ContainerInventory.Slot> carried(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> player) {
        return ContainerInventory.carried(accessor, player, container());
    }
}
