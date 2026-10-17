package com.paulorchard.islandcraft.equipmentofarrakis.shield;

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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;
import org.joml.Vector3d;
import org.joml.Vector4d;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Takes projectile damage out of Mana while the Holtzman field is up. It sits in the engine "filter" group of the damage pipeline (the
 * group where armour and wielding reductions work), after both of those reductions and so before {@code DamageSystems.ApplyDamage}:
 * the amount it sees is what would really have reached health, after the armour and resistances. A hit is recognised as a projectile
 * hit when its source is a {@link Damage.ProjectileSource} or, failing that, its cause is the projectile cause. The hit is cancelled: a
 * cancelled damage event is skipped by every system after this one, so health is untouched and there is no hit animation, indicator,
 * damage sound, particle or knockback from it. The Mana taken is {@code ManaPerDamage} per point (creative pays nothing). A hit that
 * uses up the Mana (cost equal to or more than what is left) is absorbed whole, takes Mana to 0, breaks the field (shatter and sound)
 * and marks the shield broken: the toggle stays ON and the field returns by itself when the Mana is back to the re-arm level.
 */
final class HoltzmanDamageSystem extends DamageEventSystem {

    private final Supplier<ShieldConfig> settings;
    private Query<EntityStore> query;

    HoltzmanDamageSystem(Supplier<ShieldConfig> settings) {
        this.settings = settings;
    }

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(Player.getComponentType(), PlayerRef.getComponentType());
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
        Power power = Power.get();
        if (power == null || !isProjectile(damage) || damage.getAmount() <= 0f) {
            return;
        }
        ShieldConfig cfg = settings.get();
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        HoltzmanState state = player == null ? null : HoltzmanState.peek(player.getUuid());
        if (state == null) {
            return;
        }
        boolean alive = store.getComponent(ref, DeathComponent.getComponentType()) == null;
        float mana = power.get(store, ref);
        boolean creative = power.isCreative(store, ref);
        // Decided from the state itself, not from the effect, so a hit in the tick the toggle was switched on (or the tick after a shot)
        // is already covered; while the cooldown after the owner last shot runs the field is down and absorbs nothing.
        boolean cooling = state.cooling(player.getUuid(), System.nanoTime());
        if (!HoltzmanRules.raised(state.on, alive, creative ? Math.max(mana, 1f) : mana, cooling, state.broken)) {
            return;
        }
        HoltzmanRules.Absorption result = HoltzmanRules.absorb(damage.getAmount(), mana, creative ? 0f : (float) cfg.getManaPerDamage());
        if (!creative && result.cost() <= 0f) {
            return;
        }
        damage.setCancelled(true);
        if (!creative) {
            // Power.set only changes the value (spend would be all or nothing and refuse the hit that empties it).
            power.set(commandBuffer, ref, result.manaAfter());
        }

        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        Vector3d feet = transform == null ? new Vector3d() : transform.getPosition();
        double[] centre = {feet.x, feet.y + cfg.getFieldCenterHeight(), feet.z};
        Vector4d hit = damage.getIfPresentMetaObject(Damage.HIT_LOCATION);
        double[] point = HoltzmanRules.hitPoint(centre, hit == null ? null : new double[] {hit.x, hit.y, hit.z}, cfg.getFieldRadius());
        HoltzmanField.hitFlash(commandBuffer, new Vector3d(point[0], point[1], point[2]));
        if (result.broken() && !creative) {
            state.broken = true;
            HoltzmanField.breakShield(commandBuffer, ref, cfg, new Vector3d(centre[0], centre[1], centre[2]), state);
        } else {
            // The shimmer over the body runs once, fast, as the impact.
            HoltzmanField.impact(commandBuffer, ref, cfg);
        }
    }
}
