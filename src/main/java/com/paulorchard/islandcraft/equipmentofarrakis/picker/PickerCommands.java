package com.paulorchard.islandcraft.equipmentofarrakis.picker;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CommandRoot;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.GiveRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToggleRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldPositional;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;

/**
 * <pre>
 * /arrakis tools give    every tool and the Blood Bag: the hotbar if there is room, else the inventory
 * /arrakis tools state   the held item, the tools found with container and slot, the toggles, Mana
 * /arrakis tools open    the picker page without right-clicking
 * </pre>
 */
final class PickerCommands {

    private static final String LANG = CommandRoot.LANG + "tools.";

    private PickerCommands() {
    }

    static AbstractCommandCollection build() {
        AbstractCommandCollection tools = new AbstractCommandCollection("tools", LANG + "desc") {
        };
        tools.addSubCommand(WorldPositional.build("give", LANG + "give.desc", (c, w, s, a) -> player(c, ref -> give(c, s, ref))));
        tools.addSubCommand(WorldPositional.build("state", LANG + "state.desc", (c, w, s, a) -> player(c, ref -> state(c, s, ref))));
        tools.addSubCommand(WorldPositional.build("open", LANG + "open.desc", (c, w, s, a) -> player(c, ref -> {
            Player player = s.getComponent(ref, Player.getComponentType());
            PlayerRef playerRef = s.getComponent(ref, PlayerRef.getComponentType());
            player.getPageManager().openCustomPage(ref, s, new ToolPicker(playerRef));
        })));
        return tools;
    }

    private static void give(CommandContext c, Store<EntityStore> store, Ref<EntityStore> ref) {
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        for (GiveRegistry.Entry entry : GiveRegistry.get().all()) {
            ItemStack rest = hotbar.getInventory().addItemStack(entry.make()).getRemainder();
            if (rest != null && !rest.isEmpty()) {
                rest = storage.getInventory().addItemStack(rest).getRemainder();
            }
            say(c, (rest == null || rest.isEmpty() ? "gave " : "NO ROOM for ") + entry.itemId());
        }
    }

    private static void state(CommandContext c, Store<EntityStore> store, Ref<EntityStore> ref) {
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        ItemStack held = hotbar.getActiveItem();
        say(c, "held, hotbar slot " + (hotbar.getActiveSlot() + 1) + ": " + (held == null || held.isEmpty() ? "(empty)" : held.getItemId()));
        for (ToolSwap.Found tool : ToolPicker.carried(ref, store)) {
            say(c, "  " + tool.itemId() + ": " + ToolSwap.where(tool.loc()) + " (slot " + tool.loc().slot() + ")");
        }
        for (ToggleRegistry.Toggle toggle : ToggleRegistry.get().all()) {
            say(c, "  toggle " + toggle.id() + ": " + (toggle.isOn(player.getUuid()) ? "ON" : "OFF"));
        }
        Power power = Power.get();
        if (power != null) {
            say(c, "  Mana " + Math.round(power.get(store, ref)) + " of " + Math.round(power.max(store, ref)));
        }
    }

    private static void player(CommandContext c, Action action) {
        if (!c.isPlayer()) {
            say(c, "Run this as a player in the game.");
            return;
        }
        Ref<EntityStore> ref = c.senderAsPlayerRef();
        if (ref != null) {
            action.run(ref);
        }
    }

    private interface Action {
        void run(Ref<EntityStore> ref);
    }

    private static void say(CommandContext c, String text) {
        CoreModule.say(c, text);
    }
}
