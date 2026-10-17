package com.paulorchard.islandcraft.equipmentofarrakis.shield;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * The "Shield" section of Equipment_of_Arrakis.json: the Holtzman Shield. The look (tint colours, outline, bubble size) is not here,
 * it is asset data (the Arrakis_Holtzman_* effects, particles and sounds).
 */
public final class ShieldConfig {

    /** The entity effect that is the field. */
    private String effectId = "Arrakis_Holtzman_Field";
    /** The short entity effect that carries the quick shimmer when a hit is absorbed. */
    private String impactEffectId = "Arrakis_Holtzman_Impact";
    /** Mana taken for each point of projectile damage. */
    private double manaPerDamage = 1.0;
    /** The Mana the player must have again after a break before the field rises by itself. */
    private double rearmMana = 100.0;
    /** The bubble radius in blocks, for where the hit flash is put (half the bubble diameter). */
    private double fieldRadius = 1.1;
    /** Height of the bubble centre above the feet, in blocks. */
    private double fieldCenterHeight = 1.0;
    /** Seconds the field stays down after the last shot; the base value, see {@link ShieldCooldown}. */
    private double cooldownSeconds = 1.0;

    public static final BuilderCodec<ShieldConfig> CODEC = BuilderCodec.builder(ShieldConfig.class, ShieldConfig::new)
            .documentation("The Holtzman Shield: a toggle on the picker page that absorbs projectile damage for Mana.")
            .append(new KeyedCodec<>("EffectId", Codec.STRING, false), (s, v) -> s.effectId = v, s -> s.effectId)
            .documentation("The entity effect that is the field.").add()
            .append(new KeyedCodec<>("ImpactEffectId", Codec.STRING, false), (s, v) -> s.impactEffectId = v, s -> s.impactEffectId)
            .documentation("The short entity effect with the quick shimmer played when a hit is absorbed.").add()
            .append(new KeyedCodec<>("ManaPerDamage", Codec.DOUBLE, false), (s, v) -> s.manaPerDamage = v, s -> s.manaPerDamage)
            .documentation("Mana taken for each point of projectile damage absorbed. Creative pays nothing.").add()
            .append(new KeyedCodec<>("RearmMana", Codec.DOUBLE, false), (s, v) -> s.rearmMana = v, s -> s.rearmMana)
            .documentation("After a break the field rises again by itself once Mana is back to this much (or to the maximum, if lower).").add()
            .append(new KeyedCodec<>("FieldRadius", Codec.DOUBLE, false), (s, v) -> s.fieldRadius = v, s -> s.fieldRadius)
            .documentation("Radius of the bubble in blocks (where the hit flash is placed).").add()
            .append(new KeyedCodec<>("FieldCenterHeight", Codec.DOUBLE, false), (s, v) -> s.fieldCenterHeight = v, s -> s.fieldCenterHeight)
            .documentation("Height of the bubble centre above the feet, in blocks.").add()
            .append(new KeyedCodec<>("CooldownSeconds", Codec.DOUBLE, false), (s, v) -> s.cooldownSeconds = v, s -> s.cooldownSeconds)
            .documentation("Seconds the field stays down after the last projectile or Cutter Ray shot of the player.").add()
            .build();

    public String getEffectId() { return effectId; }
    public String getImpactEffectId() { return impactEffectId; }
    public double getManaPerDamage() { return manaPerDamage; }
    public double getRearmMana() { return rearmMana; }
    public double getFieldRadius() { return fieldRadius; }
    public double getFieldCenterHeight() { return fieldCenterHeight; }
    public double getCooldownSeconds() { return cooldownSeconds; }
}
