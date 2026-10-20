package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The fall probe of one player (no engine types): feed it the client's flags each tick and it returns a line for every flip of
 * falling, fallingFar and onGround (with the height above the last ground, the time since the last onGround and the client vertical
 * speed), and, while a descent is engaged, the vertical speed every quarter second. It is how the fallingFar threshold is learned.
 */
final class FallProbe {

    static final long ENGAGED_INTERVAL_MS = 250;

    private final boolean chat;
    private boolean falling;
    private boolean far;
    private boolean ground = true;
    private boolean started;
    private double groundY;
    private long groundMs;
    private long nextEngagedMs;
    private double fastest;

    FallProbe(boolean chat) {
        this.chat = chat;
    }

    boolean chat() {
        return chat;
    }

    List<String> observe(boolean nowFalling, boolean nowFar, boolean nowGround, double y, double vy, long nowMs, boolean engaged) {
        List<String> lines = new ArrayList<>();
        if (!started) {
            started = true;
            falling = nowFalling;
            far = nowFar;
            ground = nowGround;
            groundY = y;
            groundMs = nowMs;
            lines.add("probe on: falling " + nowFalling + ", fallingFar " + nowFar + ", onGround " + nowGround + ", y " + fmt(y) + ", vy " + fmt(vy));
            return lines;
        }
        double fastestFall = fastest;
        if (nowGround) {
            groundY = y;
            groundMs = nowMs;
            fastest = 0;
        } else {
            fastest = Math.min(fastest, vy);
        }
        String where = "height above the last ground " + fmt(groundY - y) + ", " + fmt((nowMs - groundMs) / 1000.0) + " s since onGround, vy " + fmt(vy);
        if (nowFalling != falling) {
            lines.add("falling " + falling + " -> " + nowFalling + ": " + where);
        }
        if (nowFar != far) {
            lines.add("fallingFar " + far + " -> " + nowFar + ": " + where);
        }
        if (nowGround != ground) {
            lines.add("onGround " + ground + " -> " + nowGround + ": " + where + (nowGround ? ", fastest fall " + fmt(fastestFall) : ""));
        }
        falling = nowFalling;
        far = nowFar;
        ground = nowGround;
        if (engaged) {
            if (nowMs >= nextEngagedMs) {
                nextEngagedMs = nowMs + ENGAGED_INTERVAL_MS;
                lines.add("engaged: vy " + fmt(vy) + ", height above the last ground " + fmt(groundY - y));
            }
        } else {
            nextEngagedMs = 0;
        }
        return lines;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
