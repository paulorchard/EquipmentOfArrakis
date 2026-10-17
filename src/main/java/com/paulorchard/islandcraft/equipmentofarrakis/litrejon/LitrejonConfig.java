package com.paulorchard.islandcraft.equipmentofarrakis.litrejon;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/** The "Litrejon" section of Equipment_of_Arrakis.json. */
public class LitrejonConfig {

    private int capacity = 500;
    private boolean startsFull = false;
    private int pourPerClick = 50;

    public int capacity() {
        return capacity;
    }

    public boolean startsFull() {
        return startsFull;
    }

    public int pourPerClick() {
        return pourPerClick;
    }

    public static final BuilderCodec<LitrejonConfig> CODEC = BuilderCodec.builder(LitrejonConfig.class, LitrejonConfig::new)
            .documentation("Settings of the LitreJon module (the Equipment flask Arrakis_Tool_Litrejon).")
            .append(new KeyedCodec<>("Capacity", Codec.INTEGER, false), (c, v) -> c.capacity = v, c -> c.capacity)
            .documentation("Units of water a full flask holds (also the maximum of its durability bar).")
            .add()
            .append(new KeyedCodec<>("StartsFull", Codec.BOOLEAN, false), (c, v) -> c.startsFull = v, c -> c.startsFull)
            .documentation("True: a flask with no saved amount counts as full. False (default): empty.")
            .add()
            .append(new KeyedCodec<>("PourPerClick", Codec.INTEGER, false), (c, v) -> c.pourPerClick = v, c -> c.pourPerClick)
            .documentation("Most units one left click moves from the flask into the player (also limited by what the player lacks).")
            .add()
            .build();
}
