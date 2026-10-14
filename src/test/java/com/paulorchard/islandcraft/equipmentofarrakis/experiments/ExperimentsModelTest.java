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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Flame Cutter's weapon model: the vanilla flamethrower model with a tapered solid line (a stack of unlit white boxes) on its muzzle
 * node, reaching 3 blocks, and the animation set with a fixed third-person pose (see tools/assets/experiments/make_flame_model.js).
 */
class ExperimentsModelTest {

    private static final Path RES = Path.of("src/main/resources");
    private static final Path COMMON = RES.resolve("Common/Items/Equipment_of_Arrakis/Experiments");
    private static final Path MODEL = COMMON.resolve("Arrakis_Exp_Flame_Cutter.blockymodel");
    private static final Path STILL = RES.resolve("Common/Characters/Animations/Items/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Static.blockyanim");
    private static final Path TEXTURE = COMMON.resolve("Arrakis_Exp_Flame_Cutter_Texture.png");
    private static final Path SET = RES.resolve("Server/Item/Animations/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Static.json");

    private static BsonDocument read(Path path) throws IOException {
        return BsonDocument.parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static void collect(BsonDocument node, List<BsonDocument> out) {
        out.add(node);
        for (BsonValue child : node.containsKey("children") ? node.getArray("children") : new BsonArray()) {
            collect(child.asDocument(), out);
        }
    }

    private static List<BsonDocument> nodes() throws IOException {
        List<BsonDocument> all = new ArrayList<>();
        for (BsonValue root : read(MODEL).getArray("nodes")) {
            collect(root.asDocument(), all);
        }
        return all;
    }

    private static double num(BsonDocument d, String key) {
        return d.getNumber(key).doubleValue();
    }

    @Test
    void theBeamIsAStackOfBoxesOnTheMuzzleThatReachesThreeBlocksAndTapers() throws IOException {
        List<BsonDocument> all = nodes();
        BsonDocument beam = all.stream().filter(n -> n.getString("name").getValue().equals("Arrakis_Beam")).findFirst().orElseThrow();
        BsonDocument muzzle = all.stream().filter(n -> n.getString("name").getValue().equals("Origin_Projectile")).findFirst().orElseThrow();
        assertTrue(muzzle.getArray("children").contains(beam), "the beam hangs on the muzzle node, the end of the barrel");

        List<BsonDocument> boxes = beam.getArray("children").stream().map(BsonValue::asDocument).toList();
        assertTrue(boxes.size() >= 12, "enough boxes for a smooth taper: " + boxes.size());
        // On screen (1920x1080, 70 degrees, the muzzle 1 block from the eye: the script's guesses) the line goes from 50 px to 2 px wide. In the world it
        // first widens a little (the perspective shrinks it faster than the taper), so the test is on the pixels, not on the units.
        double last = Double.MAX_VALUE;
        double firstPx = 0;
        double lastPx = 0;
        double reach = 0;
        for (BsonDocument box : boxes) {
            BsonDocument shape = box.getDocument("shape");
            assertEquals("box", shape.getString("type").getValue());
            assertEquals("fullbright", shape.getString("shadingMode").getValue(), "an unlit line");
            BsonDocument size = shape.getDocument("settings").getDocument("size");
            assertEquals(num(size, "x"), num(size, "z"), 1e-9, "square section");
            double y = num(box.getDocument("position"), "y");
            double distance = 1.0 + (y - 2.0) / 32.0 * 1.0;
            double px = num(size, "x") / 32.0 / (2 * distance * Math.tan(Math.toRadians(35)) / 1080);
            assertTrue(px <= last + 1e-6, "it only narrows on the screen: " + px);
            last = px;
            lastPx = px;
            firstPx = firstPx == 0 ? px : firstPx;
            reach = Math.max(reach, y + num(size, "y") / 2);
        }
        assertEquals(50, firstPx, 4, "about 50 px at the muzzle");
        assertEquals(2, lastPx, 0.6, "about 2 px at the end");
        assertEquals(96, reach, 1.0, "3 blocks of 32 units");
    }

    @Test
    void theNodeIdsAreUniqueAndTheFacesPointAtTheWhitePatch() throws IOException {
        Set<String> ids = new HashSet<>();
        for (BsonDocument node : nodes()) {
            assertTrue(ids.add(node.getString("id").getValue()), "id " + node.getString("id").getValue() + " is used once");
        }
        BufferedImage image = ImageIO.read(TEXTURE.toFile());
        assertNotNull(image);
        for (BsonDocument node : nodes()) {
            if (!node.getString("name").getValue().startsWith("Arrakis_Beam_")) {
                continue;
            }
            BsonDocument size = node.getDocument("shape").getDocument("settings").getDocument("size");
            for (var face : node.getDocument("shape").getDocument("textureLayout").entrySet()) {
                int x = face.getValue().asDocument().getDocument("offset").getInt32("x").getValue();
                int y = face.getValue().asDocument().getDocument("offset").getInt32("y").getValue();
                int w = (int) Math.ceil(Math.max(num(size, "x"), num(size, "y")));
                assertTrue(w <= 16 && x >= 0 && y >= 0 && x + w <= image.getWidth() && y + w <= image.getHeight(), "the face fits the white patch");
                for (int j = 0; j < Math.min(w, 16); j++) {
                    for (int i = 0; i < Math.min(w, 16); i++) {
                        assertEquals(0xFFFFFFFF, image.getRGB(x + i, y + j), "white and opaque at " + (x + i) + "," + (y + j));
                    }
                }
            }
        }
    }

    @Test
    void theVanillaGunIsIntactAroundTheBeam() throws IOException {
        Set<String> names = new HashSet<>();
        for (BsonDocument node : nodes()) {
            names.add(node.getString("name").getValue());
        }
        for (String name : new String[] {"R-Attachment", "Origin_Projectile", "Origin_Item", "Handle", "Rail", "Barrel", "Stock", "Butt", "Flamethrower_Bottle"}) {
            assertTrue(names.contains(name), "the vanilla node " + name + " is still there");
        }
    }

    @Test
    void everyThirdPersonAnimationIsTheStillPoseAndFirstPersonIsLeftAlone() throws IOException {
        BsonDocument set = read(SET);
        assertEquals("Rifle", set.getString("Parent").getValue());
        BsonDocument animations = set.getDocument("Animations");
        assertTrue(animations.size() >= 30, "every state of the rifle set is covered: " + animations.size());
        for (var entry : animations.entrySet()) {
            for (var key : entry.getValue().asDocument().entrySet()) {
                if (key.getKey().startsWith("ThirdPerson")) {
                    assertEquals("Characters/Animations/Items/Equipment_of_Arrakis/Experiments/Arrakis_Exp_Flame_Static.blockyanim", key.getValue().asString().getValue(),
                            entry.getKey() + "." + key.getKey() + " is the still pose");
                } else if (key.getKey().equals("FirstPerson")) {
                    assertTrue(key.getValue().asString().getValue().startsWith("Characters/Animations/Items/Dual_Handed/Rifle/"), "first person is the vanilla rifle's, by path");
                }
            }
        }
        assertTrue(Files.exists(STILL));
    }

    @Test
    void theStillPoseHasOneKeyPerChannelAndNoMovement() throws IOException {
        BsonDocument still = read(STILL);
        for (var node : still.getDocument("nodeAnimations").entrySet()) {
            for (var channel : node.getValue().asDocument().entrySet()) {
                BsonArray keys = channel.getValue().asArray();
                assertTrue(keys.size() <= 1, node.getKey() + "." + channel.getKey() + " has at most one key");
                if (keys.size() == 1) {
                    assertEquals(0, keys.get(0).asDocument().getNumber("time").intValue());
                }
            }
        }
    }
}
