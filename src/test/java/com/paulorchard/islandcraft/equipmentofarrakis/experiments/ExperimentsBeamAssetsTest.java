// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
package com.paulorchard.islandcraft.equipmentofarrakis.experiments;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Flame Beam Cutter's source files: a static weapon (no animation), the vanilla NatureBeam by id placed exactly as
 * tools/assets/experiments/calibration.json says, nothing that burns, hurts or sounds, and the calibration particle systems complete.
 */
class ExperimentsBeamAssetsTest {

    private static final Path RES = Path.of("src/main/resources");
    private static final Path ITEM = RES.resolve("Server/Item/Items/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Beam.json");
    private static final Path ROOT = RES.resolve("Server/Item/RootInteractions/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Beam_Primary.json");
    private static final Path LOOK = RES.resolve("Server/Item/Interactions/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Beam_Look.json");
    private static final Path CALIBRATION = Path.of("tools/assets/experiments/calibration.json");
    private static final Path PARTICLES = RES.resolve("Server/Particles/Equipment_of_Arrakis/Experiments/Calibration");

    private static final Pattern FORBIDDEN = Pattern.compile(
            "Fire|PlaceFluid|Damage|Knockback|ApplyEffect|Selector|Sound|Projectile|Burn_Damage|Animation");

    private static BsonDocument read(Path path) throws IOException {
        return BsonDocument.parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static void strings(BsonValue value, List<String> out) {
        if (value.isDocument()) {
            for (var entry : value.asDocument().entrySet()) {
                if (!entry.getKey().equals("$Comment")) {
                    out.add(entry.getKey());
                    strings(entry.getValue(), out);
                }
            }
        } else if (value.isArray()) {
            value.asArray().forEach(v -> strings(v, out));
        } else if (value.isString()) {
            out.add(value.asString().getValue());
        }
    }

    @Test
    void theItemIsTheFlameCutterWithItsOwnPrimary() throws IOException {
        BsonDocument item = read(ITEM);
        BsonDocument cutter = read(RES.resolve("Server/Item/Items/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Cutter.json"));
        for (String key : new String[] {"Particles", "Light", "SoundEventId", "ItemSoundSetId", "Recipe"}) {
            assertFalse(item.containsKey(key), "the item has no " + key);
        }
        for (String key : new String[] {"Model", "Texture", "PlayerAnimationsId", "Icon", "Tags", "Weapon", "Categories"}) {
            assertEquals(cutter.get(key), item.get(key), key + " is the Flame Cutter's");
        }
        assertEquals("Arrakis_Exp_Flame_Beam_Primary", item.getDocument("Interactions").getString("Primary").getValue());
    }

    @Test
    void theChainIsTickThenLookAndHasNoFireDamageSoundOrAnimation() throws IOException {
        assertEquals(List.of("Arrakis_Exp_Cutter_Tick", "Arrakis_Exp_Flame_Beam_Look"),
                read(ROOT).getArray("Interactions").stream().map(v -> v.asString().getValue()).toList());
        List<String> strings = new ArrayList<>();
        strings(read(ROOT), strings);
        strings(read(LOOK), strings);
        for (String s : strings) {
            assertFalse(FORBIDDEN.matcher(s).find(), "a static weapon that does not burn, hurt or sound: found " + s);
        }
        assertEquals("Simple", read(LOOK).getString("Type").getValue());
    }

    @Test
    void theLaserIsTheNatureBeamPlacedFromTheCalibration() throws IOException {
        BsonDocument calibration = read(CALIBRATION);
        BsonDocument effects = read(LOOK).getDocument("Effects");
        check(calibration.getDocument("tp"), effects.getArray("Particles").get(0).asDocument());
        check(calibration.getDocument("fp"), effects.getArray("FirstPersonParticles").get(0).asDocument());
        assertTrue(calibration.getNumber("apex").doubleValue() > 0);
        try (Stream<Path> files = Files.walk(RES)) {
            assertEquals(List.of(), files.filter(f -> f.getFileName().toString().contains("NatureBeam")).map(Path::toString).toList(),
                    "NatureBeam is referenced, never copied");
        }
    }

    private static void check(BsonDocument calibration, BsonDocument entry) {
        assertEquals("NatureBeam", entry.getString("SystemId").getValue());
        assertEquals("Entity", entry.getString("TargetEntityPart").getValue());
        assertEquals(calibration.getString("node").getValue(), entry.getString("TargetNodeName").getValue());
        BsonDocument position = entry.getDocument("PositionOffset");
        assertEquals(calibration.getNumber("x").doubleValue(), position.getNumber("X").doubleValue(), 1e-9);
        assertEquals(calibration.getNumber("y").doubleValue(), position.getNumber("Y").doubleValue(), 1e-9);
        assertEquals(calibration.getNumber("z").doubleValue(), position.getNumber("Z").doubleValue(), 1e-9);
        BsonDocument rotation = entry.getDocument("RotationOffset");
        assertEquals(calibration.getNumber("yaw").doubleValue(), rotation.getNumber("Yaw").doubleValue(), 1e-9);
        assertEquals(calibration.getNumber("pitch").doubleValue(), rotation.getNumber("Pitch").doubleValue(), 1e-9);
        assertEquals(calibration.getNumber("roll").doubleValue(), rotation.getNumber("Roll").doubleValue(), 1e-9);
        assertEquals(calibration.getBoolean("detached").getValue(), entry.containsKey("DetachedFromModel"));
        assertEquals(calibration.getNumber("scale").doubleValue(), entry.containsKey("Scale") ? entry.getNumber("Scale").doubleValue() : 1, 1e-9);
    }

    @Test
    void theCalibrationFileIsTheBlockTheCommandPrints() throws IOException {
        String text = Files.readString(CALIBRATION, StandardCharsets.UTF_8);
        ExpCalibration c = ExpCalibration.fromJson(text);
        assertEquals(ExpCalibration.fromJson(c.toJson()).toJson(), c.toJson());
        assertFalse(c.fp.node.isBlank() || c.tp.node.isBlank());
        assertTrue(text.contains("\"$Comment\": \"EXPERIMENT:"), "the block carries the marker the isolation test wants");
    }

    @Test
    void everyCalibrationSystemNamesSpawnersThatAreShipped() throws IOException {
        List<Path> systems;
        try (Stream<Path> files = Files.list(PARTICLES)) {
            systems = files.filter(f -> f.toString().endsWith(".particlesystem")).toList();
        }
        assertEquals(4, systems.size(), "dot, mark, ruler, stream");
        for (Path system : systems) {
            for (BsonValue entry : read(system).getArray("Spawners")) {
                String id = entry.asDocument().getString("SpawnerId").getValue();
                assertTrue(Files.exists(PARTICLES.resolve("Spawners").resolve(id + ".particlespawner")), system.getFileName() + " names " + id);
            }
        }
    }
}
