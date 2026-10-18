package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The .ui and the art have the names the code uses (HudView sets these ids; the textures are named in the .ui). */
class HudAssetsTest {

    private static final String BASE = "/Common/UI/Custom/Hud/Equipment_of_Arrakis/Hud/";

    private static byte[] bytes(String name) throws IOException {
        try (InputStream in = HudAssetsTest.class.getResourceAsStream(BASE + name)) {
            assertNotNull(in, "missing " + name);
            return in.readAllBytes();
        }
    }

    @Test
    void theUiHasEveryIdTheViewSets() throws IOException {
        String ui = new String(bytes("Arrakis_Hud.ui"), StandardCharsets.UTF_8);
        for (HudBar bar : HudBar.values()) {
            for (String part : new String[] {"Row", "Track", "Pad", "Empty", "Fill", "Sheen", "IconRow", "Icon", "IconPad"}) {
                assertTrue(ui.contains("#" + bar.id + part + " {"), "#" + bar.id + part);
            }
        }
    }

    @Test
    void everyTextureTheUiNamesExistsAsAnAtTwoTimesPngWithAValidHeader() throws IOException {
        String ui = new String(bytes("Arrakis_Hud.ui"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\"(Arrakis_Hud_[A-Za-z_]+)\\.png\"").matcher(ui);
        int found = 0;
        while (m.find()) {
            byte[] png = bytes(m.group(1) + "@2x.png");
            assertEquals((byte) 0x89, png[0], m.group(1));
            assertEquals((byte) 0x50, png[1], m.group(1));
            found++;
        }
        assertEquals(4 * 3 + 4, found, "three textures on each of the four bars, and four icons");
    }

    @Test
    void theTexturesAreTwiceTheVirtualSizeTheUiShowsThemAt() throws IOException {
        byte[] fill = bytes("Arrakis_Hud_Bar_Fill_Left@2x.png");
        assertEquals(80, ByteBuffer.wrap(fill, 16, 4).getInt());
        assertEquals(24, ByteBuffer.wrap(fill, 20, 4).getInt(), "12 virtual pixels of thickness at 2x");
        byte[] icon = bytes("Arrakis_Hud_Icon_Power@2x.png");
        assertEquals(44, ByteBuffer.wrap(icon, 16, 4).getInt(), "22 virtual pixels at 2x");
        assertEquals(44, ByteBuffer.wrap(icon, 20, 4).getInt());
    }
}
