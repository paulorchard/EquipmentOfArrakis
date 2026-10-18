package com.paulorchard.islandcraft.equipmentofarrakis.hud;

/** The images this module ships in VanillaDerived/ (names without the @2x.png). */
final class HudArt {

    /** Derived images, used by both routes: a white silhouette, grey copies of fills, mirrored error overlays. */
    static final String[] DERIVED = {"StaminaIconWhite", "NeutralHealthBarFill", "NeutralCreativeHealthBarFill", "NeutralManaFill",
            "NeutralCreativeManaFill", "ErrorLeft", "ErrorOutlineLeft"};
    /** Plain copies of vanilla files, used by the Copy route only (Misc.png and Natural-Fluid.png come on top of these, unnumbered). */
    static final String[] COPIES = {"HealthBackground", "HealthBarFill", "HealthBarEffect", "CreativeHealthBarFill", "HealthIcon",
            "CreativeHealthIcon", "ManaBackground", "ManaFill", "ManaEffect", "CreativeManaFill", "ManaBackgroundError", "ManaBackgroundErrorOutline"};

    private HudArt() {
    }
}
