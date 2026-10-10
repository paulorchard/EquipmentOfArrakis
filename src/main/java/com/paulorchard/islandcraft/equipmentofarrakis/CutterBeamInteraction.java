package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Interaction type {@code Arrakis_Cutter_Beam}: one tick of the Cutter Ray. The engine runs it again every
 * {@code RunTime} seconds for as long as Primary is held, so a tick does everything: pays for the tick out of
 * {@link ArrakisPower}, finds the first block on the look ray within {@code Range}, damages it, and draws the beam.
 * When there is no power left the tick fails, which draws nothing and does nothing.
 *
 * <p>Block damage goes through {@link BlockHarvestUtils#performBlockDamage}, the same call a pickaxe swing makes,
 * with an unbreakable stack of {@code ToolItemId} standing in for the held item: the block's hardness, the tool's
 * gather-type powers and qualities, drops, sounds and the damage event all behave exactly as for that pickaxe. The
 * stack has no durability, so nothing is worn out. {@code DamageScale} is the share of a pickaxe swing one tick is
 * worth (a swing takes about 0.4167 s, a tick 0.1 s: 0.24).
 *
 * <pre>
 * {"Type": "Arrakis_Cutter_Beam", "RunTime": 0.1, "ToolItemId": "Tool_Pickaxe_Mithril", "Range": 5,
 *  "PowerPerSecond": 1, "DamageScale": 0.24}
 * </pre>
 */
public class CutterBeamInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Cutter_Beam";

    /** The tool id the block asset's gathering data is looked up with: what the vanilla pickaxe breaks with. */
    private static final String TOOL_ID = "Pickaxe";
    /** Distance between the glowing dots that make up the beam, in blocks. */
    private static final double DOT_SPACING = 0.12;

    public static final BuilderCodec<CutterBeamInteraction> CODEC = BuilderCodec
            .builder(CutterBeamInteraction.class, CutterBeamInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("ToolItemId", Codec.STRING, false), (i, v) -> i.toolItemId = v, i -> i.toolItemId)
            .add()
            .append(new KeyedCodec<>("Range", Codec.FLOAT, false), (i, v) -> i.range = v, i -> i.range)
            .add()
            .append(new KeyedCodec<>("PowerPerSecond", Codec.FLOAT, false), (i, v) -> i.powerPerSecond = v,
                    i -> i.powerPerSecond)
            .add()
            .append(new KeyedCodec<>("DamageScale", Codec.FLOAT, false), (i, v) -> i.damageScale = v,
                    i -> i.damageScale)
            .add()
            .append(new KeyedCodec<>("BeamParticleId", Codec.STRING, false), (i, v) -> i.beamParticleId = v,
                    i -> i.beamParticleId)
            .add()
            .build();

    private String toolItemId = "Tool_Pickaxe_Mithril";
    private float range = 5f;
    private float powerPerSecond = 1f;
    private float damageScale = 0.24f;
    private String beamParticleId = "Arrakis_Cutter_Beam";

    /** Built on first use: the stack needs the item assets, which are not loaded when the codec is. */
    private transient ItemStack toolStack;

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        if (buffer == null || ref == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!ArrakisPower.spend(buffer, ref, powerPerSecond * getRunTime())) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        World world = buffer.getExternalData().getWorld();
        Transform look = TargetUtil.getLook(ref, buffer);
        Vector3d eye = look.getPosition();
        Vector3d dir = look.getDirection().normalize();

        double length = range;
        Vector3i target = TargetUtil.getTargetBlock(ref, range, buffer);
        if (target != null) {
            length = Math.min(range, BeamMath.entryDistance(eye, dir, target));
            damage(world, buffer, ref, target);
        }
        drawBeam(buffer, eye, dir, length);
    }

    private void damage(World world, CommandBuffer<EntityStore> buffer, Ref<EntityStore> ref, Vector3i target) {
        ItemStack tool = tool();
        if (tool == null) {
            return;
        }
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(target.x, target.y, target.z);
        if (section == null || !section.isValid()) {
            return;
        }
        Player player = buffer.getComponent(ref, Player.getComponentType());
        if (player != null && player.getGameMode() == GameMode.Creative) {
            BlockHarvestUtils.performBlockBreak(ref, tool, target, 4096, section, buffer, chunks.getStore());
        } else {
            BlockHarvestUtils.performBlockDamage(ref, ref, target, tool, null, TOOL_ID, false, damageScale, 0, false,
                    section, buffer, chunks.getStore());
        }
    }

    private ItemStack tool() {
        if (toolStack == null) {
            try {
                // Max durability 0 is "unbreakable": the damage code then skips the durability loss, which would
                // otherwise be written into the player's held item.
                toolStack = new ItemStack(toolItemId, 1).withMaxDurability(0);
            } catch (RuntimeException e) {
                EquipmentOfArrakisPlugin.get().getLogger().atWarning().log("Cutter Ray: no item %s", toolItemId);
                return null;
            }
        }
        return toolStack;
    }

    /** Dots from just in front of the muzzle to the end of the beam, as a solid red line. */
    private void drawBeam(CommandBuffer<EntityStore> buffer, Vector3d eye, Vector3d dir, double length) {
        // Right-hand vector (forward x up), so the beam leaves from the gun held at the right hip, not from the eye.
        Vector3d right = new Vector3d(-dir.z, 0, dir.x);
        if (right.lengthSquared() > 1e-6) {
            right.normalize();
        }
        Vector3d muzzle = new Vector3d(eye).fma(0.7, dir).fma(0.22, right).add(0, -0.22, 0);
        Vector3d end = new Vector3d(eye).fma(length, dir);
        Vector3d along = new Vector3d(end).sub(muzzle);
        double beam = along.length();
        if (beam < 0.1) {
            return;
        }
        along.div(beam);
        for (double d = 0; d <= beam; d += DOT_SPACING) {
            ParticleUtil.spawnParticleEffect(beamParticleId, new Vector3d(muzzle).fma(d, along), buffer);
        }
    }
}
