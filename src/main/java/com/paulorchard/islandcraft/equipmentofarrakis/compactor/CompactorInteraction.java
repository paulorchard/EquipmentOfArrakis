package com.paulorchard.islandcraft.equipmentofarrakis.compactor;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldAccess;
import com.paulorchard.islandcraft.equipmentofarrakis.power.ManaRefusal;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.List;

/**
 * Interaction type {@code Arrakis_Compactor_Compact}: the left click (Primary) of the Compactor, held. It is the vanilla {@code Charging} interaction
 * the Compactor already had for its aim pose (held until the button is released, with the aim animation and {@code RequireNewClick}
 * of its root, all unchanged), with one addition: while the button is held, every tick of the held interaction also compacts.
 * Nothing else is needed to repeat: the engine ticks a running interaction on every world tick, and the Charging code ends it when
 * the client reports the release, which is when this class stops compacting too ({@link #tick0}).
 *
 * <p>A tick works on the first block within {@code Range} under the crosshair, and only if it is a sand block by
 * {@link CompactMath#allowed}; on anything else nothing happens (the aim pose carries on): no crack, no power, no hum. On a sand block
 * it spends {@code PowerPerSecond} times the time since the last tick, adds that time to the cycle, shows the crack
 * ({@link CompactorTrail#showCrack}), and keeps the hum effect refreshed. When the cycle reaches {@code CompactSeconds} it pops
 * {@code DropsPerCycle} times what breaking the block would give out of the face, gives the crack back, and starts again. The block is
 * never broken or replaced. A different target, a block that is not sand (any more), no power or the end of the hold gives the crack
 * back and resets the cycle.
 *
 * <pre>
 * {"Type": "Arrakis_Compactor_Compact", "Range": 3, "CompactSeconds": 1, "DropsPerCycle": 1, "PowerPerSecond": 1, "MaxCrack": 0.9,
 *  "AllowedGroups": ["Sand"], "ExcludedBlocks": ["Arrakis_Sand"],
 *  "ExcludedPrefixes": ["Arrakis_Worm_Ripple_Layer_", "Arrakis_Worm_Sunk_"], "HumEffectId": "Arrakis_Compactor_Active", ...Charging fields}
 * </pre>
 */
public class CompactorInteraction extends ChargingInteraction {

    public static final String TYPE = "Arrakis_Compactor_Compact";

    public static final BuilderCodec<CompactorInteraction> CODEC = BuilderCodec
            .builder(CompactorInteraction.class, CompactorInteraction::new, ChargingInteraction.CODEC)
            .append(new KeyedCodec<>("Range", Codec.FLOAT, false), (i, v) -> i.range = v, i -> i.range)
            .add()
            .append(new KeyedCodec<>("CompactSeconds", Codec.FLOAT, false), (i, v) -> i.compactSeconds = v, i -> i.compactSeconds)
            .add()
            .append(new KeyedCodec<>("DropsPerCycle", Codec.INTEGER, false), (i, v) -> i.dropsPerCycle = v, i -> i.dropsPerCycle)
            .add()
            .append(new KeyedCodec<>("PowerPerSecond", Codec.FLOAT, false), (i, v) -> i.powerPerSecond = v, i -> i.powerPerSecond)
            .add()
            .append(new KeyedCodec<>("MaxCrack", Codec.FLOAT, false), (i, v) -> i.maxCrack = v, i -> i.maxCrack)
            .add()
            .append(new KeyedCodec<>("AllowedGroups", Codec.STRING_ARRAY, false), (i, v) -> i.allowedGroups = List.of(v),
                    i -> i.allowedGroups.toArray(new String[0]))
            .add()
            .append(new KeyedCodec<>("ExcludedBlocks", Codec.STRING_ARRAY, false), (i, v) -> i.excludedBlocks = List.of(v),
                    i -> i.excludedBlocks.toArray(new String[0]))
            .add()
            .append(new KeyedCodec<>("ExcludedPrefixes", Codec.STRING_ARRAY, false), (i, v) -> i.excludedPrefixes = List.of(v),
                    i -> i.excludedPrefixes.toArray(new String[0]))
            .add()
            .append(new KeyedCodec<>("DustParticleId", Codec.STRING, false), (i, v) -> i.dustParticleId = v, i -> i.dustParticleId)
            .add()
            .append(new KeyedCodec<>("DustInterval", Codec.FLOAT, false), (i, v) -> i.dustInterval = v, i -> i.dustInterval)
            .add()
            .append(new KeyedCodec<>("HumEffectId", Codec.STRING, false), (i, v) -> i.humEffectId = v, i -> i.humEffectId)
            .add()
            .build();

    private float range = 3f;
    private float compactSeconds = 1f;
    private int dropsPerCycle = 1;
    private float powerPerSecond = 1f;
    /** The share of its health the block shows cracked at the end of a cycle (at most 0.9: it can never be broken by this). */
    private float maxCrack = 0.9f;
    private List<String> allowedGroups = List.of("Sand");
    private List<String> excludedBlocks = List.of("Arrakis_Sand");
    private List<String> excludedPrefixes = List.of("Arrakis_Worm_Ripple_Layer_", "Arrakis_Worm_Sunk_");
    private String humEffectId = "Arrakis_Compactor_Active";
    /** Sand dust off the top face when that is the face looked at (the engine shows it off the side faces), and the seconds between two puffs. */
    private String dustParticleId = "Arrakis_Compactor_Dust";
    private float dustInterval = 0.2f;

    @Override
    protected void tick0(boolean firstRun, float time, InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        super.tick0(firstRun, time, type, context, cooldowns);
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        if (buffer == null || ref == null) {
            return;
        }
        CompactorTrail.State state = CompactorTrail.state(buffer, ref);
        if (state == null) {
            return;
        }
        if (context.getState().state != InteractionState.NotFinished) {
            // Released (or cancelled): the Charging code has ended the hold.
            CompactorTrail.release(state);
            return;
        }
        compact(buffer, ref, state);
    }

    /**
     * A puff of sand, tinted like the block, off the face the crosshair is on, but only when that is the top face: the engine shows
     * its own hit particles off the side faces, and not off the top one, which is the face a player harvests sand from.
     */
    private void dust(World world, CommandBuffer<EntityStore> buffer, Ref<EntityStore> ref, CompactorTrail.State state, BlockType block,
            long now) {
        if (dustParticleId == null || now - state.lastDust < (long) (dustInterval * 1_000_000_000L)) {
            return;
        }
        Transform look = TargetUtil.getLook(ref, buffer);
        BeamMath.Entry entry = BeamMath.entry(look.getPosition(), new Vector3d(look.getDirection()).normalize(), state.target);
        if (entry.normal().y != 1) {
            return;
        }
        state.lastDust = now;
        Vector3d at = BeamMath.offFace(entry, 0.02);
        SpatialResource<Ref<EntityStore>, EntityStore> players = buffer.getResource(EntityModule.get().getPlayerSpatialResourceType());
        List<Ref<EntityStore>> nearby = SpatialResource.getThreadLocalReferenceList();
        players.getSpatialStructure().collect(at, 75.0, nearby);
        Color color = block.getParticleColor();
        if (color == null) {
            ParticleUtil.spawnParticleEffect(dustParticleId, at, nearby, buffer);
        } else {
            ParticleUtil.spawnParticleEffect(dustParticleId, at, 0f, 0f, 0f, 1f, color, nearby, buffer);
        }
    }

    private void compact(CommandBuffer<EntityStore> buffer, Ref<EntityStore> ref, CompactorTrail.State state) {
        World world = buffer.getExternalData().getWorld();
        state.world = world;
        long now = System.nanoTime();
        double dt = state.lastTick == 0 ? 0 : (now - state.lastTick) / 1e9;
        if (dt > CompactorTrail.MAX_STEP_SECONDS) {
            // A gap: the hold had stopped (or the server stalled); start the cycle over.
            CompactorTrail.release(state);
            dt = 0;
        }
        Vector3i target = TargetUtil.getTargetBlock(ref, range, buffer);
        // Unloaded reads as air, so as no block.
        int blockId = target == null ? 0 : new WorldAccess(world).block(target.x, target.y, target.z);
        BlockType block = WorldAccess.blockTypeOf(blockId);
        if (block == null || !CompactMath.allowed(block.getGroup(), block.getId(), allowedGroups, excludedBlocks, excludedPrefixes)) {
            CompactorTrail.release(state);
            state.lastTick = 0;
            return;
        }
        if (state.target == null || !state.target.equals(target) || state.blockId != blockId) {
            CompactorTrail.release(state);
            state.target = new Vector3i(target);
            state.blockId = blockId;
            state.cycle = new CompactMath.Cycle(compactSeconds, CompactorTrail.MAX_STEP_SECONDS);
            dt = 0;
        }
        state.lastTick = now;
        // Mana: 1 per second while really compacting; none left (creative never runs out) stops it and the block heals.
        Power power = Power.get();
        boolean creative = power != null && power.isCreative(buffer, ref);
        if (power == null || (!creative && power.get(buffer, ref) <= 0f)) {
            if (power != null) {
                ManaRefusal.say(buffer.getComponent(ref, PlayerRef.getComponentType()), powerPerSecond);
            }
            CompactorTrail.release(state);
            return;
        }
        power.spendUpTo(buffer, ref, (float) (powerPerSecond * Math.min(dt, CompactorTrail.MAX_STEP_SECONDS)));
        CompactorTrail.hum(buffer, ref, humEffectId);
        dust(world, buffer, ref, state, block, now);
        boolean done = state.cycle.advance(dt);
        double crack = CompactMath.clampMaxCrack(maxCrack);
        if (done) {
            List<ItemStack> drops = CompactorTrail.dropsOf(block);
            for (int i = 0; i < Math.max(1, dropsPerCycle); i++) {
                CompactorTrail.drop(buffer, ref, state.target, drops);
            }
            Vector3i keep = state.target;
            CompactorTrail.release(state);
            // The block stays targeted for the next cycle.
            state.target = keep;
            state.blockId = blockId;
            state.cycle = new CompactMath.Cycle(compactSeconds, CompactorTrail.MAX_STEP_SECONDS);
        } else {
            CompactorTrail.showCrack(state, CompactMath.healthAt(state.cycle.fraction(), crack), (float) (1.0 - crack));
        }
    }
}
