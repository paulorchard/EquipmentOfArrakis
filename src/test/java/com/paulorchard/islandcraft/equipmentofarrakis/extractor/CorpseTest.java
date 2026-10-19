package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorpseTest {

    private static final Predicate<String> ALIVE = key -> false;
    private static final long A = 1L;
    private static final long B = 2L;

    @Test
    void onlyNonPlayerCharactersOutsideTheExclusionListAreExtended() {
        Set<String> excluded = Set.of("worm");
        assertTrue(CorpseRules.shouldExtend(false, true, "Cow", excluded));
        assertFalse(CorpseRules.shouldExtend(true, true, "Cow", excluded), "a player is never touched");
        assertFalse(CorpseRules.shouldExtend(false, false, null, excluded), "an entity that is not a non-player character");
        assertFalse(CorpseRules.shouldExtend(false, true, "Worm", excluded), "an excluded role, whatever its case");
        assertTrue(CorpseRules.shouldExtend(false, true, null, excluded), "no role name: not excluded");
        assertTrue(CorpseRules.shouldExtend(false, true, "Worm", Set.of()), "empty list by default");
    }

    @Test
    void theSystemAndTheExtractorShareOneCorpseRule() {
        for (boolean player : new boolean[] {false, true}) {
            for (boolean npc : new boolean[] {false, true}) {
                assertEquals(CorpseAim.isCorpse(player, true, npc, true), CorpseRules.shouldExtend(player, npc, null, Set.of()));
            }
        }
    }

    @Test
    void remainingTimeIsShownToTheNearestTen() {
        assertEquals(120, CorpseRules.nearestTen(118.2));
        assertEquals(10, CorpseRules.nearestTen(6.0));
        assertEquals(0, CorpseRules.nearestTen(4.9));
        assertEquals(0, CorpseRules.nearestTen(-3));
    }

    @Test
    void aBodyAlreadyKnownIsNotExtendedAgain() {
        CorpseLedger<String> ledger = new CorpseLedger<>();
        assertEquals(List.of(), ledger.add("cow", A, 100, 5, ALIVE));
        assertTrue(ledger.knows("cow"));
        assertEquals(List.of(), ledger.add("cow", A, 200, 5, ALIVE), "the replaced timer raises the event again: nothing happens");
        assertEquals(1, ledger.size(ALIVE));
    }

    @Test
    void aChunkKeepsItsNewestBodiesAndTheOldestAreCut() {
        CorpseLedger<String> ledger = new CorpseLedger<>();
        for (int i = 1; i <= 5; i++) {
            assertEquals(List.of(), ledger.add("a" + i, A, 100, 5, ALIVE));
        }
        assertEquals(List.of("a1"), ledger.add("a6", A, 100, 5, ALIVE), "the sixth cuts the oldest");
        assertEquals(List.of("a2", "a3"), List.of(ledger.add("a7", A, 100, 5, ALIVE).get(0), ledger.add("a8", A, 100, 5, ALIVE).get(0)));
        assertEquals(5, ledger.inChunk(A, ALIVE));
        assertEquals(3, ledger.cutByCap());
    }

    @Test
    void otherChunksAreNotAffected() {
        CorpseLedger<String> ledger = new CorpseLedger<>();
        for (int i = 1; i <= 5; i++) {
            ledger.add("a" + i, A, 100, 5, ALIVE);
        }
        assertEquals(List.of(), ledger.add("b1", B, 100, 5, ALIVE), "a different chunk has its own five");
        assertEquals(List.of("a1"), ledger.add("a6", A, 100, 5, ALIVE));
        assertEquals(1, ledger.inChunk(B, ALIVE));
        assertEquals(5, ledger.busiest(ALIVE));
        assertEquals(6, ledger.size(ALIVE));
    }

    @Test
    void anExtractedBodyLeavesTheCountAndFreesARoom() {
        CorpseLedger<String> ledger = new CorpseLedger<>();
        for (int i = 1; i <= 5; i++) {
            ledger.add("a" + i, A, 100, 5, ALIVE);
        }
        ledger.release("a3");
        assertEquals(4, ledger.inChunk(A, ALIVE));
        assertEquals(List.of(), ledger.add("a6", A, 100, 5, ALIVE), "room again: nothing is cut");
        assertTrue(ledger.knows("a3"), "a released body is still known, so it is never extended again");
        assertEquals(List.of("a1"), ledger.add("a7", A, 100, 5, ALIVE));
    }

    @Test
    void entitiesThatAreGoneAreDroppedFromTheCountAndFromTheMemory() {
        CorpseLedger<String> ledger = new CorpseLedger<>();
        ledger.add("a1", A, 100, 5, ALIVE);
        ledger.add("a2", A, 100, 5, ALIVE);
        ledger.release("a2");
        Predicate<String> a1Gone = "a1"::equals;
        assertEquals(0, ledger.size(a1Gone));
        assertTrue(ledger.knows("a2"));
        ledger.prune("a2"::equals);
        assertFalse(ledger.knows("a1"));
        assertFalse(ledger.knows("a2"), "no leak");
    }

    @Test
    void noLimitWhenTheCapIsZero() {
        CorpseLedger<String> ledger = new CorpseLedger<>();
        for (int i = 0; i < 40; i++) {
            assertEquals(List.of(), ledger.add("a" + i, A, 100, 0, ALIVE));
        }
        assertEquals(40, ledger.inChunk(A, ALIVE));
    }

    @Test
    void releaseAllEmptiesTheCountAndReturnsEveryBody() {
        CorpseLedger<String> ledger = new CorpseLedger<>();
        ledger.add("a1", A, 100, 5, ALIVE);
        ledger.add("b1", B, 100, 5, ALIVE);
        assertEquals(List.of("a1", "b1"), ledger.releaseAll());
        assertEquals(0, ledger.size(ALIVE));
        assertEquals(List.of(), ledger.releaseAll());
    }

    @Test
    void remainingSecondsCountDownFromTheEnd() {
        CorpseLedger<String> ledger = new CorpseLedger<>();
        ledger.add("a1", A, 120_000_000_000L, 5, ALIVE);
        assertEquals(List.of(60.0), ledger.remainingSeconds(60_000_000_000L, ALIVE));
        assertEquals(List.of(0.0), ledger.remainingSeconds(200_000_000_000L, ALIVE));
    }

    @Test
    void theDefaultsAreTheAgreedOnes() {
        ExtractorConfig config = new ExtractorConfig();
        assertEquals(120.0, config.corpseSeconds());
        assertEquals(1.0, config.extractedSeconds());
        assertEquals(5, config.maxCorpsesPerChunk());
        assertTrue(config.excludedRoles().isEmpty());
    }
}
