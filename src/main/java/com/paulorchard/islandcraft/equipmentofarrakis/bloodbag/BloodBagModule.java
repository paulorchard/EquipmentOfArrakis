package com.paulorchard.islandcraft.equipmentofarrakis.bloodbag;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CommandRoot;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ContainerInventory;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ContainerItem;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.EquipmentModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.GiveRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleAssets;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ModuleContext;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldPositional;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Blood Bag: a fillable item (like the LitreJon) holding blood in the stack metadata {@code Arrakis_Blood_Units}, shown on the
 * durability bar. It does nothing on its own, is not in the picker, and is a clean API ({@link BloodBag}) for a later purifier.
 * Operator helper: {@code /arrakis blood add <n>} fills the first bag with room.
 */
public final class BloodBagModule implements EquipmentModule {

    public static final String ID = "bloodbag";
    public static final String ITEM = "Arrakis_Blood_Bag";
    public static final String KEY = "Arrakis_Blood_Units";
    private static final int ORDER = 80;

    private static BloodBagConfig config = new BloodBagConfig();
    private static final ContainerItem CONTAINER =
            new ContainerItem(ITEM, KEY, () -> config.capacity(), () -> config.startsFull());

    private final ModuleAssets.Guard guard;

    public BloodBagModule(JavaPlugin plugin) {
        guard = ModuleAssets.guard(plugin, ID, id -> id.equals(ITEM) || id.startsWith("Arrakis_Bloodbag_"));
    }

    static ContainerItem container() {
        return CONTAINER;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void setup(ModuleContext ctx) {
        guard.activate();
        config = ctx.settings(ID, BloodBagConfig.class);
        ContainerInventory.register(CONTAINER);
        GiveRegistry.get().add(ITEM, ORDER, CONTAINER::fresh);
        String lang = CommandRoot.LANG + "blood.";
        ctx.group("blood").addSubCommand(WorldPositional.build("add", lang + "add.desc", (c, w, s, a) -> add(c, s, a), "amount"));
    }

    private static void add(CommandContext c, Store<EntityStore> store, String[] args) {
        if (!c.isPlayer() || c.senderAsPlayerRef() == null) {
            CoreModule.say(c, "Run this as a player in the game.");
            return;
        }
        Ref<EntityStore> ref = c.senderAsPlayerRef();
        int amount;
        try {
            amount = Integer.parseInt(args.length > 0 ? args[0] : "");
        } catch (NumberFormatException e) {
            CoreModule.say(c, "Usage: /arrakis blood add <n>");
            return;
        }
        int added = ContainerInventory.addToFirst(store, ref, CONTAINER, amount);
        CoreModule.say(c, added == 0 ? "No Blood Bag with room (or nothing to add)." : "Added " + added + " blood to the first bag with room.");
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
            boolean problem = !inTab || item.hasRecipesToGenerate() || item.getMaxStack() != 1 || item.getMaxDurability() <= 0;
            lines.add((problem ? "PROBLEM " : "") + "item " + ITEM + " loaded, categories " + Arrays.toString(categories)
                    + ", max durability " + item.getMaxDurability() + " (the fullness bar), max stack " + item.getMaxStack()
                    + ", interactions " + item.getInteractions());
        }
        lines.add(Interaction.getAssetMap().getAsset("Arrakis_Bloodbag_Nothing") == null
                ? "PROBLEM interaction Arrakis_Bloodbag_Nothing is not loaded" : "interaction Arrakis_Bloodbag_Nothing loaded");
        lines.add(RootInteraction.getAssetMap().getAsset("Arrakis_Bloodbag_None") == null
                ? "PROBLEM root interaction Arrakis_Bloodbag_None is not loaded" : "root interaction Arrakis_Bloodbag_None loaded");
        return lines;
    }
}
