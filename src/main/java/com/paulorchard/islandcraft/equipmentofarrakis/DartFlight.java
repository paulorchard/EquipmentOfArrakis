package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Put on every dart entity by {@link DartFlightSystem} the moment it is spawned: where it was launched from. Not saved (no codec): a
 * dart does not outlive a restart. The range rule ({@link DartRangeSystem}, {@link DartRangeDamageSystem}) measures from here.
 */
final class DartFlight implements Component<EntityStore> {

    /** Set by the plugin when the component is registered. */
    static ComponentType<EntityStore, DartFlight> type;

    final double[] launch;

    DartFlight() {
        this(new double[3]);
    }

    DartFlight(double[] launch) {
        this.launch = launch;
    }

    @Override
    public Component<EntityStore> clone() {
        return new DartFlight(launch.clone());
    }
}
