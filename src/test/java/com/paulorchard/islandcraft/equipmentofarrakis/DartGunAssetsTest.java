package com.paulorchard.islandcraft.equipmentofarrakis;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks on the generated assets of the Maula Pistol and the Dartgun (tools/assets/make_dart_guns.js), read from the jar's resources. */
class DartGunAssetsTest {

    private static final String SERVER = "/Server/";
    private static final String[] KEYS = {"Maula", "Dartgun"};

    private static String text(String path) throws IOException {
        try (InputStream in = DartGunAssetsTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject json(String path) throws IOException {
        return JsonParser.parseString(text(path)).getAsJsonObject();
    }

    private static JsonObject root(String key) throws IOException {
        return json(SERVER + "Item/RootInteractions/Equipment_of_Arrakis/Arrakis_" + key + "_Primary.json");
    }

    @Test
    void oneShotAClickAtMostThreeASecond() throws IOException {
        for (String key : KEYS) {
            JsonObject root = root(key);
            assertTrue(root.get("RequireNewClick").getAsBoolean(), key + ": holding the button must not repeat");
            assertEquals("Shoot", root.getAsJsonObject("Cooldown").get("Id").getAsString());
            assertEquals(0.33, root.getAsJsonObject("Cooldown").get("Cooldown").getAsDouble(), 1e-9);
            assertTrue(1.0 / 0.33 < 3.04, "about three a second at most");
            assertEquals("Arrakis_" + key + "_Gate", root.getAsJsonArray("Interactions").get(0).getAsString());
        }
    }

    @Test
    void theGateTakesOneAmmoAndFallsBackToTheVanillaDryClick() throws IOException {
        for (String key : KEYS) {
            JsonObject gate = json(SERVER + "Item/Interactions/Equipment_of_Arrakis/Arrakis_" + key + "_Gate.json");
            assertEquals("Arrakis_Dart_Ammo_Gate", gate.get("Type").getAsString());
            assertEquals(1.0, gate.get("AmmoPerShot").getAsDouble(), 0.0);
            assertEquals("Arrakis_" + key + "_Shoot", gate.get("Next").getAsString());
            assertEquals("Gun_Shoot_Fail", gate.get("Failed").getAsString());
        }
    }

    @Test
    void nothingNeedsOrConsumesAnInventoryItem() throws IOException {
        for (String key : KEYS) {
            String all = text(SERVER + "Item/Items/Equipment_of_Arrakis/" + (key.equals("Maula") ? "Arrakis_Maula_Pistol" : "Arrakis_Dartgun") + ".json")
                    + text(SERVER + "Item/RootInteractions/Equipment_of_Arrakis/Arrakis_" + key + "_Primary.json")
                    + text(SERVER + "Item/Interactions/Equipment_of_Arrakis/Arrakis_" + key + "_Gate.json")
                    + text(SERVER + "Item/Interactions/Equipment_of_Arrakis/Arrakis_" + key + "_Shoot.json");
            assertFalse(all.contains("ModifyInventory"));
            assertFalse(all.contains("Weapon_Arrow_Crude"));
            assertFalse(all.contains("RequiredGameMode"), "ammo is spent in every game mode");
        }
    }

    @Test
    void theShotHasTheSmokeAndTheGunsOwnSoundsButNoFlash() throws IOException {
        double[] z = {0.35, 0.85};
        for (int i = 0; i < KEYS.length; i++) {
            JsonObject shot = json(SERVER + "Item/Interactions/Equipment_of_Arrakis/Arrakis_" + KEYS[i] + "_Shoot.json");
            assertEquals("LaunchProjectile", shot.get("Type").getAsString());
            assertEquals(0.0, shot.get("RunTime").getAsDouble(), 0.0);
            assertEquals("Arrakis_" + KEYS[i] + "_Dart", shot.get("ProjectileId").getAsString());
            JsonObject effects = shot.getAsJsonObject("Effects");
            assertEquals("Shoot", effects.get("ItemAnimationId").getAsString());
            assertEquals("Handgun_Shoot", effects.get("CameraEffect").getAsString());
            assertEquals("SFX_Arrakis_" + KEYS[i] + "_Fire", effects.get("WorldSoundEventId").getAsString());
            assertEquals("SFX_Arrakis_" + KEYS[i] + "_Fire_Local", effects.get("LocalSoundEventId").getAsString());
            JsonArray particles = effects.getAsJsonArray("Particles");
            assertEquals(1, particles.size());
            JsonObject smoke = particles.get(0).getAsJsonObject();
            assertEquals("Arrakis_Dart_Shot", smoke.get("SystemId").getAsString());
            assertEquals(z[i], smoke.getAsJsonObject("PositionOffset").get("Z").getAsDouble(), 1e-9);
            assertEquals(0.15, smoke.getAsJsonObject("PositionOffset").get("Y").getAsDouble(), 1e-9);
            assertEquals(90, smoke.getAsJsonObject("RotationOffset").get("Pitch").getAsDouble(), 0.0);
        }
        String system = text(SERVER + "Particles/Equipment_of_Arrakis_Darts/Arrakis_Dart_Shot.particlesystem");
        JsonArray spawners = JsonParser.parseString(system).getAsJsonObject().getAsJsonArray("Spawners");
        assertEquals(1, spawners.size(), "the smoke only");
        JsonObject smoke = spawners.get(0).getAsJsonObject();
        assertEquals("Rifle_Smoke", smoke.get("SpawnerId").getAsString());
        assertEquals(0.0, smoke.getAsJsonObject("PositionOffset").get("X").getAsDouble(), 0.0);
        assertEquals(0.3, smoke.getAsJsonObject("PositionOffset").get("Y").getAsDouble(), 1e-9);
        assertEquals(-0.08, smoke.getAsJsonObject("PositionOffset").get("Z").getAsDouble(), 1e-9);
        for (String flash : new String[] {"Rifle_Fire_Splash2", "Explosion_Fail_Flash_Glow", "Gun_Glow", "Rifle_Bullet_Case"}) {
            assertFalse(system.contains(flash), flash);
        }
    }

    @Test
    void theDartsAreTheVanillaBulletsWithOneDamageAndSpeedOneHundred() throws IOException {
        String[] vanillaDeath = {"SFX_GunPvP_Handgun_Bullet_Death", "SFX_GunPvP_Assault_Rifle_Bullet_Death"};
        for (int i = 0; i < KEYS.length; i++) {
            JsonObject dart = json(SERVER + "Projectiles/Equipment_of_Arrakis/Arrakis_" + KEYS[i] + "_Dart.json");
            assertEquals(1, dart.get("Damage").getAsInt());
            assertEquals(100, dart.get("MuzzleVelocity").getAsInt());
            assertEquals(100, dart.get("TerminalVelocity").getAsInt());
            assertEquals(10, dart.get("Gravity").getAsInt(), "gravity is the vanilla value");
            assertEquals("Bullet_Blunderbuss", dart.get("Appearance").getAsString());
            assertEquals(0.1, dart.get("Radius").getAsDouble(), 1e-9);
            assertTrue(dart.get("DeathEffectsOnHit").getAsBoolean());
            assertEquals("Gun_Impact", dart.getAsJsonObject("DeathParticles").get("SystemId").getAsString());
            assertEquals(vanillaDeath[i], dart.get("DeathSoundEventId").getAsString());
        }
        // the pistol's bullet keeps its own particle offset
        assertEquals(-0.3, json(SERVER + "Projectiles/Equipment_of_Arrakis/Arrakis_Maula_Dart.json")
                .getAsJsonObject("DeathParticles").getAsJsonObject("PositionOffset").get("Y").getAsDouble(), 1e-9);
    }

    @Test
    void theItemsAreTheVanillaOnesByParentAndOnlyThePistolIsMadeIncompatible() throws IOException {
        JsonObject maula = json(SERVER + "Item/Items/Equipment_of_Arrakis/Arrakis_Maula_Pistol.json");
        JsonObject dartgun = json(SERVER + "Item/Items/Equipment_of_Arrakis/Arrakis_Dartgun.json");
        assertEquals("Weapon_Handgun", maula.get("Parent").getAsString());
        assertEquals("Weapon_Assault_Rifle", dartgun.get("Parent").getAsString());
        assertFalse(maula.getAsJsonObject("Utility").get("Compatible").getAsBoolean());
        assertFalse(dartgun.has("Utility"), "the rifle has none and gets none");
        for (JsonObject item : new JsonObject[] {maula, dartgun}) {
            assertFalse(item.has("Model") || item.has("Texture") || item.has("Icon") || item.has("PlayerAnimationsId") || item.has("Categories"),
                    "model, texture, icon, animations and categories come from the vanilla item");
            assertEquals("Gun_Attack", item.getAsJsonObject("Interactions").get("Secondary").getAsString());
        }
        assertEquals("server.items.Arrakis_Maula_Pistol.name", maula.getAsJsonObject("TranslationProperties").get("Name").getAsString());
        assertEquals("server.items.Arrakis_Dartgun.name", dartgun.getAsJsonObject("TranslationProperties").get("Name").getAsString());
        String lang = text(SERVER + "Languages/en-US/server.lang");
        assertTrue(lang.contains("items.Arrakis_Maula_Pistol.name = Maula Pistol"));
        assertTrue(lang.contains("items.Arrakis_Dartgun.name = Dartgun"));
    }

    @Test
    void theFireSoundsAreTheVanillaOnesWithTheTransientHalved() throws IOException {
        // the vanilla Transient volumes in dB: the pistol 6.0, the rifle unset (0); -6 dB is half the amplitude
        double[] expected = {0.0, -6.0};
        String[] clips = {"Sounds/Weapons/Guns/Handgun/Handgun_Shot_Transient_01.ogg", "Sounds/Weapons/Guns/Rifle/Rifle_Shot_Transient_01.ogg"};
        for (int i = 0; i < KEYS.length; i++) {
            for (String suffix : new String[] {"", "_Local"}) {
                JsonObject event = json(SERVER + "Audio/SoundEvents/Equipment_of_Arrakis/SFX_Arrakis_" + KEYS[i] + "_Fire" + suffix + ".json");
                JsonArray layers = event.getAsJsonArray("Layers");
                assertEquals(4, layers.size());
                JsonObject transientLayer = layers.get(0).getAsJsonObject();
                assertEquals(clips[i], transientLayer.getAsJsonArray("Files").get(0).getAsString());
                assertEquals(expected[i], transientLayer.get("Volume").getAsDouble(), 1e-9, KEYS[i] + suffix);
                assertEquals("SFX_Attn_Loud", event.get("Parent").getAsString());
                assertEquals("AudioCat_Weapons", event.get("AudioCategory").getAsString());
                assertEquals(30, event.get("MaxInstance").getAsInt());
                assertTrue(event.get("PreventSoundInterruption").getAsBoolean());
                if (suffix.isEmpty()) {
                    assertFalse(layers.get(1).getAsJsonObject().getAsJsonArray("Files").get(0).getAsString().contains("_Stereo_"));
                } else {
                    assertTrue(layers.get(1).getAsJsonObject().getAsJsonArray("Files").get(0).getAsString().contains("_Stereo_"));
                }
            }
        }
        // the other layers keep their vanilla volumes: pistol -6, 6, -2; rifle -4, -4, -4
        JsonArray pistol = json(SERVER + "Audio/SoundEvents/Equipment_of_Arrakis/SFX_Arrakis_Maula_Fire.json").getAsJsonArray("Layers");
        assertEquals(-6.0, pistol.get(1).getAsJsonObject().get("Volume").getAsDouble(), 0.0);
        assertEquals(6.0, pistol.get(2).getAsJsonObject().get("Volume").getAsDouble(), 0.0);
        assertEquals(-2.0, pistol.get(3).getAsJsonObject().get("Volume").getAsDouble(), 0.0);
        JsonArray rifle = json(SERVER + "Audio/SoundEvents/Equipment_of_Arrakis/SFX_Arrakis_Dartgun_Fire.json").getAsJsonArray("Layers");
        for (int layer = 1; layer < 4; layer++) {
            assertEquals(-4.0, rifle.get(layer).getAsJsonObject().get("Volume").getAsDouble(), 0.0);
        }
    }

    @Test
    void theAmmoStatIsLikeThePowerStat() throws IOException {
        JsonObject ammo = json(SERVER + "Entity/Stats/Equipment_of_Arrakis/Arrakis_Ammo.json");
        JsonObject power = json(SERVER + "Entity/Stats/Equipment_of_Arrakis/Arrakis_Power.json");
        for (String key : new String[] {"InitialValue", "Min", "Max", "Shared", "ResetType"}) {
            assertEquals(power.get(key), ammo.get(key), key);
        }
        assertEquals(100, ammo.get("InitialValue").getAsInt());
        assertEquals(100, ammo.get("Max").getAsInt());
    }
}
