package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * Marks one entity of the Cutter Ray's laser line and says whose it is. It has no codec, so it is never saved, and
 * {@link BeamSegmentSystem} removes a marked entity whose owner no longer has it in {@link MoltenTrail.State#segments}.
 */
public final class BeamSegment implements Component<EntityStore> {

    private static ComponentType<EntityStore, BeamSegment> type;

    private UUID owner;

    public BeamSegment() {
    }

    BeamSegment(UUID owner) {
        this.owner = owner;
    }

    static void register(ComponentType<EntityStore, BeamSegment> registered) {
        type = registered;
    }

    static ComponentType<EntityStore, BeamSegment> getComponentType() {
        return type;
    }

    UUID owner() {
        return owner;
    }

    @Override
    public Component<EntityStore> clone() {
        return new BeamSegment(owner);
    }
}
