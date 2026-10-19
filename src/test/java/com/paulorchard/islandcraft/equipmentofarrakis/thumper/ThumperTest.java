package com.paulorchard.islandcraft.equipmentofarrakis.thumper;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThumperTest {

    private static final UUID ME = new UUID(1, 1);
    private static final UUID YOU = new UUID(2, 2);
    private static final long SECOND = 1_000_000_000L;

    // ---- the gate rules

    @Test
    void theRefusalsComeInTheDocumentedOrder() {
        // crouched, live, max, room, onSand, occupied
        assertEquals(ThumperRules.Refusal.NONE, ThumperRules.refusal(true, 0, 3, true, true, false));
        assertEquals(ThumperRules.Refusal.NOT_CROUCHING, ThumperRules.refusal(false, 0, 3, true, true, false));
        assertEquals(ThumperRules.Refusal.NOT_CROUCHING, ThumperRules.refusal(false, 9, 3, false, false, true), "crouch is the first reason");
        assertEquals(ThumperRules.Refusal.LIMIT, ThumperRules.refusal(true, 3, 3, false, false, true), "three standing, a fourth is refused");
        assertEquals(ThumperRules.Refusal.NONE, ThumperRules.refusal(true, 2, 3, true, true, false), "the third is allowed");
        assertEquals(ThumperRules.Refusal.NO_ROOM, ThumperRules.refusal(true, 0, 3, false, false, true), "no room before sand");
        assertEquals(ThumperRules.Refusal.NOT_SAND, ThumperRules.refusal(true, 0, 3, true, false, true), "sand before the spot being taken");
        assertEquals(ThumperRules.Refusal.OCCUPIED, ThumperRules.refusal(true, 0, 3, true, true, true));
    }

    @Test
    void crouchCountsOnlyWhenItIsThePlayersOwn() {
        assertTrue(com.paulorchard.islandcraft.equipmentofarrakis.core.Crouch.counts(true, false));
        assertFalse(com.paulorchard.islandcraft.equipmentofarrakis.core.Crouch.counts(false, false));
        assertFalse(com.paulorchard.islandcraft.equipmentofarrakis.core.Crouch.counts(true, true), "a forced crouch is not the player's choice");
        assertFalse(com.paulorchard.islandcraft.equipmentofarrakis.core.Crouch.counts(false, true));
    }

    // ---- the neighbour block in the look direction

    private static String cell(double dirX, double dirZ) {
        int[] c = ThumperSpot.neighbour(dirX, dirZ);
        return c == null ? "none" : c[0] + "," + c[1];
    }

    private static String atDegrees(double degrees) {
        double r = Math.toRadians(degrees);
        return cell(Math.cos(r), Math.sin(r));
    }

    @Test
    void theEightDirectionsAreTheEightSectorsOfFortyFiveDegrees() {
        assertEquals("1,0", atDegrees(0));
        assertEquals("1,1", atDegrees(45));
        assertEquals("0,1", atDegrees(90));
        assertEquals("-1,1", atDegrees(135));
        assertEquals("-1,0", atDegrees(180));
        assertEquals("-1,-1", atDegrees(225));
        assertEquals("0,-1", atDegrees(270));
        assertEquals("1,-1", atDegrees(315));
    }

    @Test
    void theSectorEdgesAndTheWrapAroundAt360() {
        assertEquals("1,0", atDegrees(22.4), "just inside east");
        assertEquals("1,1", atDegrees(22.6), "just inside north-east");
        assertEquals("1,0", atDegrees(-22.4));
        assertEquals("1,-1", atDegrees(-22.6));
        assertEquals("1,0", atDegrees(360), "360 is 0");
        assertEquals("1,0", atDegrees(359.9));
        assertEquals("1,-1", atDegrees(337.4), "just past the edge between east and south-east");
        assertEquals("1,0", atDegrees(337.6));
        assertEquals("-1,0", atDegrees(-180));
        assertEquals("0,1", atDegrees(450), "a quarter turn past 360");
    }

    @Test
    void theCellIsNeverTheOwnBlockAndPitchDoesNotMatter() {
        for (int degrees = 0; degrees < 360; degrees += 5) {
            double r = Math.toRadians(degrees);
            for (double length : new double[] {1.0, 0.5, 0.01}) {
                int[] c = ThumperSpot.neighbour(Math.cos(r) * length, Math.sin(r) * length);
                assertTrue(c != null && (c[0] != 0 || c[1] != 0) && Math.abs(c[0]) <= 1 && Math.abs(c[1]) <= 1, degrees + " degrees");
            }
        }
        assertEquals(atDegrees(60), cell(Math.cos(Math.toRadians(60)) * 0.02, Math.sin(Math.toRadians(60)) * 0.02), "looking almost straight down: the same direction");
        assertEquals("none", cell(0, 0), "no horizontal direction at all");
    }

    // ---- the ground in the column and the room above it

    private static java.util.function.IntFunction<String> column(java.util.Map<Integer, String> blocks) {
        return y -> blocks.get(y);
    }

    @Test
    void theGroundIsTheFirstBlockFromOneAboveTheFeetDownToTwoBelow() {
        int feet = 100;
        assertEquals(100, ThumperSpot.ground(feet, column(java.util.Map.of(100, "Arrakis_Sand", 99, "Arrakis_Sand"))), "level ground: the block at feet level");
        assertEquals(99, ThumperSpot.ground(feet, column(java.util.Map.of(99, "Arrakis_Sand"))), "one lower");
        assertEquals(98, ThumperSpot.ground(feet, column(java.util.Map.of(98, "Arrakis_Sand"))), "two lower is the limit");
        assertEquals(Integer.MIN_VALUE, ThumperSpot.ground(feet, column(java.util.Map.of(97, "Arrakis_Sand"))), "three lower: a drop, no ground");
        assertEquals(101, ThumperSpot.ground(feet, column(java.util.Map.of(101, "Arrakis_Sand", 100, "Arrakis_Sand"))), "one higher: a step up, taken from the top");
        assertEquals(101, ThumperSpot.ground(feet, column(java.util.Map.of(101, "Rock_Stone", 99, "Arrakis_Sand"))), "the first block found from above is the ground");
        assertEquals(Integer.MIN_VALUE, ThumperSpot.ground(feet, y -> null), "air all the way");
        assertEquals(100, ThumperSpot.ground(feet, column(java.util.Map.of(100, "Plant_Grass_Tall"))), "a plant is the first block, as in Worms");
    }

    @Test
    void twoFreeBlocksAboveTheGroundAreNeeded() {
        assertTrue(ThumperSpot.headroom(100, y -> false));
        assertFalse(ThumperSpot.headroom(100, y -> y == 101), "a wall right above the ground");
        assertFalse(ThumperSpot.headroom(100, y -> y == 102), "a dune face two up");
        assertTrue(ThumperSpot.headroom(100, y -> y == 103), "the third block does not matter");
    }

    // ---- the Worms sand rule mirrored

    private static final Set<String> ARRAKIS = Set.of("Arrakis_Sand");

    @Test
    void theWormsRuleLooksFromBelowTheFeetDownByTheToleranceToTheFirstBlock() {
        // a Thumper standing at y 100: the ground block is at 99 (its top face at 100)
        assertTrue(ThumperSpot.wormsOnSand(100.0, 1.0, column(java.util.Map.of(99, "Arrakis_Sand")), ARRAKIS), "sand right under");
        assertFalse(ThumperSpot.wormsOnSand(100.0, 1.0, column(java.util.Map.of(99, "Rock_Stone", 98, "Arrakis_Sand")), ARRAKIS), "rock over sand is rock");
        assertTrue(ThumperSpot.wormsOnSand(100.0, 1.0, column(java.util.Map.of(98, "Arrakis_Sand")), ARRAKIS), "sand under a gap of air, within tolerance 1.0");
        assertFalse(ThumperSpot.wormsOnSand(100.0, 1.0, column(java.util.Map.of(97, "Arrakis_Sand")), ARRAKIS), "sand two under a gap: beyond tolerance 1.0");
        assertTrue(ThumperSpot.wormsOnSand(100.0, 2.0, column(java.util.Map.of(97, "Arrakis_Sand")), ARRAKIS), "tolerance 2.0 reaches it");
        assertFalse(ThumperSpot.wormsOnSand(100.0, 1.0, y -> null, ARRAKIS), "in the air");
        assertFalse(ThumperSpot.wormsOnSand(100.0, 1.0, column(java.util.Map.of(99, "Plant_Grass_Tall", 98, "Arrakis_Sand")), ARRAKIS), "a plant on the sand is the first block");
    }

    @Test
    void theTenthOfABlockOffsetDecidesWhichBlockIsTheTop() {
        // y - 0.1 is floored: a Thumper at 100.0 starts at block 99, one at 100.05 also at 99, one at 100.2 at block 100
        assertTrue(ThumperSpot.wormsOnSand(100.05, 1.0, column(java.util.Map.of(99, "Arrakis_Sand")), ARRAKIS));
        assertFalse(ThumperSpot.wormsOnSand(100.2, 1.0, column(java.util.Map.of(99, "Arrakis_Sand", 100, "Rock_Stone")), ARRAKIS), "a hair higher and rock is the first block");
        assertTrue(ThumperSpot.wormsOnSand(99.95, 1.0, column(java.util.Map.of(98, "Arrakis_Sand")), ARRAKIS), "a hair lower: the block below");
        // the gate stands the Thumper at ground + 1 exactly, so the ground block itself is always the one the rule looks at
        for (int ground = 60; ground < 70; ground++) {
            assertTrue(ThumperSpot.wormsOnSand(ground + 1, 1.0, column(java.util.Map.of(ground, "Arrakis_Sand")), ARRAKIS), "ground " + ground);
            assertFalse(ThumperSpot.wormsOnSand(ground + 1, 1.0, column(java.util.Map.of(ground, "Rock_Stone", ground - 1, "Arrakis_Sand")), ARRAKIS));
        }
    }

    @Test
    void theGateAndWormsAgreeForEveryGroundTheGateAccepts() {
        // Every column the gate can pick (ground from feet+1 down to feet-2) gives the same answer under Worms' rule at ground + 1.
        for (int feet = 90; feet < 93; feet++) {
            for (int groundOffset = -2; groundOffset <= 1; groundOffset++) {
                for (String block : new String[] {"Arrakis_Sand", "Rock_Stone"}) {
                    int ground = feet + groundOffset;
                    java.util.Map<Integer, String> blocks = java.util.Map.of(ground, block, ground - 1, "Arrakis_Sand");
                    int found = ThumperSpot.ground(feet, column(blocks));
                    assertEquals(ground, found);
                    assertEquals(block.equals("Arrakis_Sand"), ThumperSpot.wormsOnSand(found + 1, 1.0, column(blocks), ARRAKIS));
                }
            }
        }
    }

    // ---- one Thumper per spot

    @Test
    void aSpotWithAThumperWithinEightTenthsOfABlockIsTaken() {
        ThumperBook book = new ThumperBook(1.0, 0.25);
        UUID a = UUID.randomUUID();
        book.tick(a, ME, 10.5, 64, 20.5, 0.05, 0);
        assertTrue(book.occupied(10.5, 64, 20.5, 0.8, 0), "the same spot");
        assertTrue(book.occupied(11.2, 64, 20.5, 0.8, 0), "0.7 away");
        assertFalse(book.occupied(11.5, 64, 20.5, 0.8, 0), "a block away: the next spot");
        assertFalse(book.occupied(10.5, 66, 20.5, 0.8, 0), "two up");
        assertTrue(book.occupied(10.5, 64, 20.5, 0.8, SECOND), "still seen a second later");
        assertFalse(book.occupied(10.5, 64, 20.5, 0.8, 4 * SECOND), "not seen for a long time: gone");
    }

    @Test
    void aPlacementNotYetSeenAlreadyTakesTheSpotAndItsDriftIsNoted() {
        ThumperBook book = new ThumperBook(1.0, 0.25);
        book.reserve(ME, 0, 5.5, 70, 5.5);
        assertTrue(book.occupied(5.5, 70, 5.5, 0.8, 0), "a quick second click on the same spot");
        assertFalse(book.occupied(6.5, 70, 5.5, 0.8, 0));
        UUID entity = UUID.randomUUID();
        book.tick(entity, ME, 5.5, 71, 5.5, 0.05, SECOND / 10);
        Double drift = book.popDrift(entity);
        assertEquals(1.0, drift, 1e-9, "the entity stands one block above the intended spot: visible");
        assertEquals(null, book.popDrift(entity), "read once");
        assertTrue(book.occupied(5.5, 71, 5.5, 0.8, SECOND / 10), "now by the entity");
    }

    // ---- the sand set

    @Test
    void theSandSetFollowsWormsOfArrakis() {
        Set<String> dunes = ThumperRules.resolveSand(new String[0], null, true);
        assertEquals(Set.of("Arrakis_Sand"), dunes, "Dunes of Arrakis installed: only its sand, as Worms does");
        assertFalse(dunes.contains("Soil_Sand"));
        assertEquals(Set.of("Soil_Sand", "Soil_Sand_Ashen", "Soil_Sand_Red", "Soil_Sand_White"), ThumperRules.resolveSand(new String[0], null, false),
                "no Dunes: the four vanilla sands");
        assertEquals(Set.of("Soil_Sand_Red"), ThumperRules.resolveSand(new String[0], Set.of("Soil_Sand_Red"), true), "what Worms reports wins over the default");
        assertEquals(Set.of("Soil_Sand", "Soil_Sand_Ashen", "Soil_Sand_Red", "Soil_Sand_White"), ThumperRules.resolveSand(new String[0], Set.of(), false),
                "an empty report from Worms is no report: the default applies");
        assertEquals(Set.of("My_Sand"), ThumperRules.resolveSand(new String[] {"My_Sand"}, Set.of("Soil_Sand_Red"), true), "the config list wins over everything");
    }

    // ---- the beat

    private static List<Double> hitTimes(BeatClock clock, double step, double seconds) {
        List<Double> times = new ArrayList<>();
        double t = 0;
        for (int i = 0; i < (int) Math.round(seconds / step); i++) {
            t += step;
            int hits = clock.advance(step);
            for (int h = 0; h < hits; h++) {
                times.add(t);
            }
        }
        return times;
    }

    @Test
    void aDoubleThumpIsTwoHitsAQuarterSecondApartThenARest() {
        assertEquals(List.of(0.125, 0.25, 1.0, 1.25, 2.0, 2.25), hitTimes(new BeatClock(1.0, 0.25), 0.125, 2.5));
    }

    @Test
    void theFirstBeatIsAtThePlacement() {
        BeatClock clock = new BeatClock(1.0, 0.25);
        assertEquals(1, clock.advance(0.05), "the very first tick thumps");
        assertEquals(0, clock.advance(0.05));
    }

    @Test
    void aGapOfZeroIsASingleHitPerBeat() {
        assertEquals(List.of(0.125, 1.0, 2.0), hitTimes(new BeatClock(1.0, 0), 0.125, 2.5));
        assertEquals(List.of(0.125, 1.0, 2.0), hitTimes(new BeatClock(1.0, -1), 0.125, 2.5), "a negative gap too");
    }

    @Test
    void aGapThatDoesNotFitInTheBeatIsASingleHit() {
        assertEquals(List.of(0.125, 1.0), hitTimes(new BeatClock(1.0, 1.0), 0.125, 1.5));
    }

    @Test
    void aLongPauseGivesAtMostOneBeatNotABurst() {
        BeatClock clock = new BeatClock(1.0, 0.25);
        clock.advance(0.1);
        assertTrue(clock.advance(30.0) <= 2, "a lag spike or a chunk that was not ticking");
    }

    @Test
    void aBeatLongerThanASecondWorks() {
        assertEquals(List.of(0.125, 0.25, 2.0, 2.25), hitTimes(new BeatClock(2.0, 0.25), 0.125, 3.0));
    }

    // ---- the book

    @Test
    void aThumperThatStopsTickingIsDroppedAndPlacementsAreCounted() {
        ThumperBook book = new ThumperBook(1.0, 0.25);
        UUID a = UUID.randomUUID();
        assertEquals(1, book.tick(a, ME, 1, 2, 3, 0.05, 0), "first beat at once");
        assertEquals(1, book.live(ME, 0));
        assertEquals(0, book.live(YOU, 0), "somebody else's");
        book.tick(a, ME, 1, 2, 3, 0.05, SECOND);
        assertEquals(1, book.prune(SECOND + 3 * SECOND), "not seen for more than two seconds");
        assertEquals(0, book.size());
        assertEquals(0, book.live(ME, 4 * SECOND));
    }

    @Test
    void anAcceptedPlacementCountsUntilItsEntityIsSeen() {
        ThumperBook book = new ThumperBook(1.0, 0.25);
        book.reserve(ME, 0, 0, 0, 0);
        book.reserve(ME, 0, 0, 0, 0);
        assertEquals(2, book.live(ME, 0), "two clicks before the entities exist");
        book.tick(UUID.randomUUID(), ME, 0, 0, 0, 0.05, SECOND / 10);
        assertEquals(2, book.live(ME, SECOND / 10), "one placement became an entity, one is still waiting");
        book.tick(UUID.randomUUID(), ME, 0, 0, 0, 0.05, SECOND / 10);
        assertEquals(2, book.live(ME, SECOND / 10));
        assertEquals(0, book.prune(SECOND / 10));
        assertEquals(2, book.live(ME, 2 * SECOND), "no stale reservations left");
    }

    @Test
    void aReservationThatNeverBecameAnEntityRunsOut() {
        ThumperBook book = new ThumperBook(1.0, 0.25);
        book.reserve(ME, 0, 0, 0, 0);
        assertEquals(1, book.live(ME, 2 * SECOND));
        assertEquals(0, book.live(ME, 4 * SECOND), "the engine refused the spawn: it does not count for ever");
        book.prune(4 * SECOND);
        assertEquals(0, book.live(ME, 4 * SECOND));
    }

    @Test
    void eachThumperHasItsOwnClock() {
        ThumperBook book = new ThumperBook(1.0, 0.25);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(1, book.tick(a, ME, 0, 0, 0, 0.125, 0));
        assertEquals(1, book.tick(a, ME, 0, 0, 0, 0.125, 0), "a: the second hit at 0.25");
        assertEquals(1, book.tick(b, ME, 0, 0, 0, 0.125, 0), "b starts its own beat when it is placed");
        assertEquals(2, book.live(ME, 0));
        book.clear();
        assertEquals(0, book.live(ME, 0));
    }

    @Test
    void theSoundUnitsConvertTheWayTheEngineDoes() {
        assertEquals(1.4125, ThumperRules.dbToGain(3.0), 0.0001, "+3 dB is the 1.4125 the first boot showed");
        assertEquals(0.7071, ThumperRules.semitonesToRatio(-6.0), 0.0001, "-6 semitones is the 0.7071 the first boot showed (the audition rate of 0.7)");
        assertEquals(1.0, ThumperRules.dbToGain(0), 1e-9);
        assertEquals(1.0, ThumperRules.semitonesToRatio(0), 1e-9);
        assertEquals(3.0, ThumperRules.gainToDb(ThumperRules.dbToGain(3.0)), 1e-4);
        assertEquals(-6.0, ThumperRules.ratioToSemitones(ThumperRules.semitonesToRatio(-6.0)), 1e-4);
    }

    // ---- the settings

    @Test
    void theDefaultsAreTheAgreedOnes() {
        ThumperConfig config = new ThumperConfig();
        assertEquals(3, config.maxLivePerPlayer());
        assertEquals(0, config.sandBlocks().length);
        assertEquals(1.0, config.beatSeconds());
        assertEquals(0.25, config.doubleThumpGapSeconds());
        assertEquals(-6.0, config.thumpPitchSemitones());
        assertEquals(3.0, config.thumpVolumeDb());
        assertEquals(0.0, config.maxLiveSeconds());
    }

    @Test
    void theGiveCommandCountDefaultsToFourAndIsClamped() {
        assertEquals(4, ThumperCommands.parseCount(new String[0]));
        assertEquals(4, ThumperCommands.parseCount(new String[] {" "}));
        assertEquals(4, ThumperCommands.parseCount(new String[] {"many"}));
        assertEquals(9, ThumperCommands.parseCount(new String[] {"9"}));
        assertEquals(1, ThumperCommands.parseCount(new String[] {"0"}));
        assertEquals(256, ThumperCommands.parseCount(new String[] {"100000"}));
        assertEquals(4, ThumperCommands.parseCount(null));
    }
}
