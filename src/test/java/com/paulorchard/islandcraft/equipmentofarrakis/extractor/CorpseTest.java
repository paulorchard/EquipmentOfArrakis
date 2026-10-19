package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorpseTest {

    private static final Predicate<String> ALIVE = key -> false;
    private static final long A = 1L;
    private static final long B = 2L;

    @Test
    void onlyHoldingNonPlayerCharactersOutsideTheExclusionListAreExtended() {
        Set<String> excluded = Set.of("worm");
        assertTrue(CorpseRules.shouldExtend(false, true, "Cow", excluded, true, false));
        assertFalse(CorpseRules.shouldExtend(true, true, "Cow", excluded, true, false), "a player is never touched");
        assertFalse(CorpseRules.shouldExtend(false, false, null, excluded, true, false), "an entity that is not a non-player character");
        assertFalse(CorpseRules.shouldExtend(false, true, "Worm", excluded, true, false), "an excluded role, whatever its case");
        assertTrue(CorpseRules.shouldExtend(false, true, null, excluded, true, false), "no role name: not excluded");
        assertTrue(CorpseRules.shouldExtend(false, true, "Worm", Set.of(), true, false), "empty list by default");
    }

    @Test
    void aClipThatDoesNotHoldKeepsTheVanillaTimerUnlessAllowed() {
        assertFalse(CorpseRules.shouldExtend(false, true, "Squirrel", Set.of(), false, false), "the body would stand up again");
        assertTrue(CorpseRules.shouldExtend(false, true, "Squirrel", Set.of(), false, true), "ExtendNonHolding");
        assertFalse(CorpseRules.shouldExtend(true, true, "Squirrel", Set.of(), false, true), "players stay untouched even then");
        assertFalse(CorpseRules.shouldExtend(false, true, "Worm", Set.of("worm"), false, true), "excluded roles stay excluded");
    }

    @Test
    void theSystemAndTheExtractorShareOneCorpseRule() {
        for (boolean player : new boolean[] {false, true}) {
            for (boolean npc : new boolean[] {false, true}) {
                assertEquals(CorpseAim.isCorpse(player, true, npc, true), CorpseRules.shouldExtend(player, npc, null, Set.of(), true, false));
            }
        }
    }

    @Test
    void theHoldFlagIsParsedFromAClip() {
        assertEquals(Boolean.TRUE, CorpseHold.parse("{ \"duration\": 40, \"holdLastKeyframe\": true, \"nodeAnimations\": {}}"));
        assertEquals(Boolean.FALSE, CorpseHold.parse("{\"duration\":20,\"holdLastKeyframe\":false,\"nodeAnimations\":{}}"));
        assertNull(CorpseHold.parse("{\"duration\": 20, \"nodeAnimations\": {}}"), "absent");
        assertNull(CorpseHold.parse("not json at all {{{"), "malformed");
        assertNull(CorpseHold.parse("{\"holdLastKeyframe\": maybe}"), "a value that is no boolean");
        assertNull(CorpseHold.parse(null));
    }

    @Test
    void severalClipsHoldOnlyWhenEveryOneDoes() {
        assertEquals(Boolean.TRUE, CorpseHold.allHold(List.of(true, true)));
        assertEquals(Boolean.FALSE, CorpseHold.allHold(List.of(true, false)));
        assertEquals(Boolean.FALSE, CorpseHold.allHold(List.of()), "no clip: nothing to hold");
        assertNull(CorpseHold.allHold(java.util.Arrays.asList(true, null)), "an unreadable clip makes it unknown");
        assertEquals(Boolean.FALSE, CorpseHold.allHold(java.util.Arrays.asList(null, false)), "one known failure decides");
        assertTrue(CorpseHold.fromList("Cow", Set.of("Squirrel")));
        assertFalse(CorpseHold.fromList("Squirrel", Set.of("Squirrel")));
    }

    @Test
    void theVerdictIsComputedAndLoggedOncePerModel() {
        CorpseHold.Cache cache = new CorpseHold.Cache();
        int[] computed = {0};
        for (int i = 0; i < 3; i++) {
            CorpseHold.Verdict verdict = cache.get("Cow/Death", () -> {
                computed[0]++;
                return new CorpseHold.Verdict("NPC/Livestock/Cow/Animations/Damage/Death.blockyanim", true);
            });
            assertTrue(verdict.holds());
        }
        assertEquals(1, computed[0]);
        assertTrue(cache.firstTime("Cow"));
        assertFalse(cache.firstTime("Cow"));
        assertTrue(cache.firstTime("Squirrel"));
    }

    @Test
    void theLogLineNamesTheModelTheClipAndTheDecision() {
        CorpseHold.Verdict verdict = new CorpseHold.Verdict("a/Death.blockyanim", true);
        assertEquals("corpse: Cow model Cow death clip a/Death.blockyanim holds=true -> extended 120 s", CorpseHold.logLine("Cow", "Cow", verdict, true, 120));
        assertEquals("corpse: Squirrel model Squirrel death clip b/Death.blockyanim holds=false -> vanilla timer",
                CorpseHold.logLine("Squirrel", "Squirrel", new CorpseHold.Verdict("b/Death.blockyanim", false), false, 120));
    }

    @Test
    void theRoleArgumentOfTheTestCommand() {
        assertEquals("Cow", CorpseRules.parseRole(new String[0], "Cow"));
        assertEquals("Cow", CorpseRules.parseRole(new String[] {" "}, "Cow"));
        assertEquals("Squirrel", CorpseRules.parseRole(new String[] {"Squirrel"}, "Cow"));
        assertEquals("Goblin_Duke", CorpseRules.parseRole(new String[] {" Goblin_Duke "}, "Cow"));
        assertNull(CorpseRules.parseRole(new String[] {"../x"}, "Cow"), "not a role name");
        assertEquals("Cow", CorpseRules.parseRole(null, "Cow"));
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
        assertFalse(config.extendNonHolding());
        assertEquals(1.0, config.testKillDelaySeconds());
        assertEquals("Interact", config.pokeAnimation());
    }
}
