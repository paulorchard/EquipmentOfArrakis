package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
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
import org.joml.Vector3i;

/**
 * Interaction type {@code Arrakis_Cutter_Beam}: one tick of the Cutter Ray. The engine runs it again every
 * {@code RunTime} seconds for as long as Primary is held. A tick does one of these:
 * <ul>
 *   <li>With no lock: traces the look ray; if it hits a block within {@code Range} and there is power, that block (and
 *       the face the ray entered, and its middle) becomes the lock, see {@link MoltenTrail.Lock}. With no block in range,
 *       or no power, nothing happens.</li>
 *   <li>With a lock: pays for the tick out of {@link ArrakisPower} and damages the locked block. It never re-targets.</li>
 *   <li>While a broken lock waits for a release: nothing at all, and no power is spent.</li>
 * </ul>
 * The molten globs and the laser line are drawn on every world tick by {@link MoltenTrailSystem}, which also breaks the
 * lock when the crosshair has been off the block for longer than {@code LockGraceSeconds}.
 *
 * <p>A tick that does nothing ends as Finished and not Failed. Whether a Failed chain is started again while the button
 * is held is up to the client, which the server cannot see; a Finished tick comes again after {@code RunTime} like every
 * other, so a held button always keeps arriving and a released one always stops, and that is what the release test
 * ({@link MoltenTrail#refuse}) relies on.
 *
 * <p>Block damage goes through {@link BlockHarvestUtils#performBlockDamage}, the same call a pickaxe swing makes,
 * with an unbreakable stack of {@code ToolItemId} standing in for the held item: the block's hardness, the tool's
 * gather-type powers and qualities, drops, sounds and the damage event all behave exactly as for that pickaxe. The
 * stack has no durability, so nothing is worn out. {@code DamageScale} is the share of a pickaxe swing one tick is
 * worth (a swing takes about 0.4167 s, a tick 0.1 s: 0.24).
 *
 * <pre>
 * {"Type": "Arrakis_Cutter_Beam", "RunTime": 0.1, "ToolItemId": "Tool_Pickaxe_Mithril", "Range": 5,
 *  "PowerPerSecond": 1, "DamageScale": 0.24, "ParticleId": "Arrakis_Cutter_Molten", "SpawnsPerTick": 1,
 *  "LockGraceSeconds": 0.15}
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
            .append(new KeyedCodec<>("ParticleId", Codec.STRING, false), (i, v) -> i.particleId = v, i -> i.particleId)
            .add()
            .append(new KeyedCodec<>("SpawnsPerTick", Codec.INTEGER, false), (i, v) -> i.spawnsPerTick = v,
                    i -> i.spawnsPerTick)
            .add()
            .append(new KeyedCodec<>("LockGraceSeconds", Codec.FLOAT, false), (i, v) -> i.lockGraceSeconds = v,
                    i -> i.lockGraceSeconds)
            .add()
            .build();

    private String toolItemId = "Tool_Pickaxe_Mithril";
    private float range = 5f;
    private float powerPerSecond = 1f;
    private float damageScale = 0.24f;
    private String particleId = "Arrakis_Cutter_Molten";
    /** Bursts of globs per world tick, all at the one fixed point at the end of the beam: the intensity dial. */
    private int spawnsPerTick = 1;
    /** How long the crosshair may be off the locked block before the lock breaks. */
    private float lockGraceSeconds = 0.15f;

    /** Built on first use: the stack needs the item assets, which are not loaded when the codec is. */
    private transient ItemStack toolStack;

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        MoltenTrail.State state = buffer == null || ref == null ? null : MoltenTrail.state(buffer, ref);
        if (state == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        long now = System.nanoTime();
        if (MoltenTrail.refuse(state, now)) {
            // A broken lock waits for a release: nothing happens, no power is spent, and it does not count as use.
            return;
        }
        World world = buffer.getExternalData().getWorld();
        float cost = powerPerSecond * getRunTime();
        if (state.lock == null) {
            if (ArrakisPower.get(buffer, ref) <= 0f) {
                // Trying with an empty pack counts as use, so that holding the button does not recharge it.
                ArrakisPower.spend(buffer, ref, cost);
                return;
            }
            MoltenTrail.Lock lock = MoltenTrail.acquire(buffer, ref, world, range);
            if (lock == null) {
                return;
            }
            ArrakisPower.spend(buffer, ref, cost);
            state.particleId = particleId;
            state.range = range;
            state.spawnsPerTick = spawnsPerTick;
            state.graceNanos = (long) (lockGraceSeconds * 1_000_000_000L);
            state.lock = lock;
        } else if (!MoltenTrail.blockStillThere(world, state.lock)) {
            MoltenTrail.breakLock(buffer, state);
            return;
        } else if (!ArrakisPower.spend(buffer, ref, cost)) {
            MoltenTrail.end(buffer, state);
            return;
        }
        state.lastTick = now;
        damage(world, buffer, ref, state.lock.block);
        if (ArrakisPower.get(buffer, ref) <= 0f) {
            MoltenTrail.end(buffer, state);
        }
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
