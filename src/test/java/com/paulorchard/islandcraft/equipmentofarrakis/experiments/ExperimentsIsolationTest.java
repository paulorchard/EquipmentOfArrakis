// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
package com.paulorchard.islandcraft.equipmentofarrakis.experiments;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the experiments deletable. Scans the project and fails, naming the file and the line, when:
 * <ul>
 *   <li>a file outside the allowed locations contains {@code Arrakis_Exp_} or {@code experiments} (in its text or its path);</li>
 *   <li>another module imports the experiments package;</li>
 *   <li>a file under an Experiments path has no EXPERIMENT marker;</li>
 *   <li>one of the three allowed edits outside those locations (the one plugin line, the config entries, the translation block) is
 *       missing its marker or its fence.</li>
 * </ul>
 * The allowed locations: any path with an {@code experiments} folder (the package, the assets, tools/assets/experiments, these
 * tests), the plugin and config source files on marked lines only, the block fenced by {@code # EXPERIMENT BEGIN} and
 * {@code # EXPERIMENT END} in server.lang, and the section "Experiments (delete this section with the module)" of EXPERIMENT_LOG.md.
 */
class ExperimentsIsolationTest {

    static final String PACKAGE_DIR = "src/main/java/com/paulorchard/islandcraft/equipmentofarrakis";
    static final String PLUGIN = PACKAGE_DIR + "/EquipmentOfArrakisPlugin.java";
    static final String CONFIG = PACKAGE_DIR + "/EquipmentOfArrakisConfig.java";
    static final String LANG = "src/main/resources/Server/Languages/en-US/server.lang";
    static final String LOG = "EXPERIMENT_LOG.md";
    static final String LOG_SECTION = "## Experiments (delete this section with the module)";
    static final String LOG_REMOVAL = "### Removing the experiments";
    static final String MARKER = "EXPERIMENT:";
    static final String BEGIN = "# EXPERIMENT BEGIN";
    static final String END = "# EXPERIMENT END";

    /** Folders that are never part of the project's own text. */
    private static final Set<String> SKIPPED_DIRS = Set.of(".git", ".gradle", "build", ".idea", "node_modules", "out", ".claude");
    private static final Set<String> TEXT = Set.of("java", "json", "kts", "md", "lang", "js", "sh", "txt", "properties", "xml", "gradle",
            "particlesystem", "particlespawner", "yml", "yaml", "gitignore", "gitattributes", "bat");

    @Test
    void theProjectKeepsTheExperimentsIsolated() throws IOException {
        List<String> problems = problems(Path.of("."));
        assertTrue(problems.isEmpty(), "The experiments are not isolated (see EXPERIMENT_LOG.md, \"Removing the experiments\"):\n  "
                + String.join("\n  ", problems));
    }

    // ---- the scanner proves itself on small made-up projects

    @Test
    void aCleanProjectPasses(@TempDir Path root) throws IOException {
        cleanProject(root);
        assertEquals(List.of(), problems(root));
    }

    @Test
    void aStrayIdOrWordOutsideTheAllowedLocationsIsReported(@TempDir Path root) throws IOException {
        cleanProject(root);
        write(root, PACKAGE_DIR + "/catalog/CatalogModule.java", "// uses Arrakis_Exp_Flame_Cutter\n");
        write(root, "build.gradle.kts", "// the experiments plugin\n");
        List<String> problems = problems(root);
        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("CatalogModule.java") && p.contains("another module may not")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("build.gradle.kts") && p.contains("outside the allowed locations")));
    }

    @Test
    void anotherModuleImportingExperimentsIsReported(@TempDir Path root) throws IOException {
        cleanProject(root);
        write(root, PACKAGE_DIR + "/power/PowerModule.java",
                "import com.paulorchard.islandcraft.equipmentofarrakis.experiments.ExpManaGate;\n");
        List<String> problems = problems(root);
        assertTrue(problems.stream().anyMatch(p -> p.contains("PowerModule.java") && p.contains("imports the experiments")), problems.toString());
    }

    @Test
    void aFileUnderAnExperimentsPathWithoutAMarkerIsReported(@TempDir Path root) throws IOException {
        cleanProject(root);
        write(root, PACKAGE_DIR + "/experiments/Bare.java", "package x;\nclass Bare {}\n");
        write(root, "src/main/resources/Server/Item/Items/Equipment_of_Arrakis/Experiments/Bare.json", "{\"Type\": \"x\"}\n");
        write(root, "src/main/resources/Server/Item/Items/Equipment_of_Arrakis/Experiments/Wrong.json", "{\"$Comment\": \"just a note\"}\n");
        write(root, "tools/assets/experiments/bare.js", "console.log(1);\n");
        List<String> problems = problems(root);
        assertEquals(4, problems.size(), problems.toString());
        assertTrue(problems.stream().allMatch(p -> p.contains("marker")), problems.toString());
    }

    @Test
    void theThreeEditsNeedTheirMarkersAndFences(@TempDir Path root) throws IOException {
        cleanProject(root);
        write(root, PLUGIN, "all = List.of(new experiments.ExperimentsModule());\n");
        write(root, CONFIG, "private String[] modules = {\"core\", \"experiments\"};\n");
        write(root, LANG, "items.Arrakis_Exp_Flame_Cutter.name = X\n# EXPERIMENT BEGIN\n");
        List<String> problems = problems(root);
        assertTrue(problems.stream().anyMatch(p -> p.contains("EquipmentOfArrakisPlugin.java") && p.contains("marker")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("EquipmentOfArrakisConfig.java") && p.contains("marker")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("server.lang") && p.contains("outside")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("server.lang") && p.contains("END")), problems.toString());
    }

    @Test
    void theLogSectionMustBeThereAndTextOutsideItIsReported(@TempDir Path root) throws IOException {
        cleanProject(root);
        write(root, LOG, "# Log\n\n## core\n\nExperiments are mentioned here.\n");
        List<String> problems = problems(root);
        assertTrue(problems.stream().anyMatch(p -> p.contains(LOG) && p.contains("outside")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains(LOG) && p.contains("section")), problems.toString());
    }

    // ---- the scanner

    /** Every way the project breaks the isolation rules, as readable lines; empty when it keeps them. */
    static List<String> problems(Path root) throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                Path relativePath = root.relativize(file);
                if (skipped(relativePath)) {
                    continue;
                }
                String relative = relativePath.toString().replace('\\', '/');
                boolean inExperiments = hasExperimentsFolder(relative);
                if (inExperiments) {
                    markerProblem(file, relative, problems);
                }
                if (!inExperiments && (relative.contains("Arrakis_Exp_") || relative.toLowerCase(Locale.ROOT).contains("experiments"))) {
                    problems.add(relative + ": the path names an experiment but is outside the allowed locations (put it in an Experiments folder)");
                }
                if (!isText(relative)) {
                    continue;
                }
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (inExperiments) {
                    continue;
                }
                List<String> lines = text.lines().toList();
                switch (relative) {
                    case PLUGIN -> markedLines(relative, lines, problems, true);
                    case CONFIG -> markedLines(relative, lines, problems, false);
                    case LANG -> fencedLines(relative, lines, problems);
                    case LOG -> logSection(relative, lines, problems);
                    default -> {
                        for (int i = 0; i < lines.size(); i++) {
                            if (hit(lines.get(i))) {
                                String where = relative.startsWith(PACKAGE_DIR + "/") && relative.endsWith(".java")
                                        ? "imports the experiments package or names an experiment (another module may not): "
                                        : "outside the allowed locations: ";
                                problems.add(relative + ":" + (i + 1) + ": " + where + lines.get(i).trim());
                            }
                        }
                    }
                }
            }
        }
        if (!Files.exists(root.resolve(LOG))) {
            problems.add(LOG + ": missing (it carries the section \"" + LOG_SECTION.substring(3) + "\")");
        }
        return problems;
    }

    private static boolean skipped(Path relative) {
        for (Path part : relative) {
            if (SKIPPED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasExperimentsFolder(String relative) {
        String[] parts = relative.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (parts[i].equalsIgnoreCase("experiments")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isText(String relative) {
        String name = relative.substring(relative.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return TEXT.contains(dot < 0 ? name.toLowerCase(Locale.ROOT) : name.substring(dot + 1).toLowerCase(Locale.ROOT))
                || (dot == 0 && TEXT.contains(name.substring(1).toLowerCase(Locale.ROOT)));
    }

    static boolean hit(String line) {
        return line.contains("Arrakis_Exp_") || line.toLowerCase(Locale.ROOT).contains("experiments");
    }

    /** Edits (a) and (b): every line that names the experiments carries the marker, and at least one such line exists. */
    private static void markedLines(String relative, List<String> lines, List<String> problems, boolean exactlyOne) {
        int found = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (hit(line)) {
                found++;
                if (!line.contains("// " + MARKER)) {
                    problems.add(relative + ":" + (i + 1) + ": a line naming the experiments has no \"// " + MARKER + "\" marker: " + line.trim());
                }
            }
        }
        if (found == 0) {
            return; // the edit is simply not there (the experiments were removed), which is fine
        }
        if (exactlyOne && found != 1) {
            problems.add(relative + ": the edit must be exactly one line, found " + found);
        }
    }

    /** Edit (c): the translation entries sit between the BEGIN and END comment lines, once. */
    private static void fencedLines(String relative, List<String> lines, List<String> problems) {
        int begin = -1;
        int end = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.equals(BEGIN)) {
                begin = begin < 0 ? i : begin;
            } else if (line.equals(END)) {
                end = i;
            }
        }
        if (begin >= 0 && end < begin) {
            problems.add(relative + ": the \"" + BEGIN + "\" fence has no \"" + END + "\" after it");
        }
        if (end >= 0 && begin < 0) {
            problems.add(relative + ": the \"" + END + "\" fence has no \"" + BEGIN + "\" before it");
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean fenceLine = line.trim().equals(BEGIN) || line.trim().equals(END);
            boolean inside = begin >= 0 && end > begin && i > begin && i < end;
            if (hit(line) && !inside && !fenceLine) {
                problems.add(relative + ":" + (i + 1) + ": a translation entry for an experiment outside the \"" + BEGIN + "\" ... \"" + END
                        + "\" block: " + line.trim());
            }
        }
    }

    /** EXPERIMENT_LOG.md names the experiments only inside its own section, which must have the removal subsection. */
    private static void logSection(String relative, List<String> lines, List<String> problems) {
        int start = lines.indexOf(LOG_SECTION);
        int stop = lines.size();
        for (int i = start + 1; start >= 0 && i < lines.size(); i++) {
            if (lines.get(i).startsWith("## ")) {
                stop = i;
                break;
            }
        }
        if (start < 0) {
            problems.add(relative + ": the section \"" + LOG_SECTION.substring(3) + "\" is missing");
        } else if (!lines.subList(start, stop).contains(LOG_REMOVAL)) {
            problems.add(relative + ": the section has no \"" + LOG_REMOVAL.substring(4) + "\" subsection");
        }
        for (int i = 0; i < lines.size(); i++) {
            boolean inside = start >= 0 && i >= start && i < stop;
            if (hit(lines.get(i)) && !inside) {
                problems.add(relative + ":" + (i + 1) + ": names the experiments outside the allowed section: " + lines.get(i).trim());
            }
        }
    }

    /** Every file under an Experiments path starts with, or carries, its EXPERIMENT marker. */
    private static void markerProblem(Path file, String relative, List<String> problems) throws IOException {
        String name = relative.substring(relative.lastIndexOf('/') + 1);
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        byte[] bytes = Files.readAllBytes(file);
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        boolean ok;
        switch (extension) {
            case "java" -> ok = head(text, 3).contains("// " + MARKER + " delete with the whole experiments module");
            case "js", "md", "sh" -> ok = head(text, 3).contains(MARKER);
            case "json", "particlesystem", "particlespawner" -> {
                try {
                    String comment = BsonDocument.parse(new String(bytes, StandardCharsets.UTF_8)).getString("$Comment").getValue();
                    ok = comment.startsWith(MARKER);
                } catch (RuntimeException e) {
                    ok = false;
                }
            }
            case "png" -> ok = text.contains("tEXt") && text.contains(MARKER);
            default -> {
                problems.add(relative + ": no marker rule for this kind of file; add one to ExperimentsIsolationTest");
                return;
            }
        }
        if (!ok) {
            problems.add(relative + ": has no \"" + MARKER + "\" marker (Java: a header comment; JSON: a \"$Comment\" starting with it; "
                    + "scripts: a comment in the first lines; PNG: a tEXt comment)");
        }
    }

    private static String head(String text, int lines) {
        return String.join("\n", text.lines().limit(lines).toList());
    }

    // ---- fixtures

    private static void write(Path root, String relative, String text) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /** A project that keeps every rule, with the three edits in place. */
    private static void cleanProject(Path root) throws IOException {
        write(root, PLUGIN, "all = stream(new ExperimentsModule()); // EXPERIMENT: remove with the experiments module\n");
        write(root, CONFIG, "private String[] modules = {\"core\", \"experiments\"}; // EXPERIMENT: remove with the experiments module\n");
        write(root, LANG, "items.Other.name = Other\n# EXPERIMENT BEGIN\nitems.Arrakis_Exp_Thing.name = X\n# EXPERIMENT END\n");
        write(root, LOG, "# Log\n\n## core\n\nnothing\n\n" + LOG_SECTION + "\n\n" + LOG_REMOVAL + "\n\nsteps\n");
        write(root, PACKAGE_DIR + "/core/CoreModule.java", "class CoreModule {}\n");
        write(root, PACKAGE_DIR + "/experiments/ExperimentsModule.java",
                "// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md)\nclass ExperimentsModule {}\n");
        write(root, "src/main/resources/Server/Item/Items/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Thing.json",
                "{\"$Comment\": \"EXPERIMENT: delete with the whole experiments module\"}\n");
        write(root, "tools/assets/experiments/make.js", "// EXPERIMENT: delete with the whole experiments module\n");
    }
}
