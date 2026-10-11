package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Interaction type {@code Arrakis_Dart_Ammo_Gate}: the only interaction of a spring gun's Primary root, run once for every click that
 * gets past the cooldown. It does what the vanilla {@code ModifyInventory} did for the arrow, with Arrakis_Ammo in its place:
 * <ul>
 *   <li>With at least {@code AmmoPerShot} ammo it takes that much and ends normally, so the interaction named by {@code Next} (the shot:
 *       animation, camera shake, smoke, sounds, dart) runs after it.</li>
 *   <li>With less it takes nothing and ends as Failed, so the interaction named by {@code Failed} runs instead: the vanilla
 *       {@code Gun_Shoot_Fail}, the dry click. The shot never runs, so no dart, no smoke and no sound of the gun.</li>
 * </ul>
 * Ammo is spent in every game mode. The engine turns {@code Next} and {@code Failed} of a simple interaction into the two branches of
 * the chain (verified in {@code SimpleInteraction.compile}); this class is a {@code SimpleInstantInteraction}, as {@code ModifyInventory} is,
 * and like it asks the client to wait for the server's answer, because only the server knows the ammo.
 *
 * <pre>
 * {"Type": "Arrakis_Dart_Ammo_Gate", "AmmoPerShot": 1, "Next": "Arrakis_Maula_Shoot", "Failed": "Gun_Shoot_Fail"}
 * </pre>
 */
public class DartAmmoGateInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "Arrakis_Dart_Ammo_Gate";

    public static final BuilderCodec<DartAmmoGateInteraction> CODEC = BuilderCodec
            .builder(DartAmmoGateInteraction.class, DartAmmoGateInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("AmmoPerShot", Codec.FLOAT, false), (i, v) -> i.ammoPerShot = v, i -> i.ammoPerShot)
            .add()
            .build();

    /** Arrakis_Ammo taken by each shot. */
    private float ammoPerShot = 1f;

    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldowns) {
        CommandBuffer<EntityStore> buffer = context.getCommandBuffer();
        Ref<EntityStore> ref = context.getEntity();
        if (buffer == null || ref == null || !ArrakisAmmo.spend(buffer, ref, ammoPerShot)) {
            if (buffer != null) {
                // Tells the Ranged check that this chain was a refused shot, not a gun that launched nothing.
                RangedWatch.noteRefusal(buffer, ref);
            }
            context.getState().state = InteractionState.Failed;
        }
    }
}
