package com.paulorchard.islandcraft.equipmentofarrakis;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The trimmed Portal_Zap_02 clip (tools/assets/make_fizzle.js) and the sound events of the Cutter Ray, read from the jar's resources. */
class FizzleClipTest {

    private static byte[] resource(String path) throws IOException {
        try (InputStream in = FizzleClipTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing resource " + path);
            return in.readAllBytes();
        }
    }

    private static int crc(byte[] b, int from, int len) {
        int c = 0;
        for (int i = from; i < from + len; i++) {
            c ^= (b[i] & 0xff) << 24;
            for (int k = 0; k < 8; k++) {
                c = (c & 0x80000000) != 0 ? (c << 1) ^ 0x04c11db7 : c << 1;
            }
        }
        return c;
    }

    private static final String SOUNDS = "/Common/Sounds/Equipment_of_Arrakis/Arrakis_Cutter_Ray_";

    /** Parses a trimmed clip page by page and returns its sample rate; asserts CRCs, sequence, one stream, flags and the last granule. */
    private static int checkClip(String name, double seconds) throws IOException {
        byte[] file = resource(SOUNDS + name + ".ogg");
        ByteBuffer bb = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        List<Long> granules = new ArrayList<>();
        List<Integer> flags = new ArrayList<>();
        Set<Integer> serials = new HashSet<>();
        int p = 0;
        int seq = 0;
        int rate = 0;
        while (p < file.length) {
            assertEquals("OggS", new String(file, p, 4, StandardCharsets.US_ASCII), name);
            int nseg = file[p + 26] & 0xff;
            int len = 0;
            for (int i = 0; i < nseg; i++) {
                len += file[p + 27 + i] & 0xff;
            }
            int total = 27 + nseg + len;
            int stored = bb.getInt(p + 22);
            byte[] copy = new byte[total];
            System.arraycopy(file, p, copy, 0, total);
            copy[22] = copy[23] = copy[24] = copy[25] = 0;
            assertEquals(stored, crc(copy, 0, total), name + " CRC of page " + seq);
            assertEquals(seq, bb.getInt(p + 18), name + " sequence number");
            if (seq == 0) {
                // The identification header alone on the first page: type 1, "vorbis", then version, channels and rate.
                int data = p + 27 + nseg;
                assertEquals(1, file[data], name + " identification header");
                assertEquals("vorbis", new String(file, data + 1, 6, StandardCharsets.US_ASCII));
                assertEquals(1, file[data + 11], name + " mono");
                rate = bb.getInt(data + 12);
            }
            if (seq == 1) {
                int data = p + 27 + nseg;
                assertEquals(3, file[data], name + " comment header follows");
            }
            seq++;
            serials.add(bb.getInt(p + 14));
            granules.add(bb.getLong(p + 6));
            flags.add(file[p + 5] & 0xff);
            p += total;
        }
        assertEquals(1, serials.size(), name + " one stream");
        assertEquals(0x02, flags.get(0), name + " first page is the beginning of the stream");
        assertEquals(0x04, flags.get(flags.size() - 1), name + " last page is the end of the stream");
        long expected = Math.round(seconds * rate);
        assertEquals(expected, granules.get(granules.size() - 1), name + " cut length in samples");
        assertEquals(0L, granules.get(0));
        return rate;
    }

    @Test
    void theTrimmedClipsAreValidOggsOfTheRightLength() throws IOException {
        assertEquals(44100, checkClip("Fizzle", 0.27));
        assertEquals(11907L, Math.round(0.27 * 44100));
        // The impact pieces are cut to 0.1 s of their own rates: 4,800 samples at 48 kHz, 4,410 at 44.1 kHz.
        assertEquals(48000, checkClip("Impact_Stone", 0.1));
        assertEquals(44100, checkClip("Impact_Burn", 0.1));
    }

    @Test
    void theSoundEventsPointAtTheRightFiles() throws IOException {
        String dir = "/Server/Audio/SoundEvents/Equipment_of_Arrakis/";
        for (String suffix : new String[] {"", "_Local"}) {
            for (String id : new String[] {"Firing", "Firing_End"}) {
                String text = new String(resource(dir + "SFX_Arrakis_Cutter_Ray_" + id + suffix + ".json"), StandardCharsets.UTF_8);
                int a = text.indexOf("Portal_Zap_01.ogg");
                int b = text.indexOf("Portal_Zap_03.ogg");
                int c = text.indexOf("Portal_Zap_04.ogg");
                assertTrue(a > 0 && a < b && b < c, id + ": 01, 03, 04 in that order");
                assertFalse(text.contains("Portal_Zap_02"));
                assertTrue(text.contains("\"RoundRobinHistorySize\": 2"));
                assertEquals(id.equals("Firing") ? 2 : 0, text.split("\"Looping\": true", -1).length - 1, id + " loop layers");
            }
            String impact = new String(resource(dir + "SFX_Arrakis_Cutter_Ray_Impact" + suffix + ".json"), StandardCharsets.UTF_8);
            assertTrue(impact.contains("Arrakis_Cutter_Ray_Impact_Stone.ogg") && impact.contains("Arrakis_Cutter_Ray_Impact_Burn.ogg"));
            String fizzle = new String(resource(dir + "SFX_Arrakis_Cutter_Ray_Fizzle" + suffix + ".json"), StandardCharsets.UTF_8);
            assertTrue(fizzle.contains("Sounds/Equipment_of_Arrakis/Arrakis_Cutter_Ray_Fizzle.ogg"));
            assertFalse(fizzle.contains("\"Looping\""));
            // The per-shot fire sound of the first version is gone.
            assertTrue(FizzleClipTest.class.getResource(dir + "SFX_Arrakis_Cutter_Ray_Fire" + suffix + ".json") == null);
        }
    }

    @Test
    void theFiringEffectKeepsTheLoopRunningWithoutReplayingTheStart() throws IOException {
        String effect = new String(resource("/Server/Entity/Effects/Equipment_of_Arrakis/Arrakis_Cutter_Ray_Firing.json"),
                StandardCharsets.UTF_8);
        assertTrue(effect.contains("\"OverlapBehavior\": \"Overwrite\""));
        assertTrue(effect.contains("\"Infinite\": false"));
        assertTrue(effect.contains("\"Duration\": 0.25"));
        String primary = new String(resource("/Server/Item/RootInteractions/Equipment_of_Arrakis/Arrakis_Cutter_Ray_Primary.json"),
                StandardCharsets.UTF_8);
        int gate = primary.indexOf("Arrakis_Cutter_Ray_Gate");
        int firing = primary.indexOf("Arrakis_Cutter_Ray_Firing");
        int shoot = primary.indexOf("Arrakis_Cutter_Ray_Shoot");
        assertTrue(gate > 0 && gate < firing && firing < shoot, "gate, then the effect, then the shot");
    }
}
