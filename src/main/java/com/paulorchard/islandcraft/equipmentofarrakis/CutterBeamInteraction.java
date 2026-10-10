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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Interaction type {@code Arrakis_Cutter_Beam}: one tick of the Cutter Ray. The engine runs it again every
 * {@code RunTime} seconds for as long as Primary is held, so a tick does everything: pays for the tick out of
 * {@link ArrakisPower}, finds the first block on the look ray within {@code Range}, damages it, and tells the weapon
 * model how long the beam is (see {@link BeamDisplay}). When there is no power left the tick fails, switches the
 * beam off and does nothing else.
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
            .build();

    private String toolItemId = "Tool_Pickaxe_Mithril";
    private float range = 5f;
    private float powerPerSecond = 1f;
    private float damageScale = 0.24f;

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
            BeamDisplay.hide(buffer, ref);
            context.getState().state = InteractionState.Failed;
            return;
        }

        World world = buffer.getExternalData().getWorld();
        Transform look = TargetUtil.getLook(ref, buffer);
        Vector3d eye = look.getPosition();
        Vector3d dir = look.getDirection().normalize();

        // With nothing in range the beam is full length; otherwise it reaches the surface it hits.
        double length = range;
        Vector3i target = TargetUtil.getTargetBlock(ref, range, buffer);
        if (target != null) {
            length = Math.min(range, BeamMath.entryDistance(eye, dir, target));
            damage(world, buffer, ref, target);
        }
        BeamDisplay.show(buffer, ref, BeamMath.stepForDistance(length));
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
}
