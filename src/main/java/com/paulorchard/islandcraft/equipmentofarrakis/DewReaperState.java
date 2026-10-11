package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the Dew Reaper remembers between the start of a swing ({@link DewReaperGateInteraction}) and its end
 * ({@link DewReaperFinishInteraction}), and the parts of it that touch the engine: the flask lookup, the chat messages, the hum
 * effect. A swing that is interrupted never reaches its end; {@link DewReaperSystem} then clears the memory and stops the hum.
 */
final class DewReaperState {

    static final String ROOT_ID = "Arrakis_Root_Dew_Reaper";
    /** The swing lasts 2.08333 s; a memory older than this has no swing behind it. */
    static final long STALE_NANOS = 3_500_000_000L;

    /** An accepted click: what to do at the end of the swing. */
    record Pending(DewReaperRules.Mode mode, Vector3i primrose, String primroseBlockId, double primroseWater, double airUnits,
            boolean showMessages, String humEffectId, long startNanos) {
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private DewReaperState() {
    }

    static void put(UUID player, Pending pending) {
        PENDING.put(player, pending);
    }

    static Pending get(UUID player) {
        return PENDING.get(player);
    }

    /** Takes the memory of this player's swing, or null when there is none. */
    static Pending take(UUID player) {
        return PENDING.remove(player);
    }

    static void forget(UUID player) {
        PENDING.remove(player);
    }

    // ---- the flask

    /** A Litrejon with room: the container and slot it sits in, and the stack. */
    record Flask(ItemContainer container, short slot, ItemStack stack) {
    }

    /** The first Litrejon with room, searching the hotbar, then the storage, then the backpack. Null when there is none. */
    static Flask findFlask(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, WaterApi water) {
        ItemContainer[] places = {
                container(accessor.getComponent(ref, InventoryComponent.Hotbar.getComponentType())),
                container(accessor.getComponent(ref, InventoryComponent.Storage.getComponentType())),
                container(accessor.getComponent(ref, InventoryComponent.Backpack.getComponentType())),
        };
        double[][] amounts = new double[places.length][];
        for (int p = 0; p < places.length; p++) {
            int size = places[p] == null ? 0 : places[p].getCapacity();
            amounts[p] = new double[size];
            for (int s = 0; s < size; s++) {
                ItemStack stack = places[p].getItemStack((short) s);
                amounts[p][s] = water.isLitrejon(stack) ? water.amount(stack) : -1;
            }
        }
        int[] hit = DewReaperRules.firstWithRoom(amounts, water.capacity());
        if (hit == null) {
            return null;
        }
        short slot = (short) hit[1];
        return new Flask(places[hit[0]], slot, places[hit[0]].getItemStack(slot));
    }

    private static ItemContainer container(InventoryComponent inventory) {
        return inventory == null ? null : inventory.getInventory();
    }

    // ---- messages and the hum

    static void say(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, boolean show, String key, Object... values) {
        if (!show) {
            return;
        }
        PlayerRef player = accessor.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        Message message = Message.translation("server.dewReaper." + key);
        for (int i = 0; i + 1 < values.length; i += 2) {
            message = message.param(String.valueOf(values[i]), String.valueOf(values[i + 1]));
        }
        player.sendMessage(message);
    }

    /** Applies the hum effect (for the length of its Duration). */
    static void startHum(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, String effectId) {
        EffectControllerComponent controller = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
        if (controller != null && effect != null) {
            controller.addEffect(ref, effect, accessor);
        }
    }

    /** Takes the hum effect off at once, which stops the loop. */
    static void stopHum(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, String effectId) {
        EffectControllerComponent controller = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        int index = EntityEffect.getAssetMap().getIndex(effectId);
        if (controller != null && index >= 0) {
            controller.removeEffect(ref, index, accessor);
        }
    }
}
