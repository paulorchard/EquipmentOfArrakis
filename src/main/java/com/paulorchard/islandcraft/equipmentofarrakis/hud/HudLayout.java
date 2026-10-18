package com.paulorchard.islandcraft.equipmentofarrakis.hud;

/** Where a bar goes, from the vanilla metrics, the config and the nudge of that bar. Pure. */
final class HudLayout {

    private HudLayout() {
    }

    /** Distance from the bottom of the screen to the bottom of the bar row: the vanilla row, the gap for the top row, the lift and the nudge. */
    static int rowBottom(HudBar bar, double rowGap, double lift, double nudgeY) {
        return (int) Math.round(HudMetrics.ROW_BOTTOM + (bar.topRow ? rowGap : 0) + lift + nudgeY);
    }

    /**
     * Width of the pad between the middle of the screen and the inner end of the bar. A left bar moves right by shrinking it, a right bar
     * moves right by growing it. A bar cannot be nudged past the middle of the screen (the pad stops at 0).
     */
    static int pad(HudBar bar, double nudgeX) {
        double pad = bar.left ? HudMetrics.INNER_GAP - nudgeX : HudMetrics.INNER_GAP + nudgeX;
        return (int) Math.max(0, Math.round(pad));
    }
}
