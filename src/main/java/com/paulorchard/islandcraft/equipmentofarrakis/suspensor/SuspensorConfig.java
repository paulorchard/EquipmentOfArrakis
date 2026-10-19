package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * The "Suspensor" section of Equipment_of_Arrakis.json. Speeds are the fly speeds of the movement settings; the lab found that 0.5 in
 * Adventure feels like 0.1 in creative, so both marks start at 1.0 and the numbers are meant to be tuned live
 * ({@code /arrakis suspensor set}).
 */
public class SuspensorConfig {

    /** The speeds of one mark. */
    public static class Mark {
        private double horizontalSpeed;
        private double verticalSpeed;

        public Mark() {
        }

        Mark(double horizontalSpeed, double verticalSpeed) {
            this.horizontalSpeed = horizontalSpeed;
            this.verticalSpeed = verticalSpeed;
        }

        public double horizontalSpeed() {
            return Math.max(0, horizontalSpeed);
        }

        public double verticalSpeed() {
            return Math.max(0, verticalSpeed);
        }

        void set(double horizontal, double vertical) {
            horizontalSpeed = horizontal;
            verticalSpeed = vertical;
        }

        public static final BuilderCodec<Mark> CODEC = BuilderCodec.builder(Mark.class, Mark::new)
                .append(new KeyedCodec<>("HorizontalSpeed", Codec.DOUBLE, false), (c, v) -> c.horizontalSpeed = v, c -> c.horizontalSpeed).add()
                .append(new KeyedCodec<>("VerticalSpeed", Codec.DOUBLE, false), (c, v) -> c.verticalSpeed = v, c -> c.verticalSpeed).add()
                .build();
    }

    private Mark mk3 = new Mark(1.0, 1.0);
    private Mark mk2 = new Mark(1.0, 0.0);
    private double manaPerSecond = 1.0;
    private double resumeMana = 5.0;
    private boolean hum = true;
    private String mk1 = "not read yet: the slow-descent mark is not built";

    public Mark mk3() {
        return mk3;
    }

    public Mark mk2() {
        return mk2;
    }

    /** Mana the belt costs a second while the player flies (creative pays nothing, and the belt does nothing there). */
    public double manaPerSecond() {
        return Math.max(0, manaPerSecond);
    }

    /** After a cut-off at 0 Mana the belt arms again when Mana is back at this value or more. */
    public double resumeMana() {
        return Math.max(0.1, resumeMana);
    }

    /** True: the wind loop plays while the belt flies. */
    public boolean hum() {
        return hum;
    }

    /** Placeholder for Mk I (slow descent): documented, never read. */
    public String mk1() {
        return mk1;
    }

    SuspensorConfig.Mark of(BeltMachine.Mark mark) {
        return mark == BeltMachine.Mark.III ? mk3 : mk2;
    }

    public static final BuilderCodec<SuspensorConfig> CODEC = BuilderCodec.builder(SuspensorConfig.class, SuspensorConfig::new)
            .documentation("Settings of the suspensor module: the Suspensor Belt Mk II and Mk III toggles (flight on the client's double tap of Space).")
            .append(new KeyedCodec<>("Mk3", Mark.CODEC, false), (c, v) -> c.mk3 = v, c -> c.mk3)
            .documentation("Mk III: HorizontalSpeed and VerticalSpeed of the flight (the fly speeds of the movement settings; 1.0 and 1.0 to start).").add()
            .append(new KeyedCodec<>("Mk2", Mark.CODEC, false), (c, v) -> c.mk2 = v, c -> c.mk2)
            .documentation("Mk II: the same, with VerticalSpeed 0.0 (horizontal flight only).").add()
            .append(new KeyedCodec<>("Mk1", Codec.STRING, false), (c, v) -> c.mk1 = v, c -> c.mk1)
            .documentation("Placeholder for the slow-descent mark; nothing reads it yet.").add()
            .append(new KeyedCodec<>("ManaPerSecond", Codec.DOUBLE, false), (c, v) -> c.manaPerSecond = v, c -> c.manaPerSecond)
            .documentation("Mana per second while flying.").add()
            .append(new KeyedCodec<>("ResumeMana", Codec.DOUBLE, false), (c, v) -> c.resumeMana = v, c -> c.resumeMana)
            .documentation("After the belt cut a flight off at 0 Mana, it works again at this much Mana.").add()
            .append(new KeyedCodec<>("Hum", Codec.BOOLEAN, false), (c, v) -> c.hum = v, c -> c.hum)
            .documentation("Play the wind loop while flying.").add()
            .build();
}
