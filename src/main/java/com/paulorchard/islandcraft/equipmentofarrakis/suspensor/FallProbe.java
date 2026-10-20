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
    private boolean flyingFlag;
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

    private boolean gliding;
    private boolean glidingStat;
    private boolean glideStarted;
    private boolean landedGround = true;
    private float healthAtTakeoff;

    /**
     * Flips of the gliding flag and of the stat GlidingActive (with the vertical speed), and at each landing the health change since the
     * takeoff. {@code health} is -1 when unknown.
     */
    List<String> observeGlide(boolean nowGliding, boolean nowStat, boolean nowGround, float health, double vy) {
        List<String> lines = new ArrayList<>();
        if (!glideStarted) {
            glideStarted = true;
            gliding = nowGliding;
            glidingStat = nowStat;
            landedGround = nowGround;
            healthAtTakeoff = health;
            return lines;
        }
        if (nowGliding != gliding) {
            lines.add("gliding " + gliding + " -> " + nowGliding + ": vy " + fmt(vy));
            gliding = nowGliding;
        }
        if (nowStat != glidingStat) {
            lines.add("stat GlidingActive " + glidingStat + " -> " + nowStat + ": vy " + fmt(vy));
            glidingStat = nowStat;
        }
        if (landedGround && !nowGround) {
            healthAtTakeoff = health;
        } else if (!landedGround && nowGround) {
            lines.add("landing: vy " + fmt(vy) + ", health " + fmt(healthAtTakeoff) + " -> " + fmt(health) + " (" + fmt(health - healthAtTakeoff) + ")");
        }
        landedGround = nowGround;
        return lines;
    }

    List<String> observe(boolean nowFalling, boolean nowFar, boolean nowGround, boolean nowFlying, double y, double vy, long nowMs, boolean engaged,
                         int instructions) {
        List<String> lines = new ArrayList<>();
        if (!started) {
            started = true;
            falling = nowFalling;
            far = nowFar;
            ground = nowGround;
            flyingFlag = nowFlying;
            groundY = y;
            groundMs = nowMs;
            lines.add("probe on: falling " + nowFalling + ", fallingFar " + nowFar + ", onGround " + nowGround + ", flying " + nowFlying + ", y " + fmt(y) + ", vy " + fmt(vy));
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
        if (nowFlying != flyingFlag) {
            lines.add("flying " + flyingFlag + " -> " + nowFlying + ": " + where);
        }
        falling = nowFalling;
        far = nowFar;
        ground = nowGround;
        flyingFlag = nowFlying;
        if (engaged) {
            if (nowMs >= nextEngagedMs) {
                nextEngagedMs = nowMs + ENGAGED_INTERVAL_MS;
                lines.add("engaged: vy " + fmt(vy) + ", flying " + nowFlying + ", onGround " + nowGround + ", instructions " + instructions + ", height above the last ground " + fmt(groundY - y));
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
