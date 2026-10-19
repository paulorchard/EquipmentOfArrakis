package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
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
 * No Mana. The same module keeps the bodies of killed mobs on the ground for {@code CorpseSeconds} ({@link CorpseTimerSystem}) and lets a
 * finished blood draw end a body in {@code ExtractedSeconds}. Operator helpers: {@code /arrakis blood corpse} spawns a cow and kills it two
 * blocks ahead through the normal death path; {@code blood corpses [clear]} shows or clears the extended bodies.
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
        Corpses.configure(ctx.settings(ID, ExtractorConfig.class));
        ctx.system(new ExtractorSystem());
        ctx.system(new CorpseTimerSystem());
        ctx.events().registerGlobal(PlayerDisconnectEvent.class, event -> ExtractorState.forget(event.getPlayerRef().getUuid()));
        ctx.group("blood").addSubCommand(WorldPositional.build("corpse", CommandRoot.LANG + "blood.corpse.desc", (c, w, s, a) -> corpse(c, s)));
        ctx.group("blood").addSubCommand(WorldPositional.build("corpses", CommandRoot.LANG + "blood.corpses.desc", (c, w, s, a) -> corpses(c, s, a), "action"));
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
            CoreModule.say(c, "A dead " + CORPSE_ROLE + " is two blocks ahead; it stays " + Math.round(Corpses.config().corpseSeconds()) + " seconds, like a real corpse.");
        } catch (RuntimeException e) {
            CoreModule.say(c, "Could not make the stand-in corpse: " + e);
        }
    }

    /** The numbers of /arrakis blood corpses; with the word clear, every extended body gets the quick removal. */
    private static void corpses(CommandContext c, Store<EntityStore> store, String[] args) {
        ExtractorConfig config = Corpses.config();
        if (args.length > 0 && args[0] != null && args[0].equalsIgnoreCase("clear")) {
            List<Ref<EntityStore>> all = Corpses.LEDGER.releaseAll();
            for (Ref<EntityStore> body : all) {
                Corpses.setTimer(store, body, config.extractedSeconds());
            }
            CoreModule.say(c, all.size() + " bodies set to vanish in " + config.extractedSeconds() + " s.");
            return;
        }
        int here = 0;
        if (c.isPlayer() && c.senderAsPlayerRef() != null) {
            TransformComponent transform = store.getComponent(c.senderAsPlayerRef(), TransformComponent.getComponentType());
            if (transform != null) {
                Vector3d at = transform.getPosition();
                here = Corpses.LEDGER.inChunk(ChunkUtil.indexChunkFromBlock(at.x, at.z), Corpses.GONE);
            }
        }
        java.util.Map<Integer, Integer> tens = new java.util.TreeMap<>();
        for (double left : Corpses.LEDGER.remainingSeconds(System.nanoTime(), Corpses.GONE)) {
            tens.merge(CorpseRules.nearestTen(left), 1, Integer::sum);
        }
        CoreModule.say(c, Corpses.LEDGER.size(Corpses.GONE) + " extended bodies; " + here + " in your chunk, " + Corpses.LEDGER.busiest(Corpses.GONE)
                + " in the busiest; " + Corpses.LEDGER.cutByCap() + " cut by the limit of " + config.maxCorpsesPerChunk()
                + " per chunk; seconds left (nearest ten): " + tens);
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
