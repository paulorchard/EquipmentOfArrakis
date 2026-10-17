package com.paulorchard.islandcraft.equipmentofarrakis.shield;

/** The rules of the Holtzman Shield that need no engine classes, so they can be tested on their own. */
final class HoltzmanRules {

    private HoltzmanRules() {
    }

    /**
     * The field is up while the toggle is ON, the player is alive, there is Mana (anything above 0), the cooldown after the last shot is
     * not running and the shield has not broken and is still waiting to be re-armed. Nothing about blocking or clicking.
     */
    static boolean raised(boolean on, boolean alive, float mana, boolean cooling, boolean broken) {
        return on && alive && mana > 0f && !cooling && !broken;
    }

    /** What to do to the field this tick. */
    enum Change {
        NONE, RAISE, LOWER
    }

    /** Which one-shot sound goes with it: the power-up, the power-down, or none. */
    enum Sound {
        NONE, ON, OFF
    }

    /** A change to the field and the sound that goes with it. */
    record Plan(Change change, Sound sound) {
    }

    /**
     * What to do, from what is: whether the toggle is ON, the player alive, the Mana, whether the cooldown is running, whether the shield
     * is broken (waiting to re-arm), whether the field is up now, and whether this tick is a silent one (the first tick for a player or
     * a new entity, so a join or a change of world makes no sound). A field that should be up and is not is raised with the power-up
     * sound (this is also the re-arm); one that is up and should not be is lowered with the power-down sound, except for a dead player,
     * which is silent. A break lowers the field itself before this runs, so it never reaches here as a change and plays neither sound.
     */
    static Plan plan(boolean on, boolean alive, float mana, boolean cooling, boolean broken, boolean fieldUp, boolean silent) {
        boolean want = raised(on, alive, mana, cooling, broken);
        if (want && !fieldUp) {
            return new Plan(Change.RAISE, silent ? Sound.NONE : Sound.ON);
        }
        if (!want && fieldUp) {
            return new Plan(Change.LOWER, silent || !alive ? Sound.NONE : Sound.OFF);
        }
        return new Plan(Change.NONE, Sound.NONE);
    }

    /** What one absorbed hit did: the Mana left, whether the field broke, and how much Mana the hit cost. */
    record Absorption(float manaAfter, boolean broken, float cost) {
    }

    /**
     * A hit of {@code damage} on a field with {@code mana}, at {@code manaPerDamage} Mana for each point. The whole hit is always
     * absorbed, with no overflow to health; if the cost is equal to or more than the Mana left the Mana goes to 0 and the field
     * breaks. A hit of no damage costs nothing and breaks nothing; a price of 0 or less per point means hits are free.
     */
    static Absorption absorb(float damage, float mana, float manaPerDamage) {
        if (damage <= 0f || manaPerDamage <= 0f) {
            return new Absorption(mana, false, 0f);
        }
        float cost = damage * manaPerDamage;
        if (cost >= mana) {
            return new Absorption(0f, true, cost);
        }
        return new Absorption(mana - cost, false, cost);
    }

    /** The Mana at which a broken shield re-arms: {@code rearmAt}, but never more than the maximum (a lowered maximum must not lock it). */
    static float rearmLevel(float rearmAt, float max) {
        return max > 0f ? Math.min(rearmAt, max) : rearmAt;
    }

    /** True while the shield stays broken: it has broken and the Mana has not come back to the re-arm level. */
    static boolean stillBroken(boolean broken, float mana, float rearmLevel) {
        return broken && mana < rearmLevel;
    }

    /**
     * Where a hit meets the field: the point on the line from the field's centre towards the hit, {@code radius} out from the centre.
     * With no hit location (null), or one that is at the centre, the point straight above the centre by {@code radius} is no good, so
     * the centre itself is returned for the caller to treat as "fall back to the torso".
     */
    static double[] hitPoint(double[] centre, double[] hit, double radius) {
        if (hit == null) {
            return centre.clone();
        }
        double dx = hit[0] - centre[0];
        double dy = hit[1] - centre[1];
        double dz = hit[2] - centre[2];
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-6) {
            return centre.clone();
        }
        double k = radius / length;
        return new double[] {centre[0] + dx * k, centre[1] + dy * k, centre[2] + dz * k};
    }
}
