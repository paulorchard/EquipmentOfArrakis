package com.paulorchard.islandcraft.equipmentofarrakis.picker;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The tool picker's engine-free rules. The tools are found by a predicate (the ToolRegistry); a slot holds an item id, or null
 * when empty. Choosing a tool swaps two stacks in place: the chosen one goes to the active hotbar slot (the hand), the one that was
 * in the hand goes to the slot the chosen one came from. No free slot is needed and nothing is parked.
 */
final class ToolSwap {

    private ToolSwap() {
    }

    enum Where { HOTBAR, STORAGE }

    record Loc(Where where, int slot) {
    }

    record Found(Loc loc, String itemId) {
    }

    enum Outcome { SWAPPED, ALREADY_IN_HAND, HAND_NOT_TOOL, CHOSEN_GONE }

    /** What to do: the outcome, and when SWAPPED the slot to swap with the hand. */
    record Plan(Outcome outcome, Loc from, int handSlot) {
    }

    /** Every tool in the hotbar, then in the storage, in slot order. */
    static List<Found> find(List<String> hotbar, List<String> storage, Predicate<String> isTool) {
        List<Found> found = new ArrayList<>();
        scan(Where.HOTBAR, hotbar, isTool, found);
        scan(Where.STORAGE, storage, isTool, found);
        return found;
    }

    private static void scan(Where where, List<String> slots, Predicate<String> isTool, List<Found> into) {
        for (int slot = 0; slot < slots.size(); slot++) {
            String id = slots.get(slot);
            if (id != null && isTool.test(id)) {
                into.add(new Found(new Loc(where, slot), id));
            }
        }
    }

    /**
     * Decides a click against the inventory as it is now. The hand must still hold a tool, and the chosen slot must still hold the
     * item the page showed. A tool already in the hand (the same slot) is a no-op.
     */
    static Plan plan(List<String> hotbar, List<String> storage, int activeSlot, Loc chosen, String expectedId, Predicate<String> isTool) {
        if (activeSlot < 0 || activeSlot >= hotbar.size() || hotbar.get(activeSlot) == null || !isTool.test(hotbar.get(activeSlot))) {
            return new Plan(Outcome.HAND_NOT_TOOL, chosen, activeSlot);
        }
        List<String> source = chosen.where() == Where.HOTBAR ? hotbar : storage;
        if (chosen.slot() < 0 || chosen.slot() >= source.size() || source.get(chosen.slot()) == null
                || !isTool.test(source.get(chosen.slot())) || !source.get(chosen.slot()).equals(expectedId)) {
            return new Plan(Outcome.CHOSEN_GONE, chosen, activeSlot);
        }
        if (chosen.where() == Where.HOTBAR && chosen.slot() == activeSlot) {
            return new Plan(Outcome.ALREADY_IN_HAND, chosen, activeSlot);
        }
        return new Plan(Outcome.SWAPPED, chosen, activeSlot);
    }

    /** Applies a SWAPPED plan to two lists (the tests; the game does the same with ItemContainer.swapItems). */
    static <T> void apply(List<T> hotbar, List<T> storage, Plan plan) {
        if (plan.outcome() != Outcome.SWAPPED) {
            return;
        }
        List<T> source = plan.from().where() == Where.HOTBAR ? hotbar : storage;
        T held = hotbar.get(plan.handSlot());
        hotbar.set(plan.handSlot(), source.get(plan.from().slot()));
        source.set(plan.from().slot(), held);
    }

    /** "Hotbar 3" (slots are counted from 1 on the bar) or "Inventory". */
    static String where(Loc loc) {
        return loc.where() == Where.HOTBAR ? "Hotbar " + (loc.slot() + 1) : "Inventory";
    }
}
