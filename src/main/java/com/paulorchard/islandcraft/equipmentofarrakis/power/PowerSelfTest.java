package com.paulorchard.islandcraft.equipmentofarrakis.power;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.paulorchard.islandcraft.equipmentofarrakis.core.WorldAccess;
import it.unimi.dsi.fastutil.Pair;
import org.joml.Vector3d;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Development: runs the power service on a test dummy (every living entity has a stat map with Mana), because a headless server
 * has no player. It checks the clamping, the all-or-nothing spend, and the four questions about max modifiers: what raising and
 * lowering the max do to the current value, and whether a modifier put directly survives the engine's own recalculation.
 * Lines saying PROBLEM mean a step did not behave. Creative mode and the client-side bar need a player. In the headless 0.7.0
 * world the NPC plugin refused the spawn on every try (see the SKIPPED line), so the dummy path is unproven there.
 */
final class PowerSelfTest {

    private PowerSelfTest() {
    }

    static void run(World world, Power power, Consumer<String> say) {
        say.accept("loading the chunk at 0, 200, 0 for the dummy");
        WorldAccess.loadTestArea(world, 0, 0, 0, 0, 200).whenCompleteAsync((held, error) -> {
            if (error != null) {
                say.accept("PROBLEM the chunk did not load: " + error);
                return;
            }
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                // The dummy stands on the natural ground at 0, 0 (a spawn in the empty sky section is refused as an invalid position).
                WorldAccess access = new WorldAccess(world);
                double feet = access.height(0, 0) + 1;
                say.accept("column 0, 0 ticking right after the load: " + access.isTicking(0, 0));
                say.accept("ground at 0, 0: top block y " + (feet - 1) + ", the dummy spawns at y " + feet);
                Pair<Ref<EntityStore>, ?> dummy = null;
                for (String role : new String[] {"Test_Dummy_Stationary", "Test_Dummy", "Test_Dummy_Damage"}) {
                    boolean known = NPCPlugin.get().hasRoleName(role);
                    say.accept("role " + role + ": known " + known + ", index " + NPCPlugin.get().getIndex(role));
                    if (known && dummy == null) {
                        dummy = NPCPlugin.get().spawnNPC(store, role, null, new Vector3d(0.5, feet, 0.5), new Rotation3f());
                        if (dummy == null) {
                            Object[] captured = new Object[1];
                            var result = NPCPlugin.get().spawnNPCWithSpaceValidation(store, role, null, new Vector3d(0.5, feet, 0.5),
                                    new Rotation3f(), (npc, spawned, s) -> captured[0] = spawned);
                            say.accept("  spawn with validation: " + result + (captured[0] == null ? "" : ", spawned"));
                            if (captured[0] != null) {
                                @SuppressWarnings("unchecked") Ref<EntityStore> got = (Ref<EntityStore>) captured[0];
                                dummy = Pair.of(got, null);
                            }
                        }
                    }
                }
                if (dummy == null) {
                    say.accept("SKIPPED the engine refused every test dummy spawn (FAIL_INVALID_POSITION from the NPC plugin even on loaded ticking ground); "
                            + "the power service is then checked by hand with the /arrakis power commands on a client");
                    held.release();
                    return;
                }
                Ref<EntityStore> ref = dummy.left();
                later(world, 1500, () -> {
                    try {
                        basics(power, store, ref, say);
                        modifiers(power, store, ref, say);
                    } catch (RuntimeException e) {
                        say.accept("PROBLEM the power test threw " + e);
                    }
                    later(world, 800, () -> {
                        try {
                            recalculation(power, store, ref, say);
                        } catch (RuntimeException e) {
                            say.accept("PROBLEM the power test threw " + e);
                        }
                        later(world, 800, () -> {
                            try {
                                after(power, store, ref, say);
                            } finally {
                                store.removeEntity(ref, RemoveReason.REMOVE);
                                held.release();
                                say.accept("power test finished");
                            }
                        });
                    });
                });
            } catch (RuntimeException e) {
                say.accept("PROBLEM the power test threw " + e);
                held.release();
            }
        }, world);
    }

    private static String state(Power power, Store<EntityStore> store, Ref<EntityStore> ref) {
        return power.get(store, ref) + " / " + power.max(store, ref);
    }

    private static void check(Consumer<String> say, boolean ok, String text) {
        say.accept((ok ? "" : "PROBLEM ") + text);
    }

    private static void basics(Power power, Store<EntityStore> store, Ref<EntityStore> ref, Consumer<String> say) {
        say.accept("start: Mana " + state(power, store, ref) + ", stat map present: "
                + (store.getComponent(ref, EntityStatMap.getComponentType()) != null));
        power.set(store, ref, 50);
        check(say, power.get(store, ref) == 50f, "set 50 -> " + power.get(store, ref));
        boolean spent = power.spend(store, ref, 30);
        check(say, spent && power.get(store, ref) == 20f, "spend 30 of 50 -> " + spent + ", left " + power.get(store, ref) + " (expect true, 20)");
        boolean refused = power.spend(store, ref, 30);
        check(say, !refused && power.get(store, ref) == 20f, "spend 30 of 20 -> " + refused + ", left " + power.get(store, ref)
                + " (expect false, 20, unchanged)");
        check(say, power.spend(store, ref, 0) && power.spend(store, ref, -5) && power.get(store, ref) == 20f,
                "spend 0 and spend -5 are no-ops that succeed, left " + power.get(store, ref));
        float taken = power.spendUpTo(store, ref, 50);
        check(say, taken == 20f && power.get(store, ref) == 0f, "spendUpTo 50 with 20 -> took " + taken + ", left " + power.get(store, ref)
                + " (expect 20, 0)");
        power.add(store, ref, 1000);
        check(say, power.get(store, ref) == power.max(store, ref), "add 1000 clamps to the max -> " + state(power, store, ref));
        power.add(store, ref, -1000);
        check(say, power.get(store, ref) == 0f, "add -1000 clamps to 0 -> " + power.get(store, ref));
        power.fill(store, ref);
        check(say, power.get(store, ref) == power.max(store, ref), "fill -> " + state(power, store, ref));
        say.accept("creative: not testable on a dummy (it has no Player component); isCreative says " + power.isCreative(store, ref));
    }

    private static void modifiers(Power power, Store<EntityStore> store, Ref<EntityStore> ref, Consumer<String> say) {
        PowerModifiers mods = power.modifiers();
        power.set(store, ref, 80);
        say.accept("Q1 raising the max: before " + state(power, store, ref));
        mods.putMax(store, ref, "IslandCraft:Test", PowerModifiers.Kind.ADDITIVE, 16);
        say.accept("Q1 after +16 additive: " + state(power, store, ref) + " (the current value should stay 80)");
        check(say, power.max(store, ref) == 116f, "max is 116 -> " + power.max(store, ref));
        check(say, power.get(store, ref) == 80f, "raising the max did not raise the current value -> " + power.get(store, ref));
        mods.putMax(store, ref, "IslandCraft:Test", PowerModifiers.Kind.ADDITIVE, 30);
        check(say, power.max(store, ref) == 130f, "the same id again replaces (130, not 146) -> " + power.max(store, ref));
        mods.putMax(store, ref, "IslandCraft:Test2", PowerModifiers.Kind.MULTIPLICATIVE, 2);
        say.accept("two modifiers: +30 and x2 -> max " + power.max(store, ref) + " (the order the engine applies them in decides 260 or 230)");
        mods.removeMax(store, ref, "IslandCraft:Test2");
        power.set(store, ref, 130);
        say.accept("Q2 lowering the max: before " + state(power, store, ref));
        boolean removed = mods.removeMax(store, ref, "IslandCraft:Test");
        say.accept("Q2 after removing +30: " + state(power, store, ref) + " (removed " + removed + "; the current value should be "
                + "clamped to the max)");
        check(say, removed && power.max(store, ref) == 100f, "max back to 100 -> " + power.max(store, ref));
        say.accept((power.get(store, ref) <= power.max(store, ref) ? "" : "PROBLEM ") + "current value after lowering the max: "
                + power.get(store, ref) + " (clamped by the engine itself: " + (power.get(store, ref) <= 100f) + ")");
        // For the recalculation question: put one and ask the engine to recalculate.
        mods.putMax(store, ref, "IslandCraft:Survivor", PowerModifiers.Kind.ADDITIVE, 50);
        EntityStatMap map = store.getComponent(ref, EntityStatMap.getComponentType());
        map.getStatModifiersManager().scheduleRecalculate();
        say.accept("Q3 modifier put, recalculation scheduled; max now " + power.max(store, ref));
    }

    private static void recalculation(Power power, Store<EntityStore> store, Ref<EntityStore> ref, Consumer<String> say) {
        boolean listed = power.modifiers().list(store, ref).stream().anyMatch(m -> m.id().equals("IslandCraft:Survivor"));
        check(say, listed && power.max(store, ref) == 150f, "Q3 after the engine's recalculation tick the directly put modifier is "
                + (listed ? "still there" : "GONE") + ", max " + power.max(store, ref) + " (expect 150)");
        // Put on armor would make the engine write its own keys; ours must be untouched. An armor swap needs a player.
        say.accept("Q3 the swap of real armor needs a player on a client: see the manual checks");
    }

    private static void after(Power power, Store<EntityStore> store, Ref<EntityStore> ref, Consumer<String> say) {
        power.modifiers().removeMax(store, ref, "IslandCraft:Survivor");
        check(say, power.max(store, ref) == 100f, "cleanup: max back to " + power.max(store, ref));
    }


    private static void later(World world, long millis, Runnable action) {
        CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS).execute(() -> world.execute(action));
    }
}
