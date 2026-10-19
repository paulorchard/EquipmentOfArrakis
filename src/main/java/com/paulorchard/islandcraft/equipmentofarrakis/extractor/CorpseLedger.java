package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The bodies the mod gave the long timer to, engine-free (the key is an entity ref in the game, a string in the tests). Counts them per
 * chunk column and, when a chunk would hold more than the cap, hands back the oldest ones to be cut to the quick removal. A body that is
 * cut or extracted is {@link #release released}: it leaves the count but is remembered, so the system that extends bodies never extends
 * it a second time. Entries whose entity is gone are dropped on every call that changes the ledger. Nothing is saved.
 */
final class CorpseLedger<K> {

    private record Body<K>(K key, long chunk, long death, long endNanos) {
    }

    private final Map<K, Body<K>> bodies = new LinkedHashMap<>();
    private final Set<K> released = new HashSet<>();
    private long sequence;
    private int cut;

    /** True for a body that is counted or was released: the system leaves it alone. */
    synchronized boolean knows(K key) {
        return bodies.containsKey(key) || released.contains(key);
    }

    /**
     * Records a new body and returns the bodies of its chunk that must now be cut (the oldest first, already released). {@code max} 0 or
     * less means no cap. A body already known adds nothing.
     */
    synchronized List<K> add(K key, long chunk, long endNanos, int max, Predicate<K> gone) {
        prune(gone);
        List<K> cuts = new ArrayList<>();
        if (knows(key)) {
            return cuts;
        }
        bodies.put(key, new Body<>(key, chunk, sequence++, endNanos));
        if (max > 0) {
            List<Body<K>> here = new ArrayList<>();
            for (Body<K> body : bodies.values()) {
                if (body.chunk() == chunk) {
                    here.add(body);
                }
            }
            here.sort((a, b) -> Long.compare(a.death(), b.death()));
            for (int i = 0; i < here.size() - max; i++) {
                K oldest = here.get(i).key();
                bodies.remove(oldest);
                released.add(oldest);
                cut++;
                cuts.add(oldest);
            }
        }
        return cuts;
    }

    /** Takes a body out of the count (extracted, or cleared by the operator); it stays known. */
    synchronized void release(K key) {
        if (bodies.remove(key) != null) {
            released.add(key);
        }
    }

    /** Releases every counted body and returns them. */
    synchronized List<K> releaseAll() {
        List<K> all = new ArrayList<>(bodies.keySet());
        for (K key : all) {
            release(key);
        }
        return all;
    }

    synchronized void prune(Predicate<K> gone) {
        bodies.keySet().removeIf(gone);
        released.removeIf(gone);
    }

    synchronized int size(Predicate<K> gone) {
        prune(gone);
        return bodies.size();
    }

    synchronized int inChunk(long chunk, Predicate<K> gone) {
        prune(gone);
        int n = 0;
        for (Body<K> body : bodies.values()) {
            if (body.chunk() == chunk) {
                n++;
            }
        }
        return n;
    }

    /** Count of bodies in the fullest chunk, 0 when none. */
    synchronized int busiest(Predicate<K> gone) {
        prune(gone);
        Map<Long, Integer> perChunk = new HashMap<>();
        int best = 0;
        for (Body<K> body : bodies.values()) {
            best = Math.max(best, perChunk.merge(body.chunk(), 1, Integer::sum));
        }
        return best;
    }

    /** Remaining seconds of each counted body at {@code nowNanos}, in the order they died. */
    synchronized List<Double> remainingSeconds(long nowNanos, Predicate<K> gone) {
        prune(gone);
        List<Double> out = new ArrayList<>();
        for (Body<K> body : bodies.values()) {
            out.add(Math.max(0, (body.endNanos() - nowNanos) / 1e9));
        }
        return out;
    }

    /** A counted body for display. */
    record Info<K>(K key, double remainingSeconds) {
    }

    /** Every counted body with its remaining seconds at {@code nowNanos}. */
    synchronized List<Info<K>> bodies(long nowNanos, Predicate<K> gone) {
        prune(gone);
        List<Info<K>> out = new ArrayList<>();
        for (Body<K> body : bodies.values()) {
            out.add(new Info<>(body.key(), Math.max(0, (body.endNanos() - nowNanos) / 1e9)));
        }
        return out;
    }

    /** How many bodies the cap has cut since the start. */
    synchronized int cutByCap() {
        return cut;
    }
}
