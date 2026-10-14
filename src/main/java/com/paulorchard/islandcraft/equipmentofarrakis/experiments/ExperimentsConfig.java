// EXPERIMENT: delete with the whole experiments module (see EXPERIMENT_LOG.md, "Removing the experiments").
package com.paulorchard.islandcraft.equipmentofarrakis.experiments;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * The "Experiments" section of Equipment_of_Arrakis.json. The settings of the Flame Cutter (range, Mana per second, damage scale,
 * stand-in tool) are data in Arrakis_Exp_Cutter_Tick.json, not here.
 */
public class ExperimentsConfig {

    private boolean logSelfCheck = true;

    public boolean logSelfCheck() {
        return logSelfCheck;
    }

    public static final BuilderCodec<ExperimentsConfig> CODEC = BuilderCodec.builder(ExperimentsConfig.class, ExperimentsConfig::new)
            .documentation("Settings of the experiments module (throw-away look-and-feel experiments).")
            .append(new KeyedCodec<>("LogSelfCheck", Codec.BOOLEAN, false), (c, v) -> c.logSelfCheck = v, c -> c.logSelfCheck)
            .documentation("True: what the experiments found in the loaded assets is written to the log at start.")
            .add()
            .build();
}
