package com.paulorchard.islandcraft.equipmentofarrakis;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the pack clean: no asset and no language key that nothing refers to. An asset counts as referenced when its file name (or its stem)
 * appears in another asset, in the Java sources or in the build script; a high-resolution image ("Name@2x.png") when the name without
 * "@2x" does (the client picks the @2x file by convention). A language key counts as referenced when its text appears in another file, or,
 * for the keys that code builds from parts, by the rules in {@link #builtKeyIsUsed}.
 */
class AssetReferencesTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path LANG = RESOURCES.resolve("Server/Languages/en-US/server.lang");
    /** Files reached by the game itself, not by a name in our files. */
    private static final Set<String> ENGINE_READS = Set.of("manifest.json", "server.lang");

    private static String readText(Path file) {
        try {
            return Files.readString(file, StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new IllegalStateException(file.toString(), e);
        }
    }

    private static List<Path> files(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }

    /** Every text that can name an asset: the resources except images and sounds, the Java sources, the build script. */
    private static List<String> texts(Path except) throws IOException {
        List<String> texts = new ArrayList<>();
        List<Path> all = new ArrayList<>(files(RESOURCES));
        all.addAll(files(Path.of("src/main/java")));
        all.add(Path.of("build.gradle.kts"));
        for (Path file : all) {
            String name = file.getFileName().toString();
            if (file.equals(except) || name.matches(".*\\.(png|ogg|wav|jar)$")) {
                continue;
            }
            texts.add(readText(file));
        }
        return texts;
    }

    @Test
    void everyAssetIsNamedBySomethingElse() throws IOException {
        List<String> orphans = new ArrayList<>();
        for (Path file : files(RESOURCES)) {
            String name = file.getFileName().toString();
            if (ENGINE_READS.contains(name)) {
                continue;
            }
            String stem = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
            String wanted = stem.endsWith("@2x") ? stem.substring(0, stem.length() - 3) : stem;
            boolean named = false;
            for (String text : texts(file)) {
                if (text.contains(wanted)) {
                    named = true;
                    break;
                }
            }
            if (!named) {
                orphans.add(RESOURCES.relativize(file).toString());
            }
        }
        assertTrue(orphans.isEmpty(), "assets nothing refers to: " + orphans);
    }

    @Test
    void everyLanguageKeyIsUsed() throws IOException {
        List<String> sources = new ArrayList<>();
        for (Path file : files(Path.of("src/main/java"))) {
            sources.add(readText(file));
        }
        List<String> texts = texts(LANG);
        List<String> unused = new ArrayList<>();
        for (String line : Files.readAllLines(LANG, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#") || !line.contains("=")) {
                continue;
            }
            String key = line.substring(0, line.indexOf('=')).trim();
            boolean used = false;
            for (String text : texts) {
                if (text.contains(key)) {
                    used = true;
                    break;
                }
            }
            if (!used && !builtKeyIsUsed(key, sources)) {
                unused.add(key);
            }
        }
        assertTrue(unused.isEmpty(), "language keys nothing refers to: " + unused);
    }

    /**
     * The keys that code assembles: a command description is {@code LANG + "give.desc"} (the part after the command group, or after a group shared by modules such as
     * blood, is a literal in the command class), and a group description is {@code LANG + "desc"} under a group name that is a literal. A message of the picker is
     * {@code LANG + "toolGone"}: its last part is a literal.
     */
    private static boolean builtKeyIsUsed(String key, List<String> sources) {
        String[] parts = key.split("\\.");
        if (key.startsWith("commands.arrakis.")) {
            // commands.arrakis.<group>.<tail...>.desc: the tail after the group is a literal, or for the group itself the group name is
            String[] rest = java.util.Arrays.copyOfRange(parts, 2, parts.length);
            if (rest.length == 1) {
                return true;
            }
            if (rest.length == 2) {
                return anyContains(sources, "\"" + rest[0] + "\"") || anyContains(sources, "\"" + rest[0] + ".desc\"");
            }
            for (int from = 0; from < rest.length - 1; from++) {
                if (anyContains(sources, "\"" + String.join(".", java.util.Arrays.copyOfRange(rest, from, rest.length)) + "\"")) {
                    return true;
                }
            }
            return false;
        }
        return anyContains(sources, "\"" + parts[parts.length - 1] + "\"");
    }

    private static boolean anyContains(List<String> texts, String what) {
        for (String text : texts) {
            if (text.contains(what)) {
                return true;
            }
        }
        return false;
    }
}
