package com.paulorchard.islandcraft.equipmentofarrakis.weapons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks on the ten weapon items and their roots, read from the jar resources (the same files the generator writes). */
class WeaponAssetsTest {

    private static final String SERVER = "/Server/";
    private static final String ITEMS = SERVER + "Item/Items/Equipment_of_Arrakis/";
    private static final String ABILITY4 = "Arrakis_Root_Toggle_Holtzman";

    private static String text(String path) throws IOException {
        try (InputStream in = WeaponAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject json(String path) throws IOException {
        return JsonParser.parseString(text(path)).getAsJsonObject();
    }

    private static JsonObject weapon(String id) throws IOException {
        return json(ITEMS + "Weapons/" + id + ".json");
    }

    @Test
    void thereAreTenWeaponsWithTheDocumentedIds() {
        assertEquals(10, WeaponIds.all().size());
        assertTrue(WeaponIds.all().contains("Arrakis_Weapon_Sword_Atreides"));
        assertTrue(WeaponIds.all().contains("Arrakis_Weapon_Daggers_Harkonnen"));
        assertTrue(WeaponIds.all().contains("Arrakis_Weapon_Maula_Pistol"));
        assertTrue(WeaponIds.all().contains("Arrakis_Weapon_Dartgun"));
    }

    @Test
    void everyWeaponIsInTheIslandCraftWeaponsTabHasNoRecipeAndNeverWearsOut() throws IOException {
        for (String id : WeaponIds.all()) {
            JsonObject item = weapon(id);
            assertEquals(List.of("IslandCraft.Weapons"), strings(item.getAsJsonArray("Categories")), id);
            assertTrue(item.has("Recipe") && item.get("Recipe").isJsonNull(), id + ": Recipe must be null (the parent has one)");
            assertEquals(0, item.get("MaxDurability").getAsInt(), id);
            assertEquals(1, item.get("MaxStack").getAsInt(), id);
            assertTrue(item.getAsJsonObject("TranslationProperties").get("Name").getAsString().endsWith(id + ".name"), id);
        }
        for (String id : WeaponIds.melee()) {
            assertEquals(0, weapon(id).get("DurabilityLossOnHit").getAsInt(), id);
        }
    }

    @Test
    void theMeleeWeaponsAreChildrenOfTheCrudeWeaponWithNoDamageOfTheirOwn() throws IOException {
        for (String id : WeaponIds.melee()) {
            JsonObject item = weapon(id);
            String kind = id.contains("_Sword_") ? "Sword" : "Daggers";
            assertEquals("Weapon_" + kind + "_Crude", item.get("Parent").getAsString(), id);
            assertFalse(item.has("InteractionVars"), id + ": the damage numbers, effects and sounds must come from the Crude parent");
            assertFalse(item.has("ItemSoundSetId"), id);
            assertFalse(item.has("Weapon"), id);
        }
    }

    @Test
    void eachHouseUsesTheLookOfItsVanillaTier() throws IOException {
        String[][] tiers = {{"Crude", "Crude"}, {"Atreides", "Thorium"}, {"Choam", "Cobalt"}, {"Harkonnen", "Adamantite"}};
        for (String[] t : tiers) {
            JsonObject sword = weapon("Arrakis_Weapon_Sword_" + t[0]);
            assertEquals("Items/Weapons/Sword/" + t[1] + ".blockymodel", sword.get("Model").getAsString());
            assertEquals("Items/Weapons/Sword/" + t[1] + "_Texture.png", sword.get("Texture").getAsString());
            assertEquals("Icons/ItemsGenerated/Weapon_Sword_" + t[1] + ".png", sword.get("Icon").getAsString());
            JsonObject daggers = weapon("Arrakis_Weapon_Daggers_" + t[0]);
            assertEquals("Items/Weapons/Dagger/" + t[1] + ".blockymodel", daggers.get("Model").getAsString());
            assertEquals("Items/Weapons/Dagger/" + t[1] + "_Texture.png", daggers.get("Texture").getAsString());
            assertEquals("Icons/ItemsGenerated/Weapon_Daggers_" + t[1] + ".png", daggers.get("Icon").getAsString());
        }
    }

    @Test
    void meleeKeepsTheVanillaPrimarySecondaryAndSignatureAndAddsAbility4() throws IOException {
        JsonObject sword = weapon("Arrakis_Weapon_Sword_Choam").getAsJsonObject("Interactions");
        assertEquals("Root_Weapon_Sword_Primary", sword.get("Primary").getAsString());
        assertEquals("Root_Weapon_Sword_Secondary_Guard", sword.get("Secondary").getAsString());
        assertEquals("Root_Weapon_Sword_Signature_Vortexstrike", sword.get("Ability1").getAsString());
        assertEquals(ABILITY4, sword.get("Ability4").getAsString());
        JsonObject daggers = weapon("Arrakis_Weapon_Daggers_Choam").getAsJsonObject("Interactions");
        assertEquals("Root_Weapon_Daggers_Primary", daggers.get("Primary").getAsString());
        assertEquals("Root_Weapon_Daggers_Secondary_Guard", daggers.get("Secondary").getAsString());
        assertEquals("Root_Weapon_Daggers_Signature_Razorstrike", daggers.get("Ability1").getAsString());
        assertEquals(ABILITY4, daggers.get("Ability4").getAsString());
    }

    @Test
    void theGunsKeepTheBashAndGetAbility4AndTheirOwnPrimary() throws IOException {
        JsonObject maula = weapon(WeaponIds.MAULA);
        assertEquals("Weapon_Handgun", maula.get("Parent").getAsString());
        assertFalse(maula.getAsJsonObject("Utility").get("Compatible").getAsBoolean(), "the Maula is main hand only");
        assertEquals("Arrakis_Weapon_Maula_Primary", maula.getAsJsonObject("Interactions").get("Primary").getAsString());
        JsonObject dartgun = weapon(WeaponIds.DARTGUN);
        assertEquals("Weapon_Assault_Rifle", dartgun.get("Parent").getAsString());
        assertFalse(dartgun.has("Utility"), "the vanilla rifle is already two-handed");
        for (JsonObject gun : new JsonObject[] {maula, dartgun}) {
            assertEquals("Gun_Attack", gun.getAsJsonObject("Interactions").get("Secondary").getAsString());
            assertEquals(ABILITY4, gun.getAsJsonObject("Interactions").get("Ability4").getAsString());
        }
    }

    @Test
    void oneShotAClickAtMostThreeASecondAndTheGateIsTheOnlyPlaceACostGoes() throws IOException {
        for (String key : new String[] {"Maula", "Dartgun"}) {
            JsonObject root = json(SERVER + "Item/RootInteractions/Equipment_of_Arrakis/Weapons/Arrakis_Weapon_" + key + "_Primary.json");
            assertTrue(root.get("RequireNewClick").getAsBoolean(), key + ": holding the button must not repeat");
            assertEquals("Shoot", root.getAsJsonObject("Cooldown").get("Id").getAsString());
            assertEquals(0.33, root.getAsJsonObject("Cooldown").get("Cooldown").getAsDouble(), 1e-9);
            assertTrue(1.0 / 0.33 < 3.04, "about three a second at most");
            assertEquals("Arrakis_Weapon_" + key + "_Gate", root.getAsJsonArray("Interactions").get(0).getAsString());
            assertEquals("Ranged", root.getAsJsonObject("Tags").getAsJsonArray("Attack").get(0).getAsString());
            JsonObject gate = json(SERVER + "Item/Interactions/Equipment_of_Arrakis/Weapons/Arrakis_Weapon_" + key + "_Gate.json");
            assertEquals("Arrakis_Gun_Gate", gate.get("Type").getAsString());
            assertEquals(0, gate.get("Cost").getAsDouble(), 0.0, "free for now");
            assertEquals("Arrakis_Weapon_" + key + "_Shoot", gate.get("Next").getAsString());
            assertEquals("Gun_Shoot_Fail", gate.get("Failed").getAsString());
            JsonObject shoot = json(SERVER + "Item/Interactions/Equipment_of_Arrakis/Weapons/Arrakis_Weapon_" + key + "_Shoot.json");
            assertEquals("LaunchProjectile", shoot.get("Type").getAsString());
            assertEquals("Arrakis_" + key + "_Dart", shoot.get("ProjectileId").getAsString());
            JsonArray particles = shoot.getAsJsonObject("Effects").getAsJsonArray("Particles");
            assertEquals(1, particles.size(), "smoke only: no muzzle flash, no brass");
            assertEquals("Arrakis_Dart_Shot", particles.get(0).getAsJsonObject().get("SystemId").getAsString());
            assertEquals("Handgun_Shoot", shoot.getAsJsonObject("Effects").get("CameraEffect").getAsString());
        }
    }

    @Test
    void theDartsDoOneDamageAtSpeedOneHundredAndTheSmokeIsTheVanillaSpawnerOnly() throws IOException {
        for (String key : new String[] {"Maula", "Dartgun"}) {
            JsonObject dart = json(SERVER + "Projectiles/Equipment_of_Arrakis/Weapons/Arrakis_" + key + "_Dart.json");
            assertEquals(1, dart.get("Damage").getAsInt());
            assertEquals(100, dart.get("MuzzleVelocity").getAsDouble(), 0.0);
            assertEquals(100, dart.get("TerminalVelocity").getAsDouble(), 0.0);
        }
        JsonObject smoke = json(SERVER + "Particles/Equipment_of_Arrakis_Darts/Arrakis_Dart_Shot.particlesystem");
        assertEquals(1, smoke.getAsJsonArray("Spawners").size());
        assertEquals("Rifle_Smoke", smoke.getAsJsonArray("Spawners").get(0).getAsJsonObject().get("SpawnerId").getAsString());
    }

    @Test
    void theGateCostHookPassesAFreeShotAndRefusesAPricedOneUntilAnAmmoSystemExists() {
        assertTrue(GunCost.charge(null, null, 0f));
        assertFalse(GunCost.charge(null, null, 1f));
    }

    @Test
    void theSevenHandToolsHaveAbility4AndTheBloodBagDoesNot() throws IOException {
        String[][] tools = {
                {"Cutter", "Arrakis_Tool_Cutter_Ray"}, {"Extractor", "Arrakis_Tool_Blood_Extractor"}, {"Compactor", "Arrakis_Tool_Compactor"},
                {"Harvester", "Arrakis_Tool_Dew_Harvester"}, {"Litrejon", "Arrakis_Tool_Litrejon"},
                {"Placeholders", "Arrakis_Tool_Vehicle_Summoner"}, {"Placeholders", "Arrakis_Tool_Build_Repair"}};
        for (String[] tool : tools) {
            JsonObject item = json(ITEMS + tool[0] + "/" + tool[1] + ".json");
            assertEquals(ABILITY4, item.getAsJsonObject("Interactions").get("Ability4").getAsString(), tool[1]);
            assertEquals("Arrakis_Picker_Secondary", item.getAsJsonObject("Interactions").get("Secondary").getAsString(), tool[1] + ": the picker stays");
        }
        JsonObject bag = json(ITEMS + "BloodBag/Arrakis_Blood_Bag.json");
        JsonElement interactions = bag.get("Interactions");
        assertTrue(interactions == null || !interactions.getAsJsonObject().has("Ability4"), "the Blood Bag is not a hand tool");
    }

    @Test
    void weaponsHaveNoPickerOnRightClick() throws IOException {
        for (String id : WeaponIds.all()) {
            assertFalse(text(ITEMS + "Weapons/" + id + ".json").contains("Picker"), id);
        }
    }

    @Test
    void theToggleRootHasAShortDebounceAndNoCostOrRules() throws IOException {
        JsonObject root = json(SERVER + "Item/RootInteractions/Equipment_of_Arrakis/Core/" + ABILITY4 + ".json");
        assertEquals(0.2, root.getAsJsonObject("Cooldown").get("Cooldown").getAsDouble(), 1e-9);
        assertFalse(root.has("Rules"), "no rules: it neither blocks nor interrupts a swing or a shot");
        assertEquals("Arrakis_Toggle_Holtzman", root.getAsJsonArray("Interactions").get(0).getAsString());
        JsonObject interaction = json(SERVER + "Item/Interactions/Equipment_of_Arrakis/Core/Arrakis_Toggle_Holtzman.json");
        assertEquals("Arrakis_Toggle", interaction.get("Type").getAsString());
        assertEquals("Holtzman", interaction.get("ToggleId").getAsString());
    }

    private static List<String> strings(JsonArray array) {
        return array.asList().stream().map(JsonElement::getAsString).toList();
    }
}
