package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudNudgeTest {

    @Test
    void theStepIsTheValueMinusOneTimesTen() {
        assertEquals(1.0, HudNudge.step(1.1), 0.0, "1.1 moves +1");
        assertEquals(-1.0, HudNudge.step(0.9), 0.0, "0.9 moves -1");
        assertEquals(5.0, HudNudge.step(1.5), 0.0);
        assertEquals(-5.0, HudNudge.step(0.5), 0.0);
        assertEquals(0.0, HudNudge.step(1.0), 0.0);
        assertEquals(0.5, HudNudge.step(1.05), 0.0);
        assertEquals(10.0, HudNudge.step(2), 0.0);
    }

    @Test
    void commasAndSpacesBothSeparateTheNumbers() {
        for (String[] args : new String[][] {{"1.1,1"}, {"1.1,", "1"}, {"1.1", "1"}, {"1.1 ,", "1"}, {"1.1", ",1"}, {"1.1,", "1,"}}) {
            HudNudge.Parsed p = HudNudge.parse(args);
            assertEquals(HudNudge.Kind.MOVE, p.kind(), String.join("|", args));
            assertEquals(1.0, p.dx(), 0.0);
            assertEquals(0.0, p.dy(), 0.0);
            assertFalse(p.ignoredExtra());
        }
    }

    @Test
    void aThirdNumberIsIgnoredAndSaid() {
        HudNudge.Parsed p = HudNudge.parse(new String[] {"1.1,", "1.5,", "9"});
        assertEquals(HudNudge.Kind.MOVE, p.kind());
        assertEquals(1.0, p.dx(), 0.0);
        assertEquals(5.0, p.dy(), 0.0);
        assertTrue(p.ignoredExtra());
        assertTrue(HudNudge.parse(new String[] {"1,1,1"}).ignoredExtra());
    }

    @Test
    void noNumbersShowsAndTheWordResetResets() {
        assertEquals(HudNudge.Kind.SHOW, HudNudge.parse(new String[0]).kind());
        assertEquals(HudNudge.Kind.SHOW, HudNudge.parse(new String[] {"", ""}).kind());
        assertEquals(HudNudge.Kind.RESET, HudNudge.parse(new String[] {"reset"}).kind());
        assertEquals(HudNudge.Kind.RESET, HudNudge.parse(new String[] {"RESET"}).kind());
    }

    @Test
    void badInputIsRefusedWithAMessage() {
        for (String[] args : new String[][] {{"1.1"}, {"abc", "1"}, {"1", "x"}, {"1.1,"}, {"NaN", "1"}, {"Infinity", "1"}, {"reset", "now"}}) {
            HudNudge.Parsed p = HudNudge.parse(args);
            assertEquals(HudNudge.Kind.BAD, p.kind(), String.join("|", args));
            assertTrue(p.message() != null && !p.message().isEmpty());
        }
    }

    @Test
    void nudgesAccumulateAndAResetClearsOneOrAllOfThem() {
        HudConfig.Nudges nudges = new HudConfig.Nudges();
        nudges.add(HudBar.POWER, HudNudge.step(1.1), 0);
        nudges.add(HudBar.POWER, HudNudge.step(1.1), HudNudge.step(1.5));
        assertEquals(2.0, nudges.of(HudBar.POWER).getX(), 0.0);
        assertEquals(5.0, nudges.of(HudBar.POWER).getY(), 0.0);
        nudges.add(HudBar.POWER, HudNudge.step(0.9), 0);
        assertEquals(1.0, nudges.of(HudBar.POWER).getX(), 0.0);
        nudges.add(HudBar.WATER, 3, -4);
        assertEquals(0.0, nudges.of(HudBar.HEALTH).getX(), 0.0, "bars are independent");
        nudges.reset(HudBar.POWER);
        assertEquals(0.0, nudges.of(HudBar.POWER).getX(), 0.0);
        assertEquals(3.0, nudges.of(HudBar.WATER).getX(), 0.0, "one reset leaves the others");
        nudges.add(HudBar.EXPOSURE, 1, 1);
        nudges.resetAll();
        for (HudBar bar : HudBar.values()) {
            assertEquals(0.0, nudges.of(bar).getX(), 0.0);
            assertEquals(0.0, nudges.of(bar).getY(), 0.0);
        }
    }

    @Test
    void accumulatedFractionsDoNotDriftInTheSavedValue() {
        HudConfig.Nudges nudges = new HudConfig.Nudges();
        for (int i = 0; i < 30; i++) {
            nudges.add(HudBar.HEALTH, HudNudge.step(1.01), 0);
        }
        assertEquals(3.0, nudges.of(HudBar.HEALTH).getX(), 0.0);
    }

    @Test
    void nudgesAreSavedInTheHudSectionByPositionAndSurviveARoundTrip() {
        HudConfig config = new HudConfig();
        config.nudges().add(HudBar.HEALTH, 2, -3);
        config.nudges().add(HudBar.EXPOSURE, -1.5, 4);
        BsonValue written = HudConfig.CODEC.encode(config);
        BsonDocument nudges = written.asDocument().getDocument("Nudges");
        assertEquals(2.0, nudges.getDocument("LeftBottom").getDouble("X").getValue(), 0.0);
        assertEquals(-3.0, nudges.getDocument("LeftBottom").getDouble("Y").getValue(), 0.0);
        assertEquals(-1.5, nudges.getDocument("RightTop").getDouble("X").getValue(), 0.0);
        assertTrue(nudges.containsKey("LeftTop") && nudges.containsKey("RightBottom"));
        HudConfig read = HudConfig.CODEC.decode(written);
        assertEquals(2.0, read.nudges().of(HudBar.HEALTH).getX(), 0.0);
        assertEquals(-3.0, read.nudges().of(HudBar.HEALTH).getY(), 0.0);
        assertEquals(4.0, read.nudges().of(HudBar.EXPOSURE).getY(), 0.0);
        assertEquals(0.0, read.nudges().of(HudBar.POWER).getX(), 0.0);
        assertEquals(written, HudConfig.CODEC.encode(read), "writing, reading and writing again changes nothing");
    }

    @Test
    void anEmptyHudSectionHasNoNudgesAndTheNewDefaults() {
        HudConfig read = HudConfig.CODEC.decode(new BsonDocument());
        for (HudBar bar : HudBar.values()) {
            assertEquals(0.0, read.nudges().of(bar).getX(), 0.0);
            assertEquals(0.0, read.nudges().of(bar).getY(), 0.0);
        }
        assertEquals(0.0, read.getBarsLift(), 0.0);
        assertEquals(26.0, read.getRowGap(), 0.0);
        assertEquals("copy", read.getArtSource(), "the Reference documents are not in the pack");
        assertTrue(read.copyArt());
    }

    @Test
    void theCopyRouteIsTheDefaultAndReferenceNeedsItsWord() {
        HudConfig config = new HudConfig();
        assertTrue(config.copyArt());
        config.setArtSource("reference");
        assertFalse(config.copyArt());
        config.setArtSource("REFERENCE");
        assertFalse(config.copyArt());
        config.setArtSource("copy");
        assertTrue(config.copyArt());
        assertTrue(HudConfig.CODEC.decode(BsonDocument.parse("{\"ArtSource\": \"something\"}")).copyArt(), "anything but reference is the copy route");
    }
}
