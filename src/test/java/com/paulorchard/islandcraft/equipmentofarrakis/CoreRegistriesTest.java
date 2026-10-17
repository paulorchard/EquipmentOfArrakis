package com.paulorchard.islandcraft.equipmentofarrakis;

import com.paulorchard.islandcraft.equipmentofarrakis.core.ContainerInventory;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ContainerItem;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToggleRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToolRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToolUse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreRegistriesTest {

    @Test
    void toolsComeBackInDisplayOrderThenById() {
        ToolRegistry registry = new ToolRegistry();
        registry.register("Arrakis_Tool_B", 20, null);
        registry.register("Arrakis_Tool_A", 20, "server.custom.label");
        registry.register("Arrakis_Tool_Z", 10, null);
        assertEquals(List.of("Arrakis_Tool_Z", "Arrakis_Tool_A", "Arrakis_Tool_B"), registry.ids());
        assertTrue(registry.isTool("Arrakis_Tool_A"));
        assertFalse(registry.isTool("Arrakis_Blood_Bag"), "detection is by the list, not by a prefix");
        assertFalse(registry.isTool(null));
        assertEquals("server.custom.label", registry.nameKey("Arrakis_Tool_A"));
        assertEquals("server.items.Arrakis_Tool_B.name", registry.nameKey("Arrakis_Tool_B"));
    }

    @Test
    void registeringAgainReplaces() {
        ToolRegistry registry = new ToolRegistry();
        registry.register("Arrakis_Tool_A", 5, null);
        registry.register("Arrakis_Tool_A", 1, null);
        assertEquals(1, registry.all().size());
        assertEquals(1, registry.find("Arrakis_Tool_A").order());
    }

    @Test
    void togglesAreOrderedAndForgetReachesEveryOneEvenAfterAFailure() {
        ToggleRegistry registry = new ToggleRegistry();
        List<String> forgotten = new ArrayList<>();
        UUID who = UUID.randomUUID();
        registry.register(new ToggleRegistry.Toggle("light", 20, "l", "Furniture_Crude_Torch", null, u -> false,
                u -> { throw new IllegalStateException("boom"); }));
        registry.register(new ToggleRegistry.Toggle("shield", 10, "s", "Weapon_Shield_Mithril", null, u -> true,
                u -> forgotten.add("shield")));
        registry.register(new ToggleRegistry.Toggle("zeta", 30, "z", "x", null, u -> false, u -> forgotten.add("zeta")));
        assertEquals(List.of("shield", "light", "zeta"), registry.all().stream().map(ToggleRegistry.Toggle::id).toList());
        assertTrue(registry.find("shield").isOn(who));
        registry.forget(who);
        assertEquals(List.of("shield", "zeta"), forgotten);
    }

    @Test
    void toolUseReachesEveryListenerAndSurvivesOneThatThrows() {
        ToolUse.clear();
        AtomicInteger seen = new AtomicInteger();
        ToolUse.listen(p -> { throw new IllegalStateException("boom"); });
        ToolUse.listen(p -> seen.incrementAndGet());
        ToolUse.fired(null);
        ToolUse.fired(null);
        assertEquals(2, seen.get());
        ToolUse.clear();
    }

    @Test
    void containerArithmeticClampsAndAdds() {
        assertEquals(0, ContainerItem.clamp(-5, 500));
        assertEquals(500, ContainerItem.clamp(900, 500));
        assertEquals(120, ContainerItem.clamp(120, 500));
        assertEquals(0, ContainerItem.clamp(10, -3), "a bad capacity is 0");
        assertEquals(30, ContainerItem.added(20, 10, 500));
        assertEquals(500, ContainerItem.added(495, 10, 500), "adding past the top stops at the capacity");
        assertEquals(0, ContainerItem.added(5, -10, 500), "taking more than there is stops at 0");
        assertEquals(500, ContainerItem.added(Integer.MAX_VALUE, 10, 500), "no overflow");
        assertEquals(500, ContainerItem.room(0, 500));
        assertEquals(0, ContainerItem.room(500, 500));
        assertEquals(0, ContainerItem.room(700, 500));
    }

    @Test
    void anItemOfAnotherIdIsNotAContainer() {
        ContainerItem jon = new ContainerItem("Arrakis_Tool_Litrejon", "Arrakis_Water_Units", () -> 500, () -> false);
        assertFalse(jon.isContainer(null));
        assertEquals(0, jon.getAmount(null));
        assertEquals(0, jon.room(null));
        assertNull(null);
    }

    @Test
    void theFirstContainerWithRoomIsPickedHotbarFirst() {
        // place 0 = hotbar, place 1 = storage; negative = not the item, 0 = full
        assertEquals("[0, 3]", java.util.Arrays.toString(ContainerInventory.firstWithRoom(new int[][] {{-1, 0, -1, 7}, {500}})));
        assertEquals("[1, 0]", java.util.Arrays.toString(ContainerInventory.firstWithRoom(new int[][] {{-1, 0, -1}, {1, 500}})));
        assertNull(ContainerInventory.firstWithRoom(new int[][] {{0, 0}, {0}}), "all full");
        assertNull(ContainerInventory.firstWithRoom(new int[][] {{-1, -1}, {-1}}), "none carried");
        assertNull(ContainerInventory.firstWithRoom(new int[][] {}));
    }
}
