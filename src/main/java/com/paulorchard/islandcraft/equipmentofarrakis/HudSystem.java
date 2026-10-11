package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Runs every world tick for every player: makes sure the {@link EquipmentHud} exists (a HUD that is gone, on the first tick, after
 * a respawn or a change of world, is added again, as the Water mod does), and keeps POWER, AMMO and the tool panel up to date,
 * sending only what changed (POWER at most {@code PowerUpdatesPerSecond} times a second).
 *
 * <p>"A tool is in use" is read straight from the player's interaction manager: a chain of type Secondary that is still running
 * ({@code NotFinished}) whose root interaction is the Secondary root of one of the tools in {@code ToolItems}, while that tool is the
 * item in the utility slot or the main hand and the player is alive. For the Compactor that is the whole of the held right click,
 * aim pose included; no tool asset had to change, and there is no timer to wait out, so the panel goes at once when the button is
 * released, the tool is swapped, the player dies, or the chain is cancelled. (The alternative, an entity effect refreshed by each tool,
 * would have needed every tool's chain edited and lapses only after its Duration.)
 */
final class HudSystem extends EntityTickingSystem<EntityStore> {

    /** What the system remembers about one player. */
    private static final class PlayerHudState {
        EquipmentHud hud;
        long lastPowerSend;
        final HudMath.ToolPanel panel = new HudMath.ToolPanel();
        String rigTool;
        long rigUntil;
    }

    private static final Map<UUID, PlayerHudState> STATES = new ConcurrentHashMap<>();

    private final Supplier<HudSettings> settings;
    private Query<EntityStore> query;
    /** Secondary root id to tool item id, built from the settings' tool list when it changes. */
    private String[] rootsFor;
    private Map<String, String> toolByRoot = Map.of();

    HudSystem(Supplier<HudSettings> settings) {
        this.settings = settings;
    }

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(Player.getComponentType(), PlayerRef.getComponentType());
        }
        return query;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    static void forget(UUID player) {
        STATES.remove(player);
        AmmoReadout.forget(player);
    }

    /** Development: shows the tool panel for this tool for {@code seconds}, whether or not it is in use. */
    static void rigTool(UUID player, String itemId, double seconds) {
        PlayerHudState state = STATES.computeIfAbsent(player, id -> new PlayerHudState());
        state.rigTool = itemId;
        state.rigUntil = System.nanoTime() + (long) (seconds * 1e9);
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        Player player = chunk.getComponent(index, Player.getComponentType());
        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        HudSettings cfg = settings.get();
        PlayerHudState state = STATES.computeIfAbsent(playerRef.getUuid(), id -> new PlayerHudState());
        Ref<EntityStore> ref = chunk.getReferenceTo(index);

        // A HUD that is gone (first tick, respawn, world change) or built for old settings is added again.
        var huds = player.getHudManager();
        if (state.hud == null || huds.getCustomHud(EquipmentHud.KEY) != state.hud || state.hud.generation != HudSettings.generation()) {
            if (huds.getCustomHud(EquipmentHud.KEY) != null) {
                huds.removeCustomHud(playerRef, EquipmentHud.KEY);
            }
            state.hud = new EquipmentHud(playerRef, cfg);
            state.panel.forget();
            state.lastPowerSend = 0;
            huds.addCustomHud(playerRef, state.hud);
        }

        long now = System.nanoTime();
        float power = ArrakisPower.get(store, ref);
        int shown = HudMath.powerDisplay(power < 0 ? 0 : power);
        boolean low = HudMath.isLow(shown, cfg.getLowPowerThreshold());
        // Power is sent at most PowerUpdatesPerSecond times a second; the first send of a new document is always due.
        boolean powerDue = HudMath.due(state.lastPowerSend, now, cfg.getPowerUpdatesPerSecond());
        OptionalInt ammo = AmmoReadout.read(playerRef.getUuid(), now);
        if (ammo.isEmpty() && cfg.getAmmoDefault() >= 0) {
            ammo = OptionalInt.of((int) Math.round(cfg.getAmmoDefault()));
        }
        String tool = state.rigTool != null && now < state.rigUntil ? state.rigTool : toolInUse(store, ref, cfg);
        if (state.rigTool != null && now >= state.rigUntil) {
            state.rigTool = null;
        }
        state.panel.update(tool);
        if (state.hud.refresh(shown, low, powerDue, HudMath.ammoText(ammo, cfg.getAmmoPlaceholder()), state.panel.shown())) {
            state.lastPowerSend = now;
        }
    }

    /** The item id of the off-hand tool whose Secondary chain is running for this player, or null. */
    private String toolInUse(Store<EntityStore> store, Ref<EntityStore> ref, HudSettings cfg) {
        if (store.getComponent(ref, DeathComponent.getComponentType()) != null) {
            return null;
        }
        InteractionManager manager = store.getComponent(ref, InteractionModule.get().getInteractionManagerComponent());
        if (manager == null || manager.getChains().isEmpty()) {
            return null;
        }
        Map<String, String> roots = roots(cfg);
        for (InteractionChain chain : manager.getChains().values()) {
            if (chain.getType() != InteractionType.Secondary || chain.getServerState() != InteractionState.NotFinished
                    || chain.getRootInteraction() == null) {
                continue;
            }
            String item = roots.get(chain.getRootInteraction().getId());
            if (item != null && holds(store, ref, item)) {
                return item;
            }
        }
        return null;
    }

    private Map<String, String> roots(HudSettings cfg) {
        String[] items = cfg.getToolItems();
        if (items != rootsFor) {
            Map<String, String> map = new HashMap<>();
            for (String id : items) {
                Item item = Item.getAssetMap().getAsset(id);
                String root = item == null ? null : item.getInteractions().get(InteractionType.Secondary);
                if (root != null) {
                    map.put(root, id);
                }
            }
            toolByRoot = map;
            rootsFor = items;
        }
        return toolByRoot;
    }

    /** True when the item is the active item of the utility slot or of the hotbar (the off hand or the main hand). */
    private static boolean holds(Store<EntityStore> store, Ref<EntityStore> ref, String itemId) {
        InventoryComponent.Utility utility = store.getComponent(ref, InventoryComponent.Utility.getComponentType());
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        return is(utility == null ? null : utility.getActiveItem(), itemId) || is(hotbar == null ? null : hotbar.getActiveItem(), itemId);
    }

    private static boolean is(ItemStack stack, String itemId) {
        return stack != null && itemId.equals(stack.getItemId());
    }
}
