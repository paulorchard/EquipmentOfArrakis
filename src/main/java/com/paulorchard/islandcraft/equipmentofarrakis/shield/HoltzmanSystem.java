package com.paulorchard.islandcraft.equipmentofarrakis.shield;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs every world tick for every player and makes the Holtzman field match the rule {@link HoltzmanRules#raised}: up while the
 * toggle is ON, the player is alive, there is Mana, the cooldown after their last shot is not running and the shield has not broken
 * and is waiting; down otherwise. The field itself is not remembered: each tick it looks at what is, so a field left in a save, after
 * a respawn or a change of world, or on a player whose toggle was reset by anything at all, is taken off at once, and nothing can be
 * left behind. A broken shield comes back by itself here when the Mana is back to the re-arm level. Raising the field plays the
 * power-up sound and lowering it the power-down sound ({@link HoltzmanRules#plan}); the first tick for a player or a new entity, and a
 * dead player, are silent.
 */
final class HoltzmanSystem extends EntityTickingSystem<EntityStore> {

    private final Supplier<ShieldConfig> settings;
    private Query<EntityStore> query;

    HoltzmanSystem(Supplier<ShieldConfig> settings) {
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
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        Power power = Power.get();
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        if (power == null || player == null) {
            return;
        }
        ShieldConfig cfg = settings.get();
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        UUID id = player.getUuid();
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
        float mana = power.get(store, ref);
        if (state.broken && !HoltzmanRules.stillBroken(true, mana, HoltzmanRules.rearmLevel((float) cfg.getRearmMana(), power.max(store, ref)))) {
            state.broken = false; // the Mana is back: the field rises by itself, with the normal power-up sound, no message
        }
        boolean cooling = state.cooling(id, System.nanoTime());
        HoltzmanRules.Plan plan = HoltzmanRules.plan(state.on, alive, mana, cooling, state.broken, HoltzmanField.hasField(store, ref, cfg), silent);
        if (plan.change() == HoltzmanRules.Change.RAISE) {
            HoltzmanField.raise(commandBuffer, ref, cfg);
        } else if (plan.change() == HoltzmanRules.Change.LOWER) {
            HoltzmanField.lower(commandBuffer, ref, cfg);
        }
        if (plan.sound() != HoltzmanRules.Sound.NONE) {
            HoltzmanField.playPair(commandBuffer, ref, plan.sound() == HoltzmanRules.Sound.ON ? HoltzmanField.ON_SOUND : HoltzmanField.OFF_SOUND,
                    HoltzmanField.centre(commandBuffer, ref, cfg));
        }
    }
}
