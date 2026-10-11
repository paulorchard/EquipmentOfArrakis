package com.paulorchard.islandcraft.equipmentofarrakis;

import java.util.Locale;

/** The rules of the on-screen display that need no engine classes, so they can be tested on their own. */
final class HudMath {

    private HudMath() {
    }

    /**
     * The whole number shown for a power value: 0 only when it really is 0 (anything above 0 and under 1 shows 1), rounded to the
     * nearest otherwise, never more than 100.
     */
    static int powerDisplay(float power) {
        if (Float.isNaN(power) || power <= 0f) {
            return 0;
        }
        int rounded = Math.round(power);
        return Math.min(100, Math.max(1, rounded));
    }

    /** True when the shown number is below the threshold (so 20 is white and 19 is red-orange). */
    static boolean isLow(int shown, double threshold) {
        return shown < threshold;
    }

    /** Whether the POWER readout must be sent again: the first time, or when the whole number or the colour changed. */
    static boolean powerChanged(boolean sentBefore, int sentShown, boolean sentLow, int shown, boolean low) {
        return !sentBefore || sentShown != shown || sentLow != low;
    }

    /** Whether enough time has passed for another update at {@code perSecond} (the first update is always due). */
    static boolean due(long lastNanos, long nowNanos, double perSecond) {
        if (lastNanos == 0 || perSecond <= 0) {
            return true;
        }
        return nowNanos - lastNanos >= (long) (1_000_000_000L / perSecond);
    }

    /** The text of the AMMO readout: the number when a gun reports one, else the placeholder. */
    static String ammoText(java.util.OptionalInt ammo, String placeholder) {
        return ammo.isPresent() ? String.valueOf(Math.max(0, ammo.getAsInt())) : placeholder;
    }

    /** {@code #rrggbb(alpha)}: the colour syntax of a .ui style, for the outline. */
    static String outline(double strength) {
        double a = Math.max(0, Math.min(1, strength));
        return String.format(Locale.ROOT, "#000000(%.2f)", a);
    }

    /** What the tool panel should do after a tick. */
    enum Action {
        NONE, SHOW, HIDE
    }

    /**
     * The state of the tool panel: hidden, or showing one tool. {@link #update} is given the tool in use this tick (null for none) and
     * says what to send: show (also when the tool in use changes), hide, or nothing.
     */
    static final class ToolPanel {
        private String shown;

        Action update(String inUse) {
            if (inUse == null) {
                if (shown == null) {
                    return Action.NONE;
                }
                shown = null;
                return Action.HIDE;
            }
            if (inUse.equals(shown)) {
                return Action.NONE;
            }
            shown = inUse;
            return Action.SHOW;
        }

        String shown() {
            return shown;
        }

        /** The client has lost the document (a new one was built): nothing is shown there any more. */
        void forget() {
            shown = null;
        }
    }
}
