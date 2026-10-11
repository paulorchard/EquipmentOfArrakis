package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A check, for the log only, that catches a gun or tool that fires by a route the Holtzman does not see. It watches the interaction
 * chains of every player: a chain whose root interaction (the first, or the one being run, or one of its forks) carries the vanilla tag
 * {@code Attack: Ranged} and that finishes normally without the player having created a projectile while it ran, is logged once per root
 * per server run:
 * {@code Ranged-tagged interaction <id> ran but created no player projectile; the Holtzman will not drop for it.}
 * The tag is read from the root's asset data ({@code RootInteraction.getData().getRawTags()}), a map lookup. The tag is not the trigger,
 * only the check: the field drops from the projectile (see {@link ShieldShots}).
 *
 * <p>Left out on purpose: the roots in {@code IgnoredRoots} of {@code Holtzman_Shield.json} (charge-up and reload roots launch nothing),
 * a chain that did not finish normally (cancelled, or ended as Failed), and a chain in which one of our gates refused the shot
 * ({@link #noteRefusal}: the dry click at 0 ammo, the Cutter Ray gate). A chain that ends is judged one tick after it was seen to end,
 * because an entity added by that same tick is only visible after it.
 */
final class RangedWatch extends EntityTickingSystem<EntityStore> {

    /** A Ranged chain being followed. */
    private static final class Tracked {
        final String root;
        final long projectilesAtStart;
        final long refusalsAtStart;
        InteractionState endState;

        Tracked(String root, long projectilesAtStart, long refusalsAtStart) {
            this.root = root;
            this.projectilesAtStart = projectilesAtStart;
            this.refusalsAtStart = refusalsAtStart;
        }
    }

    /** What is followed for one player. */
    private static final class Watch {
        final Map<InteractionChain, Tracked> live = new IdentityHashMap<>();
        final List<Tracked> ended = new ArrayList<>();
        /** Chains already judged that the manager still lists (a finished chain can stay in its map for a tick). */
        final Set<InteractionChain> closed = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static final Map<UUID, Watch> WATCHES = new ConcurrentHashMap<>();
    /** Root id to the number of times it was seen ending without a projectile, in the order first seen. */
    private static final Map<String, Integer> WARNINGS = new LinkedHashMap<>();

    private final Supplier<HoltzmanSettings> settings;
    private Query<EntityStore> query;

    RangedWatch(Supplier<HoltzmanSettings> settings) {
        this.settings = settings;
    }

    @Override
    public Query<EntityStore> getQuery() {
        if (query == null) {
            query = Archetype.of(Player.getComponentType(), PlayerRef.getComponentType(), UUIDComponent.getComponentType());
        }
        return query;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    static void forget(UUID player) {
        WATCHES.remove(player);
    }

    /** The same, for the entity a chain runs on: ignored if it has no UUID. */
    static void noteRefusal(com.hypixel.hytale.component.ComponentAccessor<EntityStore> accessor, Ref<EntityStore> ref) {
        UUIDComponent id = ref == null ? null : accessor.getComponent(ref, UUIDComponent.getComponentType());
        if (id != null) {
            noteRefusal(id.getUuid());
        }
    }

    /** A gate refused a shot for this player (no ammo, no power, nothing in reach): the chain it is in is not a missed projectile. */
    static void noteRefusal(UUID player) {
        HoltzmanState.of(player).refusals++;
    }

    /** The warnings so far, root id to count, in the order first seen. */
    static Map<String, Integer> warnings() {
        synchronized (WARNINGS) {
            return new LinkedHashMap<>(WARNINGS);
        }
    }

    /** True when the root carries {@code Attack: Ranged} in its asset data. */
    static boolean isRanged(RootInteraction root) {
        if (root == null || root.getData() == null) {
            return false;
        }
        String[] attack = root.getData().getRawTags().get("Attack");
        if (attack == null) {
            return false;
        }
        for (String value : attack) {
            if ("Ranged".equals(value)) {
                return true;
            }
        }
        return false;
    }

    /** The Ranged-tagged root of a chain (its first root or the one being run), or null. */
    private static RootInteraction rangedRoot(InteractionChain chain) {
        RootInteraction first = chain.getInitialRootInteraction();
        if (isRanged(first)) {
            return first;
        }
        RootInteraction current = chain.getRootInteraction();
        return isRanged(current) ? current : null;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        HoltzmanSettings cfg = settings.get();
        if (!cfg.isRangedDiagnostic()) {
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        UUID id = chunk.getComponent(index, UUIDComponent.getComponentType()).getUuid();
        InteractionManager manager = store.getComponent(ref, InteractionModule.get().getInteractionManagerComponent());
        if (manager == null) {
            return;
        }
        HoltzmanState state = HoltzmanState.of(id);
        Watch watch = WATCHES.computeIfAbsent(id, k -> new Watch());

        // Chains that ended in an earlier tick are judged now, when what they launched is in the world.
        for (Tracked done : watch.ended) {
            judge(done, state, cfg);
        }
        watch.ended.clear();

        Set<InteractionChain> present = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (InteractionChain chain : manager.getChains().values()) {
            collect(chain, present);
        }
        watch.closed.retainAll(present);
        for (InteractionChain chain : present) {
            if (watch.closed.contains(chain)) {
                continue;
            }
            Tracked tracked = watch.live.get(chain);
            if (tracked == null) {
                RootInteraction root = rangedRoot(chain);
                if (root == null) {
                    continue;
                }
                // Counted from the end of the previous tick: a projectile made in the tick the chain started is in.
                tracked = new Tracked(root.getId(), state.projectilesAtPreviousTick, state.refusalsAtPreviousTick);
                watch.live.put(chain, tracked);
            }
            if (chain.getServerState() != InteractionState.NotFinished) {
                // Done, though the manager still lists it: judged next tick.
                tracked.endState = chain.getServerState();
                watch.ended.add(tracked);
                watch.live.remove(chain);
                watch.closed.add(chain);
            }
        }
        var iterator = watch.live.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!present.contains(entry.getKey())) {
                entry.getValue().endState = entry.getKey().getServerState();
                watch.ended.add(entry.getValue());
                iterator.remove();
            }
        }
        state.projectilesAtPreviousTick = state.projectiles;
        state.refusalsAtPreviousTick = state.refusals;
    }

    private static void collect(InteractionChain chain, Set<InteractionChain> into) {
        if (!into.add(chain)) {
            return;
        }
        for (InteractionChain fork : chain.getForkedChains().values()) {
            collect(fork, into);
        }
    }

    private static void judge(Tracked done, HoltzmanState state, HoltzmanSettings cfg) {
        if (done.endState != InteractionState.Finished) {
            return;
        }
        for (String ignored : cfg.getIgnoredRoots()) {
            if (ignored.equals(done.root)) {
                return;
            }
        }
        if (state.projectiles > done.projectilesAtStart || state.refusals > done.refusalsAtStart) {
            return;
        }
        synchronized (WARNINGS) {
            Integer before = WARNINGS.get(done.root);
            WARNINGS.put(done.root, before == null ? 1 : before + 1);
            if (before != null) {
                return;
            }
        }
        EquipmentOfArrakisPlugin.get().getLogger().atWarning().log(
                "Ranged-tagged interaction %s ran but created no player projectile; the Holtzman will not drop for it.", done.root);
    }
}
