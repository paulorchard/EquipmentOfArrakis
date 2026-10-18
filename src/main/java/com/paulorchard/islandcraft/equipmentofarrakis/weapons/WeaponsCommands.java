package com.paulorchard.islandcraft.equipmentofarrakis.weapons;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CommandRoot;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreModule;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToggleInteraction;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToggleRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldPositional;

/**
 * <pre>
 * /arrakis weapons give            the ten weapons: the hotbar if there is room, else the inventory
 * /arrakis weapons state           the held item, whether it has Ability4 (the H key) and the Holtzman toggle
 * /arrakis weapons trace [on|off]  log each Ability4 press that reaches the server (default on)
 * </pre>
 */
final class WeaponsCommands {

    private static final String LANG = CommandRoot.LANG + "weapons.";

    private WeaponsCommands() {
    }

    static AbstractCommandCollection build() {
        AbstractCommandCollection weapons = new AbstractCommandCollection("weapons", LANG + "desc") {
        };
        weapons.addSubCommand(WorldPositional.build("give", LANG + "give.desc", (c, w, s, a) -> player(c, ref -> give(c, s, ref))));
        weapons.addSubCommand(WorldPositional.build("state", LANG + "state.desc", (c, w, s, a) -> player(c, ref -> state(c, s, ref))));
        weapons.addSubCommand(WorldPositional.build("trace", LANG + "trace.desc", (c, w, s, a) -> {
            ToggleInteraction.trace = a.length > 0 ? "on".equalsIgnoreCase(a[0]) : !ToggleInteraction.trace;
            CoreModule.say(c, "Ability4 trace " + (ToggleInteraction.trace ? "ON: one log line per press that arrives" : "off"));
        }, "onoff"));
        return weapons;
    }

    private static void give(CommandContext c, Store<EntityStore> store, Ref<EntityStore> ref) {
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        for (String id : WeaponIds.all()) {
            ItemStack rest = hotbar.getInventory().addItemStack(new ItemStack(id, 1)).getRemainder();
            if (rest != null && !rest.isEmpty()) {
                rest = storage.getInventory().addItemStack(rest).getRemainder();
            }
            CoreModule.say(c, (rest == null || rest.isEmpty() ? "gave " : "NO ROOM for ") + id);
        }
    }

    private static void state(CommandContext c, Store<EntityStore> store, Ref<EntityStore> ref) {
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        ItemStack held = hotbar.getActiveItem();
        if (held == null || held.isEmpty()) {
            CoreModule.say(c, "held, hotbar slot " + (hotbar.getActiveSlot() + 1) + ": (empty hand): no Ability4, H does nothing");
        } else {
            String ability4 = held.getItem() == null ? null : held.getItem().getInteractions().get(InteractionType.Ability4);
            CoreModule.say(c, "held, hotbar slot " + (hotbar.getActiveSlot() + 1) + ": " + held.getItemId() + ", Ability4 "
                    + (ability4 == null ? "none: H does nothing" : ability4 + ": H toggles"));
        }
        ToggleRegistry.Toggle toggle = ToggleRegistry.get().find("Holtzman");
        CoreModule.say(c, "toggle Holtzman: " + (toggle == null ? "not registered (the shield module is off)" : toggle.isOn(player.getUuid()) ? "ON" : "OFF")
                + ", trace " + (ToggleInteraction.trace ? "on" : "off"));
    }

    private static void player(CommandContext c, Action action) {
        if (!c.isPlayer()) {
            CoreModule.say(c, "Run this as a player in the game.");
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
}
