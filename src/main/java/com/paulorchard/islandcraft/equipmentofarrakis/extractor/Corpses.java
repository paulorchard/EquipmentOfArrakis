package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entity.damage.DeferredCorpseRemoval;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.function.Predicate;

/**
 * What the mod remembers about the long-lived bodies of killed mobs (the ledger, shared by {@link CorpseTimerSystem}, the Blood Extractor
 * and the {@code blood corpses} command) and the one way a body's removal timer is replaced. The engine keeps the timer in a
 * {@link DeferredCorpseRemoval}; its time is a protected field, so a replacement is a new component with the same death particles.
 */
final class Corpses {

    static final CorpseLedger<Ref<EntityStore>> LEDGER = new CorpseLedger<>();
    static final Predicate<Ref<EntityStore>> GONE = ref -> !ref.isValid();

    private static volatile ExtractorConfig config = new ExtractorConfig();

    private Corpses() {
    }

    static ExtractorConfig config() {
        return config;
    }

    static void configure(ExtractorConfig settings) {
        config = settings;
    }

    /**
     * Gives a body {@code seconds} to live, keeping its death particles (so the vanilla effect plays when it goes). A body with no timer
     * gets one. Does nothing for an entity that is gone.
     */
    static void setTimer(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> body, double seconds) {
        if (!body.isValid()) {
            return;
        }
        DeferredCorpseRemoval old = accessor.getComponent(body, DeferredCorpseRemoval.getComponentType());
        accessor.putComponent(body, DeferredCorpseRemoval.getComponentType(),
                new DeferredCorpseRemoval(seconds, old == null ? null : old.getDeathParticles()));
    }

    /** The quick removal: the body goes in ExtractedSeconds and leaves the count. */
    static void quickRemoval(ComponentAccessor<EntityStore> accessor, Ref<EntityStore> body) {
        LEDGER.release(body);
        setTimer(accessor, body, config.extractedSeconds());
    }
}
