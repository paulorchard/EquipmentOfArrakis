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
    /** Power taken for each point of projectile damage. */
    private double powerPerDamage = 1.0;
    /** The bubble's radius in blocks, for where the hit flash is put (half the bubble diameter of the generator). */
    private double fieldRadius = 1.1;
    /** Height of the bubble's centre above the player's feet, in blocks. */
    private double fieldCenterHeight = 1.0;

    public static final BuilderCodec<HoltzmanSettings> CODEC = BuilderCodec.builder(HoltzmanSettings.class, HoltzmanSettings::new)
            .documentation("The Holtzman Shield: which item, which effect, and what a hit costs.")
            .append(new KeyedCodec<>("ItemId", Codec.STRING, false), (s, v) -> s.itemId = v, s -> s.itemId)
            .documentation("The off-hand item that raises the field.").add()
            .append(new KeyedCodec<>("EffectId", Codec.STRING, false), (s, v) -> s.effectId = v, s -> s.effectId)
            .documentation("The entity effect that is the field.").add()
            .append(new KeyedCodec<>("PowerPerDamage", Codec.DOUBLE, false), (s, v) -> s.powerPerDamage = v, s -> s.powerPerDamage)
            .documentation("Arrakis power taken for each point of projectile damage.").add()
            .append(new KeyedCodec<>("FieldRadius", Codec.DOUBLE, false), (s, v) -> s.fieldRadius = v, s -> s.fieldRadius)
            .documentation("Radius of the bubble in blocks (where the hit flash is placed).").add()
            .append(new KeyedCodec<>("FieldCenterHeight", Codec.DOUBLE, false), (s, v) -> s.fieldCenterHeight = v, s -> s.fieldCenterHeight)
            .documentation("Height of the bubble's centre above the feet, in blocks.").add()
            .build();

    public String getItemId() { return itemId; }
    public String getEffectId() { return effectId; }
    public double getPowerPerDamage() { return powerPerDamage; }
    public double getFieldRadius() { return fieldRadius; }
    public double getFieldCenterHeight() { return fieldCenterHeight; }
}
