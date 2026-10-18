package com.paulorchard.islandcraft.equipmentofarrakis.hud;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The client rule: a run-time selector that does not resolve on the client (a vanilla element, a typo) disconnects the player. */
class HudSelectorsTest {

    @Test
    void theTickNeverTouchesAVanillaClientElement() throws IOException {
        String runtime = Files.readString(Path.of("src/main/java/com/paulorchard/islandcraft/equipmentofarrakis/hud/HudRuntime.java"));
        assertFalse(runtime.contains("ActiveItemName"), "a custom HUD cannot address built-in client elements");
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
                Matcher m = Pattern.compile("ui\\.set(?:Object)?\\((?:\"#\" \\+ n \\+ \"|\"#)([^\"]*)\"").matcher(Files.readString(file));
                while (m.find()) {
                    found.add(file.getFileName() + ":" + m.group(1));
                }
            }
        }
        List<String> allowed = List.of(
                // our own elements (in the four documents): proven on a client
                "Row.Anchor", "Pad.Anchor", "Row.Visible", "Fill.FlexWeight", "Empty.FlexWeight", "Effect.Value", "Alert.FlexWeight", "AlertEmpty.FlexWeight",
                "AlertGroup.Visible", "Error.Visible", "Outline.Visible", "OutlineDim.Visible", "Fill.Background.Color", "Icon.Background.Color",
                "PowerFill.Background.Color", "PowerIcon.Background.Color", "WaterIcon.Background.Color", "ExposureFill.Background.Color", "ExposureIcon.Background.Color");
        for (String selector : found) {
            String name = selector.substring(selector.indexOf(':') + 1);
            assertTrue(allowed.contains(name), "an unlisted run-time selector: " + selector);
            assertFalse(name.contains("Bar.Color"), selector);
        }
        assertTrue(found.size() > 15, "the scan found the selectors, was " + found.size());
    }
}
