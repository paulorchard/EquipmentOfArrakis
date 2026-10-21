package com.paulorchard.islandcraft.equipmentofarrakis.suspensor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TEST DEVICE: the flight probe of one player (proof A of the descent), with no engine types. Fed one {@link Sample} a tick, it says
 * every flip of the movement flags (with the height above the last ground and the time since onGround) and, every 0.25 s while the
 * player flies, the true flags, the two vertical speeds, the wish movement, Mana and whether the belt is armed. Delete with the proof
 * (see the cleanup list in MODULES.md).
 */
final class FlightProbe {

    /** {@code wish}: the three wish movement numbers as text, or why they are not readable. */
    record Sample(long tick, long nowMs, float dt, boolean flying, boolean jumping, boolean crouching, boolean onGround, boolean falling,
                  boolean fallingFar, double y, double clientVy, String wish, float mana, boolean armed) {
    }

    static final long PERIOD_MS = 250;

    private Sample last;
    private double lastGroundY = Double.NaN;
    private long lastGroundMs = -1;
    private long nextPeriodic;

    /** The lines to log for this sample (none when nothing flipped and no period is due). */
    synchronized List<String> observe(Sample s) {
        List<String> out = new ArrayList<>();
        if (s.onGround()) {
            lastGroundY = s.y();
            lastGroundMs = s.nowMs();
        }
        double actualVy = last == null || s.dt() <= 0 ? 0 : (s.y() - last.y()) / s.dt();
        if (last != null) {
            flip(out, s, "flying", last.flying(), s.flying());
            flip(out, s, "jumping", last.jumping(), s.jumping());
            flip(out, s, "crouching", last.crouching(), s.crouching());
            flip(out, s, "onGround", last.onGround(), s.onGround());
            flip(out, s, "falling", last.falling(), s.falling());
            flip(out, s, "fallingFar", last.fallingFar(), s.fallingFar());
        } else {
            out.add(String.format(Locale.ROOT, "tick %d ms %d probe started: flags %s, y %.2f", s.tick(), s.nowMs(), flags(s), s.y()));
            nextPeriodic = s.nowMs();
        }
        if (s.flying() && s.nowMs() >= nextPeriodic) {
            nextPeriodic = s.nowMs() + PERIOD_MS;
            out.add(String.format(Locale.ROOT, "tick %d ms %d flying: flags %s, client vy %.2f, actual vy %.2f, wish %s, height %s, Mana %.1f, armed %s",
                    s.tick(), s.nowMs(), flags(s), s.clientVy(), actualVy, s.wish(), height(s), s.mana(), s.armed()));
        }
        last = s;
        return out;
    }

    private void flip(List<String> out, Sample s, String name, boolean before, boolean now) {
        if (before != now) {
            out.add(String.format(Locale.ROOT, "tick %d ms %d flip %s %s->%s: height %s, since onGround %s, client vy %.2f, flags %s", s.tick(), s.nowMs(), name,
                    before, now, height(s), sinceGround(s), s.clientVy(), flags(s)));
        }
    }

    private String height(Sample s) {
        return Double.isNaN(lastGroundY) ? "n/a (no ground seen)" : String.format(Locale.ROOT, "%.2f above ground", s.y() - lastGroundY);
    }

    private String sinceGround(Sample s) {
        return lastGroundMs < 0 ? "n/a" : String.format(Locale.ROOT, "%.2f s", (s.nowMs() - lastGroundMs) / 1000.0);
    }

    static String flags(Sample s) {
        StringBuilder b = new StringBuilder("[");
        add(b, s.flying(), "flying");
        add(b, s.jumping(), "jumping");
        add(b, s.crouching(), "crouching");
        add(b, s.onGround(), "onGround");
        add(b, s.falling(), "falling");
        add(b, s.fallingFar(), "fallingFar");
        return b.append(']').toString();
    }

    private static void add(StringBuilder b, boolean on, String name) {
        if (on) {
            b.append(b.length() > 1 ? " " : "").append(name);
        }
    }
}
