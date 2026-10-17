package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A fillable item (the LitreJon, the Blood Bag): an amount stored in the stack's metadata under a key and mirrored to the
 * durability bar (max durability = capacity, durability = amount), so the bar is the fullness meter. Only a gauge: nothing
 * wears. The arithmetic is static and pure ({@link #clamp}, {@link #added}, {@link #room}); the stack methods wrap it.
 * A stack with no saved amount holds the full capacity or nothing, as {@code startsFull} says.
 */
public final class ContainerItem {

    private final String itemId;
    private final String key;
    private final IntSupplier capacity;
    private final Supplier<Boolean> startsFull;

    public ContainerItem(String itemId, String key, IntSupplier capacity, Supplier<Boolean> startsFull) {
        this.itemId = itemId;
        this.key = key;
        this.capacity = capacity;
        this.startsFull = startsFull;
    }

    // ---- pure arithmetic

    public static int clamp(int amount, int capacity) {
        return Math.max(0, Math.min(Math.max(0, capacity), amount));
    }

    /** The amount after adding {@code delta} (negative takes), clamped to 0..capacity. */
    public static int added(int amount, int delta, int capacity) {
        return clamp((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, (long) amount + delta)), capacity);
    }

    /** What still fits. */
    public static int room(int amount, int capacity) {
        return Math.max(0, capacity - clamp(amount, capacity));
    }

    // ---- the stack

    public String itemId() {
        return itemId;
    }

    public int capacity() {
        return capacity.getAsInt();
    }

    public boolean isContainer(ItemStack stack) {
        return stack != null && !stack.isEmpty() && itemId.equals(stack.getItemId());
    }

    /** The amount in the stack; 0 for a stack that is not this item. */
    public int getAmount(ItemStack stack) {
        if (!isContainer(stack)) {
            return 0;
        }
        Integer saved = stack.getFromMetadataOrNull(key, Codec.INTEGER);
        return saved == null ? (startsFull.get() ? capacity() : 0) : clamp(saved, capacity());
    }

    public int room(ItemStack stack) {
        return isContainer(stack) ? room(getAmount(stack), capacity()) : 0;
    }

    /** A copy of the stack holding this amount (clamped), the bar included. */
    public ItemStack withAmount(ItemStack stack, int amount) {
        int clamped = clamp(amount, capacity());
        return stack.withMetadata(key, Codec.INTEGER, clamped).withMaxDurability(capacity()).withDurability(clamped);
    }

    /** A copy with {@code delta} added (negative takes), clamped. */
    public ItemStack add(ItemStack stack, int delta) {
        return withAmount(stack, added(getAmount(stack), delta, capacity()));
    }

    /** A new single stack at the starting amount, with its bar. */
    public ItemStack fresh() {
        return withAmount(new ItemStack(itemId, 1), startsFull.get() ? capacity() : 0);
    }
}
