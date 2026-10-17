package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finding and filling the fillable items (see {@link ContainerItem}) a player carries: the hotbar first, then the main inventory
 * (not the backpack, not the utility slots). The choice is pure ({@link #firstWithRoom(int[][])}); the adapter reads the inventory.
 * The containers of the mod are registered here by item id, so a module that fills another module's item (the Dew Harvester fills
 * the LitreJon, the Blood Extractor fills the Blood Bag) needs only the id.
 */
public final class ContainerInventory {

    private static final Map<String, ContainerItem> REGISTERED = new ConcurrentHashMap<>();

    private ContainerInventory() {
    }

    public static void register(ContainerItem container) {
        REGISTERED.put(container.itemId(), container);
    }

    /** The container definition of an item id, or null when its module is off. */
    public static ContainerItem find(String itemId) {
        return REGISTERED.get(itemId);
    }

    /** A container stack found in the inventory: where it is and what it holds. */
    public record Slot(ItemContainer container, short slot, ItemStack stack, boolean hotbar) {
    }

    // ---- the pure choice

    /**
     * {@code room[place][slot]}: the room left in each slot, or a negative number where the slot is not the container item. Returns
     * {place, slot} of the first slot with room above 0, in the order given, or null.
     */
    public static int[] firstWithRoom(int[][] room) {
        for (int place = 0; place < room.length; place++) {
            for (int slot = 0; slot < room[place].length; slot++) {
                if (room[place][slot] > 0) {
                    return new int[] {place, slot};
                }
            }
        }
        return null;
    }

    // ---- the adapter

    /** Every stack of the container item the player carries, hotbar first, in slot order. World thread. */
    public static List<Slot> carried(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> player, ContainerItem container) {
        List<Slot> found = new ArrayList<>();
        InventoryComponent.Hotbar hotbar = accessor.getComponent(player, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Storage storage = accessor.getComponent(player, InventoryComponent.Storage.getComponentType());
        scan(hotbar == null ? null : hotbar.getInventory(), true, container, found);
        scan(storage == null ? null : storage.getInventory(), false, container, found);
        return found;
    }

    private static void scan(ItemContainer place, boolean hotbar, ContainerItem container, List<Slot> into) {
        if (place == null) {
            return;
        }
        for (short slot = 0; slot < place.getCapacity(); slot++) {
            ItemStack stack = place.getItemStack(slot);
            if (container.isContainer(stack)) {
                into.add(new Slot(place, slot, stack, hotbar));
            }
        }
    }

    /** The first carried stack of the container item with room, or null. */
    public static Slot firstWithRoom(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> player, ContainerItem container) {
        List<Slot> all = carried(accessor, player, container);
        int[][] room = {new int[all.size()]};
        for (int i = 0; i < all.size(); i++) {
            room[0][i] = container.room(all.get(i).stack());
        }
        int[] pick = firstWithRoom(room);
        return pick == null ? null : all.get(pick[1]);
    }

    /**
     * Adds up to {@code amount} to the first stack with room and returns how much went in (0 when there is none). The stack is
     * written back to its slot. World thread.
     */
    public static int addToFirst(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> player, ContainerItem container, int amount) {
        Slot target = firstWithRoom(accessor, player, container);
        if (target == null || amount <= 0) {
            return 0;
        }
        int before = container.getAmount(target.stack());
        ItemStack after = container.add(target.stack(), amount);
        target.container().setItemStackForSlot(target.slot(), after);
        return container.getAmount(after) - before;
    }
}
