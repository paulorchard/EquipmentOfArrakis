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
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Interaction type {@code Arrakis_Cutter_Mk2_Range_Gate}: the first interaction of the Primary chain of the Cutter Ray Mk2,
 * run once for every shot, before anything else. It does one of two things:
 * <ul>
 *   <li>Refuses the shot, by ending as Failed. The engine then cancels the whole chain (verified in the jar: a Failed state
 *       makes {@code Interaction.tick} throw {@code ChainCancelledException}), so the shot interaction after it never runs: no
 *       animation, no muzzle effect, no projectile, no camera shake. A shot is refused when there is no block within
 *       {@code Range} under the crosshair, or when there is no power left (that counts as use of power, as for the Cutter
 *       Ray, so holding the button on an empty pack does not recharge it; a refusal for range does not).</li>
 *   <li>Lets the shot through, having taken its power ({@code PowerPerSecond * FireInterval}, 0.05), damaged the block by
 *       {@code BlockDamage}, and spawned the sparks at the point where the look ray enters the block face.</li>
 * </ul>
 * The bullet itself is the vanilla gun's, launched by the interaction after this one; it does no damage and its death effect is
 * the smoke and the globs. Block damage and sparks are done here, at the moment of the shot, and not when the bullet arrives: the
 * legacy projectile of this server version has no hook for a hit, and it covers 5 blocks in 17 ms.
 *
 * <p>{@code BlockDamage} is the {@code damageScale} of {@link BlockHarvestUtils#performBlockDamage}: the damage done is the power of
 * the stand-in tool for the block's gather type times this, taken off the block's health of 1.0 (a block breaks when its health
 * reaches 0). Tool_Pickaxe_Mithril has power 1.0 on Rocks and Soils, so 1 is one whole block per hit; 0.05 would be 20 hits.
 *
 * <pre>
 * {"Type": "Arrakis_Cutter_Mk2_Range_Gate", "Range": 5, "PowerPerSecond": 1, "FireInterval": 0.05, "BlockDamage": 1,
 *  "ToolItemId": "Tool_Pickaxe_Mithril", "SparksId": "Arrakis_Cutter_Mk2_Sparks"}
 * </pre>
 */
public class CutterMk2GateInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Cutter_Mk2_Range_Gate";

    /** The tool id the block asset's gathering data is looked up with: what the vanilla pickaxe breaks with. */
    private static final String TOOL_ID = "Pickaxe";

    public static final BuilderCodec<CutterMk2GateInteraction> CODEC = BuilderCodec
            .builder(CutterMk2GateInteraction.class, CutterMk2GateInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("ToolItemId", Codec.STRING, false), (i, v) -> i.toolItemId = v, i -> i.toolItemId)
            .add()
            .append(new KeyedCodec<>("Range", Codec.FLOAT, false), (i, v) -> i.range = v, i -> i.range)
            .add()
            .append(new KeyedCodec<>("PowerPerSecond", Codec.FLOAT, false), (i, v) -> i.powerPerSecond = v,
                    i -> i.powerPerSecond)
            .add()
            .append(new KeyedCodec<>("FireInterval", Codec.FLOAT, false), (i, v) -> i.fireInterval = v, i -> i.fireInterval)
            .add()
            .append(new KeyedCodec<>("BlockDamage", Codec.FLOAT, false), (i, v) -> i.blockDamage = v, i -> i.blockDamage)
            .add()
            .append(new KeyedCodec<>("SparksId", Codec.STRING, false), (i, v) -> i.sparksId = v, i -> i.sparksId)
            .add()
            .build();

    private String toolItemId = "Tool_Pickaxe_Mithril";
    private float range = 5f;
    private float powerPerSecond = 1f;
    /** Seconds between two shots (the RunTime of the shot interaction): power per shot is PowerPerSecond times this. */
    private float fireInterval = 0.05f;
    private float blockDamage = 1f;
    private String sparksId = "Arrakis_Cutter_Mk2_Sparks";

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
        float cost = powerPerSecond * fireInterval;
        if (ArrakisPower.get(buffer, ref) <= 0f) {
            // Trying with an empty pack counts as use, so that holding the button does not recharge it.
            ArrakisPower.spend(buffer, ref, cost);
            context.getState().state = InteractionState.Failed;
            return;
        }
        Vector3i target = TargetUtil.getTargetBlock(ref, range, buffer);
        if (target == null || !ArrakisPower.spend(buffer, ref, cost)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Transform look = TargetUtil.getLook(ref, buffer);
        Vector3d eye = look.getPosition();
        Vector3d dir = new Vector3d(look.getDirection()).normalize();
        // The point where the look ray enters the block face: exactly under the crosshair.
        Vector3d hit = new Vector3d(eye).fma(BeamMath.entryDistance(eye, dir, target), dir);
        ParticleUtil.spawnParticleEffect(sparksId, hit, buffer);
        damage(buffer.getExternalData().getWorld(), buffer, ref, target);
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
        // No audio from the block either: the gun is to be silent.
        int settings = SetBlockSettings.NO_SEND_AUDIO;
        Player player = buffer.getComponent(ref, Player.getComponentType());
        if (player != null && player.getGameMode() == GameMode.Creative) {
            BlockHarvestUtils.performBlockBreak(ref, tool, target, settings | SetBlockSettings.NO_FIRE_ON_BREAK, section,
                    buffer, chunks.getStore());
        } else {
            BlockHarvestUtils.performBlockDamage(ref, ref, target, tool, null, TOOL_ID, false, blockDamage, settings, false,
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
                EquipmentOfArrakisPlugin.get().getLogger().atWarning().log("Cutter Ray Mk2: no item %s", toolItemId);
                return null;
            }
        }
        return toolStack;
    }
}
