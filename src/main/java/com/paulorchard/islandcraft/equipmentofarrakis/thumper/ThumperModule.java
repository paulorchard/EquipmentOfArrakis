package com.paulorchard.islandcraft.equipmentofarrakis.thumper;

import com.hypixel.hytale.builtin.deployables.config.DeployableConfig;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleAssets;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;
import com.paulorchard.islandcraft.equipmentofarrakis.core.worms.WormsLink;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The Thumper: a consumable item (the vanilla Temple Emerald torch, unlit) that, on a right click on sand, stands as an invulnerable
 * deployable at the player's feet and thumps softly twice a second-cycle, drawing the sandworm of the Worms of Arrakis mod, which treats
 * the deployable id as a totem (this module registers it, see {@link WormsLink}). Without Worms it places, thumps and stands.
 * {@code /arrakis thumper give|state|clear} are the operator helpers.
 */
public final class ThumperModule implements EquipmentModule {

    public static final String ID = "thumper";
    public static final String ITEM = Thumpers.ID;
    private static final long MAINTAIN_SECONDS = 5;

    private final ModuleAssets.Guard guard;
    private ScheduledFuture<?> maintain;

    public ThumperModule(JavaPlugin plugin) {
        plugin.getCodecRegistry(Interaction.CODEC).register(ThumperGateInteraction.TYPE, ThumperGateInteraction.class, ThumperGateInteraction.CODEC);
        guard = ModuleAssets.guard(plugin, ID, id -> id.equals(ITEM) || id.startsWith("Arrakis_Thumper_") || id.equals(Thumpers.THUMP_SOUND));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void setup(ModuleContext ctx) {
        guard.activate();
        Thumpers.configure(ctx.settings(ID, ThumperConfig.class));
        Thumpers.logger(ctx.logger());
        ctx.events().registerGlobal(BootEvent.class, event -> {
            ThumperSetup.apply(Thumpers.config());
            ensureTotem();
        });
        ctx.system(new ThumperSystem());
        ThumperCommands.register(ctx.group("thumper"));
    }

    @Override
    public void start(ModuleContext ctx) {
        ensureTotem();
        if (!WormsLink.pluginLoaded()) {
            Thumpers.log("Thumper: Worms of Arrakis is not loaded: the Thumper places, thumps and stands until it is destroyed, and no worm comes. "
                    + "If Worms loads later it is linked then.");
        }
        // Load order is not assumed and Worms can be reloaded (a new, empty totem list): look again every few seconds.
        maintain = HytaleServer.SCHEDULED_EXECUTOR.scheduleWithFixedDelay(this::ensureTotem, MAINTAIN_SECONDS, MAINTAIN_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void stop(ModuleContext ctx) {
        if (maintain != null) {
            maintain.cancel(false);
        }
        WormsLink.releaseTotem(Thumpers.ID);
        Thumpers.book().clear();
    }

    private void ensureTotem() {
        try {
            String changed = WormsLink.ensureTotem(Thumpers.ID);
            if (changed != null) {
                Thumpers.log("Thumper: " + changed);
            }
        } catch (RuntimeException e) {
            Thumpers.log("PROBLEM Thumper: the Worms link failed: " + e);
        }
    }

    @Override
    public List<String> selfCheck() {
        List<String> lines = new ArrayList<>();
        Item item = Item.getAssetMap().getAsset(ITEM);
        if (item == null) {
            lines.add("PROBLEM the item " + ITEM + " is not loaded");
        } else {
            String[] categories = item.getCategories();
            boolean inTab = categories != null && Arrays.asList(categories).contains("IslandCraft.Tools");
            boolean problem = !inTab || item.hasRecipesToGenerate() || item.getMaxDurability() > 0 || item.getMaxStack() != 16 || item.getIcon() == null;
            lines.add((problem ? "PROBLEM " : "") + "item " + ITEM + " loaded, categories " + Arrays.toString(categories) + ", max stack " + item.getMaxStack()
                    + ", max durability " + item.getMaxDurability() + ", icon " + item.getIcon() + ", animations " + item.getPlayerAnimationsId()
                    + ", block type " + (item.hasBlockType() ? "yes (the held model)" : "NO") + ", interactions " + item.getInteractions());
        }
        for (String id : new String[] {"Arrakis_Thumper_Gate", ThumperSetup.PLACE, "Arrakis_Thumper_Consume"}) {
            lines.add(Interaction.getAssetMap().getAsset(id) == null ? "PROBLEM interaction " + id + " is not loaded" : "interaction " + id + " loaded");
        }
        lines.add(RootInteraction.getAssetMap().getAsset("Arrakis_Thumper_Secondary") == null
                ? "PROBLEM root interaction Arrakis_Thumper_Secondary is not loaded" : "root interaction Arrakis_Thumper_Secondary loaded");
        lines.add(ModelAsset.getAssetMap().getAsset(Thumpers.ID) == null ? "PROBLEM model asset " + Thumpers.ID + " is not loaded" : "model asset " + Thumpers.ID + " loaded");
        SoundEvent sound = SoundEvent.getAssetMap().getAsset(Thumpers.THUMP_SOUND);
        lines.add(sound == null ? "PROBLEM sound event " + Thumpers.THUMP_SOUND + " is not loaded"
                : "sound event " + Thumpers.THUMP_SOUND + " loaded: " + ThumperSetup.describe(sound) + ", max distance "
                        + sound.getMaxDistance() + " blocks, max instances " + sound.getMaxInstance());
        lines.add(deployableLine());
        lines.add("sand blocks in use: " + new ArrayList<>(Thumpers.sand()));
        lines.add("Worms: " + WormsLink.status(Thumpers.ID));
        return lines;
    }

    private static String deployableLine() {
        Interaction place = Interaction.getAssetMap().getAsset(ThumperSetup.PLACE);
        if (place == null) {
            return "PROBLEM the deployable (in Arrakis_Thumper_Place) is not loaded";
        }
        try {
            Field field = place.getClass().getDeclaredField("config");
            field.setAccessible(true);
            DeployableConfig config = (DeployableConfig) field.get(place);
            boolean problem = config == null || !Thumpers.ID.equals(config.getId()) || !config.getInvulnerable() || config.getModel() == null;
            return (problem ? "PROBLEM " : "") + "deployable " + (config == null ? "?" : config.getId()) + ": invulnerable " + (config != null && config.getInvulnerable())
                    + ", max live " + (config == null ? "?" : config.getMaxLiveCount()) + ", live duration " + (config == null ? "?" : config.getLiveDuration())
                    + " s (0 = never), model " + (config == null || config.getModel() == null ? "NONE" : config.getModel().getModelAssetId());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "PROBLEM the deployable config could not be read: " + e;
        }
    }
}
