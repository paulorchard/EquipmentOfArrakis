package com.paulorchard.islandcraft.equipmentofarrakis;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The module boundary rule: a module may use only its own package, core and power. core uses no other module, power uses core
 * only. The root package (the plugin and the config) may use everything. Scans the source for any reference to a package of the
 * mod, imports and fully qualified names alike.
 */
class ModuleBoundaryTest {

    private static final String ROOT = "com.paulorchard.islandcraft.equipmentofarrakis";
    private static final Pattern REFERENCE = Pattern.compile(Pattern.quote(ROOT) + "\\.([A-Za-z0-9_]+)");

    /** module package -> the packages it may reference. */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "core", Set.of("core"),
            "power", Set.of("power", "core"),
            "catalog", Set.of("catalog", "core", "power"));

    @Test
    void modulesOnlyUseTheirOwnPackageCoreAndPower() throws IOException {
        List<String> violations = scan(Path.of("src/main/java"));
        assertTrue(violations.isEmpty(), "Module boundary violated (a module may import only its own package, core and power;"
                + " core imports no module; power imports core only):\n  " + String.join("\n  ", violations));
    }

    @Test
    void theScannerCatchesAViolation() {
        assertEquals(1, check("power", "import " + ROOT + ".catalog.CatalogModule;").size(),
                "an import of another module must be reported");
        assertTrue(check("power", "import " + ROOT + ".core.WorldAccess;").isEmpty());
        assertEquals(1, check("core", "import " + ROOT + ".power.Power;").size());
        assertEquals(1, check("catalog", "import " + ROOT + ".EquipmentOfArrakisPlugin;").size(),
                "a module may not reach the root package either");
        assertEquals(1, check("catalog", "var x = " + ROOT + ".cutter.Thing.make();").size(), "fully qualified names count");
    }

    static List<String> scan(Path sourceRoot) throws IOException {
        Path base = sourceRoot.resolve(ROOT.replace('.', '/'));
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(base)) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                Path relative = base.relativize(file);
                if (relative.getNameCount() < 2) {
                    continue; // the root package: the plugin lists every module
                }
                String module = relative.getName(0).toString();
                for (String violation : check(module, Files.readString(file))) {
                    violations.add(relative + ": " + violation);
                }
            }
        }
        return violations;
    }

    static List<String> check(String module, String source) {
        // A module not listed above is a module like any other: its own package, core and power.
        Set<String> allowed = ALLOWED.getOrDefault(module, Set.copyOf(List.of(module, "core", "power")));
        List<String> violations = new ArrayList<>();
        Matcher m = REFERENCE.matcher(source);
        while (m.find()) {
            String target = m.group(1);
            boolean isPackage = Character.isLowerCase(target.charAt(0));
            if (!isPackage || !allowed.contains(target)) {
                violations.add(module + " references " + m.group().substring(ROOT.length() + 1)
                        + (isPackage ? " (the " + target + " package is not allowed to " + module + ")" : " (the root package)"));
            }
        }
        return violations;
    }
}
