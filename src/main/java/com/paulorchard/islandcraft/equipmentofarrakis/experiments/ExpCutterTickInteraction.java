// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
package com.paulorchard.islandcraft.equipmentofarrakis.experiments;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldAccess;
import org.joml.Vector3i;

import java.util.logging.Level;

/**
 * Interaction type {@code Arrakis_Exp_Cutter_Tick}: one tick of the Flame Cutter's beam, run first in the Primary chain every
 * {@code RunTime} seconds while the button is held. It either refuses (ends as Failed, so that nothing after it in the chain,
 * the animation and the beam particles, ever starts) or cuts:
 * <ol>
 *   <li>The cost of the tick is {@code ManaPerSecond * RunTime}. When the player cannot pay it the tick is refused, silently.</li>
 *   <li>The block under the crosshair within {@code Range} is damaged as a pickaxe would: {@code BlockHarvestUtils.performBlockDamage}
 *       with an unbreakable stand-in stack of {@code ToolItemId}, the tool id "Pickaxe" and {@code DamageScale}. In Creative the
 *       block is broken at once with {@code performBlockBreak}. Blocks the pickaxe cannot mine are not touched.</li>
 *   <li>The Mana is taken (Creative takes none). With no block in range nothing is taken unless {@code SpendWithoutTarget}.</li>
 * </ol>
 * No entity is looked at, so nothing is hurt, pushed or set alight, and no sound is played (block sounds are switched off with
 * {@code Silent}).
 *
 * <pre>
 * {"Type": "Arrakis_Exp_Cutter_Tick", "RunTime": 0.1, "Range": 5, "ManaPerSecond": 3.3, "DamageScale": 0.24,
 *  "ToolItemId": "Tool_Pickaxe_Mithril", "SpendWithoutTarget": false, "Silent": true}
 * </pre>
 */
public class ExpCutterTickInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Exp_Cutter_Tick";

    /** The tool id the block asset's gathering data is looked up with: what the vanilla pickaxe breaks with. */
    private static final String TOOL_ID = "Pickaxe";

    public static final BuilderCodec<ExpCutterTickInteraction> CODEC = BuilderCodec
            .builder(ExpCutterTickInteraction.class, ExpCutterTickInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("Range", Codec.FLOAT, false), (i, v) -> i.range = v, i -> i.range)
            .add()
            .append(new KeyedCodec<>("ManaPerSecond", Codec.FLOAT, false), (i, v) -> i.manaPerSecond = v, i -> i.manaPerSecond)
            .add()
            .append(new KeyedCodec<>("DamageScale", Codec.FLOAT, false), (i, v) -> i.damageScale = v, i -> i.damageScale)
            .add()
            .append(new KeyedCodec<>("ToolItemId", Codec.STRING, false), (i, v) -> i.toolItemId = v, i -> i.toolItemId)
            .add()
            .append(new KeyedCodec<>("SpendWithoutTarget", Codec.BOOLEAN, false), (i, v) -> i.spendWithoutTarget = v,
                    i -> i.spendWithoutTarget)
            .add()
            .append(new KeyedCodec<>("Silent", Codec.BOOLEAN, false), (i, v) -> i.silent = v, i -> i.silent)
            .add()
            .build();

    private float range = 5f;
    /** About what vanilla Mana regenerates per second, so holding the beam is sustainable. */
    private float manaPerSecond = 3.3f;
    /** The damageScale of performBlockDamage: 0.24 per 0.1 s is one full pickaxe swing per 0.4167 s (v1's measurement). */
    private float damageScale = 0.24f;
    private String toolItemId = "Tool_Pickaxe_Mithril";
    private boolean spendWithoutTarget;
    private boolean silent = true;

    /** Built on first use: the stack needs the item assets, which are not loaded when the codec is. */
    private transient ItemStack toolStack;

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> player = context.getEntity();
        if (buffer == null || player == null) {
            ExpManaGate.refuse(context);
            return;
        }
        float cost = ExpManaGate.cost(manaPerSecond, tickSeconds());
        // Every refusal is decided here, before the animation and the particles of the chain after this interaction.
        if (!ExpManaGate.canPay(buffer, player, cost)) {
            ExpManaGate.refuse(context);
            return;
        }
        Vector3i target = TargetUtil.getTargetBlock(player, range, buffer);
        if (target == null) {
            if (spendWithoutTarget) {
                ExpManaGate.pay(buffer, player, cost);
            }
            return; // the beam shows, nothing is cut
        }
        cut(buffer, player, target);
        ExpManaGate.pay(buffer, player, cost);
    }

    /** The seconds one tick stands for: the interaction's RunTime, or 0.1 when the data has none. */
    private float tickSeconds() {
        return getRunTime() > 0f ? getRunTime() : 0.1f;
    }

    private void cut(CommandBuffer<EntityStore> buffer, Ref<EntityStore> player, Vector3i target) {
        ItemStack tool = tool();
        if (tool == null) {
            return;
        }
        World world = buffer.getExternalData().getWorld();
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> section = new WorldAccess(world).sectionRef(target.x, target.y, target.z);
        if (section == null) {
            return;
        }
        int settings = silent ? SetBlockSettings.NO_SEND_AUDIO : 0;
        Player who = buffer.getComponent(player, Player.getComponentType());
        if (who != null && who.getGameMode() == GameMode.Creative) {
            BlockHarvestUtils.performBlockBreak(player, tool, target, settings | SetBlockSettings.NO_FIRE_ON_BREAK, section,
                    buffer, chunks.getStore());
        } else {
            BlockHarvestUtils.performBlockDamage(player, player, target, tool, null, TOOL_ID, false, damageScale, settings, false,
                    section, buffer, chunks.getStore());
        }
    }

    private ItemStack tool() {
        if (toolStack == null) {
            try {
                // Max durability 0 is "unbreakable": the damage code then skips the durability loss, which would otherwise be
                // written into the player's held item.
                toolStack = new ItemStack(toolItemId, 1).withMaxDurability(0);
            } catch (RuntimeException e) {
                ExperimentsModule.logger().at(Level.WARNING).log("Flame Cutter: no item %s (%s)", toolItemId, e);
                return null;
            }
        }
        return toolStack;
    }
}
