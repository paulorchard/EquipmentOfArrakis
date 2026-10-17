package com.paulorchard.islandcraft.equipmentofarrakis.shield;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/** The parts of the Holtzman Shield that touch the engine: the field effect, the break effects, the sounds. */
final class HoltzmanField {

    static final String HIT_PARTICLES = "Arrakis_Holtzman_Hit";
    static final String SHATTER_PARTICLES = "Arrakis_Holtzman_Shatter";
    static final String BREAK_SOUND = "SFX_Arrakis_Holtzman_Break";
    static final String ON_SOUND = "SFX_Arrakis_Holtzman_On";
    static final String OFF_SOUND = "SFX_Arrakis_Holtzman_Off";

    private HoltzmanField() {
    }

    static boolean hasField(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, ShieldConfig settings) {
        EffectControllerComponent controller = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        int index = EntityEffect.getAssetMap().getIndex(settings.getEffectId());
        return controller != null && index >= 0 && controller.hasEffect(index);
    }

    /** Raises the field (an infinite entity effect). Does nothing if it is already up. */
    static void raise(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, ShieldConfig settings) {
        EffectControllerComponent controller = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(settings.getEffectId());
        int index = EntityEffect.getAssetMap().getIndex(settings.getEffectId());
        if (controller != null && effect != null && index >= 0 && !controller.hasEffect(index)) {
            controller.addInfiniteEffect(ref, index, effect, accessor);
        }
    }

    /** Takes the field off at once (the hum, tint, outline and bubble with it). Does nothing if it is not up. */
    static void lower(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, ShieldConfig settings) {
        EffectControllerComponent controller = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        int index = EntityEffect.getAssetMap().getIndex(settings.getEffectId());
        if (controller != null && index >= 0 && controller.hasEffect(index)) {
            controller.removeEffect(ref, index, accessor);
        }
    }

    /** The quick shimmer over the body: a short effect carrying the impact ModelVFX (0.1 s sweep). */
    static void impact(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, ShieldConfig settings) {
        EffectControllerComponent controller = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(settings.getImpactEffectId());
        if (controller != null && effect != null) {
            controller.addEffect(ref, effect, accessor);
        }
    }

    /** The flash where a hit met the field. */
    static void hitFlash(ComponentAccessor<EntityStore> accessor, Vector3d at) {
        ParticleUtil.spawnParticleEffect(HIT_PARTICLES, at, accessor);
    }

    /** The centre of the field: the player feet plus the configured height. */
    static Vector3d centre(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, ShieldConfig settings) {
        TransformComponent transform = accessor.getComponent(ref, TransformComponent.getComponentType());
        Vector3d feet = transform == null ? new Vector3d() : transform.getPosition();
        return new Vector3d(feet.x, feet.y + settings.getFieldCenterHeight(), feet.z);
    }

    /**
     * Plays a sound event pair: {@code id} to the other players, positional and attenuated (the owner left out), and {@code id_Local} to
     * the owner alone, not attenuated. The engine sound utility on the world thread; the hum is different, it is the entity effect own
     * sound field and needs no call.
     */
    static void playPair(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, String id, Vector3d at) {
        int world = SoundEvent.getAssetMap().getIndex(id);
        int local = SoundEvent.getAssetMap().getIndex(id + "_Local");
        if (world > 0) {
            SoundUtil.playSoundEvent3d(ref, world, at, true, accessor);
        }
        if (local > 0) {
            SoundUtil.playSoundEvent2d(ref, local, SoundCategory.SFX, accessor);
        }
    }

    /**
     * The shield breaks: the field comes off, the cooldown is forgotten, the shatter burst and the break sound at the player. The
     * toggle stays ON; the caller marks the state broken so the field comes back by itself when the Mana does.
     */
    static void breakShield(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, ShieldConfig settings, Vector3d centre,
            HoltzmanState state) {
        lower(accessor, ref, settings);
        if (state != null) {
            state.clearCooldown();
        }
        ParticleUtil.spawnParticleEffect(SHATTER_PARTICLES, centre, accessor);
        playPair(accessor, ref, BREAK_SOUND, centre);
    }

    /** Used by the commands: the player ref of an entity, or null. */
    static PlayerRef playerOf(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        return accessor.getComponent(ref, PlayerRef.getComponentType());
    }
}
