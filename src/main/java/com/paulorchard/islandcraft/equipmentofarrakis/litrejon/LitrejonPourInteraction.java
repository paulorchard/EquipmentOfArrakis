package com.paulorchard.islandcraft.equipmentofarrakis.litrejon;

import com.paulorchard.islandcraft.equipmentofarrakis.core.water.WaterApi;
import com.paulorchard.islandcraft.equipmentofarrakis.core.water.WaterLink;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ContainerItem;
import com.paulorchard.islandcraft.equipmentofarrakis.core.Notice;

/**
 * Interaction type {@code Arrakis_Litrejon_Pour}: the left click of the LitreJon. Moves water from the held flask into the player
 * own water, up to {@code PourPerClick} (50), and only what the player lacks. An empty flask or a full player gets a short message
 * and nothing happens. Without Water of Arrakis it says so. Held, it repeats at the interaction RunTime.
 */
public class LitrejonPourInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Litrejon_Pour";

    public static final BuilderCodec<LitrejonPourInteraction> CODEC = BuilderCodec
            .builder(LitrejonPourInteraction.class, LitrejonPourInteraction::new, SimpleInstantInteraction.CODEC)
            .build();

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        ContainerItem flask = LitrejonModule.container();
        ItemStack held = context.getHeldItem();
        PlayerRef player = buffer == null || ref == null ? null : buffer.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || !flask.isContainer(held)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        WaterApi water = WaterLink.api();
        if (water == null) {
            Notice.say(player, "server.arrakis.litrejon.needsWater");
            context.getState().state = InteractionState.Failed;
            return;
        }
        int inFlask = flask.getAmount(held);
        if (inFlask <= 0) {
            Notice.say(player, "server.arrakis.litrejon.empty");
            return;
        }
        double before = water.getWater(player);
        int units = LitrejonTransfer.units(inFlask, before, water.max(), LitrejonModule.config().pourPerClick());
        if (units <= 0) {
            Notice.say(player, "server.arrakis.litrejon.full");
            return;
        }
        double gained = water.addWater(player, units) - before;
        int cost = Math.min(inFlask, LitrejonTransfer.cost(gained));
        context.getHeldItemContainer().setItemStackForSlot(context.getHeldItemSlot(), flask.add(held, -cost));
    }
}
