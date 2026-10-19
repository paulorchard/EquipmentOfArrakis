package com.paulorchard.islandcraft.equipmentofarrakis.thumper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The thumper assets as files: the item, the placement, the model, the sound event, the icon and the language keys. */
class ThumperAssetsTest {

    private static final String SERVER = "/Server/";
    private static final String ITEM = SERVER + "Item/Items/Equipment_of_Arrakis/Thumper/Arrakis_Thumper.json";
    private static final String INTERACTIONS = SERVER + "Item/Interactions/Equipment_of_Arrakis/Thumper/";
    private static final String ROOT = SERVER + "Item/RootInteractions/Equipment_of_Arrakis/Thumper/Arrakis_Thumper_Secondary.json";
    private static final String MODEL = SERVER + "Models/Equipment_of_Arrakis/Thumper/Arrakis_Thumper.json";
    private static final String SOUND = SERVER + "Audio/SoundEvents/Equipment_of_Arrakis/Thumper/SFX_Arrakis_Thumper_Thump.json";
    private static final String ICON = "/Common/Icons/Items/Equipment_of_Arrakis/Arrakis_Thumper.png";
    private static final String LANG = SERVER + "Languages/en-US/server.lang";

    /** The vanilla assets are referenced by path, never copied. */
    private static final String TORCH_MODEL = "Blocks/Decorative_Sets/Temple_Emerald/Torch.blockymodel";
    private static final String TORCH_TEXTURE = "Blocks/Decorative_Sets/Temple_Emerald/Torch_Texture.png";
    private static final String SAND_CLIP = "Sounds/PlayerActions/Footsteps/Block_Steps/Walk_Sand_01.ogg";

    private static byte[] bytes(String path) throws IOException {
        try (InputStream in = ThumperAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return in.readAllBytes();
        }
    }

    private static String text(String path) throws IOException {
        return new String(bytes(path), StandardCharsets.UTF_8);
    }

    private static JsonObject json(String path) throws IOException {
        return JsonParser.parseString(text(path)).getAsJsonObject();
    }

    @Test
    void theItemLooksLikeTheVanillaTorchWithoutLightOrFlame() throws IOException {
        JsonObject item = json(ITEM);
        assertEquals("Torch", item.get("PlayerAnimationsId").getAsString(), "held and swung like the vanilla torch");
        JsonObject block = item.getAsJsonObject("BlockType");
        assertEquals(TORCH_MODEL, block.get("CustomModel").getAsString(), "the vanilla model by path");
        assertEquals(TORCH_TEXTURE, block.getAsJsonArray("CustomModelTexture").get(0).getAsJsonObject().get("Texture").getAsString());
        for (String forbidden : new String[] {"Light", "Particles", "Support", "State", "AmbientSoundEventId", "Interactions", "Looping"}) {
            assertFalse(block.has(forbidden), "no " + forbidden + " on the block type: no light, no fire, no placement rules, no state");
        }
        assertFalse(text(ITEM).contains("Torch_Fire"), "no flame particle anywhere on the item");
        assertEquals(1, item.get("MaxStack").getAsInt(), "one Thumper per slot");
        assertFalse(item.has("Recipe"), "no recipe");
        assertFalse(item.has("MaxDurability"), "no durability");
        assertEquals(List.of("IslandCraft.Tools"), strings(item.getAsJsonArray("Categories")));
        assertEquals("Icons/Items/Equipment_of_Arrakis/Arrakis_Thumper.png", item.get("Icon").getAsString(),
                "an item icon must be under Icons/Items/ or Icons/ItemsGenerated/");
    }

    @Test
    void rightClickPlacesOurDeployableAndNeverTheTorchBlock() throws IOException {
        JsonObject item = json(ITEM);
        JsonObject interactions = item.getAsJsonObject("Interactions");
        assertEquals("Root_Unarmed_Attack_Swing_Left", interactions.get("Primary").getAsString(), "swung like the vanilla torch");
        assertEquals("Arrakis_Thumper_Secondary", interactions.get("Secondary").getAsString(), "not Block_Secondary: it can never be placed as a block");
        assertEquals(List.of("Arrakis_Thumper_Gate", "Arrakis_Thumper_Place", "Arrakis_Thumper_Consume"), strings(json(ROOT).getAsJsonArray("Interactions")),
                "gate first, then the placement, then one item used up");
        assertTrue(json(ROOT).get("RequireNewClick").getAsBoolean());
    }

    @Test
    void theDeployableIsInvulnerableHasNoCollisionAndNoDespawnTimer() throws IOException {
        JsonObject place = json(INTERACTIONS + "Arrakis_Thumper_Place.json");
        assertEquals("Arrakis_Thumper_Place", place.get("Type").getAsString(), "our own type: the position comes from the gate, not from a fixed offset");
        assertFalse(place.has("Offset") || place.has("OriginSource"), "no fixed offset");
        JsonObject config = place.getAsJsonObject("Config");
        assertEquals("Arrakis_Thumper", config.get("Id").getAsString(), "the id Worms is told about");
        assertEquals("Aoe", config.get("Type").getAsString());
        assertEquals(0.0, config.get("DamageAmount").getAsDouble(), "it does no damage");
        assertFalse(config.has("ApplyEffects"), "no effects");
        assertTrue(config.get("Invulnerable").getAsBoolean());
        assertEquals(0, config.get("LiveDuration").getAsDouble(), "0 is no despawn timer in the engine");
        assertFalse(config.has("HitboxCollisionConfig"), "no collision: it never blocks or pushes the player");
        assertFalse(config.has("SpawnParticles") || config.has("DespawnParticles"), "no particles");
        assertEquals("Arrakis_Thumper", config.get("Model").getAsString());
        JsonObject consume = json(INTERACTIONS + "Arrakis_Thumper_Consume.json");
        assertEquals("Adventure", consume.get("RequiredGameMode").getAsString(), "nothing is used up in creative");
        assertEquals(-1, consume.get("AdjustHeldItemQuantity").getAsInt());
    }

    @Test
    void theStandingModelIsTheSameTorchWithNoFlameAndNoLight() throws IOException {
        JsonObject model = json(MODEL);
        // the entity model may only point under Items/, Characters/, NPC/ or VFX/: the vanilla content is re-encoded there (a byte-identical copy would be a Duplicated Asset)
        assertEquals("Items/Equipment_of_Arrakis/Thumper/Arrakis_Thumper.blockymodel", model.get("Model").getAsString());
        assertEquals("Items/Equipment_of_Arrakis/Thumper/Arrakis_Thumper_Texture.png", model.get("Texture").getAsString());
        assertTrue(bytes("/Common/Items/Equipment_of_Arrakis/Thumper/Arrakis_Thumper.blockymodel").length > 1000, "the copied model is there");
        assertTrue(bytes("/Common/Items/Equipment_of_Arrakis/Thumper/Arrakis_Thumper_Texture.png").length > 100, "the copied texture is there");
        String modelText = text("/Common/Items/Equipment_of_Arrakis/Thumper/Arrakis_Thumper.blockymodel");
        assertTrue(modelText.contains("\"Flame\"") && modelText.contains("\"L-Attachment\""), "it is the torch model: the Flame node and the L-Attachment root");
        assertFalse(modelText.contains("particle") || modelText.contains("Particle"), "the model carries no particles");
        assertFalse(model.has("Particles") || model.has("Trails") || model.has("Light") || model.has("DynamicLight"));
        assertEquals(model.get("MinScale").getAsDouble(), model.get("MaxScale").getAsDouble());
        assertTrue(model.has("HitBox"));
    }

    @Test
    void theThumpIsTheVanillaSandFootstepLoweredAndLouderAndCarriesFarEnough() throws IOException {
        JsonObject sound = json(SOUND);
        JsonArray files = sound.getAsJsonArray("Layers").get(0).getAsJsonObject().getAsJsonArray("Files");
        assertEquals(List.of(SAND_CLIP), strings(files), "the clip by vanilla path, not copied");
        assertEquals(-6, sound.get("Pitch").getAsDouble(), "semitones");
        assertEquals(3, sound.get("Volume").getAsDouble(), "decibels");
        assertEquals("SFX_Attn_Loud", sound.get("Parent").getAsString(), "audible out to 45 blocks");
        assertTrue(sound.get("PreventSoundInterruption").getAsBoolean());
        assertTrue(sound.get("MaxInstance").getAsInt() >= 8, "several Thumpers must not cut each other off");
        ThumperConfig config = new ThumperConfig();
        assertEquals(config.thumpPitchSemitones(), sound.get("Pitch").getAsDouble(), "the file and the config default agree");
        assertEquals(config.thumpVolumeDb(), sound.get("Volume").getAsDouble());
    }

    @Test
    void theIconIsTheVanillaTorchWithTheFlameTakenOut() throws IOException {
        BufferedImage icon = ImageIO.read(ThumperAssetsTest.class.getResourceAsStream(ICON));
        assertNotNull(icon);
        assertEquals(64, icon.getWidth());
        assertEquals(64, icon.getHeight());
        int opaque = 0;
        List<String> flame = new ArrayList<>();
        for (int y = 0; y < icon.getHeight(); y++) {
            for (int x = 0; x < icon.getWidth(); x++) {
                int argb = icon.getRGB(x, y);
                if ((argb >>> 24) < 20) {
                    continue;
                }
                opaque++;
                float[] hsb = Color.RGBtoHSB((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, null);
                float hue = hsb[0] * 360f;
                if (hue >= 53 && hue <= 170 && hsb[1] >= 0.45f) {
                    flame.add(x + "," + y);
                }
            }
        }
        assertTrue(opaque > 600, "the torch is still there: " + opaque + " opaque pixels");
        assertTrue(flame.isEmpty(), "green or yellow flame pixels left: " + flame);
        assertEquals(0, icon.getRGB(32, 4) >>> 24, "the space the flame stood in is transparent");
    }

    @Test
    void everyKeyTheThumperUsesIsInTheLanguageFile() throws IOException {
        String lang = text(LANG);
        for (String key : new String[] {"items.Arrakis_Thumper.name", "items.Arrakis_Thumper.description", "arrakis.thumper.notSand", "arrakis.thumper.limit",
                "arrakis.thumper.noRoom", "arrakis.thumper.crouch", "arrakis.thumper.occupied", "arrakis.extractor.needCrouch", "commands.arrakis.thumper.desc", "commands.arrakis.thumper.give.desc", "commands.arrakis.thumper.state.desc",
                "commands.arrakis.thumper.clear.desc"}) {
            assertTrue(lang.contains("\n" + key + " = "), "missing language key " + key);
        }
        assertTrue(lang.contains("items.Arrakis_Thumper.description = Plant it in the sand. It thumps, and the worm comes for it."));
        String gate = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/paulorchard/islandcraft/equipmentofarrakis/thumper/ThumperGateInteraction.java"));
        for (String key : new String[] {"server.arrakis.thumper.notSand", "server.arrakis.thumper.limit", "server.arrakis.thumper.noRoom", "server.arrakis.thumper.crouch", "server.arrakis.thumper.occupied"}) {
            assertTrue(gate.contains(key), "the gate says " + key);
        }
    }

    @Test
    void theExtractorNeedsACrouchTheCrouchIsNotForcedAndTheMessageSaysSo() throws IOException {
        JsonObject gate = json(SERVER + "Item/Interactions/Equipment_of_Arrakis/Extractor/Arrakis_Extractor_Gate.json");
        assertTrue(gate.get("RequireCrouched").getAsBoolean());
        assertFalse(gate.get("ForceCrouch").getAsBoolean());
        assertTrue(text(LANG).contains("arrakis.extractor.needCrouch = Crouch to draw blood."));
        assertTrue(text(LANG).contains("arrakis.thumper.crouch = Crouch to plant the Thumper."));
    }

    @Test
    void theManifestNamesWormsAsOptionalAndDeployablesAsRequired() throws IOException {
        JsonObject manifest = json("/manifest.json");
        assertTrue(manifest.getAsJsonObject("OptionalDependencies").has("IslandCraft:IslandCraft - Worms of Arrakis"));
        assertTrue(manifest.getAsJsonObject("Dependencies").has("Hytale:Deployables"));
        assertFalse(manifest.getAsJsonObject("Dependencies").has("IslandCraft:IslandCraft - Worms of Arrakis"), "Worms is never required");
    }

    private static List<String> strings(JsonArray array) {
        List<String> list = new ArrayList<>();
        array.forEach(e -> list.add(e.getAsString()));
        return list;
    }
}
