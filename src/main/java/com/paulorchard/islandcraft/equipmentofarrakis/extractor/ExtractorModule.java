package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CommandRoot;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.GiveRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleAssets;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToolRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldPositional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Blood Extractor: a handgun (vanilla Handgun look, no shot) that, on a left click aimed at a fresh mob corpse within 2.5 blocks,
 * crouches the player, pokes the body for up to 1 second and puts 10 blood into the first Blood Bag with room. One body, one use.
 * No Mana. The same module keeps the bodies of killed mobs on the ground for {@code CorpseSeconds} ({@link CorpseTimerSystem}) and lets a
 * finished blood draw end a body in {@code ExtractedSeconds}. Operator helpers: {@code /arrakis blood corpse} spawns a cow and kills it two
 * blocks ahead through the normal death path; {@code blood corpses [clear]} shows or clears the extended bodies.
 */
public final class ExtractorModule implements EquipmentModule {

    public static final String ID = "extractor";
    public static final String TOOL = "Arrakis_Tool_Blood_Extractor";
    private static final int ORDER = 50;

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
        Corpses.configure(ctx.settings(ID, ExtractorConfig.class));
        Corpses.logger(ctx.logger());
        ctx.events().registerGlobal(BootEvent.class, event -> PokeSetup.apply(Corpses.config()));
        ctx.system(new ExtractorSystem());
        ctx.system(new CorpseTimerSystem());
        ctx.events().registerGlobal(PlayerDisconnectEvent.class, event -> ExtractorState.forget(event.getPlayerRef().getUuid()));
        ctx.group("blood").addSubCommand(WorldPositional.build("corpse", CommandRoot.LANG + "blood.corpse.desc", (c, w, s, a) -> CorpseCommands.corpse(c, w, s, a), "role"));
        ctx.group("blood").addSubCommand(WorldPositional.build("corpses", CommandRoot.LANG + "blood.corpses.desc", (c, w, s, a) -> CorpseCommands.corpses(c, s, a), "action"));
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
        for (String model : new String[] {CorpseCommands.DEFAULT_ROLE, "Squirrel"}) {
            ModelAsset asset = ModelAsset.getAssetMap().getAsset(model);
            ModelAsset.AnimationSet death = asset == null ? null : asset.getAnimationSetMap().get("Death");
            if (death == null) {
                lines.add("death clip of the " + model + " model: not found, skipped");
                continue;
            }
            CorpseHold.Verdict verdict = DeathClips.verdictOfSet(model, death);
            boolean unreadable = verdict.clips().contains("unreadable");
            lines.add((unreadable ? "PROBLEM " : "") + "death clip of the " + model + " model: " + verdict.clips() + " holds=" + verdict.holds()
                    + " (" + (unreadable ? "the list route" : "read from the asset registry") + "; " + DeathClips.nonHoldingList().size() + " models on the fallback list)");
        }
        return lines;
    }
}
