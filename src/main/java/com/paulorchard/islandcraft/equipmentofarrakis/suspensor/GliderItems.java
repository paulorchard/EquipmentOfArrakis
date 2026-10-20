package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.ActiveSlotInventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;

/** The engine side of {@link GliderSlot}: the glider item in the inventory components of a player (the Utility slot, the hotbar, the rest). */
final class GliderItems {

    private GliderItems() {
    }

    private static String idAt(ItemContainer container, short slot) {
        ItemStack stack = container.getItemStack(slot);
        return stack == null || stack.isEmpty() ? null : stack.getItemId();
    }

    private static ItemContainer utility(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        InventoryComponent.Utility utility = accessor.getComponent(ref, InventoryComponent.Utility.getComponentType());
        return utility == null ? null : utility.getInventory();
    }

    /** The active Utility slot number (for the log: does the slot need to be selected?). */
    static int activeUtility(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        InventoryComponent.Utility utility = accessor.getComponent(ref, InventoryComponent.Utility.getComponentType());
        return utility == null ? -1 : utility.getActiveSlot();
    }

    /** Whether our glider is in the Utility slot. */
    static boolean inUtility(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        ItemContainer utility = utility(accessor, ref);
        return utility != null && GliderSlot.removable(idAt(utility, (short) 0));
    }

    /** Applies {@link GliderSlot#place} to the first Utility slot: puts the glider in when it is empty. */
    static GliderSlot.Place placeInUtility(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        ItemContainer utility = utility(accessor, ref);
        if (utility == null) {
            return GliderSlot.Place.BLOCKED;
        }
        GliderSlot.Place result = GliderSlot.place(idAt(utility, (short) 0));
        if (result == GliderSlot.Place.PLACE) {
            utility.setItemStackForSlot((short) 0, new ItemStack(GliderSlot.ITEM_ID, 1));
        }
        return result;
    }

    /** Removes our glider from the Utility slots (and only ours); returns a word for the log. */
    static String removeFromUtility(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        ItemContainer utility = utility(accessor, ref);
        int removed = utility == null ? 0 : removeFrom(utility);
        return removed == 0 ? "not in the Utility slot (nothing removed)" : "removed from the Utility slot";
    }

    private static int removeFrom(ItemContainer container) {
        int removed = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (GliderSlot.removable(idAt(container, slot))) {
                container.removeItemStackFromSlot(slot);
                removed++;
            }
        }
        return removed;
    }

    /** The take command: our glider out of every part of the inventory; returns how many were removed. */
    static int removeEverywhere(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        List<ItemContainer> all = new ArrayList<>();
        add(all, accessor.getComponent(ref, InventoryComponent.Hotbar.getComponentType()));
        add(all, accessor.getComponent(ref, InventoryComponent.Utility.getComponentType()));
        add(all, accessor.getComponent(ref, InventoryComponent.Storage.getComponentType()));
        add(all, accessor.getComponent(ref, InventoryComponent.Backpack.getComponentType()));
        return removeFromAll(all);
    }

    /** Join cleanup: the same on the holder of a player who is being added to a world. */
    static int removeEverywhere(Holder<EntityStore> holder) {
        List<ItemContainer> all = new ArrayList<>();
        add(all, holder.getComponent(InventoryComponent.Hotbar.getComponentType()));
        add(all, holder.getComponent(InventoryComponent.Utility.getComponentType()));
        add(all, holder.getComponent(InventoryComponent.Storage.getComponentType()));
        add(all, holder.getComponent(InventoryComponent.Backpack.getComponentType()));
        return removeFromAll(all);
    }

    private static void add(List<ItemContainer> into, InventoryComponent component) {
        if (component != null && component.getInventory() != null) {
            into.add(component.getInventory());
        }
    }

    private static int removeFromAll(List<ItemContainer> containers) {
        int removed = 0;
        for (ItemContainer container : containers) {
            removed += removeFrom(container);
        }
        return removed;
    }

    /** The give command: the glider in the active hotbar slot or the Utility slot (fails when that slot holds something else). */
    static String give(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, boolean toUtility) {
        InventoryComponent.Hotbar hotbar = accessor.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        ItemContainer target = toUtility ? utility(accessor, ref) : hotbar == null ? null : hotbar.getInventory();
        short slot = toUtility ? 0 : hotbar == null ? 0 : (short) ((ActiveSlotInventoryComponent) hotbar).getActiveSlot();
        if (target == null) {
            return "no such inventory part";
        }
        String there = idAt(target, slot);
        if (there != null && !GliderSlot.removable(there)) {
            return "the " + (toUtility ? "Utility" : "active hotbar") + " slot holds " + there + "; empty it first";
        }
        target.setItemStackForSlot(slot, new ItemStack(GliderSlot.ITEM_ID, 1));
        return "glider put in the " + (toUtility ? "Utility slot (active utility slot " + activeUtility(accessor, ref) + ")" : "active hotbar slot " + slot);
    }
}
