package com.paulorchard.islandcraft.equipmentofarrakis.core;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.function.Consumer;

/**
 * Development: loads the chunk column and the sky section at 0, 200, 0, then writes a block, reads it back, removes it and
 * reads again, through {@link WorldAccess}. {@code /arrakis worldtest}. The lines say PROBLEM where a step did not behave.
 */
final class WorldAccessSelfTest {

    private WorldAccessSelfTest() {
    }

    static void run(World world, Consumer<String> say) {
        say.accept("loading the column and the sky section at 0, 200, 0");
        WorldAccess.loadTestArea(world, 0, 0, 0, 0, 200).whenCompleteAsync((held, error) -> {
            if (error != null) {
                say.accept("PROBLEM the chunk did not load: " + error);
                return;
            }
            try {
                say.accept("section held loaded: " + held.loaded() + " of " + held.wanted);
                WorldAccess access = new WorldAccess(world);
                say.accept("column loaded: " + access.isLoaded(0, 0) + ", air at 0,200,0 reads id " + access.block(0, 200, 0)
                        + " (expect 0)");
                boolean set = access.setBlock(0, 200, 0, "Rock_Stone", 0);
                BlockType type = access.blockType(0, 200, 0);
                String id = type == null ? "none" : type.getId();
                say.accept((set && "Rock_Stone".equals(id) ? "" : "PROBLEM ") + "setBlock Rock_Stone -> " + set + ", read back "
                        + id);
                boolean cleared = access.setBlock(0, 200, 0, "Empty", 0);
                say.accept((cleared && access.block(0, 200, 0) == 0 ? "" : "PROBLEM ") + "setBlock Empty -> " + cleared
                        + ", read back id " + access.block(0, 200, 0) + " (expect 0)");
                say.accept("unloaded reads as no block: " + (access.blockType(1_000_000, 200, 1_000_000) == null));
                say.accept("surface height at 0,0: " + access.height(0, 0));
            } catch (RuntimeException e) {
                say.accept("PROBLEM the world test threw " + e);
            } finally {
                held.release();
            }
        }, world);
    }
}
