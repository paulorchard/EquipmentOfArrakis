package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What {@code /arrakis tools give} hands out: every tool and the Blood Bag, each made by its module (a container starts at its
 * starting amount, with its bar). A null factory means a plain single stack of the item.
 */
public final class GiveRegistry {

    private static final GiveRegistry INSTANCE = new GiveRegistry();

    public record Entry(String itemId, int order, Supplier<ItemStack> factory) {

        public ItemStack make() {
            return factory == null ? new ItemStack(itemId, 1) : factory.get();
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public static GiveRegistry get() {
        return INSTANCE;
    }

    public GiveRegistry() {
    }

    public synchronized void add(String itemId, int order, Supplier<ItemStack> factory) {
        entries.put(itemId, new Entry(itemId, order, factory));
    }

    public synchronized List<Entry> all() {
        List<Entry> list = new ArrayList<>(entries.values());
        list.sort(Comparator.comparingInt(Entry::order).thenComparing(Entry::itemId));
        return list;
    }
}
