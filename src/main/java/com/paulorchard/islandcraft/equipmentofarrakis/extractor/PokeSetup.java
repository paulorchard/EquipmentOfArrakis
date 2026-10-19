package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;

import java.lang.reflect.Field;
import java.util.logging.Level;

/**
 * Applies the settings that live in the config to the poke interaction asset, once, at boot, before any client has been sent it: the item
 * animation ({@code PokeAnimation}) and the run time (the gate's {@code PokeSeconds}). The asset file is the default (Interact, 1 s); the
 * fields have no setters, so they are written by reflection, and the interaction's cached network packet is dropped so it is built again.
 */
final class PokeSetup {

    static final String POKE = "Arrakis_Extractor_Poke";
    static final String GATE = "Arrakis_Extractor_Gate";

    private PokeSetup() {
    }

    static void apply(ExtractorConfig config) {
        Interaction poke = Interaction.getAssetMap().getAsset(POKE);
        if (poke == null) {
            return;
        }
        try {
            Object effects = poke.getEffects();
            if (effects != null) {
                set(effects, "itemAnimationId", config.pokeAnimation());
            }
            if (Interaction.getAssetMap().getAsset(GATE) instanceof ExtractorGateInteraction gate) {
                set(poke, "runTime", gate.pokeSeconds());
            }
            set(poke, "cachedPacket", null);
            Corpses.log("Extractor poke: item animation " + (effects == null ? "?" : ((com.hypixel.hytale.server.core.modules.interaction.interaction.config.InteractionEffects) effects).getItemAnimationId())
                    + ", run time " + poke.getRunTime() + " s");
        } catch (ReflectiveOperationException | RuntimeException e) {
            Corpses.log("Extractor poke: could not apply the config (" + e + "); the asset's own animation stays");
        }
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                // look in the parent class
            }
        }
        throw new NoSuchFieldException(name);
    }
}
