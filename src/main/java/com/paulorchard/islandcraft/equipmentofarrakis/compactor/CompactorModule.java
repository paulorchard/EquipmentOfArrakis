package com.paulorchard.islandcraft.equipmentofarrakis.compactor;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.GiveRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleAssets;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToolRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Compactor (v1 behaviour, now a main-hand tool on left click): a held click on a sand block within 3 blocks cracks it and, each
 * second, drops one item without breaking the block; 1 Mana per second while really compacting. See {@link CompactorInteraction}.
 * The interaction type is registered in the constructor, before the assets load.
 */
public final class CompactorModule implements EquipmentModule {

    public static final String ID = "compactor";
    public static final String TOOL = "Arrakis_Tool_Compactor";
    private static final int ORDER = 20;

    private static HytaleLogger logger;
    private final ModuleAssets.Guard guard;

    public CompactorModule(JavaPlugin plugin) {
        logger = plugin.getLogger();
        plugin.getCodecRegistry(Interaction.CODEC).register(CompactorInteraction.TYPE, CompactorInteraction.class,
                CompactorInteraction.CODEC);
        guard = ModuleAssets.guard(plugin, ID, id -> id.equals(TOOL) || id.startsWith("Arrakis_Compactor") || id.startsWith("SFX_Arrakis_Compactor"));
    }

    static HytaleLogger logger() {
        return logger;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void setup(ModuleContext ctx) {
        guard.activate();
        ToolRegistry.get().register(TOOL, ORDER, null);
        GiveRegistry.get().add(TOOL, ORDER, null);
        ctx.system(new CompactorSystem());
        ctx.events().registerGlobal(PlayerDisconnectEvent.class, event -> CompactorTrail.forget(event.getPlayerRef().getUuid()));
    }

    @Override
    public List<String> selfCheck() {
        List<String> lines = new ArrayList<>();
        Item item = Item.getAssetMap().getAsset(TOOL);
        if (item == null) {
            lines.add("PROBLEM the item " + TOOL + " is not loaded");
        } else {
            String[] categories = item.getCategories();
            boolean inTab = categories != null && Arrays.asList(categories).contains("IslandCraft.Tools");
            boolean problem = !inTab || item.hasRecipesToGenerate() || item.getMaxDurability() > 0 || item.getMaxStack() != 1;
            lines.add((problem ? "PROBLEM " : "") + "item " + TOOL + " loaded, categories " + Arrays.toString(categories)
                    + ", max durability " + item.getMaxDurability() + ", max stack " + item.getMaxStack() + ", animations "
                    + item.getPlayerAnimationsId() + ", interactions " + item.getInteractions());
        }
        lines.add(exists(Interaction.getAssetMap().getAsset("Arrakis_Compactor_Aim"), "interaction Arrakis_Compactor_Aim"));
        lines.add(exists(RootInteraction.getAssetMap().getAsset("Arrakis_Compactor_Primary"), "root interaction Arrakis_Compactor_Primary"));
        lines.add(exists(ItemPlayerAnimations.getAssetMap().getAsset("Arrakis_Compactor"), "animation set Arrakis_Compactor"));
        lines.add(exists(EntityEffect.getAssetMap().getAsset("Arrakis_Compactor_Active"), "entity effect Arrakis_Compactor_Active"));
        lines.add(exists(SoundEvent.getAssetMap().getAsset("SFX_Arrakis_Compactor_Active"), "sound SFX_Arrakis_Compactor_Active"));
        lines.add(exists(SoundEvent.getAssetMap().getAsset("SFX_Arrakis_Compactor_Active_Local"), "sound SFX_Arrakis_Compactor_Active_Local"));
        lines.add(exists(ParticleSystem.getAssetMap().getAsset("Arrakis_Compactor_Dust"), "particle system Arrakis_Compactor_Dust"));
        lines.add(common("Items/Equipment_of_Arrakis/Compactor/Arrakis_Compactor.blockymodel"));
        lines.add(common("Items/Equipment_of_Arrakis/Compactor/Arrakis_Compactor_Texture.png"));
        lines.add(common("Characters/Animations/Items/Equipment_of_Arrakis/Compactor/Compactor_Aim.blockyanim"));
        lines.add(common("Characters/Animations/Items/Equipment_of_Arrakis/Compactor/Compactor_Aim_FPS.blockyanim"));
        return lines;
    }

    private static String exists(Object asset, String what) {
        return (asset == null ? "PROBLEM " : "") + what + (asset == null ? " is not loaded" : " loaded");
    }

    private static String common(String path) {
        boolean found = CommonAssetRegistry.hasCommonAsset(path) || CommonAssetRegistry.hasCommonAsset("Common/" + path);
        return (found ? "" : "PROBLEM not found: ") + path;
    }
}
