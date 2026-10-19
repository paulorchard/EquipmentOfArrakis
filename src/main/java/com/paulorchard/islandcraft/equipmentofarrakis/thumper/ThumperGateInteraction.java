package com.paulorchard.islandcraft.equipmentofarrakis.thumper;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.core.Notice;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldAccess;
import org.joml.Vector3d;

import java.util.Set;
import java.util.UUID;

/**
 * Interaction type {@code Arrakis_Thumper_Gate}: first in the Secondary chain of the Thumper item. Decides, in this order, ending the
 * chain as Failed (nothing placed, nothing consumed) with a short message when it must: the block under the player is not sand; the
 * player already has {@code MaxLivePerPlayer} Thumpers standing; no room (the feet are inside a solid block). Otherwise it notes the
 * placement in the book (so a quick second click is counted) and lets the chain go on to place the deployable and consume the item.
 * The numbers come from the Thumper section of the config.
 */
public class ThumperGateInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Thumper_Gate";

    public static final BuilderCodec<ThumperGateInteraction> CODEC = BuilderCodec
            .builder(ThumperGateInteraction.class, ThumperGateInteraction::new, SimpleInstantInteraction.CODEC)
            .build();

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        PlayerRef player = buffer == null || ref == null ? null : buffer.getComponent(ref, PlayerRef.getComponentType());
        TransformComponent transform = player == null ? null : buffer.getComponent(ref, TransformComponent.getComponentType());
        if (player == null || transform == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        World world = buffer.getExternalData().getWorld();
        WorldAccess access = new WorldAccess(world);
        Vector3d feet = transform.getPosition();
        int x = (int) Math.floor(feet.x);
        int z = (int) Math.floor(feet.z);
        int below = (int) Math.floor(feet.y - 0.1);
        Set<String> sand = Thumpers.sand();
        String[] column = new String[Thumpers.SAND_DEPTH];
        for (int k = 0; k < column.length; k++) {
            BlockType block = access.blockType(x, below - k, z);
            column[k] = block == null ? null : String.valueOf(block.getId());
        }
        boolean onSand = ThumperRules.onSand(column, sand);
        boolean room = hasRoom(access, x, (int) Math.floor(feet.y), z);
        UUID owner = player.getUuid();
        long now = System.nanoTime();
        int live = Thumpers.book().live(owner, now);
        ThumperRules.Refusal refusal = ThumperRules.refusal(onSand, live, Thumpers.config().maxLivePerPlayer(), room);
        if (refusal != ThumperRules.Refusal.NONE) {
            Notice.say(player, switch (refusal) {
                case NOT_SAND -> "server.arrakis.thumper.notSand";
                case LIMIT -> "server.arrakis.thumper.limit";
                default -> "server.arrakis.thumper.noRoom";
            });
            context.getState().state = InteractionState.Failed;
            return;
        }
        Thumpers.book().reserve(owner, now);
    }

    /** The two blocks the Thumper stands in (feet and one above) are not solid. */
    private static boolean hasRoom(WorldAccess access, int x, int y, int z) {
        for (int dy = 0; dy <= 1; dy++) {
            BlockType block = access.blockType(x, y + dy, z);
            if (block != null && block.getMaterial() == BlockMaterial.Solid) {
                return false;
            }
        }
        return true;
    }
}
