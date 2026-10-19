package com.paulorchard.islandcraft.equipmentofarrakis.thumper;

import com.hypixel.hytale.builtin.deployables.DeployablesUtils;
import com.hypixel.hytale.builtin.deployables.config.DeployableConfig;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/**
 * Interaction type {@code Arrakis_Thumper_Place}: second in the Thumper Secondary chain. Spawns the deployable of its {@code Config} (an
 * ordinary deployable config, the same one the vanilla spawn interactions take) at the spot the gate chose, through
 * {@link DeployablesUtils#spawnDeployable}, the call the vanilla {@code SpawnDeployableAtLocation} makes. The owner is the entity that
 * owns the interaction (the player), as in the vanilla one, the spawn face is "UP". With no spot from the gate nothing is spawned and the
 * chain fails, so nothing is consumed.
 */
public class ThumperPlaceInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Thumper_Place";

    public static final BuilderCodec<ThumperPlaceInteraction> CODEC = BuilderCodec
            .builder(ThumperPlaceInteraction.class, ThumperPlaceInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("Config", DeployableConfig.CODEC), (i, v) -> i.config = v, i -> i.config)
            .add()
            .build();

    private DeployableConfig config;

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> self = context.getEntity();
        PlayerRef player = buffer == null || self == null ? null : buffer.getComponent(self, PlayerRef.getComponentType());
        ThumperPending.Spot spot = player == null || config == null ? null : ThumperPending.take(player.getUuid(), System.nanoTime());
        if (spot == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> owner = context.getOwningEntity() != null ? context.getOwningEntity() : self;
        DeployablesUtils.spawnDeployable(buffer, buffer.getStore(), config, owner, new Vector3d(spot.x(), spot.y(), spot.z()), new Rotation3f(), "UP");
        Thumpers.log(String.format("Thumper spawned at %.2f %.2f %.2f for %s (%s)", spot.x(), spot.y(), spot.z(), player.getUuid(), spot.note()));
    }
}
