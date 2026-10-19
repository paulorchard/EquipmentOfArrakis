// EXPERIMENT: delete with the lab module
package com.paulorchard.islandcraft.equipmentofarrakis.lab;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/** The "Lab" section of Equipment_of_Arrakis.json: the movement lab. */
public class LabConfig {

    private double watchSeconds = 0.5;
    private double manaPerSecond = 1.0;
    private double flySpeed = 0.5;
    private double riseSpeed = 0.5;
    private boolean beltUsesEngineFly = false;

    /** Seconds between two lines of {@code /arrakis lab watch on}. */
    public double watchSeconds() {
        return watchSeconds > 0.05 ? watchSeconds : 0.5;
    }

    /** Mana the belt prototype costs a second while it is active. */
    public double manaPerSecond() {
        return Math.max(0, manaPerSecond);
    }

    /** Horizontal fly speed of the belt prototype (Mk II and Mk III). */
    public double flySpeed() {
        return flySpeed;
    }

    /** Vertical fly speed of Mk III. */
    public double riseSpeed() {
        return riseSpeed;
    }

    /** True: the belt prototype starts and stops flying with the engine's own Player.setFlying (fly Allowed or Forced from the settings) instead of writing fly Forced itself. */
    public boolean beltUsesEngineFly() {
        return beltUsesEngineFly;
    }

    public static final BuilderCodec<LabConfig> CODEC = BuilderCodec.builder(LabConfig.class, LabConfig::new)
            .documentation("EXPERIMENT: the movement lab (delete with the lab module).")
            .append(new KeyedCodec<>("WatchSeconds", Codec.DOUBLE, false), (c, v) -> c.watchSeconds = v, c -> c.watchSeconds).add()
            .append(new KeyedCodec<>("ManaPerSecond", Codec.DOUBLE, false), (c, v) -> c.manaPerSecond = v, c -> c.manaPerSecond).add()
            .append(new KeyedCodec<>("FlySpeed", Codec.DOUBLE, false), (c, v) -> c.flySpeed = v, c -> c.flySpeed).add()
            .append(new KeyedCodec<>("RiseSpeed", Codec.DOUBLE, false), (c, v) -> c.riseSpeed = v, c -> c.riseSpeed).add()
            .append(new KeyedCodec<>("BeltUsesEngineFly", Codec.BOOLEAN, false), (c, v) -> c.beltUsesEngineFly = v, c -> c.beltUsesEngineFly).add()
            .build();
}
