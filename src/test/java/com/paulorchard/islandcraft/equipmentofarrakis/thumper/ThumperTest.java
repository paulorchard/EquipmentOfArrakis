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
    void sandIsTheFirstRefusalThenTheLimitThenRoom() {
        assertEquals(ThumperRules.Refusal.NONE, ThumperRules.refusal(true, 0, 3, true));
        assertEquals(ThumperRules.Refusal.NOT_SAND, ThumperRules.refusal(false, 0, 3, true));
        assertEquals(ThumperRules.Refusal.NOT_SAND, ThumperRules.refusal(false, 9, 3, false), "the first reason wins");
        assertEquals(ThumperRules.Refusal.LIMIT, ThumperRules.refusal(true, 3, 3, true), "three standing, a fourth is refused");
        assertEquals(ThumperRules.Refusal.NONE, ThumperRules.refusal(true, 2, 3, true), "the third is allowed");
        assertEquals(ThumperRules.Refusal.NO_ROOM, ThumperRules.refusal(true, 0, 3, false));
        assertEquals(ThumperRules.Refusal.LIMIT, ThumperRules.refusal(true, 5, 3, false), "limit before room");
    }

    @Test
    void theBlockUnderTheFeetDecidesNotWhatIsFurtherDown() {
        Set<String> sand = Set.of("Arrakis_Sand");
        assertTrue(ThumperRules.onSand(new String[] {"Arrakis_Sand", "Rock_Stone"}, sand));
        assertFalse(ThumperRules.onSand(new String[] {"Rock_Stone", "Arrakis_Sand"}, sand), "rock on top of sand is rock");
        assertTrue(ThumperRules.onSand(new String[] {null, "Arrakis_Sand"}, sand), "air under the feet: the next block down counts, as in Worms");
        assertTrue(ThumperRules.onSand(new String[] {"", "Arrakis_Sand"}, sand));
        assertFalse(ThumperRules.onSand(new String[] {null, null}, sand), "in the air");
        assertFalse(ThumperRules.onSand(new String[] {"Soil_Dirt", null}, sand), "dirt");
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
        book.reserve(ME, 0);
        book.reserve(ME, 0);
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
        book.reserve(ME, 0);
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
