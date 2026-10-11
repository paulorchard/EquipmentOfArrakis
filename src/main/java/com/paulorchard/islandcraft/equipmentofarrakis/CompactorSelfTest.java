package com.paulorchard.islandcraft.equipmentofarrakis;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthChunk;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Development: runs the engine calls of the Compactor on a sand block placed high in the air, without a player, and reports what
 * happened. It checks what a headless server can: the block is still there however hard the crack is pushed, the health is
 * given back, and the drops are the block's own. {@code arrakis compactor selftest}.
 */
final class CompactorSelfTest {

    private CompactorSelfTest() {
    }

    static List<String> run(World world) {
        List<String> out = new ArrayList<>();
        Vector3i pos = new Vector3i(0, 200, 0);
        world.setBlock(pos.x, pos.y, pos.z, "Soil_Sand");
        int sandId = world.getBlock(pos.x, pos.y, pos.z);
        BlockType type = world.getBlockType(pos);
        out.add("block at " + pos + ": id " + (type == null ? null : type.getId()) + " group " + (type == null ? null : type.getGroup())
                + " allowed by default rule: " + (type != null && CompactMath.allowed(type.getGroup(), type.getId(),
                List.of("Sand"), List.of("Arrakis_Sand"), List.of("Arrakis_Worm_Ripple_Layer_", "Arrakis_Worm_Sunk_"))));
        CompactorTrail.State one = new CompactorTrail.State(UUID.randomUUID(), null);
        CompactorTrail.State two = new CompactorTrail.State(UUID.randomUUID(), null);
        for (CompactorTrail.State s : new CompactorTrail.State[] {one, two}) {
            s.world = world;
            s.target = new Vector3i(pos);
        }
        float floor = 0.1f;
        out.add("health at the start: " + health(world, pos));
        CompactorTrail.showCrack(one, CompactMath.healthAt(0.5, 0.9), floor);
        out.add("player one at half a cycle (expect 0.55): " + health(world, pos) + ", taken " + one.taken);
        CompactorTrail.showCrack(one, CompactMath.healthAt(0.5, 0.9), floor);
        out.add("the same tick again changes nothing (expect 0.55): " + health(world, pos) + ", taken " + one.taken);
        CompactorTrail.showCrack(two, CompactMath.healthAt(0.2, 0.9), floor);
        out.add("player two behind (progress 0.2) adds nothing (expect 0.55): " + health(world, pos) + ", taken " + two.taken);
        CompactorTrail.showCrack(two, CompactMath.healthAt(1.0, 0.9), floor);
        out.add("player two at the end of a cycle (expect 0.1): " + health(world, pos) + ", taken one " + one.taken + " two " + two.taken);
        for (int i = 0; i < 1000; i++) {
            CompactorTrail.showCrack(one, -50f, floor);
            CompactorTrail.showCrack(two, CompactMath.healthAt(5, 0.9), floor);
        }
        out.add("1000 ticks pushing far past the end (expect 0.1, never 0 or below): " + health(world, pos));
        out.add("the block is still there: " + (world.getBlock(pos.x, pos.y, pos.z) == sandId));
        out.add("taken before letting go: one " + one.taken + " two " + two.taken);
        CompactorTrail.release(one);
        out.add("player one let go (expect 0.1 + 0.45 = 0.55): " + health(world, pos));
        CompactorTrail.release(two);
        out.add("player two let go (expect 1.0, the record removed): " + health(world, pos) + ", block still there: "
                + (world.getBlock(pos.x, pos.y, pos.z) == sandId));
        if (type != null) {
            List<ItemStack> drops = CompactorTrail.dropsOf(type);
            StringBuilder names = new StringBuilder();
            for (ItemStack stack : drops) {
                names.append(stack.getItemId()).append(" x").append(stack.getQuantity()).append(' ');
            }
            out.add("drops of the block: " + names);
        }
        world.setBlock(pos.x, pos.y, pos.z, "Empty");
        return out;
    }

    private static float health(World world, Vector3i pos) {
        BlockHealthChunk health = CompactorTrail.healthChunk(world, pos);
        return health == null ? Float.NaN : health.getBlockHealth(pos);
    }
}
