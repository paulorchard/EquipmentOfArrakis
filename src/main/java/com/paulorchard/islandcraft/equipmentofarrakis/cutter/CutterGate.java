package com.paulorchard.islandcraft.equipmentofarrakis.cutter;

/** The Cutter Ray's Mana rule, engine-free: a shot needs the whole cost in hand; creative needs nothing. */
final class CutterGate {

    private CutterGate() {
    }

    static boolean canShoot(float mana, float cost, boolean creative) {
        return creative || cost <= 0f || mana >= cost;
    }

    /** How many shots a Mana pool pays for, 0 when the cost is not positive-bounded (then unlimited is not modelled: -1). */
    static int shotsFor(float mana, float cost) {
        return cost <= 0f ? -1 : (int) Math.floor(mana / cost + 1e-6);
    }
}
