package com.paulorchard.islandcraft.equipmentofarrakis.power;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/** The "Power" section of Equipment_of_Arrakis.json. Power is the vanilla Mana stat; its regeneration is vanilla's. */
public class PowerConfig {

    private boolean logStatDefinition = true;

    public boolean logStatDefinition() {
        return logStatDefinition;
    }

    public static final BuilderCodec<PowerConfig> CODEC = BuilderCodec.builder(PowerConfig.class, PowerConfig::new)
            .documentation("Settings of the power module. Power is the vanilla Mana stat; nothing here changes its regeneration.")
            .append(new KeyedCodec<>("LogStatDefinition", Codec.BOOLEAN, false),
                    (c, v) -> c.logStatDefinition = v, c -> c.logStatDefinition)
            .documentation("True: the Mana stat definition (initial, min, max, every regenerating entry) is written to the log at start.")
            .add()
            .build();
}
