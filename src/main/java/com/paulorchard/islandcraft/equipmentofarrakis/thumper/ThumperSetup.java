package com.paulorchard.islandcraft.equipmentofarrakis.thumper;

import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;

import java.lang.reflect.Field;

/**
 * Applies the settings that live in the config to the assets, once, at boot, before any client has been sent them: the pitch and volume
 * of the thump sound event ({@code ThumpPitchSemitones}, {@code ThumpVolumeDb}) and, in the deployable config inside the placement
 * interaction, the live count ({@code MaxLivePerPlayer}) and the safety-net lifetime ({@code MaxLiveSeconds}). The asset files hold the
 * defaults (-6 semitones, +3 dB, 3, never); the fields have no setters, so they are written by reflection, and a cached network packet
 * is dropped so that it is built again. A failure is logged and the asset keeps its own value.
 */
final class ThumperSetup {

    static final String PLACE = "Arrakis_Thumper_Place";

    private ThumperSetup() {
    }

    static void apply(ThumperConfig config) {
        sound(config);
        deployable(config);
    }

    private static void sound(ThumperConfig config) {
        SoundEvent event = SoundEvent.getAssetMap().getAsset(Thumpers.THUMP_SOUND);
        if (event == null) {
            return;
        }
        try {
            // the JSON holds semitones and decibels; the loaded event holds a ratio and a gain
            set(event, "pitch", ThumperRules.semitonesToRatio(config.thumpPitchSemitones()));
            set(event, "volume", ThumperRules.dbToGain(config.thumpVolumeDb()));
            event.invalidatePacketCache();
            Thumpers.log("Thumper sound: " + describe(event) + ", max distance " + event.getMaxDistance() + " blocks");
        } catch (ReflectiveOperationException | RuntimeException e) {
            Thumpers.log("Thumper sound: could not apply the config (" + e + "); the event file's own values stay");
        }
    }

    /** The pitch and volume of the loaded event in the units of the JSON and of the config. */
    static String describe(SoundEvent event) {
        return String.format("pitch %.2f semitones (ratio %.4f), volume %.2f dB (gain %.4f)", ThumperRules.ratioToSemitones(event.getPitch()),
                event.getPitch(), ThumperRules.gainToDb(event.getVolume()), event.getVolume());
    }

    private static void deployable(ThumperConfig config) {
        Interaction place = Interaction.getAssetMap().getAsset(PLACE);
        if (place == null) {
            return;
        }
        try {
            Object deployable = get(place, "config");
            set(deployable, "maxLiveCount", config.maxLivePerPlayer());
            set(deployable, "liveDuration", (float) config.maxLiveSeconds());
            set(place, "cachedPacket", null);
            com.hypixel.hytale.builtin.deployables.config.DeployableConfig shown = (com.hypixel.hytale.builtin.deployables.config.DeployableConfig) deployable;
            Thumpers.log("Thumper deployable: id " + shown.getId() + ", max live " + shown.getMaxLiveCount() + ", live duration "
                    + shown.getLiveDuration() + " s (0 = never), invulnerable " + shown.getInvulnerable());
        } catch (ReflectiveOperationException | RuntimeException e) {
            Thumpers.log("Thumper deployable: could not apply the config (" + e + "); the asset's own values stay");
        }
    }

    private static Field find(Class<?> from, String name) throws NoSuchFieldException {
        for (Class<?> type = from; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                // look in the parent class
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object get(Object target, String name) throws ReflectiveOperationException {
        return find(target.getClass(), name).get(target);
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        find(target.getClass(), name).set(target, value);
    }
}
