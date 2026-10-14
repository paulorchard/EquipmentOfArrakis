// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
package com.paulorchard.islandcraft.equipmentofarrakis.experiments;

import org.bson.BsonArray;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Drill Cutter's source files: the item is the drill with the strongest pickaxe's specs, one block, no wear, use distance 5; the
 * chain has the Mana gate first and nothing that hurts or sounds; the beam is the vanilla NatureBeam by id, never a copy.
 */
class ExperimentsDrillAssetsTest {

    private static final Path RES = Path.of("src/main/resources");
    private static final Path ITEM = RES.resolve("Server/Item/Items/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Drill_Cutter.json");
    private static final Path ROOT = RES.resolve("Server/Item/RootInteractions/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Drill_Mine.json");
    private static final Path INTERACTIONS = RES.resolve("Server/Item/Interactions/Equipment_of_Arrakis/Experiments");

    /** Anything in the chain that would hurt, push, burn or make a sound. */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "Sound|Damage|Selector|ApplyEffect|Spin_Source|Knockback|Fire|PlaceFluid|Projectile|Recipe");

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
    void theItemIsTheDrillWithAPickaxesSpecsAndNoWear() throws IOException {
        BsonDocument item = read(ITEM);
        for (String key : new String[] {"Recipe", "MaxDurability", "ItemSoundSetId", "Particles", "Light", "SoundEventId"}) {
            assertFalse(item.containsKey(key), "the item has no " + key);
        }
        BsonDocument tool = item.getDocument("Tool");
        assertFalse(tool.containsKey("DurabilityLossBlockTypes"), "no durability loss rules");
        BsonDocument shape = tool.getDocument("BreakShape");
        assertEquals("Box", shape.getString("Id").getValue());
        assertEquals(1, shape.getNumber("Width").intValue());
        assertEquals(1, shape.getNumber("Height").intValue());
        assertEquals(1, shape.getNumber("Depth").intValue());
        BsonDocument distance = item.getDocument("InteractionConfig").getDocument("UseDistance");
        assertEquals(5, distance.getNumber("Adventure").intValue());
        assertEquals(5, distance.getNumber("Creative").intValue(), "every game mode, so that Creative behaves the same");
        assertEquals("NPC/Intelligent/Goblin/Models/Weapons/Drill/Scrap.blockymodel", item.getString("Model").getValue());
        assertEquals("NPC/Intelligent/Goblin/Models/Weapons/Drill/Scrap_Texture.png", item.getString("Texture").getValue());
        assertEquals("Drill", item.getString("PlayerAnimationsId").getValue());
        assertEquals("IslandCraft.Tools", item.getArray("Categories").get(0).asString().getValue());
        BsonDocument interactions = item.getDocument("Interactions");
        assertEquals("Arrakis_Exp_Drill_Mine", interactions.getString("Primary").getValue());
        assertEquals("Arrakis_Exp_Drill_Mine", interactions.getString("Secondary").getValue());
    }

    @Test
    void theSpecsAreTheMithrilPickaxesWhenTheGameIsHere() throws IOException {
        String appData = System.getenv("APPDATA");
        if (appData == null) {
            return;
        }
        Path zipPath = Path.of(appData, "Hytale", "install", "release", "package", "game", "latest", "Assets.zip");
        if (!Files.isReadable(zipPath)) {
            return;
        }
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zip.getEntry("Server/Item/Items/Tool/Pickaxe/Tool_Pickaxe_Mithril.json");
            BsonArray theirs = BsonDocument.parse(new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8))
                    .getDocument("Tool").getArray("Specs");
            BsonArray mine = read(ITEM).getDocument("Tool").getArray("Specs");
            assertEquals(theirs.size(), mine.size());
            for (int i = 0; i < mine.size(); i++) {
                BsonDocument x = mine.get(i).asDocument();
                BsonDocument y = theirs.get(i).asDocument();
                assertEquals(y.getString("GatherType").getValue(), x.getString("GatherType").getValue());
                assertEquals(y.getNumber("Power").doubleValue(), x.getNumber("Power").doubleValue(), 1e-9);
                assertEquals(y.containsKey("Quality") ? y.getNumber("Quality").intValue() : 0, x.containsKey("Quality") ? x.getNumber("Quality").intValue() : 0);
            }
        }
    }

    @Test
    void theChainHasTheManaGateFirstAndNothingThatHurtsOrSounds() throws IOException {
        BsonDocument root = read(ROOT);
        assertEquals(0.3, root.getDocument("Cooldown").getNumber("Cooldown").doubleValue(), 1e-9, "the vanilla drill's cooldown");
        assertEquals(List.of("Arrakis_Exp_Drill_Gate", "Arrakis_Exp_Drill_Swing"),
                root.getArray("Interactions").stream().map(v -> v.asString().getValue()).toList(),
                "the gate is first: a Parallel's branches are forked chains that do not stop each other");
        BsonDocument gate = read(INTERACTIONS.resolve("Arrakis_Exp_Drill_Gate.json"));
        assertEquals("Arrakis_Exp_Mana_Gate", gate.getString("Type").getValue());
        assertEquals(3.3, gate.getNumber("ManaPerSecond").doubleValue(), 1e-9);
        assertEquals(0.3, gate.getNumber("Seconds").doubleValue(), 1e-9, "one run of the chain is one cooldown");

        List<String> strings = new ArrayList<>();
        strings(root, strings);
        try (Stream<Path> files = Files.list(INTERACTIONS)) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.getFileName().toString().startsWith("Arrakis_Exp_Drill_"))::iterator) {
                strings(read(file), strings);
            }
        }
        for (String s : strings) {
            assertFalse(FORBIDDEN.matcher(s).find(), "nothing in the drill chain may hurt or sound: found " + s);
        }
    }

    @Test
    void theBeamIsTheVanillaNatureBeamByIdAndNotACopy() throws IOException {
        BsonDocument effects = read(INTERACTIONS.resolve("Arrakis_Exp_Drill_Beam.json")).getDocument("Effects");
        for (String key : new String[] {"Particles", "FirstPersonParticles"}) {
            BsonDocument entry = effects.getArray(key).get(0).asDocument();
            assertEquals("NatureBeam", entry.getString("SystemId").getValue());
            double z = entry.getDocument("PositionOffset").getNumber("Z").doubleValue();
            assertTrue(z >= 3.0 && z <= 3.4, "the end burst of the beam sits near the 5 block mark (placed at " + z + ")");
            assertFalse(entry.containsKey("Color"), "untinted: the vanilla green");
        }
        try (Stream<Path> files = Files.walk(RES)) {
            assertEquals(List.of(), files.filter(f -> f.getFileName().toString().contains("NatureBeam")).map(Path::toString).toList(),
                    "NatureBeam is referenced, never copied");
        }
    }
}
