package com.paulorchard.islandcraft.equipmentofarrakis.power;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/** The "Power" section of Equipment_of_Arrakis.json. Power is the vanilla Mana stat; its regeneration is vanilla's. */
public class PowerConfig {

    private boolean logStatDefinition = true;
    private boolean showMessages = true;

    public boolean logStatDefinition() {
        return logStatDefinition;
    }

    /** True: a tool refused for lack of Mana says so in chat (one line per second per player). */
    public boolean showMessages() {
        return showMessages;
    }

    public static final BuilderCodec<PowerConfig> CODEC = BuilderCodec.builder(PowerConfig.class, PowerConfig::new)
            .documentation("Settings of the power module. Power is the vanilla Mana stat; nothing here changes its regeneration.")
            .append(new KeyedCodec<>("LogStatDefinition", Codec.BOOLEAN, false),
                    (c, v) -> c.logStatDefinition = v, c -> c.logStatDefinition)
            .documentation("True: the Mana stat definition (initial, min, max, every regenerating entry) is written to the log at start.")
            .add()
            .append(new KeyedCodec<>("ShowMessages", Codec.BOOLEAN, false),
                    (c, v) -> c.showMessages = v, c -> c.showMessages)
            .documentation("True: a tool that cannot be used for lack of Mana says \"needs N Mana\" in chat, at most once a second per player.")
            .add()
            .build();
}
