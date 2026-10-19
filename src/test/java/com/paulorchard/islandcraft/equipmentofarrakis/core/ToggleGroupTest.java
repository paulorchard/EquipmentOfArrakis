package com.paulorchard.islandcraft.equipmentofarrakis.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The exclusive toggle group: ON switches the others of the group OFF, OFF touches nothing, ungrouped toggles are independent. */
class ToggleGroupTest {

    private final Set<UUID> a = new HashSet<>();
    private final Set<UUID> b = new HashSet<>();
    private final Set<UUID> c = new HashSet<>();
    private final Set<UUID> solo = new HashSet<>();
    private final List<String> log = new ArrayList<>();
    private final ToggleRegistry registry = new ToggleRegistry();
    private final UUID who = UUID.randomUUID();
    private boolean failOffOfB;
    private boolean failOnOfC;

    ToggleGroupTest() {
        registry.register(new ToggleRegistry.Toggle("a", 1, "l", "i", null, a::contains, a::remove, "belt"));
        registry.register(new ToggleRegistry.Toggle("b", 2, "l", "i", null, b::contains, b::remove, "belt"));
        registry.register(new ToggleRegistry.Toggle("c", 3, "l", "i", null, c::contains, c::remove, "belt"));
        registry.register(new ToggleRegistry.Toggle("solo", 4, "l", "i", null, solo::contains, solo::remove));
    }

    private Set<UUID> setOf(String id) {
        return switch (id) {
            case "a" -> a;
            case "b" -> b;
            case "c" -> c;
            default -> solo;
        };
    }

    private ToggleRegistry.Outcome press(String id) {
        return registry.pressWith(id, who, 0, 0, (toggle, on) -> {
            if (toggle.id().equals("b") && !on && failOffOfB) {
                throw new IllegalStateException("b refuses to switch off");
            }
            if (toggle.id().equals("c") && on && failOnOfC) {
                throw new IllegalStateException("c refuses to switch on");
            }
            if (on) {
                setOf(toggle.id()).add(who);
            } else {
                setOf(toggle.id()).remove(who);
            }
            log.add(toggle.id() + "=" + on);
        });
    }

    @Test
    void switchingOneOnSwitchesTheOthersOffFirstThenItselfOn() {
        press("a");
        press("b");
        assertEquals(List.of("a=true", "a=false", "b=true"), log);
        assertFalse(a.contains(who));
        assertTrue(b.contains(who));
    }

    @Test
    void switchingOffTouchesNothingElse() {
        press("a");
        log.clear();
        assertEquals(ToggleRegistry.Outcome.OFF, press("a"));
        assertEquals(List.of("a=false"), log);
        press("b");
        log.clear();
        press("b");
        assertEquals(List.of("b=false"), log);
    }

    @Test
    void anUngroupedToggleIsNeverSwitchedOffByAGroupAndNeverSwitchesOneOff() {
        press("solo");
        press("a");
        press("b");
        assertTrue(solo.contains(who));
        press("solo");
        press("solo");
        assertTrue(b.contains(who), "pressing the independent toggle left the group alone");
    }

    @Test
    void threeMembersOnlyOneIsEverOn() {
        for (String id : new String[] {"a", "b", "c", "a", "c", "b", "b", "b", "a"}) {
            press(id);
            int on = (a.contains(who) ? 1 : 0) + (b.contains(who) ? 1 : 0) + (c.contains(who) ? 1 : 0);
            assertTrue(on <= 1, "after " + id + " " + on + " members were on");
        }
        assertTrue(a.contains(who), "the last press was an ON of a");
    }

    @Test
    void repeatedPressesOfTheSameMemberFlipItOnAndOff() {
        assertEquals(ToggleRegistry.Outcome.ON, press("a"));
        assertEquals(ToggleRegistry.Outcome.OFF, press("a"));
        assertEquals(ToggleRegistry.Outcome.ON, press("a"));
        assertEquals(List.of("a=true", "a=false", "a=true"), log, "no member was asked to switch off when it was already off");
    }

    @Test
    void otherPlayersAreNotAffected() {
        UUID other = UUID.randomUUID();
        a.add(other);
        press("b");
        assertTrue(a.contains(other));
    }

    @Test
    void aFailingOffHookOfAnotherMemberDoesNotStopTheRestOrTheNewOne() {
        press("b");
        press("a");
        assertTrue(a.contains(who));
        assertFalse(b.contains(who));
        // b is on again (set directly), its off hook now fails: c is switched on anyway and a, which is still on, is off.
        b.add(who);
        failOffOfB = true;
        a.add(who);
        press("c");
        assertTrue(c.contains(who));
        assertFalse(a.contains(who), "the member after the failing one was still switched off");
    }

    @Test
    void aFailingOnHookLeavesTheWholeGroupOffAndTheErrorGoesToTheCaller() {
        press("a");
        failOnOfC = true;
        assertThrows(IllegalStateException.class, () -> press("c"));
        assertFalse(a.contains(who));
        assertFalse(b.contains(who));
        assertFalse(c.contains(who));
        failOnOfC = false;
        assertEquals(ToggleRegistry.Outcome.ON, press("c"), "the registry is usable afterwards");
    }

    @Test
    void forgetClearsEveryMemberAndAFailingResetDoesNotStopTheOthers() {
        press("a");
        press("solo");
        registry.register(new ToggleRegistry.Toggle("boom", 0, "l", "i", null, p -> false, p -> {
            throw new IllegalStateException("reset fails");
        }, "belt"));
        registry.forget(who);
        assertFalse(a.contains(who));
        assertFalse(solo.contains(who));
    }
}
