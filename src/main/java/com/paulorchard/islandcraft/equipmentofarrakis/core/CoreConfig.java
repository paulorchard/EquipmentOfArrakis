package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/** The "Core" section of Equipment_of_Arrakis.json. */
public class CoreConfig {

    private boolean logSelfCheckAtBoot = true;

    public boolean logSelfCheckAtBoot() {
        return logSelfCheckAtBoot;
    }

    public static final BuilderCodec<CoreConfig> CODEC = BuilderCodec.builder(CoreConfig.class, CoreConfig::new)
            .documentation("Settings of the core module (plugin, module loading, commands, world access).")
            .append(new KeyedCodec<>("LogSelfCheckAtBoot", Codec.BOOLEAN, false),
                    (c, v) -> c.logSelfCheckAtBoot = v, c -> c.logSelfCheckAtBoot)
            .documentation("True: every module's self-check lines are written to the server log once the assets are loaded.")
            .add()
            .build();
}
