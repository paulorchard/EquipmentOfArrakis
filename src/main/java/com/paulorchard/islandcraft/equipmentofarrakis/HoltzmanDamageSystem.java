package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector4d;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Takes projectile damage out of Arrakis power while the Holtzman field is up. It sits in the engine's "filter" group of the damage
 * pipeline (the group where armour and wielding reductions work), after both of those reductions and so before
 * {@code DamageSystems.ApplyDamage} (which comes after the whole filter group): the amount it sees is what would really have reached
 * health, after the player's armour and resistances. A hit is recognised as a projectile hit when its source is a
 * {@link Damage.ProjectileSource} (the engine makes one for every projectile) or, failing that, its cause is the projectile cause.
 * The hit is cancelled: a cancelled damage event is skipped by every system after this one (the engine's event systems do not process
 * a cancelled event), so health is untouched and there is no hit animation, hit indicator, damage sound, particle or knockback from
 * it. The power taken is {@code PowerPerDamage} per point through {@link ArrakisPower#set}, which does not count as use of power, so
 * the recharge goes on. A hit that uses up the power breaks the field.
 */
final class HoltzmanDamageSystem extends DamageEventSystem {

    private final Supplier<HoltzmanSettings> settings;
    private Query<EntityStore> query;

    HoltzmanDamageSystem(Supplier<HoltzmanSettings> settings) {
        this.settings = settings;
    }

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(Player.getComponentType());
        }
        return query;
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
                new SystemDependency<>(Order.AFTER, DamageSystems.ArmorDamageReduction.class),
                new SystemDependency<>(Order.AFTER, DamageSystems.WieldingDamageReduction.class));
    }

    /** True for a projectile hit: a projectile source, or a projectile damage cause. */
    static boolean isProjectile(Damage damage) {
        if (damage.getSource() instanceof Damage.ProjectileSource) {
            return true;
        }
        DamageCause cause = damage.getCause();
        return cause != null && DamageCause.PROJECTILE != null && DamageCause.PROJECTILE.getId().equals(cause.getId());
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
            Damage damage) {
        if (!isProjectile(damage) || damage.getAmount() <= 0f) {
            return;
        }
        HoltzmanSettings cfg = settings.get();
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        boolean alive = store.getComponent(ref, DeathComponent.getComponentType()) == null;
        float power = ArrakisPower.get(store, ref);
        // Decided from the state itself, not from the effect, so a hit in the tick the shield was selected is already covered.
        if (!HoltzmanRules.raised(HoltzmanField.selected(store, ref, cfg), alive, power)) {
            return;
        }
        HoltzmanRules.Absorption result = HoltzmanRules.absorb(damage.getAmount(), power, (float) cfg.getPowerPerDamage());
        if (result.cost() <= 0f) {
            return;
        }
        damage.setCancelled(true);
        // Not ArrakisPower.spend: that marks the power as in use and would pause the recharge; set() only changes the value.
        ArrakisPower.set(commandBuffer, ref, result.powerAfter());

        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        Vector3d feet = transform == null ? new Vector3d() : transform.getPosition();
        double[] centre = {feet.x, feet.y + cfg.getFieldCenterHeight(), feet.z};
        Vector4d hit = damage.getIfPresentMetaObject(Damage.HIT_LOCATION);
        double[] point = HoltzmanRules.hitPoint(centre, hit == null ? null : new double[] {hit.x, hit.y, hit.z}, cfg.getFieldRadius());
        HoltzmanField.hitFlash(commandBuffer, new Vector3d(point[0], point[1], point[2]));
        if (result.broken()) {
            HoltzmanField.breakShield(commandBuffer, ref, cfg, new Vector3d(centre[0], centre[1], centre[2]));
        } else {
            // The shimmer over the body runs once, fast, as the impact.
            HoltzmanField.impact(commandBuffer, ref, cfg);
        }
    }
}
