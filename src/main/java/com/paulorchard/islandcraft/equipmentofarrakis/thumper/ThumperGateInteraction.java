package com.paulorchard.islandcraft.equipmentofarrakis.thumper;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
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
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.paulorchard.islandcraft.equipmentofarrakis.core.Crouch;
import com.paulorchard.islandcraft.equipmentofarrakis.core.Notice;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldAccess;
import com.paulorchard.islandcraft.equipmentofarrakis.core.worms.WormsLink;
import org.joml.Vector3d;

import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;

/**
 * Interaction type {@code Arrakis_Thumper_Gate}: first in the Secondary chain of the Thumper item. The Thumper is planted on the block next
 * to the player in the direction they look (one of the eight around their own block; the pitch is ignored). The gate works out that
 * column, finds the ground in it ({@link ThumperSpot}) and decides, in this order, ending the chain as Failed (nothing placed, nothing
 * consumed) with a short message when it must: not crouching; the player already has {@code MaxLivePerPlayer} Thumpers; no ground or no room
 * there; the ground is not sand by the rule Worms of Arrakis applies to a totem at the position the Thumper will have; another Thumper
 * already stands within 0.8 blocks. Otherwise it notes the spot for {@link ThumperPlaceInteraction}, which spawns the deployable there.
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
        UUID owner = player.getUuid();
        long now = System.nanoTime();
        boolean crouched = Crouch.isCrouching(buffer, ref);
        int live = Thumpers.book().live(owner, now);

        // the neighbour block in the look direction, from the horizontal part of the look (pitch is ignored)
        Transform look = TargetUtil.getLook(ref, buffer);
        Vector3d direction = look.getDirection();
        int[] cell = ThumperSpot.neighbour(direction.x, direction.z);
        Vector3d feet = transform.getPosition();
        int px = (int) Math.floor(feet.x);
        int pz = (int) Math.floor(feet.z);
        int feetY = (int) Math.floor(feet.y + 0.05);

        World world = buffer.getExternalData().getWorld();
        WorldAccess access = new WorldAccess(world);
        Set<String> sand = Thumpers.sand();
        boolean room = false;
        boolean onSand = false;
        double x = 0, y = 0, z = 0;
        String note = "no spot";
        if (cell != null) {
            int tx = px + cell[0];
            int tz = pz + cell[1];
            IntFunction<String> idAt = at -> idOf(access.blockType(tx, at, tz));
            int ground = ThumperSpot.ground(feetY, idAt);
            room = ground != Integer.MIN_VALUE && ThumperSpot.headroom(ground, at -> isSolid(access.blockType(tx, at, tz)));
            x = tx + 0.5;
            z = tz + 0.5;
            if (room) {
                y = ground + 1;
                double tolerance = WormsLink.sandTolerance();
                // Worms also says "not sand" for a column that is not ticking (AggroManager.onSand returns false first)
                onSand = access.isTicking(tx, tz) && ThumperSpot.wormsOnSand(y, tolerance, idAt, sand);
                note = String.format("player block %d %d %d, cell %+d %+d, ground %s at y %d, Worms rule with tolerance %.1f says %s, sand %s", px, feetY, pz,
                        cell[0], cell[1], idAt.apply(ground), ground, tolerance, onSand ? "sand" : "NOT sand", sand);
            }
        }
        boolean occupied = room && Thumpers.book().occupied(x, y, z, ThumperSpot.SPOT_RADIUS, now);
        ThumperRules.Refusal refusal = ThumperRules.refusal(crouched, live, Thumpers.config().maxLivePerPlayer(), room, onSand, occupied);
        if (refusal != ThumperRules.Refusal.NONE) {
            Notice.say(player, switch (refusal) {
                case NOT_CROUCHING -> "server.arrakis.thumper.crouch";
                case LIMIT -> "server.arrakis.thumper.limit";
                case NO_ROOM -> "server.arrakis.thumper.noRoom";
                case NOT_SAND -> "server.arrakis.thumper.notSand";
                default -> "server.arrakis.thumper.occupied";
            });
            context.getState().state = InteractionState.Failed;
            return;
        }
        Thumpers.book().reserve(owner, now, x, y, z);
        ThumperPending.put(owner, new ThumperPending.Spot(x, y, z, note, now));
    }

    private static String idOf(BlockType block) {
        return block == null ? null : String.valueOf(block.getId());
    }

    private static boolean isSolid(BlockType block) {
        return block != null && block.getMaterial() == BlockMaterial.Solid;
    }
}
