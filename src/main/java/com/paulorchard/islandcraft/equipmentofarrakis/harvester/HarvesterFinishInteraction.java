package com.paulorchard.islandcraft.equipmentofarrakis.harvester;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ContainerInventory;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ContainerItem;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldAccess;
import org.joml.Vector3i;

/**
 * Interaction type {@code Arrakis_Harvester_Finish}: last in the Dew Harvester Primary chain, after the swing. Only reached when the
 * swing ran to its end. If the Primrose is still where it was, it moves {@code WaterPerSwing} into the first LitreJon with room (hotbar,
 * then storage; the player own water is not touched, the Primrose is not changed) and takes the hum off.
 */
public class HarvesterFinishInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Harvester_Finish";

    public static final BuilderCodec<HarvesterFinishInteraction> CODEC = BuilderCodec
            .builder(HarvesterFinishInteraction.class, HarvesterFinishInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("FlaskItemId", Codec.STRING, false), (i, v) -> i.flaskItemId = v, i -> i.flaskItemId)
            .add()
            .build();

    private String flaskItemId = "Arrakis_Tool_Litrejon";

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        PlayerRef player = buffer == null || ref == null ? null : buffer.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        HarvesterState.Pending pending = HarvesterState.take(player.getUuid());
        if (pending == null) {
            return;
        }
        HarvesterState.stopHum(buffer, ref, pending.humEffectId());
        Vector3i at = pending.primrose();
        BlockType block = new WorldAccess(buffer.getExternalData().getWorld()).blockType(at.x, at.y, at.z);
        if (block == null || !pending.primroseBlockId().equals(block.getId())) {
            return;
        }
        ContainerItem flask = ContainerInventory.find(flaskItemId);
        if (flask != null) {
            ContainerInventory.addToFirst(buffer, ref, flask, pending.waterPerSwing());
        }
    }
}
