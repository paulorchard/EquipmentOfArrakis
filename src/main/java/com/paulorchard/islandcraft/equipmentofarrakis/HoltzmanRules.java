package com.paulorchard.islandcraft.equipmentofarrakis;

/** The rules of the Holtzman Shield that need no engine classes, so they can be tested on their own. */
final class HoltzmanRules {

    private HoltzmanRules() {
    }

    /**
     * The field is up while the shield is the selected off-hand item, the player is alive, and there is power: nothing about
     * blocking or clicking.
     */
    static boolean raised(boolean selected, boolean alive, float power) {
        return selected && alive && power > 0f;
    }

    /** What one absorbed hit did: the power left, whether the field broke, and how much power the hit cost. */
    record Absorption(float powerAfter, boolean broken, float cost) {
    }

    /**
     * A hit of {@code damage} on a field with {@code power}, at {@code powerPerDamage} power for each point. The whole hit is always
     * absorbed, with no overflow to health; if the cost is equal to or more than the power left the power goes to 0 and the field
     * breaks. A hit of no damage costs nothing and breaks nothing; a price of 0 or less per point means hits are free.
     */
    static Absorption absorb(float damage, float power, float powerPerDamage) {
        if (damage <= 0f || powerPerDamage <= 0f) {
            return new Absorption(power, false, 0f);
        }
        float cost = damage * powerPerDamage;
        if (cost >= power) {
            return new Absorption(0f, true, cost);
        }
        return new Absorption(power - cost, false, cost);
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
