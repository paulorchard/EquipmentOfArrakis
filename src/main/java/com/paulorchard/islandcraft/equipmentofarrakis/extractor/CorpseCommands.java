package com.paulorchard.islandcraft.equipmentofarrakis.extractor;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.paulorchard.islandcraft.equipmentofarrakis.core.CoreModule;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/** The operator helpers {@code /arrakis blood corpse [role]} and {@code /arrakis blood corpses [clear]}. */
final class CorpseCommands {

    /** The role of the stand-in corpse made by /arrakis blood corpse when none is named. */
    static final String DEFAULT_ROLE = "Cow";
    private static final double SHOW_RADIUS = 16.0;
    private static final int SHOW_COUNT = 5;

    private CorpseCommands() {
    }

    /**
     * Spawns the stand-in mob two blocks ahead of the player, waits {@code TestKillDelaySeconds} (a scheduled hop back to the world thread, no
     * thread sleeps) so the client has the entity, then kills it through the damage system, the path a weapon takes: the engine adds the
     * death component, the death animation is sent and the normal death systems run. Only if the damage did not kill it is the death
     * component added directly (and the report says so).
     */
    static void corpse(CommandContext c, World world, Store<EntityStore> store, String[] args) {
        if (!c.isPlayer() || c.senderAsPlayerRef() == null) {
            CoreModule.say(c, "Run this as a player in the game.");
            return;
        }
        String role = CorpseRules.parseRole(args, DEFAULT_ROLE);
        if (role == null) {
            CoreModule.say(c, "A role name is letters, digits and underscores, for example: arrakis blood corpse Squirrel");
            return;
        }
        Ref<EntityStore> player = c.senderAsPlayerRef();
        Transform look = TargetUtil.getLook(player, store);
        Vector3d dir = new Vector3d(look.getDirection());
        dir.y = 0;
        if (dir.lengthSquared() < 1e-6) {
            dir.set(0, 0, 1);
        }
        Vector3d at = new Vector3d(look.getPosition()).sub(0, 1.5, 0).add(dir.normalize().mul(2.0));
        try {
            var spawned = NPCPlugin.get().spawnNPC(store, role, null, at, new Rotation3f());
            if (spawned == null) {
                CoreModule.say(c, "Could not spawn a " + role + " (is that a role name?).");
                return;
            }
            Ref<EntityStore> mob = spawned.first();
            double delay = Corpses.config().testKillDelaySeconds();
            CoreModule.say(c, "A " + role + " is two blocks ahead; it dies in " + delay + " s.");
            if (delay <= 0) {
                world.execute(() -> kill(c, world, mob, role));
            } else {
                HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> world.execute(() -> kill(c, world, mob, role)), (long) (delay * 1000), TimeUnit.MILLISECONDS);
            }
        } catch (RuntimeException e) {
            CoreModule.say(c, "Could not make the stand-in corpse: " + e);
        }
    }

    /** On the world thread. */
    private static void kill(CommandContext c, World world, Ref<EntityStore> mob, String role) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (!mob.isValid()) {
            report(c, "the " + role + " is gone before it could be killed");
            return;
        }
        String animation = "?";
        boolean bound = false;
        ModelComponent model = store.getComponent(mob, ModelComponent.getComponentType());
        MovementStatesComponent movement = store.getComponent(mob, MovementStatesComponent.getComponentType());
        if (model != null) {
            String id = DeathClips.deathAnimationId(model.getModel(), movement == null ? new MovementStates() : movement.getMovementStates(),
                    DamageCause.PHYSICAL);
            bound = id != null;
            animation = id == null ? "none" : id;
        }
        Damage damage = new Damage(Damage.NULL_SOURCE, DamageCause.PHYSICAL, 100000f);
        DamageSystems.executeDamage(mob, store, damage);
        String shownAnimation = animation;
        boolean shownBound = bound;
        // the death component may be added when the damage is processed: look a tick later
        world.execute(() -> {
            String how = "a damage event (cause Physical)";
            if (mob.isValid() && store.getComponent(mob, DeathComponent.getComponentType()) == null) {
                DeathComponent.tryAddComponent(store, mob, damage);
                how = "DeathComponent added directly (the damage event did not kill it)";
            }
            report(c, role + ": death animation " + shownAnimation + " (" + (shownBound ? "bound" : "not bound") + " in the model), killed by " + how);
        });
    }

    private static void report(CommandContext c, String line) {
        CoreModule.say(c, line);
        Corpses.log("blood corpse: " + line);
    }

    /** The numbers of {@code blood corpses}, plus a line per nearest body; with the word clear, every extended body gets the quick removal. */
    static void corpses(CommandContext c, Store<EntityStore> store, String[] args) {
        ExtractorConfig config = Corpses.config();
        if (args.length > 0 && args[0] != null && args[0].equalsIgnoreCase("clear")) {
            List<Ref<EntityStore>> all = Corpses.LEDGER.releaseAll();
            for (Ref<EntityStore> body : all) {
                Corpses.setTimer(store, body, config.extractedSeconds());
            }
            CoreModule.say(c, all.size() + " bodies set to vanish in " + config.extractedSeconds() + " s.");
            return;
        }
        Vector3d me = null;
        int here = 0;
        if (c.isPlayer() && c.senderAsPlayerRef() != null) {
            TransformComponent transform = store.getComponent(c.senderAsPlayerRef(), TransformComponent.getComponentType());
            if (transform != null) {
                me = new Vector3d(transform.getPosition());
                here = Corpses.LEDGER.inChunk(ChunkUtil.indexChunkFromBlock(me.x, me.z), Corpses.GONE);
            }
        }
        long now = System.nanoTime();
        Map<Integer, Integer> tens = new TreeMap<>();
        List<CorpseLedger.Info<Ref<EntityStore>>> bodies = Corpses.LEDGER.bodies(now, Corpses.GONE);
        for (CorpseLedger.Info<Ref<EntityStore>> body : bodies) {
            tens.merge(CorpseRules.nearestTen(body.remainingSeconds()), 1, Integer::sum);
        }
        CoreModule.say(c, bodies.size() + " extended bodies; " + here + " in your chunk, " + Corpses.LEDGER.busiest(Corpses.GONE)
                + " in the busiest; " + Corpses.LEDGER.cutByCap() + " cut by the limit of " + config.maxCorpsesPerChunk()
                + " per chunk; seconds left (nearest ten): " + tens);
        if (me == null) {
            return;
        }
        Vector3d origin = me;
        List<String> lines = new ArrayList<>();
        bodies.stream()
                .map(b -> new Object[] {b, distance(store, b.key(), origin)})
                .filter(p -> (double) p[1] <= SHOW_RADIUS)
                .sorted(Comparator.comparingDouble(p -> (double) p[1]))
                .limit(SHOW_COUNT)
                .forEach(p -> {
                    @SuppressWarnings("unchecked")
                    CorpseLedger.Info<Ref<EntityStore>> body = (CorpseLedger.Info<Ref<EntityStore>>) p[0];
                    lines.add(describe(store, body, (double) p[1]));
                });
        lines.forEach(line -> CoreModule.say(c, line));
    }

    private static double distance(Store<EntityStore> store, Ref<EntityStore> body, Vector3d from) {
        TransformComponent transform = body.isValid() ? store.getComponent(body, TransformComponent.getComponentType()) : null;
        return transform == null ? Double.MAX_VALUE : transform.getPosition().distance(from);
    }

    private static String describe(Store<EntityStore> store, CorpseLedger.Info<Ref<EntityStore>> body, double distance) {
        Ref<EntityStore> ref = body.key();
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        ModelComponent modelComponent = store.getComponent(ref, ModelComponent.getComponentType());
        String modelId = modelComponent == null ? "?" : modelComponent.getModel().getModelAssetId();
        String status = "unreadable";
        ActiveAnimationComponent animations = store.getComponent(ref, ActiveAnimationComponent.getComponentType());
        if (animations != null) {
            String[] active = animations.getActiveAnimations();
            int slot = AnimationSlot.Status.getValue();
            status = active != null && slot >= 0 && slot < active.length ? String.valueOf(active[slot]) : "no Status slot";
        }
        String holds = "?";
        if (modelComponent != null) {
            Model model = modelComponent.getModel();
            MovementStatesComponent movement = store.getComponent(ref, MovementStatesComponent.getComponentType());
            DeathComponent death = store.getComponent(ref, DeathComponent.getComponentType());
            holds = String.valueOf(DeathClips.verdict(model, movement == null ? new MovementStates() : movement.getMovementStates(),
                    death == null || death.getDeathCause() == null ? DamageCause.PHYSICAL : death.getDeathCause()).holds());
        }
        return String.format("  %.1f blocks: %s, model %s, %.0f s left, Status animation %s, clip holds=%s", distance,
                npc == null ? "?" : npc.getRoleName(), modelId, body.remainingSeconds(), status, holds);
    }
}
