package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Development: runs the projectile hooks of the Holtzman shield in a server with no player. Two training dummies stand in for shooters;
 * one is declared a player for the test ({@link ShieldShots#testPlayer}), the other is not. A dart of the older projectile kind and a
 * vanilla shortbow arrow of the newer kind are launched with each as the creator, and the per-player state says what the hooks saw:
 * the stand-in player's shots must be counted (by both kinds) and the other shooter's must not. {@code arrakis shield hooktest}.
 */
final class ShieldHookSelfTest {

    private ShieldHookSelfTest() {
    }

    static void run(World world, Consumer<String> say) {
        // The columns, and the sky sections the floor at y 199 lies in (they may not exist yet), held loaded for the test.
        WorldAccess.loadTestArea(world, -16, 16, -16, 16, 199).whenCompleteAsync((held, error) -> {
            try {
                if (error != null) {
                    say.accept("chunks did not load: " + error);
                    return;
                }
                say.accept("sky sections at y 199 and 200 loaded and held: " + held.loaded() + " of " + held.wanted);
                stage1(world, say, held);
            } catch (RuntimeException e) {
                say.accept("hook test failed: " + e);
            }
        }, world);
        say.accept("loading the chunks; the hook test takes about 6 seconds");
    }

    private static void stage1(World world, Consumer<String> say, WorldAccess.Held held) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        WorldAccess access = new WorldAccess(world);
        int missed = 0;
        for (int x = -4; x <= 12; x++) {
            for (int z = -4; z <= 8; z++) {
                if (!access.setBlock(x, 199, z, "Rock_Stone", 0)) {
                    missed++;
                }
            }
        }
        say.accept("floor of Rock_Stone at y 199: " + (missed == 0 ? "all blocks set" : missed + " blocks NOT set (section not loaded)"));
        Pair<Ref<EntityStore>, ?> a = NPCPlugin.get().spawnNPC(store, "Test_Dummy_Stationary", null, new Vector3d(0, 200, 0), new Rotation3f());
        Pair<Ref<EntityStore>, ?> b = NPCPlugin.get().spawnNPC(store, "Test_Dummy_Stationary", null, new Vector3d(0, 200, 6), new Rotation3f());
        if (a == null || b == null) {
            say.accept("could not spawn the Test_Dummy_Stationary role");
            held.release();
            return;
        }
        UUID playerLike = store.getComponent(a.left(), UUIDComponent.getComponentType()).getUuid();
        UUID other = store.getComponent(b.left(), UUIDComponent.getComponentType()).getUuid();
        ShieldShots.testPlayer = playerLike;
        ShieldShots.trace = true;
        say.accept("shooter A (counts as a player) " + playerLike + ", shooter B (a mob) " + other);
        later(world, 1500, () -> stage2(world, store, say, a.left(), b.left(), playerLike, other, held));
    }

    private static void stage2(World world, Store<EntityStore> store, Consumer<String> say, Ref<EntityStore> refA, Ref<EntityStore> refB,
            UUID playerLike, UUID other, WorldAccess.Held held) {
        HoltzmanState stateA = HoltzmanState.of(playerLike);
        HoltzmanState stateB = HoltzmanState.of(other);
        long a0 = stateA.projectiles;
        long b0 = stateB.projectiles;
        // 1. the older projectile kind (the guns): B first, then A.
        dart(store, new Vector3d(0, 200.9, 6), other);
        later(world, 300, () -> {
            say.accept("1. legacy dart from the mob: counted for the mob " + (stateB.projectiles - b0) + " (expect 0 - not a player, and never even recorded)");
            dart(store, new Vector3d(0, 200.9, 0), playerLike);
            later(world, 300, () -> {
                say.accept("   legacy dart from the player: counted " + (stateA.projectiles - a0) + " (expect 1), cooldown running " + stateA.cooling(playerLike, System.nanoTime()));
                // 2. the newer projectile kind (vanilla bows and throws).
                ProjectileConfig config = ProjectileConfig.getAssetMap().getAsset("Projectile_Config_Arrow_Shortbow");
                if (config == null) {
                    say.accept("2. no Projectile_Config_Arrow_Shortbow");
                    return;
                }
                long a1 = stateA.projectiles;
                DevInjectSystem.run(buffer -> {
                    ProjectileModule.get().spawnProjectile(refB, buffer, config, new Vector3d(2, 200.9, 6), new Vector3d(1, 0, 0));
                    ProjectileModule.get().spawnProjectile(refA, buffer, config, new Vector3d(2, 200.9, 0), new Vector3d(1, 0, 0));
                });
                later(world, 500, () -> {
                    say.accept("2. standard arrows, one from the mob and one from the player: the player's counted " + (stateA.projectiles - a1)
                            + " (expect 1); the mob's " + (stateB.projectiles - b0) + " (expect 0)");
                    // 3. three projectiles in one tick count as three stamps of (nearly) one time, one cooldown.
                    long a2 = stateA.projectiles;
                    for (int i = 0; i < 3; i++) {
                        dart(store, new Vector3d(0, 200.9, 0), playerLike);
                    }
                    later(world, 300, () -> {
                        say.accept("3. a volley of 3 darts: " + (stateA.projectiles - a2) + " stamps, one cooldown with " + String.format("%.2f", stateA.remaining(playerLike, System.nanoTime())) + " s left");
                        later(world, 1200, () -> {
                            say.accept("4. 1.2 s after the volley the cooldown is over: " + !stateA.cooling(playerLike, System.nanoTime()) + " (expect true)");
                            held.release();
                            ShieldShots.testPlayer = null;
                            ShieldShots.trace = false;
                            HoltzmanState.forget(playerLike);
                            HoltzmanState.forget(other);
                        });
                    });
                });
            });
        });
    }

    private static void dart(Store<EntityStore> store, Vector3d at, UUID creator) {
        TimeResource time = store.getResource(TimeResource.getResourceType());
        Rotation3f rotation = new Rotation3f();
        rotation.setYaw((float) (-Math.PI / 2));
        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(time, "Arrakis_Maula_Dart", new Vector3d(at), rotation);
        ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
        projectile.initialize();
        projectile.shoot(holder, creator, at.x, at.y, at.z, rotation.yaw(), rotation.pitch());
        store.addEntity(holder, AddReason.SPAWN);
    }

    private static void later(World world, long millis, Runnable action) {
        CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS).execute(() -> world.execute(action));
    }
}
