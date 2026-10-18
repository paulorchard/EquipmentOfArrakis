package com.paulorchard.islandcraft.equipmentofarrakis.weapons;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.function.Supplier;

/**
 * The second half of the range rule: a hit from a dart whose projectile is more than {@code Range} blocks from where it was launched
 * does no damage. {@link DartRangeSystem} removes a dart at the limit, but a dart moves about 3.3 blocks a tick, so one can reach a
 * target in the step that crosses the limit; this cancels that hit. It is in the gather group, the first group of the damage pipeline
 * (before the filter group where armour and the Holtzman field work), and a cancelled damage event is skipped by every system after it,
 * so there is no health loss, no hurt animation and no power taken from a Holtzman field. The distance of the hit is measured to the nearest
 * point of the target's bounding box (the dart's own position lags a hit by up to one step), and the dart's position is checked as well.
 */
final class DartRangeDamageSystem extends DamageEventSystem {

    /** Development: the self-test sets this to have every dart hit logged with the distances the rule saw. */
    static volatile boolean trace;

    private final Supplier<WeaponsConfig> settings;

    DartRangeDamageSystem(Supplier<WeaponsConfig> settings) {
        this.settings = settings;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getGatherDamageGroup();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
            Damage damage) {
        if (!(damage.getSource() instanceof Damage.ProjectileSource source)) {
            return;
        }
        if (trace) {
            WeaponsModule.logger().atInfo().log("dart damage event: projectile %s", source.getProjectile());
        }
        Ref<EntityStore> projectile = source.getProjectile();
        if (projectile == null || !projectile.isValid()) {
            return;
        }
        DartFlight flight = store.getComponent(projectile, DartFlight.type);
        TransformComponent transform = store.getComponent(projectile, TransformComponent.getComponentType());
        if (flight == null || transform == null) {
            return;
        }
        double range = settings.get().getRange();
        Vector3d at = transform.getPosition();
        if (DartMath.beyond(flight.launch, new double[] {at.x, at.y, at.z}, range)) {
            damage.setCancelled(true);
            if (trace) {
                WeaponsModule.logger().atInfo().log("dart hit cancelled: the dart at %s is beyond %s from %s", at, range,
                        java.util.Arrays.toString(flight.launch));
            }
            return;
        }
        // The projectile's own position is where it was at the start of the tick it hit in: it lags the hit by up to a whole step (3.3
        // blocks). The hit is on the surface of the target's box, which is no nearer than the box's nearest point to the launch point.
        TransformComponent targetTransform = chunk.getComponent(index, TransformComponent.getComponentType());
        BoundingBox box = chunk.getComponent(index, BoundingBox.getComponentType());
        if (targetTransform != null && box != null) {
            Vector3d feet = targetTransform.getPosition();
            Vector3d min = box.getBoundingBox().min;
            Vector3d max = box.getBoundingBox().max;
            double reach = DartMath.distanceToBox(flight.launch, new double[] {feet.x + min.x, feet.y + min.y, feet.z + min.z},
                    new double[] {feet.x + max.x, feet.y + max.y, feet.z + max.z});
            if (trace) {
                WeaponsModule.logger().atInfo().log("dart hit: launch %s, dart at %s, target feet %s, reach %s of %s",
                        java.util.Arrays.toString(flight.launch), at, feet, reach, range);
            }
            if (reach > range) {
                damage.setCancelled(true);
            }
        }
    }
}
