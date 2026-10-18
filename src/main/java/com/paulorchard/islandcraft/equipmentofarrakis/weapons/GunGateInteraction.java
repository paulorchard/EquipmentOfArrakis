package com.paulorchard.islandcraft.equipmentofarrakis.weapons;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Interaction type {@code Arrakis_Gun_Gate}: the only interaction of a dart gun's Primary root, run once for every click that gets past
 * the cooldown. It asks {@link GunCost#charge} for {@code Cost}; with 0 (now) that always passes and the interaction named by
 * {@code Next} (the shot) runs after it. If the cost cannot be paid it ends as Failed and {@code Failed} (the vanilla
 * {@code Gun_Shoot_Fail}, the dry click) runs instead. The engine turns {@code Next} and {@code Failed} of a simple interaction into
 * the two branches of the chain; like the vanilla {@code ModifyInventory} this asks the client to wait for the server's answer.
 *
 * <pre>
 * {"Type": "Arrakis_Gun_Gate", "Cost": 0, "Next": "Arrakis_Weapon_Maula_Shoot", "Failed": "Gun_Shoot_Fail"}
 * </pre>
 */
public class GunGateInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Gun_Gate";

    public static final BuilderCodec<GunGateInteraction> CODEC = BuilderCodec
            .builder(GunGateInteraction.class, GunGateInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("Cost", Codec.FLOAT, false), (i, v) -> i.cost = v, i -> i.cost)
            .add()
            .build();

    /** What each shot costs; see {@link GunCost}. */
    private float cost;

    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        if (buffer == null || ref == null || !GunCost.charge(buffer, ref, cost)) {
            context.getState().state = InteractionState.Failed;
        }
    }
}
