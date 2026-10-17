package com.paulorchard.islandcraft.equipmentofarrakis.bloodbag;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/** The "Bloodbag" section of Equipment_of_Arrakis.json. */
public class BloodBagConfig {

    private int capacity = 500;
    private boolean startsFull = false;

    public int capacity() {
        return capacity;
    }

    public boolean startsFull() {
        return startsFull;
    }

    public static final BuilderCodec<BloodBagConfig> CODEC = BuilderCodec.builder(BloodBagConfig.class, BloodBagConfig::new)
            .documentation("Settings of the Blood Bag module (the item Arrakis_Blood_Bag).")
            .append(new KeyedCodec<>("Capacity", Codec.INTEGER, false), (c, v) -> c.capacity = v, c -> c.capacity)
            .documentation("Units of blood a full bag holds (also the maximum of its durability bar).")
            .add()
            .append(new KeyedCodec<>("StartsFull", Codec.BOOLEAN, false), (c, v) -> c.startsFull = v, c -> c.startsFull)
            .documentation("True: a bag with no saved amount counts as full. False (default): empty.")
            .add()
            .build();
}
