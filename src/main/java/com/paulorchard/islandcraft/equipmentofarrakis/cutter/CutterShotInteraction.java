package com.paulorchard.islandcraft.equipmentofarrakis.cutter;

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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.paulorchard.islandcraft.equipmentofarrakis.core.ToolUse;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldAccess;
import com.paulorchard.islandcraft.equipmentofarrakis.power.ManaRefusal;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;
import org.joml.Vector3i;

import java.util.logging.Level;

/**
 * Interaction type {@code Arrakis_Cutter_Shot}: the shot of the Cutter Ray, first in its Primary chain. Per shot, on the world thread:
 * <ol>
 *   <li>The Mana gate: {@code ManaPerShot} (10) or the shot is refused. A refusal ends the interaction as Failed, so nothing after it
 *       in the chain (the animation, the camera, the muzzle particles) starts, and tells the player once a second.</li>
 *   <li>The Mana is spent (creative pays nothing).</li>
 *   <li>The block under the crosshair within {@code Range} (5) takes one swing of {@code ToolItemId} (the crude pickaxe) through
 *       {@code BlockHarvestUtils.performBlockDamage}, the route proven by the earlier throw-away trials: {@code DamageScale} 1 is one swing,
 *       the damage accumulates on the block like a pickaxe's. Creative breaks the block at once.</li>
 *   <li>{@code ToolUse.fired} (the Holtzman Shield drops).</li>
 * </ol>
 * No entity is looked at, so nothing is hurt, and no projectile exists.
 */
public class CutterShotInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Cutter_Shot";

    /** The tool id the block asset's gathering data is looked up with: what the vanilla pickaxe breaks with. */
    private static final String TOOL_ID = "Pickaxe";

    public static final BuilderCodec<CutterShotInteraction> CODEC = BuilderCodec
            .builder(CutterShotInteraction.class, CutterShotInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("Range", Codec.FLOAT, false), (i, v) -> i.range = v, i -> i.range)
            .add()
            .append(new KeyedCodec<>("ManaPerShot", Codec.FLOAT, false), (i, v) -> i.manaPerShot = v, i -> i.manaPerShot)
            .add()
            .append(new KeyedCodec<>("DamageScale", Codec.FLOAT, false), (i, v) -> i.damageScale = v, i -> i.damageScale)
            .add()
            .append(new KeyedCodec<>("ToolItemId", Codec.STRING, false), (i, v) -> i.toolItemId = v, i -> i.toolItemId)
            .add()
            .append(new KeyedCodec<>("Silent", Codec.BOOLEAN, false), (i, v) -> i.silent = v, i -> i.silent)
            .add()
            .build();

    private float range = 5f;
    private float manaPerShot = 10f;
    private float damageScale = 1f;
    private String toolItemId = "Tool_Pickaxe_Crude";
    private boolean silent = true;

    /** Built on first use: the stack needs the item assets, which are not loaded when the codec is. */
    private transient ItemStack toolStack;

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> player = context.getEntity();
        Power power = Power.get();
        if (buffer == null || player == null || power == null) {
            refuse(context);
            return;
        }
        PlayerRef who = buffer.getComponent(player, PlayerRef.getComponentType());
        boolean creative = power.isCreative(buffer, player);
        if (!CutterGate.canShoot(power.get(buffer, player), manaPerShot, creative)) {
            ManaRefusal.say(who, manaPerShot);
            refuse(context);
            return;
        }
        if (!power.spend(buffer, player, manaPerShot)) {
            refuse(context); // raced with another spend between the check and now
            return;
        }
        Vector3i target = TargetUtil.getTargetBlock(player, range, buffer);
        if (target != null) {
            cut(buffer, player, target);
        }
        if (who != null) {
            ToolUse.fired(who);
        }
    }

    private static void refuse(InteractionContext context) {
        context.getState().state = InteractionState.Failed;
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
                CutterModule.logger().at(Level.WARNING).log("Cutter Ray: no item %s (%s)", toolItemId, e);
                return null;
            }
        }
        return toolStack;
    }
}
