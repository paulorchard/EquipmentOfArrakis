package com.paulorchard.islandcraft.equipmentofarrakis.weapons;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * The "Weapons" section of Equipment_of_Arrakis.json. The numbers that belong to an asset (the shot cooldown 0.33, the dart speed 100,
 * the dart damage 1, the cost per shot 0) are written into the item, projectile and gate JSON by
 * {@code tools/assets/weapons/make_weapons.js}, where they are listed at the top of the file.
 */
public final class WeaponsConfig {

    /** Blocks from the launch point beyond which a dart does no damage and is removed. */
    private double range = 40;
    /** The projectile assets that are darts (the range rule applies to these only). */
    private String[] projectileIds = {"Arrakis_Maula_Dart", "Arrakis_Dartgun_Dart"};

    public static final BuilderCodec<WeaponsConfig> CODEC = BuilderCodec.builder(WeaponsConfig.class, WeaponsConfig::new)
            .documentation("The weapons: how far a dart carries and which projectiles are darts.")
            .append(new KeyedCodec<>("Range", Codec.DOUBLE, false), (s, v) -> s.range = v, s -> s.range)
            .documentation("Blocks from the launch point beyond which a dart does no damage and is removed.").add()
            .append(new KeyedCodec<>("ProjectileIds", Codec.STRING_ARRAY, false), (s, v) -> s.projectileIds = v, s -> s.projectileIds)
            .documentation("Projectile asset ids that are darts.").add()
            .build();

    public double getRange() {
        return range;
    }

    public String[] getProjectileIds() {
        return projectileIds;
    }

    boolean isDart(String projectileId) {
        for (String id : projectileIds) {
            if (id.equals(projectileId)) {
                return true;
            }
        }
        return false;
    }
}
