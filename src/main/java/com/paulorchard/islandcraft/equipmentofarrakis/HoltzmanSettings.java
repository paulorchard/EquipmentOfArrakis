package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * The settings of the Holtzman Shield, read from {@code Holtzman_Shield.json} in the plugin's data folder (written with these defaults
 * at start-up if it is missing). The look (tint colours, outline, bubble size) is not here: it is asset data written by
 * {@code tools/assets/make_holtzman.js}.
 */
public final class HoltzmanSettings {

    public static final String FILE_NAME = "Holtzman_Shield";

    /** The item that raises the field. */
    private String itemId = "Arrakis_Holtzman_Shield";
    /** The entity effect that is the field. */
    private String effectId = "Arrakis_Holtzman_Field";
    /** The short entity effect that carries the quick shimmer when a hit is absorbed. */
    private String impactEffectId = "Arrakis_Holtzman_Impact";
    /** Power taken for each point of projectile damage. */
    private double powerPerDamage = 1.0;
    /** The bubble's radius in blocks, for where the hit flash is put (half the bubble diameter of the generator). */
    private double fieldRadius = 1.1;
    /** Height of the bubble's centre above the player's feet, in blocks. */
    private double fieldCenterHeight = 1.0;
    /** Seconds the field stays down after the last projectile the player launched; the base value, see {@link ShieldCooldown}. */
    private double cooldownSeconds = 1.0;
    /** Whether to log (once per root) a Ranged-tagged interaction that finished without a projectile; see {@link RangedWatch}. */
    private boolean rangedDiagnostic = true;
    /** Root interactions the Ranged check skips: charge-up and reload roots that legitimately launch nothing. */
    private String[] ignoredRoots = {"Bow_Combat_Shoot_Charging", "Bow_Ricochet_Shoot_Charging", "Bow_Shoot_Charging", "Bow_Vamp_Shoot_Charging",
            "Shortbow_Shoot_Charging", "Root_Weapon_Crossbow_Primary_Reload", "Root_Weapon_Crossbow_Primary_Overcharge",
            "Root_Common_Guard_Entry_StaminaCondition"};

    public static final BuilderCodec<HoltzmanSettings> CODEC = BuilderCodec.builder(HoltzmanSettings.class, HoltzmanSettings::new)
            .documentation("The Holtzman Shield: which item, which effect, and what a hit costs.")
            .append(new KeyedCodec<>("ItemId", Codec.STRING, false), (s, v) -> s.itemId = v, s -> s.itemId)
            .documentation("The off-hand item that raises the field.").add()
            .append(new KeyedCodec<>("EffectId", Codec.STRING, false), (s, v) -> s.effectId = v, s -> s.effectId)
            .documentation("The entity effect that is the field.").add()
            .append(new KeyedCodec<>("ImpactEffectId", Codec.STRING, false), (s, v) -> s.impactEffectId = v, s -> s.impactEffectId)
            .documentation("The short entity effect with the quick shimmer played when a hit is absorbed.").add()
            .append(new KeyedCodec<>("PowerPerDamage", Codec.DOUBLE, false), (s, v) -> s.powerPerDamage = v, s -> s.powerPerDamage)
            .documentation("Arrakis power taken for each point of projectile damage.").add()
            .append(new KeyedCodec<>("FieldRadius", Codec.DOUBLE, false), (s, v) -> s.fieldRadius = v, s -> s.fieldRadius)
            .documentation("Radius of the bubble in blocks (where the hit flash is placed).").add()
            .append(new KeyedCodec<>("FieldCenterHeight", Codec.DOUBLE, false), (s, v) -> s.fieldCenterHeight = v, s -> s.fieldCenterHeight)
            .documentation("Height of the bubble's centre above the feet, in blocks.").add()
            .append(new KeyedCodec<>("CooldownSeconds", Codec.DOUBLE, false), (s, v) -> s.cooldownSeconds = v, s -> s.cooldownSeconds)
            .documentation("Seconds the field stays down after the last projectile the player launched.").add()
            .append(new KeyedCodec<>("RangedDiagnostic", Codec.BOOLEAN, false), (s, v) -> s.rangedDiagnostic = v, s -> s.rangedDiagnostic)
            .documentation("Log a warning, once per root, for a Ranged-tagged interaction that finished without launching a projectile.").add()
            .append(new KeyedCodec<>("IgnoredRoots", Codec.STRING_ARRAY, false), (s, v) -> s.ignoredRoots = v, s -> s.ignoredRoots)
            .documentation("Root interactions the Ranged check skips (charge-up and reload roots).").add()
            .build();

    public String getItemId() { return itemId; }
    public String getEffectId() { return effectId; }
    public String getImpactEffectId() { return impactEffectId; }
    public double getPowerPerDamage() { return powerPerDamage; }
    public double getFieldRadius() { return fieldRadius; }
    public double getFieldCenterHeight() { return fieldCenterHeight; }
    public double getCooldownSeconds() { return cooldownSeconds; }
    public boolean isRangedDiagnostic() { return rangedDiagnostic; }
    public String[] getIgnoredRoots() { return ignoredRoots; }
}
