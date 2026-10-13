package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.Pair;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Development: flies real darts in a headless server, without a player, and reports what the engine did with them. It lays a stone floor
 * at y 199 in the chunks around 0, 200, 0, spawns an immovable training dummy (the vanilla {@code Test_Dummy_Stationary}, 1,000,000 health)
 * as the shooter and one in each of nine lanes at distances from 10 to 50 blocks, then launches a dart of each gun down every lane the way
 * the {@code LaunchProjectile} interaction does ({@code assembleDefaultProjectile}, {@code shoot}, {@code addEntity}) and reads the dummies'
 * health. It also samples one free dart of each gun every 20 ms to measure the speed, the drop (gravity) and where the range rule ends it,
 * and fires a burst of 30 darts (into the floor, into the sky and along the lane) to compare the entity count before and after.
 * {@code arrakis dart selftest}.
 */
final class DartSelfTest {

    private static final double[] DISTANCES = {10, 25, 36, 38, 39, 40, 41, 43, 50};
    private static final String[] DARTS = {"Arrakis_Maula_Dart", "Arrakis_Dartgun_Dart"};
    private static final double FEET_Y = 200.0;
    private static final double MUZZLE_Y = 200.9;
    private static final String HEALTH = "Health";

    private DartSelfTest() {
    }

    /** One dart being followed: where it started, its positions over time, and when it was gone. */
    private static final class Track {
        final String id;
        final Ref<EntityStore> ref;
        final double[] start;
        final long t0 = System.nanoTime();
        final List<double[]> samples = new ArrayList<>();   // seconds, x, y, z
        double goneAt = -1;

        Track(String id, Ref<EntityStore> ref, double[] start) {
            this.id = id;
            this.ref = ref;
            this.start = start;
        }
    }

    static void run(World world, Consumer<String> say) {
        DartRangeDamageSystem.trace = true;
        // The columns, and the sky sections the floor at y 199 lies in (they may not exist yet), held loaded for the test.
        WorldAccess.loadTestArea(world, -32, 80, -48, 64, 199).whenCompleteAsync((held, error) -> {
            try {
                if (error != null) {
                    say.accept("chunks did not load: " + error);
                    return;
                }
                say.accept("sky sections at y 199 and 200 loaded and held: " + held.loaded() + " of " + held.wanted);
                stage1(world, say, held);
            } catch (RuntimeException e) {
                say.accept("self-test failed: " + e);
            }
        }, world);
        say.accept("loading the chunks, then the test runs for about 12 seconds; the lines follow");
    }

    private static void stage1(World world, Consumer<String> say, WorldAccess.Held held) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        WorldAccess access = new WorldAccess(world);
        int missed = 0;
        for (int x = -4; x <= 60; x++) {
            for (int z = -4; z <= 40; z++) {
                if (!access.setBlock(x, 199, z, "Rock_Stone", 0)) {
                    missed++;
                }
            }
        }
        say.accept("floor of Rock_Stone at y 199: " + (missed == 0 ? "all blocks set" : missed + " blocks NOT set (section not loaded)"));
        Pair<Ref<EntityStore>, ?> shooter = NPCPlugin.get().spawnNPC(store, "Test_Dummy_Stationary", null, new Vector3d(-2, FEET_Y, -2),
                new Rotation3f());
        List<Ref<EntityStore>> targets = new ArrayList<>();
        for (int i = 0; i < DISTANCES.length; i++) {
            Pair<Ref<EntityStore>, ?> dummy = NPCPlugin.get().spawnNPC(store, "Test_Dummy_Stationary", null,
                    new Vector3d(DISTANCES[i], FEET_Y, 4 * i), new Rotation3f());
            targets.add(dummy == null ? null : dummy.left());
        }
        if (shooter == null || targets.contains(null)) {
            say.accept("could not spawn the Test_Dummy_Stationary role");
            held.release();
            return;
        }
        say.accept("spawned a shooter and " + targets.size() + " dummies; waiting for them to settle");
        later(world, 2500, () -> stage2(world, store, say, shooter.left(), targets, held));
    }

    private static void stage2(World world, Store<EntityStore> store, Consumer<String> say, Ref<EntityStore> shooter,
            List<Ref<EntityStore>> targets, WorldAccess.Held held) {
        UUID creator = store.getComponent(shooter, UUIDComponent.getComponentType()).getUuid();
        ammoChecks(store, shooter, say);
        float yaw = yawTowardPlusX();
        say.accept("yaw for +x: " + yaw + " (direction " + direction(new Vector3d(0, 0, 0), yaw, 0) + ")");
        int baseline = store.getEntityCount();
        int baselineProjectiles = projectiles(store);
        say.accept("before any dart: " + baseline + " entities, " + baselineProjectiles + " projectiles");
        for (int i = 0; i < targets.size(); i++) {
            TransformComponent at = store.getComponent(targets.get(i), TransformComponent.getComponentType());
            BoundingBox box = store.getComponent(targets.get(i), BoundingBox.getComponentType());
            say.accept(String.format("dummy %d is at x %.2f z %.2f, box x %.2f to %.2f", i, at.getPosition().x, at.getPosition().z,
                    box.getBoundingBox().min.x, box.getBoundingBox().max.x));
        }
        float[] before = new float[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            before[i] = health(store, targets.get(i));
            for (String dart : DARTS) {
                launch(store, dart, new Vector3d(0, MUZZLE_Y, 4 * i), yaw, 0, creator);
            }
        }
        say.accept("fired two darts (one of each gun) down each lane; health before: " + before[0]);
        // The free flights, in a lane (z = -20) with nothing in it, sampled every 20 ms.
        List<Track> tracks = new ArrayList<>();
        for (String dart : DARTS) {
            Vector3d start = new Vector3d(0, MUZZLE_Y, -20);
            Ref<EntityStore> ref = launch(store, dart, start, yaw, 0, creator);
            tracks.add(new Track(dart, ref, new double[] {start.x, start.y, start.z}));
        }
        ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dart-sampler");
            t.setDaemon(true);
            return t;
        });
        sampler.scheduleAtFixedRate(() -> world.execute(() -> sample(store, tracks)), 0, 20, TimeUnit.MILLISECONDS);
        later(world, 3500, () -> {
            sampler.shutdownNow();
            report(say, store, targets, before, tracks);
            stage3(world, store, say, creator, yaw, baseline, baselineProjectiles, held);
        });
    }

    /** The ammo stat on the shooter dummy (every entity has every stat): its start value, a payment, a refusal, the clamp and a recharge. */
    private static void ammoChecks(Store<EntityStore> store, Ref<EntityStore> who, Consumer<String> say) {
        say.accept("---- ammo (on the shooter dummy; every entity has every stat)");
        say.accept("start value: " + ArrakisAmmo.get(store, who) + " (expect 100)");
        boolean paid = ArrakisAmmo.spend(store, who, 1f);
        say.accept("spend 1: " + paid + ", now " + ArrakisAmmo.get(store, who) + " (expect true, 99)");
        ArrakisAmmo.set(store, who, 0f);
        paid = ArrakisAmmo.spend(store, who, 1f);
        say.accept("at 0, spend 1: " + paid + ", now " + ArrakisAmmo.get(store, who) + " (expect false, 0)");
        ArrakisAmmo.set(store, who, 0.5f);
        paid = ArrakisAmmo.spend(store, who, 1f);
        say.accept("at 0.5, spend 1: " + paid + ", now " + ArrakisAmmo.get(store, who) + " (expect false, 0.5)");
        ArrakisAmmo.recharge(store, who, 0.5f);
        say.accept("recharge 0.5 s: now " + ArrakisAmmo.get(store, who) + " (expect 5.5)");
        say.accept("set 250: " + ArrakisAmmo.set(store, who, 250f) + ", set -4: " + ArrakisAmmo.set(store, who, -4f) + " (expect 100, 0)");
        say.accept("fill: " + ArrakisAmmo.fill(store, who) + " (expect 100)");
    }

    private static void sample(Store<EntityStore> store, List<Track> tracks) {
        for (Track t : tracks) {
            if (t.goneAt >= 0) {
                continue;
            }
            double seconds = (System.nanoTime() - t.t0) / 1e9;
            TransformComponent transform = t.ref.isValid() ? store.getComponent(t.ref, TransformComponent.getComponentType()) : null;
            if (transform == null) {
                t.goneAt = seconds;
                continue;
            }
            Vector3d at = transform.getPosition();
            t.samples.add(new double[] {seconds, at.x, at.y, at.z});
        }
    }

    private static void report(Consumer<String> say, Store<EntityStore> store, List<Ref<EntityStore>> targets, float[] before,
            List<Track> tracks) {
        say.accept("---- damage by distance (two darts per lane, 1 damage each; the dummy's centre is at the distance, its hit box is about 0.3 either side)");
        for (int i = 0; i < targets.size(); i++) {
            float lost = before[i] - health(store, targets.get(i));
            say.accept(String.format("dummy at %.0f blocks: %.0f damage taken", DISTANCES[i], lost));
        }
        say.accept("---- the free flights (launch at x 0, y " + MUZZLE_Y + ", level)");
        for (Track t : tracks) {
            List<double[]> s = t.samples;
            if (s.isEmpty()) {
                say.accept(t.id + ": no samples");
                continue;
            }
            double[] last = s.get(s.size() - 1);
            double[] first = s.get(0);
            say.accept(String.format("%s: %d samples; first at %.3f s, x %.2f (%.2f from launch); last at %.3f s, x %.2f y %.3f; gone at %s s",
                    t.id, s.size(), first[0], first[1], first[1] - t.start[0], last[0], last[1], last[2], t.goneAt < 0 ? "never" : String.format("%.3f", t.goneAt)));
            StringBuilder drops = new StringBuilder();
            for (double mark : new double[] {10, 20, 30, 40}) {
                drops.append(String.format("  drop at %.0f blocks: %s", mark, dropAt(s, t.start, mark)));
            }
            say.accept(drops.toString());
            if (s.size() > 3) {
                double[] a = s.get(Math.min(2, s.size() - 1));
                double[] b = s.get(Math.min(8, s.size() - 1));
                if (b[0] > a[0]) {
                    say.accept(String.format("  speed over the first samples: %.1f blocks a second", (b[1] - a[1]) / (b[0] - a[0])));
                }
            }
        }
    }

    /** The drop below the launch height where the flight crossed {@code mark} blocks along x, by interpolation between samples. */
    private static String dropAt(List<double[]> samples, double[] start, double mark) {
        double[] prev = null;
        for (double[] s : samples) {
            double along = s[1] - start[0];
            if (along >= mark) {
                if (prev == null) {
                    return String.format("%.3f (first sample was already past)", start[1] - s[2]);
                }
                double pa = prev[1] - start[0];
                double f = along == pa ? 0 : (mark - pa) / (along - pa);
                double y = prev[2] + (s[2] - prev[2]) * f;
                return String.format("%.3f blocks", start[1] - y);
            }
            prev = s;
        }
        return "never got there";
    }

    private static void stage3(World world, Store<EntityStore> store, Consumer<String> say, UUID creator, float yaw, int baseline,
            int baselineProjectiles, WorldAccess.Held held) {
        say.accept("---- entity count after the first volleys (7 s on): " + store.getEntityCount() + " entities (" + baseline + " before any dart), "
                + projectiles(store) + " projectiles (" + baselineProjectiles + " before)");
        int fired = 0;
        for (int i = 0; i < 10; i++) {
            launch(store, DARTS[i % 2], new Vector3d(0, MUZZLE_Y, -8), yaw, pitchDown(), creator);
            launch(store, DARTS[i % 2], new Vector3d(0, MUZZLE_Y, -12), yaw, pitchUp(), creator);
            launch(store, DARTS[i % 2], new Vector3d(0, MUZZLE_Y, -16), yaw, 0, creator);
            fired += 3;
        }
        say.accept("fired a burst of " + fired + " darts (into the floor, into the sky, along an empty lane); at once: " + store.getEntityCount()
                + " entities, " + projectiles(store) + " projectiles");
        for (long ms : new long[] {250, 500, 1000, 2000, 4000}) {
            later(world, ms, () -> say.accept(String.format("  %.2f s after the burst: %d entities, %d projectiles", ms / 1000.0,
                    store.getEntityCount(), projectiles(store))));
        }
        later(world, 6000, () -> {
            DartRangeDamageSystem.trace = false;
            held.release();
            say.accept("  6 s after the burst: " + store.getEntityCount() + " entities (" + baseline + " before any dart), "
                + projectiles(store) + " projectiles (" + baselineProjectiles + " before)");
        });
    }

    private static int projectiles(Store<EntityStore> store) {
        return store.getEntityCountFor(Archetype.of(ProjectileComponent.getComponentType()));
    }

    private static Ref<EntityStore> launch(Store<EntityStore> store, String dart, Vector3d at, float yaw, float pitch, UUID creator) {
        TimeResource time = store.getResource(TimeResource.getResourceType());
        Rotation3f rotation = new Rotation3f();
        rotation.setPitch(pitch);
        rotation.setYaw(yaw);
        rotation.setRoll(0);
        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(time, dart, new Vector3d(at), rotation);
        ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
        projectile.initialize();
        projectile.shoot(holder, creator, at.x, at.y, at.z, yaw, pitch);
        return store.addEntity(holder, AddReason.SPAWN);
    }

    private static float health(Store<EntityStore> store, Ref<EntityStore> ref) {
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue value = stats == null ? null : stats.get(EntityStatType.getAssetMap().getIndex(HEALTH));
        return value == null ? Float.NaN : value.get();
    }

    private static Vector3d direction(Vector3d at, float yaw, float pitch) {
        Rotation3f rotation = new Rotation3f();
        rotation.setPitch(pitch);
        rotation.setYaw(yaw);
        rotation.setRoll(0);
        return new Vector3d(new Transform(at, rotation).getDirection());
    }

    private static float yawTowardPlusX() {
        float plus = (float) (Math.PI / 2);
        return direction(new Vector3d(), plus, 0).x > 0.5 ? plus : -plus;
    }

    private static float pitchUp() {
        float p = (float) Math.toRadians(30);
        return direction(new Vector3d(), yawTowardPlusX(), p).y > 0 ? p : -p;
    }

    private static float pitchDown() {
        return -pitchUp();
    }

    private static void later(World world, long millis, Runnable action) {
        CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS).execute(() -> world.execute(action));
    }
}
