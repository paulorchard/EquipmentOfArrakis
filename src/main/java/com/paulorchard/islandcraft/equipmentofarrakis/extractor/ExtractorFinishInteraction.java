package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ContainerInventory;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ContainerItem;
import com.paulorchard.islandcraft.equipmentofarrakis.core.Notice;

/**
 * Interaction type {@code Arrakis_Extractor_Finish}: last in the Blood Extractor Primary chain, after the poke. Only reached when the
 * poke ran to its end: lets the crouch go and adds {@code BloodPerBody} blood to the first Blood Bag with room. The corpse stays
 * used, and goes in {@code ExtractedSeconds} (the blood really added is what shortens its timer; a refusal or a full bag leaves the timer alone).
 */
public class ExtractorFinishInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Extractor_Finish";

    public static final BuilderCodec<ExtractorFinishInteraction> CODEC = BuilderCodec
            .builder(ExtractorFinishInteraction.class, ExtractorFinishInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("BagItemId", Codec.STRING, false), (i, v) -> i.bagItemId = v, i -> i.bagItemId)
            .add()
            .build();

    private String bagItemId = "Arrakis_Blood_Bag";

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        PlayerRef player = buffer == null || ref == null ? null : buffer.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        ExtractorState.Pending pending = ExtractorState.take(player.getUuid());
        if (pending == null) {
            return;
        }
        if (pending.forceCrouch()) {
            ExtractorState.releaseCrouch(buffer, ref, pending.wasCrouching());
        }
        ContainerItem bag = ContainerInventory.find(bagItemId);
        int added = bag == null ? 0 : ContainerInventory.addToFirst(buffer, ref, bag, pending.blood());
        if (added <= 0) {
            Notice.say(player, "server.arrakis.extractor.bagsFull");
            return;
        }
        // The blood is in the bag: the body vanishes promptly (a body that is already gone is skipped).
        Corpses.quickRemoval(buffer, pending.corpse());
    }
}
