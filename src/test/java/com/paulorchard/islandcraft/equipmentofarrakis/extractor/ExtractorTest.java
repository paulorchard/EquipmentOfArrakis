package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.paulorchard.islandcraft.equipmentofarrakis.extractor.ExtractorRules.Refusal.ALREADY_USED;
import static com.paulorchard.islandcraft.equipmentofarrakis.extractor.ExtractorRules.Refusal.BAGS_FULL;
import static com.paulorchard.islandcraft.equipmentofarrakis.extractor.ExtractorRules.Refusal.NONE;
import static com.paulorchard.islandcraft.equipmentofarrakis.extractor.ExtractorRules.Refusal.NOT_CROUCHING;
import static com.paulorchard.islandcraft.equipmentofarrakis.extractor.ExtractorRules.Refusal.NO_BAG;
import static com.paulorchard.islandcraft.equipmentofarrakis.extractor.ExtractorRules.Refusal.NO_CORPSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractorTest {

    private static final Vector3d EYE = new Vector3d(0, 1.6, 0);
    private static final Vector3d AHEAD = new Vector3d(0, 0, 1);

    @Test
    void theNearestCorpseOnTheRayIsPicked() {
        List<CorpseAim.Candidate> all = List.of(
                new CorpseAim.Candidate(1, new Vector3d(0, 1.0, 2.0)),
                new CorpseAim.Candidate(2, new Vector3d(0, 1.2, 1.0)),
                new CorpseAim.Candidate(3, new Vector3d(4, 1.0, 1.0)));
        assertEquals(2, CorpseAim.pick(EYE, AHEAD, all, 2.5, 0.9), "the nearer one in line wins; the one to the side is not hit");
    }

    @Test
    void outOfRangeOffTheRayOrBehindIsNotPicked() {
        assertEquals(-1, CorpseAim.pick(EYE, AHEAD, List.of(new CorpseAim.Candidate(1, new Vector3d(0, 1.2, 3.0))), 2.5, 0.9), "too far");
        assertEquals(-1, CorpseAim.pick(EYE, AHEAD, List.of(new CorpseAim.Candidate(1, new Vector3d(2, 1.2, 1.0))), 2.5, 0.9), "off the ray");
        assertEquals(-1, CorpseAim.pick(EYE, AHEAD, List.of(new CorpseAim.Candidate(1, new Vector3d(0, 1.2, -1.0))), 2.5, 0.9), "behind");
        assertEquals(-1, CorpseAim.pick(EYE, new Vector3d(0, 0, 0), List.of(new CorpseAim.Candidate(1, new Vector3d(0, 1.2, 1.0))), 2.5, 0.9), "no direction");
        assertEquals(-1, CorpseAim.pick(EYE, AHEAD, List.of(), 2.5, 0.9));
    }

    @Test
    void theDirectionNeedNotBeNormalised() {
        assertEquals(7, CorpseAim.pick(EYE, new Vector3d(0, 0, 10), List.of(new CorpseAim.Candidate(7, new Vector3d(0, 1.6, 2.0))), 2.5, 0.5));
    }

    @Test
    void whatCountsAsACorpse() {
        assertTrue(CorpseAim.isCorpse(false, true, true, true));
        assertFalse(CorpseAim.isCorpse(true, true, false, false), "a dead player is never a corpse for the tool");
        assertFalse(CorpseAim.isCorpse(false, false, true, true), "a living mob");
        assertFalse(CorpseAim.isCorpse(false, true, false, true), "dead but not an NPC while NPCs are required");
        assertTrue(CorpseAim.isCorpse(false, true, false, false), "the rule can be widened by data");
    }

    @Test
    void theRefusalsComeInOrder() {
        assertEquals(NO_CORPSE, ExtractorRules.refusal(false, false, false, false, 0, false));
        assertEquals(ALREADY_USED, ExtractorRules.refusal(true, true, false, false, 1, true));
        assertEquals(NOT_CROUCHING, ExtractorRules.refusal(true, false, true, false, 1, true));
        assertEquals(NONE, ExtractorRules.refusal(true, false, true, true, 1, true));
        assertEquals(NO_BAG, ExtractorRules.refusal(true, false, false, false, 0, false));
        assertEquals(BAGS_FULL, ExtractorRules.refusal(true, false, false, false, 2, false));
        assertEquals(NONE, ExtractorRules.refusal(true, false, false, false, 2, true), "forcing the crouch needs no crouch to start");
    }
}
