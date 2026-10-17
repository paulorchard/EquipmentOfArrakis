package com.paulorchard.islandcraft.equipmentofarrakis.light;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * The "Light" section of Equipment_of_Arrakis.json: the numbers of the Personal Light. By default the light is the light of a held
 * torch (read from the torch item at start: its radius and its brightest channel) tinted cool white; any of Radius, Red, Green,
 * Blue that is 0 or more replaces that number.
 */
public class LightConfig {

    private String torchItemId = "Furniture_Crude_Torch";
    private int radius = -1;
    private int red = -1;
    private int green = -1;
    private int blue = -1;
    private float tintRed = 0.85f;
    private float tintGreen = 0.93f;
    private float tintBlue = 1.0f;

    public String torchItemId() {
        return torchItemId;
    }

    public int radius() {
        return radius;
    }

    public int red() {
        return red;
    }

    public int green() {
        return green;
    }

    public int blue() {
        return blue;
    }

    public float tintRed() {
        return tintRed;
    }

    public float tintGreen() {
        return tintGreen;
    }

    public float tintBlue() {
        return tintBlue;
    }

    public static final BuilderCodec<LightConfig> CODEC = BuilderCodec.builder(LightConfig.class, LightConfig::new)
            .documentation("Settings of the Personal Light: a torch-equivalent cool white light around the player.")
            .append(new KeyedCodec<>("TorchItemId", Codec.STRING, false), (c, v) -> c.torchItemId = v, c -> c.torchItemId)
            .documentation("The item whose light is matched (its Light radius and brightest channel are read at start).")
            .add()
            .append(new KeyedCodec<>("Radius", Codec.INTEGER, false), (c, v) -> c.radius = v, c -> c.radius)
            .documentation("The light radius number. -1 (default): the torch value.")
            .add()
            .append(new KeyedCodec<>("Red", Codec.INTEGER, false), (c, v) -> c.red = v, c -> c.red)
            .documentation("The red channel (0 to 15). -1 (default): the torch brightness times TintRed.")
            .add()
            .append(new KeyedCodec<>("Green", Codec.INTEGER, false), (c, v) -> c.green = v, c -> c.green)
            .documentation("The green channel (0 to 15). -1 (default): the torch brightness times TintGreen.")
            .add()
            .append(new KeyedCodec<>("Blue", Codec.INTEGER, false), (c, v) -> c.blue = v, c -> c.blue)
            .documentation("The blue channel (0 to 15). -1 (default): the torch brightness times TintBlue.")
            .add()
            .append(new KeyedCodec<>("TintRed", Codec.FLOAT, false), (c, v) -> c.tintRed = v, c -> c.tintRed)
            .documentation("Share of the torch brightness in the red channel: cool white is a pale white leaning blue.")
            .add()
            .append(new KeyedCodec<>("TintGreen", Codec.FLOAT, false), (c, v) -> c.tintGreen = v, c -> c.tintGreen)
            .documentation("Share of the torch brightness in the green channel.")
            .add()
            .append(new KeyedCodec<>("TintBlue", Codec.FLOAT, false), (c, v) -> c.tintBlue = v, c -> c.tintBlue)
            .documentation("Share of the torch brightness in the blue channel.")
            .add()
            .build();
}
