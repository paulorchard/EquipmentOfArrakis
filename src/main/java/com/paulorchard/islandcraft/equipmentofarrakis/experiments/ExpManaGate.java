// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
package com.paulorchard.islandcraft.equipmentofarrakis.experiments;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.paulorchard.islandcraft.equipmentofarrakis.power.Power;

/**
 * The Mana gate every experiment uses, named generically so that a later experiment reuses it. A held button runs the Primary
 * chain again and again; a refusal must come before anything the player can see or hear starts, because the engine cancels the
 * rest of the chain when an interaction ends as Failed (so the animation and particles interactions after the gate never run).
 *
 * <p>Costs are per second in the interaction's data and are paid per tick: {@code cost = perSecond * tickSeconds}.
 */
final class ExpManaGate {

    private ExpManaGate() {
    }

    /** What one tick costs. Never negative. */
    static float cost(float perSecond, float tickSeconds) {
        return Math.max(0f, perSecond) * Math.max(0f, tickSeconds);
    }

    /** True when the player could pay {@code amount} now (creative always can). Takes nothing. */
    static boolean canPay(CommandBuffer<EntityStore> buffer, Ref<EntityStore> player, float amount) {
        Power power = Power.get();
        if (power == null) {
            return false;
        }
        return amount <= 0f || power.isCreative(buffer, player) || power.has(buffer, player, amount);
    }

    /** Pays {@code amount} all or nothing (creative pays nothing and succeeds). False when there is not enough. */
    static boolean pay(CommandBuffer<EntityStore> buffer, Ref<EntityStore> player, float amount) {
        Power power = Power.get();
        return power != null && power.spend(buffer, player, amount);
    }

    /** Ends the interaction as Failed: the engine cancels the whole chain, so nothing after the gate starts. */
    static void refuse(InteractionContext context) {
        context.getState().state = InteractionState.Failed;
    }
}
