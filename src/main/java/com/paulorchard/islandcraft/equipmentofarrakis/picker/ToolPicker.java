package com.paulorchard.islandcraft.equipmentofarrakis.picker;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToggleRegistry;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * The tool picker page. Opened by right-clicking an Arrakis tool (the engine OpenCustomUI interaction with this page registered as
 * Arrakis_Picker, see PickerModule) or by /arrakis tools open. Lists the registered tools in the hotbar and the main inventory (not
 * the backpack, not the utility container), the one in the hand marked and not clickable, and always the registered toggles.
 * Clicking a tool swaps it into the hand (ToolSwap) and closes the page; clicking a toggle flips it and the page stays open.
 */
final class ToolPicker extends InteractiveCustomUIPage<ToolPicker.PageData> {

    static final String ID = "Arrakis_Picker";
    private static final String PAGE = "Pages/Equipment_of_Arrakis/Picker/Arrakis_Picker_Page.ui";
    private static final String ENTRY = "Pages/Equipment_of_Arrakis/Picker/Arrakis_Picker_Entry.ui";
    private static final String TOGGLE = "Pages/Equipment_of_Arrakis/Picker/Arrakis_Picker_Toggle.ui";
    private static final String LANG = "server.customUI.arrakisPicker.";

    public static class PageData {

        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING, false), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("Id", Codec.STRING, false), (d, v) -> d.id = v, d -> d.id)
                .add()
                .append(new KeyedCodec<>("Loc", Codec.STRING, false), (d, v) -> d.loc = v, d -> d.loc)
                .add()
                .build();

        public String action;
        public String id;
        public String loc;
    }

    private final PlayerRef player;

    ToolPicker(PlayerRef player) {
        super(player, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.player = player;
    }

    // ---- the inventory, as lists the pure rule understands

    static List<String> ids(ItemContainer container) {
        List<String> ids = new ArrayList<>();
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            ids.add(stack == null || stack.isEmpty() ? null : stack.getItemId());
        }
        return ids;
    }

    /** The registered tools the player carries, hotbar first. World thread. */
    static List<ToolSwap.Found> carried(Ref<EntityStore> ref, Store<EntityStore> store) {
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        if (hotbar == null || storage == null) {
            return List.of();
        }
        return ToolSwap.find(ids(hotbar.getInventory()), ids(storage.getInventory()), ToolRegistry.get()::isTool);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
        commands.append(PAGE);
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        List<ToolSwap.Found> found = new ArrayList<>(carried(ref, store));
        found.sort(java.util.Comparator.comparingInt(f -> ToolRegistry.get().find(f.itemId()).order()));
        int hand = hotbar == null ? -1 : hotbar.getActiveSlot();
        for (int i = 0; i < found.size(); i++) {
            ToolSwap.Found tool = found.get(i);
            String row = "#ToolList[" + i + "]";
            boolean inHand = tool.loc().where() == ToolSwap.Where.HOTBAR && tool.loc().slot() == hand;
            commands.append("#ToolList", ENTRY);
            commands.set(row + " #Icon.ItemId", tool.itemId());
            commands.set(row + " #Name.TextSpans", Message.translation(ToolRegistry.get().nameKey(tool.itemId())));
            commands.set(row + " #Where.TextSpans", Message.raw(ToolSwap.where(tool.loc())));
            commands.set(row + " #InHand.Visible", inHand);
            if (!inHand) {
                String loc = (tool.loc().where() == ToolSwap.Where.HOTBAR ? "H:" : "S:") + tool.loc().slot();
                events.addEventBinding(CustomUIEventBindingType.Activating, row,
                        new EventData().append("Action", "Pick").append("Id", tool.itemId()).append("Loc", loc));
            }
        }
        commands.set("#Only.Visible", found.size() <= 1);
        commands.set("#Only.TextSpans", Message.translation(LANG + "only"));
        List<ToggleRegistry.Toggle> toggles = ToggleRegistry.get().all();
        for (int i = 0; i < toggles.size(); i++) {
            ToggleRegistry.Toggle toggle = toggles.get(i);
            boolean on = toggle.isOn(player.getUuid());
            String row = "#PassiveList[" + i + "]";
            commands.append("#PassiveList", TOGGLE);
            commands.set(row + " #Icon.ItemId", toggle.iconItemId());
            commands.set(row + " #Name.TextSpans", Message.translation(toggle.labelKey()));
            commands.set(row + " #On.Visible", on);
            commands.set(row + " #Off.Visible", !on);
            events.addEventBinding(CustomUIEventBindingType.Activating, row, new EventData().append("Action", "Toggle").append("Id", toggle.id()));
        }
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PageData data) {
        if ("Toggle".equals(data.action)) {
            ToggleRegistry.Toggle toggle = ToggleRegistry.get().find(data.id);
            if (toggle != null) {
                ToggleRegistry.get().press(data.id, store, ref, player, System.nanoTime(), 0);
                player.sendMessage(Message.translation(toggle.labelKey()).insert(": " + (toggle.isOn(player.getUuid()) ? "ON" : "OFF")));
            }
            rebuild();
        } else if ("Pick".equals(data.action)) {
            pick(ref, store, data);
            close();
        }
    }

    private void pick(Ref<EntityStore> ref, Store<EntityStore> store, PageData data) {
        ToolSwap.Outcome outcome = swap(ref, store, parse(data.loc), data.id);
        switch (outcome) {
            case SWAPPED, ALREADY_IN_HAND -> { }
            case HAND_NOT_TOOL -> player.sendMessage(Message.translation(LANG + "handChanged"));
            case CHOSEN_GONE -> player.sendMessage(Message.translation(LANG + "toolGone"));
        }
    }

    private static ToolSwap.Loc parse(String text) {
        if (text == null || text.length() < 3) {
            return new ToolSwap.Loc(ToolSwap.Where.STORAGE, -1);
        }
        try {
            return new ToolSwap.Loc(text.startsWith("H") ? ToolSwap.Where.HOTBAR : ToolSwap.Where.STORAGE, Integer.parseInt(text.substring(2)));
        } catch (NumberFormatException e) {
            return new ToolSwap.Loc(ToolSwap.Where.STORAGE, -1);
        }
    }

    /**
     * The adapter: validates against the inventory as it is now (the pure plan), then does the one swap with ItemContainer.swapItems,
     * which moves the two stacks (metadata and all) in a single write action and reports one transaction. World thread only.
     */
    static ToolSwap.Outcome swap(Ref<EntityStore> ref, Store<EntityStore> store, ToolSwap.Loc chosen, String expectedId) {
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        ToolSwap.Plan plan = ToolSwap.plan(ids(hotbar.getInventory()), ids(storage.getInventory()), hotbar.getActiveSlot(), chosen, expectedId,
                ToolRegistry.get()::isTool);
        if (plan.outcome() == ToolSwap.Outcome.SWAPPED) {
            ItemContainer from = chosen.where() == ToolSwap.Where.HOTBAR ? hotbar.getInventory() : storage.getInventory();
            ListTransaction<?> done = from.swapItems((short) chosen.slot(), hotbar.getInventory(), (short) plan.handSlot(), (short) 1);
            if (!done.succeeded()) {
                PickerModule.logger().at(Level.WARNING).log("Picker: the swap of %s with hotbar slot %s FAILED: %s", chosen, plan.handSlot(), done);
            } else {
                PickerModule.logger().at(Level.INFO).log("Picker: swapped %s %s into hotbar slot %s", ToolSwap.where(chosen), expectedId, plan.handSlot());
            }
        } else {
            PickerModule.logger().at(Level.INFO).log("Picker: click on %s %s: %s", chosen, expectedId, plan.outcome());
        }
        return plan.outcome();
    }
}
