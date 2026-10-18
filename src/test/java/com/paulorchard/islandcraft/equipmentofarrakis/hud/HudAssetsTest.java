package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two generated documents (adventure and creative art) have the ids the view sets and name only textures of our own pack, at the vanilla pixel size.
 */
class HudAssetsTest {

    private static final String BASE = "/Common/UI/Custom/Hud/Equipment_of_Arrakis/Hud/";
    /** The documents in the pack. */
    private static final String[] DOCUMENTS = {"Arrakis_Hud_Copy.ui", "Arrakis_Hud_Copy_Creative.ui"};
    /** Pixel sizes of the images in VanillaDerived/ (the @2x file of each name), the vanilla sizes. */
    private static final Map<String, int[]> SIZES = Map.ofEntries(
            Map.entry("StaminaIconWhite", new int[] {48, 48}),
            Map.entry("NeutralHealthBarFill", new int[] {636, 24}), Map.entry("NeutralCreativeHealthBarFill", new int[] {636, 24}),
            Map.entry("NeutralManaFill", new int[] {635, 24}), Map.entry("NeutralCreativeManaFill", new int[] {636, 24}),
            Map.entry("ErrorLeft", new int[] {635, 24}), Map.entry("ErrorOutlineLeft", new int[] {635, 24}),
            Map.entry("HealthBackground", new int[] {636, 24}), Map.entry("HealthBarFill", new int[] {636, 24}),
            Map.entry("HealthBarEffect", new int[] {522, 16}), Map.entry("CreativeHealthBarFill", new int[] {636, 24}),
            Map.entry("HealthIcon", new int[] {48, 48}), Map.entry("CreativeHealthIcon", new int[] {48, 48}),
            Map.entry("ManaBackground", new int[] {635, 24}), Map.entry("ManaFill", new int[] {635, 24}),
            Map.entry("ManaEffect", new int[] {522, 16}), Map.entry("CreativeManaFill", new int[] {636, 24}),
            Map.entry("ManaBackgroundError", new int[] {635, 24}), Map.entry("ManaBackgroundErrorOutline", new int[] {635, 24}));

    private static byte[] bytes(String name) throws IOException {
        try (InputStream in = HudAssetsTest.class.getResourceAsStream(BASE + name)) {
            assertNotNull(in, "missing " + name);
            return in.readAllBytes();
        }
    }

    private static String ui(String name) throws IOException {
        return new String(bytes(name), StandardCharsets.UTF_8);
    }

    private static List<String> paths(String document) throws IOException {
        Matcher m = Pattern.compile("\"([^\"]+\\.png)\"").matcher(ui(document));
        List<String> found = new ArrayList<>();
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    @Test
    void thePackHasOnlyTheCopyDocumentsAndNoByteDuplicateOfAVanillaIcon() throws IOException {
        for (String name : new String[] {"Misc.png", "Natural-Fluid.png"}) {
            String text = new String(bytes("VanillaDerived/" + name), StandardCharsets.ISO_8859_1);
            assertFalse(text.contains("tEXt") || text.contains("iCCP") || text.contains("pHYs"), name + " must be our re-encode");
        }
    }

    @Test
    void everyDocumentHasEveryIdTheViewSets() throws IOException {
        for (String document : DOCUMENTS) {
            String ui = ui(document);
            for (HudBar bar : HudBar.values()) {
                for (String part : new String[] {"Row", "Pad", "Error", "Fill", "AlertGroup", "Alert", "Effect", "Outline", "OutlineDim", "Icon"}) {
                    assertTrue(ui.contains("#" + bar.id + part + " {"), document + " #" + bar.id + part);
                }
            }
        }
    }

    @Test
    void theBarsUseTheVanillaProgressBarAndTheVanillaMetrics() throws IOException {
        for (String document : DOCUMENTS) {
            String ui = ui(document);
            assertEquals(4, count(ui, "ProgressBar #"), document + ": only the vanilla effect layer of each bar is a ProgressBar");
            assertEquals(8, count(ui, "HorizontalBorder: 9"), document + ": the fill and the alert fill of each bar are 9 pixel border groups (the slanted cap of the vanilla fills)");
            assertEquals(8, count(ui, "FlexWeight: 1000;"), document + ": each fill group has an empty partner sharing the bar length");
            assertTrue(ui.contains("Bottom: 120"), document + ": the bottom row is where the vanilla bars are");
            assertTrue(ui.contains("Bottom: 146"), document + ": the top row is the default gap above it");
            assertTrue(ui.contains("Width: 318, Height: 12"), document);
            assertTrue(ui.contains("Width: 24, Height: 24"), document);
            assertTrue(ui.contains("Top: -8"), document + ": the vanilla icon offset");
        }
    }

    /**
     * Rules learned on a client: Bar.Color does not tint a textured ProgressBar fill (the bars came out grey); Background.Color on a Group does.
     * A colour with an alpha, or any string the client cannot convert, set at run time disconnects the player.
     */
    @Test
    void noTexturedProgressBarSetsBarAndNoCodeSetsABarColor() throws IOException {
        for (String document : DOCUMENTS) {
            String ui = ui(document);
            Matcher m = Pattern.compile("ProgressBar #\\w+ \\{[^}]*\\}").matcher(ui);
            while (m.find()) {
                String block = m.group();
                assertFalse(block.contains("BarTexturePath") && block.contains("Bar:"), document + ": a textured ProgressBar must not set Bar: (it is ignored): " + block);
            }
            assertFalse(ui.contains("Bar: ("), document + ": Bar: ( is not used anywhere");
        }
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(java.nio.file.Path.of("src/main/java/com/paulorchard/islandcraft/equipmentofarrakis/hud"))) {
            for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                String code = java.nio.file.Files.readString(file);
                if (file.getFileName().toString().endsWith("Test.java")) {
                    continue;
                }
                assertFalse(code.contains("Bar.Color"), file + " mentions a Bar.Color selector");
            }
        }
    }

    @Test
    void theOverlaysAreShownAndHiddenNeverGivenAnAlphaAtRunTime() throws IOException {
        for (String document : DOCUMENTS) {
            String ui = ui(document);
            for (HudBar bar : HudBar.values()) {
                for (String part : new String[] {"Error", "Outline", "OutlineDim", "AlertGroup"}) {
                    int at = ui.indexOf("#" + bar.id + part + " {");
                    assertTrue(ui.substring(at, at + 80).contains("Visible: false;"), document + " #" + bar.id + part + " starts hidden");
                }
            }
            assertEquals(4, count(ui, "Color: #ffffff(0.5)"), document + ": the dim outline is a fixed half-opaque group");
            assertFalse(ui.contains("#ffffff(0)"), document + ": opacity 0 is done with Visible, not an alpha");
        }
    }

    private static int count(String text, String what) {
        int n = 0;
        for (int i = text.indexOf(what); i >= 0; i = text.indexOf(what, i + 1)) {
            n++;
        }
        return n;
    }

    @Test
    void everyTextureOfOurPackExistsWithTheVanillaPixelSize() throws IOException {
        int checked = 0;
        for (String document : DOCUMENTS) {
            for (String path : paths(document)) {
                if (!path.startsWith("VanillaDerived/")) {
                    continue;
                }
                String name = path.substring("VanillaDerived/".length(), path.length() - ".png".length());
                boolean plain = name.equals("Misc") || name.equals("Natural-Fluid");
                byte[] png = bytes("VanillaDerived/" + name + (plain ? ".png" : "@2x.png"));
                assertEquals((byte) 0x89, png[0], path);
                int[] size = plain ? new int[] {48, 48} : SIZES.get(name);
                assertNotNull(size, "no expected size for " + name);
                assertEquals(size[0], ByteBuffer.wrap(png, 16, 4).getInt(), path + " width");
                assertEquals(size[1], ByteBuffer.wrap(png, 20, 4).getInt(), path + " height");
                checked++;
            }
        }
        assertTrue(checked > 40, "the documents name many images of our pack, was " + checked);
    }

    @Test
    void theCopyRouteDependsOnNothingOutsideOurPack() throws IOException {
        for (String document : new String[] {"Arrakis_Hud_Copy.ui", "Arrakis_Hud_Copy_Creative.ui"}) {
            for (String path : paths(document)) {
                assertTrue(path.startsWith("VanillaDerived/"), document + " reaches outside the pack: " + path);
            }
        }
    }

    @Test
    void theCreativeDocumentUsesTheCreativeFillsAndTheCreativeHeart() throws IOException {
        String adventure = ui("Arrakis_Hud_Copy.ui");
        String creative = ui("Arrakis_Hud_Copy_Creative.ui");
        assertTrue(adventure.contains("VanillaDerived/HealthBarFill.png") && adventure.contains("VanillaDerived/ManaFill.png") && adventure.contains("VanillaDerived/HealthIcon.png"));
        assertFalse(adventure.contains("Creative"));
        assertTrue(creative.contains("VanillaDerived/CreativeHealthBarFill.png") && creative.contains("VanillaDerived/CreativeManaFill.png") && creative.contains("VanillaDerived/CreativeHealthIcon.png"));
        assertFalse(creative.contains("VanillaDerived/HealthBarFill.png") || creative.contains("VanillaDerived/ManaFill.png"));
        assertTrue(creative.contains("NeutralCreativeHealthBarFill") && creative.contains("NeutralCreativeManaFill"), "the tinted bars need creative neutral copies too");
    }

    @Test
    void everyDerivedImageIsNamedByTheModuleSelfCheck() {
        for (String name : HudArt.DERIVED) {
            assertTrue(SIZES.containsKey(name), name);
        }
        for (String name : HudArt.COPIES) {
            assertTrue(SIZES.containsKey(name), name);
        }
        assertEquals(SIZES.size(), HudArt.DERIVED.length + HudArt.COPIES.length);
    }
}
