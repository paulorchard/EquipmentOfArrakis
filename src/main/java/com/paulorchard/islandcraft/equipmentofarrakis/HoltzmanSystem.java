package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs every world tick for every player and makes the Holtzman field match the rule {@link HoltzmanRules#raised}: up while the
 * shield is the selected off-hand item, the player is alive, there is power and the cooldown after their last projectile is not
 * running; down otherwise. The field itself is not remembered: each tick it looks at what is, so a field left in a save, after a respawn
 * or a change of world, or on a player whose shield was swapped away by anything at all, is taken off at once, and nothing can be left
 * behind. What is remembered per player is only the time of the last shot ({@link HoltzmanState}, stamped by {@link ShieldShots}).
 * Raising the field plays the power-up sound and lowering it the power-down sound ({@link HoltzmanRules#plan}); the first tick for a
 * player or a new entity, and a dead player, are silent. With the shield selected and no power it also switches the off hand to empty
 * (so selecting it with 0 power does nothing). The power is read only through {@link ArrakisPower#get}.
 */
final class HoltzmanSystem extends EntityTickingSystem<EntityStore> {

    private final Supplier<HoltzmanSettings> settings;
    private Query<EntityStore> query;

    HoltzmanSystem(Supplier<HoltzmanSettings> settings) {
        this.settings = settings;
    }

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(Player.getComponentType(), PlayerRef.getComponentType(), UUIDComponent.getComponentType());
        }
        return query;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        HoltzmanSettings cfg = settings.get();
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        UUID id = chunk.getComponent(index, UUIDComponent.getComponentType()).getUuid();
        HoltzmanState state = HoltzmanState.of(id);
        boolean silent = false;
        if (state.ref != ref) {
            // First tick for this player, or a new entity (another world): the cooldown does not carry over and nothing is played.
            state.ref = ref;
            state.clearCooldown();
            silent = true;
        }
        boolean alive = store.getComponent(ref, DeathComponent.getComponentType()) == null;
        if (!alive) {
            state.clearCooldown();
        }
        boolean selected = HoltzmanField.selected(store, ref, cfg);
        float power = ArrakisPower.get(store, ref);
        boolean cooling = state.cooling(id, System.nanoTime());
        HoltzmanRules.Plan plan = HoltzmanRules.plan(selected, alive, power, cooling, HoltzmanField.hasField(store, ref, cfg), silent);
        if (plan.change() == HoltzmanRules.Change.RAISE) {
            HoltzmanField.raise(commandBuffer, ref, cfg);
        } else if (plan.change() == HoltzmanRules.Change.LOWER) {
            HoltzmanField.lower(commandBuffer, ref, cfg);
        }
        if (plan.sound() != HoltzmanRules.Sound.NONE) {
            HoltzmanField.playPair(commandBuffer, ref, plan.sound() == HoltzmanRules.Sound.ON ? HoltzmanField.ON_SOUND : HoltzmanField.OFF_SOUND,
                    HoltzmanField.centre(commandBuffer, ref, cfg));
        }
        if (selected && alive && power <= 0f) {
            // Selected with no power: the off hand goes back to empty at once.
            HoltzmanField.putAway(commandBuffer, ref);
        }
    }
}
