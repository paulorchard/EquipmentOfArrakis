package com.paulorchard.islandcraft.equipmentofarrakis.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The picker tools: a plain data list that modules fill in during setup. The picker finds a tool in an inventory by asking this
 * list, never by an id prefix. The Blood Bag is deliberately not registered.
 */
public final class ToolRegistry {

    private static final ToolRegistry INSTANCE = new ToolRegistry();

    /** One picker tool: the item id, where it sits in the page (lower first) and an optional label key (null: the item's own name). */
    public record Tool(String itemId, int order, String labelKey) {
    }

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public static ToolRegistry get() {
        return INSTANCE;
    }

    /** A registry of its own (the tests). */
    public ToolRegistry() {
    }

    /** Registers or replaces a tool. */
    public synchronized void register(String itemId, int order, String labelKey) {
        tools.put(itemId, new Tool(itemId, order, labelKey));
    }

    public synchronized boolean isTool(String itemId) {
        return itemId != null && tools.containsKey(itemId);
    }

    public synchronized Tool find(String itemId) {
        return tools.get(itemId);
    }

    /** Every tool by display order, then by id. */
    public synchronized List<Tool> all() {
        List<Tool> list = new ArrayList<>(tools.values());
        list.sort(Comparator.comparingInt(Tool::order).thenComparing(Tool::itemId));
        return list;
    }

    public synchronized List<String> ids() {
        return all().stream().map(Tool::itemId).toList();
    }

    /** The translation key shown for a tool: its label key, else the item's name key. */
    public synchronized String nameKey(String itemId) {
        Tool tool = tools.get(itemId);
        return tool != null && tool.labelKey() != null ? tool.labelKey() : ArrakisIds.itemNameKey(itemId);
    }
}
