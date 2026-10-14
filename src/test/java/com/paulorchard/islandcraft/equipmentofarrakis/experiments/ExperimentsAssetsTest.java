// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
package com.paulorchard.islandcraft.equipmentofarrakis.experiments;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Flame Cutter's source files: no fire, no damage, no sound anywhere in its chain; the red glow properties are gone from the
 * item; the numbers of the beam agree with each other (range, muzzle, speed, lifetime, the pull towards the axis); and the colours
 * have no red.
 */
class ExperimentsAssetsTest {

    private static final Path RES = Path.of("src/main/resources");
    private static final Path ITEM = RES.resolve("Server/Item/Items/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Cutter.json");
    private static final Path ROOT = RES.resolve("Server/Item/RootInteractions/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Primary.json");
    private static final Path TICK = RES.resolve("Server/Item/Interactions/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Cutter_Tick.json");
    private static final Path BURN = RES.resolve("Server/Item/Interactions/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Burn.json");
    private static final Path SYSTEM = RES.resolve("Server/Particles/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Focus.particlesystem");
    private static final Path SPAWNERS = RES.resolve("Server/Particles/Equipment_of_Arrakis/Experiments/Spawners");
    private static final Path LANG = RES.resolve("Server/Languages/en-US/server.lang");
    private static final Path TEXTURE = RES.resolve("Common/Items/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Cutter_Texture.png");

    /** Anything in the chain that would burn, hurt, push or make a sound. */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "Fire|PlaceFluid|DamageEntity|DamageCalculator|DamageEffects|Knockback|ApplyEffect|Selector|Sound|Projectile|Burn_Damage");

    private static BsonDocument read(Path path) throws IOException {
        return BsonDocument.parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    /** Every key and string value of a document, except the $Comment notes. */
    private static void strings(BsonValue value, List<String> out) {
        if (value.isDocument()) {
            for (var entry : value.asDocument().entrySet()) {
                if (!entry.getKey().equals("$Comment")) {
                    out.add(entry.getKey());
                    strings(entry.getValue(), out);
                }
            }
        } else if (value.isArray()) {
            for (BsonValue v : value.asArray()) {
                strings(v, out);
            }
        } else if (value.isString()) {
            out.add(value.asString().getValue());
        }
    }

    private static double number(BsonDocument document, String key) {
        return document.getNumber(key).doubleValue();
    }

    @Test
    void theItemKeepsTheFlamethrowerLookButLosesThePilotFlame() throws IOException {
        BsonDocument item = read(ITEM);
        for (String key : new String[] {"Particles", "Light", "SoundEventId", "ItemSoundSetId", "Recipe"}) {
            assertFalse(item.containsKey(key), "the item has no " + key + " (no red glow, no crackle, no recipe)");
        }
        assertEquals("NPC/Intelligent/Goblin/Models/Weapons/Flamethrower/Scrap.blockymodel", item.getString("Model").getValue());
        assertEquals("Goblin_Flamethrower", item.getString("PlayerAnimationsId").getValue());
        assertEquals("Icons/ItemsGenerated/Flamethrower_Scrap.png", item.getString("Icon").getValue());
        assertEquals("IslandCraft.Tools", item.getArray("Categories").get(0).asString().getValue());
        assertEquals("Arrakis_Exp_Flame_Primary", item.getDocument("Interactions").getString("Primary").getValue());
        assertTrue(Files.exists(RES.resolve("Common").resolve(item.getString("Texture").getValue())), "the texture copy is shipped");
        assertTrue(item.getDocument("Tags").getArray("Family").contains(new org.bson.BsonString("Gun")));
        assertTrue(item.containsKey("Weapon"));
    }

    @Test
    void theChainHasNoFireDamageKnockbackOrSound() throws IOException {
        List<String> strings = new ArrayList<>();
        for (Path path : new Path[] {ITEM, ROOT, TICK, BURN, SYSTEM}) {
            strings(read(path), strings);
        }
        try (var spawners = Files.list(SPAWNERS)) {
            for (Path spawner : (Iterable<Path>) spawners::iterator) {
                strings(read(spawner), strings);
            }
        }
        for (String s : strings) {
            assertFalse(FORBIDDEN.matcher(s).find(), "nothing in the chain may burn, hurt, push or sound: found " + s);
        }
        assertEquals(List.of("Arrakis_Exp_Cutter_Tick", "Arrakis_Exp_Flame_Spawn", "Arrakis_Exp_Flame_Burn"),
                read(ROOT).getArray("Interactions").stream().map(v -> v.asString().getValue()).toList(),
                "the tick comes first (it refuses the whole chain), the look after it");
    }

    @Test
    void theTickDataIsTheDocumentedOne() throws IOException {
        BsonDocument tick = read(TICK);
        assertEquals("Arrakis_Exp_Cutter_Tick", tick.getString("Type").getValue());
        assertEquals(0.1, number(tick, "RunTime"), 1e-9);
        assertEquals(5, number(tick, "Range"), 1e-9);
        assertEquals(3.3, number(tick, "ManaPerSecond"), 1e-9);
        assertTrue(number(tick, "DamageScale") > 0);
        assertEquals("Tool_Pickaxe_Mithril", tick.getString("ToolItemId").getValue());
        assertEquals(0.1, number(read(BURN), "RunTime"), 1e-9, "the look lasts one tick");
    }

    @Test
    void theBeamNumbersAgreeWithEachOther() throws IOException {
        double range = number(read(TICK), "Range");
        assertFalse(read(BURN).getDocument("Effects").containsKey("Particles"), "the ring is spawned in world space, not on the model");
        BsonDocument spawn = read(RES.resolve("Server/Item/Interactions/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Spawn.json"));
        assertEquals("Arrakis_Exp_Beam_Spawn", spawn.getString("Type").getValue());
        assertEquals("Arrakis_Exp_Flame_Focus", spawn.getString("System").getValue());
        assertEquals("Body", spawn.getString("Frame").getValue(), "held in the hand: the body frame");
        assertEquals(range, number(spawn, "Apex"), 1e-9, "the focus is the look target at Range");
        assertTrue(number(spawn, "Right") > 0, "a right-hander holds it to the right");
        assertTrue(number(spawn, "Duration") > number(read(BURN), "RunTime"), "continuous");

        BsonDocument system = read(SYSTEM);
        assertEquals("Arrakis_Exp_Flame_Focus_Beam", system.getArray("Spawners").get(0).asDocument().getString("SpawnerId").getValue());
        BsonDocument beam = read(SPAWNERS.resolve("Arrakis_Exp_Flame_Focus_Beam.particlespawner"));

        double life = number(beam.getDocument("ParticleLifeSpan"), "Min");
        assertEquals(life, number(beam.getDocument("ParticleLifeSpan"), "Max"), 1e-9);
        double speed = number(beam.getDocument("InitialVelocity").getDocument("Speed"), "Min");
        assertEquals(number(spawn, "EndOffset"), speed * life, 0.05, "the focus is EndOffset in front of the system, so the origin put that far before the target puts the focus on it");

        // A ring: Shape Sphere with only X and Y, rim only (Min = Max).
        assertEquals("Sphere", beam.getString("Shape").getValue());
        BsonDocument offset = beam.getDocument("EmitOffset");
        assertFalse(offset.containsKey("Z"), "two ranges make a ring");
        double ring = number(offset.getDocument("X"), "Max");
        assertEquals(ring, number(offset.getDocument("Y"), "Max"), 1e-9);
        assertTrue(ring >= number(offset.getDocument("X"), "Min") && ring > 0.2, "a wide ring at the muzzle: " + ring);

        // The constant pull towards the beam axis brings the ring down to a fine point exactly at the end of the life.
        BsonDocument attractor = beam.getArray("Attractors").get(0).asDocument();
        assertEquals(1, number(attractor.getDocument("RadialAxis"), "Z"), 1e-9, "the axis is the beam");
        double inward = -number(attractor, "RadialImpulse");
        assertTrue(inward > 0, "towards the axis");
        double endRadius = ring - inward * life;
        assertTrue(endRadius > 0 && endRadius <= 0.05, "the beam ends in a fine point, radius " + endRadius);

        // The size shrinks from about 0.3 to about 0.03.
        BsonDocument frame = beam.getDocument("Particle").getDocument("InitialAnimationFrame");
        double start = number(frame.getDocument("Scale").getDocument("X"), "Max");
        BsonDocument animation = beam.getDocument("Particle").getDocument("Animation");
        double end = start * number(animation.getDocument("100").getDocument("Scale").getDocument("X"), "Min");
        assertTrue(start >= 0.25 && start <= 0.4, "starts large: " + start);
        assertTrue(end <= 0.05, "ends fine: " + end);
        assertTrue(end < start / 5, "it shrinks, it does not grow");
    }

    @Test
    void theBeamColoursHaveNoRed() throws IOException {
        BsonDocument animation = read(SPAWNERS.resolve("Arrakis_Exp_Flame_Focus_Beam.particlespawner")).getDocument("Particle")
                .getDocument("Animation");
        int colours = 0;
        for (var frame : animation.entrySet()) {
            BsonDocument f = frame.getValue().asDocument();
            if (f.containsKey("Color")) {
                colours++;
                String hex = f.getString("Color").getValue();
                int r = Integer.parseInt(hex.substring(1, 3), 16);
                int g = Integer.parseInt(hex.substring(3, 5), 16);
                int b = Integer.parseInt(hex.substring(5, 7), 16);
                assertTrue(r >= 200 && g >= 0.9 * r && b >= 0.55 * r, "pale hot yellow-white to white-yellow, no red or orange: " + hex);
            }
        }
        assertTrue(colours >= 2, "the colour is keyframed over the life");
    }

    @Test
    void theTextureCopyHasNoRedFlask() throws IOException {
        BufferedImage image = ImageIO.read(TEXTURE.toFile());
        assertNotNull(image);
        assertEquals(96, image.getWidth());
        assertEquals(128, image.getHeight());
        int hot = 0;
        // The faces of Flamethrower_Eye, its Pupil and Flamethrower_Bottle_Top (see tools/assets/experiments/inspect_flame_model.js).
        for (int[] r : new int[][] {{23, 57, 46, 22}, {58, 57, 12, 22}, {80, 58, 16, 17}}) {
            for (int y = r[1]; y < r[1] + r[3]; y++) {
                for (int x = r[0]; x < r[0] + r[2]; x++) {
                    int p = image.getRGB(x, y);
                    int red = (p >> 16) & 255;
                    int green = (p >> 8) & 255;
                    int blue = p & 255;
                    if (((p >>> 24) & 255) > 0 && red >= 200 && green <= 170 && red - blue >= 80) {
                        hot++;
                    }
                }
            }
        }
        assertEquals(0, hot, "no red-orange pixel is left on the flask");
    }

    @Test
    void theTranslationsAreInTheLang() throws IOException {
        String lang = Files.readString(LANG, StandardCharsets.UTF_8);
        assertTrue(lang.contains("items.Arrakis_Exp_Flame_Cutter.name = EXPERIMENT"), "the name says EXPERIMENT");
        assertTrue(lang.contains("items.Arrakis_Exp_Flame_Cutter.description"));
        BsonArray categories = read(ITEM).getArray("Categories");
        assertEquals(1, categories.size());
    }
}
