package com.paulorchard.islandcraft.equipmentofarrakis.picker;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.paulorchard.islandcraft.equipmentofarrakis.picker.ToolSwap.Loc;
import static com.paulorchard.islandcraft.equipmentofarrakis.picker.ToolSwap.Outcome;
import static com.paulorchard.islandcraft.equipmentofarrakis.picker.ToolSwap.Plan;
import static com.paulorchard.islandcraft.equipmentofarrakis.picker.ToolSwap.Where;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The swap rule of the tool picker. */
class ToolSwapTest {

    private static final String AXE = "Arrakis_Tool_Cutter_Ray";
    private static final String PICK = "Arrakis_Tool_Compactor";
    private static final String HOE = "Arrakis_Tool_Dew_Harvester";
    private static final String SHOVEL = "Arrakis_Tool_Blood_Extractor";

    private static final java.util.Set<String> TOOLS = java.util.Set.of(AXE, PICK, HOE, SHOVEL);

    private static Plan plan(List<String> hotbar, List<String> storage, int active, Loc chosen, String expected) {
        return ToolSwap.plan(hotbar, storage, active, chosen, expected, TOOLS::contains);
    }

    private static List<ToolSwap.Found> find(List<String> hotbar, List<String> storage) {
        return ToolSwap.find(hotbar, storage, TOOLS::contains);
    }

    private static List<String> slots(int size, Object... at) {
        List<String> list = new ArrayList<>(Collections.nCopies(size, (String) null));
        for (int i = 0; i < at.length; i += 2) {
            list.set((Integer) at[i], (String) at[i + 1]);
        }
        return list;
    }

    private static Plan choose(List<String> hotbar, List<String> storage, int active, Where where, int slot) {
        List<String> source = where == Where.HOTBAR ? hotbar : storage;
        Plan plan = plan(hotbar, storage, active, new Loc(where, slot), source.get(slot));
        ToolSwap.apply(hotbar, storage, plan);
        return plan;
    }

    @Test
    void aToolFromTheStorageTradesPlacesWithTheHand() {
        List<String> hotbar = slots(9, 0, AXE);
        List<String> storage = slots(36, 5, PICK);
        Plan plan = choose(hotbar, storage, 0, Where.STORAGE, 5);
        assertEquals(Outcome.SWAPPED, plan.outcome());
        assertEquals(PICK, hotbar.get(0));
        assertEquals(AXE, storage.get(5), "the old tool took the place of the new one");
    }

    @Test
    void aToolInAnotherHotbarSlotKeepsTheActiveSlot() {
        List<String> hotbar = slots(9, 2, AXE, 6, HOE);
        List<String> storage = slots(36);
        choose(hotbar, storage, 2, Where.HOTBAR, 6);
        assertEquals(HOE, hotbar.get(2), "the active slot stays 2 and now holds the hoe");
        assertEquals(AXE, hotbar.get(6));
    }

    @Test
    void aCompletelyFullInventoryNeedsNoFreeSlot() {
        List<String> hotbar = new ArrayList<>(Collections.nCopies(9, "Rock"));
        List<String> storage = new ArrayList<>(Collections.nCopies(36, "Rock"));
        hotbar.set(4, AXE);
        storage.set(35, SHOVEL);
        choose(hotbar, storage, 4, Where.STORAGE, 35);
        assertEquals(SHOVEL, hotbar.get(4));
        assertEquals(AXE, storage.get(35));
        assertFalse(hotbar.contains(null) || storage.contains(null), "no slot was emptied or filled");
    }

    @Test
    void theToolAlreadyInTheHandIsANoOp() {
        List<String> hotbar = slots(9, 3, AXE);
        List<String> storage = slots(36, 0, PICK);
        Plan plan = choose(hotbar, storage, 3, Where.HOTBAR, 3);
        assertEquals(Outcome.ALREADY_IN_HAND, plan.outcome());
        assertEquals(AXE, hotbar.get(3));
        assertEquals(PICK, storage.get(0));
    }

    @Test
    void aToolThatIsGoneIsRefused() {
        List<String> hotbar = slots(9, 0, AXE);
        List<String> storage = slots(36, 5, PICK);
        assertEquals(Outcome.SWAPPED, plan(hotbar, storage, 0, new Loc(Where.STORAGE, 5), PICK).outcome(), "it was there when the page was built");
        storage.set(5, null);
        assertEquals(Outcome.CHOSEN_GONE, plan(hotbar, storage, 0, new Loc(Where.STORAGE, 5), PICK).outcome());
        storage.set(5, SHOVEL);
        assertEquals(Outcome.CHOSEN_GONE, plan(hotbar, storage, 0, new Loc(Where.STORAGE, 5), PICK).outcome(), "another tool took the slot");
        assertEquals(Outcome.CHOSEN_GONE, plan(hotbar, storage, 0, new Loc(Where.STORAGE, 99), PICK).outcome(), "a slot that does not exist");
        assertEquals(AXE, hotbar.get(0), "a refusal changes nothing");
    }

    @Test
    void anEmptyHandOrANonToolInTheHandIsRefused() {
        List<String> storage = slots(36, 5, PICK);
        assertEquals(Outcome.HAND_NOT_TOOL, plan(slots(9), storage, 0, new Loc(Where.STORAGE, 5), PICK).outcome());
        assertEquals(Outcome.HAND_NOT_TOOL, plan(slots(9, 0, "Rock"), storage, 0, new Loc(Where.STORAGE, 5), PICK).outcome());
        assertEquals(Outcome.HAND_NOT_TOOL, plan(slots(9, 0, AXE), storage, 12, new Loc(Where.STORAGE, 5), PICK).outcome(), "no such hand slot");
    }

    @Test
    void twoStacksOfTheSameToolAreTwoEntriesAndTheChosenOneIsTheOneSwapped() {
        List<String> hotbar = slots(9, 0, AXE);
        List<String> storage = slots(36, 3, PICK, 7, PICK);
        assertEquals(3, find(hotbar, storage).size());
        choose(hotbar, storage, 0, Where.STORAGE, 7);
        assertEquals(PICK, hotbar.get(0));
        assertEquals(PICK, storage.get(3), "the other pickaxe was not touched");
        assertEquals(AXE, storage.get(7));
    }

    @Test
    void repeatedSwapsCycleThroughAllFourTools() {
        List<String> hotbar = slots(9, 0, AXE);
        List<String> storage = slots(36, 1, PICK, 2, HOE, 3, SHOVEL);
        List<String> held = new ArrayList<>(List.of(hotbar.get(0)));
        for (String wanted : new String[] {PICK, HOE, SHOVEL, AXE}) {
            Loc where = find(hotbar, storage).stream().filter(f -> f.itemId().equals(wanted) && f.loc().where() == Where.STORAGE)
                    .findFirst().orElseThrow().loc();
            choose(hotbar, storage, 0, where.where(), where.slot());
            held.add(hotbar.get(0));
        }
        assertEquals(List.of(AXE, PICK, HOE, SHOVEL, AXE), held);
        List<String> all = new ArrayList<>(find(hotbar, storage).stream().map(ToolSwap.Found::itemId).toList());
        Collections.sort(all);
        assertEquals(Arrays.asList(SHOVEL, PICK, AXE, HOE), all, "always exactly one of each, none lost or doubled");
    }

    @Test
    void findListsTheHotbarFirstAndIgnoresOtherItems() {
        List<ToolSwap.Found> found = find(slots(9, 8, HOE, 1, "Rock"), slots(36, 0, AXE));
        assertEquals(2, found.size());
        assertEquals(new Loc(Where.HOTBAR, 8), found.get(0).loc());
        assertEquals(new Loc(Where.STORAGE, 0), found.get(1).loc());
        assertEquals(2, found.size(), "only registered tools are found, a lookalike id is not");
        assertFalse(find(slots(9, 0, "Arrakis_Tool_Cutter_Ray_Fake"), slots(36)).size() > 0);
    }

    @Test
    void theLocationsAreNamedForThePage() {
        assertEquals("Hotbar 3", ToolSwap.where(new Loc(Where.HOTBAR, 2)));
        assertEquals("Inventory", ToolSwap.where(new Loc(Where.STORAGE, 17)));
    }
}
