package com.paulorchard.islandcraft.equipmentofarrakis.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules of a toggle press (the H key and the picker page): flip, unknown id, debounce. */
class TogglePressTest {

    private static final long MS = 1_000_000L;
    private static final long DEBOUNCE = 200 * MS;

    private final Set<UUID> on = new HashSet<>();
    private final List<String> log = new ArrayList<>();
    private final ToggleRegistry registry = new ToggleRegistry();

    TogglePressTest() {
        registry.register(new ToggleRegistry.Toggle("Holtzman", 10, "label", "icon", null, on::contains, on::remove));
    }

    private ToggleRegistry.Outcome press(String id, UUID who, long now, long gap) {
        return registry.pressWith(id, who, now, gap, (toggle, state) -> {
            if (state) {
                on.add(who);
            } else {
                on.remove(who);
            }
            log.add(id + "=" + state);
        });
    }

    @Test
    void aPressFlipsTheToggleAndReportsTheNewState() {
        UUID who = UUID.randomUUID();
        assertEquals(ToggleRegistry.Outcome.ON, press("Holtzman", who, 0, DEBOUNCE));
        assertTrue(on.contains(who));
        assertEquals(ToggleRegistry.Outcome.OFF, press("Holtzman", who, 300 * MS, DEBOUNCE));
        assertEquals(List.of("Holtzman=true", "Holtzman=false"), log);
    }

    @Test
    void anIdNobodyRegisteredDoesNothing() {
        UUID who = UUID.randomUUID();
        assertEquals(ToggleRegistry.Outcome.UNKNOWN, press("Nothing", who, 0, DEBOUNCE));
        assertEquals(ToggleRegistry.Outcome.UNKNOWN, new ToggleRegistry().pressWith("Holtzman", who, 0, DEBOUNCE, (t, s) -> log.add("called")),
                "the module that owns it is off: no toggle in the registry");
        assertTrue(log.isEmpty());
    }

    @Test
    void pressesCloserThanTheDebounceAreIgnoredAndTheWindowRestartsOnlyOnAcceptedPresses() {
        UUID who = UUID.randomUUID();
        assertEquals(ToggleRegistry.Outcome.ON, press("Holtzman", who, 0, DEBOUNCE));
        assertEquals(ToggleRegistry.Outcome.DEBOUNCED, press("Holtzman", who, 50 * MS, DEBOUNCE));
        assertEquals(ToggleRegistry.Outcome.DEBOUNCED, press("Holtzman", who, 199 * MS, DEBOUNCE));
        assertTrue(on.contains(who), "still ON: the ignored presses changed nothing");
        assertEquals(ToggleRegistry.Outcome.OFF, press("Holtzman", who, 200 * MS, DEBOUNCE), "exactly the gap is allowed");
        assertEquals(2, log.size());
    }

    @Test
    void thePickerPressesWithNoDebounceAndPlayersDoNotDebounceEachOther() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(ToggleRegistry.Outcome.ON, press("Holtzman", a, 0, 0));
        assertEquals(ToggleRegistry.Outcome.OFF, press("Holtzman", a, 1, 0), "gap 0: every click counts");
        assertEquals(ToggleRegistry.Outcome.ON, press("Holtzman", a, 2, DEBOUNCE));
        assertEquals(ToggleRegistry.Outcome.DEBOUNCED, press("Holtzman", a, 3, DEBOUNCE));
        assertEquals(ToggleRegistry.Outcome.ON, press("Holtzman", b, 3, DEBOUNCE), "another player is not held back");
    }

    @Test
    void forgettingAPlayerClearsTheirDebounceAndTheState() {
        UUID who = UUID.randomUUID();
        press("Holtzman", who, 0, DEBOUNCE);
        registry.forget(who);
        assertTrue(!on.contains(who));
        assertEquals(ToggleRegistry.Outcome.ON, press("Holtzman", who, 10 * MS, DEBOUNCE), "a new session is not held back by the old one");
    }
}
