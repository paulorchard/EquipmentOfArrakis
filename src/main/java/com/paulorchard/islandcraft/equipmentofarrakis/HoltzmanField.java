package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/** The parts of the Holtzman Shield that touch the engine: is it selected, the field effect, the off-hand switch, the break effects. */
final class HoltzmanField {

    static final String HIT_PARTICLES = "Arrakis_Holtzman_Hit";
    static final String SHATTER_PARTICLES = "Arrakis_Holtzman_Shatter";
    static final String BREAK_SOUND = "SFX_Arrakis_Holtzman_Break";

    private HoltzmanField() {
    }

    /** True when the shield is the selected (active) item of the utility slot, the off hand. */
    static boolean selected(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, HoltzmanSettings settings) {
        InventoryComponent.Utility utility = accessor.getComponent(ref, InventoryComponent.Utility.getComponentType());
        ItemStack held = utility == null ? null : utility.getActiveItem();
        return held != null && settings.getItemId().equals(held.getItemId());
    }

    static boolean hasField(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, HoltzmanSettings settings) {
        EffectControllerComponent controller = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        int index = EntityEffect.getAssetMap().getIndex(settings.getEffectId());
        return controller != null && index >= 0 && controller.hasEffect(index);
    }

    /** Raises the field (an infinite entity effect). Does nothing if it is already up. */
    static void raise(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, HoltzmanSettings settings) {
        EffectControllerComponent controller = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(settings.getEffectId());
        int index = EntityEffect.getAssetMap().getIndex(settings.getEffectId());
        if (controller != null && effect != null && index >= 0 && !controller.hasEffect(index)) {
            controller.addInfiniteEffect(ref, index, effect, accessor);
        }
    }

    /** Takes the field off at once (the hum, tint, outline and bubble with it). Does nothing if it is not up. */
    static void lower(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, HoltzmanSettings settings) {
        EffectControllerComponent controller = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        int index = EntityEffect.getAssetMap().getIndex(settings.getEffectId());
        if (controller != null && index >= 0 && controller.hasEffect(index)) {
            controller.removeEffect(ref, index, accessor);
        }
    }

    /**
     * Switches the off hand to empty, on the server and on the client. {@code setActiveSlot} only changes the server's slot (and tells
     * the other players through the equipment update); the owner's own client is not told, so it would go on showing the shield as
     * selected. Then selecting "empty" in the radial sends the server a request for the slot it already has, and the server drops the
     * player with "Attempted to set hotbar slot to already selected hotbar slot!". So the client is told too, with the same
     * {@code SetActiveSlot} packet the engine itself sends when it forces a slot change.
     */
    static void putAway(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        InventoryComponent.Utility utility = accessor.getComponent(ref, InventoryComponent.Utility.getComponentType());
        if (utility == null || utility.getActiveSlot() == InventoryComponent.INACTIVE_SLOT_INDEX) {
            return;
        }
        utility.setActiveSlot(InventoryComponent.INACTIVE_SLOT_INDEX, ref, accessor);
        PlayerRef player = accessor.getComponent(ref, PlayerRef.getComponentType());
        if (player != null) {
            player.getPacketHandler().write(new SetActiveSlot(utility.getSectionId(), InventoryComponent.INACTIVE_SLOT_INDEX));
        }
    }

    /** The quick shimmer over the body: a short effect carrying the impact ModelVFX (0.1 s sweep). */
    static void impact(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, HoltzmanSettings settings) {
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

    /** The shield breaks: the field comes off, the off hand is emptied, the shatter burst and the break sound at the player. */
    static void breakShield(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref, HoltzmanSettings settings, Vector3d centre) {
        lower(accessor, ref, settings);
        putAway(accessor, ref);
        ParticleUtil.spawnParticleEffect(SHATTER_PARTICLES, centre, accessor);
        int world = SoundEvent.getAssetMap().getIndex(BREAK_SOUND);
        int local = SoundEvent.getAssetMap().getIndex(BREAK_SOUND + "_Local");
        if (world > 0) {
            SoundUtil.playSoundEvent3d(ref, world, centre, true, accessor);
        }
        if (local > 0) {
            SoundUtil.playSoundEvent2d(ref, local, SoundCategory.SFX, accessor);
        }
    }
}
