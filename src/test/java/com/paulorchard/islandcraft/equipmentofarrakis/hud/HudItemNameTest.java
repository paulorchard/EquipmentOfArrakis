package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudItemNameTest {

    @Test
    void theVanillaAnchorIsTheNumbersReadFromTheClientHotbarUi() throws IOException {
        HudItemName.Spec vanilla = HudItemName.vanilla();
        assertEquals(126, vanilla.bottom(), "2 + 74 + 50");
        assertEquals(40, vanilla.height());
        assertNull(vanilla.top());
        assertNull(vanilla.left());
        assertNull(vanilla.width());
        JsonObject v = JsonParser.parseString(Files.readString(Path.of("tools/assets/hud/vanilla-metrics.json"))).getAsJsonObject();
        assertEquals(v.get("itemNameBottom").getAsInt(), vanilla.bottom(), "the generator read it from Hotbar.ui and Common.ui");
        assertEquals(v.get("itemNameHeight").getAsInt(), vanilla.height());
        assertEquals(v.get("itemNameSpacing").getAsInt(), HudMetrics.ITEM_NAME_SLOT_SPACING);
        assertEquals("Center", v.get("itemNameAlignment").getAsString());
    }

    @Test
    void theMovedAnchorHasNoBottomSoTheVanillaBottomIsReplacedNotCombined() {
        HudItemName.Spec moved = HudItemName.moved(new HudConfig());
        assertEquals(16, moved.top());
        assertEquals(16, moved.left());
        assertEquals(420, moved.width());
        assertEquals(40, moved.height());
        assertNull(moved.bottom());
    }

    @Test
    void theMovedAnchorFollowsTheSettingsAndTheCommandNumbers() {
        HudConfig config = HudConfig.CODEC.decode(BsonDocument.parse("{\"ItemNameTop\": 30, \"ItemNameLeft\": 40.4, \"ItemNameWidth\": 300, \"ItemNameHeight\": 24}"));
        HudItemName.Spec moved = HudItemName.moved(config);
        assertEquals(30, moved.top());
        assertEquals(40, moved.left());
        assertEquals(300, moved.width());
        assertEquals(24, moved.height());
        HudItemName.Spec override = HudItemName.moved(5, 6.6, config);
        assertEquals(5, override.top());
        assertEquals(7, override.left());
        assertEquals(300, override.width(), "the command overrides only top and left");
    }

    @Test
    void nothingIsSentAtBuildByDefaultAndOnlyWhenTheConfigAsks() {
        assertFalse(HudItemName.sendAtBuild(new HudConfig()), "the default sends nothing: a selector that does not resolve ends the session");
        assertTrue(HudItemName.sendAtBuild(HudConfig.CODEC.decode(BsonDocument.parse("{\"ItemNameMove\": true}"))));
        assertFalse(new HudConfig().isItemNameMove());
    }

    @Test
    void theItemNameIsOnlyEverSentFromTheViewBuildAndTheDevCommandNeverFromATick() throws IOException {
        String view = Files.readString(Path.of("src/main/java/com/paulorchard/islandcraft/equipmentofarrakis/hud/HudView.java"));
        String runtime = Files.readString(Path.of("src/main/java/com/paulorchard/islandcraft/equipmentofarrakis/hud/HudRuntime.java"));
        assertFalse(runtime.contains("ActiveItemName") || runtime.contains("sendItemName") || runtime.contains("HudItemName"), "the tick must not send it");
        int refresh = view.indexOf("void refresh(");
        int refreshEnd = view.indexOf("void sendItemName(");
        assertFalse(view.substring(refresh, refreshEnd).contains("ItemName"), "refresh runs on a tick");
    }

    /**
     * Every selector the HUD sends at run time, with where its element is. The vanilla client elements are "verified only on a client":
     * a selector that does not resolve disconnects the player.
     */
    @Test
    void everyRuntimeSelectorIsOneWeKnow() throws IOException {
        List<String> found = new ArrayList<>();
        try (java.util.stream.Stream<Path> files = Files.list(Path.of("src/main/java/com/paulorchard/islandcraft/equipmentofarrakis/hud"))) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java") && !f.toString().endsWith("Test.java"))::iterator) {
                Matcher m = Pattern.compile("ui\\.set(?:Object)?\\((?:\"#\" \\+ n \\+ \"|HudItemName\\.SELECTOR \\+ \"|\"#)([^\"]*)\"").matcher(Files.readString(file));
                while (m.find()) {
                    found.add(file.getFileName() + ":" + m.group(1));
                }
            }
        }
        List<String> allowed = List.of(
                // our own elements (in the four documents): proven on a client
                "Row.Anchor", "Pad.Anchor", "Row.Visible", "Fill.FlexWeight", "Empty.FlexWeight", "Effect.Value", "Alert.FlexWeight", "AlertEmpty.FlexWeight",
                "AlertGroup.Visible", "Error.Visible", "Outline.Visible", "OutlineDim.Visible", "Fill.Background.Color", "Icon.Background.Color",
                "PowerFill.Background.Color", "PowerIcon.Background.Color", "WaterIcon.Background.Color", "ExposureFill.Background.Color", "ExposureIcon.Background.Color",
                // a vanilla client element (Hotbar.ui): verified only on a client, sent only by the dev command and when ItemNameMove is true
                ".Anchor", ".Style.HorizontalAlignment");
        for (String selector : found) {
            String name = selector.substring(selector.indexOf(':') + 1);
            assertTrue(allowed.contains(name), "an unlisted run-time selector: " + selector);
            assertFalse(name.contains("Bar.Color"), selector);
        }
        assertTrue(found.size() > 15, "the scan found the selectors, was " + found.size());
    }
}
