package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CommandRoot;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.GiveRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleAssets;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToolRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldPositional;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Blood Extractor: a handgun (vanilla Handgun look, no shot) that, on a left click aimed at a fresh mob corpse within 2.5 blocks,
 * crouches the player, pokes the body for up to 1 second and puts 10 blood into the first Blood Bag with room. One body, one use.
 * No Mana. Operator helper: {@code /arrakis blood corpse} spawns a cow and kills it two blocks ahead.
 */
public final class ExtractorModule implements EquipmentModule {

    public static final String ID = "extractor";
    public static final String TOOL = "Arrakis_Tool_Blood_Extractor";
    private static final int ORDER = 50;
    /** The role of the stand-in corpse made by /arrakis blood corpse. */
    private static final String CORPSE_ROLE = "Cow";

    private final ModuleAssets.Guard guard;

    public ExtractorModule(JavaPlugin plugin) {
        plugin.getCodecRegistry(Interaction.CODEC).register(ExtractorGateInteraction.TYPE, ExtractorGateInteraction.class,
                ExtractorGateInteraction.CODEC);
        plugin.getCodecRegistry(Interaction.CODEC).register(ExtractorFinishInteraction.TYPE, ExtractorFinishInteraction.class,
                ExtractorFinishInteraction.CODEC);
        guard = ModuleAssets.guard(plugin, ID, id -> id.equals(TOOL) || id.startsWith("Arrakis_Extractor_"));
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
        ctx.system(new ExtractorSystem());
        ctx.events().registerGlobal(PlayerDisconnectEvent.class, event -> ExtractorState.forget(event.getPlayerRef().getUuid()));
        ctx.group("blood").addSubCommand(WorldPositional.build("corpse", CommandRoot.LANG + "blood.corpse.desc", (c, w, s, a) -> corpse(c, s)));
    }

    /** Spawns a stand-in mob two blocks ahead of the player and kills it, for trying the extractor without a fight. */
    private static void corpse(CommandContext c, Store<EntityStore> store) {
        if (!c.isPlayer() || c.senderAsPlayerRef() == null) {
            CoreModule.say(c, "Run this as a player in the game.");
            return;
        }
        Ref<EntityStore> player = c.senderAsPlayerRef();
        Transform look = TargetUtil.getLook(player, store);
        Vector3d dir = new Vector3d(look.getDirection());
        dir.y = 0;
        if (dir.lengthSquared() < 1e-6) {
            dir.set(0, 0, 1);
        }
        Vector3d at = new Vector3d(look.getPosition()).sub(0, 1.5, 0).add(dir.normalize().mul(2.0));
        try {
            var spawned = NPCPlugin.get().spawnNPC(store, CORPSE_ROLE, null, at, new Rotation3f());
            if (spawned == null) {
                CoreModule.say(c, "Could not spawn the stand-in " + CORPSE_ROLE + ".");
                return;
            }
            DeathComponent.tryAddComponent(store, spawned.first(), new Damage(Damage.NULL_SOURCE, DamageCause.COMMAND, 100000f));
            CoreModule.say(c, "A dead " + CORPSE_ROLE + " is two blocks ahead; it vanishes within seconds, like a real corpse.");
        } catch (RuntimeException e) {
            CoreModule.say(c, "Could not make the stand-in corpse: " + e);
        }
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
        for (String id : new String[] {"Arrakis_Extractor_Gate", "Arrakis_Extractor_Poke", "Arrakis_Extractor_Finish"}) {
            lines.add(Interaction.getAssetMap().getAsset(id) == null ? "PROBLEM interaction " + id + " is not loaded" : "interaction " + id + " loaded");
        }
        lines.add(RootInteraction.getAssetMap().getAsset("Arrakis_Extractor_Primary") == null
                ? "PROBLEM root interaction Arrakis_Extractor_Primary is not loaded" : "root interaction Arrakis_Extractor_Primary loaded");
        return lines;
    }
}
