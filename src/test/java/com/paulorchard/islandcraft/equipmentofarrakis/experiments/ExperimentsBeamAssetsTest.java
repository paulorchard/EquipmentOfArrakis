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
 * The Flame Beam Cutter's source files: a static weapon (no animation), the vanilla NatureBeam by id spawned by the server from the
 * start in tools/assets/experiments/calibration.json to the look target, nothing that burns, hurts or sounds, and the calibration
 * particle systems complete.
 */
class ExperimentsBeamAssetsTest {

    private static final Path RES = Path.of("src/main/resources");
    private static final Path INTERACTIONS = RES.resolve("Server/Item/Interactions/Equipment_of_Arrakis/Experiments");
    private static final Path ITEM = RES.resolve("Server/Item/Items/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Beam.json");
    private static final Path ROOT = RES.resolve("Server/Item/RootInteractions/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Beam_Primary.json");
    private static final Path SPAWN = INTERACTIONS.resolve("Arrakis_Exp_Flame_Beam_Spawn.json");
    private static final Path PACE = INTERACTIONS.resolve("Arrakis_Exp_Flame_Beam_Pace.json");
    private static final Path CALIBRATION = Path.of("tools/assets/experiments/calibration.json");
    private static final Path PARTICLES = RES.resolve("Server/Particles/Equipment_of_Arrakis/Experiments/Calibration");

    private static final Pattern FORBIDDEN = Pattern.compile(
            "Fire|PlaceFluid|Damage|Knockback|ApplyEffect|Selector|Sound|Projectile|Burn_Damage|Animation|Particles");

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
    void theChainIsTickSpawnPaceAndHasNoFireDamageSoundAnimationOrAttachedParticles() throws IOException {
        assertEquals(List.of("Arrakis_Exp_Flame_Beam_Tick", "Arrakis_Exp_Flame_Beam_Spawn", "Arrakis_Exp_Flame_Beam_Pace"),
                read(ROOT).getArray("Interactions").stream().map(v -> v.asString().getValue()).toList());
        List<String> strings = new ArrayList<>();
        strings(read(ROOT), strings);
        strings(read(SPAWN), strings);
        strings(read(PACE), strings);
        for (String s : strings) {
            assertFalse(FORBIDDEN.matcher(s).find(), "a static weapon that does not burn, hurt or sound: found " + s);
        }
        assertEquals("Simple", read(PACE).getString("Type").getValue());
        assertFalse(read(PACE).containsKey("Effects"), "the pace has no effects, so the weapon keeps its pose");
    }

    @Test
    void theLaserIsTheNatureBeamSpawnedFromTheCalibration() throws IOException {
        BsonDocument calibration = read(CALIBRATION);
        BsonDocument spawn = read(SPAWN);
        assertEquals("Arrakis_Exp_Beam_Spawn", spawn.getString("Type").getValue());
        assertEquals("NatureBeam", spawn.getString("System").getValue());
        BsonDocument cam = calibration.getDocument("cam");
        assertEquals(cam.getNumber("x").doubleValue(), spawn.getNumber("Right").doubleValue(), 1e-6);
        assertEquals(cam.getNumber("y").doubleValue(), spawn.getNumber("Up").doubleValue(), 1e-6);
        assertEquals(cam.getNumber("z").doubleValue(), spawn.getNumber("Forward").doubleValue(), 1e-6);
        assertEquals(cam.getNumber("scale").doubleValue(), spawn.getNumber("Scale").doubleValue(), 1e-6);
        assertEquals(calibration.getNumber("apex").doubleValue(), spawn.getNumber("Apex").doubleValue(), 1e-6);
        assertEquals(calibration.getBoolean("endAtBlock").getValue(), spawn.getBoolean("EndAtBlock").getValue());
        assertEquals(1.8, spawn.getNumber("EndOffset").doubleValue(), 1e-9, "where the NatureBeam's end burst sits (measure_nature_beam.js)");
        assertTrue(spawn.getNumber("Duration").doubleValue() > read(PACE).getNumber("RunTime").doubleValue(), "continuous: a beam outlasts a pass");
        assertTrue(read(PACE).getNumber("RunTime").doubleValue() <= 1.0 / 30, "a pass is one server tick: the laser is re-spawned on every tick");
        assertEquals("Camera", spawn.getString("Frame").getValue());
        assertTrue(spawn.getBoolean("Glow").getValue(), "the laser has its pulsing glow");
        assertEquals(calibration.getNumber("pulseHz").doubleValue(), spawn.getNumber("PulseHz").doubleValue(), 1e-9);
        for (int level = 0; level < ExpBeamSpawnInteraction.GLOW_LEVELS; level++) {
            assertTrue(Files.exists(PARTICLES.getParent().resolve(ExpBeamSpawnInteraction.GLOW_PREFIX + level + ".particlesystem")), "glow level " + level);
        }
        try (Stream<Path> files = Files.walk(RES)) {
            assertEquals(List.of(), files.filter(f -> f.getFileName().toString().contains("NatureBeam")).map(Path::toString).toList(),
                    "NatureBeam is referenced, never copied");
        }
    }

    @Test
    void theBeamTickPaysAndCutsAtTheSameRatesAsTheFlameCutterTickInSmallerSteps() throws IOException {
        BsonDocument beamTick = read(INTERACTIONS.resolve("Arrakis_Exp_Flame_Beam_Tick.json"));
        BsonDocument flameTick = read(INTERACTIONS.resolve("Arrakis_Exp_Cutter_Tick.json"));
        assertEquals(read(PACE).getNumber("RunTime").doubleValue(), beamTick.getNumber("RunTime").doubleValue(), 1e-9, "one tick per pass");
        assertEquals(flameTick.getNumber("ManaPerSecond").doubleValue(), beamTick.getNumber("ManaPerSecond").doubleValue(), 1e-9);
        assertEquals(flameTick.getNumber("DamageScale").doubleValue() / flameTick.getNumber("RunTime").doubleValue(),
                beamTick.getNumber("DamageScale").doubleValue() / beamTick.getNumber("RunTime").doubleValue(), 1e-3, "the same cutting per second");
    }

    @Test
    void theCalibrationFileIsTheBlockTheCommandPrints() throws IOException {
        String text = Files.readString(CALIBRATION, StandardCharsets.UTF_8);
        ExpCalibration c = ExpCalibration.fromJson(text);
        assertEquals(ExpCalibration.fromJson(c.toJson()).toJson(), c.toJson());
        assertFalse(c.fp.node.isBlank() || c.tp.node.isBlank());
        assertTrue(text.contains("\"$Comment\": \"EXPERIMENT:"), "the block carries the marker the isolation test wants");
        assertEquals(c.toJson(), ExpCalibration.fromJson(text).toJson());
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
