// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
package com.paulorchard.islandcraft.equipmentofarrakis.experiments;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.packets.world.SpawnParticleSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * A beam in WORLD space whose far end is, by construction, the point the server believes the crosshair lands on. The particle
 * system is not attached to a model node (whose frame and rotation are guesswork); the server works out where it starts (a point
 * in the camera's frame: right, up, forward of the eye), where it must end (the look ray at the apex distance, or where it enters
 * a block), turns the system to point from the one to the other and spawns it. Everything is recomputed for every spawn, so the beam
 * follows the look exactly; the only numbers to calibrate are where the beam starts.
 */
final class ExpBeamGeometry {

    /** One spawn: where it starts, where it ends, the unit direction, and the engine's yaw and pitch for that direction (radians). */
    static final class Plan {
        final Vector3d start;
        final Vector3d end;
        final Vector3d direction;
        final float yaw;
        final float pitch;

        Plan(Vector3d start, Vector3d end) {
            this.start = start;
            this.end = end;
            this.direction = new Vector3d(end).sub(start);
            if (direction.lengthSquared() < 1e-12) {
                direction.set(0, 0, -1);
            }
            direction.normalize();
            this.yaw = (float) ExpAim.yaw(direction.x, direction.z);
            this.pitch = (float) ExpAim.pitch(direction.x, direction.y, direction.z);
        }

        double length() {
            return start.distance(end);
        }
    }

    private ExpBeamGeometry() {
    }

    /**
     * The target of the look ray: where it enters the block it hits within {@code apex + 2} blocks when {@code endAtBlock}, else
     * (or when it hits nothing) the point {@code apex} blocks along the ray.
     */
    static Vector3d target(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor, Vector3d eye, Vector3d direction, double apex,
            boolean endAtBlock) {
        if (endAtBlock) {
            Vector3i block = TargetUtil.getTargetBlock(ref, Math.max(apex, 1) + 2, accessor);
            if (block != null) {
                double d = ExpAim.entryDistance(eye.x, eye.y, eye.z, direction.x, direction.y, direction.z, block.x, block.y, block.z);
                if (!Double.isNaN(d)) {
                    return new Vector3d(eye).fma(d, direction);
                }
            }
        }
        return new Vector3d(eye).fma(apex, direction);
    }

    /**
     * The plan for a beam that starts at (right, up, forward) and ends at the target of the look ray. The start is in the camera's
     * frame (it turns with the head), or, with {@code body}, in the body's (the heading only, it does not pitch with the head).
     */
    static Plan plan(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor, boolean body, double right, double up, double forward,
            double apex, boolean endAtBlock) {
        Transform look = TargetUtil.getLook(ref, accessor);
        Vector3d eye = new Vector3d(look.getPosition());
        Vector3d direction = new Vector3d(look.getDirection()).normalize();
        double[] e = {eye.x, eye.y, eye.z};
        double[] d = {direction.x, direction.y, direction.z};
        double[] start = body ? ExpAim.bodyPoint(e, d, right, up, forward) : ExpAim.cameraPoint(e, d, right, up, forward);
        return new Plan(new Vector3d(start[0], start[1], start[2]), target(ref, accessor, eye, direction, apex, endAtBlock));
    }

    /**
     * The glow around a beam: one of {@code levels} systems named {@code prefix + level}, from dim to bright, chosen by a sine of
     * {@code hz} cycles a second of the time {@code seconds}, spawned at the START of the plan and turned along it.
     */
    static void spawnGlow(World world, String prefix, int levels, Plan plan, double seconds, double hz, float scale, float duration) {
        spawn(world, prefix + ExpAim.pulseLevel(seconds, hz, levels), plan.start, plan, scale, duration);
    }

    /** Sends the system at a position, turned to the plan's direction, to the players of the world; it stops after {@code duration} seconds. */
    static void spawn(World world, String system, Vector3d at, Plan plan, float scale, float duration) {
        SpawnParticleSystem packet = new SpawnParticleSystem(system, new Position(at.x, at.y, at.z), new Direction(plan.yaw, plan.pitch, 0f),
                scale, null, duration);
        for (PlayerRef player : world.getPlayerRefs()) {
            player.getPacketHandler().writeNoCache(packet);
        }
    }
}
