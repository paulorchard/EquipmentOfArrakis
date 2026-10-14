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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/**
 * Interaction type {@code Arrakis_Exp_Beam_Spawn}: the laser of the Flame Beam Cutter, spawned by the server in world space for
 * every tick (see {@link ExpBeamGeometry}). It starts {@code Right}, {@code Up} and {@code Forward} blocks from the eye in the camera's frame
 * and ends where the look ray lands: at the apex distance, or where it enters a block when {@code EndAtBlock}. The vanilla NatureBeam
 * has a fixed length and its end burst sits {@code EndOffset} blocks in front of the system's origin, so the origin is put that far
 * before the end point; the system is turned to point along the line from the start to the end. It runs after the Mana tick in the chain,
 * so it only shows when the tick did not refuse.
 *
 * <pre>
 * {"Type": "Arrakis_Exp_Beam_Spawn", "System": "NatureBeam", "Right": 0.15, "Up": -0.15, "Forward": 0.6, "Apex": 5, "EndAtBlock": false,
 *  "EndOffset": 1.8, "Duration": 0.14, "Scale": 1}
 * </pre>
 */
public class ExpBeamSpawnInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Exp_Beam_Spawn";

    public static final BuilderCodec<ExpBeamSpawnInteraction> CODEC = BuilderCodec
            .builder(ExpBeamSpawnInteraction.class, ExpBeamSpawnInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("System", Codec.STRING, false), (i, v) -> i.system = v, i -> i.system)
            .add()
            .append(new KeyedCodec<>("Right", Codec.FLOAT, false), (i, v) -> i.right = v, i -> i.right)
            .add()
            .append(new KeyedCodec<>("Up", Codec.FLOAT, false), (i, v) -> i.up = v, i -> i.up)
            .add()
            .append(new KeyedCodec<>("Forward", Codec.FLOAT, false), (i, v) -> i.forward = v, i -> i.forward)
            .add()
            .append(new KeyedCodec<>("Apex", Codec.FLOAT, false), (i, v) -> i.apex = v, i -> i.apex)
            .add()
            .append(new KeyedCodec<>("EndAtBlock", Codec.BOOLEAN, false), (i, v) -> i.endAtBlock = v, i -> i.endAtBlock)
            .add()
            .append(new KeyedCodec<>("EndOffset", Codec.FLOAT, false), (i, v) -> i.endOffset = v, i -> i.endOffset)
            .add()
            .append(new KeyedCodec<>("Duration", Codec.FLOAT, false), (i, v) -> i.duration = v, i -> i.duration)
            .add()
            .append(new KeyedCodec<>("Scale", Codec.FLOAT, false), (i, v) -> i.scale = v, i -> i.scale)
            .add()
            .build();

    private String system = "NatureBeam";
    private float right = 0.15f;
    private float up = -0.15f;
    private float forward = 0.6f;
    private float apex = 5f;
    private boolean endAtBlock;
    private float endOffset = 1.8f;
    private float duration = 0.14f;
    private float scale = 1f;

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> player = context.getEntity();
        if (buffer == null || player == null) {
            return;
        }
        World world = buffer.getExternalData().getWorld();
        ExpBeamGeometry.Plan plan = ExpBeamGeometry.plan(player, buffer, right, up, forward, apex, endAtBlock);
        Vector3d origin = new Vector3d(plan.end).fma(-endOffset, plan.direction);
        ExpBeamGeometry.spawn(world, system, origin, plan, scale, duration);
    }
}
