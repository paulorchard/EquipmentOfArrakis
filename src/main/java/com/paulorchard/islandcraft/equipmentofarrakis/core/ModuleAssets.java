package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.assetstore.AssetMap;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSpawner;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Switching a module off. The asset pack is one for the whole jar, so a module that is not in the config's Modules list would still
 * have its items in the game. A module makes a {@link Guard} in its constructor (before the assets load) and calls
 * {@link Guard#activate()} in setup(); at BootEvent a guard that was never activated removes the module's items, interactions,
 * animations, particles, entity effects and sound events from the loaded assets.
 */
public final class ModuleAssets {

    private ModuleAssets() {
    }

    public static final class Guard {
        private final String module;
        private final Predicate<String> ours;
        private final HytaleLogger logger;
        private volatile boolean active;

        Guard(String module, Predicate<String> ours, HytaleLogger logger) {
            this.module = module;
            this.ours = ours;
            this.logger = logger;
        }

        /** The module's setup ran: keep its assets. */
        public void activate() {
            active = true;
        }

        public boolean active() {
            return active;
        }

        void dropIfOff() {
            if (active) {
                return;
            }
            int dropped = drop(Item.getAssetStore(), Item.getAssetMap().getAssetMap())
                    + drop(RootInteraction.getAssetStore(), RootInteraction.getAssetMap().getAssetMap())
                    + drop(Interaction.getAssetStore(), Interaction.getAssetMap().getAssetMap())
                    + drop(ParticleSystem.getAssetStore(), ParticleSystem.getAssetMap().getAssetMap())
                    + drop(ParticleSpawner.getAssetStore(), ParticleSpawner.getAssetMap().getAssetMap())
                    + drop(ItemPlayerAnimations.getAssetStore(), ItemPlayerAnimations.getAssetMap().getAssetMap())
                    + drop(EntityEffect.getAssetStore(), EntityEffect.getAssetMap().getAssetMap())
                    + drop(SoundEvent.getAssetStore(), SoundEvent.getAssetMap().getAssetMap());
            logger.at(Level.INFO).log("Module %s is off in the config: removed %s of its assets", module, dropped);
        }

        private <T extends JsonAssetWithMap<String, M>, M extends AssetMap<String, T>> int drop(AssetStore<String, T, M> store,
                Map<String, T> loaded) {
            List<String> ids = loaded.keySet().stream().filter(ours).toList();
            if (!ids.isEmpty()) {
                store.removeAssets(ids);
            }
            return ids.size();
        }
    }

    /** Call from the module's constructor. {@code ours} says which asset ids belong to the module. */
    public static Guard guard(JavaPlugin plugin, String module, Predicate<String> ours) {
        Guard guard = new Guard(module, ours, plugin.getLogger());
        plugin.getEventRegistry().register(BootEvent.class, event -> guard.dropIfOff());
        return guard;
    }
}
