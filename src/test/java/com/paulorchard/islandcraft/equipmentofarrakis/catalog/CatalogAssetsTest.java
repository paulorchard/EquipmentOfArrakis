package com.paulorchard.islandcraft.equipmentofarrakis.catalog;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalog source files: valid JSON, unique child ids, translation keys all present, icons either ours or vanilla (the vanilla
 * ones are looked up in Assets.zip when this machine has it).
 */
class CatalogAssetsTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path CATEGORY = RESOURCES.resolve("Server/Item/Category/CreativeLibrary/IslandCraft.json");
    private static final Path LANG = RESOURCES.resolve("Server/Languages/en-US/server.lang");
    private static final Path ITEMS = RESOURCES.resolve("Server/Item/Items");

    private static BsonDocument read(Path path) throws IOException {
        return BsonDocument.parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static Set<String> langKeys() throws IOException {
        Set<String> keys = new HashSet<>();
        for (String line : Files.readAllLines(LANG, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            int eq = trimmed.indexOf('=');
            if (!trimmed.startsWith("#") && eq > 0) {
                keys.add("server." + trimmed.substring(0, eq).trim());
            }
        }
        return keys;
    }

    /** Assets.zip of this machine's game, or null when it cannot be read. */
    private static ZipFile vanilla() {
        String appData = System.getenv("APPDATA");
        if (appData == null) {
            return null;
        }
        Path zip = Path.of(appData, "Hytale", "install", "release", "package", "game", "latest", "Assets.zip");
        try {
            return Files.isReadable(zip) ? new ZipFile(zip.toFile()) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean ours(String commonPath) {
        return Files.exists(RESOURCES.resolve("Common").resolve(commonPath));
    }

    private static boolean inVanilla(ZipFile zip, String commonPath) {
        ZipEntry entry = zip.getEntry("Common/" + commonPath);
        return entry != null;
    }

    private static void assertIconResolves(String icon, ZipFile zip, String what) {
        assertNotNull(icon, what + " has an Icon");
        if (ours(icon)) {
            return;
        }
        if (zip == null) {
            return; // no Assets.zip on this machine: a vanilla path cannot be checked
        }
        assertTrue(inVanilla(zip, icon), what + ": icon " + icon + " is neither in this mod's Common folder nor in Assets.zip");
    }

    @Test
    void categoryIsValidWithUniqueChildrenAndTranslatedNames() throws IOException {
        BsonDocument category = read(CATEGORY);
        BsonArray children = category.getArray("Children");
        List<String> ids = new ArrayList<>();
        Set<String> keys = langKeys();
        for (BsonValue child : children) {
            BsonDocument c = child.asDocument();
            ids.add(c.getString("Id").getValue());
            String name = c.getString("Name").getValue();
            assertTrue(keys.contains(name), "server.lang has " + name);
        }
        assertEquals(CatalogModule.CHILDREN, ids, "the four children, in this order");
        assertEquals(ids.size(), new HashSet<>(ids).size(), "child ids are unique");
        assertTrue(keys.contains("server.ui.itemcategory.islandcraft"), "the tab name has a translation");
    }

    @Test
    void everyIconResolvesAndBothTabIconsAreShipped() throws IOException {
        BsonDocument category = read(CATEGORY);
        try (ZipFile zip = vanilla()) {
            String tabIcon = category.getString("Icon").getValue();
            assertTrue(ours(tabIcon), "the tab icon is ours: " + tabIcon);
            assertTrue(ours(CatalogModule.activeIcon(tabIcon)), "the selected-state icon is ours: " + CatalogModule.activeIcon(tabIcon));
            for (BsonValue child : category.getArray("Children")) {
                assertIconResolves(child.asDocument().getString("Icon").getValue(), zip, child.asDocument().getString("Id").getValue());
            }
        }
    }

    @Test
    void theTabComesAfterTheVanillaTabs() throws IOException {
        try (ZipFile zip = vanilla()) {
            if (zip == null) {
                return;
            }
            int ours = read(CATEGORY).getNumber("Order").intValue();
            for (String vanillaTab : new String[] {"Blocks", "Furniture", "Items", "Tool"}) {
                ZipEntry entry = zip.getEntry("Server/Item/Category/CreativeLibrary/" + vanillaTab + ".json");
                assertNotNull(entry, vanillaTab);
                String json = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                int order = BsonDocument.parse(json).getNumber("Order").intValue();
                assertTrue(ours > order, "IslandCraft (" + ours + ") sorts after " + vanillaTab + " (" + order + ")");
            }
        }
    }

    @Test
    void everyItemInTheTabNamesAnExistingChildAndHasATranslatedName() throws IOException {
        Set<String> childIds = new HashSet<>(CatalogModule.CHILDREN);
        Set<String> keys = langKeys();
        int found = 0;
        try (Stream<Path> files = Files.walk(ITEMS); ZipFile zip = vanilla()) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".json"))::iterator) {
                BsonDocument item = read(file);
                BsonArray categories = item.containsKey("Categories") ? item.getArray("Categories") : new BsonArray();
                boolean inTab = false;
                for (BsonValue category : categories) {
                    String text = category.asString().getValue();
                    if (text.startsWith(CatalogModule.TAB + ".")) {
                        inTab = true;
                        assertTrue(childIds.contains(text.substring(text.indexOf('.') + 1)), file + " names an unknown child: " + text);
                    }
                }
                if (inTab) {
                    found++;
                    String nameKey = item.getDocument("TranslationProperties").getString("Name").getValue();
                    assertTrue(keys.contains(nameKey), file + ": server.lang has " + nameKey);
                    assertIconResolves(item.getString("Icon").getValue(), zip, file.getFileName().toString());
                    for (String field : new String[] {"Model", "Texture"}) {
                        if (item.containsKey(field) && !ours(item.getString(field).getValue()) && zip != null) {
                            assertTrue(inVanilla(zip, item.getString(field).getValue()), file + ": " + field + " "
                                    + item.getString(field).getValue() + " is in neither this mod nor Assets.zip");
                        }
                    }
                }
            }
        }
        assertFalse(found == 0, "the proof item is in the tab");
    }

    @Test
    void theDevMarkerIsAMinimalToolsItem() throws IOException {
        BsonDocument marker = read(ITEMS.resolve("Equipment_of_Arrakis/Catalog/Arrakis_Dev_Marker.json"));
        assertEquals("IslandCraft.Tools", marker.getArray("Categories").get(0).asString().getValue());
        assertEquals(1, marker.getNumber("MaxStack").intValue());
        assertTrue(marker.getString("$Comment").getValue().contains("Delete"), "the comment says it is deleted later");
        assertFalse(marker.containsKey("Recipe"), "no recipe");
        assertFalse(marker.containsKey("Interactions"), "no interactions");
        assertFalse(marker.containsKey("Utility"), "no utility block");
    }
}
