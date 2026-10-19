package com.paulorchard.islandcraft.equipmentofarrakis.thumper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the beat system knows about the standing Thumpers: one clock per entity, who owns it, where it is, when it was last seen
 * ticking, and the placements that were accepted but have not been seen yet (with the position they were meant for, so a quick second
 * click cannot slip past the limit or onto the same spot, and so the position the entity really has can be compared with the intended
 * one). Nothing is saved. No engine class; times are nanoseconds from any steady clock.
 */
final class ThumperBook {

    /** Entries not seen for this long (the entity is gone, or its chunk stopped ticking) are dropped by {@link #prune}. */
    static final long STALE_NANOS = 2_000_000_000L;
    /** A reservation older than this is dropped (the placement did not produce an entity). */
    static final long RESERVATION_NANOS = 3_000_000_000L;

    /** One standing Thumper as the book knows it. */
    record Entry(UUID id, UUID owner, double x, double y, double z, long firstSeenNanos, double phase) {
    }

    private record Reservation(long at, double x, double y, double z) {
    }

    private static final class Slot {
        final BeatClock clock;
        final UUID id;
        final UUID owner;
        final long firstSeen;
        double x, y, z;
        long lastSeen;
        /** Distance between the position the placement meant and the one the entity had at first sight; null if unknown or already read. */
        Double drift;

        Slot(BeatClock clock, UUID id, UUID owner, long now) {
            this.clock = clock;
            this.id = id;
            this.owner = owner;
            this.firstSeen = now;
            this.lastSeen = now;
        }

        Entry entry() {
            return new Entry(id, owner, x, y, z, firstSeen, clock.phase());
        }
    }

    private final Map<UUID, Slot> slots = new HashMap<>();
    private final Map<UUID, List<Reservation>> reservations = new HashMap<>();
    private final double beat;
    private final double gap;

    ThumperBook(double beat, double gap) {
        this.beat = beat;
        this.gap = gap;
    }

    /** An accepted placement of {@code owner} at the given position, counted until its entity is seen (or the reservation runs out). */
    synchronized void reserve(UUID owner, long now, double x, double y, double z) {
        reservations.computeIfAbsent(owner, k -> new ArrayList<>()).add(new Reservation(now, x, y, z));
    }

    /**
     * The entity {@code id} ticked. Returns how many hits are due now. A first sighting starts its clock (the first beat is at
     * once) and uses up the oldest reservation of its owner, remembering how far the entity is from the position that was meant.
     */
    synchronized int tick(UUID id, UUID owner, double x, double y, double z, double dt, long now) {
        Slot slot = slots.get(id);
        if (slot == null) {
            slot = new Slot(new BeatClock(beat, gap), id, owner, now);
            List<Reservation> pending = reservations.get(owner);
            if (pending != null && !pending.isEmpty()) {
                Reservation meant = pending.remove(0);
                slot.drift = Math.sqrt(sq(meant.x - x) + sq(meant.y - y) + sq(meant.z - z));
            }
            slots.put(id, slot);
        }
        slot.x = x;
        slot.y = y;
        slot.z = z;
        slot.lastSeen = now;
        return slot.clock.advance(dt);
    }

    /** The drift noted at the first sighting of {@code id}, once; null if there is none (or it was read). */
    synchronized Double popDrift(UUID id) {
        Slot slot = slots.get(id);
        Double drift = slot == null ? null : slot.drift;
        if (slot != null) {
            slot.drift = null;
        }
        return drift;
    }

    /** Standing Thumpers of {@code owner} (seen within the stale time) plus placements still waiting to be seen. */
    synchronized int live(UUID owner, long now) {
        int count = 0;
        for (Slot slot : slots.values()) {
            if (owner.equals(slot.owner) && now - slot.lastSeen <= STALE_NANOS) {
                count++;
            }
        }
        List<Reservation> pending = reservations.get(owner);
        if (pending != null) {
            for (Reservation r : pending) {
                if (now - r.at <= RESERVATION_NANOS) {
                    count++;
                }
            }
        }
        return count;
    }

    /** True when any live Thumper (anybody's, standing or still waiting to be seen) is within {@code radius} of the point. */
    synchronized boolean occupied(double x, double y, double z, double radius, long now) {
        double limit = radius * radius;
        for (Slot slot : slots.values()) {
            if (now - slot.lastSeen <= STALE_NANOS && sq(slot.x - x) + sq(slot.y - y) + sq(slot.z - z) <= limit) {
                return true;
            }
        }
        for (List<Reservation> list : reservations.values()) {
            for (Reservation r : list) {
                if (now - r.at <= RESERVATION_NANOS && sq(r.x - x) + sq(r.y - y) + sq(r.z - z) <= limit) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Drops what is stale: entities not seen for the stale time, reservations that ran out. Returns how many entries went. */
    synchronized int prune(long now) {
        int dropped = 0;
        for (Iterator<Slot> it = slots.values().iterator(); it.hasNext(); ) {
            if (now - it.next().lastSeen > STALE_NANOS) {
                it.remove();
                dropped++;
            }
        }
        for (Iterator<List<Reservation>> it = reservations.values().iterator(); it.hasNext(); ) {
            List<Reservation> list = it.next();
            list.removeIf(r -> now - r.at > RESERVATION_NANOS);
            if (list.isEmpty()) {
                it.remove();
            }
        }
        return dropped;
    }

    /** The standing Thumpers (seen within the stale time), for the state command. */
    synchronized List<Entry> entries(long now) {
        List<Entry> list = new ArrayList<>();
        for (Slot slot : slots.values()) {
            if (now - slot.lastSeen <= STALE_NANOS) {
                list.add(slot.entry());
            }
        }
        list.sort((a, b) -> Long.compare(a.firstSeenNanos(), b.firstSeenNanos()));
        return list;
    }

    synchronized int size() {
        return slots.size();
    }

    synchronized void clear() {
        slots.clear();
        reservations.clear();
    }

    private static double sq(double v) {
        return v * v;
    }
}
