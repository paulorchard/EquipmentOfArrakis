package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Join cleanup for one release: the glider proof put {@code Arrakis_Suspensor_Glider} into players' Utility slots. The item is gone
 * from the assets, so a saved copy is found by its id string and removed when the player joins a world. Delete this class (and its
 * line in {@link SuspensorModule}) in the release after.
 */
final class LegacyGlider {

    static final String ITEM_ID = "Arrakis_Suspensor_Glider";

    private LegacyGlider() {
    }

    static boolean isIt(String itemId) {
        return ITEM_ID.equals(itemId);
    }

    /** Removes every stack of the removed item from the inventory parts of a player being added to a world; returns how many. */
    static int removeAll(Holder<EntityStore> holder) {
        List<ItemContainer> all = new ArrayList<>();
        add(all, holder.getComponent(InventoryComponent.Hotbar.getComponentType()));
        add(all, holder.getComponent(InventoryComponent.Utility.getComponentType()));
        add(all, holder.getComponent(InventoryComponent.Storage.getComponentType()));
        add(all, holder.getComponent(InventoryComponent.Backpack.getComponentType()));
        int removed = 0;
        for (ItemContainer container : all) {
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (stack != null && !stack.isEmpty() && isIt(stack.getItemId())) {
                    container.removeItemStackFromSlot(slot);
                    removed++;
                }
            }
        }
        return removed;
    }

    private static void add(List<ItemContainer> into, InventoryComponent component) {
        if (component != null && component.getInventory() != null) {
            into.add(component.getInventory());
        }
    }
}
