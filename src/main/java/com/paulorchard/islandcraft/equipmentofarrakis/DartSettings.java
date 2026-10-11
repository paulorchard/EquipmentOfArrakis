package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * The settings of the spring guns that are not asset data, read from {@code Dart_Guns.json} in the plugin's data folder (written with
 * these defaults at start-up if it is missing). The numbers that belong to an asset (the shot cooldown 0.33, the dart speed 100, the dart
 * damage 1, the ammo per shot 1) are written into the item, projectile and gate JSON by {@code tools/assets/make_dart_guns.js}, where
 * they are listed at the top of the file.
 */
public final class DartSettings {

    public static final String FILE_NAME = "Dart_Guns";

    /** Blocks from the launch point beyond which a dart does no damage and is removed. */
    private double range = 40;
    /** The projectile assets that are darts (the range rule applies to these only). */
    private String[] projectileIds = {"Arrakis_Maula_Dart", "Arrakis_Dartgun_Dart"};
    /** Ammo gained per second while the player is not shooting (the same rate as Arrakis_Power). */
    private double ammoRechargePerSecond = 10;
    /** Seconds without a shot attempt before the ammo recharges (the same wait as Arrakis_Power, 0.35). */
    private double ammoIdleSeconds = 0.35;

    public static final BuilderCodec<DartSettings> CODEC = BuilderCodec.builder(DartSettings.class, DartSettings::new)
            .documentation("The spring guns: how far a dart carries, which projectiles are darts, and how Arrakis_Ammo recharges.")
            .append(new KeyedCodec<>("Range", Codec.DOUBLE, false), (s, v) -> s.range = v, s -> s.range)
            .documentation("Blocks from the launch point beyond which a dart does no damage and is removed.").add()
            .append(new KeyedCodec<>("ProjectileIds", Codec.STRING_ARRAY, false), (s, v) -> s.projectileIds = v, s -> s.projectileIds)
            .documentation("Projectile asset ids that are darts.").add()
            .append(new KeyedCodec<>("AmmoRechargePerSecond", Codec.DOUBLE, false), (s, v) -> s.ammoRechargePerSecond = v,
                    s -> s.ammoRechargePerSecond)
            .documentation("Arrakis_Ammo gained a second while not shooting.").add()
            .append(new KeyedCodec<>("AmmoIdleSeconds", Codec.DOUBLE, false), (s, v) -> s.ammoIdleSeconds = v, s -> s.ammoIdleSeconds)
            .documentation("Seconds without a shot attempt before Arrakis_Ammo recharges.").add()
            .build();

    public double getRange() { return range; }
    public String[] getProjectileIds() { return projectileIds; }
    public double getAmmoRechargePerSecond() { return ammoRechargePerSecond; }
    public double getAmmoIdleSeconds() { return ammoIdleSeconds; }

    boolean isDart(String projectileId) {
        for (String id : projectileIds) {
            if (id.equals(projectileId)) {
                return true;
            }
        }
        return false;
    }
}
