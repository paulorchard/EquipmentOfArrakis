package com.paulorchard.islandcraft.equipmentofarrakis.weapons;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleAssets;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ten weapons in the IslandCraft tab, Weapons child: eight melee (swords and daggers in the houses Crude, Atreides, Choam, Harkonnen;
 * the look of the vanilla Crude, Thorium, Cobalt, Adamantite, the play of the Crude one) and the Maula Pistol and the Dartgun from v1
 * (a dart a click, 1 damage, 40 blocks, free for now). Every weapon has Ability4 = {@code Arrakis_Root_Toggle_Holtzman} (the H key),
 * which core runs; this module does not know the shield.
 */
public final class WeaponsModule implements EquipmentModule {

    public static final String ID = "weapons";
    private static final String ABILITY4 = "Arrakis_Root_Toggle_Holtzman";

    private static HytaleLogger logger;
    private static volatile WeaponsConfig config = new WeaponsConfig();
    private final ModuleAssets.Guard guard;

    public WeaponsModule(JavaPlugin plugin) {
        logger = plugin.getLogger();
        plugin.getCodecRegistry(Interaction.CODEC).register(GunGateInteraction.TYPE, GunGateInteraction.class, GunGateInteraction.CODEC);
        guard = ModuleAssets.guard(plugin, ID, id -> id.startsWith("Arrakis_Weapon_") || id.startsWith("Arrakis_Maula_")
                || id.startsWith("Arrakis_Dartgun_") || id.startsWith("SFX_Arrakis_Maula_") || id.startsWith("SFX_Arrakis_Dartgun_")
                || id.startsWith("Arrakis_Dart_"));
    }

    static HytaleLogger logger() {
        return logger;
    }

    static WeaponsConfig config() {
        return config;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void setup(ModuleContext ctx) {
        guard.activate();
        config = ctx.settings(ID, WeaponsConfig.class);
        DartFlight.type = ctx.plugin().getEntityStoreRegistry().registerComponent(DartFlight.class, DartFlight::new);
        ctx.system(new DartFlightSystem(WeaponsModule::config));
        ctx.system(new DartRangeSystem(WeaponsModule::config));
        ctx.system(new DartRangeDamageSystem(WeaponsModule::config));
        ctx.command(WeaponsCommands.build());
    }

    @Override
    public List<String> selfCheck() {
        List<String> lines = new ArrayList<>();
        Item swordCrude = Item.getAssetMap().getAsset("Weapon_Sword_Crude");
        Item daggersCrude = Item.getAssetMap().getAsset("Weapon_Daggers_Crude");
        for (String id : WeaponIds.all()) {
            Item item = Item.getAssetMap().getAsset(id);
            if (item == null) {
                lines.add("PROBLEM the item " + id + " is not loaded");
                continue;
            }
            String[] categories = item.getCategories();
            boolean melee = !WeaponIds.guns().contains(id);
            Item crude = !melee ? null : id.contains("_Sword_") ? swordCrude : daggersCrude;
            Map<String, String> vars = item.getInteractionVars();
            boolean sameAsCrude = crude == null || (vars != null && Objects.equals(vars, crude.getInteractionVars()));
            boolean tab = categories != null && Arrays.asList(categories).contains("IslandCraft.Weapons");
            boolean ability4 = ABILITY4.equals(item.getInteractions().get(InteractionType.Ability4));
            boolean problem = !tab || item.hasRecipesToGenerate() || item.getMaxDurability() > 0 || item.getDurabilityLossOnHit() > 0
                    || item.getMaxStack() != 1 || !ability4 || !sameAsCrude;
            lines.add((problem ? "PROBLEM " : "") + "item " + id + " loaded, categories " + Arrays.toString(categories) + ", recipe "
                    + item.hasRecipesToGenerate() + ", max durability " + item.getMaxDurability() + ", Ability4 "
                    + item.getInteractions().get(InteractionType.Ability4) + (crude == null ? "" : ", InteractionVars same as " + crude.getId() + ": " + sameAsCrude)
                    + ", Primary " + item.getInteractions().get(InteractionType.Primary));
        }
        for (String id : new String[] {"Arrakis_Weapon_Maula_Gate", "Arrakis_Weapon_Maula_Shoot", "Arrakis_Weapon_Dartgun_Gate", "Arrakis_Weapon_Dartgun_Shoot"}) {
            lines.add(Interaction.getAssetMap().getAsset(id) == null ? "PROBLEM interaction " + id + " is not loaded" : "interaction " + id + " loaded");
        }
        for (String id : new String[] {"Arrakis_Weapon_Maula_Primary", "Arrakis_Weapon_Dartgun_Primary"}) {
            lines.add(RootInteraction.getAssetMap().getAsset(id) == null ? "PROBLEM root interaction " + id + " is not loaded" : "root interaction " + id + " loaded");
        }
        for (String id : new String[] {"SFX_Arrakis_Maula_Fire", "SFX_Arrakis_Maula_Fire_Local", "SFX_Arrakis_Dartgun_Fire", "SFX_Arrakis_Dartgun_Fire_Local"}) {
            lines.add(SoundEvent.getAssetMap().getAsset(id) == null ? "PROBLEM sound " + id + " is not loaded" : "sound " + id + " loaded");
        }
        lines.add(ParticleSystem.getAssetMap().getAsset("Arrakis_Dart_Shot") == null ? "PROBLEM particle system Arrakis_Dart_Shot is not loaded"
                : "particle system Arrakis_Dart_Shot loaded");
        return lines;
    }
}
