// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
package com.paulorchard.islandcraft.equipmentofarrakis.experiments;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Interaction type {@code Arrakis_Exp_Mana_Gate}: the Mana check as an interaction of its own, for a tool whose mining is done
 * by the engine's data-only interactions (the Drill Cutter), where there is no tick of ours to pay in. It goes first in the
 * root interaction: it takes {@code ManaPerSecond * Seconds} (Creative takes nothing) and lets the chain go on, or ends as Failed,
 * which cancels the whole chain, so no animation, beam or mining ever starts. The refusal is silent.
 *
 * <pre>
 * {"Type": "Arrakis_Exp_Mana_Gate", "ManaPerSecond": 3.3, "Seconds": 0.3}
 * </pre>
 */
public class ExpManaGateInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Exp_Mana_Gate";

    public static final BuilderCodec<ExpManaGateInteraction> CODEC = BuilderCodec
            .builder(ExpManaGateInteraction.class, ExpManaGateInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("ManaPerSecond", Codec.FLOAT, false), (i, v) -> i.manaPerSecond = v, i -> i.manaPerSecond)
            .add()
            .append(new KeyedCodec<>("Seconds", Codec.FLOAT, false), (i, v) -> i.seconds = v, i -> i.seconds)
            .add()
            .build();

    private float manaPerSecond = 3.3f;
    /** The time one run of the chain stands for: the cooldown of the root interaction. */
    private float seconds = 0.3f;

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> player = context.getEntity();
        if (buffer == null || player == null) {
            ExpManaGate.refuse(context);
            return;
        }
        if (!ExpManaGate.pay(buffer, player, ExpManaGate.cost(manaPerSecond, seconds))) {
            ExpManaGate.refuse(context);
        }
    }
}
