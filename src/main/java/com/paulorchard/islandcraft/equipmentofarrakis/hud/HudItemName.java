package com.paulorchard.islandcraft.equipmentofarrakis.hud;

/**
 * The held item name: the client draws it (Label #ActiveItemNameLabel, the last element of the vanilla Hotbar.ui, hidden until the client shows
 * it when the active item changes). The server has no field for it, so it is moved, not hidden, with an Anchor sent for that selector.
 * Whether a selector of a custom HUD reaches a vanilla element is only known from a client: a selector that does not resolve disconnects the
 * player, so nothing is sent at join unless {@code ItemNameMove} is set, and the dev command sends it once, to one player. Pure.
 */
final class HudItemName {

    static final String SELECTOR = "#ActiveItemNameLabel";

    /**
     * Whether a selector of a custom HUD reaches the vanilla label: NO. Tested on a client: "Selected element in CustomUI command was not found. Selector:
     * #ActiveItemNameLabel.Anchor", and the player was disconnected. Nothing is sent for this selector any more (build or command); the code stays for a
     * future game version that lets a HUD reach built-in elements.
     */
    static final boolean REACHABLE = false;

    /** The sides of an Anchor; null means that side is not set. */
    record Spec(Integer top, Integer left, Integer bottom, Integer width, Integer height) {
    }

    private HudItemName() {
    }

    /** The anchor of the vanilla label: Bottom = slot spacing + slot size + 50, Height 40, nothing else (centred). */
    static Spec vanilla() {
        return new Spec(null, null, HudMetrics.ITEM_NAME_BOTTOM, null, HudMetrics.ITEM_NAME_HEIGHT);
    }

    /** The moved anchor: Top, Left, Width and Height and no Bottom, so the vanilla bottom anchor is replaced and not combined with it. */
    static Spec moved(HudConfig config) {
        return moved(config.getItemNameTop(), config.getItemNameLeft(), config);
    }

    static Spec moved(double top, double left, HudConfig config) {
        return new Spec((int) Math.round(top), (int) Math.round(left), null, (int) Math.round(config.getItemNameWidth()),
                (int) Math.round(config.getItemNameHeight()));
    }

    /** True when the move is sent as the HUD is built (once per build, never on a tick): only when the config asks for it. */
    static boolean sendAtBuild(HudConfig config) {
        return REACHABLE && config.isItemNameMove();
    }
}
